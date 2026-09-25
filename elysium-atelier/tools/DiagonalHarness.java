package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The forty-five degrees, executed.
 *
 * <p>The one property this exists for is that <b>a staircase of little boxes
 * is one piece</b>. Two boxes touching at a corner do not touch: the game
 * walks a mob straight through the join, and from every angle in the world it
 * looks like a continuous rail. So the footprint is flood-filled here, by
 * edges only, and proved to be a single run from one corner of the block to
 * the other and through the middle where an ordinary fence keeps its post.
 *
 * <p>The three jars this replaces add no blocks and no items at all. What they
 * add is a direction, and a direction is exactly the kind of thing that is
 * either right or silently a dotted line.
 */
public final class DiagonalHarness {

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
        theKinds();
        theRuns();
        thePrices();
        theTags();
        theGeometry();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every diagonal one unbroken run from "
                + "corner to corner, every one of them through the middle where an "
                + "ordinary fence keeps its post, none of them filling half its "
                + "block, and not one recipe that makes more than it takes");
    }

    // ==================================================================

    private static void theKinds() {
        check(!DiagonalRules.KINDS.isEmpty(), "there are no diagonals at all");
        check(DiagonalRules.repeated().isEmpty(),
                "a diagonal declared twice, or already something else: "
                        + DiagonalRules.repeated());
        check(DiagonalRules.unladdered().isEmpty(),
                "two diagonals of one thickness: " + DiagonalRules.unladdered());
        check(DiagonalRules.thick().isEmpty(),
                "a diagonal thick enough to be a wall: " + DiagonalRules.thick());
        for (DiagonalRules.Kind kind : DiagonalRules.KINDS) {
            check(DiagonalRules.kind(kind.name()) == kind,
                    kind.name() + " cannot be looked up by its own name");
            check(!DiagonalRules.boxesFor(kind.name()).isEmpty(),
                    kind.name() + " is made of no boxes at all");
            check(DiagonalRules.volume(kind.name()) > 0,
                    kind.name() + " fills nothing");
        }
        System.out.println("  the kinds: " + DiagonalRules.KINDS.size()
                + " diagonal(s), each thicker than the last and none of them the "
                + "same shape as another");
    }

    // ==================================================================

    private static void theRuns() {
        check(DiagonalRules.broken().isEmpty(),
                "a diagonal with a gap in it, or one that misses a corner: "
                        + DiagonalRules.broken());
        for (DiagonalRules.Kind kind : DiagonalRules.KINDS) {
            check(DiagonalRules.pieces(kind.name()) == 1,
                    kind.name() + " is drawn in "
                            + DiagonalRules.pieces(kind.name()) + " pieces");
            check(DiagonalRules.connected(kind.name()),
                    kind.name() + " is not one unbroken run");
            check(DiagonalRules.spans(kind.name()),
                    kind.name() + " does not reach both corners");
            check(DiagonalRules.meetsTheMiddle(kind.name()),
                    kind.name() + " misses the middle of its own block");

            // And every box has to overlap the one before it, which is the
            // thing that makes the run a run rather than a row of squares.
            List<ArchRules.Box> boxes = DiagonalRules.boxesFor(kind.name());
            for (int at = 1; at < boxes.size(); at++) {
                ArchRules.Box before = boxes.get(at - 1);
                ArchRules.Box now = boxes.get(at);
                check(now.x1() < before.x2() && now.z1() < before.z2(),
                        kind.name() + " has a box starting at " + now.x1()
                                + " and the one before it ends at " + before.x2()
                                + ", so the two meet at a corner and the game does "
                                + "not count that as meeting");
            }
            // The run has to climb: every box further along than the last, or
            // it is a pile of squares in one place.
            for (int at = 1; at < boxes.size(); at++) {
                check(boxes.get(at).x1() > boxes.get(at - 1).x1()
                                && boxes.get(at).z1() > boxes.get(at - 1).z1(),
                        kind.name() + " does not go anywhere between box "
                                + (at - 1) + " and box " + at);
            }
        }
        System.out.println("  the runs: every diagonal one piece from corner to "
                + "corner, every box of it overlapping the one before, and every "
                + "one of them through the middle of its own block");
    }

    // ==================================================================

    private static void thePrices() {
        check(DiagonalRules.profitable().isEmpty(),
                "a recipe that makes more than it takes: "
                        + DiagonalRules.profitable());
        check(DiagonalRules.recoverable().isEmpty(),
                "a recipe you could craft and break for a profit: "
                        + DiagonalRules.recoverable());
        check(DiagonalRules.wasteful().isEmpty(),
                "a recipe nobody would ever use: " + DiagonalRules.wasteful());
        for (DiagonalRules.Kind kind : DiagonalRules.KINDS) {
            int volume = DiagonalRules.volume(kind.name());
            int units = kind.units();
            check(units * DiagonalRules.EIGHTH >= volume, kind.name()
                    + " is priced at " + units + " and fills " + volume
                    + ", which is more than it costs");
            check((units - 1) * DiagonalRules.EIGHTH < volume, kind.name()
                    + " is dearer than its own shape");
            check(kind.yields() <= kind.spends(), kind.name() + " yields "
                    + kind.yields() + " out of " + kind.spends());
        }
        Random random = new Random(1_302_775L);
        int walks = 0;
        for (int walk = 0; walk < 200_000; walk++) {
            int blocks = 1 + random.nextInt(64);
            int started = blocks;
            for (int step = 0; step < 8; step++) {
                DiagonalRules.Kind kind = DiagonalRules.KINDS.get(
                        random.nextInt(DiagonalRules.KINDS.size()));
                int crafts = blocks / kind.of();
                if (crafts == 0) {
                    break;
                }
                blocks -= crafts * kind.of();
                blocks += crafts * kind.made() * kind.recovers();
            }
            walks++;
            check(blocks <= started, "a chain of crafting and breaking started with "
                    + started + " and came back with " + blocks);
        }
        System.out.println("  the prices: every diagonal priced at its own volume "
                + "rounded up, and " + walks + " random chains of crafting and "
                + "breaking not one of which came back with more than it started "
                + "with");
    }

    // ==================================================================

    private static void theTags() {
        Set<String> ids = new LinkedHashSet<>();
        int fences = 0;
        int walls = 0;
        for (DiagonalRules.Run run : DiagonalRules.catalogue()) {
            check(ids.add(run.id()), run.id() + " is in the catalogue twice");
            Set<String> tags = DiagonalRules.tagsFor(run);
            check(!tags.isEmpty(), run.id() + " is in no tag at all");
            check(tags.contains(run.material().soft()
                            ? "minecraft:mineable/axe" : "minecraft:mineable/pickaxe"),
                    run.id() + " is worked by the wrong tool");
            if (tags.contains("minecraft:fences")) {
                fences++;
                check(DiagonalRules.FENCE.equals(run.kind()),
                        run.id() + " is in the game's fence tag and is not a fence");
            }
            if (tags.contains("minecraft:walls")) {
                walls++;
                check(DiagonalRules.WALL.equals(run.kind()),
                        run.id() + " is in the game's wall tag and is not a wall");
            }
            StringBuilder line = new StringBuilder("DTAG " + run.id());
            for (String tag : new java.util.TreeSet<>(tags)) {
                line.append(' ').append(tag);
            }
            System.out.println(line);
        }
        check(fences > 0 && walls > 0, fences + " fence(s) and " + walls
                + " wall(s) in the game's own tags, and a diagonal fence outside "
                + "the fence tag is one no ordinary fence meets");
        check(DiagonalRules.howMany()
                        == AtelierRules.MATERIALS.size() * DiagonalRules.KINDS.size(),
                "the catalogue is not every kind in every material");
        System.out.println("  the tags: " + DiagonalRules.howMany() + " diagonal(s) "
                + "in " + AtelierRules.MATERIALS.size() + " material(s), " + fences
                + " of them in the game's fence tag and " + walls + " in its wall tag");
    }

    // ==================================================================

    private static void theGeometry() {
        for (DiagonalRules.Kind kind : DiagonalRules.KINDS) {
            StringBuilder line = new StringBuilder("DIAG " + kind.name());
            for (ArchRules.Box box : DiagonalRules.boxesFor(kind.name())) {
                line.append(' ').append(box.x1()).append(',').append(box.y1())
                        .append(',').append(box.z1()).append(',').append(box.x2())
                        .append(',').append(box.y2()).append(',').append(box.z2());
            }
            System.out.println(line);
        }
        System.out.println("  the geometry: " + DiagonalRules.KINDS.size()
                + " diagonal(s) printed, for the generator's table to be checked "
                + "against");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        check(!DiagonalRules.faultsInRun("control", 2, true, true).isEmpty(),
                "a run drawn in two pieces is not reported, so the check that a "
                        + "diagonal has no gap in it cannot say yes");
        check(!DiagonalRules.faultsInRun("control", 1, false, true).isEmpty(),
                "a run that misses a corner is not reported");
        check(!DiagonalRules.faultsInRun("control", 1, true, false).isEmpty(),
                "a run that misses the middle of its block is not reported");
        check(DiagonalRules.faultsInRun("control", 1, true, true).isEmpty(),
                "an ordinary run is reported as broken, so the check says yes to "
                        + "everything");

        // The flood fill itself, on two footprints whose answer is known by
        // hand: a solid block is one piece, and the two squares of a
        // chequerboard corner are two.
        check(DiagonalRules.pieces(DiagonalRules.WALL) == 1,
                "a diagonal wall is counted as "
                        + DiagonalRules.pieces(DiagonalRules.WALL)
                        + " pieces, so the flood fill is not filling");
        boolean[] apart = new boolean[DiagonalRules.SPAN * DiagonalRules.SPAN];
        apart[0] = true;
        apart[DiagonalRules.SPAN + 1] = true;
        check(countPieces(apart) == 2,
                "two squares meeting only at their corners are counted as one "
                        + "piece, so the flood fill counts a gap as a join and the "
                        + "whole of this file proves nothing");
        boolean[] together = new boolean[DiagonalRules.SPAN * DiagonalRules.SPAN];
        together[0] = true;
        together[1] = true;
        check(countPieces(together) == 1,
                "two squares sharing an edge are counted as two pieces, so the "
                        + "flood fill reports a gap in everything");

        // And the rules' own flood fill, on the same two answers. The copy
        // above proves the arithmetic; this proves the file uses it.
        check(DiagonalRules.piecesIn(apart) == 2,
                "the rules count two squares meeting only at their corners as one "
                        + "piece, so the flood fill counts a gap as a join and "
                        + "every diagonal here could have a hole in it");
        check(DiagonalRules.piecesIn(together) == 1,
                "the rules count two squares sharing an edge as two pieces");

        boolean[] whole = new boolean[DiagonalRules.SPAN * DiagonalRules.SPAN];
        java.util.Arrays.fill(whole, true);
        boolean[] nothing = new boolean[DiagonalRules.SPAN * DiagonalRules.SPAN];
        check(DiagonalRules.spansPlan(whole),
                "a footprint filling the whole block is said not to reach its "
                        + "corners, so the corner check says no to everything");
        check(!DiagonalRules.spansPlan(nothing),
                "an empty footprint is said to reach both corners, so the corner "
                        + "check cannot say no and a run that stops short would "
                        + "never be reported");
        check(DiagonalRules.meetsTheMiddlePlan(whole),
                "a footprint filling the whole block is said to miss the middle");
        check(!DiagonalRules.meetsTheMiddlePlan(nothing),
                "an empty footprint is said to pass through the middle, so that "
                        + "check cannot say no");

        check(CourseRules.pays(8, 4, 2),
                "the profitability arithmetic cannot say yes");
        check(!CourseRules.pays(2, 12, 3),
                "the profitability arithmetic says yes to everything");
        check(CourseRules.spent(4) - CourseRules.yielded(1, 1)
                        > DiagonalRules.MOST_WASTED,
                "a recipe eating four blocks and giving back one eighth is inside "
                        + "the waste ceiling, so the ceiling would allow anything");

        System.out.println("  the controls: a run in two pieces caught, a run "
                + "missing a corner caught, and the flood fill proved on a pair of "
                + "squares that meet only at their corners");
    }

    /**
     * The same flood fill, over a footprint handed in rather than measured.
     *
     * <p>A copy, deliberately: the point of the controls above is to prove the
     * counting works on an answer known by hand, and running the file's own
     * copy would prove only that it agrees with itself.
     */
    private static int countPieces(boolean[] plan) {
        int span = DiagonalRules.SPAN;
        boolean[] seen = new boolean[plan.length];
        int found = 0;
        for (int start = 0; start < plan.length; start++) {
            if (!plan[start] || seen[start]) {
                continue;
            }
            found++;
            List<Integer> waiting = new ArrayList<>();
            waiting.add(start);
            seen[start] = true;
            while (!waiting.isEmpty()) {
                int at = waiting.remove(waiting.size() - 1);
                int x = at / span;
                int z = at % span;
                int[][] beside = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
                for (int[] next : beside) {
                    if (next[0] < 0 || next[0] >= span
                            || next[1] < 0 || next[1] >= span) {
                        continue;
                    }
                    int index = next[0] * span + next[1];
                    if (plan[index] && !seen[index]) {
                        seen[index] = true;
                        waiting.add(index);
                    }
                }
            }
        }
        return found;
    }
}
