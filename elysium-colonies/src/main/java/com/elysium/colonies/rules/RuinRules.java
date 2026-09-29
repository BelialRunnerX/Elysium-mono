package com.elysium.colonies.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where the world leaves a ruin, and how far it has fallen.
 *
 * <p>The living half of natural colonies is {@link SettleRules}: a stake that
 * founds a town. This is the other half the roadmap asked for (§3, item 1,
 * "abandoned"): a marker the world places at its own sites, which on its
 * first random tick raises one building of the world's style pack as a ruin —
 * no colony, no citizens, no work orders, a chest of what was left — and is
 * gone. Everything here is a number the harness can hold: how far apart, how
 * much of a wall is missing by height, what weathers into what, what a ruin
 * cannot keep at all, and where the loot is.
 *
 * <h2>Decay</h2>
 * A ruin is read from the bottom: floors stay, walls thin as they rise, roofs
 * are mostly gone. {@link #removedShare} is that curve, and {@link #keep} rolls
 * it per block from the world seed and the block's place, so the same ruin is
 * the same ruin on every load. Wood rots faster than stone. Panes, doors,
 * lights and cloth do not survive at all. Stone that stays weathers: bricks go
 * mossy or cracked, cobblestone mossy.
 */
public final class RuinRules {

    private RuinRules() {
    }

    // ------------------------------------------------------------------
    // Where
    // ------------------------------------------------------------------

    /** Chunks between ruin sites; further apart than towns, which are 40. */
    public static final int SPACING = 56;

    /** The nearest two sites may be, in chunks. */
    public static final int SEPARATION = 24;

    /** The structure set's salt: its own, so ruins and towns never share a grid. */
    public static final int SALT = 61_209_733;

    /** Chunks from the world spawn a ruin keeps clear, half a town's. */
    public static final int FROM_SPAWN = 12;

    /** The buildings a ruin may once have been: the host's registry ids, level-one blueprints. */
    public static final List<String> KINDS = List.of(
            "minecolonies:residence", "minecolonies:tavern", "minecolonies:warehouse", "minecolonies:builder");

    /** The chest's loot table. */
    public static final String LOOT = "elysiumcolonies:chests/ruin";

    // ------------------------------------------------------------------
    // How far fallen
    // ------------------------------------------------------------------

    /** The share of the floor row that is missing. */
    public static final double FLOOR_MISSING = 0.05;
    /** The share missing where the walls begin. */
    public static final double WALL_MISSING = 0.15;
    /** The share missing at the top row: most of a roof, never all of it. */
    public static final double TOP_MISSING = 0.78;
    /** Wood rots: this much more of it is gone than stone at the same height. */
    public static final double ROT = 0.25;

    /**
     * The share of blocks missing at a row, from the floor (0) to the top
     * (height - 1). Monotone: nothing is ever more intact than the row below it.
     */
    public static double removedShare(int y, int height) {
        if (y <= 0 || height <= 1) {
            return FLOOR_MISSING;
        }
        double rise = (double) y / (double) (height - 1);
        return Math.min(TOP_MISSING, WALL_MISSING + (TOP_MISSING - WALL_MISSING) * rise);
    }

    /** A unit number in [0, 1) from the seed and a place, the same every time. */
    public static double unit(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Whether a block of a ruin still stands. */
    public static boolean keep(long seed, int x, int y, int z, int height, boolean wooden) {
        double missing = removedShare(y, height) + (wooden ? ROT : 0.0);
        return unit(seed, x, y, z) >= Math.min(0.95, missing);
    }

    // ------------------------------------------------------------------
    // What a ruin cannot keep, and what weathers
    // ------------------------------------------------------------------

    /** Words in a block id that mean it did not survive: glass, hangings, lights, doors. */
    public static final List<String> FLIMSY = List.of(
            "glass", "pane", "door", "torch", "lantern", "carpet", "bed", "banner", "ladder", "flower_pot",
            "candle", "campfire", "trapdoor", "sign", "painting", "item_frame", "bell");

    /** Words in a block id that mean wood, which rots. */
    public static final List<String> WOODEN = List.of("planks", "log", "wood", "fence", "stairs", "slab", "bookshelf", "barrel");

    /** What weathering turns a block into: the id it was, and the ids it may become (rolled by place). */
    public static final Map<String, List<String>> WEATHERED = Map.of(
            "minecraft:stone_bricks", List.of("minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
            "minecraft:stone_brick_stairs", List.of("minecraft:mossy_stone_brick_stairs"),
            "minecraft:stone_brick_slab", List.of("minecraft:mossy_stone_brick_slab"),
            "minecraft:stone_brick_wall", List.of("minecraft:mossy_stone_brick_wall"),
            "minecraft:cobblestone", List.of("minecraft:mossy_cobblestone"),
            "minecraft:cobblestone_stairs", List.of("minecraft:mossy_cobblestone_stairs"),
            "minecraft:cobblestone_slab", List.of("minecraft:mossy_cobblestone_slab"),
            "minecraft:cobblestone_wall", List.of("minecraft:mossy_cobblestone_wall"),
            "minecraft:deepslate_bricks", List.of("minecraft:cracked_deepslate_bricks"),
            "minecraft:deepslate_tiles", List.of("minecraft:cracked_deepslate_tiles"));

    /** The share of a weatherable block that weathers. */
    public static final double WEATHERS = 0.45;

    public static boolean flimsy(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String word : FLIMSY) {
            if (path.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Stone stairs and slabs are stone: wood is the wooden kinds of them. */
    public static boolean wooden(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("stone") || path.contains("brick") || path.contains("deepslate") || path.contains("blackstone")) {
            return false;
        }
        for (String word : WOODEN) {
            if (path.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** The id a kept block has after the weather, which may be its own. */
    public static String weathered(long seed, int x, int y, int z, String id) {
        List<String> into = WEATHERED.get(id);
        if (into == null || into.isEmpty()) {
            return id;
        }
        double roll = unit(seed + 7, x, y, z);
        if (roll >= WEATHERS) {
            return id;
        }
        return into.get((int) Math.floor(roll / WEATHERS * into.size()) % into.size());
    }

    // ------------------------------------------------------------------
    // Faults
    // ------------------------------------------------------------------

    public static List<String> faults() {
        List<String> faults = new ArrayList<>();
        if (SPACING <= SEPARATION || SEPARATION <= 0) {
            faults.add("a structure set with separation at or above spacing places nothing");
        }
        if (SALT == SettleRules.SALT) {
            faults.add("ruins and towns share a salt, so they are placed on one grid and fight for sites");
        }
        if (FROM_SPAWN <= 0) {
            faults.add("a ruin may be placed on the world spawn");
        }
        if (KINDS.isEmpty()) {
            faults.add("no kind of building may be a ruin, so a marker raises nothing");
        }
        if (!LOOT.contains(":")) {
            faults.add("the loot table is not a namespaced id");
        }
        int height = 12;
        double last = -1.0;
        for (int y = 0; y < height; y++) {
            double share = removedShare(y, height);
            if (share < last) {
                faults.add("row " + y + " is more intact than the row below it; a ruin falls from the top");
            }
            last = share;
        }
        if (removedShare(0, height) >= removedShare(height - 1, height)) {
            faults.add("the floor is no more intact than the roof");
        }
        if (removedShare(height - 1, height) >= 1.0 || TOP_MISSING >= 1.0) {
            faults.add("the top row is wholly gone, so no ruin ever shows a roofline");
        }
        if (FLOOR_MISSING <= 0.0 || FLOOR_MISSING > 0.2) {
            faults.add("the floor is either untouched or mostly gone; a ruin's floor is worn, not missing");
        }
        if (ROT <= 0.0 || WEATHERS <= 0.0 || WEATHERS >= 1.0) {
            faults.add("wood that never rots or stone that always or never weathers is not a ruin");
        }
        for (Map.Entry<String, List<String>> entry : WEATHERED.entrySet()) {
            if (!entry.getKey().contains(":") || entry.getValue().isEmpty()) {
                faults.add("the weathering of " + entry.getKey() + " is not a namespaced id or leads nowhere");
            }
            for (String into : entry.getValue()) {
                if (!into.contains(":")) {
                    faults.add(entry.getKey() + " weathers into \"" + into + "\", which is not a namespaced id");
                }
            }
        }
        // Determinism: the same place rolls the same, and different places differ.
        if (unit(1L, 1, 2, 3) != unit(1L, 1, 2, 3) || unit(1L, 1, 2, 3) == unit(1L, 3, 2, 1)) {
            faults.add("the roll is not a function of the seed and the place");
        }
        return faults;
    }
}
