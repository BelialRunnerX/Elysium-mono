package com.elysium.flora.rules;

/**
 * What a crop is worth tending, and what a tree is worth burning.
 *
 * <h2>The problem this solves</h2>
 *
 * String and charcoal are the two materials in this game that a player never
 * farms. String comes off spiders, which means it comes off a mob farm or off
 * luck; charcoal comes out of a furnace fed with the wood you were going to
 * build with. Neither is a decision, and both gate things that matter — every
 * bow, every bed, every torch — so the early game contains a long stretch of
 * hoping for spiders.
 *
 * <p>Two crops and a tree, then. Not because the pack needs more plants, but
 * because these two materials should be growable and are the only common ones
 * that are not.
 *
 * <h2>The shape of the numbers</h2>
 *
 * The house rule: amounts climb linearly and without a cap; anything that is a
 * percentage goes through {@code x / (x + b)} and never arrives at one.
 *
 * <p>A crop's yield climbs with the grower's world tier, like an ore's does,
 * but on its own and slower pace — {@link #TIERS_PER_EXTRA} rather than the
 * ore module's three. A vein is found once and mined out; a field is replanted
 * every morning, so the same generosity applied to a crop compounds into a
 * player who never thinks about string again.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class FloraRules {

    private FloraRules() {
    }

    // ------------------------------------------------------------------
    // Growing
    // ------------------------------------------------------------------

    /** How many stages a crop passes through, vanilla's eight. */
    public static final int STAGES = 8;

    /** The stage at which it may be harvested. */
    public static final int MATURE = STAGES - 1;

    /** Whether a crop at this age is worth breaking. */
    public static boolean ripe(int age) {
        return age >= MATURE;
    }

    /** An age clamped into the range the block actually has. */
    public static int clampAge(int age) {
        return Math.max(0, Math.min(MATURE, age));
    }

    // ------------------------------------------------------------------
    // Harvesting
    // ------------------------------------------------------------------

    /** What a ripe plant gives before anything else is counted. */
    public static final int BASE_YIELD = 1;

    /**
     * World tiers per additional fibre.
     *
     * Six, twice the ore module's three, and the doubling is the point: a
     * field is replanted every morning and a vein is not, so the same pace
     * applied to both would make crops the only material anybody farms.
     */
    public static final int TIERS_PER_EXTRA = 6;

    /** How many extra a grower at this tier may receive. Linear, uncapped. */
    public static int bonus(int worldTier) {
        return Math.max(0, worldTier) / TIERS_PER_EXTRA;
    }

    /**
     * The b in {@code x / (x + b)} for whether the bonus lands.
     *
     * Ten: a little over a third at the first tier that has a bonus at all,
     * half at ten, and never all of the time.
     */
    public static final float BONUS_PACE = 10.0F;

    /** How often the bonus lands. Approaches one; never reaches it. */
    public static float bonusChance(int worldTier) {
        float at = Math.max(0, worldTier);
        return at / (at + BONUS_PACE);
    }

    /** What one ripe plant gives, on a roll in {@code [0, 1)}. */
    public static int harvest(int worldTier, double roll) {
        int extra = bonus(worldTier);
        if (extra <= 0) {
            return BASE_YIELD;
        }
        return roll < bonusChance(worldTier) ? BASE_YIELD + extra : BASE_YIELD;
    }

    /**
     * Fortune on a crop, folded the way vanilla folds it on ore: a chance at a
     * multiple, with the worst roll unchanged.
     */
    public static int fortuned(int amount, int fortune, double roll) {
        if (fortune <= 0 || amount <= 0) {
            return Math.max(0, amount);
        }
        int multiplier = (int) (roll * (fortune + 2));
        int capped = Math.max(1, Math.min(multiplier, fortune + 1));
        return amount * capped;
    }

    // ------------------------------------------------------------------
    // What the fibre is for
    // ------------------------------------------------------------------

    /**
     * Fibres for one string.
     *
     * Three, so a full field is worth walking down and a single plant is not a
     * string. Spiders still drop string at one apiece; this is a slower road
     * that is always available rather than a faster one that replaces them.
     */
    public static final int FIBRE_PER_STRING = 3;

    /** Tufts for one string. Softer stuff, so it takes more of it. */
    public static final int TUFT_PER_STRING = 4;

    /** How many of a fibre a given count of string costs. */
    public static int fibreFor(int strings, int per) {
        return Math.max(0, strings) * Math.max(1, per);
    }

    // ------------------------------------------------------------------
    // Burning
    // ------------------------------------------------------------------

    /** Ticks one coal burns for, vanilla's number, stated so the rest can be relative. */
    public static final int COAL_TICKS = 1600;

    /**
     * Ticks emberwood charcoal burns for.
     *
     * Half as much again as coal. A tree that grows in the Nether should be
     * worth more than one that grows in a field, and a fuel that is farmable
     * and better than coal is the reward for having got to the Nether and
     * carried a sapling home.
     */
    public static final int CHARCOAL_TICKS = COAL_TICKS * 3 / 2;

    /** Ticks a block of that charcoal burns for: nine of them, exactly. */
    public static int blockTicks() {
        return CHARCOAL_TICKS * 9;
    }

    /** Ticks emberwood planks burn for. Vanilla's wood number; wood is wood. */
    public static final int PLANK_TICKS = 300;

    // ------------------------------------------------------------------
    // Where the wild ones are
    // ------------------------------------------------------------------

    /**
     * How many wild patches are attempted per chunk.
     *
     * Low, and deliberately so. A wild plant is a seed source, not a harvest:
     * the point is that a player who has never seen one finds one within an
     * afternoon's walking and then grows their own. A number high enough to
     * farm the wild ones would remove the reason to plant anything.
     */
    public static final int WILD_TRIES = 2;

    /** Plants in one wild patch. */
    public static final int WILD_PATCH = 12;

    /**
     * Seeds a wild plant gives.
     *
     * Two, so finding one patch is enough to start and a player is not sent
     * looking for a second one before they can plant anything.
     */
    public static final int WILD_SEEDS = 2;
}
