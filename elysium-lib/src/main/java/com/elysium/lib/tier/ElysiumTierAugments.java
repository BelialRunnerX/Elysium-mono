package com.elysium.lib.tier;

/**
 * What a world tier is worth, on both sides of the fight.
 *
 * <h2>The shape of the answer</h2>
 *
 * A world tier that only made mobs tougher would be a difficulty slider the
 * player did not ask for. One that only made the player stronger would be a
 * reward for having played, which is what levels already are. It has to be
 * both, and the two sides are deliberately shaped differently:
 *
 * <ul>
 *   <li><b>The world hardens toward a limit.</b> Every multiplier a tier puts
 *       on a mob is {@code 1 + K · (t / (t + b))}, so it approaches
 *       {@code 1 + K} and never passes it. A tier-400 zombie is a hard zombie,
 *       not an unkillable one.</li>
 *   <li><b>The player keeps climbing.</b> Every augment a tier gives a player
 *       is flat per tier, forever, with no curve on it at all.</li>
 * </ul>
 *
 * That asymmetry is the design, not an oversight. It is what "the player
 * scales really hard" means when it is written down: the world's contribution
 * converges and the player's does not, so the deep tiers are where a character
 * built over a long time finally outruns what the world can throw.
 *
 * <p>The luck augment is the one with a loop in it: luck raises the rarity of
 * what drops ({@link ElysiumLootRarity}), rarity is how many affixes a piece
 * carries, and affixes are what makes the next tier survivable. The loop is
 * safe because the luck term is itself a share — more luck always helps and
 * never makes anything certain.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumTierAugments {

    private ElysiumTierAugments() {
    }

    // ------------------------------------------------------------------
    // What the world gains: bounded
    // ------------------------------------------------------------------

    /** A tier-infinite mob has three times the health of a Frontier one, never more. */
    public static final double MOB_HEALTH_LIMIT = 2.0D;

    /** And twice the damage. */
    public static final double MOB_DAMAGE_LIMIT = 1.0D;

    /** And half again the armour. */
    public static final double MOB_ARMOUR_LIMIT = 0.5D;

    /** How much health a mob has, as a multiple, at this world tier. */
    public static double mobHealth(int tier) {
        return 1.0D + MOB_HEALTH_LIMIT * ElysiumWorldTier.share(tier);
    }

    /** How hard it hits. */
    public static double mobDamage(int tier) {
        return 1.0D + MOB_DAMAGE_LIMIT * ElysiumWorldTier.share(tier);
    }

    /** How much of a hit it turns aside. */
    public static double mobArmour(int tier) {
        return 1.0D + MOB_ARMOUR_LIMIT * ElysiumWorldTier.share(tier);
    }

    // ------------------------------------------------------------------
    // What the player gains: unbounded
    // ------------------------------------------------------------------

    /** Half a heart a tier, forever. */
    public static final double HEALTH_PER_TIER = 1.0D;

    /** Luck a tier, which is what feeds the rarity roll. */
    public static final double LUCK_PER_TIER = 1.0D;

    /** A twentieth more experience a tier, so the climb pays for the next climb. */
    public static final double EXPERIENCE_PER_TIER = 0.05D;

    public static double health(int tier) {
        return HEALTH_PER_TIER * Math.max(0, tier);
    }

    public static double luck(int tier) {
        return LUCK_PER_TIER * Math.max(0, tier);
    }

    public static double experience(int tier) {
        return EXPERIENCE_PER_TIER * Math.max(0, tier);
    }

    /**
     * Whether a tier gives a player anything at all.
     *
     * The Frontier does not: it is where everyone starts, and an augment there
     * would be a bonus for existing.
     */
    public static boolean augments(int tier) {
        return tier > ElysiumWorldTier.FRONTIER;
    }
}
