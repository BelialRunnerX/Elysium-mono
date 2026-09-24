package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blocks whose whole point is meeting their neighbours. The JDK alone.
 *
 * <h2>The bug that is invisible because the block works</h2>
 *
 * A fence, a gate, a pane, a door and a trapdoor are not decorations that
 * happen to sit next to things. What they are <em>for</em> is joining: a fence
 * that does not reach the gate beside it is not a fence with a cosmetic
 * problem, it is a hole in a wall.
 *
 * <p>And none of that is written in the block. It is written in <b>tags</b>.
 * The game's own fence asks whether its neighbour is in {@code #fences} and
 * whether the two agree about {@code #wooden_fences}; a villager asks whether
 * a door is in {@code #wooden_doors} before deciding it can be opened; a raid
 * asks the same thing before deciding it can be broken. So a block can be
 * built perfectly, registered perfectly, and left out of one tag — and it
 * works. It opens when you click it. It just does not join, or a villager
 * will not walk through it, or a pillager smashes a door that should have held.
 *
 * <p>Nobody finds that by reading a tag file, because the tag file is correct
 * about everything in it. It is only wrong about what is missing, and what is
 * missing is invisible by definition.
 *
 * <h2>So the tags are derived, and the joining is a relation with a property</h2>
 *
 * Every block's tags are worked out here from what it is — its kind and
 * whether its material is soft — and the harness proves two things over the
 * whole catalogue:
 *
 * <ol>
 *   <li><b>Joining is symmetric.</b> If A reaches B then B reaches A. An
 *       asymmetric relation is a fence post joined on one side, which reads as
 *       a rendering glitch and is a missing tag entry.</li>
 *   <li><b>The tags and the behaviour agree.</b> A door a hand opens must be
 *       in the tag that says so, and a door a hand does not open must not be.
 *       Those two are read by different parts of the game and neither checks
 *       the other.</li>
 * </ol>
 */
public final class JoinRules {

    private JoinRules() {
    }

    // ==================================================================
    // The five kinds
    // ==================================================================

    /** Two blocks tall, swings, and a villager may have opinions about it. */
    public static final String DOOR = "door";

    /** One block, swings flat. */
    public static final String TRAPDOOR = "trapdoor";

    /** A fence you can walk through when it is open. */
    public static final String GATE = "gate";

    /** A post that reaches for its neighbours. */
    public static final String FENCE = "fence";

    /** A thin sheet that reaches for its neighbours. */
    public static final String PANE = "pane";

    public static final List<String> KINDS =
            List.of(DOOR, TRAPDOOR, GATE, FENCE, PANE);

    /**
     * Which kinds reach for one another at all.
     *
     * <p>A door does not join anything — it fills a hole rather than making
     * one. A trapdoor likewise. The three that join are the fence, the gate
     * that stands in a run of fence, and the pane.
     */
    public static boolean joins(String kind) {
        return FENCE.equals(kind) || GATE.equals(kind) || PANE.equals(kind);
    }

    /** One block this module adds, by what it is and what it is made of. */
    public record Opening(String kind, AtelierRules.Material material) {

        public Opening {
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException(kind + " is not a kind of opening");
            }
        }

        public String id() {
            return material.name() + "_" + kind;
        }

        /** Whether a bare hand works it. */
        public boolean soft() {
            return material.soft();
        }
    }

    /**
     * What the game already ships, by kind and by material.
     *
     * <p>A measurement of somebody else's game rather than a decision of ours,
     * written out so it can be argued with. {@link AtelierRules#SHAPES} says
     * in so many words that "a decoration mod that adds what the game already
     * has adds nothing" — and the first version of this file promptly added
     * sixteen materials' worth of doors, eleven of which the game has had
     * since 2011. The principle is worth more as a check than as a sentence.
     *
     * <p>Doors, trapdoors, gates and fences: every wood, and iron for the
     * first two. Panes: glass and iron bars, neither of which is a material
     * here. So what is left is the stone half of the list, and panes in
     * everything.
     */
    public static final Map<String, Set<String>> VANILLA_ALREADY = Map.of(
            DOOR, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "bamboo", "crimson", "warped"),
            TRAPDOOR, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "bamboo", "crimson", "warped"),
            GATE, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "bamboo", "crimson", "warped"),
            FENCE, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "bamboo", "crimson", "warped"),
            PANE, Set.of());

    /** Whether the game already has this one, in which case we do not. */
    public static boolean alreadyInTheGame(String kind, AtelierRules.Material material) {
        return VANILLA_ALREADY.getOrDefault(kind, Set.of()).contains(material.name());
    }

    /**
     * Every opening this module adds — which is every one the game has not.
     *
     * <p>Twenty-five of them rather than eighty. What is left after taking out
     * what already exists is the interesting half: <b>the game has no stone
     * door at all.</b> It has one metal door, and eleven wooden ones. A door
     * of stone that needs redstone, a fence of deepslate, a pane of anything —
     * those are building pieces a player cannot get any other way, and they
     * are the entire reason this group exists.
     */
    public static List<Opening> catalogue() {
        List<Opening> made = new ArrayList<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (String kind : KINDS) {
                if (alreadyInTheGame(kind, material)) {
                    continue;
                }
                made.add(new Opening(kind, material));
            }
        }
        return List.copyOf(made);
    }

    /** Every opening we add that the game already had. Must be empty. */
    public static List<String> alreadyHad() {
        List<String> caught = new ArrayList<>();
        for (Opening opening : catalogue()) {
            if (alreadyInTheGame(opening.kind(), opening.material())) {
                caught.add(opening.id() + " is a block the game has shipped for years, "
                        + "so adding it is a second entry in the recipe book and "
                        + "nothing else");
            }
        }
        return caught;
    }

    // ==================================================================
    // What the game is going to ask about each one
    // ==================================================================

    /**
     * Every tag a block of this kind and softness must be in.
     *
     * <p>Worked out rather than written down, because a tag file is only ever
     * wrong about what is <em>missing</em> from it, and a list of four hundred
     * entries is a list nobody reads for gaps.
     */
    public static Set<String> tagsFor(Opening opening) {
        Set<String> tags = new LinkedHashSet<>();
        switch (opening.kind()) {
            case DOOR -> {
                tags.add("minecraft:doors");
                if (opening.soft()) {
                    tags.add("minecraft:wooden_doors");
                }
            }
            case TRAPDOOR -> {
                tags.add("minecraft:trapdoors");
                if (opening.soft()) {
                    tags.add("minecraft:wooden_trapdoors");
                }
            }
            case GATE -> tags.add("minecraft:fence_gates");
            case FENCE -> {
                tags.add("minecraft:fences");
                if (opening.soft()) {
                    tags.add("minecraft:wooden_fences");
                }
            }
            case PANE -> {
                // Panes join by being panes, which the game decides by class
                // rather than by tag. There is no #panes.
            }
            default -> throw new IllegalStateException(opening.kind());
        }
        tags.add(opening.soft() ? "minecraft:mineable/axe" : "minecraft:mineable/pickaxe");
        return Set.copyOf(tags);
    }

    /**
     * Whether a hand alone opens it.
     *
     * <p>The game's own rule, and the reason this module has stone doors at
     * all: a door you need redstone for is a building block vanilla has
     * exactly one of. A door only opens by hand if it is soft; a fence or a
     * pane does not open at all.
     */
    public static boolean openedByHand(Opening opening) {
        if (FENCE.equals(opening.kind()) || PANE.equals(opening.kind())) {
            return false;
        }
        return opening.soft();
    }

    /**
     * Every block whose tags say one thing and whose behaviour says another.
     *
     * <p>Must be empty. These two are read by different parts of the game —
     * the block asks its own field when you click it, a villager and a raid
     * ask the tag — and neither checks the other. A stone door tagged wooden
     * opens to a click and is also smashed by a pillager who read the tag.
     */
    public static List<String> disagreeing() {
        List<String> caught = new ArrayList<>();
        for (Opening opening : catalogue()) {
            Set<String> tags = tagsFor(opening);
            boolean saysSoft = tags.contains("minecraft:wooden_doors")
                    || tags.contains("minecraft:wooden_trapdoors")
                    || tags.contains("minecraft:wooden_fences");
            boolean opens = openedByHand(opening);
            if (DOOR.equals(opening.kind()) || TRAPDOOR.equals(opening.kind())) {
                if (saysSoft != opens) {
                    caught.add(opening.id() + " is tagged as "
                            + (saysSoft ? "something a hand opens" : "something it does "
                            + "not") + " and behaves as " + (opens ? "something it does"
                            : "something it does not"));
                }
            }
            if (tags.contains("minecraft:mineable/axe")
                    && tags.contains("minecraft:mineable/pickaxe")) {
                caught.add(opening.id() + " is mined by both an axe and a pickaxe, so "
                        + "which tool a player reaches for is a coin toss");
            }
            if (!tags.contains("minecraft:mineable/axe")
                    && !tags.contains("minecraft:mineable/pickaxe")) {
                caught.add(opening.id() + " is mined by no tool at all, so it breaks at "
                        + "hand speed and reads as a block the pack forgot");
            }
        }
        return caught;
    }

    // ==================================================================
    // Joining, which is the whole point
    // ==================================================================

    /**
     * Whether one of these reaches for the other, by the game's own rule.
     *
     * <p>A fence reaches another fence when both are in {@code #fences} and
     * they <em>agree</em> about {@code #wooden_fences} — which is the part
     * everybody gets wrong: a stone fence left in the wooden tag joins oak and
     * refuses other stone. A fence also reaches a gate. A pane reaches a pane,
     * which the game settles by class rather than by tag.
     */
    public static boolean reaches(Opening one, Opening other) {
        if (!joins(one.kind()) || !joins(other.kind())) {
            return false;
        }
        if (PANE.equals(one.kind()) || PANE.equals(other.kind())) {
            return PANE.equals(one.kind()) && PANE.equals(other.kind());
        }
        if (GATE.equals(one.kind()) || GATE.equals(other.kind())) {
            return true;
        }
        return tagsFor(one).contains("minecraft:wooden_fences")
                == tagsFor(other).contains("minecraft:wooden_fences");
    }

    /**
     * Every pair where one reaches and the other does not.
     *
     * <p>Must be empty. An asymmetric join is a fence post joined on one side,
     * which everybody reads as a rendering glitch and which is always a tag.
     */
    public static List<String> lopsided() {
        List<Opening> all = catalogue();
        List<String> caught = new ArrayList<>();
        for (Opening one : all) {
            for (Opening other : all) {
                if (reaches(one, other) != reaches(other, one)) {
                    caught.add(one.id() + " reaches " + other.id() + " and is not "
                            + "reached back, so the join is drawn from one side only");
                }
            }
        }
        return caught;
    }

    /**
     * Every fence that joins something of the other hardness.
     *
     * <p>Must be empty. A wooden fence and a stone fence standing in a row and
     * joining is the game's own rule being broken by us, and it reads as a
     * mod that does not know what its own blocks are made of.
     */
    public static List<String> crossJoined() {
        List<String> caught = new ArrayList<>();
        for (Opening one : catalogue()) {
            if (!FENCE.equals(one.kind())) {
                continue;
            }
            for (Opening other : catalogue()) {
                if (!FENCE.equals(other.kind()) || one.soft() == other.soft()) {
                    continue;
                }
                if (reaches(one, other)) {
                    caught.add(one.id() + " joins " + other.id() + " across the grain");
                }
            }
        }
        return caught;
    }

    /** Every tag this module writes, and everything that goes in it. */
    public static Map<String, List<String>> tagFiles() {
        Map<String, List<String>> made = new LinkedHashMap<>();
        for (Opening opening : catalogue()) {
            for (String tag : tagsFor(opening)) {
                made.computeIfAbsent(tag, key -> new ArrayList<>()).add(opening.id());
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : made.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    /** How many blocks this adds. */
    public static int howMany() {
        return catalogue().size();
    }

    /**
     * Every fence of ours that would join the game's own wooden fences.
     *
     * <p>Must be empty. Everything left in this catalogue is hard, so nothing
     * here belongs in {@code #wooden_fences} — and a hard fence left in it
     * joins every oak fence in the world and refuses the deepslate one beside
     * it, which reads as a rendering bug and is a tag.
     */
    public static List<String> joiningTheWrongCrowd() {
        List<String> caught = new ArrayList<>();
        for (Opening opening : catalogue()) {
            boolean wooden = tagsFor(opening).contains("minecraft:wooden_fences")
                    || tagsFor(opening).contains("minecraft:wooden_doors")
                    || tagsFor(opening).contains("minecraft:wooden_trapdoors");
            if (wooden && !opening.soft()) {
                caught.add(opening.id() + " is tagged as wooden and is not");
            }
            if (!wooden && opening.soft() && !PANE.equals(opening.kind())
                    && !GATE.equals(opening.kind())) {
                caught.add(opening.id() + " is soft and is not tagged as wooden, so "
                        + "nothing in the game that reasons about wooden ones knows it");
            }
        }
        return caught;
    }
}
