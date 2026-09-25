package com.elysium.atelier.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The forty-five degrees the game does not have. The JDK and nothing else.
 *
 * <h2>What the three jars this replaces actually do</h2>
 *
 * Nothing, by weight: between them they add no blocks and no items at all.
 * What they add is a <em>direction</em>. A fence in this game meets the fence
 * north, south, east and west of it and nothing in between, so a wall that is
 * not on the grid is a staircase of posts, and every fenced field in every
 * world is a rectangle.
 *
 * <h2>And what this does instead</h2>
 *
 * A piece that runs corner to corner. Three of them — a fence, a wall and a
 * pane — in every material, and the whole of the difficulty is that a
 * diagonal in a game of cubes is not a line, it is a staircase of little
 * boxes, and a staircase of little boxes has two ways of being wrong that
 * both render perfectly:
 *
 * <ol>
 *   <li><b>It can have a gap.</b> Boxes that meet only at a corner do not
 *       meet: a mob walks through the join, a player falls off the bridge, and
 *       from every angle it looks like a continuous rail. So the filled
 *       sixteenths are flood-filled here and proved to be <em>one</em> piece.
 *   <li><b>It can miss the corner.</b> A diagonal that stops one sixteenth
 *       short does not meet the diagonal in the next block, and a run of them
 *       is a dotted line. So both ends are proved to reach the corner.
 * </ol>
 *
 * <p>And a third, which is why it is worth doing at all: a diagonal that does
 * not pass through the middle of its own block does not meet the fence post
 * of an ordinary fence beside it, so the two kinds cannot be mixed and the
 * diagonal is only good for diagonals.
 */
public final class DiagonalRules {

    private DiagonalRules() {
    }

    /** A block, in the eighths everything in this module is priced in. */
    public static final int WHOLE = AtelierRules.WHOLE;

    /** A block, in the sixteenths a model is drawn in. */
    public static final int SPAN = CourseRules.SPAN;

    /** The filled volume one eighth of a block is worth, in cubic sixteenths. */
    public static final int EIGHTH = CourseRules.EIGHTH;

    // ==================================================================
    // The kinds
    // ==================================================================

    public static final String FENCE = "diagonal_fence";
    public static final String WALL = "diagonal_wall";
    public static final String PANE = "diagonal_pane";

    /**
     * One diagonal.
     *
     * @param name what it is called, which is also half its block id
     * @param wide how thick the run is, in sixteenths
     * @param made how many one crafting makes
     * @param of   how many blocks of material one crafting takes
     */
    public record Kind(String name, int wide, int made, int of) {

        public Kind {
            Objects.requireNonNull(name, "a diagonal with no name");
            if (wide < 2 || wide > SPAN / 2) {
                throw new IllegalArgumentException(name + " is " + wide
                        + " sixteenths thick, which is either a line with no "
                        + "width or half a block");
            }
            if (made < 1 || of < 1) {
                throw new IllegalArgumentException("a recipe that makes nothing, "
                        + "or takes nothing, is not a recipe");
            }
        }

        /** What this one costs, in eighths: its own volume, rounded up. */
        public int units() {
            return DiagonalRules.units(name);
        }

        /** What one crafting consumes, in units. */
        public int spends() {
            return CourseRules.spent(of);
        }

        /** What one crafting produces, in units. */
        public int yields() {
            return CourseRules.yielded(made, units());
        }

        /** Whether its recipe makes more than it takes. */
        public boolean profitable() {
            return CourseRules.pays(units(), made, of);
        }

        /** How many blocks breaking one gives back. Rounded down, always. */
        public int recovers() {
            return CourseRules.recovered(units());
        }
    }

    /** The three, thinnest first. */
    public static final List<Kind> KINDS = List.of(
            new Kind(PANE, 2, 12, 3),
            new Kind(FENCE, 3, 8, 3),
            new Kind(WALL, 4, 6, 3));

    /** A kind by name, or null. */
    public static Kind kind(String name) {
        for (Kind kind : KINDS) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    // ==================================================================
    // The staircase
    // ==================================================================

    /**
     * The boxes one diagonal is drawn and collided as.
     *
     * <p>A run of overlapping squares from one corner to the other. They
     * overlap rather than abut because two boxes meeting at a corner do not
     * meet at all — the game walks a mob straight through the join — and the
     * overlap is what {@link #connected(String)} proves is there.
     */
    public static List<ArchRules.Box> boxesFor(String name) {
        Kind kind = kind(name);
        if (kind == null) {
            throw new IllegalArgumentException("no shape for " + name);
        }
        int wide = kind.wide();
        int step = wide - 1;
        List<ArchRules.Box> made = new ArrayList<>();
        for (int at = 0; at < SPAN; at += step) {
            int to = Math.min(at + wide, SPAN);
            if (to - at < 1) {
                continue;
            }
            made.add(new ArchRules.Box(at, 0, at, to, SPAN, to));
        }
        return List.copyOf(made);
    }

    /** The voxels a diagonal fills, indexed {@code x * 256 + y * 16 + z}. */
    public static boolean[] filled(String name) {
        boolean[] grid = new boolean[SPAN * SPAN * SPAN];
        for (ArchRules.Box box : boxesFor(name)) {
            for (int x = box.x1(); x < box.x2(); x++) {
                for (int y = box.y1(); y < box.y2(); y++) {
                    for (int z = box.z1(); z < box.z2(); z++) {
                        grid[x * SPAN * SPAN + y * SPAN + z] = true;
                    }
                }
            }
        }
        return grid;
    }

    /** How many of the block's 4096 sixteenths a diagonal fills. */
    public static int volume(String name) {
        int count = 0;
        for (boolean one : filled(name)) {
            if (one) {
                count++;
            }
        }
        return count;
    }

    /** What a diagonal costs, in eighths: its own volume, rounded up. */
    public static int units(String name) {
        return Math.max(1, (volume(name) + EIGHTH - 1) / EIGHTH);
    }

    /** The footprint of a diagonal: which of the 256 columns it fills. */
    public static boolean[] plan(String name) {
        boolean[] grid = filled(name);
        boolean[] plan = new boolean[SPAN * SPAN];
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                plan[x * SPAN + z] = CourseRules.heightAt(grid, x, z) > 0;
            }
        }
        return plan;
    }

    /**
     * How many separate pieces the footprint is in, counting only squares that
     * share an edge.
     *
     * <p>The whole point. Two squares touching at a corner are two pieces, and
     * the gap between them is one a mob walks through and nothing looks wrong.
     */
    public static int pieces(String name) {
        return piecesIn(plan(name));
    }

    /**
     * The same, over a footprint handed in rather than measured.
     *
     * <p>Split out because over a healthy roster this can only ever answer
     * one, which is what a flood fill that has stopped filling also answers.
     * The harness hands it two squares that meet only at their corners.
     */
    public static int piecesIn(boolean[] plan) {
        boolean[] seen = new boolean[plan.length];
        int found = 0;
        for (int start = 0; start < plan.length; start++) {
            if (!plan[start] || seen[start]) {
                continue;
            }
            found++;
            Deque<Integer> waiting = new ArrayDeque<>();
            waiting.push(start);
            seen[start] = true;
            while (!waiting.isEmpty()) {
                int at = waiting.pop();
                int x = at / SPAN;
                int z = at % SPAN;
                int[][] beside = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
                for (int[] next : beside) {
                    if (next[0] < 0 || next[0] >= SPAN
                            || next[1] < 0 || next[1] >= SPAN) {
                        continue;
                    }
                    int index = next[0] * SPAN + next[1];
                    if (plan[index] && !seen[index]) {
                        seen[index] = true;
                        waiting.push(index);
                    }
                }
            }
        }
        return found;
    }

    /** Whether the footprint is one unbroken run. */
    public static boolean connected(String name) {
        return pieces(name) == 1;
    }

    /** Whether it reaches both corners it is meant to run between. */
    public static boolean spans(String name) {
        return spansPlan(plan(name));
    }

    /** The same, over a footprint handed in rather than measured. */
    public static boolean spansPlan(boolean[] plan) {
        return plan[0] && plan[(SPAN - 1) * SPAN + (SPAN - 1)];
    }

    /**
     * Whether it passes through the middle of its own block, where an ordinary
     * fence puts its post.
     *
     * <p>Without this the two kinds cannot be mixed: a diagonal that misses
     * the centre leaves a gap where it meets a straight run, and a fence with
     * one gap in it is a fence.
     */
    public static boolean meetsTheMiddle(String name) {
        return meetsTheMiddlePlan(plan(name));
    }

    /** The same, over a footprint handed in rather than measured. */
    public static boolean meetsTheMiddlePlan(boolean[] plan) {
        int middle = SPAN / 2;
        for (int x = middle - 1; x <= middle; x++) {
            for (int z = middle - 1; z <= middle; z++) {
                if (!plan[x * SPAN + z]) {
                    return false;
                }
            }
        }
        return true;
    }

    // ==================================================================
    // What has to be true
    // ==================================================================

    /** Every diagonal with a gap in it, or that misses a corner. Must be empty. */
    public static List<String> broken() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            bad.addAll(faultsInRun(kind.name(), pieces(kind.name()),
                    spans(kind.name()), meetsTheMiddle(kind.name())));
        }
        return List.copyOf(bad);
    }

    /**
     * What is wrong with a run of this shape, if anything.
     *
     * <p>Split out from the walk so the harness can hand it a run built to be
     * caught: over a healthy roster all three of these can only answer no.
     */
    public static List<String> faultsInRun(String name, int pieces, boolean spans,
                                           boolean middle) {
        List<String> bad = new ArrayList<>();
        if (pieces != 1) {
            bad.add(name + " is drawn in " + pieces + " pieces that meet only at "
                    + "their corners, which the game does not count as meeting: a "
                    + "mob walks straight through the join and it looks like a "
                    + "continuous rail from every angle");
        }
        if (!spans) {
            bad.add(name + " does not reach both corners of its own block, so a run "
                    + "of them is a dotted line");
        }
        if (!middle) {
            bad.add(name + " does not pass through the middle of its block, so it "
                    + "does not meet the post of an ordinary fence beside it and "
                    + "the two kinds cannot be mixed");
        }
        return List.copyOf(bad);
    }

    /** Every diagonal thick enough to be a wall rather than a rail. Must be empty. */
    public static List<String> thick() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (volume(kind.name()) * 2 > SPAN * SPAN * SPAN) {
                bad.add(kind.name() + " fills more than half its block, which is a "
                        + "wall, and the game has walls");
            }
        }
        return List.copyOf(bad);
    }

    /** Every pair of kinds the same shape, or the same name. Must be empty. */
    public static List<String> repeated() {
        List<String> bad = new ArrayList<>();
        Map<String, String> shapes = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Kind kind : KINDS) {
            if (!seen.add(kind.name())) {
                bad.add(kind.name() + " is declared twice");
            }
            StringBuilder key = new StringBuilder();
            for (boolean one : filled(kind.name())) {
                key.append(one ? '1' : '0');
            }
            String already = shapes.putIfAbsent(key.toString(), kind.name());
            if (already != null) {
                bad.add(kind.name() + " and " + already + " fill exactly the same "
                        + "sixteenths, so they are one block with two names");
            }
            if (AtelierRules.shape(kind.name()) != null
                    || CourseRules.kind(kind.name()) != null
                    || TrimRules.kind(kind.name()) != null
                    || JoinRules.KINDS.contains(kind.name())) {
                bad.add(kind.name() + " is already something else this module makes");
            }
        }
        return List.copyOf(bad);
    }

    /** The three, thinnest first, each thicker than the last. Must be empty. */
    public static List<String> unladdered() {
        List<String> bad = new ArrayList<>();
        int before = 0;
        for (Kind kind : KINDS) {
            if (kind.wide() <= before) {
                bad.add(kind.name() + " is " + kind.wide() + " sixteenths thick and "
                        + "the one before it is " + before + ", so two of the three "
                        + "are the same rail with two names");
            }
            before = kind.wide();
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The economy, which is AtelierRules's
    // ==================================================================

    /** Every kind whose recipe makes more than it takes. Must be empty. */
    public static List<String> profitable() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (kind.profitable()) {
                bad.add(kind.name() + " makes " + kind.yields() + " eighths out of "
                        + kind.spends() + ", so a loop of crafting it pays");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind you could craft and break for a profit. Must be empty. */
    public static List<String> recoverable() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (kind.recovers() * WHOLE * kind.made() > kind.spends()) {
                bad.add(kind.name() + ": " + kind.of() + " blocks in, "
                        + kind.recovers() * kind.made() + " back out");
            }
        }
        return List.copyOf(bad);
    }

    /** The most a recipe may waste, in units: a whole block, as everywhere else. */
    public static final int MOST_WASTED = WHOLE;

    /** Every kind whose recipe throws away more than a block. Must be empty. */
    public static List<String> wasteful() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            int wasted = kind.spends() - kind.yields();
            if (wasted > MOST_WASTED) {
                bad.add(kind.name() + " wastes " + wasted + " units a craft, which "
                        + "is more than a whole block, and nobody will ever make one");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The catalogue
    // ==================================================================

    /** One diagonal in one material. */
    public record Run(String kind, AtelierRules.Material material) {

        public Run {
            if (DiagonalRules.kind(kind) == null) {
                throw new IllegalArgumentException(kind + " is not a diagonal");
            }
            Objects.requireNonNull(material, "a diagonal made of nothing");
        }

        /** Its block id, without the namespace. */
        public String id() {
            return material.name() + "_" + kind;
        }
    }

    /** Every diagonal this module adds. */
    public static List<Run> catalogue() {
        List<Run> made = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (Kind kind : KINDS) {
                made.add(new Run(kind.name(), material));
            }
        }
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }

    /** The game tags every diagonal belongs in. */
    public static Set<String> tagsFor(Run run) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(run.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");
        if (FENCE.equals(run.kind())) {
            tags.add("minecraft:fences");
            if (run.material().soft()) {
                tags.add("minecraft:wooden_fences");
            }
        }
        if (WALL.equals(run.kind())) {
            tags.add("minecraft:walls");
        }
        return Set.copyOf(tags);
    }
}
