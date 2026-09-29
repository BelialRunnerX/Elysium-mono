package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What mines every block this module registers.
 *
 * <h2>The fault this exists because of</h2>
 *
 * A block whose properties ask for the right tool — its own line, or copied
 * from the game's stone, terracotta or iron block — drops nothing unless it
 * is in one of the game's four {@code mineable} block tags, because with no
 * tag no tool is ever the right one. Nothing tagged 459 of this module's 465
 * blocks (29 September 2026): every botany pot, the altar, the centrifuge,
 * the seven vein generators and the kitchen's iron stations broke, vanished
 * and dropped nothing in a survival player's hands, whatever their loot
 * tables said.
 *
 * <p>So every block here is on a tool's list, or excused by hand for a reason
 * the registry bears out, and {@code validate.py} reads the registries to
 * hold each material to what its blocks are actually built on.
 *
 * <h2>Why the wooden things are on a list too</h2>
 *
 * A cabinet built on the game's planks asks for no tool and would drop with
 * no tag. It is in the axe's tag all the same, as every wooden thing of the
 * game's is, because the tag is also what lets an axe break it faster than a
 * fist. Only two things are broken by hand: a comb block, built on the
 * game's honeycomb block, which is in no tool's tag itself; and a crop, which
 * is gone at a touch, so no tool could be quicker.
 */
public final class HarvestRules {

    private HarvestRules() {
    }

    /** The game's four mining tools, each with a block tag of what it is for. */
    public static final String PICKAXE = "pickaxe";
    public static final String AXE = "axe";
    public static final String SHOVEL = "shovel";
    public static final String HOE = "hoe";
    public static final List<String> TOOLS = List.of(PICKAXE, AXE, SHOVEL, HOE);

    /**
     * No tool at all: the block is in no tag.
     *
     * <p>Allowed only for a reason the registry bears out — built on a game
     * block that is in no tag itself, or gone at a touch — and never for a
     * block that asks for the right tool, since with no tag none is.
     */
    public static final String BY_HAND = "by_hand";

    /**
     * The slots from which a housing is built on steel rather than timber.
     *
     * <p>The plain hive and the apiary are timber and want an axe. The
     * advanced and industrial apiaries hold four and six upgrades and are
     * built on the game's iron block, which asks for a pickaxe. The registry
     * reads this number rather than keeping its own.
     */
    public static final int STEEL_FROM_SLOTS = 4;

    /** The star that stands for the rest of a block id in a kind's name. */
    public static final String ANY = "*";

    // ==================================================================
    // What a block is built on
    // ==================================================================

    /** The materials, named as the registries' own properties helpers are. */
    public static final String CLAY = "clay";
    public static final String FIRED = "fired";
    public static final String STEEL = "steel";
    public static final String STONE = "stone";
    public static final String STONE_BRICKS = "stone_bricks";
    public static final String TIMBER = "timber";
    public static final String NEST = "nest";
    public static final String FUNGUS = "fungus";
    public static final String SOIL = "soil";
    public static final String WAX = "wax";
    public static final String CROP = "crop";

    /**
     * One material a block is built of: the game's block it copies, and so
     * the tool the game gives it.
     *
     * @param id      the material, as the registries' properties helpers name it
     * @param builtOn the game's block(s) the properties are copied from, as
     *                {@code Blocks.X} names them; the validator holds the
     *                registry to this
     * @param tool    one of {@link #TOOLS}, or {@link #BY_HAND}
     * @param why     the reason, in words a reader can check
     */
    public record Material(String id, List<String> builtOn, String tool, String why) {

        public Material {
            Objects.requireNonNull(id, "a material called nothing");
            Objects.requireNonNull(builtOn, id + " is built on null");
            if (builtOn.isEmpty()) {
                throw new IllegalArgumentException(id + " is built on nothing of the game's");
            }
            if (!TOOLS.contains(tool) && !BY_HAND.equals(tool)) {
                throw new IllegalArgumentException(id + " is mined by " + tool
                        + ", which is no tool of the game's");
            }
            if (why == null || why.isBlank()) {
                throw new IllegalArgumentException(id + " is given " + tool
                        + " with no reason");
            }
        }

        /** Whether nothing mines this but a hand. */
        public boolean byHand() {
            return BY_HAND.equals(tool);
        }
    }

    /**
     * Every material, and what mines it.
     *
     * <p>The four that ask for the right tool are the fault this file was
     * written for; the rest are on a list for the speed, or excused.
     */
    public static final List<Material> MATERIALS = List.of(
            new Material(CLAY, List.of("TERRACOTTA"), PICKAXE,
                    "on the game's terracotta, which asks for the right tool"),
            new Material(FIRED, List.of("TERRACOTTA", "STONE"), PICKAXE,
                    "on the game's terracotta or stone, and both ask for the right tool"),
            new Material(STEEL, List.of("IRON_BLOCK"), PICKAXE,
                    "on the game's iron block, which asks for the right tool"),
            new Material(STONE, List.of("STONE"), PICKAXE,
                    "on the game's stone, which asks for the right tool"),
            new Material(STONE_BRICKS, List.of("STONE_BRICKS"), PICKAXE,
                    "on the game's stone bricks, which ask for the right tool"),
            new Material(TIMBER, List.of("OAK_PLANKS"), AXE,
                    "on the game's planks, which ask for no tool and are in the axe's "
                            + "tag for the speed, as every wooden thing of the game's is"),
            new Material(NEST, List.of("BEE_NEST"), AXE,
                    "on the game's bee nest, which asks for no tool and is in the axe's tag"),
            new Material(FUNGUS, List.of("BROWN_MUSHROOM_BLOCK"), AXE,
                    "on the game's mushroom block, which asks for no tool and is in the "
                            + "axe's tag"),
            new Material(SOIL, List.of("DIRT"), SHOVEL,
                    "on the game's dirt, which asks for no tool and is in the shovel's tag"),
            new Material(WAX, List.of("HONEYCOMB_BLOCK"), BY_HAND,
                    "on the game's honeycomb block, which asks for no tool and is in no "
                            + "tool's tag, so a hand is as quick as anything"),
            new Material(CROP, List.of("WHEAT"), BY_HAND,
                    "gone at a touch, as the game's wheat is, so no tool could be quicker"));

    // ==================================================================
    // Which blocks are which
    // ==================================================================

    /**
     * One kind of block, and the material it is built of.
     *
     * @param name     the block id, or {@link #ANY} and a suffix for every block
     *                 whose id ends that way ({@code *_comb_block})
     * @param material one of {@link #MATERIALS}, by id
     */
    public record Kind(String name, String material) {

        public Kind {
            Objects.requireNonNull(name, "a kind of nothing");
            Objects.requireNonNull(material, name + " is built of null");
            if (name.isBlank() || name.equals(ANY)) {
                throw new IllegalArgumentException("a kind with no name");
            }
        }

        /** Whether the name is one block's id rather than a suffix. */
        public boolean exact() {
            return !name.startsWith(ANY);
        }

        /** The suffix, for a kind that is one. */
        public String suffix() {
            return exact() ? name : name.substring(ANY.length());
        }

        /** Whether this kind is one a block id belongs to. */
        public boolean covers(String block) {
            return exact() ? name.equals(block)
                    : block.endsWith(suffix()) && block.length() > suffix().length();
        }
    }

    /**
     * Every kind of block the module registers.
     *
     * <p>An exact name wins over a suffix, so the rock generator is stone
     * where every other generator is steel. The validator holds each kind to
     * the statement that registers it, and refuses a kind nothing registers.
     */
    public static final List<Kind> KINDS = List.of(
            // Clay: the growing pot, and the botany pots in their three fittings.
            new Kind("pot", CLAY),
            new Kind("*_botany_pot", FIRED),
            // Steel: the machines, and the two apiaries above the timber one.
            new Kind("accelerator", STEEL),
            new Kind("centrifuge", STEEL),
            new Kind("*_generator", STEEL),
            new Kind("advanced_apiary", STEEL),
            new Kind("industrial_apiary", STEEL),
            new Kind("cooking_pot", STEEL),
            new Kind("skillet", STEEL),
            new Kind("stone_oven", STEEL),
            // Stone: the first rock generator, which wants no power, and the altar.
            new Kind("rock_generator", STONE),
            new Kind("awakening_altar", STONE_BRICKS),
            new Kind("essence_pedestal", STONE_BRICKS),
            new Kind("essence_relay", STONE_BRICKS),
            // Timber: the hive and the apiary, the tackle, the kitchen's furniture.
            new Kind("hive", TIMBER),
            new Kind("apiary", TIMBER),
            new Kind("worm_farm", TIMBER),
            new Kind("tackle_box", TIMBER),
            new Kind("*_mount", TIMBER),
            new Kind("cutting_board", TIMBER),
            new Kind("*_cabinet", TIMBER),
            new Kind("*_crate", TIMBER),
            // The rest of what an axe is for.
            new Kind("*_nest", NEST),
            new Kind("mushroom_colony", FUNGUS),
            // Soil, which is a shovel's.
            new Kind("clay_bed", SOIL),
            new Kind("gravel_bed", SOIL),
            new Kind("rich_loam", SOIL),
            new Kind("ashen_bed", SOIL),
            new Kind("void_silt", SOIL),
            // And the two things broken by hand.
            new Kind("*_comb_block", WAX),
            new Kind("*_crop", CROP));

    // ==================================================================
    // Asking
    // ==================================================================

    /** The material by id, or null. */
    public static Material material(String id) {
        return materialIn(MATERIALS, id);
    }

    private static Material materialIn(List<Material> materials, String id) {
        for (Material one : materials) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The kind a block belongs to — an exact name first, then a suffix — or null. */
    public static Kind kindOf(String block) {
        return kindIn(KINDS, block);
    }

    private static Kind kindIn(List<Kind> kinds, String block) {
        for (Kind kind : kinds) {
            if (kind.exact() && kind.covers(block)) {
                return kind;
            }
        }
        for (Kind kind : kinds) {
            if (!kind.exact() && kind.covers(block)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * What mines a block: one of {@link #TOOLS}, or {@link #BY_HAND}.
     *
     * @throws IllegalArgumentException for a block no kind covers, because a
     *         block nothing says how to mine is the fault this file is for
     */
    public static String tool(String block) {
        return toolIn(KINDS, MATERIALS, block);
    }

    private static String toolIn(List<Kind> kinds, List<Material> materials, String block) {
        Kind kind = kindIn(kinds, block);
        if (kind == null) {
            throw new IllegalArgumentException(block + " is a block no kind covers, so "
                    + "nothing says what mines it");
        }
        Material material = materialIn(materials, kind.material());
        if (material == null) {
            throw new IllegalArgumentException(block + " is built of " + kind.material()
                    + ", which is no material here");
        }
        return material.tool();
    }

    /** The reason a block is mined the way it is. */
    public static String why(String block) {
        Kind kind = kindOf(block);
        Material material = kind == null ? null : material(kind.material());
        return material == null ? "" : material.why();
    }

    /**
     * Every one of these blocks that no kind covers.
     *
     * <p>This is the rule that finds the fault: a block in this list is one
     * the generator can put in no tag and the validator can excuse for no
     * reason, so it is mined by nothing and drops nothing.
     */
    public static List<String> unknown(List<String> blocks) {
        List<String> bad = new ArrayList<>();
        for (String block : blocks) {
            if (kindOf(block) == null) {
                bad.add(block);
            }
        }
        return List.copyOf(bad);
    }

    /** The materials nothing mines but a hand, each with its reason. */
    public static List<Material> byHand() {
        List<Material> made = new ArrayList<>();
        for (Material one : MATERIALS) {
            if (one.byHand()) {
                made.add(one);
            }
        }
        return List.copyOf(made);
    }

    /**
     * Every block the other rules classes name, which is every block the
     * registries walk from a rules table.
     *
     * <p>What is left — the six blocks registered by name, the cabinets, the
     * crates and the five soils — is read out of the registries by the
     * validator, since a pure rules class cannot see them.
     */
    public static List<String> catalogued() {
        List<String> ids = new ArrayList<>();
        for (ApiaryRules.Housing housing : ApiaryRules.HOUSINGS) {
            ids.add(housing.id());
        }
        ids.add(ApiaryRules.CENTRIFUGE);
        ids.addAll(AltarRules.everything());
        ids.addAll(TackleRules.blocks());
        for (AgronomyRules.Vein vein : AgronomyRules.VEINS) {
            ids.add(vein.material() + "_generator");
        }
        for (BeeRules.Bee bee : BeeRules.BEES) {
            ids.add(bee.combBlock());
        }
        for (String home : BeeRules.homes()) {
            ids.add(home + "_nest");
        }
        for (CropRules.Crop crop : CropRules.CROPS) {
            ids.add(crop.id() + "_crop");
        }
        ids.add(CropRules.COLONY);
        for (PotRules.Pot pot : PotRules.catalogue()) {
            ids.add(pot.id());
        }
        return List.copyOf(ids);
    }

    // ==================================================================
    // Whether the tables hang together
    // ==================================================================

    /** Everything wrong with the tables as they stand. */
    public static List<String> faults() {
        return faultsIn(KINDS, MATERIALS);
    }

    /**
     * Everything wrong with a pair of tables.
     *
     * <p>A kind listed twice, a kind of a material nobody declared, a
     * material nothing is built of, two suffixes that both cover one block,
     * a housing on the wrong tool for its slots, and a block the other rules
     * name that no kind covers — which is the fault itself.
     */
    public static List<String> faultsIn(List<Kind> kinds, List<Material> materials) {
        List<String> bad = new ArrayList<>();
        Set<String> materialIds = new HashSet<>();
        for (Material one : materials) {
            if (!materialIds.add(one.id())) {
                bad.add("two materials are called " + one.id());
            }
        }
        Set<String> names = new HashSet<>();
        for (Kind kind : kinds) {
            if (!names.add(kind.name())) {
                bad.add(kind.name() + " is listed twice");
            }
            if (!materialIds.contains(kind.material())) {
                bad.add(kind.name() + " is built of " + kind.material()
                        + ", which is no material here");
            }
        }
        for (Kind one : kinds) {
            for (Kind other : kinds) {
                if (one != other && !one.exact() && !other.exact()
                        && one.suffix().endsWith(other.suffix())) {
                    bad.add(one.name() + " and " + other.name() + " both cover every "
                            + "block ending " + one.suffix() + ", so which mines it "
                            + "depends on the order of the table");
                }
            }
        }
        for (Material one : materials) {
            boolean used = false;
            for (Kind kind : kinds) {
                used |= kind.material().equals(one.id());
            }
            if (!used) {
                bad.add(one.id() + " is a material nothing is built of");
            }
        }
        for (ApiaryRules.Housing housing : ApiaryRules.HOUSINGS) {
            String want = housing.slots() >= STEEL_FROM_SLOTS ? PICKAXE : AXE;
            Kind kind = kindIn(kinds, housing.id());
            Material material = kind == null ? null : materialIn(materials, kind.material());
            String got = material == null ? "nothing" : material.tool();
            if (!want.equals(got)) {
                bad.add(housing.id() + " has " + housing.slots() + " slot(s), so it is "
                        + (housing.slots() >= STEEL_FROM_SLOTS ? "steel" : "timber")
                        + " and wants a " + want + ", and it is mined by " + got);
            }
        }
        for (String block : catalogued()) {
            if (kindIn(kinds, block) == null) {
                bad.add(block + " is a block no kind covers, so nothing says what mines it");
            }
        }
        return List.copyOf(bad);
    }
}
