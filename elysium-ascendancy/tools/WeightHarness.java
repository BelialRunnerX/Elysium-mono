package com.elysium.ascendancy.weight;

import java.util.List;
import java.util.Map;

/**
 * The weighting engine, executed. Compiled against the JDK alone — which is
 * itself the check that the engine is pure.
 */
public final class WeightHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean ok, String message) {
        checked++;
        if (!ok) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    record Item(String name, long weight) {
    }

    public static void main(String[] args) {
        // 1. Tiers: missing is zero, "everywhere" is everywhere.
        TieredWeight late = TieredWeight.byTier(Map.of(3, new TieredWeight.Entry(10, 0f),
                4, new TieredWeight.Entry(20, 0f)));
        check(late.weightAt(0, 0) == 0 && late.weightAt(2, 100) == 0,
                "an entry not listed for a tier can appear in it");
        check(late.weightAt(3, 0) == 10 && late.weightAt(4, 0) == 20,
                "a listed tier did not give its weight");
        TieredWeight common = TieredWeight.everywhere(50, 0f);
        for (int t = -1; t <= 9; t++) {
            check(common.weightAt(t, 0) == 50, "an everywhere-entry changed at tier " + t);
        }

        // 2. Luck buys quality, and only quality.
        TieredWeight fine = TieredWeight.everywhere(5, 2.5f);
        TieredWeight plain = TieredWeight.everywhere(5, 0f);
        check(fine.weightAt(0, 4) == 5 + 10, "luck 4 x quality 2.5 did not add 10");
        check(plain.weightAt(0, 1000) == 5, "luck raised an entry with no quality");
        long previous = -1;
        for (double luck = 0; luck <= 10_000; luck += 7.5) {
            long w = fine.weightAt(0, luck);
            check(w >= previous, "more luck lowered a quality entry's weight at " + luck);
            previous = w;
        }
        check(fine.weightAt(0, 1.0E30) == Long.MAX_VALUE,
                "enormous luck wrapped instead of saturating");

        // 3. The one floor: zero.
        TieredWeight cursed = TieredWeight.everywhere(5, -3f);
        check(cursed.weightAt(0, 100) == 0, "a negative weight counted as negative, not none");
        check(fine.weightAt(0, Double.NaN) == 5, "NaN luck did not count as no luck");

        // 4. Picking: every entry owns exactly its share of the rolls.
        List<Item> items = List.of(new Item("a", 1), new Item("b", 2), new Item("c", 3),
                new Item("never", 0), new Item("d", 4));
        long total = WeightedPick.total(items, Item::weight);
        check(total == 10, "the total is " + total + ", not 10");
        int steps = 100_000;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (int k = 0; k < steps; k++) {
            Item got = WeightedPick.pick(items, Item::weight, (double) k / steps);
            counts.merge(got.name(), 1, Integer::sum);
        }
        for (Item item : items) {
            int wanted = (int) (item.weight() * steps / total);
            check(counts.getOrDefault(item.name(), 0) == wanted,
                    item.name() + " was picked " + counts.getOrDefault(item.name(), 0)
                            + " times in " + steps + ", not exactly " + wanted);
        }

        // 5. Edges.
        check(WeightedPick.pick(items, Item::weight, 1.0) == items.get(4),
                "a roll of exactly 1.0 fell off the end");
        check(WeightedPick.pick(items, Item::weight, -3.0) == items.get(0)
                        && WeightedPick.pick(items, Item::weight, Double.NaN) == items.get(0),
                "a nonsense roll did not land on the first entry");
        check(WeightedPick.pick(List.of(new Item("z", 0)), Item::weight, 0.5) == null,
                "nothing with weight was still picked");
        check(WeightedPick.total(List.of(new Item("x", Long.MAX_VALUE), new Item("y", Long.MAX_VALUE)),
                Item::weight) == Long.MAX_VALUE, "the total overflowed");

        // GenContext: the same seed gives the same picks, tier gaps weigh nothing,
        // and constraints treat an empty list as "anywhere".
        java.util.List<String> names = java.util.List.of("common", "rare", "tier4only");
        java.util.Map<String, TieredWeight> ws = new java.util.HashMap<>();
        ws.put("common", TieredWeight.everywhere(90, 0.0F));
        ws.put("rare", TieredWeight.everywhere(10, 5.0F));
        ws.put("tier4only", TieredWeight.byTier(java.util.Map.of(4, new TieredWeight.Entry(1000, 0.0F))));
        GenContext a = new GenContext(new java.util.SplittableRandom(42L), 1, 0.0D, "minecraft:overworld", "minecraft:plains", 0, 64, 0);
        GenContext b = new GenContext(new java.util.SplittableRandom(42L), 1, 0.0D, "minecraft:overworld", "minecraft:plains", 0, 64, 0);
        int rareSeen = 0;
        for (int i = 0; i < 20000; i++) {
            String pa = a.pick(names, ws::get);
            check(pa.equals(b.pick(names, ws::get)), "same seed, different pick");
            check(!pa.equals("tier4only"), "a tier-4 entry rolled at tier 1");
            if (pa.equals("rare")) rareSeen++;
        }
        check(rareSeen > 1600 && rareSeen < 2400, "rare at tier 1 without luck: " + rareSeen + " of 20000, expected ~2000");
        GenContext lucky = new GenContext(new java.util.SplittableRandom(7L), 1, 18.0D, "x", "y", 0, 0, 0);
        int luckyRare = 0;
        for (int i = 0; i < 20000; i++) if ("rare".equals(lucky.pick(names, ws::get))) luckyRare++;
        check(luckyRare > 9000, "luck 18 should make rare about half: " + luckyRare);
        check(a.pick(java.util.List.of("none"), n -> null) == null, "all-zero weights picked something");
        check(a.constrained(null, java.util.List.of()), "empty constraints refuse");
        check(a.constrained(java.util.List.of("minecraft:overworld"), null), "own dimension refused");
        check(!a.constrained(java.util.List.of("minecraft:the_nether"), null), "other dimension allowed");
        check(!a.constrained(null, java.util.List.of("minecraft:desert")), "other biome allowed");

        if (failures > 0) {
            System.out.println(failures + " of " + checked + " checks failed");
            System.exit(1);
        }
        System.out.println(checked + " checks: GenContext seeded and constrained; missing tiers weigh nothing, luck buys only "
                + "quality, and each entry wins exactly its share of " + steps + " rolls");
    }
}
