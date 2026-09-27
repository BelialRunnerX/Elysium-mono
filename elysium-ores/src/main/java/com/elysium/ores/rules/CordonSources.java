package com.elysium.ores.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the Cordon's roots come from, until the creatures that leave them do.
 *
 * <h2>The gap this closes</h2>
 *
 * {@link CordonMaterials#DROPS} names six things a creature or a ruin leaves,
 * and three of the found shards are dropped or found and never dug. The
 * foundry prices all nine as tier-0 roots (docs/SPEC_FOUNDRY.md §4: "the
 * ground does not give them, but a world does"), every Cordoned alloy eats one
 * and every gear set stands on an alloy — and, until this table, nothing in
 * the game gave any of them. Seven alloys and their gear were unobtainable in
 * survival, with nothing in a log to say so.
 *
 * <p>The creatures each drop names are another module's, and planned. Until
 * they exist, every seed has a source in vanilla's own loot: a creature whose
 * leaving the drop resembles, and the chest of the ruin that would hold it.
 * The Ashborn's slag comes off a blaze and out of a bastion; the Shell
 * Carrier's shell off a guardian and out of an ocean ruin. When the bestiary
 * ships the real creature, its own table joins this one; nothing here has to
 * come out for that.
 *
 * <h2>What the harness proves</h2>
 *
 * Every seed — every drop and every {@code SHARD} the roster ships — has at
 * least one source; no (seed, table) pair is written twice, because a
 * duplicated modifier is a doubled rate nobody tuned; no source names a seed
 * the roster does not ship; every table is vanilla's, because this module names
 * no other mod; and every chance is low and positive. The generator walks
 * {@link #SOURCES} to write one loot modifier per row, so a row here is a
 * source in the game and a source in the game is a row here.
 *
 * <p>The JDK and nothing else, so the harness can execute it.
 */
public final class CordonSources {

    private CordonSources() {
    }

    /** How a seed leaves vanilla's loot: off a killed creature, or out of a chest. */
    public enum Kind { KILL, CHEST }

    /**
     * One source.
     *
     * @param seed  the item this module ships (a drop's name or a shard's family)
     * @param table the vanilla loot table the modifier listens for
     */
    public record Source(String seed, String table) {

        public Kind kind() {
            return table.startsWith("minecraft:entities/") ? Kind.KILL : Kind.CHEST;
        }

        /** The chance the modifier rolls on this table. */
        public float chance() {
            return kind() == Kind.KILL ? KILL_CHANCE : CHEST_CHANCE;
        }

        /** The last part of the table's path: {@code blaze}, {@code bastion_treasure}. */
        public String tableName() {
            return table.substring(table.lastIndexOf('/') + 1);
        }

        /** The loot modifier's file name, one per row. */
        public String modifier() {
            return seed + "_from_" + tableName();
        }
    }

    /** One creature in thirty-three leaves a root. Low, because a kill is cheap and repeatable. */
    public static final float KILL_CHANCE = 0.03F;

    /** One chest in sixteen holds one. Higher than a kill, because a chest is opened once. */
    public static final float CHEST_CHANCE = 0.06F;

    /** No source may be likelier than this; past it a root is a common drop, not a find. */
    public static final float MAX_CHANCE = 0.10F;

    private static Source at(String seed, String table) {
        return new Source(seed, table);
    }

    /**
     * Every source, seeds in {@link CordonMaterials#DROPS} order and then the
     * shards in roster order. Each table is vanilla's own.
     */
    public static final List<Source> SOURCES = List.of(
            // the Ashborn, and the Slag Works
            at("ember_slag", "minecraft:entities/blaze"),
            at("ember_slag", "minecraft:chests/bastion_treasure"),
            at("ember_slag", "minecraft:chests/bastion_other"),
            // the Terraformer Frame and the Rootwalker
            at("bio_culture", "minecraft:entities/witch"),
            at("bio_culture", "minecraft:chests/jungle_temple"),
            // the Shell Carrier
            at("pressure_shell", "minecraft:entities/guardian"),
            at("pressure_shell", "minecraft:entities/elder_guardian"),
            at("pressure_shell", "minecraft:chests/underwater_ruin_big"),
            at("pressure_shell", "minecraft:chests/underwater_ruin_small"),
            // the Flesh Strains and the Bloom Cysts
            at("blight_crystal", "minecraft:chests/ancient_city"),
            // the Null Scorpion, Lattice Nodes and the glassed wastes
            at("null_shard", "minecraft:entities/enderman"),
            at("null_shard", "minecraft:chests/end_city_treasure"),
            // Project Remains and the Containment Vaults
            at("relic_shard", "minecraft:archaeology/trail_ruins_rare"),
            at("relic_shard", "minecraft:archaeology/trail_ruins_common"),
            at("relic_shard", "minecraft:chests/desert_pyramid"),
            // the anomalies' shards
            at("phasium", "minecraft:entities/enderman"),
            at("phasium", "minecraft:chests/end_city_treasure"),
            at("lattice", "minecraft:chests/end_city_treasure"),
            at("chitin", "minecraft:entities/spider"),
            at("chitin", "minecraft:entities/cave_spider"));

    /** Every item that needs a source: the drops, and every shard the roster ships. */
    public static List<String> seeds() {
        List<String> out = new ArrayList<>();
        for (CordonMaterials.Drop drop : CordonMaterials.DROPS) {
            out.add(drop.name());
        }
        for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
            if (material.kind() == CordonMaterials.Kind.SHARD && material.shipped()) {
                out.add(material.family());
            }
        }
        return List.copyOf(out);
    }

    /** The sources of one seed, in table order. */
    public static List<Source> of(String seed) {
        List<Source> out = new ArrayList<>();
        for (Source source : SOURCES) {
            if (source.seed().equals(seed)) {
                out.add(source);
            }
        }
        return List.copyOf(out);
    }

    /** Every seed with no source: a root the world does not give. */
    public static List<String> unsourced() {
        List<String> out = new ArrayList<>();
        for (String seed : seeds()) {
            if (of(seed).isEmpty()) {
                out.add(seed);
            }
        }
        return List.copyOf(out);
    }

    /** Every (seed, table) pair written twice: a rate doubled by accident. */
    public static List<Source> duplicated() {
        Set<Source> seen = new HashSet<>();
        List<Source> out = new ArrayList<>();
        for (Source source : SOURCES) {
            if (!seen.add(source)) {
                out.add(source);
            }
        }
        return List.copyOf(out);
    }

    /** Every source naming a seed the roster does not ship: a modifier that adds nothing. */
    public static List<Source> orphans() {
        Set<String> seeds = new LinkedHashSet<>(seeds());
        List<Source> out = new ArrayList<>();
        for (Source source : SOURCES) {
            if (!seeds.contains(source.seed())) {
                out.add(source);
            }
        }
        return List.copyOf(out);
    }

    /** Every source on a table that is not vanilla's: a dependency this module must not have. */
    public static List<Source> foreign() {
        List<Source> out = new ArrayList<>();
        for (Source source : SOURCES) {
            if (!source.table().startsWith("minecraft:")) {
                out.add(source);
            }
        }
        return List.copyOf(out);
    }

    /** Every distinct vanilla table listened to. */
    public static List<String> tables() {
        Set<String> out = new LinkedHashSet<>();
        for (Source source : SOURCES) {
            out.add(source.table());
        }
        return List.copyOf(out);
    }
}
