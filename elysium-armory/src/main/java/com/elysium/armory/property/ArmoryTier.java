package com.elysium.armory.property;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The harvest ladder (Silent Gear's SG-5): what a material can mine, as a
 * named tier rather than a number.
 *
 * <p>A tier has a <b>level hint</b>, which is what compares two tiers, and the
 * vanilla tag of blocks it cannot mine. Naming the rungs rather than numbering
 * them is what lets copper and amethyst sit between stone and iron (1.5)
 * without renumbering everything above them, and what lets a mod add a rung.
 *
 * <p>A piece of gear takes the best tier among its parts, never the average:
 * a netherite tip on an iron pickaxe mines what netherite mines.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public record ArmoryTier(String name, double levelHint, String incorrectTag) {

    /** Vanilla's four tool tags, which every rung aliases one of. */
    public static final String WOODEN = "minecraft:incorrect_for_wooden_tool";
    public static final String STONE = "minecraft:incorrect_for_stone_tool";
    public static final String IRON = "minecraft:incorrect_for_iron_tool";
    public static final String DIAMOND = "minecraft:incorrect_for_diamond_tool";
    public static final String NETHERITE = "minecraft:incorrect_for_netherite_tool";
    public static final String GOLD = "minecraft:incorrect_for_gold_tool";

    public static final Map<String, ArmoryTier> ALL;

    static {
        Map<String, ArmoryTier> m = new LinkedHashMap<>();
        Object[][] rows = {
                // level 0: anything can mine what these can
                {"wood", 0.0D, WOODEN}, {"bamboo", 0.0D, WOODEN}, {"netherwood", 0.0D, WOODEN},
                {"vanilla_gold", 0.0D, GOLD},
                // level 1: stone and its kin
                {"stone", 1.0D, STONE}, {"flint", 1.0D, STONE}, {"bone", 1.0D, STONE},
                {"basalt", 1.0D, STONE}, {"blackstone", 1.0D, STONE}, {"end_stone", 1.0D, STONE},
                {"netherrack", 1.0D, STONE}, {"obsidian", 1.0D, STONE}, {"sandstone", 1.0D, STONE},
                {"terracotta", 1.0D, STONE}, {"gold", 1.0D, STONE},
                // level 1.5: the copper rung, which also mines what copper tools may
                {"copper", 1.5D, STONE}, {"amethyst", 1.5D, STONE}, {"lapis_lazuli", 1.5D, STONE},
                // level 2: iron
                {"iron", 2.0D, IRON}, {"bronze", 2.0D, IRON}, {"blaze_gold", 2.0D, IRON},
                {"quartz", 2.0D, IRON}, {"emerald", 2.0D, IRON}, {"high_carbon_steel", 2.0D, IRON},
                // level 3: diamond
                {"diamond", 3.0D, DIAMOND}, {"crimson_iron", 3.0D, DIAMOND},
                {"azure_silver", 3.0D, DIAMOND}, {"dimerald", 3.0D, DIAMOND},
                // level 4: netherite
                {"netherite", 4.0D, NETHERITE}, {"crimson_steel", 4.0D, NETHERITE},
                {"azure_electrum", 4.0D, NETHERITE}, {"tyrian_steel", 4.0D, NETHERITE},
        };
        for (Object[] r : rows) {
            m.put((String) r[0], new ArmoryTier((String) r[0], (Double) r[1], (String) r[2]));
        }
        ALL = Map.copyOf(m);
    }

    /** The rungs that satisfy vanilla's copper requirement: level 1.5 and up. */
    public static final double COPPER_LEVEL = 1.5D;

    public static ArmoryTier named(String name) {
        return ALL.get(name);
    }

    /** The better of two tiers; either may be null, and two equal hints keep the first. */
    public static ArmoryTier best(ArmoryTier a, ArmoryTier b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.levelHint() > a.levelHint() ? b : a;
    }

    /** Whether this tier can mine a block that needs {@code needed}. */
    public boolean canMine(ArmoryTier needed) {
        return needed == null || levelHint >= needed.levelHint();
    }

    /** Every tier, in the order they were declared. */
    public static List<ArmoryTier> list() {
        return List.copyOf(ALL.values());
    }
}
