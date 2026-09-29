package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.machine.MachineBlock;
import com.elysium.space.block.PaleVinesBlock;
import com.elysium.space.block.PaleVinesPlantBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.MangroveLeavesBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.Optional;

/**
 * Every block the space module adds.
 *
 * Ported from Stellaris with new ids: moon sand and stone become pale regolith
 * and pale stone; mars sand and stone become frost regolith and frost stone; the
 * venus set becomes cinder sand, stone and sandstone; moon vines become pale
 * vines. Emgrove is new: the mangrove set with its own tree.
 */
public final class SpaceBlocks {

    private SpaceBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ElysiumSpace.MODID);

    private static BlockBehaviour.Properties copy(Block vanilla) {
        return BlockBehaviour.Properties.ofFullCopy(vanilla);
    }

    // --- the ported worldgen blocks --------------------------------------
    public static final DeferredBlock<Block> PALE_REGOLITH = BLOCKS.register("pale_regolith",
            () -> new ColoredFallingBlock(new ColorRGBA(0xB4B4B4), copy(Blocks.SAND).mapColor(MapColor.COLOR_LIGHT_GRAY)));
    public static final DeferredBlock<Block> PALE_STONE = BLOCKS.register("pale_stone",
            () -> new Block(copy(Blocks.STONE).mapColor(MapColor.COLOR_LIGHT_GRAY)));
    public static final DeferredBlock<Block> PALE_VINES = BLOCKS.register("pale_vines",
            () -> new PaleVinesBlock(copy(Blocks.CAVE_VINES).lightLevel(CaveVines.emission(14))));
    public static final DeferredBlock<Block> PALE_VINES_PLANT = BLOCKS.register("pale_vines_plant",
            () -> new PaleVinesPlantBlock(copy(Blocks.CAVE_VINES_PLANT).lightLevel(CaveVines.emission(14))));
    public static final DeferredBlock<Block> FROST_REGOLITH = BLOCKS.register("frost_regolith",
            () -> new ColoredFallingBlock(new ColorRGBA(0xA9542B), copy(Blocks.RED_SAND)));
    public static final DeferredBlock<Block> FROST_STONE = BLOCKS.register("frost_stone",
            () -> new Block(copy(Blocks.STONE).mapColor(MapColor.TERRACOTTA_ORANGE)));
    public static final DeferredBlock<Block> CINDER_SAND = BLOCKS.register("cinder_sand",
            () -> new ColoredFallingBlock(new ColorRGBA(0xC9A15C), copy(Blocks.SAND)));
    public static final DeferredBlock<Block> CINDER_STONE = BLOCKS.register("cinder_stone",
            () -> new Block(copy(Blocks.STONE).mapColor(MapColor.TERRACOTTA_YELLOW)));
    public static final DeferredBlock<Block> CINDER_SANDSTONE = BLOCKS.register("cinder_sandstone",
            () -> new Block(copy(Blocks.SANDSTONE)));

    // --- emgrove -----------------------------------------------------------
    public static final ResourceKey<ConfiguredFeature<?, ?>> EMGROVE_TREE = feature("emgrove");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TALL_EMGROVE_TREE = feature("tall_emgrove");

    /** Grows like a mangrove: 85% the plain tree, otherwise the tall one. */
    public static final TreeGrower EMGROVE_GROWER = new TreeGrower("elysiumspace_emgrove", 0.85F,
            Optional.empty(), Optional.empty(), Optional.of(EMGROVE_TREE), Optional.of(TALL_EMGROVE_TREE),
            Optional.empty(), Optional.empty());

    private static ResourceKey<ConfiguredFeature<?, ?>> feature(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, path));
    }

    private static BlockBehaviour.Properties wood(Block vanilla) {
        return copy(vanilla).mapColor(MapColor.COLOR_GREEN);
    }

    public static final DeferredBlock<Block> EMGROVE_LOG = BLOCKS.register("emgrove_log",
            () -> new RotatedPillarBlock(wood(Blocks.MANGROVE_LOG)));
    public static final DeferredBlock<Block> EMGROVE_WOOD = BLOCKS.register("emgrove_wood",
            () -> new RotatedPillarBlock(wood(Blocks.MANGROVE_WOOD)));
    public static final DeferredBlock<Block> STRIPPED_EMGROVE_LOG = BLOCKS.register("stripped_emgrove_log",
            () -> new RotatedPillarBlock(wood(Blocks.STRIPPED_MANGROVE_LOG)));
    public static final DeferredBlock<Block> STRIPPED_EMGROVE_WOOD = BLOCKS.register("stripped_emgrove_wood",
            () -> new RotatedPillarBlock(wood(Blocks.STRIPPED_MANGROVE_WOOD)));
    public static final DeferredBlock<Block> EMGROVE_PLANKS = BLOCKS.register("emgrove_planks",
            () -> new Block(wood(Blocks.MANGROVE_PLANKS)));
    public static final DeferredBlock<Block> EMGROVE_LEAVES = BLOCKS.register("emgrove_leaves",
            () -> new MangroveLeavesBlock(copy(Blocks.MANGROVE_LEAVES)));
    public static final DeferredBlock<Block> EMGROVE_ROOTS = BLOCKS.register("emgrove_roots",
            () -> new MangroveRootsBlock(copy(Blocks.MANGROVE_ROOTS)));
    public static final DeferredBlock<Block> MUDDY_EMGROVE_ROOTS = BLOCKS.register("muddy_emgrove_roots",
            () -> new RotatedPillarBlock(copy(Blocks.MUDDY_MANGROVE_ROOTS)));
    public static final DeferredBlock<Block> EMGROVE_PROPAGULE = BLOCKS.register("emgrove_propagule",
            () -> new MangrovePropaguleBlock(EMGROVE_GROWER, copy(Blocks.MANGROVE_PROPAGULE)));

    // --- machines ----------------------------------------------------------
    /** Rockets are placed on this, and orbit arrivals stand on it. */
    public static final DeferredBlock<Block> LAUNCH_PAD = BLOCKS.register("launch_pad",
            () -> new Block(copy(Blocks.IRON_BLOCK)));

    public static final DeferredBlock<Block> OXYGEN_DISTRIBUTOR = BLOCKS.register("oxygen_distributor",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.OXYGEN_DISTRIBUTOR,
                    com.elysium.space.block.OxygenDistributorBlockEntity::new));
    public static final DeferredBlock<Block> COAL_GENERATOR = BLOCKS.register("coal_generator",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.COAL_GENERATOR,
                    com.elysium.space.machine.CoalGeneratorBlockEntity::new));
    public static final DeferredBlock<Block> SOLAR_PANEL = BLOCKS.register("solar_panel",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.SOLAR_PANEL,
                    com.elysium.space.machine.SolarPanelBlockEntity::new));
    public static final DeferredBlock<Block> FUEL_REFINERY = BLOCKS.register("fuel_refinery",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.FUEL_REFINERY,
                    com.elysium.space.machine.FuelRefineryBlockEntity::new));
    public static final DeferredBlock<Block> WATER_SEPARATOR = BLOCKS.register("water_separator",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.WATER_SEPARATOR,
                    com.elysium.space.machine.WaterSeparatorBlockEntity::new));

    public static final DeferredBlock<Block> PUMPJACK = BLOCKS.register("pumpjack",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.PUMPJACK,
                    com.elysium.space.machine.PumpjackBlockEntity::new));
    public static final DeferredBlock<Block> DIESEL_GENERATOR = BLOCKS.register("diesel_generator",
            () -> new MachineBlock(copy(Blocks.IRON_BLOCK), SpaceBlockEntities.DIESEL_GENERATOR,
                    com.elysium.space.machine.DieselGeneratorBlockEntity::new));
    public static final DeferredBlock<Block> FLUID_TANK = BLOCKS.register("fluid_tank",
            () -> new MachineBlock(copy(Blocks.GLASS).strength(3.0F), SpaceBlockEntities.FLUID_TANK,
                    com.elysium.space.machine.FluidTankBlockEntity::new));
    public static final DeferredBlock<Block> CABLE = BLOCKS.register("cable",
            () -> new com.elysium.space.machine.ConduitBlock(copy(Blocks.IRON_BARS).noOcclusion(),
                    com.elysium.space.machine.ConduitBlock.Carries.ENERGY, SpaceBlockEntities.CABLE,
                    com.elysium.space.machine.ConduitBlockEntity.Cable::new));
    public static final DeferredBlock<Block> PIPE = BLOCKS.register("pipe",
            () -> new com.elysium.space.machine.ConduitBlock(copy(Blocks.IRON_BARS).noOcclusion(),
                    com.elysium.space.machine.ConduitBlock.Carries.FLUID, SpaceBlockEntities.PIPE,
                    com.elysium.space.machine.ConduitBlockEntity.Pipe::new));

    // --- the waygate ------------------------------------------------------
    //
    // Two blocks and nothing else. The frame is the cost of the gate and the
    // anchor is the gate; there is deliberately no portal block, no fuel tank
    // and no dialling device, because every one of those would be a thing a
    // player has to maintain and the whole claim of the waygate is that it is
    // furniture once it is built.

    /** The ring. Fifteen of them, and they are the whole price. */
    public static final DeferredBlock<Block> WAYGATE_FRAME = BLOCKS.register("waygate_frame",
            () -> new Block(copy(Blocks.OBSIDIAN).lightLevel(state -> 3)
                    .mapColor(MapColor.COLOR_CYAN)));

    /** The block you talk to, in the bottom-centre of the ring. */
    public static final DeferredBlock<Block> WAYGATE_ANCHOR = BLOCKS.register("waygate_anchor",
            () -> new com.elysium.space.gate.WaygateAnchorBlock(
                    copy(Blocks.OBSIDIAN).lightLevel(state -> 9)
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)));

    // --- freight ----------------------------------------------------------
    //
    // The logistics half of the rockets. One block: a chest that can be aimed
    // at another world, which is the whole of the system a player touches.

    /** Load it, fuel it, point it at another terminal. */
    public static final DeferredBlock<Block> FREIGHT_TERMINAL = BLOCKS.register("freight_terminal",
            () -> new com.elysium.space.freight.FreightTerminalBlock(
                    copy(Blocks.IRON_BLOCK).lightLevel(state -> 4)
                            .mapColor(MapColor.COLOR_ORANGE)));

    // --- ores (Stellaris's, with Elysium names) and their metals ---------------
    public static final DeferredBlock<Block> LUNITE_ORE = ore("lunite_ore", () -> PALE_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> PALE_STEEL_ORE = ore("pale_steel_ore", () -> PALE_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> PALE_IRON_ORE = ore("pale_iron_ore", () -> PALE_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> PALE_RIME_SHARD_ORE = ore("pale_rime_shard_ore", () -> PALE_STONE.get(), 1, 3);
    public static final DeferredBlock<Block> FROSTITE_ORE = ore("frostite_ore", () -> FROST_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> CRYONITE_ORE = ore("cryonite_ore", () -> FROST_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> FROST_IRON_ORE = ore("frost_iron_ore", () -> FROST_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> FROST_DIAMOND_ORE = ore("frost_diamond_ore", () -> FROST_STONE.get(), 3, 7);
    public static final DeferredBlock<Block> FROST_RIME_SHARD_ORE = ore("frost_rime_shard_ore", () -> FROST_STONE.get(), 1, 3);
    public static final DeferredBlock<Block> CINDRIUM_ORE = ore("cindrium_ore", () -> CINDER_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> CINDER_COAL_ORE = ore("cinder_coal_ore", () -> CINDER_STONE.get(), 0, 2);
    public static final DeferredBlock<Block> CINDER_GOLD_ORE = ore("cinder_gold_ore", () -> CINDER_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> CINDER_DIAMOND_ORE = ore("cinder_diamond_ore", () -> CINDER_STONE.get(), 3, 7);
    public static final DeferredBlock<Block> STEEL_ORE = ore("steel_ore", () -> Blocks.STONE, 0, 0);
    public static final DeferredBlock<Block> DEEPSLATE_STEEL_ORE = ore("deepslate_steel_ore", () -> Blocks.DEEPSLATE, 0, 0);
    public static final DeferredBlock<Block> NEPTUNIUM_ORE = ore("neptunium_ore", () -> CINDER_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> PLUTONIUM_ORE = ore("plutonium_ore", () -> FROST_STONE.get(), 0, 0);
    public static final DeferredBlock<Block> NEPTUNIUM_BLOCK = metal("neptunium_block");
    public static final DeferredBlock<Block> RAW_NEPTUNIUM_BLOCK = metal("raw_neptunium_block");
    public static final DeferredBlock<Block> PLUTONIUM_BLOCK = metal("plutonium_block");
    public static final DeferredBlock<Block> RAW_PLUTONIUM_BLOCK = metal("raw_plutonium_block");
    public static final DeferredBlock<Block> HEAVY_METAL_BLOCK = metal("heavy_metal_block");
    public static final DeferredBlock<Block> LUNITE_BLOCK = metal("lunite_block");
    public static final DeferredBlock<Block> RAW_LUNITE_BLOCK = metal("raw_lunite_block");
    public static final DeferredBlock<Block> FROSTITE_BLOCK = metal("frostite_block");
    public static final DeferredBlock<Block> RAW_FROSTITE_BLOCK = metal("raw_frostite_block");
    public static final DeferredBlock<Block> CRYONITE_BLOCK = metal("cryonite_block");
    public static final DeferredBlock<Block> RAW_CRYONITE_BLOCK = metal("raw_cryonite_block");
    public static final DeferredBlock<Block> CINDRIUM_BLOCK = metal("cindrium_block");
    public static final DeferredBlock<Block> RAW_CINDRIUM_BLOCK = metal("raw_cindrium_block");
    public static final DeferredBlock<Block> STEEL_BLOCK = metal("steel_block");
    public static final DeferredBlock<Block> RAW_STEEL_BLOCK = metal("raw_steel_block");

    /** An ore in a world's stone; gem and coal ores give experience like vanilla's. */
    private static DeferredBlock<Block> ore(String name, java.util.function.Supplier<Block> host, int xpMin, int xpMax) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.DropExperienceBlock(
                net.minecraft.util.valueproviders.UniformInt.of(xpMin, xpMax),
                copy(host.get()).strength(host.get() == Blocks.DEEPSLATE ? 4.5F : 3.0F, 3.0F)
                        .requiresCorrectToolForDrops()));
    }

    private static DeferredBlock<Block> metal(String name) {
        return BLOCKS.register(name, () -> new Block(copy(Blocks.IRON_BLOCK)));
    }

    // --- building blocks the Stellaris structures are made of -----------------
    public static final DeferredBlock<Block> PALE_COBBLESTONE = deco("pale_cobblestone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_PALE_STONE = deco("polished_pale_stone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_STONE_BRICKS = deco("pale_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CHISELED_PALE_STONE_BRICKS = deco("chiseled_pale_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_STONE_STAIRS = stairs("pale_stone_stairs", PALE_STONE);
    public static final DeferredBlock<Block> PALE_STONE_SLAB = slab("pale_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_PALE_STONE_STAIRS = stairs("polished_pale_stone_stairs", POLISHED_PALE_STONE);
    public static final DeferredBlock<Block> POLISHED_PALE_STONE_SLAB = slab("polished_pale_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_STONE_BRICK_STAIRS = stairs("pale_stone_brick_stairs", PALE_STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_STONE_BRICK_SLAB = slab("pale_stone_brick_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_STONE_PILLAR = pillar("pale_stone_pillar", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_COBBLESTONE = deco("frost_cobblestone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_FROST_STONE = deco("polished_frost_stone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_STONE_BRICKS = deco("frost_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CHISELED_FROST_STONE_BRICKS = deco("chiseled_frost_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_STONE_STAIRS = stairs("frost_stone_stairs", FROST_STONE);
    public static final DeferredBlock<Block> FROST_STONE_SLAB = slab("frost_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_FROST_STONE_STAIRS = stairs("polished_frost_stone_stairs", POLISHED_FROST_STONE);
    public static final DeferredBlock<Block> POLISHED_FROST_STONE_SLAB = slab("polished_frost_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_STONE_BRICK_STAIRS = stairs("frost_stone_brick_stairs", FROST_STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_STONE_BRICK_SLAB = slab("frost_stone_brick_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> FROST_STONE_PILLAR = pillar("frost_stone_pillar", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_COBBLESTONE = deco("cinder_cobblestone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_CINDER_STONE = deco("polished_cinder_stone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_STONE_BRICKS = deco("cinder_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CHISELED_CINDER_STONE_BRICKS = deco("chiseled_cinder_stone_bricks", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_STONE_STAIRS = stairs("cinder_stone_stairs", CINDER_STONE);
    public static final DeferredBlock<Block> CINDER_STONE_SLAB = slab("cinder_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> POLISHED_CINDER_STONE_STAIRS = stairs("polished_cinder_stone_stairs", POLISHED_CINDER_STONE);
    public static final DeferredBlock<Block> POLISHED_CINDER_STONE_SLAB = slab("polished_cinder_stone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_STONE_BRICK_STAIRS = stairs("cinder_stone_brick_stairs", CINDER_STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_STONE_BRICK_SLAB = slab("cinder_stone_brick_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> CINDER_STONE_PILLAR = pillar("cinder_stone_pillar", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> PALE_DEEPSLATE = deco("pale_deepslate", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> SMOOTH_CINDER_SANDSTONE = deco("smooth_cinder_sandstone", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> SMOOTH_CINDER_SANDSTONE_STAIRS = stairs("smooth_cinder_sandstone_stairs", SMOOTH_CINDER_SANDSTONE);
    public static final DeferredBlock<Block> SMOOTH_CINDER_SANDSTONE_SLAB = slab("smooth_cinder_sandstone_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> IRON_PLATING = deco("iron_plating", Blocks.IRON_BLOCK);
    public static final DeferredBlock<Block> IRON_PLATING_STAIRS = stairs("iron_plating_stairs", IRON_PLATING);
    public static final DeferredBlock<Block> IRON_PLATING_SLAB = slab("iron_plating_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> IRON_PILLAR = pillar("iron_pillar", Blocks.IRON_BLOCK);
    public static final DeferredBlock<Block> STEEL_PLATING = deco("steel_plating", Blocks.IRON_BLOCK);
    public static final DeferredBlock<Block> STEEL_PLATING_STAIRS = stairs("steel_plating_stairs", STEEL_PLATING);
    public static final DeferredBlock<Block> STEEL_PLATING_SLAB = slab("steel_plating_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> STEEL_PILLAR = pillar("steel_pillar", Blocks.IRON_BLOCK);
    public static final DeferredBlock<Block> LUNITE_PLATING = deco("lunite_plating", Blocks.IRON_BLOCK);
    public static final DeferredBlock<Block> LUNITE_PLATING_STAIRS = stairs("lunite_plating_stairs", LUNITE_PLATING);
    public static final DeferredBlock<Block> LUNITE_PLATING_SLAB = slab("lunite_plating_slab", Blocks.STONE_BRICKS);
    public static final DeferredBlock<Block> LUNITE_PILLAR = pillar("lunite_pillar", Blocks.IRON_BLOCK);

    private static DeferredBlock<Block> deco(String name, Block like) {
        return BLOCKS.register(name, () -> new Block(copy(like)));
    }

    private static DeferredBlock<Block> stairs(String name, DeferredBlock<Block> base) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.StairBlock(
                base.get().defaultBlockState(), copy(base.get())));
    }

    private static DeferredBlock<Block> slab(String name, Block like) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.SlabBlock(copy(like)));
    }

    private static DeferredBlock<Block> pillar(String name, Block like) {
        return BLOCKS.register(name, () -> new RotatedPillarBlock(copy(like)));
    }

    /** Log to stripped log, for the axe. */
    public static Map<Block, Block> strippable() {
        return Map.of(EMGROVE_LOG.get(), STRIPPED_EMGROVE_LOG.get(),
                EMGROVE_WOOD.get(), STRIPPED_EMGROVE_WOOD.get());
    }
}
