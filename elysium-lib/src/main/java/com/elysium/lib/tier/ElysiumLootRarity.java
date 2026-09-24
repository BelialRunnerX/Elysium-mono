package com.elysium.lib.tier;

import java.util.function.DoubleSupplier;

/**
 * How good a thing that just dropped is.
 *
 * <h2>One scale, not two</h2>
 *
 * Elysium already has a scale for how good a piece of gear is: the tier behind
 * {@code ElysiumRarities} — Common, Uncommon, Rare, Epic, Legendary, Unique,
 * and Ascendant N above that, forever. A second scale for "loot rarity" would
 * be a second set of names for the same idea, and a player would have to learn
 * which of two Rares they were holding.
 *
 * <p>So a rarity <em>is</em> a gear tier. This decides which one a drop rolls,
 * and that number means everywhere else exactly what it already meant: the
 * colour of the name, the level needed to use it, the elemental advantage, and
 * — new here — how many affixes the piece carries.
 *
 * <h2>How the roll works</h2>
 *
 * A drop starts Common and climbs. Each step up is a separate test, and each
 * one is harder than the last by a fixed factor, so a Legendary is not rare
 * because a table says 0.4% — it is rare because it had to pass four tests in
 * a row. That is also why there is no ceiling: nothing stops the climb, it
 * just gets less likely forever.
 *
 * <p>What makes a step likelier is the world tier and the finder's luck, both
 * through {@code x / (x + b)} so that neither reaches certainty however far it
 * is pushed — which is what keeps "uncap everything" from meaning "every drop
 * is Unique at level 400".
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumLootRarity {

    private ElysiumLootRarity() {
    }

    /**
     * How much of the world tier each step up gets to lean on.
     *
     * Step 0 is asked to clear a bar set by a third of the tier, step 1 a
     * quarter, step 2 a fifth, and so on: the divisor is {@code step + 2}.
     *
     * <p>A flat falloff — every step a fixed fraction as likely as the last —
     * was the first version of this and it was wrong in a way worth recording.
     * Because the fractions multiply, the odds of a Legendary collapse to
     * roughly the same vanishing number whatever the tier: a player at tier
     * 400 found the same distribution as one at tier 4, and the whole climb
     * bought nothing. Dividing the tier instead means a step gets harder as it
     * goes up and easier as the player does, which is what a world tier is
     * supposed to mean.
     */
    public static final double STEP_DIVISOR_OFFSET = 2.0D;

    /** Luck at which the luck term is halfway to its limit. */
    public static final double LUCK_PACE = 6.0D;

    /**
     * How far a single drop may climb.
     *
     * Not a ceiling on rarity — a piece can be ascended past this by other
     * means, and the odds of reaching even a fraction of it are vanishing.
     * It is here so that a roll is guaranteed to end, the same reason the crit
     * chain has one.
     */
    public static final int MAX_STEPS = 64;

    /** The luck term: a share, so no amount of luck is a certainty. */
    public static double luckShare(double luck) {
        double at = Math.max(0.0D, luck);
        return at / (at + LUCK_PACE);
    }

    /**
     * The chance of the step that takes a drop from {@code step} to
     * {@code step + 1}.
     *
     * The world tier and the finder's luck are combined as two independent
     * chances to miss rather than added, so the result is always below one
     * however large either is — which is what keeps "uncap everything" from
     * meaning "every drop is Unique at level 400".
     */
    public static double stepChance(int tier, double luck, int step) {
        double divisor = Math.max(0, step) + STEP_DIVISOR_OFFSET;
        double byTier = ElysiumWorldTier.share(tier, 1.0D / divisor);
        double byLuck = luckShare(luck / divisor);
        return 1.0D - (1.0D - byTier) * (1.0D - byLuck);
    }

    /** The chance of the first step up: from Common to Uncommon. */
    public static double base(int tier, double luck) {
        return stepChance(tier, luck, 0);
    }

    /**
     * The rarity a drop comes out at.
     *
     * @param rolls a source of numbers in [0, 1)
     */
    public static int roll(int tier, double luck, DoubleSupplier rolls) {
        int rarity = 0;
        while (rarity < MAX_STEPS && rolls.getAsDouble() < stepChance(tier, luck, rarity)) {
            rarity++;
        }
        return rarity;
    }

    /**
     * How many affixes a piece of this rarity carries: one per step above
     * Common.
     *
     * A Common piece is a plain piece. That matters more than it sounds: it is
     * what keeps an affix meaning something, since most of what a player picks
     * up has none.
     */
    public static int affixes(int rarity) {
        return Math.max(0, rarity);
    }

    /**
     * The highest rarity a world tier can be expected to produce, for a
     * tooltip or a codex page rather than for a roll.
     *
     * "Expected" as in: the rarity whose odds are still better than one in a
     * hundred at this tier. Above it the drops exist and nobody should plan
     * around them.
     */
    public static int expected(int tier, double luck) {
        double odds = 1.0D;
        int rarity = 0;
        while (rarity < MAX_STEPS) {
            odds *= stepChance(tier, luck, rarity);
            if (odds < 0.01D) {
                return rarity;
            }
            rarity++;
        }
        return rarity;
    }
}
