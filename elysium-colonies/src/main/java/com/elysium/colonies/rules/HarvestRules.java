package com.elysium.colonies.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What mines the elevator, and the two stakes the world plants.
 *
 * <h2>The fault this closes</h2>
 *
 * The six parts of a spaceport are metal that asks for the right tool
 * ({@code PortBlocks.metal()} says so), and a block that asks for the right
 * tool drops nothing unless some tool's mineable tag names it. Nothing named
 * them. The harvest audit of 29 September 2026 found all six in no tool's tag,
 * so a colony that took its elevator down to move it lost every block of it —
 * some three hundred and forty ingots' worth for the shortest legal port.
 *
 * <p>A pickaxe is the tool for all six, as it is for the game's iron block and
 * anvil they are made from. The ruin marker is a stone stake and goes with
 * them; the survey stake is wood and is an axe's, as the game's signs are.
 * Neither stake asks for a tool — the world plants them and a player pulls
 * them out — so their tags set the speed and nothing else. Nothing here is
 * broken by hand; a block that were would be named in {@link #BY_HAND}, with
 * its reason in {@code validate.py}.
 *
 * <p>Pure: the JDK and nothing else. {@code tools/gen_port.py} reads the
 * lists below and writes the game's mineable tags from them, one write a tag.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /** A pickaxe: the six metal parts of the elevator, and the stone ruin marker. */
    public static final List<String> PICKAXE = List.of(
            "elevator_anchor", "elevator_tether", "elevator_counterweight",
            "elevator_pylon", "elevator_collar", "elevator_deck", "ruin_seed");

    /** An axe: the wooden survey stake, as the game's signs are. */
    public static final List<String> AXE = List.of("settlement_seed");

    /** A shovel is for nothing here; nothing is earth. */
    public static final List<String> SHOVEL = List.of();

    /** A hoe is for nothing here; nothing is leaf or sponge. */
    public static final List<String> HOE = List.of();

    /** Broken by hand, with a reason in the validator: nothing. */
    public static final List<String> BY_HAND = List.of();

    /** Which blocks each tool is for, keyed by the tool's name in {@link #TOOLS}. */
    public static Map<String, List<String>> minedBy() {
        return Map.of("pickaxe", PICKAXE, "axe", AXE, "shovel", SHOVEL, "hoe", HOE);
    }

    /**
     * Every block nothing says how to mine, or two things are said about.
     *
     * <p>A block on no tool's list and not broken by hand is mined at a fist's
     * pace by every tool, and if it asks for the right tool it drops nothing at
     * all. A block on two lists, or on one and broken by hand, is a tag saying
     * one thing and the rules another. And a name on a list that is no block of
     * this module makes the game refuse the whole tag it is written into.
     */
    public static List<String> untooled(List<String> blocks, Map<String, List<String>> minedBy,
                                        List<String> byHand) {
        List<String> bad = new ArrayList<>();
        for (String block : blocks) {
            int said = byHand.contains(block) ? 1 : 0;
            for (List<String> listed : minedBy.values()) {
                if (listed.contains(block)) {
                    said++;
                }
            }
            if (said == 0) {
                bad.add(block + " is on no tool's list and not broken by hand either, "
                        + "so nothing says what mines it");
            }
            if (said > 1) {
                bad.add(block + " is given " + said + " answers to what mines it, so "
                        + "the tags say one thing and the exemption another");
            }
        }
        List<String> decided = new ArrayList<>(byHand);
        for (List<String> listed : minedBy.values()) {
            decided.addAll(listed);
        }
        for (String name : decided) {
            if (!blocks.contains(name)) {
                bad.add(name + " is given a tool and is not a block of this module, "
                        + "so the game refuses the whole tag it is written into");
            }
        }
        return List.copyOf(bad);
    }
}
