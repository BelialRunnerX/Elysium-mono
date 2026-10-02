package com.elysium.wilds.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * The way into the Vale. The JDK and nothing else.
 *
 * <h2>Why there is one</h2>
 *
 * The Vale shipped with fifteen biomes, twenty-eight creatures and two almanac
 * quests, and the only way in was an operator's command. Its own bestiary reads
 * it as rings out from "the crossing a portal drops you nearest", and there was
 * no portal, so a survival player could have none of it.
 *
 * <p>So: a ring of Vale Frames, built in the shape of a Nether portal, lit by
 * using the Surveyor's Compass on it. The compass is not used up. It is this
 * module's way-finding tool, and a ring already costs three crafts.
 *
 * <h2>The shape, and why it is flooded rather than measured</h2>
 *
 * An upright rectangle of frames round an empty opening at least
 * {@value #LEAST_ACROSS} across and {@value #LEAST_UP} up, and at most
 * {@value #MOST} either way: the Nether portal's, which everybody knows, and
 * the rift's. The corners are not needed, so the smallest ring is ten frames.
 *
 * <p>The opening is found by flooding it four ways in the ring's own plane. A
 * flood refuses an L-shape, a gap in the frame and a block left inside, all of
 * which a width and a height would accept and then light as a gate with a hole
 * in it. The flood starts from every cell beside the frame that was used,
 * corner to corner as well, so any frame of a ring lights it: a side, the sill,
 * the lintel or a corner. The rift steps into its opening only up, down and out
 * of its own plane, so a rift struck on a side is refused as no frame at all;
 * this is the same flood without that.
 *
 * <h2>The other side</h2>
 *
 * A way in puts a player in the gentlest crossing of the Vale, the one its
 * bestiary counts every ring out from, beside a ring built there to come back
 * through. The Vale side is filed under the ring that led to it, so it is
 * searched for once and built once, and everybody through one ring arrives at
 * the same place.
 *
 * <p>And it is built on dry ground. The search finds a biome, not a block, so
 * the nearest column of the verdant vale is as likely a lake as a meadow; the
 * side goes to the nearest dry column within {@link #DRY_SEARCH_BLOCKS}, and
 * when all of that is sea, to the nearest shore the eight compass lines meet
 * within {@link #FAR_DRY_SEARCH_BLOCKS}. Dry is nothing flowing at the top of
 * the column and a top that holds a player up. And the first dry column met
 * is at the waterline, so from it the ring is walked on along the line it was
 * found on, up to {@link #SETTLE_IN_BLOCKS}, until its whole footing, the
 * sill and both landings, is dry.
 */
public final class PortalRules {

    private PortalRules() {
    }

    // ==================================================================
    // The ring
    // ==================================================================

    /** The narrowest opening a ring may close round: a Nether portal's. */
    public static final int LEAST_ACROSS = 2;

    /** And the shortest. */
    public static final int LEAST_UP = 3;

    /** The widest and the tallest. */
    public static final int MOST = 21;

    /**
     * Frames one craft makes. Four mossy stone bricks, four moss blocks and an
     * ender pearl give four, the rift frame's recipe in the Vale's stone. The
     * smallest ring is {@link #leastFrames()} frames, so three crafts.
     */
    public static final int FRAMES_PER_CRAFT = 4;

    /** What one cell of a ring's plane holds, as far as a ring is concerned. */
    public enum Cell {
        /** A Vale Frame. */
        FRAME,
        /** Air, or a gate already lit: something a gate may fill. */
        OPEN,
        /** Anything else, which ends the ring. */
        OTHER
    }

    /** The plane a ring stands in, read one cell at a time. */
    @FunctionalInterface
    public interface Plane {

        /** What is at this cell: across runs along the ring, up runs up. */
        Cell at(int across, int up);
    }

    /** One cell of a plane. */
    public record Spot(int across, int up) {
    }

    /**
     * A ring that closes: its opening, and the one cell it is known by.
     *
     * <p>The anchor is the lowest cell of the opening, then the one least
     * across. It has to come out the same from every cell of the ring, because
     * the Vale side a ring leads to is filed under it. An anchor taken from
     * whichever block was touched would file one ring under as many names as it
     * has blocks, and every trip would search and build again.
     *
     * @param opening every cell of the opening, lowest first
     * @param anchor  the cell the ring is known by
     * @param wide    how many cells across the opening is
     * @param tall    how many cells up
     */
    public record Ring(List<Spot> opening, Spot anchor, int wide, int tall) {
    }

    /** The four ways a flood goes. */
    private static final int[][] SIDEWAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** And the four more a used frame looks, so that a corner lights its ring too. */
    private static final int[][] CORNERWAYS = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /**
     * The ring a used cell belongs to, or null.
     *
     * <p>An open cell floods from itself. A frame floods from each open cell
     * beside it, sideways first and then corner to corner, because a frame
     * borders the outside of its ring as well as the inside, and the outside is
     * a flood that fails.
     */
    public static Ring around(Plane plane, int across, int up) {
        Objects.requireNonNull(plane, "no plane to look in");
        if (plane.at(across, up) == Cell.OPEN) {
            return enclosing(plane, across, up);
        }
        for (int[][] ways : new int[][][] {SIDEWAYS, CORNERWAYS}) {
            for (int[] way : ways) {
                int nextAcross = across + way[0];
                int nextUp = up + way[1];
                if (plane.at(nextAcross, nextUp) != Cell.OPEN) {
                    continue;
                }
                Ring ring = enclosing(plane, nextAcross, nextUp);
                if (ring != null) {
                    return ring;
                }
            }
        }
        return null;
    }

    /**
     * The opening an open cell is inside, if it is a ring's, or null.
     *
     * <p>Flooded four ways, stopped by frames and refused by anything else. It
     * is refused too once it holds more cells than the largest opening there
     * is, because an opening with no frame round it is the whole sky, and a
     * flood of the sky does not stop by itself.
     */
    public static Ring enclosing(Plane plane, int across, int up) {
        Objects.requireNonNull(plane, "no plane to look in");
        if (plane.at(across, up) != Cell.OPEN) {
            return null;
        }
        Set<Spot> opening = new HashSet<>();
        Queue<Spot> queue = new ArrayDeque<>();
        Spot start = new Spot(across, up);
        opening.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Spot at = queue.poll();
            if (opening.size() > MOST * MOST) {
                return null;
            }
            for (int[] way : SIDEWAYS) {
                Spot next = new Spot(at.across() + way[0], at.up() + way[1]);
                Cell cell = plane.at(next.across(), next.up());
                if (cell == Cell.FRAME) {
                    continue;
                }
                if (cell != Cell.OPEN) {
                    return null;
                }
                if (opening.add(next)) {
                    queue.add(next);
                }
            }
        }

        int leastAcross = Integer.MAX_VALUE;
        int leastUp = Integer.MAX_VALUE;
        int mostAcross = Integer.MIN_VALUE;
        int mostUp = Integer.MIN_VALUE;
        for (Spot spot : opening) {
            leastAcross = Math.min(leastAcross, spot.across());
            leastUp = Math.min(leastUp, spot.up());
            mostAcross = Math.max(mostAcross, spot.across());
            mostUp = Math.max(mostUp, spot.up());
        }
        int wide = mostAcross - leastAcross + 1;
        int tall = mostUp - leastUp + 1;
        if (wide < LEAST_ACROSS || tall < LEAST_UP || wide > MOST || tall > MOST) {
            return null;
        }
        // A flood of a rectangle is exactly as many cells as the rectangle.
        // Anything else is closed in and is not one: an L, or a frame left
        // standing in the middle of the opening.
        if (opening.size() != wide * tall) {
            return null;
        }
        List<Spot> ordered = new ArrayList<>(opening);
        ordered.sort(Comparator.comparingInt(Spot::up).thenComparingInt(Spot::across));
        return new Ring(List.copyOf(ordered), ordered.get(0), wide, tall);
    }

    /** Frames in the smallest ring: its four sides, and no corners. */
    public static int leastFrames() {
        return 2 * (LEAST_ACROSS + LEAST_UP);
    }

    /** How many crafts the smallest ring takes. */
    public static int leastCrafts() {
        return (leastFrames() + FRAMES_PER_CRAFT - 1) / FRAMES_PER_CRAFT;
    }

    // ==================================================================
    // The ring the Vale builds
    // ==================================================================

    /**
     * The frames of the ring built where a player arrives: the smallest ring,
     * with its corners, placed so its opening starts at (0, 0). The corners are
     * only for looks; the harness proves the ring lights without them.
     */
    public static List<Spot> builtFrames() {
        List<Spot> frames = new ArrayList<>();
        for (int up = -1; up <= LEAST_UP; up++) {
            for (int across = -1; across <= LEAST_ACROSS; across++) {
                if (!inBuiltOpening(across, up)) {
                    frames.add(new Spot(across, up));
                }
            }
        }
        return List.copyOf(frames);
    }

    /** Its opening, lowest first. */
    public static List<Spot> builtOpening() {
        List<Spot> opening = new ArrayList<>();
        for (int up = 0; up < LEAST_UP; up++) {
            for (int across = 0; across < LEAST_ACROSS; across++) {
                opening.add(new Spot(across, up));
            }
        }
        return List.copyOf(opening);
    }

    private static boolean inBuiltOpening(int across, int up) {
        return across >= 0 && across < LEAST_ACROSS && up >= 0 && up < LEAST_UP;
    }

    /**
     * How far out of a ring's plane a player is put down: one cell, in front
     * of the opening. In the plane is in the gate, and a player standing in a
     * gate is sent straight back the moment the cooldown runs out.
     */
    public static final int LANDING_OUT = 1;

    /**
     * How near a lit gate must be to where a new Vale side would go for the
     * trip to use that ring rather than build another. A built ring is
     * {@code LEAST_ACROSS + 2} frames wide, so two built nearer than that share
     * blocks, and building the second breaks the first.
     */
    public static final int NEAR_ACROSS = LEAST_ACROSS + 2;

    /** And how far up or down it is looked for. */
    public static final int NEAR_UP = 8;

    /**
     * Which side of a ring's plane someone stands, from how far past the low
     * edge of the plane's own block they are: -1 short of its middle, 1 past
     * it. The way back puts them on the side they came in from.
     */
    public static int side(double past) {
        return past < 0.5D ? -1 : 1;
    }

    // ==================================================================
    // Stepping through
    // ==================================================================

    /**
     * Ticks a player is ignored by every gate after going through one. A gate
     * is a block you stand in, so it is asked every tick; three seconds, the
     * rift's, is long enough to walk clear of the ring you arrive beside.
     */
    public static final int COOLDOWN_TICKS = 60;

    /**
     * Where a way in puts you: the gentlest crossing of the Vale.
     *
     * <p>The bestiary reads the Vale as rings out from its middle crossing, the
     * middle band of warmth crossed with the middle band of rain, and
     * everything one ring out is harder than everything in the ring inside it.
     * A way in that dropped a player in the sulphur springs would be the coin
     * flip those rings exist to take away.
     */
    public static final String ARRIVAL = "verdant_vale";

    /**
     * How many rings out from the middle crossing a crossing is: bands of
     * warmth away plus bands of rain away. The same count the bestiary's
     * {@code DenizenRules.ring} makes, worked out here from the bands rather
     * than copied, since the rules of a module are the JDK and its own.
     */
    public static int ringOf(ValeRules.Biome one) {
        return Math.abs(one.warmth() - ValeRules.WARMTHS / 2)
                + Math.abs(one.wet() - ValeRules.WETS / 2);
    }

    /** The biome a way in looks for, without its namespace. */
    public static String arrivalBiome() {
        ValeRules.Biome one = ValeRules.biome(ARRIVAL);
        return one == null ? "" : one.id();
    }

    /**
     * How far a way in looks for it: as far as the compass is ever allowed to,
     * on the compass's own grid, so one trip costs what one search costs. And
     * only a ring's first trip looks at all.
     */
    public static final double ARRIVAL_SEARCH_BLOCKS = WildRules.MOST_SEARCH_BLOCKS;

    /** The grid that search samples on. */
    public static double arrivalStep() {
        return WildRules.stepFor(ARRIVAL_SEARCH_BLOCKS);
    }

    /** How many climates it samples, at one height. */
    public static long arrivalSamples() {
        return WildRules.samples(ARRIVAL_SEARCH_BLOCKS, arrivalStep());
    }

    // ==================================================================
    // Dry ground
    // ==================================================================

    /**
     * How far from the column a way in found a dry one is looked for: a modest
     * spiral, {@value} blocks each way, nearest first.
     *
     * <p>The ground a Vale side is set on is the heightmap that stops at
     * water, so the top of a lake is as high as its bank; and the search finds
     * a biome, not a block, so the nearest verdant column is as likely a lake,
     * a river or the sea as a meadow. Built there, the ring stood on the water
     * and the player landed in it (the live stage of 1 October 2026 arrived at
     * sea level in a bay). A gate should land a player on dry ground.
     * Sixty-four each way is {@link #drySamples()} columns, nine chunks by
     * nine once a ring. Twenty-four crossed any river or pond but not the
     * Vale's seas: the Vale has no ocean biome, so its water is low ground of
     * the land biomes, and the nearest verdant column can sit in a bay forty
     * blocks from shore (1 October 2026, a player left swimming). Sixty-four
     * still stays inside one patch of biome, about {@link WildRules#WORLD_GRAIN}
     * across, so a dry landing is still a verdant one.
     */
    public static final int DRY_SEARCH_BLOCKS = 64;

    /** Blocks to a chunk, which is what looking at a column costs. */
    private static final int CHUNK = 16;

    /**
     * And the most the reach may be: four chunks each way. Every column looked
     * at is a chunk generated, on the server thread, on top of the seconds the
     * first trip's biome search already holds it for; nine chunks by nine is
     * a few seconds once a ring, and more would be felt.
     */
    public static final int DRY_SEARCH_MOST = 4 * CHUNK;

    /** A column of the Vale, so many blocks east and south of the one a way in found. */
    public record Column(int east, int south) {
    }

    /**
     * What stands at the top of a column: the block its heightmap stopped
     * under, as the two things a landing asks of it.
     *
     * @param fluid  whether a fluid stands in it: water, or lava
     * @param sturdy whether its upper face would hold a player up
     */
    public record Footing(boolean fluid, boolean sturdy) {

        /** Ground: nothing flows, and it holds. */
        public static final Footing GROUND = new Footing(false, true);

        /** The top of a lake, a river or the sea. */
        public static final Footing WATER = new Footing(true, false);
    }

    /** The ground round the column a way in found, read one column at a time. */
    @FunctionalInterface
    public interface Surface {

        /** What stands at the top of this column. */
        Footing at(int east, int south);
    }

    /**
     * Whether a column is dry: nothing flows at its surface, and the surface
     * would hold a player up.
     *
     * <p>Fluid is asked because the heightmap has already counted the water
     * as ground: the block its column stops under is the lake's top. Lava is
     * as wet as water as far as a landing goes, and worse, so the two are not
     * told apart. Sturdiness is asked because the heightmap stops under
     * whatever blocks motion, and a fence post, a bottom slab, a chest's lid
     * or a bank of snow stops it without holding anybody up: a ring built on
     * one puts a player down through it.
     */
    public static boolean dry(Surface surface, Column at) {
        Objects.requireNonNull(surface, "no ground to read");
        Footing top = surface.at(at.east(), at.south());
        return !top.fluid() && top.sturdy();
    }

    /**
     * Nearest the column a way in found first. Ties go to the northernmost
     * and then the westernmost, so the order is the same on every server and
     * a ring's far side is where the harness says. Declared before the lists
     * it sorts, since a static field is nothing until its line is reached.
     */
    private static final Comparator<Column> NEAREST_FIRST = Comparator.comparingInt(PortalRules::farOf)
            .thenComparingInt(Column::south).thenComparingInt(Column::east);

    /** Every column within reach, nearest first, worked out once. */
    private static final List<Column> DRY_SEARCH = spiral();

    /**
     * Every column within {@link #DRY_SEARCH_BLOCKS} of the one a way in
     * found, nearest first, that column itself first of all, ties as
     * {@link #NEAREST_FIRST} settles them.
     */
    public static List<Column> drySearch() {
        return DRY_SEARCH;
    }

    private static List<Column> spiral() {
        List<Column> columns = new ArrayList<>();
        for (int south = -DRY_SEARCH_BLOCKS; south <= DRY_SEARCH_BLOCKS; south++) {
            for (int east = -DRY_SEARCH_BLOCKS; east <= DRY_SEARCH_BLOCKS; east++) {
                columns.add(new Column(east, south));
            }
        }
        columns.sort(NEAREST_FIRST);
        return List.copyOf(columns);
    }

    /** How far a column is from the one a way in found, squared. */
    public static int farOf(Column one) {
        return one.east() * one.east() + one.south() * one.south();
    }

    /** How many columns the search looks at, at most: all of them, over the sea. */
    public static int drySamples() {
        return (2 * DRY_SEARCH_BLOCKS + 1) * (2 * DRY_SEARCH_BLOCKS + 1);
    }

    /**
     * The dry column nearest the one a way in found, or null when everything
     * within reach is water. Then the way in settles for the column it found,
     * as it always did: a ring on the sea is still a way home.
     */
    public static Column nearestDry(Surface surface) {
        Objects.requireNonNull(surface, "no ground to read");
        for (Column one : drySearch()) {
            if (dry(surface, one)) {
                return one;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The far shore
    // ------------------------------------------------------------------

    /**
     * How far apart the far stage samples along each of its lines: {@value}
     * blocks.
     *
     * <p>The near spiral looks at every column, because a bank can be one
     * block wide and a pond one block across. The far stage is looking for a
     * shore, once the spiral has found only water, and a shore worth leaving
     * a sea for is wider than a stride; one look every four blocks meets it
     * within four blocks of where it is. Four is a quarter of a chunk, so a
     * line generates each chunk it crosses once and looks at it four times.
     */
    public static final int FAR_DRY_STEP = 4;

    /**
     * How far out the far stage walks: {@value} blocks along each of the
     * eight compass lines, north, north-east, east and on round.
     *
     * <p>The Vale has no ocean biome, so its seas are low ground of the land
     * biomes, and the first trip of a fresh ring landed in one where 16,629 of
     * the 16,641 columns within sixty-four were water at sea level, the other
     * twelve the ring's own pads, and the shore a hundred blocks off (1 October
     * 2026, late). A spiral wide enough for such a sea is tens of thousands of
     * columns and hundreds of chunks generated on the server thread. Eight
     * lines sampled every {@link #FAR_DRY_STEP} blocks out to {@value} are at
     * most eight by sixty-four looks, 512, and {@link #farDrySamples()} once
     * the strides inside the spiral's square are left out; the chunks under
     * them, about a hundred and thirty along the lines, are a few seconds
     * once a ring. The samples are taken in order of distance across all
     * eight lines, so the first dry column met is the nearest dry one of
     * those sampled and the side goes to the nearest shore, not the first
     * line's.
     */
    public static final int FAR_DRY_SEARCH_BLOCKS = 256;

    /**
     * And the most it may: {@value} blocks, thirty-two chunks along each
     * line. Twice the reach is twice the chunks and twice the seconds a first
     * trip holds the server for, and a sea the lines cannot cross at {@value}
     * is one a ring on the water is the honest answer to: the way in settles
     * for the column it found, as it always did.
     */
    public static final int FAR_DRY_SEARCH_MOST = 512;

    /**
     * The eight compass lines, each as one stride east and one stride south:
     * north, north-east, east, south-east, south, south-west, west,
     * north-west. A diagonal's stride is a stride in each, so its samples sit
     * a stride further apart than an axis's; the sort puts every sample where
     * its distance says.
     */
    private static final int[][] LINES = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /** Every column the far stage looks at, nearest first, worked out once. */
    private static final List<Column> FAR_DRY_SEARCH = lines();

    /**
     * Every column the far stage looks at, nearest first: every
     * {@link #FAR_DRY_STEP} blocks along each of the eight compass lines out
     * to {@link #FAR_DRY_SEARCH_BLOCKS}, leaving out the strides that fall
     * inside the near spiral's square, which has looked there already. Ties
     * as {@link #NEAREST_FIRST} settles them, so the shore a ring lands on is
     * the same on every server.
     */
    public static List<Column> farDrySearch() {
        return FAR_DRY_SEARCH;
    }

    /**
     * The stride the lines are walked by. A stride of nought is a fault,
     * and {@link #faults()} says so; it is not also a loop that never ends.
     */
    private static int stride() {
        return Math.max(1, FAR_DRY_STEP);
    }

    private static List<Column> lines() {
        List<Column> columns = new ArrayList<>();
        for (int out = stride(); out <= FAR_DRY_SEARCH_BLOCKS; out += stride()) {
            if (out <= DRY_SEARCH_BLOCKS) {
                continue;
            }
            for (int[] line : LINES) {
                columns.add(new Column(line[0] * out, line[1] * out));
            }
        }
        columns.sort(NEAREST_FIRST);
        return List.copyOf(columns);
    }

    /**
     * How many columns the far stage looks at, at most: all of them, over the
     * open sea. The strides of a line past the spiral's square and inside the
     * reach, eight times.
     */
    public static int farDrySamples() {
        return LINES.length * Math.max(0, FAR_DRY_SEARCH_BLOCKS / stride() - DRY_SEARCH_BLOCKS / stride());
    }

    /**
     * The nearest dry column the compass lines meet, or null when every
     * column they sample is water: the sea is wider than the reach, and the
     * way in settles for the column it found.
     */
    public static Column nearestFarDry(Surface surface) {
        Objects.requireNonNull(surface, "no ground to read");
        for (Column one : farDrySearch()) {
            if (dry(surface, one)) {
                return one;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The footing
    // ------------------------------------------------------------------

    /**
     * Which way a ring runs, as the game names its axes: along east (X) or
     * along south (Z). The rules are the JDK alone, so the game's own axis
     * is read into this one at the door, and the footprint below runs the
     * way {@code ValePortal.across} and {@code ValePortal.out} do.
     */
    public enum Axis {
        /** Across runs east, through runs south. */
        X,
        /** Across runs south, through runs east. */
        Z
    }

    /** One block across a ring on this axis, as a column: east along X, south along Z. */
    public static Column across(Axis axis) {
        return axis == Axis.X ? new Column(1, 0) : new Column(0, 1);
    }

    /** One block out through a ring on this axis: south through X, east through Z. */
    public static Column through(Axis axis) {
        return axis == Axis.X ? new Column(0, 1) : new Column(1, 0);
    }

    /**
     * How far the dry column a stage found may be walked on, for one whose
     * whole footing is dry: {@value} blocks.
     *
     * <p>The spiral and the lines find a dry <em>column</em>, and the first
     * dry column met is at the waterline by construction: the one before it
     * was wet. A ring settled there straddles the shore, its sill on the
     * first dry block and one of its landings over the water (2 October
     * 2026: a fresh ring's first trip landed at a shore, the player on a
     * built pad with land a step east). So the column found is walked on
     * along the line it was found on, which points inland, until the sill
     * and both landings are dry. A shore is seldom a straight line: a beach
     * that steps down into the sea has its pools, a river mouth its bars,
     * and the far stage strides {@link #FAR_DRY_STEP}, so the shore it meets
     * can be three blocks back from the sample. Twelve is three of those
     * strides, and at most {@link #settleLooks()} looks, on chunks the stage
     * that found the column has generated already. A column with no dry
     * footing within twelve, a spit or an islet narrower than a ring, is
     * kept: the built pads still hold a player up.
     */
    public static final int SETTLE_IN_BLOCKS = 12;

    /**
     * And the most: {@value} blocks, two chunks. Every block walked past the
     * column found can be a chunk neither stage generated, on the server
     * thread; and a dry column with no dry footing within two chunks of it
     * is one the pads are the honest answer to.
     */
    public static final int SETTLE_IN_MOST = 2 * CHUNK;

    /** The column the way in found, which every other is measured from. */
    private static final Column ORIGIN = new Column(0, 0);

    /** The columns a settled ring needs dry on each axis, worked out once. */
    private static final List<Column> FOOTPRINT_X = footprintOf(Axis.X);
    private static final List<Column> FOOTPRINT_Z = footprintOf(Axis.Z);

    /**
     * A ring's footprint on this axis: the columns a settled ring needs dry,
     * as offsets from its anchor. The sill's row, {@link #LEAST_ACROSS} wide
     * along the across axis from the anchor, and both landing rows, one
     * {@link #LANDING_OUT} out either side along the through axis. The side
     * posts' columns and the pads' outer blocks are not asked: a player
     * stands on a landing and walks off it across the sill's line, and a
     * ring whose sill and landings are dry stands on a shore rather than
     * across it.
     */
    public static List<Column> footprint(Axis axis) {
        return axis == Axis.X ? FOOTPRINT_X : FOOTPRINT_Z;
    }

    private static List<Column> footprintOf(Axis axis) {
        Column along = across(axis);
        Column out = through(axis);
        List<Column> columns = new ArrayList<>();
        for (int row : new int[] {-LANDING_OUT, 0, LANDING_OUT}) {
            for (int a = 0; a < LEAST_ACROSS; a++) {
                columns.add(new Column(a * along.east() + row * out.east(),
                        a * along.south() + row * out.south()));
            }
        }
        return List.copyOf(columns);
    }

    /**
     * Whether a ring settled at a column stands on dry ground: every column
     * of its footprint is dry, by the one test.
     */
    public static boolean footingDry(Surface surface, Column column, Axis axis) {
        Objects.requireNonNull(surface, "no ground to read");
        for (Column one : footprint(axis)) {
            if (!dry(surface, new Column(column.east() + one.east(), column.south() + one.south()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where a ring settles, from the dry column a stage found: the first
     * column, walking on from it along the line it was found on, whose whole
     * footing is dry, within {@link #SETTLE_IN_BLOCKS}; the column found
     * when there is none.
     *
     * <p>The line is the one from the column the way in found to the dry
     * one: the far stage's compass line, or the spiral's way from the wet
     * column to the nearest dry, which points inland. The column found is
     * tried first, so one whose footing is dry already is not moved. When
     * the column the way in found was dry itself there is no line, and the
     * spiral is walked again, nearest first and within the same reach, for
     * the first column whose footing is dry.
     */
    public static Column settled(Surface surface, Column found, Axis axis) {
        Objects.requireNonNull(surface, "no ground to read");
        Column way = new Column(Integer.signum(found.east()), Integer.signum(found.south()));
        if (way.equals(ORIGIN)) {
            for (Column one : drySearch()) {
                if (within(one, SETTLE_IN_BLOCKS) && footingDry(surface, one, axis)) {
                    return one;
                }
            }
            return found;
        }
        for (int stride = 0; stride <= SETTLE_IN_BLOCKS; stride++) {
            Column at = new Column(found.east() + way.east() * stride, found.south() + way.south() * stride);
            if (footingDry(surface, at, axis)) {
                return at;
            }
        }
        return found;
    }

    /** Whether a column is within so many blocks of the origin each way. */
    private static boolean within(Column one, int reach) {
        return Math.abs(one.east()) <= reach && Math.abs(one.south()) <= reach;
    }

    /**
     * How many columns settling looks at, at most: the spiral walked again,
     * every column within reach each way, at a whole footprint apiece. The
     * walk along a line is far fewer, one footprint a stride.
     */
    public static int settleLooks() {
        return (2 * SETTLE_IN_BLOCKS + 1) * (2 * SETTLE_IN_BLOCKS + 1) * FOOTPRINT_X.size();
    }

    // ==================================================================
    // What has to be true
    // ==================================================================

    /** Everything wrong with the numbers. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        if (LEAST_ACROSS < 1 || LEAST_UP < 1) {
            bad.add("a ring's opening may be " + LEAST_ACROSS + " by " + LEAST_UP
                    + ", which is no opening at all");
        }
        if (MOST < Math.max(LEAST_ACROSS, LEAST_UP)) {
            bad.add("the largest ring is " + MOST + " and the smallest opening is "
                    + LEAST_ACROSS + " by " + LEAST_UP + ", so no ring lights");
        }
        if (FRAMES_PER_CRAFT < 1) {
            bad.add("a craft makes " + FRAMES_PER_CRAFT + " frames");
        }
        if (LANDING_OUT < 1) {
            bad.add("a player is put down " + LANDING_OUT + " cells out of the ring's "
                    + "plane, which is in the gate, and is sent straight back");
        }
        if (NEAR_ACROSS < LEAST_ACROSS + 2) {
            bad.add("a standing ring is looked for " + NEAR_ACROSS + " across, and two "
                    + "built rings nearer than " + (LEAST_ACROSS + 2) + " share frames");
        }
        if (COOLDOWN_TICKS < 1) {
            bad.add("a gate has no cooldown, so it is used twenty times a second");
        }
        if (DRY_SEARCH_BLOCKS < 1) {
            bad.add("a dry column is looked for " + DRY_SEARCH_BLOCKS + " blocks out, so a Vale "
                    + "side is never moved off the water and a gate lands a player in the sea");
        }
        if (DRY_SEARCH_BLOCKS > DRY_SEARCH_MOST) {
            bad.add("a dry column is looked for " + DRY_SEARCH_BLOCKS + " blocks out, which is "
                    + drySamples() + " columns and a flood of chunks generated on the server "
                    + "thread; four chunks each way, " + DRY_SEARCH_MOST + ", is the most");
        }
        if (FAR_DRY_STEP < 1) {
            bad.add("the far stage strides " + FAR_DRY_STEP + " blocks along its lines, which is "
                    + "standing still: every look would be at the column the spiral found wet");
        }
        if (FAR_DRY_SEARCH_BLOCKS <= DRY_SEARCH_BLOCKS) {
            bad.add("the far stage reaches " + FAR_DRY_SEARCH_BLOCKS + " blocks and the near spiral "
                    + DRY_SEARCH_BLOCKS + ", so it looks nowhere it did not once the spiral has found "
                    + "only water, and a gate still lands a player in the sea");
        } else if (farDrySamples() == 0) {
            bad.add("the far stage strides " + FAR_DRY_STEP + " blocks and reaches "
                    + FAR_DRY_SEARCH_BLOCKS + ", so it takes no stride past the spiral at all");
        }
        if (FAR_DRY_SEARCH_BLOCKS > FAR_DRY_SEARCH_MOST) {
            bad.add("the far stage reaches " + FAR_DRY_SEARCH_BLOCKS + " blocks, which is "
                    + farDrySamples() + " looks and the chunks under them generated on the server "
                    + "thread every first trip; " + FAR_DRY_SEARCH_MOST + " is the most");
        }
        if (SETTLE_IN_BLOCKS < 1) {
            bad.add("the dry column found is walked on " + SETTLE_IN_BLOCKS + " blocks for a dry "
                    + "footing, which is not at all, so a ring found at a waterline stays astride it "
                    + "with a landing over the water");
        }
        if (SETTLE_IN_BLOCKS > SETTLE_IN_MOST) {
            bad.add("the dry column found is walked on up to " + SETTLE_IN_BLOCKS + " blocks, which is "
                    + settleLooks() + " looks in chunks nobody has generated; two chunks on from the "
                    + "shore, " + SETTLE_IN_MOST + ", is the most");
        }
        if (SETTLE_IN_BLOCKS > DRY_SEARCH_BLOCKS) {
            bad.add("the dry column found is walked on up to " + SETTLE_IN_BLOCKS + " blocks, and the "
                    + "spiral walked again when there is no line reaches " + DRY_SEARCH_BLOCKS
                    + ", so the settling reaches further along a line than off one");
        }
        ValeRules.Biome arrival = ValeRules.biome(ARRIVAL);
        if (arrival == null) {
            bad.add(ARRIVAL + " is not a crossing of the Vale, so a way in looks for "
                    + "a biome that is not there and puts everybody wherever it gives up");
        } else if (ringOf(arrival) != 0) {
            bad.add(ARRIVAL + " is " + ringOf(arrival) + " ring(s) out from the middle "
                    + "of the Vale, so a way in drops a player past creatures the "
                    + "bestiary means for later");
        }
        if (arrivalStep() >= WildRules.WORLD_GRAIN) {
            bad.add("a way in samples every " + Math.round(arrivalStep()) + " blocks and a "
                    + "patch of biome is about " + Math.round(WildRules.WORLD_GRAIN)
                    + " across, so it would walk over the verdant vale");
        }
        return List.copyOf(bad);
    }
}
