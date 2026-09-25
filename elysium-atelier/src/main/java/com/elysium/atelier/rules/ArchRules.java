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
 * The pieces a building is made of, as opposed to the pieces in it. The JDK alone.
 *
 * <h2>The difference between this and {@link AtelierRules}</h2>
 *
 * A chair is finished when it is one block that looks like a chair. A roof is
 * not finished until you can build a <em>closed</em> one, and that is a
 * property of the set rather than of any piece in it. A roof slope with no
 * outer corner cannot turn a corner; one with no inner corner cannot meet
 * another roof; one with no ridge leaves a slot along the top. Each of those
 * is a perfectly good block that renders perfectly, and each of them is a hole
 * in somebody's house.
 *
 * <p>Nobody finds that by looking at the blocks, because every block is
 * correct. It is only wrong about what is missing — the same shape of bug the
 * tags have in {@link JoinRules}, arrived at from the other direction.
 *
 * <h2>What each family has to satisfy</h2>
 *
 * <ol>
 *   <li><b>Roofs close.</b> Every pitch has a slope, an outer corner, an inner
 *       corner and a ridge, or a roof of that pitch cannot be finished.
 *   <li><b>Paths stay underfoot.</b> A path taller than a slab is a slab, and
 *       the game has slabs.
 *   <li><b>Lights climb.</b> Each is brighter than the last and costs more
 *       than the last; two lights at one brightness are one light with two
 *       names.
 *   <li><b>Rails join.</b> A bridge is a deck, a rail and a support, and the
 *       rail has to reach the rail beside it — which is the property
 *       {@link JoinRules} proves about fences, and it is proved the same way.
 *   <li><b>Nothing pays.</b> The unit economy is {@link AtelierRules}'s, and
 *       every piece here is priced in the same eighths, so a roof cannot be a
 *       way of turning two blocks into three.
 * </ol>
 */
public final class ArchRules {

    private ArchRules() {
    }

    // ==================================================================
    // The families
    // ==================================================================

    public static final String ROOF = "roof";
    public static final String PATH = "path";
    public static final String LIGHT = "light";
    public static final String BRIDGE = "bridge";
    public static final String TRIM = "trim";

    /** Every family, in the order a builder meets them. */
    public static final List<String> FAMILIES =
            List.of(ROOF, PATH, LIGHT, BRIDGE, TRIM);

    /** The four pieces without which a roof of a pitch cannot be closed. */
    public static final String SLOPE = "slope";
    public static final String OUTER = "outer";
    public static final String INNER = "inner";
    public static final String RIDGE = "ridge";

    /** A roof that has these four can be built closed; one that lacks any cannot. */
    public static final List<String> CLOSES = List.of(SLOPE, OUTER, INNER, RIDGE);

    /** How tall a block is, in the eighths everything here is priced in. */
    public static final int WHOLE = AtelierRules.WHOLE;

    /** Taller than this and a path is a slab, and the game has slabs. */
    public static final int UNDERFOOT = WHOLE / 2;

    /** The brightest anything may be. The game's own ceiling. */
    public static final int BRIGHTEST = 15;

    /**
     * One architectural piece.
     *
     * @param name   what it is called, which is half its block id
     * @param family which family it belongs to
     * @param role   its job inside that family; for a roof, one of
     *               {@link #CLOSES}, and for anything else a word of its own
     * @param pitch  which slope a roof piece belongs to; empty for the rest,
     *               because a lamp does not have a pitch and a field that is
     *               sometimes meaningless is a field somebody reads anyway
     * @param units  what one costs, in eighths of a block
     * @param made   how many one crafting makes
     * @param of     how many blocks of material one crafting takes
     * @param tall   how tall it stands, in eighths
     * @param light  how brightly it burns, nothing for anything that does not
     * @param joins  whether it has to reach the one beside it
     */
    public record Piece(String name, String family, String role, String pitch,
                        int units, int made, int of, int tall, int light,
                        boolean joins) {

        public Piece {
            Objects.requireNonNull(name, "a piece with no name");
            if (!FAMILIES.contains(family)) {
                throw new IllegalArgumentException(name + " is in the " + family
                        + " family, and the families are " + FAMILIES);
            }
            if (units < 1 || units > WHOLE * 2) {
                throw new IllegalArgumentException(name + " costs " + units
                        + " eighths, which is either free or more than two blocks");
            }
            if (made < 1 || of < 1) {
                throw new IllegalArgumentException(name + " has a recipe that makes "
                        + "nothing or takes nothing, which is not a recipe");
            }
            if (tall < 1 || tall > WHOLE) {
                throw new IllegalArgumentException(name + " stands " + tall
                        + " eighths tall, and a block is " + WHOLE);
            }
            if (light < 0 || light > BRIGHTEST) {
                throw new IllegalArgumentException(name + " burns at " + light
                        + " and the band is 0 to " + BRIGHTEST);
            }
            if (ROOF.equals(family) && !CLOSES.contains(role)) {
                throw new IllegalArgumentException(name + " is a roof piece whose "
                        + "role is " + role + ", and a roof is built out of "
                        + CLOSES);
            }
            if (ROOF.equals(family) == pitch.isEmpty()) {
                throw new IllegalArgumentException(name + " is a roof piece with no "
                        + "pitch, or is not a roof piece and has one — and a field "
                        + "that is sometimes meaningless is a field somebody reads");
            }
        }

        /** What one crafting consumes, in units. */
        public int spends() {
            return of * WHOLE;
        }

        /** What one crafting produces, in units. */
        public int yields() {
            return made * units;
        }

        /** Whether its recipe makes more than it takes. */
        public boolean profitable() {
            return yields() > spends();
        }

        /** How many blocks breaking one gives back. Rounded down, always. */
        public int recovers() {
            return units / WHOLE;
        }

        /** Whether it gives off light. */
        public boolean burns() {
            return light > 0;
        }
    }

    private static Piece roof(String pitch, String role, int units, int made,
                              int of) {
        return new Piece(pitch.isEmpty() ? "roof_" + role
                : pitch + "_roof_" + role, ROOF, role, pitch, units, made, of,
                WHOLE, 0, false);
    }

    /** The pitches a roof comes in. */
    public static final List<String> PITCHES = List.of("shallow", "steep");

    /**
     * Every piece there is.
     *
     * <p>The roofs are built by a loop over pitch and role rather than written
     * out, which is the only reason the closure property is true by
     * construction rather than by somebody remembering. Adding a third pitch
     * is one entry in {@link #PITCHES} and four more blocks; adding one by
     * hand is four chances to forget the inner corner, and the inner corner is
     * the one nobody notices is missing until they build an L-shaped house.
     */
    public static final List<Piece> PIECES = pieces();

    private static List<Piece> pieces() {
        List<Piece> found = new ArrayList<>();
        for (String pitch : PITCHES) {
            boolean steep = "steep".equals(pitch);
            found.add(roof(pitch, SLOPE, steep ? 5 : 4, 4, 3));
            found.add(roof(pitch, OUTER, steep ? 4 : 3, 4, 2));
            found.add(roof(pitch, INNER, steep ? 6 : 5, 5, 4));
            found.add(roof(pitch, RIDGE, steep ? 5 : 4, steep ? 5 : 4, steep ? 4 : 3));
        }

        // Underfoot. A path is a sixteenth of a block deep and a border is a
        // quarter, which is what makes a path read as a path rather than as a
        // very thin slab.
        found.add(new Piece("path", PATH, "flat", "", 1, 8, 1, 1, 0, false));
        found.add(new Piece("path_border", PATH, "edge", "", 2, 4, 1, 2, 0, false));
        found.add(new Piece("path_step", PATH, "step", "", 3, 2, 1, 3, 0, false));

        // Light, in a ladder. Each rung is brighter and dearer than the last,
        // which lightsThatDoNotClimb() is what keeps true.
        found.add(new Piece("sconce", LIGHT, "wall", "", 2, 4, 1, 4, 7, false));
        found.add(new Piece("post_light", LIGHT, "post", "", 4, 2, 1, WHOLE, 10,
                false));
        found.add(new Piece("lamp", LIGHT, "standing", "", 6, 2, 2, WHOLE, 13,
                false));
        found.add(new Piece("chandelier", LIGHT, "hanging", "", 8, 1, 1, WHOLE,
                BRIGHTEST, false));

        // A bridge is three pieces and it is not a bridge without all three:
        // something to walk on, something to stop you falling off it, and
        // something holding it up.
        found.add(new Piece("bridge_deck", BRIDGE, "deck", "", 3, 4, 2, 2, 0, false));
        found.add(new Piece("bridge_rail", BRIDGE, "rail", "", 2, 6, 2, WHOLE, 0,
                true));
        found.add(new Piece("bridge_support", BRIDGE, "support", "", 6, 3, 3, WHOLE,
                0, false));

        // The pieces that finish an elevation.
        found.add(new Piece("sill", TRIM, "sill", "", 2, 8, 3, 2, 0, false));
        found.add(new Piece("awning", TRIM, "awning", "", 3, 8, 4, 3, 0, false));
        found.add(new Piece("cornice", TRIM, "cornice", "", 3, 6, 3, 4, 0, true));
        return List.copyOf(found);
    }

    // ==================================================================
    // The geometry
    // ==================================================================

    /**
     * One box, in the game's own sixteenths, facing north.
     *
     * <p>Here rather than in the block class or the generator because both of
     * those need it and neither may be the one that decides. A model and a
     * collision box that disagree is a block you fall through or cannot walk
     * onto, and it looks completely correct from every angle.
     */
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {

        public Box {
            if (x1 >= x2 || y1 >= y2 || z1 >= z2) {
                throw new IllegalArgumentException("a box with no volume: "
                        + x1 + "," + y1 + "," + z1 + " to " + x2 + "," + y2 + ","
                        + z2);
            }
            if (x1 < 0 || y1 < 0 || z1 < 0 || x2 > 16 || y2 > 16 || z2 > 16) {
                throw new IllegalArgumentException("a box outside its own block: "
                        + x1 + "," + y1 + "," + z1 + " to " + x2 + "," + y2 + ","
                        + z2);
            }
        }
    }

    /** How tall a piece stands, in the game's sixteenths. */
    public static int sixteenths(Piece piece) {
        return piece.tall() * (16 / WHOLE);
    }

    /**
     * The boxes a piece is made of, facing north.
     *
     * <p>A roof is two boxes and that is the whole of why it reads as a roof:
     * a slab at the bottom and something on top of it that says which way the
     * slope runs. A steep roof's upper box is deeper, which is the only
     * difference between the two pitches.
     */
    public static List<Box> boxesFor(Piece piece) {
        int tall = sixteenths(piece);
        if (ROOF.equals(piece.family())) {
            boolean steep = "steep".equals(piece.pitch());
            int rise = steep ? 12 : 8;
            List<Box> made = new ArrayList<>();
            switch (piece.role()) {
                case SLOPE -> {
                    made.add(new Box(0, 0, 0, 16, 8, 16));
                    made.add(new Box(0, 8, 0, 16, 16, rise));
                }
                case OUTER -> {
                    made.add(new Box(0, 0, 0, 16, 8, 16));
                    made.add(new Box(0, 8, 0, rise, 16, rise));
                }
                case INNER -> {
                    made.add(new Box(0, 0, 0, 16, 8, 16));
                    made.add(new Box(0, 8, 0, 16, 16, rise));
                    made.add(new Box(0, 8, rise, rise, 16, 16));
                }
                default -> {
                    made.add(new Box(0, 0, 2, 16, 8, 14));
                    made.add(new Box(0, 8, steep ? 4 : 5, 16, 14,
                            steep ? 12 : 11));
                }
            }
            return List.copyOf(made);
        }
        if (PATH.equals(piece.family())) {
            return List.of(new Box(0, 0, 0, 16, tall, 16));
        }
        return switch (piece.role()) {
            case "sill", "awning", "cornice", "wall" ->
                    List.of(new Box(0, 16 - tall, 0, 16, 16, tall));
            case "deck" -> List.of(new Box(0, 0, 0, 16, tall, 16));
            case "post", "standing", "support" ->
                    List.of(new Box(4, 0, 4, 12, 16, 12));
            case "hanging" -> List.of(new Box(3, 4, 3, 13, 16, 13));
            case "rail" -> List.of(new Box(7, 0, 0, 9, 16, 16));
            default -> List.of(new Box(0, 0, 0, 16, 16, 16));
        };
    }

    /** The smallest box holding all of a piece's boxes. */
    public static Box bounding(Piece piece) {
        List<Box> boxes = boxesFor(piece);
        int x1 = 16, y1 = 16, z1 = 16, x2 = 0, y2 = 0, z2 = 0;
        for (Box box : boxes) {
            x1 = Math.min(x1, box.x1());
            y1 = Math.min(y1, box.y1());
            z1 = Math.min(z1, box.z1());
            x2 = Math.max(x2, box.x2());
            y2 = Math.max(y2, box.y2());
            z2 = Math.max(z2, box.z2());
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    /**
     * Every piece whose boxes do not stand as tall as the rules say it does.
     *
     * <p>The two numbers are written in two places on purpose — one is what
     * the economy and the slab check read, the other is what a player walks
     * on — and this is what stops them drifting apart.
     */
    public static List<String> geometryThatDisagrees() {
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            Box whole = bounding(one);
            int tall = whole.y2() - whole.y1();
            if (PATH.equals(one.family()) && tall != sixteenths(one)) {
                bad.add(one.name() + " is " + sixteenths(one) + " sixteenths tall "
                        + "in the rules and " + tall + " in its boxes, so the check "
                        + "that a path is not a slab is about a different block "
                        + "from the one a player stands on");
            }
            if (tall > 16 || whole.x2() > 16 || whole.z2() > 16) {
                bad.add(one.name() + " sticks out of its own block");
            }
        }
        return List.copyOf(bad);
    }

    /** The piece of that name, or null. */
    public static Piece piece(String name) {
        for (Piece one : PIECES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /** Every piece of a family. */
    public static List<Piece> of(String family) {
        List<Piece> found = new ArrayList<>();
        for (Piece one : PIECES) {
            if (one.family().equals(family)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** Every block this adds, as material and piece. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (Piece one : PIECES) {
                names.add(material.name() + "_" + one.name());
            }
        }
        return List.copyOf(names);
    }

    /** How many, which the registry counts itself against. */
    public static int howMany() {
        return AtelierRules.MATERIALS.size() * PIECES.size();
    }

    // ==================================================================
    // Whether a building can actually be built out of them
    // ==================================================================

    /**
     * Every pitch that cannot be closed.
     *
     * <p>The one check this file exists for. A roof missing an outer corner
     * cannot turn; missing an inner corner cannot meet another roof; missing a
     * ridge is open along the top. All three render perfectly and all three
     * are a hole in a house.
     */
    public static List<String> roofsThatCannotTurn() {
        List<String> bad = new ArrayList<>();
        Map<String, Set<String>> byPitch = new LinkedHashMap<>();
        for (Piece one : of(ROOF)) {
            byPitch.computeIfAbsent(one.pitch(), key -> new LinkedHashSet<>())
                    .add(one.role());
        }
        for (String pitch : PITCHES) {
            Set<String> roles = byPitch.getOrDefault(pitch, Set.of());
            for (String needed : CLOSES) {
                if (!roles.contains(needed)) {
                    bad.add("a " + pitch + " roof has no " + needed + ", so one of "
                            + "that pitch cannot be closed — and every block in it "
                            + "renders perfectly");
                }
            }
        }
        for (String pitch : byPitch.keySet()) {
            if (!PITCHES.contains(pitch)) {
                bad.add(pitch + " is a pitch nothing lists, so nothing counts what "
                        + "it is missing");
            }
        }
        return List.copyOf(bad);
    }

    /** Every family with nothing in it. */
    public static List<String> emptyFamilies() {
        List<String> bad = new ArrayList<>();
        for (String family : FAMILIES) {
            if (of(family).isEmpty()) {
                bad.add("the " + family + " family is named and empty, so every "
                        + "check about it passes by never running");
            }
        }
        return List.copyOf(bad);
    }

    /** Every path a player would be better off using a slab for. */
    public static List<String> pathsThatAreSlabs() {
        List<String> bad = new ArrayList<>();
        for (Piece one : of(PATH)) {
            if (one.tall() > UNDERFOOT) {
                bad.add(one.name() + " stands " + one.tall() + " eighths, and "
                        + "anything over " + UNDERFOOT + " is a slab — which the "
                        + "game already has");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every rung of the light ladder that does not climb.
     *
     * <p>Both halves matter and they fail differently. Two lights at one
     * brightness are one light with two names. A brighter light that costs
     * less makes the dimmer one a thing nobody crafts.
     */
    public static List<String> lightsThatDoNotClimb() {
        List<Piece> lights = new ArrayList<>(of(LIGHT));
        lights.sort((one, other) -> Integer.compare(one.light(), other.light()));
        List<String> bad = new ArrayList<>();
        for (Piece one : lights) {
            if (!one.burns()) {
                bad.add(one.name() + " is a light that gives off none");
            }
        }
        for (int rung = 1; rung < lights.size(); rung++) {
            Piece below = lights.get(rung - 1);
            Piece above = lights.get(rung);
            if (above.light() == below.light()) {
                bad.add(above.name() + " and " + below.name() + " are both "
                        + above.light() + " bright, so they are one light with two "
                        + "names");
            }
            if (above.units() <= below.units()) {
                bad.add(above.name() + " is brighter than " + below.name()
                        + " and costs no more, so nobody would ever make a "
                        + below.name());
            }
        }
        return List.copyOf(bad);
    }

    /** Every bridge piece the set is missing, and every rail that cannot join. */
    public static List<String> bridgesThatDoNotSpan() {
        Set<String> roles = new TreeSet<>();
        for (Piece one : of(BRIDGE)) {
            roles.add(one.role());
        }
        List<String> bad = new ArrayList<>();
        for (String needed : List.of("deck", "rail", "support")) {
            if (!roles.contains(needed)) {
                bad.add("a bridge has no " + needed + ", and a bridge is something "
                        + "to walk on, something to stop you falling off it and "
                        + "something holding it up");
            }
        }
        for (Piece one : of(BRIDGE)) {
            if ("rail".equals(one.role()) && !one.joins()) {
                bad.add(one.name() + " does not reach the rail beside it, which is "
                        + "a gap in a railing and reads as a rendering glitch");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Whether two pieces reach each other.
     *
     * <p>Only pieces that join at all, and only within a family: a cornice
     * does not meet a bridge rail, and a rule that said it did would be
     * describing a building nobody drew.
     */
    public static boolean reaches(Piece one, Piece other) {
        return one.joins() && other.joins() && one.family().equals(other.family());
    }

    /** Every pair that reaches one way and not the other. */
    public static List<String> lopsided() {
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            for (Piece other : PIECES) {
                if (reaches(one, other) != reaches(other, one)) {
                    bad.add(one.name() + " and " + other.name() + " disagree about "
                            + "whether they meet, which is a post joined on one side");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece whose recipe makes more than it takes. */
    public static List<String> profitable() {
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            if (one.profitable()) {
                bad.add(one.name() + " makes " + one.yields() + " eighths out of "
                        + one.spends() + ", so a loop of crafting it pays");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * The most a recipe may waste, in units.
     *
     * <p>A whole block. The furniture side has had this rule from the start
     * and this side shipped without it: a recipe that eats four blocks and
     * gives back twelve eighths is not exploitable, it is simply one nobody
     * will ever use, and a piece nobody makes is a piece that may as well not
     * be there.
     */
    public static final int MOST_WASTED = WHOLE;

    /** Every piece whose recipe throws away more than a block. */
    public static List<String> wasteful() {
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            int wasted = one.spends() - one.yields();
            if (wasted > MOST_WASTED) {
                bad.add(one.name() + " wastes " + wasted + " units a craft, which is "
                        + "more than a whole block, and nobody will ever make one");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece a player could craft and break for more than they started with. */
    public static List<String> recoverable() {
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            if (one.made() * one.recovers() > one.of()) {
                bad.add(one.name() + ": " + one.of() + " blocks in, "
                        + one.made() * one.recovers() + " back out");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece declared twice, or sharing a name with a furniture shape. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Piece one : PIECES) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        Set<String> furniture = new TreeSet<>();
        for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
            furniture.add(shape.name());
        }
        for (Piece one : PIECES) {
            if (furniture.contains(one.name())) {
                bad.add(one.name() + " is both a piece and a furniture shape, so "
                        + "one of the two registrations will lose");
            }
        }
        for (String kind : JoinRules.KINDS) {
            if (piece(kind) != null) {
                bad.add(kind + " is both a piece and an opening");
            }
        }
        return List.copyOf(bad);
    }
}
