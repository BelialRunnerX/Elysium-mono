package com.elysium.colonies.rules;

/**
 * What a thing is worth here, today.
 *
 * <h2>Why a market and not a rate</h2>
 *
 * The exchange began as one number: eight of anything buys one of anything,
 * adjusted by the charter. That is enough to stop a town deadlocking and it is
 * not enough to be interesting, because every trade is the same trade. Nothing
 * about it rewards knowing the frontier.
 *
 * <p>A market says something the flat rate cannot: <em>this</em> is scarce
 * <em>here</em>. Iron is cheap on a world with iron in the ground and dear on
 * one without. Cinderveil wants coolant and Frostfall wants fuel, and the fact
 * that a player noticed is worth something. It is the same information the
 * charter already holds, priced.
 *
 * <h2>The shape of a price</h2>
 *
 * A base value, scaled by local scarcity, nudged by a drift that wanders and
 * returns. Everything multiplies, everything is bounded, and the spread
 * between what a market pays and what it charges is fixed — so a player cannot
 * make money by selling something back to the market that they bought from it
 * a moment ago, which is the first thing anybody tries.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class MarketRules {

    private MarketRules() {
    }

    // ------------------------------------------------------------------
    // What something is worth at all
    // ------------------------------------------------------------------

    /** The value of an ordinary thing, in the market's own units. */
    public static final int BASE_VALUE = 100;

    /**
     * How much scarcity moves a price, at most.
     *
     * A thing the world produces is worth {@code 1 - SWING} of the base; a
     * thing nobody here has is worth {@code 1 + SWING}. Sixty per cent either
     * way, so the spread between the cheapest and dearest world for the same
     * commodity is four to one — enough that carrying cargo between worlds is
     * worth the trip, not so much that it outpays everything else in the game.
     */
    public static final float SWING = 0.6F;

    /**
     * The price of a commodity on a world, before drift.
     *
     * @param base    what the commodity is worth in general
     * @param local   whether this world produces it
     * @param demand  how badly this world wants it, in [0, 1]
     */
    public static int priceOf(int base, boolean local, float demand) {
        float want = Math.max(0.0F, Math.min(1.0F, demand));
        float scarcity = local ? -SWING : SWING * want;
        return Math.max(1, Math.round(Math.max(1, base) * (1.0F + scarcity)));
    }

    // ------------------------------------------------------------------
    // Drift
    // ------------------------------------------------------------------

    /**
     * How far a price wanders from where scarcity put it.
     *
     * A quarter. Enough that a player who learns a route finds it better some
     * weeks than others; not enough that the route stops being a route.
     */
    public static final float DRIFT = 0.25F;

    /** How many days a full wander takes. */
    public static final int DRIFT_PERIOD = 24;

    /**
     * Where the drift stands on a given day for a given commodity.
     *
     * A deterministic wave rather than a random walk, and deliberately so: a
     * random walk has to be stored, and a stored price is a price that a
     * player can only learn by visiting. A wave can be read off the day, which
     * means a market that has never been visited still has a history, and a
     * player who works out the period can plan around it. Knowing the frontier
     * ought to be worth something.
     *
     * @param seed a number that differs per commodity and per world
     */
    public static float driftAt(int day, int seed) {
        double phase = 2.0D * Math.PI
                * (Math.floorMod(day + seed * 7, DRIFT_PERIOD) / (double) DRIFT_PERIOD);
        return (float) (DRIFT * Math.sin(phase));
    }

    /** A price with its day's drift applied. Never below one. */
    public static int drifted(int price, int day, int seed) {
        return Math.max(1, Math.round(price * (1.0F + driftAt(day, seed))));
    }

    // ------------------------------------------------------------------
    // The spread
    // ------------------------------------------------------------------

    /**
     * The share of a price the market keeps.
     *
     * A third. A market that bought and sold at the same number would let
     * anybody turn a profit by trading with it twice, which is not a market,
     * it is a bug with a shopfront. A third is wide enough that round-tripping
     * always loses and narrow enough that an honest run between two worlds
     * still pays.
     */
    public static final float SPREAD = 1.0F / 3.0F;

    /** What the market charges for one of something. */
    public static int asking(int price) {
        return Math.max(1, Math.round(Math.max(1, price) * (1.0F + SPREAD / 2.0F)));
    }

    /** What the market pays for one of something. */
    public static int offering(int price) {
        return Math.max(1, Math.round(Math.max(1, price) * (1.0F - SPREAD / 2.0F)));
    }

    /** Whether selling something straight back turns a profit. It must not. */
    public static boolean roundTripPays(int price) {
        return offering(price) > asking(price);
    }

    // ------------------------------------------------------------------
    // What a colony can spend
    // ------------------------------------------------------------------

    /**
     * How much a town's surplus is worth to it as buying power.
     *
     * The market's own offer, so a colony and a player selling the same crate
     * of cobblestone get the same number. A colony that got a better price
     * than a player would make the player's trading pointless; a worse one
     * would make the colony's survival depend on the player being there.
     */
    public static long purseFrom(int count, int price) {
        return (long) Math.max(0, count) * offering(price);
    }

    /** How many of something a purse of this size buys. */
    public static int buys(long purse, int price) {
        int cost = asking(price);
        if (cost <= 0) {
            return 0;
        }
        long many = Math.max(0L, purse) / cost;
        return (int) Math.min(Integer.MAX_VALUE, many);
    }

    /** What buying that many costs. */
    public static long costOf(int count, int price) {
        return (long) Math.max(0, count) * asking(price);
    }
}
