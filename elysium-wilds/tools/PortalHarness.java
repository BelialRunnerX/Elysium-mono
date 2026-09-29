package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                + "costs one compass search and drops a player in the gentlest crossing");
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
