package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The courses a wall is laid in: the cuts of a block, and the ways up one.
 * The JDK and nothing else.
 *
 * <h2>Why this is a third file rather than more shapes</h2>
 *
 * {@link AtelierRules} is furniture — a chair is finished when it looks like a
 * chair. {@link ArchRules} is the outside of a building — a roof is not
 * finished until a closed one can be built. This file is the wall itself, and
 * it has a third property that neither of the others has:
 *
 * <p><b>A stair has to be walkable.</b> A block shaped like a staircase that a
 * player has to jump up is not a staircase, it is a decoration, and nothing
 * about the model says which one you have built. Every stair here is proved to
 * climb: at least two heights, no single riser taller than half a block, and a
 * top that actually reaches the next block up. Eight mods in this pack add
 * stair variants between them and not one of them checks that.
 *
 * <h2>Everything here is priced by its own volume</h2>
 *
 * A shape in {@link AtelierRules} carries a price somebody chose. A course
 * does not: {@link #units(String)} counts the filled sixteenths of the block
 * and rounds up to the next eighth. So a piece cannot be mispriced, only
 * mis-shaped, and the shape is the thing you can see.
 *
 * <h2>And nothing here is something the game already has</h2>
 *
 * The game has twenty-seven of this module's thirty-three materials in stairs
 * and slabs already, and twelve of them in walls. {@link #VANILLA_ALREADY}
 * writes that down, the catalogue subtracts it, and the validator checks the
 * list against the game's own blockstates rather than trusting it — which is
 * the check {@link JoinRules} should have had and did not.
 */
public final class CourseRules {

    private CourseRules() {
    }

    /** A block, in the eighths everything in this module is priced in. */
    public static final int WHOLE = AtelierRules.WHOLE;

    /** A block, in the sixteenths a model is drawn in. */
    public static final int SPAN = 16;

    /** The filled volume one eighth of a block is worth, in cubic sixteenths. */
    public static final int EIGHTH = SPAN * SPAN * SPAN / WHOLE;

    // ==================================================================
    // The families, and what each family has to satisfy
    // ==================================================================

    /** A plain cut of a block: the shapes the game has some of and not others. */
    public static final String CUT = "cut";

    /** A way up: proved walkable rather than merely stair-shaped. */
    public static final String STEP = "step";

    /** Something to stand on, or to stop you falling off it. */
    public static final String DECK = "deck";

    /** Something to climb. */
    public static final String CLIMB = "climb";

    public static final List<String> FAMILIES = List.of(CUT, STEP, DECK, CLIMB);

    /** A deck piece you stand on. */
    public static final String FLOOR = "floor";

    /** A deck piece that stops you walking off one. */
    public static final String RAIL = "rail";

    // ==================================================================
    // The kinds
    // ==================================================================

    public static final String STAIR = "stair";
    public static final String SLAB = "slab";
    public static final String VERTICAL_SLAB = "vertical_slab";
    public static final String WALL = "wall";

    public static final String COMPACT = "compact_stairs";
    public static final String LOFT = "loft_stairs";
    public static final String TERRACE = "terrace_stairs";
    public static final String SKYLINE = "skyline_stairs";
    public static final String BULK = "bulk_stairs";

    public static final String BALCONY = "balcony";
    public static final String RAILING = "railing";
    public static final String PLATFORM = "platform";

    public static final String LADDER = "ladder";

    /**
     * One kind of course.
     *
     * <p>There is no price in here. {@link #units(String)} works it out from
     * the boxes, so the only way to make a course cheaper is to make it
     * smaller, which is the only honest way.
     *
     * @param name   what it is called, which is also half its block id
     * @param family which of the four sets of rules it answers to
     * @param role   {@link #FLOOR} or {@link #RAIL} for a deck, empty otherwise
     * @param made   how many one crafting makes
     * @param of     how many blocks of material one crafting takes
     * @param joins  whether it reaches the one beside it
     */
    public record Kind(String name, String family, String role, int made, int of,
                       boolean joins) {

        public Kind {
            Objects.requireNonNull(name, "a course with no name");
            if (!FAMILIES.contains(family)) {
                throw new IllegalArgumentException(name + " is in the " + family
                        + " family, and the families are " + FAMILIES);
            }
            if (DECK.equals(family) && !FLOOR.equals(role) && !RAIL.equals(role)) {
                throw new IllegalArgumentException(name + " is a deck piece and "
                        + "is neither a floor nor a rail, so no rule applies to it");
            }
            if (made < 1 || of < 1) {
                throw new IllegalArgumentException("a recipe that makes nothing, "
                        + "or takes nothing, is not a recipe");
            }
        }

        /** What this one costs, in eighths: its own volume, rounded up. */
        public int units() {
            return CourseRules.units(name);
        }

        /** What one crafting consumes, in units. */
        public int spends() {
            return spent(of);
        }

        /** What one crafting produces, in units. */
        public int yields() {
            return yielded(made, units());
        }

        /** Whether its recipe makes more than it takes. */
        public boolean profitable() {
            return pays(units(), made, of);
        }

        /** How many blocks breaking one gives back. Rounded down, always. */
        public int recovers() {
            return recovered(units());
        }
    }

    /**
     * Every kind, and why these thirteen.
     *
     * <p>Four cuts, because the game has an uneven half of them. Five ways up,
     * because eight of the jars this module replaces add stair variants and
     * between them they are five distinct shapes with forty names. Three deck
     * pieces, because a balcony without a rail is a hole. And a ladder,
     * because the game has exactly one and it is made of oak.
     */
    public static final List<Kind> KINDS = List.of(
            new Kind(STAIR, CUT, "", 1, 1, false),
            new Kind(SLAB, CUT, "", 2, 1, false),
            new Kind(VERTICAL_SLAB, CUT, "", 2, 1, false),
            new Kind(WALL, CUT, "", 2, 1, true),

            new Kind(COMPACT, STEP, "", 6, 3, false),
            new Kind(LOFT, STEP, "", 8, 3, false),
            new Kind(TERRACE, STEP, "", 6, 4, false),
            new Kind(SKYLINE, STEP, "", 4, 4, false),
            new Kind(BULK, STEP, "", 4, 4, false),

            new Kind(BALCONY, DECK, FLOOR, 8, 3, false),
            new Kind(RAILING, DECK, RAIL, 12, 2, true),
            new Kind(PLATFORM, DECK, FLOOR, 8, 2, false),

            new Kind(LADDER, CLIMB, "", 8, 1, false));

    /**
     * The kinds a stonecutter makes, one block at a time.
     *
     * <p>Every one of them is a cut the game already makes somewhere, and the
     * game makes those on a stonecutter: one block in, a fixed number out,
     * with no grid at all. Which settles a problem the crafting grid has and
     * nothing in the game warns about — <b>there are only so many arrangements
     * of three blocks</b>, this module already has forty-five recipes on them,
     * and two blocks on one grid is one recipe and one block nobody can ever
     * make. A cut with no grid cannot collide with anything.
     */
    public static final List<String> STONECUT =
            List.of(STAIR, SLAB, VERTICAL_SLAB, WALL, LADDER);

    /** Whether the stonecutter makes this one. */
    public static boolean stonecut(String kind) {
        return STONECUT.contains(kind);
    }

    /**
     * Every kind the stonecutter is said to make that does not take one block,
     * and every kind named there that is not a kind. Must be empty.
     *
     * <p>A stonecutter takes exactly one of its ingredient. A recipe claiming
     * to take two is a recipe the game will load, ignore the second of, and
     * hand the player twice the material's worth for one block.
     */
    public static List<String> miscut() {
        List<String> bad = new ArrayList<>();
        for (String name : STONECUT) {
            Kind kind = kind(name);
            if (kind == null) {
                bad.add(name + " is cut on a stonecutter and is not a course");
                continue;
            }
            if (kind.of() != 1) {
                bad.add(name + " is cut on a stonecutter out of " + kind.of()
                        + " blocks, and a stonecutter takes one");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The arithmetic, apart from the things it is about
    // ==================================================================

    /**
     * The economy as three numbers rather than as a kind.
     *
     * <p>Split out so the harness can build a recipe that is meant to be
     * caught. A predicate that only ever runs over a healthy roster has never
     * been seen to answer yes, and one that has quietly stopped being able to
     * looks exactly like one that has nothing to report.
     */
    public static int spent(int of) {
        return of * WHOLE;
    }

    /** What a recipe making {@code count} of something worth {@code units} yields. */
    public static int yielded(int count, int units) {
        return count * units;
    }

    /** Whether such a recipe makes more than it takes. */
    public static boolean pays(int units, int made, int of) {
        return yielded(made, units) > spent(of);
    }

    /** What breaking one of something worth {@code units} gives back, in blocks. */
    public static int recovered(int units) {
        return units / WHOLE;
    }

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
    // The shapes, which are also the prices
    // ==================================================================

    /**
     * The boxes one course is drawn and collided as.
     *
     * <p>They may overlap. {@link #filled(String)} unions them into voxels
     * before it counts anything, so a box written twice costs what one costs.
     */
    public static List<ArchRules.Box> boxesFor(String name) {
        List<ArchRules.Box> made = new ArrayList<>();
        switch (name) {
            case STAIR -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 8));
            }
            case SLAB -> made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));
            case VERTICAL_SLAB -> made.add(new ArchRules.Box(0, 0, 0, 8, 16, 16));
            case WALL -> {
                made.add(new ArchRules.Box(4, 0, 4, 12, 16, 12));
                made.add(new ArchRules.Box(5, 0, 0, 11, 14, 16));
            }
            // Half the footprint, so a stairwell fits where the game's own
            // stair will not.
            case COMPACT -> {
                made.add(new ArchRules.Box(0, 0, 0, 8, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 8, 16, 8));
            }
            // Open underneath: a tread and a riser and nothing else, so a
            // stair can run over a room rather than through it.
            case LOFT -> {
                made.add(new ArchRules.Box(0, 6, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 8));
            }
            // Three shallow treads rather than one tall one.
            case TERRACE -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 4, 16));
                made.add(new ArchRules.Box(0, 4, 0, 16, 8, 11));
                made.add(new ArchRules.Box(0, 8, 0, 16, 12, 6));
                made.add(new ArchRules.Box(0, 12, 0, 16, 16, 2));
            }
            // A stair with a rail down the open side.
            case SKYLINE -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 8));
                made.add(new ArchRules.Box(0, 8, 14, 3, 16, 16));
            }
            // A deep tread, for a stair somebody is meant to walk up abreast.
            case BULK -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 12));
            }
            case BALCONY -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 3, 16));
                made.add(new ArchRules.Box(0, 3, 13, 16, 14, 16));
            }
            case RAILING -> {
                made.add(new ArchRules.Box(6, 0, 6, 10, 16, 10));
                made.add(new ArchRules.Box(0, 10, 7, 16, 14, 9));
                made.add(new ArchRules.Box(0, 1, 7, 16, 4, 9));
            }
            case PLATFORM -> made.add(new ArchRules.Box(0, 4, 0, 16, 8, 16));
            case LADDER -> {
                made.add(new ArchRules.Box(0, 0, 14, 2, 16, 16));
                made.add(new ArchRules.Box(14, 0, 14, 16, 16, 16));
                made.add(new ArchRules.Box(2, 2, 14, 14, 4, 16));
                made.add(new ArchRules.Box(2, 7, 14, 14, 9, 16));
                made.add(new ArchRules.Box(2, 12, 14, 14, 14, 16));
            }
            default -> throw new IllegalArgumentException("no shape for " + name);
        }
        return List.copyOf(made);
    }

    /**
     * The voxels a course fills, as a 16x16x16 grid indexed
     * {@code x * 256 + y * 16 + z}.
     */
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

    /** How many of the block's 4096 sixteenths a course actually fills. */
    public static int volume(String name) {
        int count = 0;
        for (boolean one : filled(name)) {
            if (one) {
                count++;
            }
        }
        return count;
    }

    /**
     * What a course costs, in eighths of a block: its own volume, rounded up.
     *
     * <p>Rounded up rather than down, and that is the whole of why no loop
     * here pays. Rounding down would let a piece be sold for less than the
     * material in it, and two of those in a row is a machine.
     */
    public static int units(String name) {
        int made = (volume(name) + EIGHTH - 1) / EIGHTH;
        return Math.max(1, made);
    }

    /**
     * How tall the course stands in the column at {@code x, z}, in sixteenths,
     * or zero where it is empty. The top of the highest filled voxel, which is
     * the height a player would be standing at.
     */
    public static int heightAt(boolean[] grid, int x, int z) {
        for (int y = SPAN - 1; y >= 0; y--) {
            if (grid[x * SPAN * SPAN + y * SPAN + z]) {
                return y + 1;
            }
        }
        return 0;
    }

    /** Every height this course stands at, lowest first, zero left out. */
    public static List<Integer> treads(String name) {
        boolean[] grid = filled(name);
        Set<Integer> seen = new TreeSet<>();
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                int tall = heightAt(grid, x, z);
                if (tall > 0) {
                    seen.add(tall);
                }
            }
        }
        return List.copyOf(seen);
    }

    /** How many of the 256 columns the course fills at all. */
    public static int footprint(String name) {
        boolean[] grid = filled(name);
        int count = 0;
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                if (heightAt(grid, x, z) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /** How many columns a player could stand on: filled, and no higher than a slab. */
    public static int standable(String name) {
        boolean[] grid = filled(name);
        int count = 0;
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                int tall = heightAt(grid, x, z);
                if (tall > 0 && tall <= SPAN / 2) {
                    count++;
                }
            }
        }
        return count;
    }

    // ==================================================================
    // What the game already has
    // ==================================================================

    /**
     * The materials the game already cuts each way, so this module does not.
     *
     * <p>Written down so it can be argued with, and then checked: the
     * validator reads the game's own blockstates and fails if this list and
     * the game disagree in either direction. {@link JoinRules} has the same
     * list for doors and fences and only ever checked that it was <em>used</em>
     * — which would not have caught a material added to the game later, and
     * would not have caught one typed wrong here.
     */
    public static final Map<String, Set<String>> VANILLA_ALREADY = Map.of(
            STAIR, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "crimson", "warped", "bamboo", "stone",
                    "tuff", "blackstone", "andesite", "diorite", "granite",
                    "sandstone", "red_sandstone", "prismarine", "dark_prismarine",
                    "purpur_block", "bricks", "mud_bricks", "nether_bricks",
                    "end_stone_bricks", "quartz"),
            SLAB, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "crimson", "warped", "bamboo", "stone",
                    "tuff", "blackstone", "andesite", "diorite", "granite",
                    "sandstone", "red_sandstone", "prismarine", "dark_prismarine",
                    "purpur_block", "bricks", "mud_bricks", "nether_bricks",
                    "end_stone_bricks", "quartz"),
            WALL, Set.of("tuff", "blackstone", "andesite", "diorite", "granite",
                    "sandstone", "red_sandstone", "prismarine", "bricks",
                    "mud_bricks", "nether_bricks", "end_stone_bricks"),
            VERTICAL_SLAB, Set.of());

    /**
     * What the game calls a cut of this material, where that is not the
     * material's own name.
     *
     * <p>The game's stair is {@code brick_stairs} and not {@code bricks_stairs},
     * {@code purpur_stairs} and not {@code purpur_block_stairs}. The validator
     * needs this to look the list above up in the game's blockstates, and it
     * is wrong in exactly the way a hand-typed list is wrong, so it is small
     * and it is checked.
     */
    public static final Map<String, String> AS_THE_GAME_SPELLS_IT = Map.of(
            "bricks", "brick",
            "mud_bricks", "mud_brick",
            "nether_bricks", "nether_brick",
            "end_stone_bricks", "end_stone_brick",
            "purpur_block", "purpur",
            "amethyst_block", "amethyst",
            "dripstone_block", "dripstone");

    /** Whether the game already has this one, in which case we do not. */
    public static boolean alreadyInTheGame(String kind, AtelierRules.Material material) {
        return VANILLA_ALREADY.getOrDefault(kind, Set.of()).contains(material.name());
    }

    // ==================================================================
    // The catalogue
    // ==================================================================

    /**
     * One course in one material.
     *
     * @param kind     which of {@link #KINDS}
     * @param material what it is cut out of
     */
    public record Course(String kind, AtelierRules.Material material) {

        public Course {
            if (CourseRules.kind(kind) == null) {
                throw new IllegalArgumentException(kind + " is not a course");
            }
            Objects.requireNonNull(material, "a course cut out of nothing");
        }

        /** Its block id, without the namespace. */
        public String id() {
            return material.name() + "_" + kind;
        }
    }

    /** Every course this module adds, which is every one the game has not. */
    public static List<Course> catalogue() {
        List<Course> made = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (Kind kind : KINDS) {
                if (alreadyInTheGame(kind.name(), material)) {
                    continue;
                }
                made.add(new Course(kind.name(), material));
            }
        }
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }

    /** Every course the game already ships, named, so the count can be argued with. */
    public static List<String> alreadyHad() {
        List<String> had = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (Kind kind : KINDS) {
                if (alreadyInTheGame(kind.name(), material)) {
                    had.add(material.name() + "_" + kind.name());
                }
            }
        }
        return List.copyOf(had);
    }

    // ==================================================================
    // The one thing this file exists for: a stair you can walk up
    // ==================================================================

    /** The tallest step a player walks up without jumping, in sixteenths. */
    public static final int LONGEST_STRIDE = 8;

    /**
     * Every way up that is not one.
     *
     * <p>Must be empty. A stair is three things at once and a model says
     * nothing about any of them: it has to rise in more than one go, no one of
     * those goes may be more than {@link #LONGEST_STRIDE}, and the last of
     * them has to reach the top of the block or it puts you nowhere.
     */
    public static List<String> unwalkable() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!STEP.equals(kind.family())) {
                continue;
            }
            bad.addAll(faultsInTreads(kind.name(), treads(kind.name())));
        }
        return List.copyOf(bad);
    }

    /** What is wrong with a way up that stands at these heights, if anything. */
    public static List<String> faultsInTreads(String name, List<Integer> treads) {
        List<String> bad = new ArrayList<>();
        if (treads.size() < 2) {
            bad.add(name + " stands at one height, " + treads
                    + ", so it is a slab with a staircase drawn on it");
            return List.copyOf(bad);
        }
        if (treads.get(treads.size() - 1) != SPAN) {
            bad.add(name + " tops out at " + treads.get(treads.size() - 1)
                    + " of " + SPAN + ", so it does not reach the block above it");
        }
        int below = 0;
        for (int tread : treads) {
            if (tread - below > LONGEST_STRIDE) {
                bad.add(name + " steps from " + below + " to " + tread
                        + ", which is more than a player walks up");
            }
            below = tread;
        }
        return List.copyOf(bad);
    }

    /**
     * Every deck piece that is not the thing its role says it is.
     *
     * <p>A floor you cannot stand on and a rail you cannot see through are
     * both perfectly good models of something else.
     */
    public static List<String> misdecked() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!DECK.equals(kind.family())) {
                continue;
            }
            bad.addAll(faultsInDeck(kind.name(), kind.role(), kind.joins(),
                    standable(kind.name()), footprint(kind.name())));
        }
        return List.copyOf(bad);
    }

    /** What is wrong with a deck piece of this shape, if anything. */
    public static List<String> faultsInDeck(String name, String role, boolean joins,
                                            int standable, int footprint) {
        List<String> bad = new ArrayList<>();
        int columns = SPAN * SPAN;
        if (FLOOR.equals(role)) {
            if (standable * 2 < columns) {
                bad.add(name + " is a floor a player can stand on " + standable
                        + " of " + columns + " squares of, which is not a floor");
            }
        } else {
            if (footprint * 4 > columns) {
                bad.add(name + " is a rail filling " + footprint + " of " + columns
                        + " squares, which is a wall");
            }
            if (!joins) {
                bad.add(name + " is a rail that does not reach the rail beside it, "
                        + "so a run of them has gaps");
            }
        }
        return List.copyOf(bad);
    }

    /** Every climbable piece that is not against a face of its block. */
    public static List<String> unclimbable() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!CLIMB.equals(kind.family())) {
                continue;
            }
            bad.addAll(faultsInClimb(kind.name(), boxesFor(kind.name())));
        }
        return List.copyOf(bad);
    }

    /** What is wrong with something climbable drawn as these boxes, if anything. */
    public static List<String> faultsInClimb(String name, List<ArchRules.Box> boxes) {
        List<String> bad = new ArrayList<>();
        for (ArchRules.Box box : boxes) {
            if (box.z1() < SPAN - 4) {
                bad.add(name + " has a box reaching " + box.z1()
                        + " sixteenths from the wall, so it is not on one");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Nothing here is two things with one shape, or one thing with two names
    // ==================================================================

    /** Every pair of kinds filling exactly the same voxels. */
    public static List<String> sameShape() {
        List<String> bad = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Kind kind : KINDS) {
            StringBuilder key = new StringBuilder();
            for (boolean one : filled(kind.name())) {
                key.append(one ? '1' : '0');
            }
            String already = seen.putIfAbsent(key.toString(), kind.name());
            if (already != null) {
                bad.add(kind.name() + " and " + already + " fill exactly the "
                        + "same sixteenths, so they are one block with two names");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind declared twice, or sharing a name with a shape or a piece. */
    public static List<String> repeated() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!seen.add(kind.name())) {
                bad.add(kind.name() + " is declared twice");
            }
            if (AtelierRules.shape(kind.name()) != null) {
                bad.add(kind.name() + " is also a furniture shape");
            }
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                if (piece.name().equals(kind.name())) {
                    bad.add(kind.name() + " is also an architectural piece");
                }
            }
            if (JoinRules.KINDS.contains(kind.name())) {
                bad.add(kind.name() + " is also an opening");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The economy, which is AtelierRules's
    // ==================================================================

    /** Every kind whose recipe makes more units than it consumes. Must be empty. */
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
            if (recovered(kind.units()) * WHOLE * kind.made() > kind.spends()) {
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
    // Joining, proved the way the fences are
    // ==================================================================

    /** Whether two courses reach one another. */
    public static boolean reaches(Course one, Course other) {
        return kind(one.kind()).joins() && kind(other.kind()).joins()
                && one.kind().equals(other.kind());
    }

    /** Every pair that joins one way and not the other. Must be empty. */
    public static List<String> lopsided() {
        List<String> bad = new ArrayList<>();
        List<Course> all = catalogue();
        for (Course one : all) {
            for (Course other : all) {
                if (reaches(one, other) != reaches(other, one)) {
                    bad.add(one.id() + " and " + other.id()
                            + " disagree about whether they meet");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every course that joins something of another kind. Must be empty. */
    public static List<String> crossJoined() {
        List<String> bad = new ArrayList<>();
        for (Course one : catalogue()) {
            for (Course other : catalogue()) {
                if (!one.kind().equals(other.kind()) && reaches(one, other)) {
                    bad.add(one.id() + " reaches " + other.id()
                            + ", which is a different kind of thing");
                }
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Tags
    // ==================================================================

    /** The game tags every course of this kind belongs in. */
    public static Set<String> tagsFor(Course course) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(course.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");
        switch (course.kind()) {
            case STAIR -> tags.add("minecraft:stairs");
            case SLAB -> tags.add("minecraft:slabs");
            case WALL -> tags.add("minecraft:walls");
            case LADDER -> tags.add("minecraft:climbable");
            default -> {
            }
        }
        if (course.material().soft()) {
            switch (course.kind()) {
                case STAIR -> tags.add("minecraft:wooden_stairs");
                case SLAB -> tags.add("minecraft:wooden_slabs");
                default -> {
                }
            }
        }
        return Set.copyOf(tags);
    }

    /** Every tag file this module owes the game, and what goes in it. */
    public static Map<String, List<String>> tagFiles() {
        Map<String, List<String>> files = new LinkedHashMap<>();
        for (Course course : catalogue()) {
            for (String tag : tagsFor(course)) {
                files.computeIfAbsent(tag, ignored -> new ArrayList<>())
                        .add("elysiumatelier:" + course.id());
            }
        }
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : files.entrySet()) {
            sorted.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(sorted);
    }

    /**
     * Every course in a tag it has no business in, and every one missing a tag
     * its kind needs. Must be empty.
     *
     * <p>The check {@link JoinRules} has for doors, because the symptom is the
     * same and it is silent: a slab outside {@code minecraft:slabs} is a slab
     * no other slab stacks with, and it looks like a rendering bug.
     */
    public static List<String> disagreeing() {
        List<String> bad = new ArrayList<>();
        for (Course course : catalogue()) {
            Set<String> tags = tagsFor(course);
            boolean soft = course.material().soft();
            if (tags.contains("minecraft:mineable/axe") != soft) {
                bad.add(course.id() + " is worked by the wrong tool");
            }
            if (LADDER.equals(course.kind())
                    != tags.contains("minecraft:climbable")) {
                bad.add(course.id() + " is climbable in the tags and not in the "
                        + "rules, or the other way about");
            }
            if (tags.contains("minecraft:wooden_stairs") && !soft) {
                bad.add(course.id() + " is a wooden stair made of stone");
            }
        }
        return List.copyOf(bad);
    }
}
