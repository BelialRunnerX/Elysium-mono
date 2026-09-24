package com.elysium.space.world;

/**
 * What it costs to send a crate to another world, and how long it takes.
 *
 * <h2>Why rockets are the freight system and the waygate is not</h2>
 *
 * Because the two questions are different. A player wants to get somewhere and
 * come back, repeatedly, without thinking about it — so the waygate is free to
 * use once built, and that is the right answer for travel. Cargo is the
 * opposite: moving a thousand items between worlds should be a decision with a
 * cost and a delay, because a logistics system where distance is free is a
 * logistics system with nothing to solve.
 *
 * <p>So the rockets that were the only way to travel become the way goods
 * travel. They keep everything that made them interesting — fuel, distance,
 * the route table — and lose the one thing that made them tiresome, which is
 * having to ride in one every time you wanted to be somewhere else.
 *
 * <h2>The three numbers</h2>
 *
 * <ol>
 *   <li><b>Fuel.</b> The route's own cost, from {@link SpaceRoute}, scaled by
 *       how full the rocket is — and scaled so that a full rocket is far
 *       cheaper per stack than an empty one. Economies of scale are what make
 *       a logistics system worth optimising.</li>
 *   <li><b>Time.</b> Longer for further, on a curve that flattens, so the
 *       Bastion is a real wait and nothing is an unbounded one.</li>
 *   <li><b>Room.</b> One rocket, one load, and a terminal that can only have
 *       so many in the air at once.</li>
 * </ol>
 *
 * <p>Pure: the JDK and {@link SpaceRoute}, which is itself pure.
 */
public final class FreightRules {

    private FreightRules() {
    }

    // ------------------------------------------------------------------
    // What fits
    // ------------------------------------------------------------------

    /**
     * How many stacks a terminal holds, and therefore what one rocket lifts.
     *
     * Twenty-seven: a chest. Deliberately a familiar number rather than a
     * tuned one — a player already knows how much a chest is, and a freight
     * terminal that held some other amount would be a number to look up.
     */
    public static final int SLOTS = 27;

    /**
     * How much fuel a terminal holds.
     *
     * Eight buckets, which is more than the longest run costs empty and less
     * than a full load to the Bastion. So a terminal is always good for a
     * short hop unattended, and a long haul is something you fuel for.
     */
    public static final int TANK_MB = 8000;

    /**
     * How many rockets one terminal may have in the air at once.
     *
     * Three. Enough that a supply line does not stall behind a single slow
     * run; few enough that a terminal is a place with a queue rather than an
     * infinite pipe, which is the thing that would make distance stop
     * mattering again.
     */
    public static final int IN_FLIGHT = 3;

    // ------------------------------------------------------------------
    // Fuel
    // ------------------------------------------------------------------

    /**
     * How much of the route's fuel an empty rocket burns.
     *
     * All of it. A cargo rocket is the same rocket; flying it somewhere costs
     * what flying there costs, and the cargo is what is added to that.
     */
    public static final float EMPTY_SHARE = 1.0F;

    /**
     * And how much each stack aboard adds, as a share of the empty cost.
     *
     * Fifteen per cent. A full rocket burns about five times an empty one and
     * carries twenty-seven times as much, so the cost per stack falls by
     * roughly six to one between a single crate and a full load. That is the
     * whole reason to build a logistics system rather than carrying things
     * yourself, and it is the number to change if freight ever feels pointless.
     */
    public static final float PER_STACK = 0.15F;

    /**
     * What a run costs, in millibuckets.
     *
     * @param routeMb what {@link SpaceRoute#fuelFor} says the trip takes
     * @param stacks  how many slots of the rocket are full
     */
    public static int fuelFor(int routeMb, int stacks) {
        if (routeMb < 0) {
            return -1;
        }
        float weighted = EMPTY_SHARE + PER_STACK * Math.max(0, Math.min(SLOTS, stacks));
        return Math.max(1, Math.round(Math.max(1, routeMb) * weighted));
    }

    /** What one stack of a full load costs, for a screen that wants to say so. */
    public static int perStack(int routeMb, int stacks) {
        int full = fuelFor(routeMb, stacks);
        return full / Math.max(1, stacks);
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /** The shortest a run can take, however close the destination. */
    public static final int LEAST_TICKS = 20 * 20;

    /** And the longest, however far. */
    public static final int MOST_TICKS = 20 * 240;

    /**
     * The distance at which a run takes half the span between those two.
     *
     * Five thousand megametres, which is between the Pale Moon and Cinderveil.
     * The curve is {@code gap / (gap + SCALE)} — the same shape this project
     * uses everywhere a number has to grow without ever arriving — so a longer
     * trip is always slower and no trip is ever unbounded.
     */
    public static final double HALFWAY = 5_000.0D;

    /** How long a run of this distance takes, in ticks. */
    public static int flightTicks(double gap) {
        double reach = Math.max(0.0D, gap);
        double share = reach / (reach + HALFWAY);
        return LEAST_TICKS + (int) Math.round((MOST_TICKS - LEAST_TICKS) * share);
    }

    /** How long that is in seconds, for something a player reads. */
    public static int flightSeconds(double gap) {
        return flightTicks(gap) / 20;
    }

    // ------------------------------------------------------------------
    // Arriving
    // ------------------------------------------------------------------

    /**
     * How often a landing is attempted when the destination has no room.
     *
     * Every five seconds. A rocket that cannot unload holds rather than
     * dropping its cargo on the floor or deleting it: a logistics system that
     * loses goods when a chest fills is a logistics system nobody will trust
     * with anything they care about.
     */
    public static final int HOLD_TICKS = 20 * 5;

    /** Whether a shipment launched then is due now. */
    public static boolean landed(long launchedAt, long now, int flight) {
        return now - launchedAt >= flight;
    }

    /** How long a shipment still has, in seconds, or zero once it is down. */
    public static int remaining(long launchedAt, long now, int flight) {
        long left = flight - (now - launchedAt);
        return left <= 0L ? 0 : (int) (left / 20L) + 1;
    }
}
