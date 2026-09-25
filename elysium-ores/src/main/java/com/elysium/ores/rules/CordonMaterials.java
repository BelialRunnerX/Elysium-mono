package com.elysium.ores.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The materials behind Valoria's seventeen gear sets, as the Cordoned Planet's
 * own (docs/SPLITS.md "Valoria: materials"; docs/SPEC_VALORIA.md).
 *
 * <h2>Why this table is here</h2>
 *
 * Valoria's gear and Valoria's materials were two lists in two places, and
 * they had already drifted: four of its armour sets had no material at all and
 * were found, never made. The rule this module inherits — a tier with no
 * material behind it is a weapon nobody can craft — means one list, read by
 * everything. This is it. elysium-arsenal and elysium-armory take their rungs
 * from {@link #SETS}; the generator writes every item, tag and recipe from
 * {@link #MATERIALS}; the ore half of this module gets its families from the
 * same rows. Nothing about a Cordoned material is typed twice.
 *
 * <h2>How Valoria's sets were read</h2>
 *
 * Out of its own language file: every item ending in a gear suffix names a
 * set, and every set's recipes name the material. Where two sets shared a
 * material they share a family here — the depth armour was made of aquarius
 * ingots, so both are {@code bathysteel}; the awakened void armour was made of
 * void ingots, so both are {@code nullsteel}. The lunar set had no material
 * and is given the Pale Moon's: it is {@code lunite}, which elysium-space
 * already mines, so this module ships nothing for it and names that module
 * nowhere. The phantasm set had none either and becomes the lattice, the Null
 * Lattice's own crystal.
 *
 * <h2>Kinds</h2>
 *
 * <ul>
 *   <li><b>METAL</b>: dug. Raw, ingot, nugget, a block of each; the ore drops
 *       the raw form and a furnace makes the ingot.</li>
 *   <li><b>GEM</b>: dug, and already finished. The gem and its block.</li>
 *   <li><b>ALLOY</b>: made, from {@code madeOf}. Ingot, nugget and block.</li>
 *   <li><b>SHARD</b>: dropped or found, never dug. The shard and its block.</li>
 *   <li><b>CLOTH</b>: woven from vanilla things. The cloth and its block.</li>
 *   <li><b>EXTERNAL</b>: another Elysium module's family. Nothing shipped.</li>
 * </ul>
 *
 * <p>The numbers are Valoria's own tool tiers and armour materials, kept for
 * the arsenal and the armory to read; this class does not interpret them. The
 * JDK and nothing else, so the harness can walk every row.
 */
public final class CordonMaterials {

    private CordonMaterials() {
    }

    public enum Kind { METAL, GEM, ALLOY, SHARD, CLOTH, EXTERNAL }

    /** The element a set's gear carries; NONE for inert struck metal. */
    public enum Element { VOID, PLASMA, NEURAL, DIMENSIONAL, KINETIC, NONE }

    /** Valoria's tool tier: harvest level, durability, speed, bonus damage, enchantability. */
    public record Tier(int level, int uses, float speed, float damage, int enchant) {
    }

    /** Valoria's armour material: total protection, durability multiplier, enchantability, knockback resistance, Null defence. */
    public record Plate(int protection, int durability, int enchant, float knockback, float nullDefence) {
    }

    /**
     * One material.
     *
     * @param family  the {@code c:} family name and the item prefix
     * @param kind    what is shipped for it
     * @param band    how hard the ore is to find (OreRules), for dug kinds
     * @param element what its gear carries
     * @param colour  its base hue, from which the art derives every ramp
     * @param tool    the tool tier, or null for a material nothing is swung from
     * @param armour  the armour material, or null for one nothing is worn from
     * @param madeOf  what an alloy takes, by item (a family's ingot as
     *                {@code <family>_ingot}, a drop by name, or a vanilla id)
     * @param yield   how many ingots the alloy recipe makes
     */
    public record Material(String family, Kind kind, int band, Element element, String colour,
                           Tier tool, Plate armour, Map<String, Integer> madeOf, int yield) {

        public Material {
            madeOf = Map.copyOf(madeOf);
        }

        /** Whether this module ships items for it. */
        public boolean shipped() {
            return kind != Kind.EXTERNAL;
        }

        /** Whether an ore is generated for it. */
        public boolean dug() {
            return kind == Kind.METAL || kind == Kind.GEM;
        }

        /** The item a recipe or a tier asks for: the ingot for metals, the thing itself otherwise. */
        public String ingredient() {
            return kind == Kind.METAL || kind == Kind.ALLOY || kind == Kind.EXTERNAL
                    ? family + "_ingot" : family;
        }

        /** Every item shipped for this material, in registration order. */
        public List<String> items() {
            return switch (kind) {
                case METAL -> List.of("raw_" + family, family + "_ingot", family + "_nugget");
                case ALLOY -> List.of(family + "_ingot", family + "_nugget");
                case GEM, SHARD, CLOTH -> List.of(family);
                case EXTERNAL -> List.of();
            };
        }

        /** Every block shipped for this material. */
        public List<String> blocks() {
            return switch (kind) {
                case METAL -> List.of(family + "_block", "raw_" + family + "_block");
                case EXTERNAL -> List.of();
                default -> List.of(family + "_block");
            };
        }
    }

    /** A creature's or a structure's leaving that an alloy takes; shipped here so the recipes have something to name. */
    public record Drop(String name, Element element, String colour, String from) {
    }

    /** A part made of a material, for gear that is riveted rather than cast. */
    public record Part(String name, String family, String colour, int takes) {
    }

    /** One of Valoria's gear sets and the family that carries it. */
    public record Set(String valoria, String family, boolean tools, boolean armour) {
    }

    private static Material dug(String family, Kind kind, int band, Element element, String colour,
                                Tier tool, Plate armour) {
        return new Material(family, kind, band, element, colour, tool, armour, Map.of(), 0);
    }

    private static Material alloy(String family, Element element, String colour, Tier tool, Plate armour,
                                  int yield, Object... parts) {
        Map<String, Integer> madeOf = new LinkedHashMap<>();
        for (int at = 0; at < parts.length; at += 2) {
            madeOf.put((String) parts[at], (Integer) parts[at + 1]);
        }
        return new Material(family, Kind.ALLOY, OreRules.EXOTIC, element, colour, tool, armour, madeOf, yield);
    }

    private static Material found(String family, Kind kind, Element element, String colour,
                                  Tier tool, Plate armour) {
        return new Material(family, kind, OreRules.EXOTIC, element, colour, tool, armour, Map.of(), 0);
    }

    /**
     * Every material, dug ones first so an alloy never names something below
     * it in the list. Colours are base hues; the art derives the ramps.
     */
    public static final List<Material> MATERIALS = List.of(
            // --- dug: the ores this module generates in every stone ------------
            dug("cobalt", Kind.METAL, OreRules.PRECIOUS, Element.KINETIC, "#4a7fc4",
                    new Tier(3, 1750, 12.0F, 4.0F, 12), new Plate(45, 46, 18, 0.05F, 0.0F)),
            dug("pearlium", Kind.METAL, OreRules.INDUSTRIAL, Element.NONE, "#d9cfe0",
                    new Tier(2, 425, 7.0F, 2.0F, 6), null),
            dug("jade", Kind.GEM, OreRules.EXOTIC, Element.KINETIC, "#4fbf6a",
                    new Tier(5, 4112, 22.0F, 11.0F, 20), null),
            dug("pyratite", Kind.GEM, OreRules.EXOTIC, Element.KINETIC, "#e0a030",
                    new Tier(6, 3112, 24.0F, 13.0F, 15), new Plate(62, 72, 12, 0.0F, 15.0F)),
            dug("amber", Kind.GEM, OreRules.PRECIOUS, Element.PLASMA, "#e8a23a", null, null),
            dug("ruby", Kind.GEM, OreRules.PRECIOUS, Element.PLASMA, "#d0304a", null, null),
            dug("sapphire", Kind.GEM, OreRules.PRECIOUS, Element.DIMENSIONAL, "#3a5fd0", null, null),
            dug("null_amethyst", Kind.GEM, OreRules.EXOTIC, Element.VOID, "#8a4fd6", null, null),

            // --- made: alloys, each of a dug metal and something a creature or a world leaves ---
            alloy("nightgold", Element.NONE, "#6a5a30", null, new Plate(20, 55, 20, 0.0F, 0.0F), 2,
                    "minecraft:gold_ingot", 1, "cobalt_ingot", 1),
            alloy("slagsteel", Element.PLASMA, "#b04a2a",
                    new Tier(5, 4256, 20.0F, 10.0F, 19), new Plate(65, 76, 14, 0.12F, 0.0F), 2,
                    "cobalt_ingot", 1, "ember_slag", 1),
            alloy("biolith", Element.NEURAL, "#6aa04a",
                    new Tier(4, 2651, 16.0F, 8.0F, 17), new Plate(55, 66, 16, 0.10F, 0.0F), 2,
                    "cobalt_ingot", 1, "bio_culture", 1),
            alloy("bathysteel", Element.DIMENSIONAL, "#3a8ab0",
                    new Tier(5, 3256, 18.0F, 9.0F, 18), new Plate(60, 72, 16, 0.10F, 0.0F), 2,
                    "cobalt_ingot", 1, "pressure_shell", 1),
            alloy("hemalite", Element.KINETIC, "#a02a3a",
                    new Tier(5, 2431, 24.0F, 15.0F, 15), new Plate(68, 72, 12, 0.0F, 35.0F), 2,
                    "nightgold_ingot", 1, "blight_crystal", 1, "minecraft:bone", 1),
            alloy("nullsteel", Element.VOID, "#3a2a5a",
                    new Tier(5, 5248, 30.0F, 17.0F, 20), new Plate(70, 80, 10, 0.15F, 75.0F), 2,
                    "nightgold_ingot", 1, "null_shard", 1),
            alloy("relicsteel", Element.KINETIC, "#b0a070",
                    new Tier(2, 1250, 8.0F, 5.0F, 7), new Plate(35, 55, 16, 0.15F, 0.0F), 1,
                    "relic_shard", 4),

            // --- found: what the anomalies leave -------------------------------
            found("phasium", Kind.SHARD, Element.DIMENSIONAL, "#9ad0e0",
                    new Tier(3, 2025, 14.0F, 5.0F, 15), new Plate(50, 60, 18, 0.10F, 0.0F)),
            found("lattice", Kind.SHARD, Element.VOID, "#c8b0ff",
                    new Tier(5, 6428, 35.0F, 20.0F, 20), new Plate(80, 85, 12, 0.25F, 100.0F)),
            found("chitin", Kind.SHARD, Element.NEURAL, "#5a4a3a",
                    new Tier(5, 2831, 22.0F, 11.0F, 15), new Plate(50, 68, 14, 0.10F, 25.0F)),
            found("mireweave", Kind.CLOTH, Element.NEURAL, "#6a7a4a",
                    null, new Plate(20, 58, 12, 0.0F, 0.0F)),

            // --- another module's -----------------------------------------------
            found("lunite", Kind.EXTERNAL, Element.DIMENSIONAL, "#d8cce8",
                    new Tier(2, 1450, 6.0F, 3.0F, 8), null));

    /** What the creatures and the ruins leave behind, and who leaves it. */
    public static final List<Drop> DROPS = List.of(
            new Drop("ember_slag", Element.PLASMA, "#d06020", "the Ashborn, and the Slag Works"),
            new Drop("bio_culture", Element.NEURAL, "#60c060", "the Terraformer Frame and the Rootwalker"),
            new Drop("pressure_shell", Element.DIMENSIONAL, "#60a0c0", "the Shell Carrier"),
            new Drop("blight_crystal", Element.KINETIC, "#c03060", "the Flesh Strains and the Bloom Cysts"),
            new Drop("null_shard", Element.VOID, "#6a3ab0", "the Null Scorpion, Lattice Nodes and the glassed wastes"),
            new Drop("relic_shard", Element.KINETIC, "#c0b080", "Project Remains and the Containment Vaults"));

    /** Parts riveted from a material rather than cast. */
    public static final List<Part> PARTS = List.of(
            new Part("pyratite_plate", "pyratite", "#e0a030", 4));

    /** What a cloth is woven from: vanilla things, by id, and how many of each make {@link #CLOTH_YIELD}. */
    public static final Map<String, Integer> CLOTH_MADE_OF = Map.of("minecraft:bone", 2, "minecraft:string", 2);
    public static final int CLOTH_YIELD = 2;

    /**
     * Valoria's seventeen gear sets, each on the family that carries it here.
     * Whether a set had tools and whether it had armour is read off the same
     * language file the sets were, so the arsenal makes tools for exactly the
     * sets that had them and the armory the armour.
     */
    public static final List<Set> SETS = List.of(
            new Set("infernal", "slagsteel", true, true),
            new Set("nature", "biolith", true, true),
            new Set("crimtane", "hemalite", true, true),
            new Set("jade", "jade", true, false),
            new Set("aquarius", "bathysteel", true, false),
            new Set("cobalt", "cobalt", true, true),
            new Set("ethereal", "phasium", true, true),
            new Set("void", "nullsteel", true, false),
            new Set("lunar", "lunite", true, false),
            new Set("phantasm", "lattice", false, true),
            new Set("pyratite", "pyratite", false, true),
            new Set("spider", "chitin", false, true),
            new Set("black_gold", "nightgold", false, true),
            new Set("awakened_void", "nullsteel", false, true),
            new Set("depth", "bathysteel", false, true),
            new Set("marsh", "mireweave", false, true),
            new Set("samurai", "relicsteel", true, true));

    /** How many sets Valoria had. The harness holds the list to it. */
    public static final int VALORIA_SETS = 17;

    public static Material named(String family) {
        for (Material material : MATERIALS) {
            if (material.family().equals(family)) {
                return material;
            }
        }
        return null;
    }

    /** Every material this module ships something for. */
    public static List<Material> shipped() {
        List<Material> out = new ArrayList<>();
        for (Material material : MATERIALS) {
            if (material.shipped()) {
                out.add(material);
            }
        }
        return List.copyOf(out);
    }

    /** Every family an ore is generated for. */
    public static List<Material> dug() {
        List<Material> out = new ArrayList<>();
        for (Material material : MATERIALS) {
            if (material.dug()) {
                out.add(material);
            }
        }
        return List.copyOf(out);
    }

    /** Every item id (without namespace) this module ships for the Cordon's materials, in order. */
    public static List<String> items() {
        List<String> out = new ArrayList<>();
        for (Material material : MATERIALS) {
            out.addAll(material.items());
        }
        for (Drop drop : DROPS) {
            out.add(drop.name());
        }
        for (Part part : PARTS) {
            out.add(part.name());
        }
        return List.copyOf(out);
    }

    /** Every block id this module ships for them. */
    public static List<String> blocks() {
        List<String> out = new ArrayList<>();
        for (Material material : MATERIALS) {
            out.addAll(material.blocks());
        }
        return List.copyOf(out);
    }

    /** Whether a name in an alloy's madeOf is something this class ships (an ingot, a gem, a drop). */
    public static boolean ships(String ingredient) {
        if (ingredient == null || ingredient.contains(":")) {
            return false;
        }
        for (Material material : MATERIALS) {
            if (material.shipped() && material.items().contains(ingredient)) {
                return true;
            }
        }
        for (Drop drop : DROPS) {
            if (drop.name().equals(ingredient)) {
                return true;
            }
        }
        return false;
    }

    /** Whether every alloy is made of things below it: a dug material, a drop, or vanilla's. */
    public static List<String> unmakeable() {
        List<String> out = new ArrayList<>();
        for (int at = 0; at < MATERIALS.size(); at++) {
            Material material = MATERIALS.get(at);
            if (material.kind() != Kind.ALLOY) {
                continue;
            }
            for (String part : material.madeOf().keySet()) {
                if (part.startsWith("minecraft:")) {
                    continue;
                }
                boolean below = false;
                for (int before = 0; before < at; before++) {
                    if (MATERIALS.get(before).shipped() && MATERIALS.get(before).items().contains(part)) {
                        below = true;
                    }
                }
                for (Drop drop : DROPS) {
                    if (drop.name().equals(part)) {
                        below = true;
                    }
                }
                if (!below) {
                    out.add(material.family() + " is made of " + part + ", which is not below it");
                }
            }
        }
        return out;
    }
}
