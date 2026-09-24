package com.elysium.lib.works;

/**
 * How loud unregistered building is, and what it costs to be heard.
 *
 * <h2>The gap this closes</h2>
 *
 * Building inside a claim already feeds the Imperial file: every placement
 * raises a permanent Suspicion floor, capped at seventy so that building alone
 * can never make a player Hunted (see {@code ElysiumClaim}). Building
 * <em>outside</em> a claim cost nothing at all. A player could raise a factory
 * anywhere in the world and the Empire would never notice, while the player
 * who did the lawful thing and planted a beacon carried a file for it. That is
 * the incentive upside down.
 *
 * <h2>Registered works are filed; unregistered works are found</h2>
 *
 * The two are deliberately different shapes, so that registering is a real
 * choice rather than a strictly better or worse one:
 *
 * <pre>
 *                 registered (claim)          unregistered (works)
 *   record        permanent floor             none; a site that decays
 *   bound         never above 70              none: repeated notices can
 *                                             carry a player into Hunted
 *   shape         smooth, predictable         lumpy: silence, then an incident
 * </pre>
 *
 * A claim trades freedom for certainty. A works trades certainty for the
 * chance nobody looks. Neither dominates, and that is the design.
 *
 * <h2>What is loud</h2>
 *
 * Industry, not shelter. A dirt hut, a stone house and a wheat field weigh
 * nothing; the Empire is not interested in where you sleep. What weighs is a
 * working machine and a short, data-driven list of conspicuous vanilla blocks
 * — furnaces in banks, metal in quantity, a beacon. So the ordinary game is
 * untouched and the thing being taxed is the thing §14.2 taxes everywhere
 * else: extraction and industry.
 *
 * <h2>Why decay is computed and not ticked</h2>
 *
 * A site's weight is a pure function of what was built and how long ago, so
 * nothing needs to tick for an unloaded works to go quiet, and a harness can
 * walk the whole curve without a world. See {@link #decayed}.
 *
 * <p>Imports nothing, and must not: it is in {@code EXECUTED_PURE}.
 */
public final class ElysiumConspicuity {

    private ElysiumConspicuity() {
    }

    // ------------------------------------------------------------------
    // Weights
    // ------------------------------------------------------------------

    /** A working machine: any block with a block entity in a machine tag or an Elysium namespace. */
    public static final int WEIGHT_MACHINE = 10;

    /** A conspicuous block from the data-driven tag: a furnace, a metal block, a beacon. */
    public static final int WEIGHT_CONSPICUOUS = 3;

    /** Everything else. Walls, floors, fields and huts are not the Empire's business. */
    public static final int WEIGHT_NONE = 0;

    // ------------------------------------------------------------------
    // Sites
    // ------------------------------------------------------------------

    /**
     * The side of one site, in blocks.
     *
     * A works is a place, not a player: two people building one factory are
     * building one works, and a player with two bases has two. A hundred and
     * twenty-eight is two claim-radii across, so a works is about the size of
     * the claim that would register it.
     */
    public static final int CELL = 128;

    /** The site a block coordinate belongs to. Floor division, so negative coordinates do not fold onto zero. */
    public static int cellOf(int coordinate) {
        return Math.floorDiv(coordinate, CELL);
    }

    // ------------------------------------------------------------------
    // Being noticed
    // ------------------------------------------------------------------

    /**
     * The weight at which a site is noticed.
     *
     * Twelve machines put down in a hurry, or forty furnaces. A house with a
     * furnace and a smoker never comes close, and a solarpunk workshop of four
     * or five machines built over an evening decays faster than it grows —
     * see the harness, which proves both.
     */
    public static final int NOTICE_AT = 120;

    /**
     * The weight at which the builder is warned.
     *
     * Three quarters of the way. The row this feature answers says building
     * <em>visibly</em> raises Suspicion, and a notice that arrives with no
     * warning is visible only after it is too late to decide anything.
     */
    public static final int WARN_AT = NOTICE_AT * 3 / 4;

    /** Whether this placement took a site across the warning line without being noticed. */
    public static boolean warns(int before, int after) {
        return before < WARN_AT && after >= WARN_AT && after < NOTICE_AT;
    }

    /** Weight shed per minute of game time. */
    public static final int DECAY_PER_MINUTE = 2;

    /** Ticks in a game minute. */
    public static final long TICKS_PER_MINUTE = 20L * 60L;

    /**
     * What is left of a site after it is noticed.
     *
     * Half, not zero. Zero would make being noticed a reset — and a reset
     * would make the second incident exactly as far away as the first, which
     * is the opposite of an Empire paying attention. At half, a player who
     * keeps building is noticed again sooner.
     */
    public static final int RESIDUAL = NOTICE_AT / 2;

    /** Suspicion for the first notice at a site. */
    public static final int FIRST_NOTICE = 12;

    /** What each further notice at the same site adds. */
    public static final int ESCALATION = 4;

    /** The most one notice can cost, however many came before. */
    public static final int MOST_PER_NOTICE = 32;

    /**
     * The weight a site has after {@code elapsedTicks} of silence.
     *
     * Linear, floored at zero, and safe against a negative elapsed time
     * (a clock rolled back, a save moved between worlds), which is treated as
     * no time at all rather than as the site growing on its own.
     */
    public static int decayed(int weight, long elapsedTicks) {
        if (weight <= 0) {
            return 0;
        }
        if (elapsedTicks <= 0) {
            return weight;
        }
        // Past this, the multiplication below wraps negative, a negative
        // shed is subtracted, and the site GROWS from silence — the loudest
        // thing a corrupted timestamp could do. Found by the harness at
        // Long.MAX_VALUE. Anything that long is simply quiet.
        if (elapsedTicks > Long.MAX_VALUE / DECAY_PER_MINUTE) {
            return 0;
        }
        long shed = elapsedTicks * DECAY_PER_MINUTE / TICKS_PER_MINUTE;
        return shed >= weight ? 0 : (int) (weight - shed);
    }

    /**
     * Adds a placement's weight to a site, saturating rather than wrapping.
     *
     * A hand-edited save or a mod that places a million machines must not be
     * able to overflow a site into a negative weight — a negative site is a
     * silent one, and the loudest works in the world would become the only
     * one the Empire could never find.
     */
    public static int added(int weight, int by) {
        long sum = (long) Math.max(0, weight) + (long) Math.max(0, by);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Whether a site at this weight is noticed. */
    public static boolean noticed(int weight) {
        return weight >= NOTICE_AT;
    }

    /**
     * The Suspicion a notice costs, given how many came before it at this
     * site.
     *
     * Escalating and capped. The cap is per notice, never on the meter: that
     * is what lets a player who keeps building an unregistered works climb past
     * the seventy a claim could never push them over.
     */
    public static int suspicionFor(int priorNotices) {
        long cost = (long) FIRST_NOTICE
                + (long) ESCALATION * Math.max(0, priorNotices);
        return (int) Math.min(MOST_PER_NOTICE, cost);
    }

    /**
     * A placement's weight after the builder's doctrine has its say.
     *
     * Rounded up while it is above zero, the same rule the ore handler uses:
     * a multiplier that could round a machine down to nothing would make quiet
     * industry free. And a block that weighs nothing stays at nothing — a
     * doctrine changes how loud you are, it does not make a wall a machine.
     */
    public static int scaled(int weight, float doctrineScale) {
        if (weight <= 0) {
            return 0;
        }
        if (!(doctrineScale > 0.0F)) {        // NaN and non-positive alike
            return 1;
        }
        double scaled = Math.ceil(weight * (double) doctrineScale);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, scaled));
    }
}
