package com.elysium.lib.telegraph;

/**
 * How long a warning a blow of a given size has to earn.
 *
 * <h2>The promise, stated once</h2>
 *
 * <b>Nothing hits a player hard without telling them first.</b> Not "the mod's
 * own mobs try to"; nothing. That is the difference between a telegraph system
 * and a decoration, and it is why the enforcement lives in the damage pipeline
 * rather than in each mob: a blow above {@link #DANGEROUS} from something that
 * did not announce itself is <em>capped</em> at {@link #DANGEROUS}, whoever it
 * came from. A mob from another mod that swings for twenty out of nowhere does
 * six, and the player survives to learn what it was.
 *
 * <h2>Why capping and not cancelling</h2>
 *
 * A cancelled hit teaches a player that some attacks do nothing, which is a
 * worse lesson than any amount of damage. Capping leaves the attack real,
 * leaves the mob threatening, and removes only the part that could not have
 * been played around — the difference between losing half a health bar and
 * losing the fight.
 *
 * <h2>Why the wind-up grows with the blow</h2>
 *
 * A fixed warning is either far too long for a small hit or far too short for a
 * large one. Tying it to the damage means the window a player gets is
 * proportional to what they are being asked to avoid, which is the rule they
 * will infer anyway after the third time — better that it be true.
 *
 * <h2>No Minecraft here</h2>
 *
 * A number in, a number out, so the harness can walk every damage value the
 * game can produce and check the promise holds across all of them.
 */
public final class ElysiumTelegraph {

    private ElysiumTelegraph() {
    }

    /**
     * The most a blow may do with no warning at all.
     *
     * Six is three hearts on vanilla's scale: enough to be frightening, not
     * enough to kill a player at full health from nothing. Everything above it
     * has to be announced.
     */
    public static final float DANGEROUS = 6.0F;

    /** The shortest warning a dangerous blow may carry, in ticks. */
    public static final int MINIMUM_WINDUP = 10;

    /**
     * The longest, in ticks.
     *
     * A second and a quarter. Past that a mob is standing still long enough
     * that the fight stops being a fight — the player simply walks away, and
     * the bigger the monster the easier it becomes, which is backwards.
     */
    public static final int MAXIMUM_WINDUP = 25;

    /** Ticks of warning earned per point of damage above the threshold. */
    public static final float WINDUP_PER_POINT = 1.2F;

    /**
     * How long this blow must be announced for.
     *
     * Zero for anything at or under {@link #DANGEROUS}: a jab does not need a
     * telegraph, and giving it one would mean every skeleton in the world
     * pausing to announce three damage.
     */
    public static int windupFor(float damage) {
        if (!(damage > DANGEROUS)) {
            return 0;               // also catches NaN, which is never dangerous
        }
        // Clamped in float before it is ever an int, and compared with the
        // ceiling by a negated less-than so that infinity and any other
        // non-finite value land on the ceiling rather than falling through.
        //
        // This was an overflow, found by the harness rather than by reading:
        // rounding an unbounded damage gave Integer.MAX_VALUE, adding the
        // floor to it wrapped negative, and a negative wind-up reads as "no
        // warning needed" — so the single most dangerous blow the game can
        // produce was the one blow this system would have let through.
        float earned = MINIMUM_WINDUP + (damage - DANGEROUS) * WINDUP_PER_POINT;
        if (!(earned < MAXIMUM_WINDUP)) {
            return MAXIMUM_WINDUP;
        }
        return Math.max(MINIMUM_WINDUP, Math.round(earned));
    }

    /** Whether a blow of this size needs announcing at all. */
    public static boolean isDangerous(float damage) {
        return windupFor(damage) > 0;
    }

    /**
     * What a blow is allowed to do, given how long it was announced for.
     *
     * The whole promise in one function. A blow announced for long enough does
     * what it was going to do; one that was not is capped at the most a player
     * can be asked to take without warning.
     *
     * @param announcedFor ticks the warning has been showing, or 0 for none
     */
    public static float allowed(float damage, int announcedFor) {
        if (!(damage > DANGEROUS)) {
            return damage;
        }
        return announcedFor >= windupFor(damage) ? damage : DANGEROUS;
    }
}
