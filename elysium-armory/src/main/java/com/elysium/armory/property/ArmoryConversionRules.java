package com.elysium.armory.property;

import java.util.List;
import java.util.Map;

/**
 * What a piece of the base game's gear becomes when it is made modular
 * (Silent Gear's SG-42).
 *
 * <h2>Read off the name</h2>
 *
 * {@code minecraft:iron_pickaxe} is iron, and a pickaxe, so a vanilla piece
 * this module has never heard of converts the moment its material exists. The
 * shape is the end of the name and the material is what comes before it.
 *
 * <h2>A whole word, the longest first</h2>
 *
 * "iron_pickaxe" also ends in "axe", and "crossbow" in "bow". The recipe once
 * took the first shape its map offered that the name ended with, and a
 * {@code Map.ofEntries} is ordered differently on every launch: in twelve
 * starts a pickaxe converted eight times and a crossbow three, and shears
 * never did, having no material in the name and none of their own (found 29
 * September 2026). Here a shape counts only as the whole name or after an
 * underscore, and the longest one that counts wins, so the answer is the same
 * on every start whatever order {@link #SHAPES} is in.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ArmoryConversionRules {

    private ArmoryConversionRules() {
    }

    /** The base game's material words, under the names the material files use. */
    public static final Map<String, String> MATERIALS = Map.of(
            "wooden", "wood", "stone", "stone", "iron", "iron", "golden", "gold",
            "diamond", "diamond", "netherite", "netherite", "leather", "leather", "chainmail", "iron");

    /**
     * One of the base game's shapes: the word its names end with, the gear
     * type it becomes, and what it is made of when the name says no material
     * (a bow, a shield). A null material means the name has to say.
     */
    public record Shape(String word, String gearType, String material) {
    }

    /** The shapes that convert. Order does not matter: see the class comment. */
    public static final List<Shape> SHAPES = List.of(
            new Shape("sword", "sword", null),
            new Shape("pickaxe", "pickaxe", null),
            new Shape("axe", "axe", null),
            new Shape("shovel", "shovel", null),
            new Shape("hoe", "hoe", null),
            new Shape("helmet", "helmet", null),
            new Shape("chestplate", "chestplate", null),
            new Shape("leggings", "leggings", null),
            new Shape("boots", "boots", null),
            // Two iron ingots in the base game, and nothing in the name says so.
            new Shape("shears", "shears", "iron"),
            new Shape("bow", "bow", "wood"),
            new Shape("crossbow", "crossbow", "wood"),
            new Shape("trident", "trident", "prismarine"),
            new Shape("shield", "shield", "wood"),
            new Shape("fishing_rod", "fishing_rod", "wood"),
            new Shape("elytra", "elytra", "phantom_membrane"));

    /** What a base-game item becomes: a gear type, and the material it is made of. */
    public record Conversion(String gearType, String material) {
    }

    /**
     * What the base game's item of this name becomes, or null when it becomes
     * nothing.
     *
     * @param path the path of a {@code minecraft:} item id, such as "iron_pickaxe"
     */
    public static Conversion of(String path) {
        return of(path, SHAPES);
    }

    /** The same, against a list of shapes in any order; the harness shuffles it. */
    public static Conversion of(String path, List<Shape> shapes) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        Shape best = null;
        for (Shape shape : shapes) {
            boolean whole = path.equals(shape.word()) || path.endsWith("_" + shape.word());
            if (whole && (best == null || shape.word().length() > best.word().length())) {
                best = shape;
            }
        }
        if (best == null) {
            return null;
        }
        String prefix = path.equals(best.word()) ? ""
                : path.substring(0, path.length() - best.word().length() - 1);
        String material = prefix.isEmpty() ? best.material() : MATERIALS.get(prefix);
        return material == null ? null : new Conversion(best.gearType(), material);
    }
}
