package com.elysium.colonies.rules;

/**
 * The spaceport: what a colony can ship, and what it takes to ship it.
 *
 * <h2>The problem this solves</h2>
 *
 * A colony's only way of getting what it lacks is the emergency exchange —
 * which is, by design, a bad deal taken in a crisis and capped at two stacks a
 * day. That is the correct shape for a lifeline and the wrong shape for an
 * economy. A settlement that has been running for a year and produces a
 * surplus of four materials has no way to turn that into the one thing it
 * cannot make, except badly, in small amounts, as though it were still
 * desperate.
 *
 * <p>MineColonies has no answer because MineColonies has no outside: every
 * colony is an island and the only import is a player with a full inventory.
 * The spaceport is the outside.
 *
 * <h2>A space elevator, and why the shape matters</h2>
 *
 * It is one building in the sense that matters: an anchor on the ground, an
 * unbroken tether, a counterweight at the top, and nothing in the way of the
 * sky. Cut the tether anywhere and the port stops — which is the whole reason
 * to make it a monolith rather than a hut. A hut is a thing you place; a
 * hundred-block column is a thing you commit to, and it should behave like
 * infrastructure: expensive to build, visible from a distance, and capable of
 * being sabotaged.
 *
 * <p>Throughput climbs with height, linearly and without a ceiling of its own,
 * because build height is already a ceiling and a second one would be a number
 * a player has to look up.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class PortRules {

    private PortRules() {
    }

    // ------------------------------------------------------------------
    // The structure
    // ------------------------------------------------------------------

    /**
     * The shortest tether that is a space elevator rather than a mast.
     *
     * Eighty blocks. From a settlement on a plain that is most of the way to
     * the build limit, which is the point: a spaceport should be the tallest
     * thing for miles and should cost a real quantity of material. Shorter and
     * it is a radio tower; much longer and no colony below Y-80 could ever
     * build one.
     */
    public static final int MINIMUM_TETHER = 80;

    /** Whether a column of this height is a spaceport at all. */
    public static boolean tall(int tether) {
        return tether >= MINIMUM_TETHER;
    }

    // ------------------------------------------------------------------
    // The flare
    // ------------------------------------------------------------------
    //
    // A space elevator is not a column; it is a column somebody had to hold
    // up, and everything that makes one read as infrastructure is in the legs
    // splayed out underneath it. The curve they follow is written here rather
    // than in the generator that draws it, because three separate things need
    // to agree about it: the blueprint a player raises, the reading that
    // decides whether a port is braced, and the validator that checks the two
    // are the same building.

    /** How many legs a full brace has. Four, on the diagonals. */
    public static final int LEGS = 4;

    /** How far out a leg stands where it meets the deck. */
    public static final int FOOT = 12;

    /** And how close to the shaft it has gathered by the top. */
    public static final int WAIST = 3;

    /**
     * The height the legs finish gathering at.
     *
     * Three times the width of the deck they stand on. The first version of
     * this was as tall as it was wide, and it rendered as a tent: what makes
     * the shape in the photograph read as something holding a weight up is
     * that it is far taller than it is broad, so the legs are long and thin
     * rather than short and heavy.
     */
    public static final int GATHER = 36;

    /**
     * How sharply the legs bend.
     *
     * Above one, so most of the bending happens near the ground and the top
     * two thirds stand nearly vertical. That is the difference between a flare
     * and a cone, and it is the whole silhouette.
     */
    public static final float BEND = 2.0F;

    /**
     * Where a leg's centre is, this far above the deck.
     *
     * Clamped at both ends rather than extrapolated: below the deck there is
     * no leg, and above the gather the leg is part of the bundle and stays
     * where the bundle is.
     */
    public static float legRadius(int height) {
        if (height >= GATHER) {
            return WAIST;
        }
        float share = (GATHER - Math.max(0, height)) / (float) GATHER;
        return WAIST + (FOOT - WAIST) * (float) Math.pow(share, BEND);
    }

    /** How far off that radius a leg may be found and still count. */
    public static final int LEG_TOLERANCE = 3;

    /**
     * Where a leg stands, as an offset from the shaft.
     *
     * On the diagonals, starting at forty-five degrees, so the flare reads the
     * same from every side. A player standing square to a port built on the
     * axes would see two legs and a gap; on the diagonals they see all four.
     *
     * @param leg    which of the {@link #LEGS}
     * @param height how far above the deck
     */
    public static int legX(int leg, int height) {
        return Math.round(legRadius(height) * (float) Math.cos(angleOf(leg)));
    }

    /** The other half of the same offset. */
    public static int legZ(int leg, int height) {
        return Math.round(legRadius(height) * (float) Math.sin(angleOf(leg)));
    }

    private static double angleOf(int leg) {
        return Math.PI / 4.0 + Math.floorMod(leg, LEGS) * (2.0 * Math.PI / LEGS);
    }

    /**
     * The heights a reading looks at to decide whether a leg is there.
     *
     * Four samples rather than the whole column, because a leg is twenty-four
     * blocks of stone and asking about all of it is eight hundred block reads
     * for a question four answer. Spread so that a stub at the bottom and a
     * ring at the top both fail: a brace is a leg that goes all the way up.
     */
    public static int[] legSamples() {
        return new int[]{Math.max(1, GATHER / 12), GATHER / 4, GATHER / 2,
                GATHER * 3 / 4};
    }

    /**
     * How much a braced port ships beyond a bare one, per leg, per day.
     *
     * A stack. Four legs is a second port's worth of capacity for a building
     * that costs two thousand blocks — which is the right trade for a
     * monument: it is never the efficient way to get capacity, and it is
     * always worth having once you have decided to build the thing anyway.
     *
     * It is added rather than multiplied, so it neither scales away to nothing
     * on a tall port nor turns a short one into a trade empire.
     */
    public static final int PER_LEG = 64;

    // ------------------------------------------------------------------
    // What it can move
    // ------------------------------------------------------------------

    /**
     * Items a day at the shortest legal height.
     *
     * Four stacks. Twice what the emergency exchange will move, so a port is
     * immediately worth having; not so much that the first one a player builds
     * ends their material problems forever.
     */
    public static final int BASE_THROUGHPUT = 256;

    /**
     * Extra items a day per block of tether above the minimum.
     *
     * Four. A port built to the build limit from sea level runs at roughly
     * three times the base, which is a real reward for a real amount of
     * building and still not an infinite supply of anything.
     */
    public static final int PER_BLOCK = 4;

    /**
     * How much a port of this height moves in a day.
     *
     * Linear and with no cap of its own — the world's build height is the cap,
     * and stating a second one would be a number a player has to discover
     * rather than reason about. A column below the minimum moves nothing,
     * which is the same as not having built one.
     */
    public static int throughput(int tether) {
        return throughput(tether, 0);
    }

    /**
     * How much a port of this height, braced by this many legs, moves in a day.
     *
     * The legs are worth capacity because that is what a brace is for: the
     * column carries the load and the legs carry the column. A port with no
     * legs still works, so a player who wanted a mast has one; a player who
     * built the monument gets paid for it.
     */
    public static int throughput(int tether, int legs) {
        if (!tall(tether)) {
            return 0;
        }
        int braced = Math.max(0, Math.min(LEGS, legs));
        return BASE_THROUGHPUT + PER_BLOCK * (tether - MINIMUM_TETHER)
                + PER_LEG * braced;
    }

    /**
     * How that day's throughput is divided between sending and receiving.
     *
     * Half each. A port that could spend its whole capacity importing would
     * let a colony live entirely on its treasury; one that could spend it all
     * exporting would strip a warehouse in a week. Splitting it means a port
     * is always doing both, which is what a shipping and receiving office is.
     */
    public static int outbound(int tether) {
        return outbound(tether, 0);
    }

    public static int outbound(int tether, int legs) {
        return throughput(tether, legs) / 2;
    }

    /** The other half. */
    public static int inbound(int tether) {
        return inbound(tether, 0);
    }

    public static int inbound(int tether, int legs) {
        return throughput(tether, legs) - outbound(tether, legs);
    }

    // ------------------------------------------------------------------
    // Freight between two colonies
    // ------------------------------------------------------------------

    /**
     * What two colonies with ports save by dealing with each other.
     *
     * The market keeps a third of every price as its spread; two settlements
     * shipping directly keep all of it, splitting the difference at the mid
     * price. So a second spaceport is worth building not because it unlocks
     * anything but because it makes the first one pay better — which is the
     * right reason for a player to build a second of anything.
     */
    public static int midPrice(int asking, int offering) {
        return Math.max(1, (Math.max(1, asking) + Math.max(1, offering)) / 2);
    }

    /**
     * What a freight run of this size costs the colony receiving it.
     *
     * The mid price, times the count, and nothing else: no spread, because
     * there is no market standing between two colonies, and no distance term,
     * because {@link #REACHES_EVERY_WORLD} already says the elevator does not
     * care. A long is returned for the same reason a treasury is one — a
     * hundred-block port running for a year would overflow an int.
     */
    public static long freightCost(int count, int mid) {
        return (long) Math.max(0, count) * Math.max(1, mid);
    }

    /**
     * How many a treasury of this size can have shipped in.
     *
     * The inverse of {@link #freightCost}, and it has to stay the inverse: a
     * colony that ordered what it could not pay for would either be given
     * goods for nothing or leave a sister colony's warehouse short. Whole
     * units only — half a plank has never crossed anything.
     */
    public static int freightBuys(long treasury, int mid) {
        long many = Math.max(0L, treasury) / Math.max(1, mid);
        return (int) Math.min(Integer.MAX_VALUE, many);
    }

    /**
     * How far a freight run reaches.
     *
     * Everywhere. A space elevator's whole claim is that it puts cargo above
     * the atmosphere, and once it is up there the distance between two worlds
     * costs time rather than fuel — time a colony has. A limit here would be a
     * rule invented to create difficulty rather than one that follows from
     * anything.
     */
    public static final boolean REACHES_EVERY_WORLD = true;

    /**
     * How much of a single freight run one port will give up.
     *
     * Half its outbound capacity. A port that would empty itself supplying a
     * sister colony is a port that starves its own town to feed another, and
     * the player who built both would have to choose which one to switch off.
     */
    public static int spareFor(int tether) {
        return outbound(tether) / 2;
    }

    /** The same, for a braced port. */
    public static int spareFor(int tether, int legs) {
        return outbound(tether, legs) / 2;
    }

    // ------------------------------------------------------------------
    // The treasury
    // ------------------------------------------------------------------

    /**
     * The most a colony's treasury will hold.
     *
     * Enough to buy a full day's inbound capacity at a poor price, several
     * times over. A treasury with no ceiling would let a colony bank a decade
     * of exports and then buy an entire tech tree in an afternoon, which is
     * the same failure the development bank has a ceiling to avoid.
     */
    public static long ceiling(int tether) {
        return (long) inbound(tether) * 400L;
    }

    /** A treasury after a day's takings, capped. */
    public static long banked(long held, long earned, int tether) {
        return Math.min(ceiling(tether), Math.max(0L, held) + Math.max(0L, earned));
    }

    /**
     * The share of a sale a port keeps for its own upkeep.
     *
     * None. The elevator was the cost; running it is not. Stated as a constant
     * rather than left implicit so that a future change to it is a change
     * somebody made on purpose.
     */
    public static final float UPKEEP = 0.0F;
}
