package com.elysium.industry.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Multiblocks: what a shape is, and the four things one has to be.
 *
 * <h2>Why this is arithmetic and not a block</h2>
 *
 * A multiblock is the one thing in a tech mod a player can spend twenty
 * minutes building and get no feedback from. Every failure mode is the same
 * from the outside — you put the last block in and nothing happens — and the
 * difference between a good implementation and a bad one is entirely in what
 * it can tell you about <em>why</em>.
 *
 * <p>So the checker is pure arithmetic over a grid of symbols, and the world
 * is only ever asked "what block is at this position". Everything that can be
 * wrong about a shape can then be swept: every rotation, every mirror, every
 * one-block mutation of a correct build.
 *
 * <h2>The four things</h2>
 *
 * <ol>
 * <li><b>One assembly.</b> No two shapes may be buildable out of the same
 *     arrangement of blocks, in any rotation or mirror of either. A machine
 *     that can be built by accident while building a different one is a
 *     machine whose recipe book is a lie.</li>
 * <li><b>A refusal names the block.</b> {@link #wrongAt} gives the position
 *     and what was wanted there. "Nothing happens" is the worst possible
 *     answer for something that took twenty minutes.</li>
 * <li><b>Everything comes back.</b> {@link #billOf} is what a shape consumes,
 *     and disassembly returns exactly it — not most of it.</li>
 * <li><b>A correct shape is always accepted.</b> The positive control,
 *     without which a checker that refuses everything passes every test in
 *     this file.</li>
 * </ol>
 *
 * <h2>The grid</h2>
 *
 * A shape is a list of layers, bottom first; each layer is a list of rows,
 * north first; each row is a string, one character per column, west first. A
 * space means "this position is not part of the machine and nothing is
 * checked there". Every other character is a part, looked up in the shape's
 * own key.
 */
public final class ShapeRules {

    private ShapeRules() {
    }

    /** What a position not part of the machine is written as. */
    public static final char NOTHING = ' ';

    /** What the block a player interacts with is written as. */
    public static final char CONTROLLER = 'X';

    /**
     * One multiblock.
     *
     * @param id     what the machine is called
     * @param layers the grid, bottom layer first, north row first
     * @param key    what each character in the grid stands for
     */
    public record Shape(String id, List<String> layers, Map<Character, String> key) {

        public Shape {
            Objects.requireNonNull(id, "a shape with no name");
            layers = List.copyOf(Objects.requireNonNull(layers, "a shape with no layers"));
            key = Map.copyOf(Objects.requireNonNull(key, "a shape with no key"));
            if (layers.isEmpty()) {
                throw new IllegalArgumentException(id + " has no layers at all");
            }
        }

        /** How many layers high. */
        public int tall() {
            return layers.size();
        }

        /** How many rows deep one layer is. */
        public int deep() {
            return rowsOf(layers.get(0)).size();
        }

        /** How many columns wide one row is. */
        public int wide() {
            return rowsOf(layers.get(0)).get(0).length();
        }

        /** The grid as characters: {@code at(layer, row, column)}. */
        public char at(int layer, int row, int column) {
            return rowsOf(layers.get(layer)).get(row).charAt(column);
        }
    }

    /**
     * One layer's rows.
     *
     * <p>A layer is written as one string with newlines in it, because a
     * shape written as a list of lists of strings is a shape nobody can read
     * in a source file and therefore a shape nobody checks by eye.
     */
    public static List<String> rowsOf(String layer) {
        return List.of(layer.split("\n", -1));
    }

    /** The whole grid of a shape as a three-dimensional array of characters. */
    public static char[][][] gridOf(Shape shape) {
        char[][][] grid = new char[shape.tall()][shape.deep()][shape.wide()];
        for (int layer = 0; layer < shape.tall(); layer++) {
            List<String> rows = rowsOf(shape.layers().get(layer));
            for (int row = 0; row < shape.deep(); row++) {
                // A ragged shape is reported by faultsInShape. It must not
                // also throw here: an index out of bounds is a worse answer
                // than the sentence that was about to be written, and a rule
                // that crashes on the input it was written to judge is a rule
                // nobody can switch off to see what it was catching.
                String here = row < rows.size() ? rows.get(row) : "";
                for (int column = 0; column < shape.wide(); column++) {
                    grid[layer][row][column] = column < here.length()
                            ? here.charAt(column) : NOTHING;
                }
            }
        }
        return grid;
    }

    // ==================================================================
    // Turning and mirroring
    // ==================================================================

    /** One grid turned a quarter turn about the vertical. */
    public static char[][][] turned(char[][][] grid) {
        int tall = grid.length;
        int deep = grid[0].length;
        int wide = grid[0][0].length;
        char[][][] made = new char[tall][wide][deep];
        for (int layer = 0; layer < tall; layer++) {
            for (int row = 0; row < deep; row++) {
                for (int column = 0; column < wide; column++) {
                    made[layer][column][deep - 1 - row] = grid[layer][row][column];
                }
            }
        }
        return made;
    }

    /** One grid reflected west to east. */
    public static char[][][] mirrored(char[][][] grid) {
        int tall = grid.length;
        int deep = grid[0].length;
        int wide = grid[0][0].length;
        char[][][] made = new char[tall][deep][wide];
        for (int layer = 0; layer < tall; layer++) {
            for (int row = 0; row < deep; row++) {
                for (int column = 0; column < wide; column++) {
                    made[layer][row][wide - 1 - column] = grid[layer][row][column];
                }
            }
        }
        return made;
    }

    /**
     * Every way one shape can be stood in the world, with duplicates gone.
     *
     * <p>Four turns times two handednesses is eight, and a shape with any
     * symmetry in it has fewer than eight distinct ones. Deduplicating
     * matters: it is what makes "how many ways can this be built" a number
     * rather than always eight, and it is the number the check about two
     * shapes colliding is really about.
     */
    public static List<char[][][]> orientations(Shape shape) {
        List<char[][][]> made = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        char[][][] grid = gridOf(shape);
        for (int handed = 0; handed < 2; handed++) {
            char[][][] here = handed == 0 ? grid : mirrored(grid);
            for (int turn = 0; turn < 4; turn++) {
                if (seen.add(written(here))) {
                    made.add(here);
                }
                here = turned(here);
            }
        }
        return List.copyOf(made);
    }

    /** One grid as a string, for comparing two of them. */
    public static String written(char[][][] grid) {
        StringBuilder out = new StringBuilder();
        for (char[][] layer : grid) {
            for (char[] row : layer) {
                out.append(row).append('/');
            }
            out.append('|');
        }
        return out.toString();
    }

    // ==================================================================
    // Matching
    // ==================================================================

    /**
     * Where a build stops being the shape, or null when it is the shape.
     *
     * <p>The whole reason this returns a position rather than a boolean.
     *
     * @param shape what was being built
     * @param grid  what the world actually has there, same size
     * @param key   what each of the shape's characters stands for; the world's
     *              grid holds the same characters
     */
    public static String wrongAt(Shape shape, char[][][] grid) {
        char[][][] want = gridOf(shape);
        if (grid.length != want.length || grid[0].length != want[0].length
                || grid[0][0].length != want[0][0].length) {
            return "the space is " + grid.length + " by " + grid[0].length
                    + " by " + grid[0][0].length + " and " + shape.id()
                    + " is " + want.length + " by " + want[0].length + " by "
                    + want[0][0].length;
        }
        for (int layer = 0; layer < want.length; layer++) {
            for (int row = 0; row < want[0].length; row++) {
                for (int column = 0; column < want[0][0].length; column++) {
                    char wanted = want[layer][row][column];
                    if (wanted == NOTHING) {
                        continue;
                    }
                    char found = grid[layer][row][column];
                    if (found != wanted) {
                        return "at layer " + layer + ", row " + row
                                + ", column " + column + " there should be "
                                + named(shape, wanted) + " and there is "
                                + named(shape, found);
                    }
                }
            }
        }
        return null;
    }

    /** What one of a shape's characters stands for, in words. */
    public static String named(Shape shape, char part) {
        if (part == NOTHING) {
            return "nothing";
        }
        String found = shape.key().get(part);
        return found == null ? "something this shape has no name for ('"
                + part + "')" : found;
    }

    /** Whether a build is this shape, in any rotation or mirror of it. */
    public static boolean matches(Shape shape, char[][][] grid) {
        for (char[][][] one : orientations(shape)) {
            if (sameAs(one, grid)) {
                return true;
            }
        }
        return false;
    }

    /** Whether two grids agree everywhere the first one cares about. */
    public static boolean sameAs(char[][][] want, char[][][] grid) {
        if (grid.length != want.length || grid[0].length != want[0].length
                || grid[0][0].length != want[0][0].length) {
            return false;
        }
        for (int layer = 0; layer < want.length; layer++) {
            for (int row = 0; row < want[0].length; row++) {
                for (int column = 0; column < want[0][0].length; column++) {
                    if (want[layer][row][column] == NOTHING) {
                        continue;
                    }
                    if (want[layer][row][column] != grid[layer][row][column]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ==================================================================
    // What a shape costs
    // ==================================================================

    /** How many of each block a shape is built out of. */
    public static Map<String, Integer> billOf(Shape shape) {
        Map<String, Integer> made = new LinkedHashMap<>();
        char[][][] grid = gridOf(shape);
        for (char[][] layer : grid) {
            for (char[] row : layer) {
                for (char part : row) {
                    if (part == NOTHING) {
                        continue;
                    }
                    String block = shape.key().get(part);
                    if (block == null) {
                        continue;
                    }
                    made.merge(block, 1, Integer::sum);
                }
            }
        }
        return Map.copyOf(made);
    }

    /** How many blocks a shape is, altogether. */
    public static int blocksIn(Shape shape) {
        int count = 0;
        for (int value : billOf(shape).values()) {
            count += value;
        }
        return count;
    }

    /** Where in the grid the controller is, as layer, row, column. */
    public static int[] controllerOf(Shape shape) {
        char[][][] grid = gridOf(shape);
        for (int layer = 0; layer < grid.length; layer++) {
            for (int row = 0; row < grid[0].length; row++) {
                for (int column = 0; column < grid[0][0].length; column++) {
                    if (grid[layer][row][column] == CONTROLLER) {
                        return new int[] {layer, row, column};
                    }
                }
            }
        }
        return null;
    }

    // ==================================================================
    // What has to be true of a set of shapes
    // ==================================================================

    /** Everything wrong with one shape on its own. */
    public static List<String> faultsInShape(Shape shape) {
        List<String> bad = new ArrayList<>();
        int rows = -1;
        int columns = -1;
        for (int layer = 0; layer < shape.tall(); layer++) {
            List<String> here = rowsOf(shape.layers().get(layer));
            if (rows < 0) {
                rows = here.size();
                columns = here.get(0).length();
            }
            if (here.size() != rows) {
                bad.add(shape.id() + " layer " + layer + " is " + here.size()
                        + " row(s) deep and layer 0 is " + rows
                        + ", so the shape is not a box and nothing can walk it");
            }
            for (String row : here) {
                if (row.length() != columns) {
                    bad.add(shape.id() + " layer " + layer + " has a row "
                            + row.length() + " wide and another " + columns
                            + " wide, so the shape is ragged");
                }
            }
        }
        if (!bad.isEmpty()) {
            // Everything below walks the grid as a box. A shape that is not
            // one cannot be walked, and an index out of bounds is a worse
            // answer than the sentence already in the list.
            return bad;
        }
        int controllers = 0;
        Set<Character> used = new LinkedHashSet<>();
        char[][][] grid = gridOf(shape);
        for (char[][] layer : grid) {
            for (char[] row : layer) {
                for (char part : row) {
                    if (part == NOTHING) {
                        continue;
                    }
                    used.add(part);
                    if (part == CONTROLLER) {
                        controllers++;
                    }
                    if (!shape.key().containsKey(part)) {
                        bad.add(shape.id() + " uses '" + part + "' and its key "
                                + "does not say what that is, so a player is "
                                + "told to place something nameless");
                    }
                }
            }
        }
        if (controllers != 1) {
            bad.add(shape.id() + " has " + controllers + " controller(s), and "
                    + "a machine with none is one nobody can use while a "
                    + "machine with two is one that comes apart in two places");
        }
        for (Character part : shape.key().keySet()) {
            if (!used.contains(part)) {
                bad.add(shape.id() + " names '" + part + "' in its key and "
                        + "never uses it, so the key is a list of what "
                        + "somebody meant to build");
            }
        }
        if (blocksIn(shape) < 2) {
            bad.add(shape.id() + " is " + blocksIn(shape) + " block(s), which "
                    + "is not a multiblock");
        }
        return bad;
    }

    /**
     * Everything wrong with a set of shapes taken together.
     *
     * <p>One rule, and it is the one that matters: no arrangement of blocks
     * may be two machines. Checked over every orientation of every shape
     * against every orientation of every other, because a shape that collides
     * with another only when one of them is turned is a machine that can be
     * built by accident and only sometimes.
     */
    public static List<String> faultsInSet(List<Shape> shapes) {
        List<String> bad = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Shape shape : shapes) {
            if (!names.add(shape.id())) {
                bad.add(shape.id() + " names two shapes");
            }
            for (char[][][] one : orientations(shape)) {
                String written = written(one);
                String already = seen.put(written, shape.id());
                if (already != null && !already.equals(shape.id())) {
                    bad.add(already + " and " + shape.id() + " are the same "
                            + "arrangement of blocks in some rotation, so one "
                            + "of them can be built by accident while "
                            + "building the other");
                }
            }
        }
        return bad;
    }
}
