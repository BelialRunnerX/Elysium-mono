package com.elysium.armory.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What mines a bench.
 *
 * <h2>The fault this closes</h2>
 *
 * A block breaks at the speed of the tool the game lists it under, and a block
 * that asks for the right tool drops nothing unless some tool is listed as
 * right. Nothing listed the benches. The harvest audit of 29 September 2026
 * found 676 blocks across the pack in no tool's tag; the eight benches were
 * among them, and every one broke at a fist's pace whatever a player held.
 *
 * <p>Every bench is a machine on metal or stone, so a pickaxe is the tool for
 * all eight, as it is for the game's own anvil, smithing table and stonecutter.
 * Nothing here is broken by hand; a bench that were would be named in
 * {@link #BY_HAND}, with its reason in {@code validate.py}.
 *
 * <p>Pure: the JDK and nothing else. {@code tools/gen_harvest.py} reads the
 * lists below and writes the game's mineable tags from them.
 */
public final class ArmoryHarvestRules {

    private ArmoryHarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /** The benches a pickaxe is for: all eight. Metal or stone, every one. */
    public static final List<String> PICKAXE = List.of(
            "gear_smithing_table", "salvager", "reforging_table", "metal_press",
            "stone_anvil", "alloy_forge", "material_grader", "starlight_charger");

    /** An axe is for nothing here; no bench is wood. */
    public static final List<String> AXE = List.of();

    /** A shovel is for nothing here; no bench is earth. */
    public static final List<String> SHOVEL = List.of();

    /** A hoe is for nothing here; no bench is leaf or sponge. */
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
