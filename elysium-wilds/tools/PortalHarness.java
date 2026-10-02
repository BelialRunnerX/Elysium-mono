package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The way into the Vale, executed.
 *
 * <p>A ring that looks finished and does not light is a player using the
 * compass on a wall and guessing. A ring with a hole in it that lights anyway
 * is a gate nobody built. So every ring from one cell to twenty-three is built
 * here, whole and broken, on a plane that is open sky everywhere else, which is
 * the worst plane a flood can be asked to stop on.
 */
public final class PortalHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theNumbers();
        theShape();
        theRefusals();
        theSmallest();
        theBuilt();
        theArrival();
        theLanding();
        theFarShore();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every ring from one cell to twenty-three "
                + "lit or refused by the Nether portal's rule from any frame of it, "
                + "corners included, a gap, an L, a block and a frame left inside "
                + "refused, the smallest ring three crafts, the ring the Vale builds a "
                + "ring by the same rule with the landing outside it, and a way in that "
                + "costs one compass search and drops a player in the gentlest crossing, on "
                + "the nearest dry ground, or past a sea on the nearest shore its compass "
                + "lines meet");
    }

    // ==================================================================
    // A plane to build in
    // ==================================================================

    /** A plane of open sky with frames and other blocks set into it. */
    private static final class Sky implements PortalRules.Plane {

        final Set<PortalRules.Spot> frames = new HashSet<>();
        final Set<PortalRules.Spot> other = new HashSet<>();
        int asked;

        @Override
        public PortalRules.Cell at(int across, int up) {
            asked++;
            PortalRules.Spot spot = new PortalRules.Spot(across, up);
            if (frames.contains(spot)) {
                return PortalRules.Cell.FRAME;
            }
            if (other.contains(spot)) {
                return PortalRules.Cell.OTHER;
            }
            return PortalRules.Cell.OPEN;
        }
    }

    /** A ring round an opening of this size with its low corner at (0, 0). */
    private static Sky ring(int wide, int tall, boolean corners) {
        Sky sky = new Sky();
        for (int up = -1; up <= tall; up++) {
            for (int across = -1; across <= wide; across++) {
                boolean inside = across >= 0 && across < wide && up >= 0 && up < tall;
                boolean corner = (across == -1 || across == wide) && (up == -1 || up == tall);
                if (!inside && (corners || !corner)) {
                    sky.frames.add(new PortalRules.Spot(across, up));
                }
            }
        }
        return sky;
    }

    private static boolean lights(int wide, int tall) {
        return wide >= PortalRules.LEAST_ACROSS && tall >= PortalRules.LEAST_UP
                && wide <= PortalRules.MOST && tall <= PortalRules.MOST;
    }

    // ==================================================================

    private static void theNumbers() {
        check(PortalRules.faults().isEmpty(), "something wrong with the numbers: "
                + PortalRules.faults());
        System.out.println("  the numbers: an opening from " + PortalRules.LEAST_ACROSS
                + " by " + PortalRules.LEAST_UP + " to " + PortalRules.MOST + " by "
                + PortalRules.MOST + ", " + PortalRules.FRAMES_PER_CRAFT
                + " frames a craft, " + PortalRules.COOLDOWN_TICKS
                + " ticks between trips");
    }

    /** Every size, with and without corners, from one frame and from all of them. */
    private static void theShape() {
        int lit = 0;
        int refused = 0;
        for (int wide = 1; wide <= PortalRules.MOST + 2; wide++) {
            for (int tall = 1; tall <= PortalRules.MOST + 2; tall++) {
                for (boolean corners : new boolean[] {false, true}) {
                    Sky sky = ring(wide, tall, corners);
                    // The sill's first frame, which every ring has.
                    PortalRules.Ring found = PortalRules.around(sky, 0, -1);
                    if (!lights(wide, tall)) {
                        check(found == null, "a " + wide + " by " + tall + " opening lights, "
                                + "and the rule is " + PortalRules.LEAST_ACROSS + " by "
                                + PortalRules.LEAST_UP + " to " + PortalRules.MOST);
                        refused++;
                        continue;
                    }
                    lit++;
                    check(found != null, "a " + wide + " by " + tall + " ring "
                            + (corners ? "with" : "without") + " corners does not light");
                    if (found == null) {
                        continue;
                    }
                    check(found.wide() == wide && found.tall() == tall
                                    && found.opening().size() == wide * tall,
                            "a " + wide + " by " + tall + " ring lights as " + found.wide()
                                    + " by " + found.tall() + " with "
                                    + found.opening().size() + " cells");
                    check(found.anchor().equals(new PortalRules.Spot(0, 0)),
                            "a ring's anchor is " + found.anchor() + " and its lowest, "
                                    + "then least-across, cell is (0, 0)");
                    boolean every = wide <= 8 && tall <= 8
                            || wide == PortalRules.MOST || tall == PortalRules.MOST
                            || wide == PortalRules.LEAST_ACROSS || tall == PortalRules.LEAST_UP;
                    if (!every) {
                        continue;
                    }
                    // From every frame: the sides as well as the sill (the rift
                    // is refused from its sides), and the corners when present.
                    for (PortalRules.Spot frame : sky.frames) {
                        PortalRules.Ring again = PortalRules.around(sky, frame.across(),
                                frame.up());
                        check(again != null, "a " + wide + " by " + tall + " ring does not "
                                + "light when the compass is used on its frame at "
                                + frame);
                        if (again != null) {
                            check(again.anchor().equals(found.anchor()),
                                    "the ring's anchor from " + frame + " is "
                                            + again.anchor() + " and from its sill "
                                            + found.anchor() + ", so one ring has two "
                                            + "names and every trip builds again");
                        }
                    }
                    // And from every cell of the opening, which is how a trip
                    // works out which ring it came through.
                    for (PortalRules.Spot cell : found.opening()) {
                        PortalRules.Ring inside = PortalRules.around(sky, cell.across(),
                                cell.up());
                        check(inside != null && inside.anchor().equals(found.anchor()),
                                "the ring through " + cell + " is not the ring it is in");
                    }
                }
            }
        }
        System.out.println("  the shape: " + lit + " ring(s) lit and " + refused
                + " refused for their size, each lit one found from every frame and "
                + "every cell of it under one anchor");
    }

    /** Everything that looks nearly like a ring. */
    private static void theRefusals() {
        int broken = 0;
        for (int wide = PortalRules.LEAST_ACROSS; wide <= 6; wide++) {
            for (int tall = PortalRules.LEAST_UP; tall <= 7; tall++) {
                // One frame gone from a side, anywhere along it: the flood
                // walks out of the gap into the sky.
                Sky whole = ring(wide, tall, false);
                for (PortalRules.Spot gone : new ArrayList<>(whole.frames)) {
                    Sky gap = ring(wide, tall, false);
                    gap.frames.remove(gone);
                    for (PortalRules.Spot frame : gap.frames) {
                        PortalRules.Ring found = PortalRules.around(gap, frame.across(),
                                frame.up());
                        check(found == null, "a " + wide + " by " + tall + " ring with a "
                                + "gap at " + gone + " lights from " + frame);
                    }
                    broken++;
                }
                // Something left in the opening.
                for (int up = 0; up < tall; up++) {
                    for (int across = 0; across < wide; across++) {
                        Sky block = ring(wide, tall, false);
                        block.other.add(new PortalRules.Spot(across, up));
                        check(PortalRules.around(block, 0, -1) == null,
                                "a " + wide + " by " + tall + " ring with a block left "
                                        + "inside at (" + across + ", " + up + ") lights");
                        Sky island = ring(wide, tall, false);
                        island.frames.add(new PortalRules.Spot(across, up));
                        PortalRules.Ring round = PortalRules.around(island, -1, 0);
                        check(round == null || round.opening().size()
                                        == round.wide() * round.tall(),
                                "a ring with a frame left standing inside it lights "
                                        + "round the frame");
                        broken += 2;
                    }
                }
            }
        }
        // An L: a 4 by 4 frame with the top right 2 by 2 filled with frames.
        Sky ell = ring(4, 4, false);
        for (int up = 2; up < 4; up++) {
            for (int across = 2; across < 4; across++) {
                ell.frames.add(new PortalRules.Spot(across, up));
            }
        }
        check(PortalRules.around(ell, 0, -1) == null, "an L-shaped opening lights");
        // Standing on the ground rather than on a sill: the ground is not a frame.
        Sky grounded = ring(2, 3, false);
        grounded.frames.remove(new PortalRules.Spot(0, -1));
        grounded.frames.remove(new PortalRules.Spot(1, -1));
        grounded.other.add(new PortalRules.Spot(0, -1));
        grounded.other.add(new PortalRules.Spot(1, -1));
        check(PortalRules.around(grounded, -1, 0) == null,
                "a ring with the ground for a sill lights");
        System.out.println("  the refusals: " + (broken + 2) + " rings with a gap, a block "
                + "or a frame left inside, an L and a ring on bare ground, none of them lit");
    }

    /** Ten frames, three crafts. */
    private static void theSmallest() {
        Sky smallest = ring(PortalRules.LEAST_ACROSS, PortalRules.LEAST_UP, false);
        check(smallest.frames.size() == PortalRules.leastFrames(),
                "the smallest ring is " + smallest.frames.size() + " frames and the rules "
                        + "say " + PortalRules.leastFrames());
        for (PortalRules.Spot frame : smallest.frames) {
            check(PortalRules.around(smallest, frame.across(), frame.up()) != null,
                    "the smallest ring, without corners, does not light from " + frame);
        }
        check(PortalRules.leastFrames() == 10, "the smallest ring is "
                + PortalRules.leastFrames() + " frames, and the Nether portal's is ten");
        check(PortalRules.leastCrafts() == 3, "the smallest ring takes "
                + PortalRules.leastCrafts() + " crafts, and the price of the rift's shape "
                + "is three");
        System.out.println("  the smallest: " + PortalRules.leastFrames() + " frames, no "
                + "corners, lit from each of them, in " + PortalRules.leastCrafts()
                + " crafts of " + PortalRules.FRAMES_PER_CRAFT);
    }

    /** The ring the Vale builds is a ring, and nobody lands in it. */
    private static void theBuilt() {
        Sky built = new Sky();
        built.frames.addAll(PortalRules.builtFrames());
        check(PortalRules.builtFrames().size() == PortalRules.leastFrames() + 4,
                "the built ring is " + PortalRules.builtFrames().size() + " frames, and "
                        + "the smallest ring with its corners is "
                        + (PortalRules.leastFrames() + 4));
        for (PortalRules.Spot frame : PortalRules.builtFrames()) {
            PortalRules.Ring found = PortalRules.around(built, frame.across(), frame.up());
            check(found != null, "the ring the Vale builds does not light from " + frame
                    + ", so it closes for good the first time it is mended");
            if (found != null) {
                check(new HashSet<>(found.opening()).equals(
                                new HashSet<>(PortalRules.builtOpening())),
                        "the ring the Vale builds has " + found.opening().size()
                                + " cells of opening by the rule and "
                                + PortalRules.builtOpening().size() + " filled");
                check(found.anchor().equals(new PortalRules.Spot(0, 0)),
                        "the ring the Vale builds is known by " + found.anchor()
                                + " and is filed under (0, 0)");
            }
        }
        for (PortalRules.Spot cell : PortalRules.builtOpening()) {
            check(!PortalRules.builtFrames().contains(cell),
                    "the built ring puts a frame in its own opening at " + cell);
        }
        check(PortalRules.LANDING_OUT >= 1, "a player arriving is put down in the gate");
        // Two built rings side by side: they share frames exactly when they are
        // nearer than the standing ring is looked for.
        for (int apart = -12; apart <= 12; apart++) {
            Set<PortalRules.Spot> first = new HashSet<>(PortalRules.builtFrames());
            first.addAll(PortalRules.builtOpening());
            boolean shared = false;
            for (PortalRules.Spot spot : PortalRules.builtFrames()) {
                if (first.contains(new PortalRules.Spot(spot.across() + apart, spot.up()))) {
                    shared = true;
                }
            }
            if (shared) {
                check(Math.abs(apart) <= PortalRules.NEAR_ACROSS,
                        "two rings " + apart + " apart share frames and a standing ring "
                                + "is only looked for " + PortalRules.NEAR_ACROSS + " across");
            }
        }
        check(PortalRules.side(0.2D) == -1 && PortalRules.side(0.8D) == 1,
                "the side of a ring someone stands on is read backwards");
        System.out.println("  the built ring: " + PortalRules.builtFrames().size()
                + " frames round " + PortalRules.builtOpening().size() + " cells, a ring by "
                + "the same rule from every frame, the landing "
                + PortalRules.LANDING_OUT + " cell out of it");
    }

    /** The gentlest crossing, found for the price of one search. */
    private static void theArrival() {
        ValeRules.Biome arrival = ValeRules.biome(PortalRules.ARRIVAL);
        check(arrival != null, PortalRules.ARRIVAL + " is not a crossing of the Vale");
        int gentle = 0;
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            int ring = PortalRules.ringOf(one);
            check(ring >= 0 && ring <= 3, one.name() + " is " + ring + " rings out, and the "
                    + "Vale has four rings counting the middle");
            if (ring == 0) {
                gentle++;
                check(one.name().equals(PortalRules.ARRIVAL), "the middle of the Vale is "
                        + one.name() + " and a way in goes to " + PortalRules.ARRIVAL);
            }
        }
        check(gentle == 1, gentle + " crossing(s) are the middle of the Vale");
        check(PortalRules.arrivalBiome().equals("vale_verdant_vale"),
                "a way in looks for " + PortalRules.arrivalBiome());
        check(PortalRules.ARRIVAL_SEARCH_BLOCKS <= WildRules.MOST_SEARCH_BLOCKS,
                "a way in looks further than the compass is ever allowed to");
        long cost = PortalRules.arrivalSamples();
        check(cost <= WildRules.SAMPLES_PER_SEARCH * 1.05D + 4_000L,
                "a way in costs " + cost + " samples, and one compass search is "
                        + WildRules.SAMPLES_PER_SEARCH);
        check(PortalRules.arrivalStep() >= WildRules.FINEST_STEP,
                "a way in samples finer than climate changes");
        System.out.println("  the arrival: " + PortalRules.ARRIVAL + ", the one crossing "
                + "nought rings out, looked for " + Math.round(PortalRules.ARRIVAL_SEARCH_BLOCKS)
                + " blocks every " + Math.round(PortalRules.arrivalStep()) + " at one height, "
                + cost + " samples, once a ring");
    }

    /**
     * A way in lands on dry ground: the nearest dry column within reach, found
     * for a bounded price, and the column it found when all of it is sea.
     */
    private static void theLanding() {
        int reach = PortalRules.DRY_SEARCH_BLOCKS;
        List<PortalRules.Column> search = PortalRules.drySearch();
        check(search.size() == PortalRules.drySamples()
                        && new HashSet<>(search).size() == search.size(),
                "the dry search is " + search.size() + " columns, "
                        + new HashSet<>(search).size() + " of them distinct, and a square "
                        + reach + " each way is " + PortalRules.drySamples());
        check(!search.isEmpty() && search.get(0).equals(new PortalRules.Column(0, 0)),
                "the dry search does not start at the column the way in found, so a dry "
                        + "landing is moved off itself");
        boolean nearestFirst = true;
        boolean inReach = true;
        int last = -1;
        for (PortalRules.Column one : search) {
            int far = PortalRules.farOf(one);
            nearestFirst &= far >= last;
            last = far;
            inReach &= Math.abs(one.east()) <= reach && Math.abs(one.south()) <= reach;
        }
        check(nearestFirst, "the dry search is not nearest first, so a landing is moved "
                + "further than it need be");
        check(inReach, "the dry search looks further than " + reach + " blocks");

        // Dryness is the one question, asked the right way round.
        PortalRules.Surface land = (east, south) -> PortalRules.Footing.GROUND;
        PortalRules.Surface sea = (east, south) -> PortalRules.Footing.WATER;
        check(PortalRules.dry(land, new PortalRules.Column(0, 0)), "dry ground is not dry");
        check(!PortalRules.dry(sea, new PortalRules.Column(0, 0)), "a lake is dry");

        // Dry where the way in found: it stays put, after one look.
        int[] asked = {0};
        PortalRules.Column stay = PortalRules.nearestDry((east, south) -> {
            asked[0]++;
            return PortalRules.Footing.GROUND;
        });
        check(new PortalRules.Column(0, 0).equals(stay),
                "a dry column is moved off itself to " + stay);
        check(asked[0] == 1, "a dry column was looked at " + asked[0] + " times");

        // The sea: nothing dry, so null, and no more looks than the search has.
        asked[0] = 0;
        PortalRules.Column none = PortalRules.nearestDry((east, south) -> {
            asked[0]++;
            return PortalRules.Footing.WATER;
        });
        check(none == null, "the open sea has a dry column at " + none);
        check(asked[0] == PortalRules.drySamples(), "the sea was looked at " + asked[0]
                + " times, and the search is " + PortalRules.drySamples() + " columns");

        // Round ponds of every size that fits: the bank is found, and it is the
        // nearest bank there is.
        int ponds = 0;
        for (int radius = 1; radius < reach; radius++) {
            int wet = radius * radius;
            PortalRules.Surface pond = (east, south) -> top(east * east + south * south <= wet);
            PortalRules.Column bank = PortalRules.nearestDry(pond);
            check(bank != null && PortalRules.dry(pond, bank),
                    "a pond of radius " + radius + " has no bank within " + reach);
            if (bank != null) {
                int far = PortalRules.farOf(bank);
                check(far > wet && far <= (radius + 1) * (radius + 1),
                        "the bank of a pond of radius " + radius + " is found at " + bank
                                + ", and the nearest bank is nearer");
            }
            ponds++;
        }
        // A river down the middle: its near bank, the west one of the two.
        int rivers = 0;
        for (int half = 0; half < reach; half++) {
            int width = half;
            PortalRules.Column bank = PortalRules.nearestDry(
                    (east, south) -> top(Math.abs(east) <= width));
            check(bank != null && bank.equals(new PortalRules.Column(-(width + 1), 0)),
                    "a river " + (2 * width + 1) + " wide lands at " + bank + " and its near "
                            + "west bank is " + (width + 1) + " out");
            rivers++;
        }
        // Water past the whole search is the sea.
        int wide = 4 * reach * reach;
        check(PortalRules.nearestDry((east, south) -> top(east * east + south * south <= wide))
                        == null, "a lake wider than the search has a bank inside it");

        // Shores of every shape, against the slow answer: the column found is
        // dry, and nothing dry is nearer.
        Random random = new Random(1_002_026L);
        int shores = 0;
        for (int trial = 0; trial < 400; trial++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double shore = random.nextInt(2 * reach + 1) - reach;
            boolean coast = random.nextBoolean();
            int[][] lakes = new int[random.nextInt(4)][];
            for (int i = 0; i < lakes.length; i++) {
                lakes[i] = new int[] {random.nextInt(2 * reach + 1) - reach,
                        random.nextInt(2 * reach + 1) - reach, 1 + random.nextInt(Math.max(1, reach))};
            }
            PortalRules.Surface mixed = (east, south) -> {
                if (coast && east * Math.cos(angle) + south * Math.sin(angle) < shore) {
                    return PortalRules.Footing.WATER;
                }
                for (int[] lake : lakes) {
                    int de = east - lake[0];
                    int ds = south - lake[1];
                    if (de * de + ds * ds <= lake[2] * lake[2]) {
                        return PortalRules.Footing.WATER;
                    }
                }
                return PortalRules.Footing.GROUND;
            };
            PortalRules.Column found = PortalRules.nearestDry(mixed);
            int nearest = Integer.MAX_VALUE;
            for (int south = -reach; south <= reach; south++) {
                for (int east = -reach; east <= reach; east++) {
                    if (PortalRules.dry(mixed, new PortalRules.Column(east, south))) {
                        nearest = Math.min(nearest, east * east + south * south);
                    }
                }
            }
            if (nearest == Integer.MAX_VALUE) {
                check(found == null, "a shore with no dry column within reach lands at " + found);
            } else {
                check(found != null && PortalRules.dry(mixed, found)
                                && PortalRules.farOf(found) == nearest,
                        "a shore lands at " + found + " and the nearest dry column is "
                                + nearest + " away, squared");
            }
            shores++;
        }
        System.out.println("  the landing: the nearest dry column within " + reach + " blocks, "
                + search.size() + " columns nearest first, " + ponds + " ponds and " + rivers
                + " rivers banked, " + shores + " shores against the slow answer, the sea left "
                + "as found");
    }

    /** What stands at a column's top, from whether it is water. */
    private static PortalRules.Footing top(boolean water) {
        return water ? PortalRules.Footing.WATER : PortalRules.Footing.GROUND;
    }

    /** Which of the nine ways a column lies from the origin: the middle is 4. */
    private static int wayOf(PortalRules.Column one) {
        return (Integer.signum(one.east()) + 1) * 3 + Integer.signum(one.south()) + 1;
    }

    /**
     * A way in that finds only sea within the spiral walks the eight compass
     * lines for a shore: every stride of every line past the spiral's square,
     * nearest first, and the shore it lands on is the nearest one sampled.
     * Dry is nothing flowing and a top that holds a player up.
     */
    private static void theFarShore() {
        int near = PortalRules.DRY_SEARCH_BLOCKS;
        int reach = PortalRules.FAR_DRY_SEARCH_BLOCKS;
        int stride = PortalRules.FAR_DRY_STEP;
        PortalRules.Column origin = new PortalRules.Column(0, 0);
        List<PortalRules.Column> far = PortalRules.farDrySearch();
        if (stride < 1 || reach <= near || far.size() < 2) {
            // The numbers have said what is wrong; there is no far shore to prove.
            check(false, "the far stage strides " + stride + " out to " + reach + " past a spiral of "
                    + near + ", " + far.size() + " sample(s): nothing to walk");
            return;
        }
        int strides = reach / stride - near / stride;
        check(!far.isEmpty() && far.size() == PortalRules.farDrySamples()
                        && new HashSet<>(far).size() == far.size(),
                "the far search is " + far.size() + " columns, " + new HashSet<>(far).size()
                        + " distinct, and eight lines of " + strides + " strides is "
                        + PortalRules.farDrySamples());
        boolean nearestFirst = true;
        boolean onLine = true;
        boolean onStride = true;
        boolean pastNear = true;
        boolean inReach = true;
        int last = -1;
        int[] ways = new int[9];
        for (PortalRules.Column one : far) {
            int farOff = PortalRules.farOf(one);
            nearestFirst &= farOff >= last;
            last = farOff;
            int east = Math.abs(one.east());
            int south = Math.abs(one.south());
            int out = Math.max(east, south);
            onLine &= out > 0 && (east == 0 || east == out) && (south == 0 || south == out);
            onStride &= out % stride == 0;
            pastNear &= out > near;
            inReach &= out <= reach;
            ways[wayOf(one)]++;
        }
        check(nearestFirst, "the far search is not nearest first, so a landing crosses a wider "
                + "sea than it need");
        check(onLine, "the far search leaves the eight compass lines");
        check(onStride, "the far search does not stride " + stride + " blocks");
        check(pastNear, "the far search looks inside the spiral's square, which has looked "
                + "there already");
        check(inReach, "the far search looks further than " + reach + " blocks");
        boolean everyLine = ways[4] == 0;
        for (int way = 0; way < ways.length; way++) {
            everyLine &= way == 4 || ways[way] == strides;
        }
        check(everyLine, "the far search does not walk all eight compass lines " + strides
                + " strides each: " + Arrays.toString(ways));

        // Dry is both things, and either alone is not.
        check(PortalRules.dry((east, south) -> PortalRules.Footing.GROUND, origin), "ground is not dry");
        check(!PortalRules.dry((east, south) -> PortalRules.Footing.WATER, origin), "water is dry");
        check(!PortalRules.dry((east, south) -> new PortalRules.Footing(false, false), origin),
                "a fence post is called ground: nothing flows at it and it holds nobody up");
        check(!PortalRules.dry((east, south) -> new PortalRules.Footing(true, true), origin),
                "a flooded floor is called ground");

        // On land the far stage names its first sample, the nearest there is.
        PortalRules.Column first = PortalRules.nearestFarDry((east, south) -> PortalRules.Footing.GROUND);
        check(first != null && first.equals(far.get(0))
                        && PortalRules.farOf(first) == far.stream().mapToInt(PortalRules::farOf).min().orElse(-1),
                "on land the far stage lands at " + first + " rather than its nearest sample");

        // A fence post at the nearest sample is passed over for the ground at the next.
        PortalRules.Column post = far.get(0);
        PortalRules.Column ground = far.get(1);
        PortalRules.Column past = PortalRules.nearestFarDry((east, south) -> {
            PortalRules.Column at = new PortalRules.Column(east, south);
            return at.equals(post) ? new PortalRules.Footing(false, false)
                    : at.equals(ground) ? PortalRules.Footing.GROUND : PortalRules.Footing.WATER;
        });
        check(ground.equals(past), "a sea with a fence post at " + post + " and ground at " + ground
                + " lands at " + past);

        // A coast a hundred blocks off in each of the eight directions: the
        // spiral finds nothing, and the far stage lands on that line at the
        // coast, which is a whole number of strides out.
        int coast = 100;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int[][] lines = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        int coasts = 0;
        for (int line = 0; line < lines.length; line++) {
            int de = lines[line][0];
            int ds = lines[line][1];
            int along = de * de + ds * ds;
            PortalRules.Surface shore = (east, south) -> top(east * de + south * ds < coast * along);
            check(PortalRules.nearestDry(shore) == null, "the spiral finds a bank in a sea a hundred "
                    + "blocks from the " + names[line] + " shore");
            PortalRules.Column landed = PortalRules.nearestFarDry(shore);
            check(landed != null && landed.equals(new PortalRules.Column(de * coast, ds * coast)),
                    "a shore a hundred blocks " + names[line] + " is landed on at " + landed
                            + " rather than (" + de * coast + ", " + ds * coast + ")");
            coasts++;
        }
        // A coast east at every distance the far stage reaches: landed on at
        // the first stride past the water, never nearer, never a stride late.
        int easts = 0;
        for (int at = near + 1; at <= reach; at++) {
            int shoreAt = at;
            PortalRules.Column landed = PortalRules.nearestFarDry((east, south) -> top(east < shoreAt));
            int expected = (shoreAt + stride - 1) / stride * stride;
            check(landed != null && landed.equals(new PortalRules.Column(expected, 0)),
                    "a shore " + shoreAt + " blocks east is landed on at " + landed + " and the first "
                            + "stride past the water is " + expected);
            easts++;
        }

        // A round sea of radius a hundred: nothing dry within the spiral, and
        // the far stage lands on the nearest sample outside it.
        int round = coast * coast;
        PortalRules.Surface roundSea = (east, south) -> top(east * east + south * south <= round);
        check(PortalRules.nearestDry(roundSea) == null, "the spiral finds a bank in a round sea of radius " + coast);
        PortalRules.Column rim = PortalRules.nearestFarDry(roundSea);
        int nearestRim = far.stream().filter(one -> PortalRules.dry(roundSea, one))
                .mapToInt(PortalRules::farOf).min().orElse(-1);
        check(rim != null && PortalRules.dry(roundSea, rim) && PortalRules.farOf(rim) == nearestRim
                        && PortalRules.farOf(rim) > round,
                "a round sea of radius " + coast + " is landed on at " + rim + ", and the nearest dry "
                        + "sample is " + nearestRim + " away, squared");

        // A sea wider than the reach: null, after every look and no more.
        int[] asked = {0};
        int wide = 2 * reach * reach + 1;
        PortalRules.Column none = PortalRules.nearestFarDry((east, south) -> {
            asked[0]++;
            return top(east * east + south * south <= wide);
        });
        check(none == null, "a sea wider than the far reach has a shore at " + none);
        check(asked[0] == PortalRules.farDrySamples(), "a sea wider than the far reach was looked "
                + "at " + asked[0] + " times, and the far search is " + PortalRules.farDrySamples()
                + " columns");

        // Seas of every shape, against the slow answer: the shore found is
        // dry, and no sample is nearer.
        Random random = new Random(1_010_2026L);
        int seas = 0;
        for (int trial = 0; trial < 400; trial++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double shoreAt = near + random.nextInt(2 * reach);
            boolean disc = random.nextBoolean();
            int radius = near + random.nextInt(reach);
            PortalRules.Surface sea = (east, south) -> top(disc
                    ? east * east + south * south <= radius * radius
                    : east * Math.cos(angle) + south * Math.sin(angle) < shoreAt);
            PortalRules.Column found = PortalRules.nearestFarDry(sea);
            int nearest = far.stream().filter(one -> PortalRules.dry(sea, one))
                    .mapToInt(PortalRules::farOf).min().orElse(Integer.MAX_VALUE);
            if (nearest == Integer.MAX_VALUE) {
                check(found == null, "a sea past every sample is landed on at " + found);
            } else {
                check(found != null && PortalRules.dry(sea, found) && PortalRules.farOf(found) == nearest,
                        "a sea is landed on at " + found + " and the nearest dry sample is "
                                + nearest + " away, squared");
            }
            seas++;
        }
        System.out.println("  the far shore: past a sea, " + far.size() + " samples every " + stride
                + " blocks along eight compass lines out to " + reach + ", nearest first, " + coasts
                + " coasts landed on where they lie, " + easts + " eastern shores at the first stride "
                + "past the water, a round sea rimmed, " + seas + " seas against the slow answer, a "
                + "sea past the reach left as found, and a fence post not called ground");
    }

    /** The checks, checked. */
    private static void theControls() {
        Sky open = new Sky();
        check(PortalRules.around(open, 0, 0) == null, "a plane of open sky is a ring");
        check(open.asked < 4 * (PortalRules.MOST * PortalRules.MOST + 8),
                "a flood of the open sky asked " + open.asked + " cells before stopping");
        PortalRules.Plane solid = (across, up) -> PortalRules.Cell.OTHER;
        check(PortalRules.around(solid, 0, 0) == null, "solid rock is a ring");
        PortalRules.Plane frames = (across, up) -> PortalRules.Cell.FRAME;
        check(PortalRules.around(frames, 0, 0) == null, "a wall of frames is a ring");
        PortalRules.Plane nothing = (across, up) -> null;
        check(PortalRules.around(nothing, 0, 0) == null, "a plane that answers nothing is a ring");
        // A ring beside the used frame on its outside as well: the inside wins.
        Sky twin = ring(2, 3, false);
        Sky other = ring(2, 3, false);
        for (PortalRules.Spot spot : other.frames) {
            twin.frames.add(new PortalRules.Spot(spot.across() + 3, spot.up()));
        }
        PortalRules.Ring shared = PortalRules.around(twin, 2, 1);
        check(shared != null, "a pillar two rings share lights neither");
        List<String> allowed = new ArrayList<>();
        if (PortalRules.around(ring(1, 3, false), 0, -1) != null) {
            allowed.add("a one-wide opening");
        }
        if (PortalRules.around(ring(2, 2, false), 0, -1) != null) {
            allowed.add("a two-high opening");
        }
        check(allowed.isEmpty(), "the rule lights what it says it refuses: " + allowed);
        System.out.println("  the controls: open sky, rock, a wall of frames and a plane "
                + "that answers nothing refused, the open sky in " + open.asked
                + " look(s), a pillar two rings share lighting one of them");
    }
}
