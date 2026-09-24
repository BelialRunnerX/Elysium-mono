package com.elysium.lib.affix;

import java.util.ArrayList;
import java.util.List;

/**
 * The affix arithmetic, executed.
 *
 * Weights and rolls are the part of an affix system a player feels and nobody
 * can see: a pool that never draws its rarest entry, or one that draws it as
 * often as the commonest, plays the same as a pool with no weights at all.
 * Every claim below is one that would otherwise only be true by inspection.
 */
public final class AffixHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    public static void main(String[] args) {
        picking();
        weighting();
        levels();
        interpolation();
        names();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: weighted pools, level rolls, "
                + "interpolation and the order of a name");
    }

    private static List<ElysiumAffixRules.Candidate> pool() {
        return List.of(new ElysiumAffixRules.Candidate("common", 10),
                new ElysiumAffixRules.Candidate("uncommon", 5),
                new ElysiumAffixRules.Candidate("rare", 1));
    }

    private static void picking() {
        check(ElysiumAffixRules.pick(List.of(), 0.5D) == null,
                "an empty pool produced an affix out of nothing");
        check(ElysiumAffixRules.pick(List.of(new ElysiumAffixRules.Candidate("x", 0)), 0.5D) == null,
                "a pool whose only entry weighs nothing still drew it");
        check("common".equals(ElysiumAffixRules.pick(pool(), 0.0D)),
                "the weakest roll did not land on the first entry");
        check("rare".equals(ElysiumAffixRules.pick(pool(), 1.0D)),
                "a roll of exactly 1 fell off the end of the pool");
        check("rare".equals(ElysiumAffixRules.pick(pool(), 1.5D)),
                "a roll past 1 fell off the end of the pool");
        check("common".equals(ElysiumAffixRules.pick(pool(), -1.0D)),
                "a roll below 0 fell off the front of the pool");
        check(ElysiumAffixRules.pick(pool(), 0.5D) != null, "a roll in the middle drew nothing");
    }

    private static void weighting() {
        // 10 : 5 : 1 over sixteen even steps, which is the whole of the table.
        int[] counts = new int[3];
        List<ElysiumAffixRules.Candidate> pool = pool();
        for (int i = 0; i < 1600; i++) {
            String drawn = ElysiumAffixRules.pick(pool, i / 1600.0D);
            counts[drawn.equals("common") ? 0 : drawn.equals("uncommon") ? 1 : 2]++;
        }
        check(counts[0] == 1000, "the weight-10 entry came up " + counts[0] + " times in 1600, not 1000");
        check(counts[1] == 500, "the weight-5 entry came up " + counts[1] + " times in 1600, not 500");
        check(counts[2] == 100, "the weight-1 entry came up " + counts[2] + " times in 1600, not 100");
        check(counts[0] + counts[1] + counts[2] == 1600, "some rolls drew nothing at all");
        List<ElysiumAffixRules.Candidate> zeroed = new ArrayList<>(pool);
        zeroed.set(2, new ElysiumAffixRules.Candidate("rare", 0));
        for (int i = 0; i < 100; i++) {
            check(!"rare".equals(ElysiumAffixRules.pick(zeroed, i / 100.0D)),
                    "an entry weighing nothing was drawn anyway");
            break;                                     // one is enough to state it
        }
    }

    private static void levels() {
        check(ElysiumAffixRules.level(1, 0.0D) == 1 && ElysiumAffixRules.level(1, 1.0D) == 1,
                "an affix with one level rolled something other than that level");
        check(ElysiumAffixRules.level(4, 0.0D) == 1, "the weakest roll was not level 1");
        check(ElysiumAffixRules.level(4, 0.999D) == 4, "the strongest roll was not the maximum");
        check(ElysiumAffixRules.level(4, 1.0D) == 4, "a roll of exactly 1 passed the maximum");
        check(ElysiumAffixRules.level(4, -1.0D) == 1, "a roll below 0 went below level 1");
        for (int i = 0; i <= 100; i++) {
            int level = ElysiumAffixRules.level(4, i / 100.0D);
            check(level >= 1 && level <= 4, "a roll produced level " + level + ", outside 1..4");
            if (level < 1 || level > 4) {
                break;
            }
        }
        check(ElysiumAffixRules.level(0, 0.5D) == 1,
                "an affix declaring no levels rolled level 0, which is a name on an item that does nothing");
    }

    private static void interpolation() {
        check(ElysiumAffixRules.between(1.0D, 3.0D, 0.0D) == 1.0D, "the weakest roll was not the minimum");
        check(ElysiumAffixRules.between(1.0D, 3.0D, 1.0D) == 3.0D, "the strongest roll was not the maximum");
        check(ElysiumAffixRules.between(1.0D, 3.0D, 0.5D) == 2.0D, "the middle roll was not the middle");
        check(ElysiumAffixRules.between(1.0D, 3.0D, 2.0D) == 3.0D, "a roll past 1 went past the maximum");
        check(ElysiumAffixRules.between(0.0D, -0.3D, 1.0D) == -0.3D,
                "a range that runs downwards — a penalty, which Heavy has — was not read");
    }

    private static void names() {
        check(ElysiumAffixRules.nameParts("a", "b").size() == 3, "a name with both halves has three parts");
        check(ElysiumAffixRules.nameParts(null, "b").size() == 2, "a name with no prefix still had one");
        check(ElysiumAffixRules.nameParts("a", null).size() == 2, "a name with no suffix still had one");
        check(ElysiumAffixRules.nameParts(null, null).size() == 1, "a plain name was not just the item");
        check(ElysiumAffixRules.nameParts("a", "b").get(1).isEmpty(),
                "the item's own name is not in the middle of its own name");
        check(ElysiumAffixRules.isPlacement("prefix") && ElysiumAffixRules.isPlacement("suffix"),
                "a prefix or a suffix is not a placement");
        check(!ElysiumAffixRules.isPlacement("infix"),
                "a placement nobody can put anywhere was accepted");
        check(ElysiumAffixRules.count(0) == 0 && ElysiumAffixRules.count(3) == 3
                && ElysiumAffixRules.count(-1) == 0,
                "the number of affixes for a rarity is not the rarity, floored at none");
    }
}
