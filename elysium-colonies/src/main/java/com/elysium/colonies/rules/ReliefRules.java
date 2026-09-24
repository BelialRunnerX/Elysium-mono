package com.elysium.colonies.rules;

/**
 * The charter grant, and the watchdog that notices a town has stopped.
 *
 * <h2>The two problems, which are the same problem</h2>
 *
 * A town trades its way out of shortages. A town with nothing has nothing to
 * trade with, so the first hut — the builder's hut, without which it cannot
 * build anything at all, including the builder's hut — is a thing it can never
 * reach. That is the bootstrap.
 *
 * <p>And the general case is worse, because it cannot be enumerated. A town
 * can stop for reasons nobody thought of: a builder walled in by its own wall,
 * a request for an item no recipe in the pack produces, a warehouse full of
 * cobblestone and nothing else, a blueprint that wants a block from a mod that
 * was removed. Writing a rule per cause is writing rules forever and still
 * missing the next one.
 *
 * <h2>The answer to both</h2>
 *
 * Watch the outcome rather than the causes. A town that has finished nothing
 * for {@link #PATIENCE} days is stuck, whatever the reason, and the remedy
 * escalates: first the market, then the grant, then a plain report that
 * something is wrong. Everything the grant hands over is free, and that is
 * paid for by it running out — {@link #GRANT_DAYS} of a town's life, once,
 * after which a settlement is on its own for good.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ReliefRules {

    private ReliefRules() {
    }

    // ------------------------------------------------------------------
    // The charter grant
    // ------------------------------------------------------------------

    /**
     * How long a settlement is entitled to be supplied for.
     *
     * A hundred colony days. This began at twelve, which was sized for the
     * bootstrap alone — builder up, one house, a farmer fed — and that turned
     * out to be the wrong problem. A colony on a frontier world is not short
     * of a fortnight's help; it is short of the decades of infrastructure the
     * Overworld had before it. The grant is what the Empire spends to put a
     * settlement somewhere nobody would otherwise live, and a hundred days is
     * a real founding charter rather than a starter kit.
     *
     * <p>It still ends, and that is what pays for its size: after it, nothing
     * is free again, ever, for that settlement. A player who wanted free
     * materials forever would have to keep founding colonies, and the
     * watchdog only hands anything over to a town that has genuinely stopped.
     */
    public static final int GRANT_DAYS = 100;

    /** Whether a settlement is still inside its founding grant. */
    public static boolean granted(int day, int foundedOn) {
        if (foundedOn < 0) {
            return false;                  // a town whose founding was never recorded
        }
        return day - foundedOn < GRANT_DAYS;
    }

    /**
     * The most a grant hands over in one day, in items.
     *
     * Sixty-four stacks. This was two, matched to the emergency exchange's own
     * budget on the theory that the two systems should be indistinguishable —
     * and that theory was wrong in a way that showed up as soon as a colony
     * needed a building rather than a crate. A MineColonies hut wants
     * thousands of blocks; a grant that moved a hundred and twenty-eight items
     * a day could not put up a single one inside its whole term, so the
     * "grant" was a rounding error on a warehouse.
     *
     * <p>Four thousand and ninety-six is a day's real supply run: enough to
     * finish a building, not enough to finish a town. It is bounded three
     * times over — by the day, by the grant's hundred days, and by the fact
     * that nothing is handed over at all unless the settlement is stuck and
     * waiting on that exact item.
     */
    public static final int GRANT_STACKS_PER_DAY = 64;

    /** And the same number in items, which is what the arithmetic works in. */
    public static final int GRANT_PER_DAY = GRANT_STACKS_PER_DAY * 64;

    /** How many of a wanted item a grant will cover today. */
    public static int granting(int wanted, int givenToday) {
        int left = Math.max(0, GRANT_PER_DAY - Math.max(0, givenToday));
        return Math.max(0, Math.min(Math.max(0, wanted), left));
    }

    // ------------------------------------------------------------------
    // The watchdog
    // ------------------------------------------------------------------

    /**
     * Days of no progress before a town counts as stuck.
     *
     * Four. A colony can reasonably spend two or three days on one building —
     * a builder walks, gathers and places — so three would cry wolf. Past
     * four, something is wrong that waiting will not fix.
     */
    public static final int PATIENCE = 4;

    /** What "progress" means: any of these having moved since yesterday. */
    public static boolean progressed(int builtBefore, int builtNow,
                                     long developmentBefore, long developmentNow,
                                     int citizensBefore, int citizensNow) {
        return builtNow != builtBefore
                || developmentNow != developmentBefore
                || citizensNow != citizensBefore;
    }

    /** How stuck a town is, in days, given when it last moved. */
    public static int stuckFor(int day, int lastProgress) {
        if (lastProgress < 0) {
            return 0;                      // never measured; not yet stuck
        }
        return Math.max(0, day - lastProgress);
    }

    /** Whether it has been long enough to do something about it. */
    public static boolean stuck(int day, int lastProgress) {
        return stuckFor(day, lastProgress) >= PATIENCE;
    }

    /** What the watchdog does about it. */
    public enum Relief {
        /** Nothing is wrong. */
        NONE,
        /** Buy what it is waiting for, whatever it costs. */
        MARKET,
        /** Give it what it is waiting for, if it is still entitled. */
        GRANT,
        /** Nothing left to try. Say so, once, and keep watching. */
        REPORT
    }

    /**
     * How the remedy escalates.
     *
     * One step per {@link #PATIENCE} days of continued silence, so a town that
     * unsticks itself never reaches the later steps and a town that does not
     * works through all of them in order. Trading first because it costs the
     * town something; the grant only when trading has been tried and the town
     * is still entitled to be helped; and after that a report, because a
     * settlement that cannot be unstuck by either is a settlement with a
     * problem this module did not foresee and should not pretend to fix.
     */
    public static Relief reliefFor(int day, int lastProgress, int foundedOn) {
        if (!stuck(day, lastProgress)) {
            return Relief.NONE;
        }
        int steps = stuckFor(day, lastProgress) / PATIENCE;
        if (steps <= 1) {
            return Relief.MARKET;
        }
        if (granted(day, foundedOn)) {
            return Relief.GRANT;
        }
        return Relief.REPORT;
    }

    /**
     * How many rounds of being stuck before the owner is told, grant or no grant.
     *
     * Four, so about a fortnight of a town finishing nothing. This exists
     * because the grant grew: when it ran for twelve days, "report once the
     * grant is spent" was a fortnight's wait and fine. At a hundred days it
     * would be three months of a colony sitting silent while free materials
     * were poured into a problem they were never going to fix — which is the
     * exact failure the watchdog was written to prevent, reintroduced by
     * making the charter generous.
     *
     * <p>So the report is no longer the <em>last</em> remedy, it is a
     * <em>parallel</em> one: the grant keeps arriving, and the player is told
     * anyway. Nothing about being helped means nobody should be told.
     */
    public static final int REPORT_AFTER = 4;

    /**
     * Whether a town has been stuck long enough that its owner should hear
     * about it, whatever else is being done for it.
     */
    public static boolean alsoReport(int day, int lastProgress) {
        return stuckFor(day, lastProgress) >= PATIENCE * REPORT_AFTER;
    }

    /**
     * Days between one report of a stuck town and the next.
     *
     * Seven. A player who has been told their colony is stuck does not need
     * telling again tomorrow, and a message that arrives every day is a
     * message that stops being read on the third.
     */
    public static final int BETWEEN_REPORTS = 7;

    /** Whether it is time to say so again. */
    public static boolean dueToReport(int day, int lastReported) {
        return day - lastReported >= BETWEEN_REPORTS;
    }
}
