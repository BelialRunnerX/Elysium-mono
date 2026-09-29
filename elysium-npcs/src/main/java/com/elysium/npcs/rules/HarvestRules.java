package com.elysium.npcs.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What mines a bench.
 *
 * <h2>The fault this closes</h2>
 *
 * A block breaks at the speed of the tool the game lists it under, and a
 * block in no tool's list breaks at a fist's pace whatever a player holds.
 * Five of the six benches were listed under the pickaxe by a file typed by
 * hand; the gear bench, added later, was in no list at all. The harvest audit
 * of 29 September 2026 found it. The list is written from here now, for every
 * bench, so a seventh is on it the day its station is declared.
 *
 * <p>Every bench is a copper frame with a working face, so a pickaxe is the
 * tool for all of them, as it is for the game's own copper block. None asks
 * for the tool (a bench a villager needs must not vanish for want of one), so
 * the list sets the speed and nothing else. Nothing is broken by hand; a bench
 * that were would be named in {@link #BY_HAND}, with its reason in
 * {@code validate.py}.
 *
 * <p>Pure: the JDK and nothing else. {@code tools/gen_village.py} reads
 * {@link #BENCH_TOOL} and {@link #BY_HAND} and writes the game's mineable tags
 * from them, one write a tag.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The four tools the game keeps a list for, in the order it names them. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /** The tool every bench is for: a pickaxe, for the copper it is framed in. */
    public static final String BENCH_TOOL = "pickaxe";

    /** Broken by hand, with a reason in the validator: nothing. */
    public static final List<String> BY_HAND = List.of();

    /** Which benches each tool is for: all of them under {@link #BENCH_TOOL}, less {@link #BY_HAND}. */
    public static Map<String, List<String>> minedBy(List<String> benches) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        for (String tool : TOOLS) {
            lists.put(tool, new ArrayList<>());
        }
        for (String bench : benches) {
            if (!BY_HAND.contains(bench) && lists.containsKey(BENCH_TOOL)) {
                lists.get(BENCH_TOOL).add(bench);
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
