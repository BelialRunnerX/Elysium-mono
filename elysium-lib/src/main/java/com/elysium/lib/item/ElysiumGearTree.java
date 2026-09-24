package com.elysium.lib.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What kind of thing a piece of gear is, as a tree.
 *
 * <h2>Why this is in the library</h2>
 *
 * Four systems need to say "this applies to swords" and mean the same sentence
 * by it: Silent Gear's traits, Apotheosis' affixes, and Elysium's own gems and
 * runes. Each one arrived with its own idea of what a sword is — a class test,
 * a tag, a list of item ids — and the moment two of them disagree a player is
 * holding a sword that one system can see and another cannot.
 *
 * So there is one tree, and it lives here, where everything that asks can
 * reach it. {@code ArmoryGearType} is this tree; it reads its rows from this
 * file rather than keeping a second copy that could drift.
 *
 * <h2>The shape of it</h2>
 *
 * Every kind has a parent, up to {@code all}. A rule written for {@code tool}
 * holds for a pickaxe; a rule written for {@code pickaxe} holds for a hammer,
 * which is a pickaxe with a wider head. That is what lets an affix pool say
 * "melee_weapon" once instead of naming seven shapes, and what lets a property
 * key on {@code harvest_tool} be overridden by a closer one on {@code axe}.
 *
 * <p>Pure: the JDK and nothing else, so a harness can run it without a game.
 */
public final class ElysiumGearTree {

    private ElysiumGearTree() {
    }

    /** A kind and the kind it is a sort of. */
    public record Kind(String name, String parent) {
    }

    /** The root every kind descends from. */
    public static final String ROOT = "all";

    public static final Map<String, Kind> ALL;

    static {
        Map<String, Kind> m = new LinkedHashMap<>();
        String[][] rows = {
                {"all", null},
                {"tool", "all"}, {"armor", "all"}, {"curio", "all"}, {"projectile", "all"},
                {"weapon", "tool"}, {"harvest_tool", "tool"}, {"fishing_rod", "tool"}, {"shield", "tool"},
                {"melee_weapon", "weapon"}, {"ranged_weapon", "weapon"}, {"hybrid_weapon", "weapon"},
                {"pickaxe", "harvest_tool"}, {"shovel", "harvest_tool"}, {"axe", "harvest_tool"},
                {"hoe", "harvest_tool"}, {"shears", "harvest_tool"}, {"sickle", "harvest_tool"},
                {"mattock", "harvest_tool"}, {"paxel", "harvest_tool"},
                {"hammer", "pickaxe"}, {"prospector_hammer", "pickaxe"}, {"excavator", "shovel"}, {"saw", "axe"},
                {"sword", "melee_weapon"}, {"katana", "melee_weapon"}, {"machete", "melee_weapon"},
                {"spear", "melee_weapon"}, {"mace", "melee_weapon"}, {"dagger", "melee_weapon"},
                {"knife", "melee_weapon"},
                {"bow", "ranged_weapon"}, {"crossbow", "ranged_weapon"}, {"slingshot", "ranged_weapon"},
                {"trident", "hybrid_weapon"},
                {"helmet", "armor"}, {"chestplate", "armor"}, {"leggings", "armor"}, {"boots", "armor"},
                {"elytra", "armor"},
                {"arrow", "projectile"},
                {"bracelet", "curio"}, {"necklace", "curio"}, {"ring", "curio"},
        };
        for (String[] row : rows) {
            m.put(row[0], new Kind(row[0], row[1]));
        }
        ALL = Collections.unmodifiableMap(m);
    }

    /** The kind by that name, or null. */
    public static Kind named(String name) {
        return name == null ? null : ALL.get(name);
    }

    public static boolean exists(String name) {
        return named(name) != null;
    }

    /**
     * A kind, then its parent, up to {@code all}.
     *
     * Stops if a parent is missing or the chain loops, so bad data cannot hang
     * a lookup — a tree read from a datapack is data like any other.
     */
    public static List<String> lineage(String name) {
        List<String> out = new ArrayList<>();
        Kind at = named(name);
        while (at != null && !out.contains(at.name()) && out.size() < ALL.size() + 1) {
            out.add(at.name());
            at = at.parent() == null ? null : ALL.get(at.parent());
        }
        return out;
    }

    /** Whether a kind is a sort of another — a hammer is a pickaxe is a tool. */
    public static boolean is(String kind, String ancestor) {
        return lineage(kind).contains(ancestor);
    }

    /**
     * Whether a kind answers to a selector, which is how every data file in the
     * mod names what it applies to.
     *
     * <ul>
     *   <li>{@code any} — anything at all, including a kind this tree has never
     *       heard of;</li>
     *   <li>{@code not:<selector>} — the opposite of one;</li>
     *   <li>anything else — a kind name, matched against the whole lineage, so
     *       {@code melee_weapon} catches a katana.</li>
     * </ul>
     */
    public static boolean matches(String kind, String selector) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        if (selector.equals("any")) {
            return true;
        }
        if (selector.startsWith("not:")) {
            return !matches(kind, selector.substring(4));
        }
        return is(kind, selector);
    }

    /** Whether a kind answers to every selector in a list; an empty list is anything. */
    public static boolean matchesAll(String kind, List<String> selectors) {
        for (String selector : selectors) {
            if (!matches(kind, selector)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a kind answers to at least one selector; an empty list is nothing. */
    public static boolean matchesAny(String kind, List<String> selectors) {
        for (String selector : selectors) {
            if (matches(kind, selector)) {
                return true;
            }
        }
        return false;
    }

    /** Every kind, in the order the tree declares them. */
    public static List<String> names() {
        return List.copyOf(ALL.keySet());
    }

    /** Every kind with no children: the shapes an item can actually be. */
    public static List<String> leaves() {
        List<String> parents = new ArrayList<>();
        for (Kind kind : ALL.values()) {
            if (kind.parent() != null) {
                parents.add(kind.parent());
            }
        }
        List<String> out = new ArrayList<>();
        for (String name : ALL.keySet()) {
            if (!parents.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }
}
