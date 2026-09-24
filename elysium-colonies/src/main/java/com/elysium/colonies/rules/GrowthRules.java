package com.elysium.colonies.rules;

/**
 * A town that gets on with it while you are away.
 *
 * <h2>The problem this solves</h2>
 *
 * A colony only ever improves when a player is standing in it clicking on
 * things. Every upgrade is a menu the owner opens, and the town between visits
 * is a town on pause — which is the opposite of what a settlement is for. The
 * appeal of building a place is coming back to it; coming back to exactly what
 * you left is coming back to a save file.
 *
 * <h2>What it does, and the limit on it</h2>
 *
 * A town accumulates <em>development</em> from the work its people actually
 * do, and spends it asking for its own building upgrades. It is the colony
 * that files the request; the builder still has to build it and the warehouse
 * still has to hold the materials, so nothing appears from nowhere and a town
 * with no resources gets no further than a town with no resources should.
 *
 * <p>And it stops. {@link #UNAIDED_LEVEL} is as far as a town raises itself:
 * enough to become a working settlement, not enough to become a great one. A
 * colony that could reach its ceiling alone would be a colony with no reason
 * for its owner to come home, which is a worse failure than one that never
 * moves.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class GrowthRules {

    private GrowthRules() {
    }

    // ------------------------------------------------------------------
    // Earning it
    // ------------------------------------------------------------------

    /**
     * Development a single content, unhurt, unpractised citizen earns in a
     * Minecraft day working on a world that neither helps nor hinders.
     *
     * Ten, so the numbers below can be read as "citizen-days" — the cost of
     * the first upgrade is a fortnight for a village of four, and an afternoon
     * for a town of forty. That relationship is the design; the unit is
     * arbitrary.
     */
    public static final int PER_CITIZEN_DAY = 10;

    /**
     * What a town of this size earns in a day.
     *
     * @param citizens how many people live there
     * @param output   the average of {@code CitizenRules.output} across them,
     *                 which already folds in stress, wounds, practice and what
     *                 the world is like
     */
    public static int perDay(int citizens, float output) {
        if (citizens <= 0 || output <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.round(citizens * PER_CITIZEN_DAY * output));
    }

    /**
     * Idle people earn nothing.
     *
     * Not a penalty — simply that development is a measure of work done, and a
     * citizen with no job has done none. Counted separately from stress, which
     * also punishes unemployment, because the two say different things: one is
     * that they are unhappy about it and the other is that the town has not
     * got any further because of it.
     */
    public static int working(int citizens, int unemployed) {
        return Math.max(0, citizens - Math.max(0, unemployed));
    }

    // ------------------------------------------------------------------
    // Spending it
    // ------------------------------------------------------------------

    /**
     * What the first upgrade costs, in the units above.
     *
     * Four hundred: forty citizen-days, so a village of four earns it in ten
     * days and a town of twenty in two. Slow enough to be a thing you notice
     * having happened, fast enough that a returning player sees a difference.
     */
    public static final int FIRST_UPGRADE = 400;

    /**
     * How the cost climbs with the level being built.
     *
     * Squared, matching the shape of everything a colony already charges for
     * levels: the fourth level of a building is not four times the first, it
     * is many times it, and a town that grew linearly would go from hamlet to
     * capital in one uninterrupted slide.
     */
    public static long costOf(int targetLevel) {
        int level = Math.max(1, targetLevel);
        return (long) FIRST_UPGRADE * level * level;
    }

    /**
     * The highest level a town will raise a building to unaided.
     *
     * Three of MineColonies' five. A town can make itself a working
     * settlement — housed, fed, defended — and the two levels that turn a
     * working settlement into a capital are the owner's to build. That is the
     * line between a place that looks after itself and a place that does not
     * need you.
     */
    public static final int UNAIDED_LEVEL = 3;

    /** Whether a town will raise a building at this level on its own. */
    public static boolean willRaise(int currentLevel, int maxLevel) {
        if (currentLevel < 1) {
            return false;                 // not built yet; that is a player's decision
        }
        if (currentLevel >= maxLevel) {
            return false;                 // already as good as it gets
        }
        return currentLevel + 1 <= UNAIDED_LEVEL;
    }

    /** Whether a town has saved enough to raise a building to this level. */
    public static boolean affords(long development, int targetLevel) {
        return development >= costOf(targetLevel);
    }

    /** What is left after paying for it. */
    public static long spend(long development, int targetLevel) {
        return Math.max(0L, development - costOf(targetLevel));
    }

    // ------------------------------------------------------------------
    // How often it may
    // ------------------------------------------------------------------

    /**
     * The fewest days between two self-ordered upgrades.
     *
     * Two. Development is capped by nothing, so a very large town would
     * otherwise queue every upgrade it can afford in the same tick, and a
     * player would come home to a builder with forty work orders and a
     * warehouse stripped bare. One thing at a time, deliberately.
     */
    public static final int DAYS_BETWEEN = 2;

    /** Whether enough time has passed since the last self-ordered upgrade. */
    public static boolean patient(int day, int lastDay) {
        return day - lastDay >= DAYS_BETWEEN;
    }

    /**
     * The ceiling on saved development.
     *
     * Enough for one upgrade at the unaided level and no more. A town that
     * banked development for a year and then spent it all at once would make
     * the {@link #DAYS_BETWEEN} rule cosmetic, and would mean the reward for
     * ignoring a colony is the same as the reward for tending it.
     */
    public static long ceiling() {
        return costOf(UNAIDED_LEVEL);
    }

    /** Development after a day's earnings, capped. */
    public static long banked(long held, int earned) {
        return Math.min(ceiling(), Math.max(0L, held) + Math.max(0, earned));
    }
}
