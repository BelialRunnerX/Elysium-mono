package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shapes a house is made of. The JDK and nothing else at all.
 *
 * <h2>What a decoration mod is, and what it gets wrong</h2>
 *
 * Thirty jars in this pack exist to add chairs, shelves, lattices and lamps.
 * Between them they add several thousand blocks and one recurring bug: a way
 * of turning a material into a shape and back again that comes out ahead.
 *
 * <p>It is never deliberate and it is never noticed. Somebody writes "four
 * planks make four chairs" because four is a nice number, and somebody else
 * writes "a chair gives a plank back" because it would be annoying not to, and
 * a chair costs three quarters of a plank, so the loop pays. Nobody reads the
 * four hundred recipes; the loop sits there for a year.
 *
 * <h2>So every shape has a price, in eighths of a block</h2>
 *
 * A block of anything is {@link #WHOLE} units. Every shape costs some number
 * of units, every recipe is generated from those numbers rather than typed,
 * and the harness proves over the whole catalogue that <b>no recipe makes more
 * units than it consumes</b> and <b>no chain of them comes back with more than
 * it started with</b>. Which is the same property {@code elysium-arcana}
 * proves about the whole game, applied to the corner of it this module owns.
 */
public final class AtelierRules {

    private AtelierRules() {
    }

    /** What a whole block is worth, in the units shapes are priced in. */
    public static final int WHOLE = 8;

    /**
     * One shape, and what it costs.
     *
     * @param name  what it is called, which is also half its block id
     * @param units what one of it costs, in eighths of a block
     * @param made  how many one crafting makes
     * @param of    how many blocks of material one crafting takes
     */
    public record Shape(String name, int units, int made, int of) {

        public Shape {
            if (units < 1 || units > WHOLE * 2) {
                throw new IllegalArgumentException("a shape costing " + units
                        + " eighths is either free or is more than two blocks");
            }
            if (made < 1 || of < 1) {
                throw new IllegalArgumentException("a recipe that makes nothing, or "
                        + "takes nothing, is not a recipe");
            }
        }

        /** What one crafting consumes, in units. */
        public int spends() {
            return of * WHOLE;
        }

        /** What one crafting produces, in units. */
        public int yields() {
            return made * units;
        }

        /** Whether this shape's recipe makes more than it takes. */
        public boolean profitable() {
            return yields() > spends();
        }

        /**
         * How many blocks of material breaking one of these gives back.
         *
         * <p>Rounded down, always, and that is the whole of why no loop pays:
         * a chair worth six eighths gives back nothing, so six chairs are six
         * chairs and never a block of wood.
         */
        public int recovers() {
            return units / WHOLE;
        }
    }

    /**
     * Every shape, and why these twelve.
     *
     * <p>Because vanilla already has slabs, stairs, walls, fences, doors and
     * trapdoors, and a decoration mod that adds those again adds nothing. What
     * a house needs and the game does not have is somewhere to sit, somewhere
     * to put things, and the pieces that make a wall look built rather than
     * placed.
     *
     * <p>That sentence is now a check rather than a promise. {@link JoinRules}
     * adds the five kinds this list deliberately leaves out — but only in the
     * materials the game has none of, which it works out from a written-down
     * list of what the game actually ships. Its first draft added sixteen
     * materials' worth of doors, eleven of which have been in the game since
     * 2011; the principle was in a javadoc and the javadoc did not stop it.
     */
    public static final List<Shape> SHAPES = List.of(
            // Somewhere to sit and somewhere to put a cup.
            new Shape("stool", 4, 4, 2),
            new Shape("chair", 6, 4, 3),
            new Shape("bench", 8, 2, 2),
            new Shape("table", 6, 4, 4),
            // Somewhere to put things.
            new Shape("shelf", 6, 4, 3),
            new Shape("cabinet", 8, 6, 6),
            new Shape("crate", 8, 4, 4),
            // The pieces that make a wall look built.
            new Shape("pillar", 8, 3, 3),
            new Shape("beam", 4, 12, 6),
            new Shape("lattice", 4, 8, 4),
            new Shape("shutter", 3, 12, 5),
            // And something soft.
            new Shape("rug", 2, 16, 5),
            // A kitchen, which is the one room the game gives a player nothing
            // for at all.
            new Shape("counter", 8, 4, 4),
            new Shape("sink", 8, 2, 2),
            new Shape("stove", 8, 2, 2),
            // Somewhere to put clothes, and the rest of a bedroom.
            new Shape("wardrobe", 8, 5, 6),
            new Shape("drawers", 8, 4, 5),
            new Shape("nightstand", 6, 4, 3),
            new Shape("desk", 7, 5, 5),
            // Things that hold other things.
            new Shape("planter", 5, 4, 3),
            new Shape("trough", 6, 4, 4),
            new Shape("rack", 4, 6, 3),
            // And two that divide a room rather than fill it.
            new Shape("screen", 4, 6, 3),
            new Shape("post", 4, 8, 4));

    /**
     * Every material the shapes come in.
     *
     * <p>All vanilla, every one of them, which is the reason this module
     * declares no dependencies and ships no textures: a chair is a shape cut
     * out of wood somebody already has, wearing that wood's own face.
     *
     * @param name  the material, which is half every block id
     * @param block the vanilla block a recipe consumes
     * @param face  the vanilla texture the shape wears
     * @param soft  whether a hand alone works it — whether it is, in the
     *              game's own word, wooden. It decides nothing about a chair;
     *              it decides everything about a door, which is why it lives
     *              here rather than in a list somewhere else that would
     *              eventually disagree with this one.
     */
    public record Material(String name, String block, String face, boolean soft) {
    }

    private static Material wood(String name) {
        return new Material(name, "minecraft:" + name + "_planks",
                "minecraft:block/" + name + "_planks", true);
    }

    private static Material stone(String name, String texture) {
        return new Material(name, "minecraft:" + name, "minecraft:block/" + texture,
                false);
    }

    public static final List<Material> MATERIALS = materials();

    private static List<Material> materials() {
        List<Material> found = new ArrayList<>();
        for (String name : List.of("oak", "spruce", "birch", "jungle", "acacia",
                "dark_oak", "mangrove", "cherry", "crimson", "warped")) {
            found.add(wood(name));
        }
        found.add(new Material("bamboo", "minecraft:bamboo_planks",
                "minecraft:block/bamboo_planks", true));
        for (String name : List.of("stone", "deepslate", "tuff", "blackstone",
                "andesite", "diorite", "granite", "calcite", "dripstone_block",
                "smooth_basalt", "sandstone", "red_sandstone", "prismarine",
                "dark_prismarine", "purpur_block", "bricks", "mud_bricks",
                "nether_bricks", "end_stone_bricks", "amethyst_block")) {
            found.add(stone(name, name));
        }
        // The two whose face is not called what the block is called. Written
        // out rather than special-cased in stone(), because the exception is
        // the interesting thing: a pillar has a side and a top, and a shape
        // cut out of one wears its side.
        found.add(new Material("quartz", "minecraft:quartz_block",
                "minecraft:block/quartz_block_side", false));
        found.add(new Material("basalt", "minecraft:basalt",
                "minecraft:block/basalt_side", false));
        return List.copyOf(found);
    }

    /** Every block this module adds, as material and shape. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>(MATERIALS.size() * SHAPES.size());
        for (Material material : MATERIALS) {
            for (Shape shape : SHAPES) {
                names.add(material.name() + "_" + shape.name());
            }
        }
        return List.copyOf(names);
    }

    /** How many blocks the catalogue comes to. */
    public static int howMany() {
        return MATERIALS.size() * SHAPES.size();
    }

    /**
     * Every shape whose recipe makes more units than it consumes.
     *
     * <p>Must be empty. One entry is a loop a player can ride for ever, in a
     * module nobody would think to look in.
     */
    public static List<String> profitable() {
        List<String> caught = new ArrayList<>();
        for (Shape shape : SHAPES) {
            if (shape.profitable()) {
                caught.add(shape.name());
            }
        }
        return caught;
    }

    /**
     * Every shape you could craft and break for a profit.
     *
     * <p>The other direction, and the one that is actually missed: a recipe
     * can lose units and the pair still pay, if breaking gives back more than
     * the share of a block the shape cost. Must be empty.
     */
    public static List<String> recoverable() {
        List<String> caught = new ArrayList<>();
        for (Shape shape : SHAPES) {
            if (shape.recovers() * WHOLE * shape.made() > shape.spends()) {
                caught.add(shape.name());
            }
        }
        return caught;
    }

    /** What the whole catalogue costs, as a map from block name to units. */
    public static Map<String, Integer> priced() {
        Map<String, Integer> prices = new LinkedHashMap<>();
        for (Material material : MATERIALS) {
            for (Shape shape : SHAPES) {
                prices.put(material.name() + "_" + shape.name(), shape.units());
            }
        }
        return Map.copyOf(prices);
    }

    /** A shape by name, or null. */
    public static Shape shape(String name) {
        for (Shape shape : SHAPES) {
            if (shape.name().equals(name)) {
                return shape;
            }
        }
        return null;
    }
}
