package com.elysium.lib.tier;

import java.util.Random;

/**
 * How good a drop is, executed.
 *
 * Rarity is felt as a distribution rather than as a number, so the checks here
 * are mostly about shape: that a higher tier really is better, that luck helps
 * and never guarantees, that the chain always ends, and that Common stays the
 * common case — which is the whole reason an affix means anything.
 */
public final class LootRarityHarness {

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
        chances();
        theClimb();
        distribution();
        affixes();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a rarity is a gear tier, each step leaning "
                + "on less of the world tier than the last, no ceiling and no certainty");
    }

    private static void chances() {
        check(ElysiumLootRarity.base(0, 0.0D) == 0.0D,
                "a Frontier player with no luck could still find something better than Common");
        check(ElysiumLootRarity.base(4, 0.0D) > 0.0D, "the world tier bought nothing at all");
        check(ElysiumLootRarity.base(4, 5.0D) > ElysiumLootRarity.base(4, 0.0D),
                "luck did nothing");
        check(ElysiumLootRarity.base(1_000_000, 1e9) < 1.0D,
                "an absurd tier and absurd luck together reached certainty");
        check(ElysiumLootRarity.luckShare(-5.0D) == 0.0D, "negative luck granted a share");
        check(Math.abs(ElysiumLootRarity.luckShare(ElysiumLootRarity.LUCK_PACE) - 0.5D) < 1e-9,
                "luck is not half its limit at its pace");
        for (int tier = 0; tier < 200; tier++) {
            check(ElysiumLootRarity.base(tier, 3.0D) < 1.0D,
                    "the first step became certain at tier " + tier);
            check(tier == 0 || ElysiumLootRarity.base(tier, 3.0D) > ElysiumLootRarity.base(tier - 1, 3.0D),
                    "a higher tier was not better at tier " + tier);
        }
    }

    private static void theClimb() {
        for (int step = 0; step < 10; step++) {
            double here = ElysiumLootRarity.stepChance(8, 2.0D, step);
            double next = ElysiumLootRarity.stepChance(8, 2.0D, step + 1);
            check(next < here, "step " + (step + 1) + " was not harder than step " + step);
            check(here < 1.0D, "step " + step + " was a certainty");
        }
        // Always rolling low climbs; the cap is what makes that terminate.
        check(ElysiumLootRarity.roll(50, 100.0D, () -> 0.0D) == ElysiumLootRarity.MAX_STEPS,
                "a roll that always succeeds did not stop at the cap, so a drop could hang the game");
        check(ElysiumLootRarity.roll(50, 100.0D, () -> 0.999999D) == 0,
                "a roll that always fails still climbed");
        check(ElysiumLootRarity.roll(0, 0.0D, () -> 0.0D) == 0,
                "the Frontier produced something better than Common on a perfect roll");
    }

    private static void distribution() {
        Random random = new Random(20260915L);
        int[] counts = new int[ElysiumLootRarity.MAX_STEPS + 1];
        for (int i = 0; i < 200_000; i++) {
            counts[ElysiumLootRarity.roll(4, 0.0D, random::nextDouble)]++;
        }
        check(counts[0] > 100_000,
                "most drops at the Apotheosis tier were not Common; an affix means nothing "
                        + "if everything has one");
        for (int rarity = 1; rarity < 6; rarity++) {
            check(counts[rarity] < counts[rarity - 1],
                    "rarity " + rarity + " came up more often than rarity " + (rarity - 1));
        }
        int high = 0;
        for (int rarity = 5; rarity < counts.length; rarity++) {
            high += counts[rarity];
        }
        check(high < 200, "Unique and above came up " + high + " times in 200,000, which is "
                + "not rare");
        check(ElysiumLootRarity.expected(4, 0.0D) >= 1,
                "nothing above Common is worth planning around at the Apotheosis tier");
        check(ElysiumLootRarity.expected(0, 0.0D) == 0,
                "the Frontier was expected to produce better than Common");
        check(ElysiumLootRarity.expected(400, 50.0D) > ElysiumLootRarity.expected(4, 0.0D),
                "climbing far past the named tiers bought nothing a player could plan around");
    }

    private static void affixes() {
        check(ElysiumLootRarity.affixes(0) == 0, "a Common piece carried an affix");
        check(ElysiumLootRarity.affixes(3) == 3, "an Epic piece did not carry three affixes");
        check(ElysiumLootRarity.affixes(-2) == 0, "a negative rarity carried a negative affix count");
    }
}
