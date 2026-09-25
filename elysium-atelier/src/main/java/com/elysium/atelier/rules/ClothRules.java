package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Colour, without a single new texture. The JDK and nothing else.
 *
 * <h2>The axis this module did not have</h2>
 *
 * Every other file here crosses <em>one</em> roster with the materials: shapes
 * times materials, pieces times materials, courses times materials. The
 * furniture jars this module replaces do something else. One of them ships
 * seven hundred and forty-eight blocks that are eleven woods times sixteen
 * colours times four seats, and its four seats are four seats this module
 * already has. The whole of the difference is <b>the colour of the cushion</b>.
 *
 * <p>So this file is that second axis, and it costs nothing to draw: the game
 * has had sixteen colours of wool since 2011, and a model may wear two
 * textures. An upholstered chair is this module's own chair with its seat and
 * its back drawn in somebody's wool instead of in oak.
 *
 * <h2>What has to be true of it</h2>
 *
 * <ol>
 *   <li><b>The crossing is complete.</b> Sixteen colours times eleven woods
 *       times three seats, and a hole in that is one combination a player
 *       cannot make and nothing else looks any different.
 *   <li><b>Some of the block is cloth and some of it is not.</b> A seat
 *       upholstered on every box is a block of wool shaped like a chair; one
 *       upholstered on none is a chair. Which boxes are padded is written
 *       down here, and the generator is checked against it — so a chair that
 *       grows a sixth leg is a build failure rather than a chair with a
 *       padded leg.
 *   <li><b>It costs its own parts.</b> An upholstered seat is the bare seat
 *       plus the block of wool, exactly, derived rather than typed. Nothing
 *       here is a way of turning wool into more wool.
 *   <li><b>Nothing here is already something else.</b> Sixteen colours and
 *       thirty-three materials share one namespace, and {@code white_oak_chair}
 *       colliding with something would be one of the two silently not
 *       existing.
 * </ol>
 */
public final class ClothRules {

    private ClothRules() {
    }

    /** A block, in the eighths everything in this module is priced in. */
    public static final int WHOLE = AtelierRules.WHOLE;

    /** A block, in the sixteenths a model is drawn in. */
    public static final int SPAN = CourseRules.SPAN;

    /** The filled volume one eighth of a block is worth, in cubic sixteenths. */
    public static final int EIGHTH = CourseRules.EIGHTH;

    // ==================================================================
    // The colours
    // ==================================================================

    /**
     * The game's sixteen dyes, in the game's own order.
     *
     * <p>Written out because it is the game's list and not ours; the harness
     * checks the count against {@link #MANY} so a seventeenth or a fifteenth
     * is a build failure rather than a hole in a menu.
     */
    public static final List<String> COLOURS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
            "red", "black");

    /** How many dyes the game has. */
    public static final int MANY = 16;

    /** The block a colour's cloth is cut from. */
    public static String woolOf(String colour) {
        return "minecraft:" + colour + "_wool";
    }

    /** The texture a colour's cloth wears. */
    public static String faceOf(String colour) {
        return "minecraft:block/" + colour + "_wool";
    }

    // ==================================================================
    // The seats
    // ==================================================================

    /**
     * One seat this module already makes, and which of its boxes are padded.
     *
     * @param shape  the name of the shape in {@link AtelierRules#SHAPES}
     * @param boxes  how many boxes the shape is drawn as
     * @param padded which of those boxes are cloth rather than wood
     */
    public record Seat(String shape, int boxes, List<Integer> padded) {

        public Seat {
            Objects.requireNonNull(shape, "a seat with no shape");
            padded = List.copyOf(padded);
            if (boxes < 2) {
                throw new IllegalArgumentException(shape + " is drawn as " + boxes
                        + " box(es), and a seat with one box has nothing to "
                        + "upholster that is not the whole of it");
            }
            if (padded.isEmpty()) {
                throw new IllegalArgumentException(shape + " is upholstered "
                        + "nowhere, which is a chair");
            }
            if (padded.size() >= boxes) {
                throw new IllegalArgumentException(shape + " is upholstered on "
                        + "every one of its " + boxes + " boxes, which is a block "
                        + "of wool shaped like a chair");
            }
            for (int one : padded) {
                if (one < 0 || one >= boxes) {
                    throw new IllegalArgumentException(shape + " is upholstered on "
                            + "box " + one + " and is drawn as " + boxes);
                }
            }
        }
    }

    /**
     * The seats, and where the cushion goes.
     *
     * <p>Box zero is the seat in all three, because that is how the generator
     * draws them; a chair has a back as well. The counts are here so that the
     * two descriptions of one shape can be compared: see
     * {@link #misdrawn(String, int)}.
     */
    public static final List<Seat> SEATS = List.of(
            new Seat("stool", 5, List.of(0)),
            new Seat("chair", 6, List.of(0, 5)),
            new Seat("bench", 5, List.of(0)));

    /** A seat by the name of the shape it upholsters, or null. */
    public static Seat seat(String shape) {
        for (Seat one : SEATS) {
            if (one.shape().equals(shape)) {
                return one;
            }
        }
        return null;
    }

    /**
     * Whether a shape is drawn as a different number of boxes than this file
     * thinks it is.
     *
     * <p>The one thing this file cannot see for itself: the boxes live in the
     * generator and in the block, and a shape that gains one would go on being
     * upholstered on the boxes it used to have — which is a chair with a
     * padded leg, and it renders perfectly.
     */
    public static String misdrawn(String shape, int drawn) {
        Seat seat = seat(shape);
        if (seat == null) {
            return shape + " is not a seat this file upholsters";
        }
        if (seat.boxes() != drawn) {
            return shape + " is drawn as " + drawn + " boxes and upholstered as "
                    + seat.boxes() + ", so the cushion is on whichever box that "
                    + "number happens to land on";
        }
        return null;
    }

    /** The woods a seat is framed in: every material a hand alone works. */
    public static List<AtelierRules.Material> woods() {
        List<AtelierRules.Material> found = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            if (material.soft()) {
                found.add(material);
            }
        }
        return List.copyOf(found);
    }

    /**
     * What an upholstered seat costs: the bare seat, plus the block of wool
     * that went into it.
     *
     * <p>Derived. The recipe is that same seat and that same block of wool, so
     * the two cannot drift, and no amount of upholstering and breaking comes
     * back with wool.
     */
    public static int unitsOf(String shape) {
        AtelierRules.Shape bare = AtelierRules.shape(shape);
        if (bare == null) {
            throw new IllegalArgumentException(shape + " is not a shape");
        }
        return bare.units() + WHOLE;
    }

    /** One upholstered seat. */
    public record Upholstered(String colour, AtelierRules.Material wood,
                              String shape) {

        public Upholstered {
            if (!COLOURS.contains(colour)) {
                throw new IllegalArgumentException(colour + " is not one of the "
                        + "game's dyes");
            }
            Objects.requireNonNull(wood, "a seat framed in nothing");
            if (seat(shape) == null) {
                throw new IllegalArgumentException(shape + " is not a seat");
            }
        }

        /** Its block id, without the namespace. */
        public String id() {
            return colour + "_" + wood.name() + "_" + shape;
        }

        /** What it costs, in eighths. */
        public int units() {
            return unitsOf(shape);
        }

        /** What making one consumes: the bare seat and a block of wool. */
        public int spends() {
            return AtelierRules.shape(shape).units() + WHOLE;
        }
    }

    // ==================================================================
    // The cloth on its own
    // ==================================================================

    /** One thing made of cloth and nothing else. */
    public record Cloth(String name, int made, int of) {

        public Cloth {
            Objects.requireNonNull(name, "cloth with no name");
            if (made < 1 || of < 1) {
                throw new IllegalArgumentException("a recipe that makes nothing, "
                        + "or takes nothing, is not a recipe");
            }
        }

        /** What it costs, in eighths: its own volume, rounded up. */
        public int units() {
            return ClothRules.units(name);
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

    public static final String DRAPE = "drape";
    public static final String CANOPY = "canopy";
    public static final String PILLOW = "pillow";
    public static final String RUNNER = "runner";

    /**
     * The four things made of cloth alone.
     *
     * <p>Named so that none of them is a name this module already uses. A
     * curtain and a cushion are already fittings, cut out of wood, and two
     * blocks called the same thing in one namespace is one of them quietly
     * not existing.
     */
    public static final List<Cloth> CLOTHS = List.of(
            new Cloth(DRAPE, 12, 2),
            new Cloth(CANOPY, 12, 2),
            new Cloth(PILLOW, 7, 2),
            new Cloth(RUNNER, 14, 2));

    /** A cloth by name, or null. */
    public static Cloth cloth(String name) {
        for (Cloth one : CLOTHS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /** The boxes a piece of cloth is drawn and collided as. */
    public static List<ArchRules.Box> boxesFor(String name) {
        List<ArchRules.Box> made = new ArrayList<>();
        switch (name) {
            case DRAPE -> made.add(new ArchRules.Box(0, 0, 13, 16, 16, 15));
            case CANOPY -> made.add(new ArchRules.Box(0, 12, 0, 16, 14, 16));
            case PILLOW -> made.add(new ArchRules.Box(2, 0, 2, 14, 6, 14));
            case RUNNER -> made.add(new ArchRules.Box(0, 0, 4, 16, 1, 12));
            default -> throw new IllegalArgumentException("no shape for " + name);
        }
        return List.copyOf(made);
    }

    /** How many of the block's 4096 sixteenths a piece of cloth fills. */
    public static int volume(String name) {
        int count = 0;
        for (ArchRules.Box box : boxesFor(name)) {
            count += (box.x2() - box.x1()) * (box.y2() - box.y1())
                    * (box.z2() - box.z1());
        }
        return count;
    }

    /** What a piece of cloth costs, in eighths: its own volume, rounded up. */
    public static int units(String name) {
        return Math.max(1, (volume(name) + EIGHTH - 1) / EIGHTH);
    }

    // ==================================================================
    // The catalogue
    // ==================================================================

    /** Every upholstered seat: every colour, every wood, every seat. */
    public static List<Upholstered> seats() {
        List<Upholstered> made = new ArrayList<>();
        for (String colour : COLOURS) {
            for (AtelierRules.Material wood : woods()) {
                for (Seat seat : SEATS) {
                    made.add(new Upholstered(colour, wood, seat.shape()));
                }
            }
        }
        return List.copyOf(made);
    }

    /** Every piece of cloth: every colour, every kind. */
    public static List<String> cloths() {
        List<String> made = new ArrayList<>();
        for (String colour : COLOURS) {
            for (Cloth cloth : CLOTHS) {
                made.add(colour + "_" + cloth.name());
            }
        }
        return List.copyOf(made);
    }

    /** Every block id this file adds. */
    public static List<String> catalogue() {
        List<String> made = new ArrayList<>();
        for (Upholstered one : seats()) {
            made.add(one.id());
        }
        made.addAll(cloths());
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }

    // ==================================================================
    // What has to be true
    // ==================================================================

    /** Every colour that is not one, or is one twice. Must be empty. */
    public static List<String> miscoloured() {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String colour : COLOURS) {
            if (!seen.add(colour)) {
                bad.add(colour + " is named twice, so one colour of everything "
                        + "here simply does not exist");
            }
            for (AtelierRules.Material material : AtelierRules.MATERIALS) {
                if (material.name().equals(colour)) {
                    bad.add(colour + " is both a dye and a material, and two "
                            + "blocks with one id is one of them not existing");
                }
            }
        }
        if (COLOURS.size() != MANY) {
            bad.add("there are " + COLOURS.size() + " colours and the game has "
                    + MANY + ", so a colour of everything here is missing or made up");
        }
        return List.copyOf(bad);
    }

    /**
     * Every id this file makes that something else here already makes.
     *
     * <p>Must be empty. Sixteen colours and thirty-three materials share one
     * namespace: {@code light_gray_oak_chair} is fine and
     * {@code red_sandstone_chair} would not be, and the symptom of getting it
     * wrong is one of the two blocks quietly not being registered.
     */
    public static List<String> collides(Set<String> alreadyHere) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : catalogue()) {
            if (!seen.add(id)) {
                bad.add(id + " is in this catalogue twice");
            }
            if (alreadyHere.contains(id)) {
                bad.add(id + " is already a block this module makes, so one of "
                        + "the two is not registered and nothing says which");
            }
        }
        return List.copyOf(bad);
    }

    /** What breaking something worth this many eighths gives back, in blocks. */
    public static int recovered(int units) {
        return CourseRules.recovered(units);
    }

    /** Every upholstered seat that does not cost its own parts. Must be empty. */
    public static List<String> mispriced() {
        List<String> bad = new ArrayList<>();
        for (Seat seat : SEATS) {
            AtelierRules.Shape bare = AtelierRules.shape(seat.shape());
            if (bare == null) {
                bad.add(seat.shape() + " is upholstered and is not a shape this "
                        + "module makes at all");
                continue;
            }
            int cost = unitsOf(seat.shape());
            if (cost != bare.units() + WHOLE) {
                bad.add(seat.shape() + " upholstered costs " + cost + " and is a "
                        + bare.units() + "-eighth seat plus a block of wool");
            }
            // Breaking an upholstered seat may not give back more than
            // breaking the bare one gave plus the block of wool that went in,
            // or upholstering is a way of making blocks out of seats.
            int back = recovered(cost);
            int paid = recovered(bare.units()) + 1;
            if (back > paid) {
                bad.add(seat.shape() + " upholstered gives back " + back
                        + " block(s) and was made of a seat worth "
                        + recovered(bare.units()) + " and one block of wool, so "
                        + "upholstering and breaking is a way of making blocks");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece of cloth whose recipe pays, or wastes a block. Must be empty. */
    public static List<String> profitable() {
        List<String> bad = new ArrayList<>();
        for (Cloth cloth : CLOTHS) {
            if (cloth.profitable()) {
                bad.add(cloth.name() + " makes " + cloth.yields()
                        + " eighths out of " + cloth.spends()
                        + ", so a loop of crafting it pays");
            }
            if (cloth.recovers() * WHOLE * cloth.made() > cloth.spends()) {
                bad.add(cloth.name() + ": " + cloth.of() + " blocks in, "
                        + cloth.recovers() * cloth.made() + " back out");
            }
            if (cloth.spends() - cloth.yields() > WHOLE) {
                bad.add(cloth.name() + " wastes "
                        + (cloth.spends() - cloth.yields())
                        + " units a craft, which is more than a whole block, and "
                        + "nobody will ever make one");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece of cloth sharing a name with something else here. Must be empty. */
    public static List<String> renamed() {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Cloth cloth : CLOTHS) {
            if (!seen.add(cloth.name())) {
                bad.add(cloth.name() + " is declared twice");
            }
            if (AtelierRules.shape(cloth.name()) != null) {
                bad.add(cloth.name() + " is also a furniture shape");
            }
            if (CourseRules.kind(cloth.name()) != null) {
                bad.add(cloth.name() + " is also a course");
            }
            if (TrimRules.kind(cloth.name()) != null) {
                bad.add(cloth.name() + " is also a fitting");
            }
            if (JoinRules.KINDS.contains(cloth.name())) {
                bad.add(cloth.name() + " is also an opening");
            }
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                if (piece.name().equals(cloth.name())) {
                    bad.add(cloth.name() + " is also an architectural piece");
                }
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Tags
    // ==================================================================

    /** Everything here is cloth or wood, and both are worked by hand. */
    public static Map<String, List<String>> tagFiles() {
        Map<String, List<String>> files = new LinkedHashMap<>();
        List<String> mine = new ArrayList<>();
        for (String id : catalogue()) {
            mine.add("elysiumatelier:" + id);
        }
        files.put("minecraft:mineable/axe", List.copyOf(mine));
        return Map.copyOf(files);
    }
}
