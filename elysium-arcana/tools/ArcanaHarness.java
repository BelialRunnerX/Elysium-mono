package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Every number in {@link ArcanaRules}, executed.
 *
 * The claim this file exists for is that no sequence of crafting, dissolving
 * and condensing ever leaves a player better off than they started. That is a
 * property over every walk through the whole recipe set, which is not
 * something anybody checks by reading a table of ten thousand numbers.
 */
public final class ArcanaHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theSeeds();
        theDerivation();
        theDirections();
        theWalk();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no recipe anywhere makes more than it "
                + "consumes, a round trip always loses, and over a million random acts "
                + "of crafting, dissolving and condensing not one left anybody better "
                + "off than they started");
    }

    // ==================================================================

    private static void theSeeds() {
        check(ArcanaRules.SEEDS.size() >= 20,
                "there are only " + ArcanaRules.SEEDS.size() + " decided values, which "
                        + "leaves most of the game unpriceable");
        for (Map.Entry<String, Long> seed : ArcanaRules.SEEDS.entrySet()) {
            check(seed.getValue() > 0L,
                    seed.getKey() + " is worth " + seed.getValue() + ", and something "
                            + "worth nothing is something you can dissolve forever");
            check(seed.getKey().contains(":"),
                    seed.getKey() + " is not a namespaced id, so it names nothing");
        }
        check(ArcanaRules.SEEDS.get("minecraft:cobblestone") == 1L,
                "the unit is not a cobblestone, so every ratio in the module is "
                        + "relative to something nobody can hold");
        check(ArcanaRules.SEEDS.get("minecraft:diamond")
                        > ArcanaRules.SEEDS.get("minecraft:iron_ingot"),
                "iron is worth more than diamond");
        System.out.println("  seeds: " + ArcanaRules.SEEDS.size()
                + " decided values, all positive, all named");
    }

    private static ArcanaRules.Step step(String output, int makes, Object... pairs) {
        Map<String, Integer> needs = new HashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            needs.put((String) pairs[at], (Integer) pairs[at + 1]);
        }
        return new ArcanaRules.Step(output, makes, needs);
    }

    private static void theDerivation() {
        Map<String, ArcanaRules.Step> recipes = new HashMap<>();
        recipes.put("plank", step("plank", 4, "minecraft:oak_log", 1));
        recipes.put("stick", step("stick", 4, "plank", 2));
        recipes.put("torch", step("torch", 4, "stick", 1, "minecraft:coal", 1));
        recipes.put("pick", step("pick", 1, "stick", 2, "minecraft:cobblestone", 3));

        Map<String, Long> worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(worth.get("plank") == 8L, "four planks out of a 32 log is not 8 each");
        check(worth.get("stick") == 4L, "four sticks out of two 8 planks is not 4 each");
        check(worth.get("pick") == 11L, "a stone pick is not two sticks and three stone");
        check(worth.get("minecraft:oak_log") == 32L, "a decided value was derived over");

        check(ArcanaRules.profitable(recipes, worth).isEmpty(),
                "an ordinary recipe set contains a profitable recipe: "
                        + ArcanaRules.profitable(recipes, worth));

        // Cheapest wins. A second, worse way of making a thing must not raise
        // its price, because the cheap way is what it actually costs and the
        // gap between them is what a player would ride.
        recipes.put("plank_expensive", step("plank", 1, "minecraft:oak_log", 1));
        Map<String, Long> again = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(again.get("plank") == 8L,
                "a second, worse recipe raised the price of a plank to "
                        + again.get("plank"));
        check(ArcanaRules.profitable(recipes, again).isEmpty(),
                "adding a worse recipe made something profitable");

        // An ingredient nobody can price makes its recipe unpriceable, rather
        // than free.
        recipes.put("mystery", step("mystery", 1, "something_nobody_ships", 1));
        recipes.put("mystery_box", step("mystery_box", 1, "mystery", 4));
        Map<String, Long> third = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(!third.containsKey("mystery"),
                "an item made of something unpriceable was priced anyway");
        check(!third.containsKey("mystery_box"),
                "an item made of an unpriceable item was priced anyway");

        // The shape that breaks every naive table: a loop.
        Map<String, ArcanaRules.Step> looped = new HashMap<>();
        looped.put("block", step("block", 1, "ingot", 9));
        looped.put("ingot", step("ingot", 9, "block", 1));
        looped.put("ingot_from_ore", step("ingot", 1, "minecraft:cobblestone", 300));
        Map<String, Long> spun = ArcanaRules.derive(looped, ArcanaRules.SEEDS);
        check(ArcanaRules.profitable(looped, spun).isEmpty(),
                "the nine-to-one loop every pack contains came out profitable: "
                        + ArcanaRules.profitable(looped, spun));

        // And a set that deliberately cannot be settled at all.
        Map<String, ArcanaRules.Step> nonsense = new HashMap<>();
        nonsense.put("a", step("a", 2, "b", 1));
        nonsense.put("b", step("b", 2, "a", 1));
        nonsense.put("a_seeded", step("a", 1, "minecraft:diamond", 1));
        Map<String, Long> settled = ArcanaRules.derive(nonsense, ArcanaRules.SEEDS);
        check(ArcanaRules.profitable(nonsense, settled).isEmpty(),
                "a halving loop was priced into something profitable: "
                        + ArcanaRules.profitable(nonsense, settled));

        System.out.println("  derivation: cheapest wins, unpriceable stays unpriceable, "
                + "and neither a nine-to-one loop nor a halving loop is profitable");
    }

    private static void theDirections() {
        check(ArcanaRules.BURN_SHARE < 1.0F,
                "a round trip is lossless, so any rounding error anywhere in a table of "
                        + "ten thousand numbers is an infinite supply of something");
        check(ArcanaRules.BURN_SHARE > 0.5F,
                "dissolving loses more than half, which is not a transmutation system, "
                        + "it is a bin");

        Random random = new Random(1_618_033L);
        for (int round = 0; round < 400_000; round++) {
            long worth = 1L + random.nextInt(100_000);
            int count = 1 + random.nextInt(64);
            long got = ArcanaRules.dissolve(worth, count);
            long costs = ArcanaRules.condensing(worth, count);
            check(got <= costs, "dissolving " + count + " at " + worth + " gave " + got
                    + " and condensing them back costs " + costs);
            check(got >= 0L, "dissolving gave a negative amount");
            int back = ArcanaRules.affords(got, worth);
            check(back <= count, "dissolving " + count + " bought back " + back);
            check(ArcanaRules.condensing(worth, back) <= got,
                    "what a balance says it affords costs more than the balance");
        }
        check(ArcanaRules.dissolve(0L, 10) == 0L, "something worth nothing gave motes");
        check(ArcanaRules.dissolve(100L, 0) == 0L, "dissolving nothing gave motes");
        check(ArcanaRules.dissolve(-5L, 10) == 0L, "a negative worth gave motes");
        check(ArcanaRules.affords(1_000L, 0L) == 0,
                "a balance buys any number of something worth nothing");
        check(ArcanaRules.affords(-1L, 10L) == 0, "a negative balance buys something");
        System.out.println("  directions: 400,000 round trips, and not one of them came "
                + "back with as much as it started with");
    }

    /**
     * The property the module exists for.
     *
     * <p>A random walk of the three things a player can do — craft, dissolve,
     * condense — watching one number: what they have, plus what it is worth.
     * It may fall. It may stay still. It may never rise.
     */
    private static void theWalk() {
        Random random = new Random(2_718_281L);
        Map<String, ArcanaRules.Step> recipes = new HashMap<>();
        List<String> names = new ArrayList<>(ArcanaRules.SEEDS.keySet());
        for (int index = 0; index < 120; index++) {
            String made = "thing" + index;
            Map<String, Integer> needs = new HashMap<>();
            int count = 1 + random.nextInt(3);
            for (int need = 0; need < count; need++) {
                needs.put(names.get(random.nextInt(names.size())), 1 + random.nextInt(8));
            }
            recipes.put("r" + index, new ArcanaRules.Step(made, 1 + random.nextInt(4),
                    needs));
            names.add(made);
        }
        // And some recipes that consume the derived things, so the table has
        // depth in it rather than one layer over the seeds.
        for (int index = 0; index < 120; index++) {
            Map<String, Integer> needs = new HashMap<>();
            int count = 1 + random.nextInt(3);
            for (int need = 0; need < count; need++) {
                needs.put(names.get(random.nextInt(names.size())), 1 + random.nextInt(4));
            }
            recipes.put("deep" + index, new ArcanaRules.Step("deep" + index,
                    1 + random.nextInt(3), needs));
            names.add("deep" + index);
        }

        Map<String, Long> worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        List<String> profitable = ArcanaRules.profitable(recipes, worth);
        check(profitable.isEmpty(),
                profitable.size() + " of 240 random recipes came out profitable: "
                        + profitable.subList(0, Math.min(4, profitable.size())));
        check(worth.size() > ArcanaRules.SEEDS.size() + 100,
                "only " + worth.size() + " things could be priced out of a set of 240 "
                        + "recipes, so the walk below is mostly about nothing");

        List<String> priced = new ArrayList<>(worth.keySet());
        long crafted = 0;
        long dissolved = 0;
        long condensed = 0;
        for (int life = 0; life < 400; life++) {
            Map<String, Integer> held = new HashMap<>();
            long motes = 0L;
            for (int index = 0; index < 8; index++) {
                held.merge(priced.get(random.nextInt(priced.size())),
                        1 + random.nextInt(32), Integer::sum);
            }
            long was = ArcanaRules.estate(motes, held, worth);

            for (int act = 0; act < 2_500; act++) {
                switch (random.nextInt(3)) {
                    case 0 -> {                                   // craft
                        ArcanaRules.Step step = new ArrayList<>(recipes.values())
                                .get(random.nextInt(recipes.size()));
                        boolean can = true;
                        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                            if (held.getOrDefault(need.getKey(), 0) < need.getValue()) {
                                can = false;
                                break;
                            }
                        }
                        if (!can) {
                            break;
                        }
                        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                            held.merge(need.getKey(), -need.getValue(), Integer::sum);
                        }
                        held.merge(step.output(), step.makes(), Integer::sum);
                        crafted++;
                    }
                    case 1 -> {                                   // dissolve
                        String what = priced.get(random.nextInt(priced.size()));
                        int have = held.getOrDefault(what, 0);
                        if (have <= 0) {
                            break;
                        }
                        int spend = 1 + random.nextInt(have);
                        held.merge(what, -spend, Integer::sum);
                        motes += ArcanaRules.dissolve(worth.get(what), spend);
                        dissolved++;
                    }
                    default -> {                                  // condense
                        String what = priced.get(random.nextInt(priced.size()));
                        long each = worth.get(what);
                        int most = ArcanaRules.affords(motes, each);
                        if (most <= 0) {
                            break;
                        }
                        int want = 1 + random.nextInt(most);
                        motes -= ArcanaRules.condensing(each, want);
                        held.merge(what, want, Integer::sum);
                        condensed++;
                    }
                }
                long now = ArcanaRules.estate(motes, held, worth);
                check(now <= was, "a player's estate rose from " + was + " to " + now
                        + " on act " + act + " of life " + life);
                check(motes >= 0L, "a balance went negative");
                was = now;
            }
        }
        check(crafted > 1_000 && dissolved > 1_000 && condensed > 1_000,
                "the walk did " + crafted + " crafts, " + dissolved + " dissolves and "
                        + condensed + " condenses, so one of the three is untested");
        System.out.println("  the walk: 400 lives, " + crafted + " crafts, " + dissolved
                + " dissolves and " + condensed + " condenses, and the estate never "
                + "rose once");
    }
}
