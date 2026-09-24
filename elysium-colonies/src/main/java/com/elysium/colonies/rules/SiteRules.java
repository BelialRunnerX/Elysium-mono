package com.elysium.colonies.rules;

/**
 * Where a town puts the next thing it builds.
 *
 * <h2>The problem this solves</h2>
 *
 * MineColonies has no notion of a colony choosing a site. A building exists
 * because a player stood somewhere, pointed a build tool at the ground and
 * pressed a button; the colony itself has no opinion about where its next hut
 * should go. That is the last thing standing between a town and running
 * itself.
 *
 * <h2>The shape of the answer</h2>
 *
 * A ring search outward from the town hall on a fixed grid. A grid rather than
 * free placement because buildings need room around them and a town laid out
 * on a grid looks like a town rather than a scatter; outward from the hall
 * because a settlement grows from its centre, and because it means the first
 * things built are the closest and a returning player finds them.
 *
 * <p>Everything here is arithmetic on coordinates. Whether a particular square
 * is actually flat, free and inside the colony is a question about the world,
 * and is asked elsewhere.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class SiteRules {

    private SiteRules() {
    }

    /**
     * Blocks between one building's anchor and the next.
     *
     * Eleven. MineColonies' buildings are mostly nine to fifteen blocks across
     * at their upper levels, so this leaves room to grow without leaving a
     * town full of gaps — and an odd number so the grid never lines up with
     * chunk borders, which is where a town that has to be rebuilt always
     * wants to put something.
     */
    public static final int SPACING = 11;

    /** How far out the search will go before giving up, in grid steps. */
    public static final int RINGS = 8;

    /** The furthest a building will ever be placed from the hall. */
    public static int furthest() {
        return SPACING * RINGS;
    }

    /**
     * How many candidate squares there are in a ring.
     *
     * The perimeter of a square of side {@code 2r+1}, which is {@code 8r} for
     * any ring past the centre. Stated so the harness can check the search
     * covers every square exactly once — a search that visited a square twice
     * would place two buildings on top of each other given the right timing.
     */
    public static int squaresIn(int ring) {
        return ring <= 0 ? 1 : 8 * ring;
    }

    /** Every square the search will look at, over every ring. */
    public static int squaresSearched() {
        int total = 0;
        for (int ring = 0; ring <= RINGS; ring++) {
            total += squaresIn(ring);
        }
        return total;
    }

    /**
     * The x offset of the {@code index}-th square of a ring, in grid steps.
     *
     * The ring is walked as four sides. Index zero of every ring is its
     * north-west corner and the walk goes clockwise, which is arbitrary but
     * fixed — a town built twice from the same seed puts its huts in the same
     * places.
     */
    public static int offsetX(int ring, int index) {
        if (ring <= 0) {
            return 0;
        }
        int side = 2 * ring;
        int at = Math.floorMod(index, 8 * ring);
        if (at < side) {
            return -ring + at;               // north edge, west to east
        }
        if (at < 2 * side) {
            return ring;                     // east edge
        }
        if (at < 3 * side) {
            return ring - (at - 2 * side);   // south edge, east to west
        }
        return -ring;                        // west edge
    }

    /** The z offset of the same square. */
    public static int offsetZ(int ring, int index) {
        if (ring <= 0) {
            return 0;
        }
        int side = 2 * ring;
        int at = Math.floorMod(index, 8 * ring);
        if (at < side) {
            return -ring;                    // north edge
        }
        if (at < 2 * side) {
            return -ring + (at - side);      // east edge, north to south
        }
        if (at < 3 * side) {
            return ring;                     // south edge
        }
        return ring - (at - 3 * side);       // west edge, south to north
    }

    /** That square's offset from the hall, in blocks. */
    public static int blocksX(int ring, int index) {
        return offsetX(ring, index) * SPACING;
    }

    /** The same, in blocks, north to south. */
    public static int blocksZ(int ring, int index) {
        return offsetZ(ring, index) * SPACING;
    }

    /**
     * How far the ground under a candidate may differ from the hall's before
     * the site is refused.
     *
     * Six. A settlement that will build up a cliff face ends up with huts
     * nobody can walk between; one that insists on a billiard table never
     * builds anything in hills.
     */
    public static final int LEVEL_TOLERANCE = 6;

    /** Whether ground at this height is flat enough to build on. */
    public static boolean levelEnough(int hallY, int candidateY) {
        return Math.abs(hallY - candidateY) <= LEVEL_TOLERANCE;
    }
}
