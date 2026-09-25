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
 * The fittings: what goes on a wall rather than what a wall is made of.
 * The JDK and nothing else.
 *
 * <h2>The fourth file, and why the fourth</h2>
 *
 * {@link AtelierRules} is furniture, {@link ArchRules} is the outside of a
 * building, {@link CourseRules} is the wall. This is everything the block
 * packs add that is none of those: panelling, blinds, boards, frames,
 * trellises, quoins, banisters, catwalks, girders, cushions, curtains and
 * rope. Four thousand blocks between them in the jars this replaces, and
 * almost all of that four thousand is <em>the same dozen shapes in a
 * different colour</em>.
 *
 * <p>So this file is twelve shapes, and the colours are the game's own
 * materials. What it adds that the jars do not is that the twelve are a set
 * with rules rather than twelve independent blocks:
 *
 * <ol>
 *   <li><b>The facings are a ladder.</b> Panelling, blinds and boards go on
 *       the same face of the same block and differ only in how far they stand
 *       out from it. So each has to stand further out than the last and cost
 *       more than the last — and every one of them has to be thinner than a
 *       slab, because the game has slabs.
 *   <li><b>The hollow ones are hollow.</b> A frame you cannot see through is
 *       not a frame, it is a block, and nothing about the model says which one
 *       you have drawn. It is proved by looking for a line of empty
 *       sixteenths straight through.
 *   <li><b>The edges join.</b> A banister with a gap in it is a banister
 *       nobody can lean on, which is the property the fences are proved to
 *       have and it is proved the same way.
 *   <li><b>The soft ones are soft.</b> A cushion of deepslate is a joke the
 *       generator makes on its own, once, in every material, and nobody
 *       notices until somebody opens the creative menu.
 * </ol>
 */
public final class TrimRules {

    private TrimRules() {
    }

    /** A block, in the eighths everything in this module is priced in. */
    public static final int WHOLE = AtelierRules.WHOLE;

    /** A block, in the sixteenths a model is drawn in. */
    public static final int SPAN = CourseRules.SPAN;

    /** The filled volume one eighth of a block is worth, in cubic sixteenths. */
    public static final int EIGHTH = CourseRules.EIGHTH;

    // ==================================================================
    // The families
    // ==================================================================

    /** Something that goes on the face of a wall: a ladder of depths. */
    public static final String FACING = "facing";

    /** Something you can see through, proved rather than drawn. */
    public static final String HOLLOW = "hollow";

    /** Something along an edge: it joins, or it turns a corner. */
    public static final String EDGE = "edge";

    /** Something out of a material a hand alone works. */
    public static final String SOFT = "soft";

    public static final List<String> FAMILIES = List.of(FACING, HOLLOW, EDGE, SOFT);

    // ==================================================================
    // The kinds
    // ==================================================================

    public static final String PANEL = "panel";
    public static final String BLINDS = "blinds";
    public static final String BOARD = "board";

    public static final String FRAME = "frame";
    public static final String TRELLIS = "trellis";

    public static final String QUOIN = "quoin";
    public static final String BANISTER = "banister";
    public static final String CATWALK = "catwalk";
    public static final String GIRDER = "girder";

    public static final String CUSHION = "cushion";
    public static final String CURTAIN = "curtain";
    public static final String ROPE = "rope";

    /**
     * One fitting.
     *
     * <p>No price, for the reason {@link CourseRules.Kind} has none: it is
     * worked out from the shape by {@link #units(String)}, so a fitting cannot
     * be mispriced, only mis-shaped.
     *
     * @param name  what it is called, which is also half its block id
     * @param family which of the four sets of rules it answers to
     * @param made  how many one crafting makes
     * @param of    how many blocks of material one crafting takes
     * @param joins whether it reaches the one beside it
     * @param climbs whether a player goes up it
     */
    public record Kind(String name, String family, int made, int of,
                       boolean joins, boolean climbs) {

        public Kind {
            Objects.requireNonNull(name, "a fitting with no name");
            if (!FAMILIES.contains(family)) {
                throw new IllegalArgumentException(name + " is in the " + family
                        + " family, and the families are " + FAMILIES);
            }
            if (made < 1 || of < 2) {
                throw new IllegalArgumentException(name + " is crafted out of "
                        + of + " blocks into " + made + ", and a fitting is made "
                        + "on a grid out of at least two");
            }
        }

        /** What this one costs, in eighths: its own volume, rounded up. */
        public int units() {
            return TrimRules.units(name);
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

    /**
     * Every fitting, and why these twelve.
     *
     * <p>Because between them the four block packs this module replaces add
     * about four thousand blocks and these are the shapes underneath: the
     * rest is colour, and colour this module gets from the game.
     */
    public static final List<Kind> KINDS = List.of(
            // The ladder of facings: each further out from the wall than the last.
            new Kind(PANEL, FACING, 12, 2, false, false),
            new Kind(BLINDS, FACING, 6, 2, false, false),
            new Kind(BOARD, FACING, 5, 2, false, false),

            // The ones you can see through.
            new Kind(FRAME, HOLLOW, 14, 2, false, false),
            new Kind(TRELLIS, HOLLOW, 12, 2, false, true),

            // The ones along an edge.
            new Kind(QUOIN, EDGE, 5, 2, false, false),
            new Kind(BANISTER, EDGE, 14, 2, true, false),
            new Kind(CATWALK, EDGE, 7, 2, true, false),
            new Kind(GIRDER, EDGE, 14, 2, true, false),

            // And the ones out of something a hand works.
            new Kind(CUSHION, SOFT, 7, 2, false, false),
            new Kind(CURTAIN, SOFT, 14, 2, false, false),
            new Kind(ROPE, SOFT, 14, 2, false, true));

    /** A kind by name, or null. */
    public static Kind kind(String name) {
        for (Kind kind : KINDS) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    /** The facings, deepest last: the ladder {@link #unladdered()} proves. */
    public static final List<String> FACINGS = List.of(PANEL, BLINDS, BOARD);

    // ==================================================================
    // The shapes, which are also the prices
    // ==================================================================

    /** The boxes one fitting is drawn and collided as. */
    public static List<ArchRules.Box> boxesFor(String name) {
        List<ArchRules.Box> made = new ArrayList<>();
        switch (name) {
            // Panelling lies against the wall; blinds stand off it; a board
            // wall stands off it further still.
            case PANEL -> made.add(new ArchRules.Box(0, 0, 15, 16, 16, 16));
            case BLINDS -> made.add(new ArchRules.Box(0, 0, 13, 16, 16, 16));
            case BOARD -> made.add(new ArchRules.Box(0, 0, 11, 16, 16, 16));

            // A ring with nothing in the middle of it.
            case FRAME -> {
                made.add(new ArchRules.Box(0, 0, 6, 16, 2, 10));
                made.add(new ArchRules.Box(0, 14, 6, 16, 16, 10));
                made.add(new ArchRules.Box(0, 2, 6, 2, 14, 10));
                made.add(new ArchRules.Box(14, 2, 6, 16, 14, 10));
            }
            // A grid of slats with the gaps a plant grows through.
            case TRELLIS -> {
                made.add(new ArchRules.Box(0, 0, 14, 2, 16, 16));
                made.add(new ArchRules.Box(7, 0, 14, 9, 16, 16));
                made.add(new ArchRules.Box(14, 0, 14, 16, 16, 16));
                made.add(new ArchRules.Box(0, 0, 14, 16, 2, 16));
                made.add(new ArchRules.Box(0, 7, 14, 16, 9, 16));
                made.add(new ArchRules.Box(0, 14, 14, 16, 16, 16));
            }

            // The dressed stones at the corner of a building: an L, not a block.
            case QUOIN -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 16, 3));
                made.add(new ArchRules.Box(0, 0, 3, 3, 16, 16));
            }
            // A rail that climbs with the stair it runs beside.
            case BANISTER -> {
                made.add(new ArchRules.Box(0, 2, 7, 4, 8, 9));
                made.add(new ArchRules.Box(4, 4, 7, 8, 10, 9));
                made.add(new ArchRules.Box(8, 6, 7, 12, 12, 9));
                made.add(new ArchRules.Box(12, 8, 7, 16, 14, 9));
            }
            // A grated floor with a rail down each side.
            case CATWALK -> {
                made.add(new ArchRules.Box(0, 4, 0, 16, 6, 16));
                made.add(new ArchRules.Box(0, 6, 0, 16, 14, 2));
                made.add(new ArchRules.Box(0, 6, 14, 16, 14, 16));
            }
            // An I-beam, which is two flanges and a web.
            case GIRDER -> {
                made.add(new ArchRules.Box(0, 0, 7, 16, 2, 9));
                made.add(new ArchRules.Box(0, 14, 7, 16, 16, 9));
                made.add(new ArchRules.Box(7, 2, 7, 9, 14, 9));
            }

            case CUSHION -> made.add(new ArchRules.Box(1, 0, 1, 15, 5, 15));
            case CURTAIN -> made.add(new ArchRules.Box(0, 0, 14, 16, 16, 16));
            case ROPE -> made.add(new ArchRules.Box(7, 0, 7, 9, 16, 9));

            default -> throw new IllegalArgumentException("no shape for " + name);
        }
        return List.copyOf(made);
    }

    /** The voxels a fitting fills, indexed {@code x * 256 + y * 16 + z}. */
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

    /** How many of the block's 4096 sixteenths a fitting fills. */
    public static int volume(String name) {
        int count = 0;
        for (boolean one : filled(name)) {
            if (one) {
                count++;
            }
        }
        return count;
    }

    /** What a fitting costs, in eighths of a block: its own volume, rounded up. */
    public static int units(String name) {
        return Math.max(1, (volume(name) + EIGHTH - 1) / EIGHTH);
    }

    /**
     * How far a fitting stands out from the wall it is on, in sixteenths.
     *
     * <p>Measured rather than declared: the depth of the shallowest filled
     * sixteenth, counted back from the face. A facing that has quietly become
     * as deep as a slab is a slab, and the game has slabs.
     */
    public static int standsOut(String name) {
        boolean[] grid = filled(name);
        for (int z = 0; z < SPAN; z++) {
            for (int x = 0; x < SPAN; x++) {
                for (int y = 0; y < SPAN; y++) {
                    if (grid[x * SPAN * SPAN + y * SPAN + z]) {
                        return SPAN - z;
                    }
                }
            }
        }
        return 0;
    }

    /** How many of the 256 columns a fitting fills at all. */
    public static int footprint(String name) {
        boolean[] grid = filled(name);
        int count = 0;
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                if (CourseRules.heightAt(grid, x, z) > 0) {
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
                int tall = CourseRules.heightAt(grid, x, z);
                if (tall > 0 && tall <= SPAN / 2) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * How many straight lines you can see through a fitting along its depth.
     *
     * <p>A column of the block front to back with nothing in it. This is what
     * makes a frame a frame rather than a block with a frame painted on it,
     * and it is the one thing a model cannot tell you by being looked at.
     */
    public static int seeThrough(String name) {
        boolean[] grid = filled(name);
        int clear = 0;
        for (int x = 0; x < SPAN; x++) {
            for (int y = 0; y < SPAN; y++) {
                boolean blocked = false;
                for (int z = 0; z < SPAN; z++) {
                    if (grid[x * SPAN * SPAN + y * SPAN + z]) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) {
                    clear++;
                }
            }
        }
        return clear;
    }

    // ==================================================================
    // The four properties
    // ==================================================================

    /** The deepest a facing may stand out and still not be a slab. */
    public static final int DEEPEST_FACING = SPAN / 2 - 1;

    /**
     * Everything wrong with the ladder of facings. Must be empty.
     *
     * <p>Three blocks on one wall differing only in depth: each has to be
     * deeper than the last and dearer than the last, or two of them are one
     * block with two names, and none may be as deep as a slab.
     */
    public static List<String> unladdered() {
        List<String> bad = new ArrayList<>();
        int below = 0;
        int paid = 0;
        for (String name : FACINGS) {
            if (kind(name) == null) {
                bad.add(name + " is on the ladder of facings and is not a fitting");
                continue;
            }
            int deep = standsOut(name);
            int price = units(name);
            bad.addAll(faultsInRung(name, deep, price, below, paid));
            below = deep;
            paid = price;
        }
        for (Kind kind : KINDS) {
            if (FACING.equals(kind.family()) && !FACINGS.contains(kind.name())) {
                bad.add(kind.name() + " is a facing and is on no ladder, so nothing "
                        + "compares it with the facings either side of it");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * What is wrong with one rung of the ladder of facings, if anything.
     *
     * <p>Split out from the walk so the harness can hand it a rung built to
     * be caught. A predicate run only over a healthy roster has never been
     * seen to say yes, and one that has quietly lost the ability to looks
     * exactly like one with nothing to report.
     */
    public static List<String> faultsInRung(String name, int deep, int price,
                                            int below, int paid) {
        List<String> bad = new ArrayList<>();
        if (deep <= below) {
            bad.add(name + " stands " + deep + " sixteenths out and the one below "
                    + "it stands " + below + ", so it is the same wall twice or a "
                    + "shallower one further up the ladder");
        }
        if (price <= paid) {
            bad.add(name + " costs " + price + " eighths and the one below it "
                    + "costs " + paid + ", so the deeper one is the cheaper one");
        }
        if (deep > DEEPEST_FACING) {
            bad.add(name + " stands " + deep + " sixteenths out, which is a slab, "
                    + "and the game has slabs");
        }
        return List.copyOf(bad);
    }

    /** Every see-through fitting you cannot see through. Must be empty. */
    public static List<String> notHollow() {
        List<String> bad = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!HOLLOW.equals(kind.family())) {
                continue;
            }
            bad.addAll(faultsInHollow(kind.name(), seeThrough(kind.name()),
                    footprint(kind.name())));
        }
        return List.copyOf(bad);
    }

    /** What is wrong with something meant to be seen through, if anything. */
    public static List<String> faultsInHollow(String name, int clear, int footprint) {
        List<String> bad = new ArrayList<>();
        if (clear == 0) {
            bad.add(name + " has no line of sight through it at all, so it is a "
                    + "solid block with a frame drawn on it");
        }
        if (footprint * 2 > SPAN * SPAN) {
            bad.add(name + " fills " + footprint + " of " + SPAN * SPAN
                    + " squares, which is more wall than hole");
        }
        return List.copyOf(bad);
    }

    /** Everything wrong along an edge. Must be empty. */
    public static List<String> misedged() {
        List<String> bad = new ArrayList<>();
        int joining = 0;
        for (Kind kind : KINDS) {
            if (!EDGE.equals(kind.family())) {
                continue;
            }
            if (kind.joins()) {
                joining++;
                if (footprint(kind.name()) * 2 > SPAN * SPAN
                        && standable(kind.name()) * 2 < SPAN * SPAN) {
                    bad.add(kind.name() + " joins its neighbour, fills "
                            + footprint(kind.name()) + " of " + SPAN * SPAN
                            + " squares and is not a floor either, so it is a wall "
                            + "that pretends to be a rail");
                }
            }
        }
        if (joining == 0) {
            bad.add("nothing along an edge joins anything, so the symmetry proved "
                    + "over the catalogue is about a relation that is never true");
        }
        return List.copyOf(bad);
    }

    /** Every soft fitting that is not soft, or soft fitting in a hard material. */
    public static List<String> misgrained() {
        return misgrainedIn(catalogue());
    }

    /**
     * The same, over any roster at all.
     *
     * <p>Split out because {@link #catalogue()} builds itself with
     * {@link #fits}, which is the same rule — so run over the catalogue this
     * check can only ever answer no, which is exactly what a check that has
     * stopped working also does. The harness hands it a cushion of stone.
     */
    public static List<String> misgrainedIn(List<Fitting> roster) {
        List<String> bad = new ArrayList<>();
        for (Fitting fitting : roster) {
            boolean wants = SOFT.equals(kind(fitting.kind()).family());
            if (wants && !fitting.material().soft()) {
                bad.add(fitting.id() + " is a " + fitting.kind() + " made of "
                        + "something a hand cannot work, which is a joke the "
                        + "generator makes on its own in every material");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The catalogue
    // ==================================================================

    /** One fitting in one material. */
    public record Fitting(String kind, AtelierRules.Material material) {

        public Fitting {
            if (TrimRules.kind(kind) == null) {
                throw new IllegalArgumentException(kind + " is not a fitting");
            }
            Objects.requireNonNull(material, "a fitting made of nothing");
        }

        /** Its block id, without the namespace. */
        public String id() {
            return material.name() + "_" + kind;
        }
    }

    /** Whether this kind can be made out of this material at all. */
    public static boolean fits(String kind, AtelierRules.Material material) {
        return !SOFT.equals(kind(kind).family()) || material.soft();
    }

    /** Every fitting this module adds. */
    public static List<Fitting> catalogue() {
        List<Fitting> made = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (Kind kind : KINDS) {
                if (!fits(kind.name(), material)) {
                    continue;
                }
                made.add(new Fitting(kind.name(), material));
            }
        }
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }

    // ==================================================================
    // One thing with two names, and two things with one shape
    // ==================================================================

    /** Every filled sixteenth of a fitting, as one string, for comparing. */
    public static String voxelKey(String name) {
        StringBuilder key = new StringBuilder();
        for (boolean one : filled(name)) {
            key.append(one ? '1' : '0');
        }
        return key.toString();
    }

    /** Every pair of kinds filling exactly the same voxels. Must be empty. */
    public static List<String> sameShape() {
        List<String> bad = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Kind kind : KINDS) {
            String already = seen.putIfAbsent(voxelKey(kind.name()), kind.name());
            if (already != null) {
                bad.add(kind.name() + " and " + already + " fill exactly the same "
                        + "sixteenths, so they are one block with two names");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind declared twice, or sharing a name with anything else here. */
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
            if (CourseRules.kind(kind.name()) != null) {
                bad.add(kind.name() + " is also a course");
            }
            if (JoinRules.KINDS.contains(kind.name())) {
                bad.add(kind.name() + " is also an opening");
            }
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                if (piece.name().equals(kind.name())) {
                    bad.add(kind.name() + " is also an architectural piece");
                }
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
    // Joining
    // ==================================================================

    /** Whether two fittings reach one another. */
    public static boolean reaches(Fitting one, Fitting other) {
        return kind(one.kind()).joins() && kind(other.kind()).joins()
                && one.kind().equals(other.kind());
    }

    /** Every pair that joins one way and not the other. Must be empty. */
    public static List<String> lopsided() {
        List<String> bad = new ArrayList<>();
        List<Fitting> all = catalogue();
        for (Fitting one : all) {
            for (Fitting other : all) {
                if (reaches(one, other) != reaches(other, one)) {
                    bad.add(one.id() + " and " + other.id()
                            + " disagree about whether they meet");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every fitting that joins something of another kind. Must be empty. */
    public static List<String> crossJoined() {
        List<String> bad = new ArrayList<>();
        for (Fitting one : catalogue()) {
            for (Fitting other : catalogue()) {
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

    /** The game tags every fitting of this kind belongs in. */
    public static Set<String> tagsFor(Fitting fitting) {
        Set<String> tags = new TreeSet<>();
        tags.add(fitting.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");
        if (kind(fitting.kind()).climbs()) {
            tags.add("minecraft:climbable");
        }
        return Set.copyOf(tags);
    }

    /** Every tag file this module owes the game for the fittings. */
    public static Map<String, List<String>> tagFiles() {
        Map<String, List<String>> files = new LinkedHashMap<>();
        for (Fitting fitting : catalogue()) {
            for (String tag : tagsFor(fitting)) {
                files.computeIfAbsent(tag, ignored -> new ArrayList<>())
                        .add("elysiumatelier:" + fitting.id());
            }
        }
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : files.entrySet()) {
            sorted.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(sorted);
    }

    /**
     * Every fitting in a tag it has no business in. Must be empty.
     *
     * <p>A thing a player climbs that is not in the game's climbable tag is a
     * thing they slide down, and the model is perfect.
     */
    public static List<String> disagreeing() {
        List<String> bad = new ArrayList<>();
        for (Fitting fitting : catalogue()) {
            Set<String> tags = tagsFor(fitting);
            boolean soft = fitting.material().soft();
            if (tags.contains("minecraft:mineable/axe") != soft) {
                bad.add(fitting.id() + " is worked by the wrong tool");
            }
            if (tags.contains("minecraft:climbable")
                    != kind(fitting.kind()).climbs()) {
                bad.add(fitting.id() + " is climbable in the rules and not in the "
                        + "tags, or the other way about, and the one a player feels "
                        + "is the tag");
            }
        }
        return List.copyOf(bad);
    }
}
