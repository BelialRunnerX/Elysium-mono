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
