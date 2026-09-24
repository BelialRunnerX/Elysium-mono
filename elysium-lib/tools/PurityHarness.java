package com.elysium.lib.item;

import java.util.Random;

/**
 * Purities, executed.
 *
 * A purity is a rune tier, and a rune tier is a price: {@code 2^(tier-1)} base
 * runes. Everything here is about that price staying honest — that grinding
 * loses, that cutting costs what merging costs, and that neither overflows at
 * a tier a hand-edited save can name.
 */
public final class PurityHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        names();
        prices();
        grinding();
        cutting();
        drops();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + ElysiumPurity.NAMED + " named purities, "
                + "a price that doubles, grinding that loses "
                + (int) (ElysiumPurity.GRINDING_LOSS * 100) + "%, cutting that costs what "
                + "merging costs");
    }

    private static void names() {
        check(ElysiumPurity.name(1).equals("Cracked"), "the first purity is not the worst one");
        check(ElysiumPurity.name(6).equals("Perfect"), "the last named purity is not the best one");
        check(ElysiumPurity.name(7).equals("Tier 7"),
                "a purity past the named ones does not fall back to its number");
        check(ElysiumPurity.name(0).equals("Cracked") && ElysiumPurity.name(-4).equals("Cracked"),
                "a tier below the first is not read as the first");
        check(ElysiumPurity.nameKey(6).equals("elysium.purity.perfect"),
                "a named purity reads the wrong key");
        check(ElysiumPurity.nameKey(40).equals("elysium.purity.beyond"),
                "every purity past the names would need a translation of its own");
        for (int tier = 1; tier <= ElysiumPurity.NAMED; tier++) {
            check(!ElysiumPurity.name(tier).startsWith("Tier "),
                    "purity " + tier + " is inside the named range and has no name");
        }
    }

    private static void prices() {
        check(ElysiumPurity.baseRunes(1) == 1, "the base rune costs more than itself");
        check(ElysiumPurity.baseRunes(2) == 2, "the second tier is not two runes");
        check(ElysiumPurity.baseRunes(5) == 16, "the fifth tier is not sixteen runes");
        check(ElysiumPurity.baseRunes(10) == 512, "the tenth tier is not five hundred and twelve");
        for (int tier = 1; tier < 31; tier++) {
            check(ElysiumPurity.baseRunes(tier + 1) == ElysiumPurity.baseRunes(tier) * 2,
                    "the price stopped doubling at tier " + tier);
            check(ElysiumPurity.baseRunes(tier) > 0,
                    "the price went negative at tier " + tier + ", which is an overflow");
        }
        check(ElysiumPurity.baseRunes(64) == Integer.MAX_VALUE,
                "an absurd tier overflowed instead of saturating");
        check(ElysiumPurity.baseRunes(0) == 1, "a tier below the first cost something other than one");
    }

    private static void grinding() {
        check(ElysiumPurity.dustFrom(1) >= 1,
                "the first tier grinds into nothing, so a spare rune is worth nothing");
        for (int tier = 1; tier < 30; tier++) {
            check(ElysiumPurity.dustFrom(tier) < ElysiumPurity.baseRunes(tier)
                            || ElysiumPurity.baseRunes(tier) == 1,
                    "grinding tier " + tier + " loses nothing, so dust is a way to make value");
            check(ElysiumPurity.dustFrom(tier + 1) >= ElysiumPurity.dustFrom(tier),
                    "a better rune ground into less dust at tier " + tier);
        }
        check(ElysiumPurity.dustFrom(64) > 0, "an absurd tier ground into a negative pile");
    }

    private static void cutting() {
        for (int tier = 1; tier < 20; tier++) {
            check(ElysiumPurity.cutCost(tier) == ElysiumPurity.baseRunes(tier),
                    "cutting from tier " + tier + " does not cost what merging would");
            // The loop that matters: grind a rune, and what you get back must
            // not pay for the cut you would have needed to make it.
            check(ElysiumPurity.dustFrom(tier) < ElysiumPurity.cutCost(tier)
                            || ElysiumPurity.cutCost(tier) == 1,
                    "grinding a tier-" + tier + " rune pays for cutting one back up, which is "
                            + "a loop that makes runes out of nothing");
        }
        check(ElysiumPurity.canCut(1), "the first tier cannot be cut at all");
        check(!ElysiumPurity.canCut(ElysiumRuneTiers.MAX_TIER),
                "a rune at the top of the arithmetic can still be cut");
        check(!ElysiumPurity.canCut(0), "a tier below the first can be cut");
    }

    private static void drops() {
        check(ElysiumPurity.dropChance(0) == 0.0D, "the Frontier drops runes");
        check(ElysiumPurity.dropChance(4) > 0.0D, "no world tier drops a rune at all");
        for (int tier = 0; tier < 500; tier++) {
            check(ElysiumPurity.dropChance(tier) < 1.0D,
                    "a rune became a certain drop at world tier " + tier);
            check(tier == 0 || ElysiumPurity.dropChance(tier) > ElysiumPurity.dropChance(tier - 1),
                    "the drop chance stopped rising at world tier " + tier);
        }
        Random random = new Random(20260923L);
        check(ElysiumPurity.foundPurity(0, () -> 0.0D) == 1,
                "the Frontier found better than a cracked rune on a perfect roll");
        check(ElysiumPurity.foundPurity(1_000_000, () -> 0.0D) == ElysiumPurity.NAMED,
                "a perfect roll at an absurd tier did not reach the best named purity");
        check(ElysiumPurity.foundPurity(40, () -> 0.999999D) == 1,
                "a failed roll still found a better rune");
        int total = 0;
        for (int i = 0; i < 20_000; i++) {
            total += ElysiumPurity.foundPurity(4, random::nextDouble);
        }
        double average = total / 20_000.0D;
        check(average > 1.0D && average < 2.0D,
                "the average purity found at the last named world tier is " + average
                        + "; a rune is meant to be a find, not a certainty");
    }
}
