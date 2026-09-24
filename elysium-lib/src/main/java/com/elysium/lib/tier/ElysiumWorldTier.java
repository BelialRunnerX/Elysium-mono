package com.elysium.lib.tier;

/**
 * How far into the game a player is, as one number.
 *
 * <h2>Derived, not chosen</h2>
 *
 * Apotheosis makes the world tier a setting a player advances by completing
 * gated content, which is a second progression bar beside the one they are
 * already climbing. Elysium already knows how far along a player is: they have
 * a character level, and they have been to places that are hard to reach. So
 * the tier is read off those rather than tracked separately.
 *
 * <p>That choice has a consequence worth stating: a player cannot lower their
 * world tier to farm easier content, and cannot raise it to farm harder
 * content they have not earned. The tier is what they have done.
 *
 * <h2>Two sources, whichever is higher</h2>
 *
 * <ul>
 *   <li><b>Character level.</b> The ordinary path: ten levels to a tier,
 *       forever.</li>
 *   <li><b>The deepest world reached.</b> A floor rather than a term, because
 *       a player who has stood on Cinderveil has been somewhere a Frontier
 *       drop is an insult, whatever their level says.</li>
 * </ul>
 *
 * <h2>No ceiling</h2>
 *
 * Nothing here caps a tier. Five have names; above that it reads as
 * "Apotheosis N" and keeps climbing, the way an ascended piece of gear does.
 * What the tier <em>buys</em> is another matter: a count climbs with it
 * forever, and anything that is a share of something follows
 * {@code stat / (stat + b)}, so it approaches its ceiling without reaching it.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumWorldTier {

    private ElysiumWorldTier() {
    }

    /** Ten character levels to a tier. */
    public static final int LEVELS_PER_TIER = 10;

    public static final int FRONTIER = 0;
    public static final int ASCENT = 1;
    public static final int SUMMIT = 2;
    public static final int PINNACLE = 3;
    public static final int APOTHEOSIS = 4;

    /** The highest tier with a name of its own. Not a ceiling. */
    public static final int MAX_NAMED = APOTHEOSIS;

    /**
     * The half-value for tier shares: at this many tiers past zero, a share is
     * halfway to its limit.
     *
     * Four, so the named tiers are where most of the movement is and the climb
     * past them still pays — which is the same shape everything else percentile
     * in this mod has.
     */
    public static final double SHARE_PACE = 4.0D;

    /**
     * The tier a player is at.
     *
     * @param characterLevel their level, 1 and up
     * @param worldFloor     the tier of the deepest world they have reached
     */
    public static int of(int characterLevel, int worldFloor) {
        int byLevel = Math.max(0, characterLevel) / LEVELS_PER_TIER;
        return Math.max(0, Math.max(byLevel, worldFloor));
    }

    /**
     * World tiers earned per ascension of a player's base.
     *
     * One, and the one is the whole point. A beacon's ascension is already the
     * most expensive deliberate act in the mod — triangular price, a beacon
     * for the first tier, two for the second — and it is the act a builder
     * performs instead of levelling. Before this it bought ground and
     * attention and nothing else, which made the base a side quest for anybody
     * who wanted the world to get harder and pay better.
     *
     * <p>So ascending the base is now the third road to a tier, alongside the
     * character sheet and the journey. Not an additional road, an alternative
     * one: the three are compared, not summed, by {@link #of}. A player who
     * levels to forty and ascends four times is tier four, not eight — the
     * same rule the world floor has always followed, for the same reason. You
     * are as far along as the furthest thing you have done.
     */
    public static final int TIERS_PER_ASCENSION = 1;

    /** The world tier a base of this ascension is worth on its own. */
    public static int tierForAscension(int ascension) {
        return Math.max(0, ascension) * TIERS_PER_ASCENSION;
    }

    /** The level at which a tier is reached by levelling alone. */
    public static int levelFor(int tier) {
        return Math.max(0, tier) * LEVELS_PER_TIER;
    }

    /** The untranslated name of a tier, for a log line or a fallback. */
    public static String name(int tier) {
        if (tier > MAX_NAMED) {
            return "Apotheosis " + (tier - MAX_NAMED);
        }
        return switch (Math.max(0, tier)) {
            case ASCENT -> "Ascent";
            case SUMMIT -> "Summit";
            case PINNACLE -> "Pinnacle";
            case APOTHEOSIS -> "Apotheosis";
            default -> "Frontier";
        };
    }

    /** The translation key for a tier's name; everything past the named range shares one. */
    public static String nameKey(int tier) {
        return tier > MAX_NAMED ? "elysium.world_tier.beyond"
                : "elysium.world_tier." + name(tier).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * A share that grows with tier and never reaches one: {@code t / (t + b)}.
     *
     * Everything percentile the tier buys goes through here, so no amount of
     * climbing produces a certainty, a division by nothing, or a negative.
     */
    public static double share(int tier) {
        int at = Math.max(0, tier);
        return at / (at + SHARE_PACE);
    }

    /**
     * That share at {@code scale} times the tier behind it — the form used
     * where a bonus should keep climbing past what one tier alone buys.
     */
    public static double share(int tier, double scale) {
        double at = Math.max(0.0D, tier * Math.max(0.0D, scale));
        return at / (at + SHARE_PACE);
    }

    /**
     * Whether a tier is high enough for something that asks for one.
     *
     * A requirement of zero is no requirement, which is what an affix or a
     * loot table that says nothing about tiers means.
     */
    public static boolean allows(int tier, int required) {
        return Math.max(0, tier) >= Math.max(0, required);
    }

    /**
     * How far past a requirement a player is, which is what decides whether
     * old content keeps paying: zero at the gate, climbing after it.
     */
    public static int beyond(int tier, int required) {
        return Math.max(0, Math.max(0, tier) - Math.max(0, required));
    }
}
