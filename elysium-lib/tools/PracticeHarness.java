package com.elysium.lib.practice;

/**
 * The practice ladder, executed against every price the game can offer.
 *
 * <h2>What is worth running here</h2>
 *
 * One thing above all: <b>no trade ever becomes free.</b> A discount applied to
 * a price is the single most dangerous small feature in a mod, because the
 * failure is not a crash — it is an infinite emerald machine that looks like it
 * is working, and the player who finds it will not report it.
 *
 * So the discount is walked against every base cost vanilla can produce, at
 * every band, and checked from both ends: never at or below zero, and never
 * higher than it started.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/ph elysium-lib/tools/PracticeHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/ph" \
 *         com.elysium.lib.practice.PracticeHarness
 */
public final class PracticeHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /**
     * Vanilla's first cost runs 1 to 64. Walked past both ends on purpose:
     * another mod's offer is not obliged to stay in that range, and the one
     * that does not is the one that finds the bug.
     */
    private static final int MAX_COST = 128;

    /** Nothing ever becomes free, and nothing ever gets dearer. */
    private static void nothingEverBecomesFree() {
        for (ElysiumPractice band : ElysiumPractice.values()) {
            for (int cost = -8; cost <= MAX_COST; cost++) {
                int priced = band.priceFor(cost);
                if (cost >= ElysiumPractice.FLOOR) {
                    check(priced >= ElysiumPractice.FLOOR,
                            band + " priced a cost of " + cost + " at " + priced
                                    + ". A trade that reaches zero is an "
                                    + "infinite emerald machine that looks like "
                                    + "it is working");
                    check(priced <= cost,
                            band + " made a cost of " + cost + " dearer, at "
                                    + priced + "; practice is supposed to help");
                } else {
                    check(priced == cost,
                            band + " changed a nonsensical cost of " + cost
                                    + " into " + priced + " instead of leaving "
                                    + "it alone");
                }
            }
        }
    }

    /**
     * The number handed to vanilla is never positive.
     *
     * Vanilla <em>adds</em> the special price difference to the cost, so a
     * positive one would make a practised villager dearer — the exact opposite
     * of the feature, and it would read as the mod punishing loyalty.
     */
    private static void theDiffIsNeverPositive() {
        for (ElysiumPractice band : ElysiumPractice.values()) {
            for (int cost = 1; cost <= MAX_COST; cost++) {
                int diff = band.priceDiffFor(cost);
                check(diff <= 0,
                        band + " hands vanilla a difference of +" + diff
                                + " on a cost of " + cost + ", which makes the "
                                + "trade dearer");
                check(cost + diff >= ElysiumPractice.FLOOR,
                        band + " would take a cost of " + cost + " down to "
                                + (cost + diff));
            }
        }
    }

    /** The bands are ordered, distinct, and start at nothing. */
    private static void theBandsAreOrdered() {
        ElysiumPractice[] all = ElysiumPractice.values();
        check(all[0].trades() == 0 && all[0].discount() == 0.0F,
                "the first band already gives a discount, so every villager in "
                        + "the world starts cheap and practice buys nothing");
        for (int i = 1; i < all.length; i++) {
            check(all[i].trades() > all[i - 1].trades(),
                    all[i] + " needs no more trades than " + all[i - 1]);
            check(all[i].discount() > all[i - 1].discount(),
                    all[i] + " is worth no more than " + all[i - 1]
                            + "; two bands that do the same thing are one band "
                            + "with two names");
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ElysiumPractice band : all) {
            check(ids.add(band.id()), "two bands share the id " + band.id());
            check(ElysiumPractice.byId(band.id()) == band,
                    band + " does not come back from its own id");
        }
        check(ElysiumPractice.byId("a_removed_mods_band") == null,
                "an unknown band id throws rather than reading as none");
    }

    /** The ladder is climbable and the counting stops where it stops mattering. */
    private static void theLadderIsClimbable() {
        ElysiumPractice top = ElysiumPractice.values()[
                ElysiumPractice.values().length - 1];
        check(ElysiumPractice.CEILING == top.trades() + 1,
                "counting stops at " + ElysiumPractice.CEILING
                        + " and the top band needs " + top.trades()
                        + "; they are one number written twice");
        check(ElysiumPractice.forTrades(ElysiumPractice.CEILING) == top,
                "a villager at the counting ceiling is not at the top band");
        check(ElysiumPractice.forTrades(0) == ElysiumPractice.UNFAMILIAR,
                "a villager who has never made a trade is already practised");
        check(ElysiumPractice.forTrades(-4) == ElysiumPractice.UNFAMILIAR,
                "a negative trade count produced "
                        + ElysiumPractice.forTrades(-4));
        check(ElysiumPractice.forTrades(Integer.MAX_VALUE) == top,
                "an enormous trade count does not read as the top band");
    }

    /**
     * What the discount is actually worth, at the prices players see.
     *
     * Printed rather than only asserted: the figure that decides whether this
     * feature is generous or broken is the proportion off a <em>cheap</em>
     * trade, and no assertion states it.
     */
    private static void whatItIsWorth() {
        ElysiumPractice top = ElysiumPractice.values()[
                ElysiumPractice.values().length - 1];
        System.out.println("  worth: at the top band, a cost of");
        float worst = 0.0F;
        for (int cost : new int[]{1, 2, 4, 8, 16, 32, 64}) {
            int priced = top.priceFor(cost);
            float off = (cost - priced) / (float) cost;
            worst = Math.max(worst, off);
            System.out.println("    " + String.format("%3d", cost) + " becomes "
                    + String.format("%3d", priced) + "  ("
                    + String.format(java.util.Locale.ROOT, "%.0f%% off", off * 100)
                    + ")");
        }
        // Tightened from three quarters once the discount became proportional.
        // The old ceiling was set for a flat discount, where a cheap trade
        // could lose most of its price; with a proportion the worst case is
        // the band's own figure plus a rounding, and a ceiling far above that
        // is a ceiling nothing can reach.
        check(worst <= 0.35F,
                "the best discount takes " + String.format(java.util.Locale.ROOT,
                        "%.0f%%", worst * 100) + " off. Past about three "
                        + "a third a regular customer is not getting a good "
                        + "price, they are getting the shop");
    }

    public static void main(String[] args) {
        nothingEverBecomesFree();
        theDiffIsNeverPositive();
        theBandsAreOrdered();
        theLadderIsClimbable();
        whatItIsWorth();

        System.out.println("practice: " + ElysiumPractice.values().length
                + " bands across " + (MAX_COST + 9) + " costs — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("no trade ever becomes free");
    }
}
