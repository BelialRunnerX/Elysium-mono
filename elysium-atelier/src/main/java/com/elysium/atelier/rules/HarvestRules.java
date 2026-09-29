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
 * What mines each block, decided once for all seven rosters.
 *
 * <h2>The fault this was written after</h2>
 *
 * On 29 September 2026 the running game's harvest audit named 693 blocks of
 * this module that no tool could mine: every roof, awning, cornice, sill,
 * path, bridge and light, in all thirty-three materials. A stone block asks
 * for the right tool before it drops anything, and "the right tool" is
 * whichever tool's tag names the block. Nothing named those, so no tool was
 * ever right: a survival player broke a stone roof slope, watched it vanish
 * and held nothing. The wooden ones dropped, but at a fist's pace whatever
 * was in hand.
 *
 * <p>Six families of block each wrote their own tag list and the seventh
 * wrote none. So the decision is made once here, for every block the module
 * registers, and the generator writes each tool's list once from this.
 *
 * <h2>The rule</h2>
 *
 * The material decides, as it does for the game's own blocks: wood is an
 * axe's and stone is a pickaxe's, whether or not the block needs one. The
 * game puts every plank thing in {@code mineable/axe} for the same reason,
 * since the tag sets the breaking speed as well as the drop.
 *
 * <p>Cloth goes on the axe's list with the seats it is sewn onto. The game's
 * own wool is on no tool's list because shears cut it by a rule of their own
 * and not by a tag, and a drape of ours is not wool to the game, so no shears
 * help it: the axe's list is the one list that makes it come down faster
 * than a fist. It costs nothing, because the wool it copies asks for no tool.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The game's four tools that have a list of what they mine. */
    public static final String PICKAXE = "pickaxe";
    public static final String AXE = "axe";
    public static final String SHOVEL = "shovel";
    public static final String HOE = "hoe";

    public static final List<String> TOOLS = List.of(PICKAXE, AXE, SHOVEL, HOE);

    /** What a block no tool is for says in place of a tool. */
    public static final String HAND = "hand";

    /** What a piece of cloth alone is cut from, to this file. */
    public static final String WOOL = "wool";

    /**
     * The kinds no tool is for.
     *
     * <p>None. Everything here is cut from wood, stone or wool, and the
     * game's own wood and stone are on a tool's list whatever the shape. A
     * kind named here is left out of every list, so it must not ask for the
     * right tool where it is registered: {@code validate.py} reads the
     * registry to make sure, and wants a reason written beside each one.
     */
    public static final List<String> BY_HAND = List.of();

    /** The seven rosters, named as the registry names them. */
    public static final List<String> ROSTERS = List.of(
            "shapes", "openings", "pieces", "courses", "fittings", "cloth",
            "diagonals");

    /** The tag a tool's list is. */
    public static String tag(String tool) {
        return "minecraft:mineable/" + tool;
    }

    /** What mines a block cut from this material: an axe for wood, a pickaxe for stone. */
    public static String toolFor(AtelierRules.Material material) {
        return material.soft() ? AXE : PICKAXE;
    }

    /** What mines a block of cloth alone: the axe, with the seats it is sewn onto. */
    public static final String CLOTH = AXE;

    /** One block and what mines it. */
    public record Harvest(String id, String roster, String kind, String material,
                          String tool) {

        public Harvest {
            Objects.requireNonNull(id, "a block with no id");
            Objects.requireNonNull(kind, id + " is a block of no kind");
            if (!ROSTERS.contains(roster)) {
                throw new IllegalArgumentException(id + " is in no roster: " + roster);
            }
            if (!HAND.equals(tool) && !TOOLS.contains(tool)) {
                throw new IllegalArgumentException(id + " is mined by a " + tool
                        + ", which is no tool the game has a list for");
            }
        }

        /** Whether no tool is for this one. */
        public boolean byHand() {
            return HAND.equals(tool);
        }
    }

    private static Harvest of(String roster, String id, String kind,
                              AtelierRules.Material material) {
        return new Harvest(id, roster, kind, material.name(),
                BY_HAND.contains(kind) ? HAND : toolFor(material));
    }

    /**
     * Every block the module registers, with its tool.
     *
     * <p>Walked from the same seven rosters the registry walks, in the same
     * order, so a roster added there and not here is a count that no longer
     * adds up rather than a family of blocks nobody can mine.
     */
    public static List<Harvest> catalogue() {
        List<Harvest> all = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
                all.add(of("shapes", material.name() + "_" + shape.name(),
                        shape.name(), material));
            }
        }
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            all.add(of("openings", opening.id(), opening.kind(), opening.material()));
        }
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                all.add(of("pieces", material.name() + "_" + piece.name(),
                        piece.name(), material));
            }
        }
        for (CourseRules.Course course : CourseRules.catalogue()) {
            all.add(of("courses", course.id(), course.kind(), course.material()));
        }
        for (TrimRules.Fitting fitting : TrimRules.catalogue()) {
            all.add(of("fittings", fitting.id(), fitting.kind(), fitting.material()));
        }
        for (ClothRules.Upholstered seat : ClothRules.seats()) {
            all.add(of("cloth", seat.id(), seat.shape(), seat.wood()));
        }
        for (String id : ClothRules.cloths()) {
            String kind = id.substring(id.lastIndexOf('_') + 1);
            all.add(new Harvest(id, "cloth", kind, WOOL,
                    BY_HAND.contains(kind) ? HAND : CLOTH));
        }
        for (DiagonalRules.Run run : DiagonalRules.catalogue()) {
            all.add(of("diagonals", run.id(), run.kind(), run.material()));
        }
        return List.copyOf(all);
    }

    /** How many blocks the seven rosters come to, counted where each is declared. */
    public static int howMany() {
        return AtelierRules.howMany() + JoinRules.catalogue().size()
                + ArchRules.howMany() + CourseRules.howMany() + TrimRules.howMany()
                + ClothRules.howMany() + DiagonalRules.howMany();
    }

    /** Every block, by the tool that mines it; the ones no tool is for under {@link #HAND}. */
    public static Map<String, List<String>> lists() {
        Map<String, List<String>> made = new LinkedHashMap<>();
        for (Harvest one : catalogue()) {
            made.computeIfAbsent(one.tool(), tool -> new ArrayList<>()).add(one.id());
        }
        Map<String, List<String>> sealed = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : made.entrySet()) {
            sealed.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(sealed);
    }

    // ==================================================================
    // Whether the catalogue hangs together
    // ==================================================================

    /** Every id that two rosters both register. Must be empty. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Harvest one : catalogue()) {
            if (!seen.add(one.id())) {
                twice.add(one.id() + " is registered by " + one.roster()
                        + " and by a roster before it");
            }
        }
        return List.copyOf(twice);
    }

    /**
     * Every block nothing says how to mine, or that two things are said of.
     *
     * <p>A block on no tool's list and not by hand is mined at a fist's pace
     * by every tool, and if it asks for the right tool it drops nothing at
     * all. A block on a tool's list whose kind is also by hand is one the
     * list says one thing about and the exemption another. Takes the blocks
     * and the exemptions rather than reading its own, so a harness can hand
     * it a broken pair and see it complain.
     */
    public static List<String> undecided(List<Harvest> blocks, List<String> byHand) {
        List<String> bad = new ArrayList<>();
        for (Harvest one : blocks) {
            boolean excused = byHand.contains(one.kind());
            if (one.byHand() && !excused) {
                bad.add(one.id() + " is on no tool's list and its kind is not by "
                        + "hand either, so nothing says what mines it");
            }
            if (!one.byHand() && excused) {
                bad.add(one.id() + " is on the " + one.tool() + "'s list and its "
                        + "kind is by hand, so the list says one thing and the "
                        + "exemption another");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind {@link #BY_HAND} names that no roster has: an exemption for nothing. */
    public static List<String> exemptingNothing() {
        Set<String> kinds = new LinkedHashSet<>();
        for (Harvest one : catalogue()) {
            kinds.add(one.kind());
        }
        List<String> bad = new ArrayList<>();
        for (String kind : BY_HAND) {
            if (!kinds.contains(kind)) {
                bad.add(kind + " is broken by hand and is no kind of block this "
                        + "module registers");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every material whose blocks are on two tools' lists. Must be empty.
     *
     * <p>A material is wood or stone, and that decides the tool for every
     * shape cut from it; a stone chair on the axe's list beside a stone door
     * on the pickaxe's is two answers to one question.
     */
    public static List<String> split() {
        Map<String, Set<String>> tools = new LinkedHashMap<>();
        for (Harvest one : catalogue()) {
            if (!one.byHand()) {
                tools.computeIfAbsent(one.material(), m -> new TreeSet<>()).add(one.tool());
            }
        }
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : tools.entrySet()) {
            if (entry.getValue().size() > 1) {
                bad.add(entry.getKey() + " is mined by " + entry.getValue()
                        + ", which is two answers to one question");
            }
        }
        return List.copyOf(bad);
    }
}
