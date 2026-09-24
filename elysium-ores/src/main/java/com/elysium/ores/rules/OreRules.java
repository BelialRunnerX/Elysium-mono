package com.elysium.ores.rules;

/**
 * How much stone a material is worth digging through, and what a tier is worth
 * at the bottom of it.
 *
 * <h2>The problem this solves</h2>
 *
 * Every mod that adds an ore places it in stone and deepslate, because that is
 * where the player is. The result is that the Overworld is the only place
 * worth mining: the Nether has quartz and ancient debris, the End has nothing,
 * and a mod that adds five new worlds adds five places to walk across. A
 * player who has reached Cinderveil goes back to the Overworld to get lead.
 *
 * <p>So this module puts every material in every stone the pack is made of —
 * and then has to answer the question that creates: if lead is everywhere, why
 * go anywhere? The answer is here. The deeper the world, the better the same
 * vein pays.
 *
 * <h2>How it pays</h2>
 *
 * The house rule, which every system in this repository follows: amounts climb
 * linearly and without a cap, and anything that is a percentage goes through
 * {@code x / (x + b)} and therefore never arrives at one.
 *
 * <ul>
 *   <li>The <b>count</b> of raw material a vein block drops climbs by one per
 *       {@link #TIERS_PER_EXTRA} world tiers, forever. That is the linear,
 *       uncapped half, and it is what makes a deep world worth mining in.</li>
 *   <li>The <b>chance</b> of that bonus landing on any given block is
 *       {@code tier / (tier + PACE)}, which rises steeply at first and then
 *       approaches, without reaching, certainty. A player at tier twenty is
 *       nearly always getting the bonus; nobody is ever always getting it.</li>
 * </ul>
 *
 * <h2>Bands</h2>
 *
 * Four, and they set how much of a vein there is rather than how much it
 * pays. Coal is everywhere in large veins; the exotic materials are a handful
 * of blocks you are pleased to find. Rarity is expressed as scarcity of the
 * ore, never as a smaller drop, because a rare ore that also drops badly is
 * two punishments for one event.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class OreRules {

    private OreRules() {
    }

    // ------------------------------------------------------------------
    // Bands
    // ------------------------------------------------------------------

    /** Coal, copper, iron: the ones a player wants by the stack. */
    public static final int COMMON = 0;

    /** Lead, tin, nickel, zinc, aluminum: what a tech mod eats. */
    public static final int INDUSTRIAL = 1;

    /** Gold, silver, platinum, diamond, emerald. */
    public static final int PRECIOUS = 2;

    /** Uranium, osmium, and this pack's own strange materials. */
    public static final int EXOTIC = 3;

    /** The number of bands, for a harness that wants to walk all of them. */
    public static final int BANDS = 4;

    /** Blocks in a vein of the commonest band. */
    public static final int BASE_SIZE = 9;

    /** How many blocks a band takes off a vein. */
    public static final int SIZE_PER_BAND = 2;

    /** No vein is ever smaller than this; a one-block vein is not a vein. */
    public static final int MINIMUM_SIZE = 3;

    /** How many blocks are in one vein of this band. */
    public static int veinSize(int band) {
        int size = BASE_SIZE - SIZE_PER_BAND * clampBand(band);
        return Math.max(MINIMUM_SIZE, size);
    }

    /** Veins per chunk attempt for the commonest band. */
    public static final int BASE_TRIES = 12;

    /**
     * How the attempt count falls away with the band.
     *
     * Halving rather than subtracting, because the interesting range is small:
     * twelve, six, three, one. A linear fall would have made the exotic band
     * nine tries, which is not rare, and a steeper one would have made it zero.
     */
    public static int tries(int band) {
        int at = clampBand(band);
        int count = BASE_TRIES >> at;
        return Math.max(1, count);
    }

    /** The share of a vein exposed to air, before any rarity is applied. */
    public static final float DISCARD_ON_AIR = 0.5F;

    // ------------------------------------------------------------------
    // What a tier is worth at the rock face
    // ------------------------------------------------------------------

    /**
     * World tiers per additional raw material.
     *
     * Three, so the first extra arrives at the tier a player reaches by
     * levelling to thirty or by ascending their base three times — late enough
     * that it is a reward for having got somewhere, early enough that a player
     * feels it inside one world.
     */
    public static final int TIERS_PER_EXTRA = 3;

    /**
     * How many raw materials a block of ore is worth at this tier, before the
     * chance below is rolled.
     *
     * Linear and uncapped, which is the point: there is no tier at which a
     * deeper world stops paying better, and no arithmetic here that quietly
     * stops working at tier nine.
     */
    public static int bonus(int worldTier) {
        return Math.max(0, worldTier) / TIERS_PER_EXTRA;
    }

    /**
     * The b in {@code x / (x + b)} for the chance that the bonus lands.
     *
     * Six: half the time at tier six, three-quarters at eighteen, and never
     * all of the time. Chosen so that the first tier at which a bonus exists
     * at all ({@link #TIERS_PER_EXTRA}, three) lands it one swing in three —
     * noticeable rather than theoretical.
     */
    public static final float BONUS_PACE = 6.0F;

    /** How often the bonus actually lands. Approaches one; never reaches it. */
    public static float bonusChance(int worldTier) {
        float at = Math.max(0, worldTier);
        return at / (at + BONUS_PACE);
    }

    /**
     * What one block of ore yields, given a roll in {@code [0, 1)}.
     *
     * Always at least one: the tier changes what mining pays, never whether
     * mining pays.
     */
    public static int yield(int worldTier, double roll) {
        int extra = bonus(worldTier);
        if (extra <= 0) {
            return 1;
        }
        return roll < bonusChance(worldTier) ? 1 + extra : 1;
    }

    /**
     * Fortune, folded in the same way vanilla folds it: a chance at a multiple
     * rather than a flat addition, so the enchantment stays worth having at
     * every tier instead of being swamped by one.
     *
     * A level of fortune multiplies by two, three, ... with equal chance, and
     * the worst roll is unchanged — which is exactly vanilla's ore bonus rule,
     * restated here so this module has one place that decides it.
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

    private static int clampBand(int band) {
        return Math.max(COMMON, Math.min(EXOTIC, band));
    }
}
