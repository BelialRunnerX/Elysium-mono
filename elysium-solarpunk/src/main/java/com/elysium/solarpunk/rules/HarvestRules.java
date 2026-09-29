package com.elysium.solarpunk.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What mines a solarpunk block.
 *
 * <h2>Why this is a rules class</h2>
 *
 * A block breaks at the speed of the tool the game lists it under, and a block
 * that asks for the right tool drops nothing unless some tool is listed as
 * right. None of this module's blocks asks for one (a building set a player
 * cannot take back down is a building set they will not experiment with), so
 * the lists set the speed and nothing else; but a block on no list breaks at a
 * fist's pace whatever a player holds, and the harvest audit of 29 September
 * 2026 found 676 such blocks across the pack. Which tool is for which block
 * was a key in the generator's table until then. It is decided here, by what
 * the block is made of, and {@code tools/gen_solarpunk.py} reads it.
 *
 * <h2>The one exception</h2>
 *
 * Bloom glass is glass: it breaks at a touch, asks for no tool, and the game's
 * own glass is in no tool's list. A lamp you need a pickaxe for is a lamp you
 * place once. So it is broken by hand, with its reason in {@code validate.py},
 * and the validator reads where it is registered to hold that it never starts
 * asking for a tool.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /**
     * The tool for each material a building block is made of: stone, earth,
     * plaster, clay, glass-and-brass and every metal under the pickaxe, as
     * the game's own are; cane and leaf under the axe, as the game's wood.
     */
    public static final Map<String, String> TOOL_OF_MATERIAL = Map.ofEntries(
            Map.entry("earth", "pickaxe"),
            Map.entry("plaster", "pickaxe"),
            Map.entry("patina", "pickaxe"),
            Map.entry("brass", "pickaxe"),
            Map.entry("clay", "pickaxe"),
            Map.entry("copper", "pickaxe"),
            Map.entry("bloom", "pickaxe"),
            Map.entry("marble", "pickaxe"),
            Map.entry("limestone", "pickaxe"),
            Map.entry("alabaster", "pickaxe"),
            Map.entry("cane", "axe"),
            Map.entry("leaf", "axe"));

    /** The tool every machine is for: a pickaxe, for the copper it is cased in. */
    public static final String MACHINE_TOOL = "pickaxe";

    /** Broken by hand, with a reason in the validator: the glass, and nothing else. */
    public static final List<String> BY_HAND = List.of("bloom_glass");

    /**
     * Which tool mines a block, or an empty string for one broken by hand.
     *
     * @param blockId  the block
     * @param material what it is made of, a key of {@link #TOOL_OF_MATERIAL}
     * @param machine  whether it is a machine rather than a building block
     * @throws IllegalArgumentException for a material nothing here decides
     */
    public static String toolFor(String blockId, String material, boolean machine) {
        if (BY_HAND.contains(blockId)) {
            return "";
        }
        if (machine) {
            return MACHINE_TOOL;
        }
        String tool = TOOL_OF_MATERIAL.get(material);
        if (tool == null) {
            throw new IllegalArgumentException(blockId + " is made of " + material
                    + ", and nothing here says what mines that");
        }
        return tool;
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
