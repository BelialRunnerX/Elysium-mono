package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What mines a machine.
 *
 * <h2>The fault this closes</h2>
 *
 * Every machine is built on the game's iron block ({@code FarmBlocks} copies
 * {@code Blocks.IRON_BLOCK}), and the iron block asks for the right tool: it
 * drops nothing unless some tool's mineable tag names it. Nothing named the
 * machines. The harvest audit of 29 September 2026 found all nine in no tool's
 * tag, so a player who moved a grinder watched it break in seven seconds and
 * held nothing afterwards.
 *
 * <p>A pickaxe is the tool for every one of them, as it is for the iron block
 * they are built on. Nothing is broken by hand; a machine that were would be
 * named in {@link #BY_HAND}, with its reason in {@code validate.py}, and would
 * have to stop copying the iron block.
 *
 * <p>Pure: the JDK and nothing else. {@code tools/gen_bestiary.py} reads
 * {@link #MACHINE_TOOL} and {@link #BY_HAND} and writes the game's mineable
 * tags from them, one write a tag.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /** The tool every machine is for: a pickaxe, for the iron block each is built on. */
    public static final String MACHINE_TOOL = "pickaxe";

    /** Broken by hand, with a reason in the validator: nothing. */
    public static final List<String> BY_HAND = List.of();

    /** Which machines each tool is for: all of them under {@link #MACHINE_TOOL}, less {@link #BY_HAND}. */
    public static Map<String, List<String>> minedBy(List<FarmRules.Machine> machines) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        for (String tool : TOOLS) {
            lists.put(tool, new ArrayList<>());
        }
        for (FarmRules.Machine one : machines) {
            if (!BY_HAND.contains(one.id()) && lists.containsKey(MACHINE_TOOL)) {
                lists.get(MACHINE_TOOL).add(one.id());
            }
        }
        return lists;
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
