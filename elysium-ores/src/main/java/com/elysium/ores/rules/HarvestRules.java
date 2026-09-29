package com.elysium.ores.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What mines an ore, and what mines a block of the Cordon's materials.
 *
 * <h2>The fault this closes</h2>
 *
 * A block breaks at the speed of the tool the game lists it under, and a block
 * that asks for the right tool drops nothing unless some tool is listed as
 * right. Every ore of the matrix and every block of metal, gem, alloy or shard
 * asks for a pickaxe and is listed under one. The cloth block was listed under
 * nothing, by a generator that skipped the cloth kind by name; the harvest
 * audit of 29 September 2026 found it. It is decided here now, by kind: a
 * cloth block is wool, and the game's own wool is in no tool's list, so it is
 * broken by hand with its reason in {@code validate.py}, and the validator
 * reads where it is registered to hold that it never starts asking for a tool.
 *
 * <p>Pure: the JDK and nothing else. {@code tools/gen_ores.py} reads the lists
 * below and writes the game's mineable tags from them, one write a tag.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /** The tool every ore of the matrix is for: a pickaxe, as every ore of the game's. */
    public static final String ORE_TOOL = "pickaxe";

    /**
     * The kinds of material whose blocks a pickaxe is for: metal, gem, alloy
     * and shard, all of which ask for the right tool where they are registered,
     * as the game's own metal and gem blocks do.
     */
    public static final List<String> PICKAXE_KINDS = List.of("METAL", "GEM", "ALLOY", "SHARD");

    /**
     * The kinds broken by hand, with a reason in the validator: cloth. Its block
     * is wool, breaks at 0.8 like the game's wool, and asks for no tool.
     */
    public static final List<String> BY_HAND_KINDS = List.of("CLOTH");

    /**
     * Which tool mines a block of this kind of material, or an empty string for
     * one broken by hand.
     *
     * @throws IllegalArgumentException for a kind nothing here decides; the
     *                                  external kind registers no block at all
     */
    public static String toolForKind(String kind) {
        if (BY_HAND_KINDS.contains(kind)) {
            return "";
        }
        if (PICKAXE_KINDS.contains(kind)) {
            return "pickaxe";
        }
        throw new IllegalArgumentException(kind + " is a kind of material nothing here "
                + "says what mines");
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
