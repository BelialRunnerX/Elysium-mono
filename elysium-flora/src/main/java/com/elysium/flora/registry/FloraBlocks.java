package com.elysium.flora.registry;

import com.elysium.flora.ElysiumFlora;
import com.elysium.flora.block.FibreCropBlock;
import com.elysium.flora.block.WildPlantBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.Optional;

/**
 * Every block this module adds: two crops, their wild forms, one tree's worth
 * of wood, and two things you can stack.
 */
public final class FloraBlocks {

    private FloraBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumFlora.MODID);

    private static BlockBehaviour.Properties copy(Block vanilla) {
        return BlockBehaviour.Properties.ofFullCopy(vanilla);
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> feature(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(ElysiumFlora.MODID, name));
    }

    // --- the crops ---------------------------------------------------------

    /**
     * No loot table on any of the four, and that is deliberate rather than an
     * omission: what a plant gives depends on who broke it and how ripe it was,
     * which a table cannot express, so {@code FibreCropBlock.getDrops} answers
     * instead. Declaring the absence here stops the game looking for a file
     * nobody is going to write.
     */
    public static final DeferredBlock<Block> LOOMFLAX = BLOCKS.register("loomflax",
            () -> new FibreCropBlock(copy(Blocks.WHEAT).noLootTable(),
                    () -> FloraItems.LOOMFLAX_SEEDS.get(),
                    () -> FloraItems.LOOMFLAX_FIBRE.get()));

    public static final DeferredBlock<Block> WILD_LOOMFLAX = BLOCKS.register("wild_loomflax",
            () -> new WildPlantBlock(copy(Blocks.TALL_GRASS).noLootTable(),
                    () -> FloraItems.LOOMFLAX_SEEDS.get()));

    public static final DeferredBlock<Block> DOWNBLOOM = BLOCKS.register("downbloom",
            () -> new FibreCropBlock(copy(Blocks.WHEAT).noLootTable(),
                    () -> FloraItems.DOWNBLOOM_SEEDS.get(),
                    () -> FloraItems.DOWNBLOOM_TUFT.get()));

    public static final DeferredBlock<Block> WILD_DOWNBLOOM = BLOCKS.register("wild_downbloom",
            () -> new WildPlantBlock(copy(Blocks.TALL_GRASS).noLootTable(),
                    () -> FloraItems.DOWNBLOOM_SEEDS.get()));

    // --- emberwood ---------------------------------------------------------

    public static final ResourceKey<ConfiguredFeature<?, ?>> EMBERWOOD_TREE = feature("emberwood");

    /**
     * No flowered or mega variant: one tree, one shape. A second shape is a
     * second set of files and a difference a player cannot act on.
     */
    public static final TreeGrower EMBERWOOD_GROWER = new TreeGrower("elysiumflora_emberwood",
            Optional.empty(), Optional.of(EMBERWOOD_TREE), Optional.empty());

    public static final DeferredBlock<Block> EMBERWOOD_LOG = BLOCKS.register("emberwood_log",
            () -> new RotatedPillarBlock(copy(Blocks.CRIMSON_STEM).mapColor(MapColor.COLOR_RED)));

    public static final DeferredBlock<Block> EMBERWOOD_WOOD = BLOCKS.register("emberwood_wood",
            () -> new RotatedPillarBlock(copy(Blocks.CRIMSON_HYPHAE)));

    public static final DeferredBlock<Block> STRIPPED_EMBERWOOD_LOG =
            BLOCKS.register("stripped_emberwood_log",
                    () -> new RotatedPillarBlock(copy(Blocks.STRIPPED_CRIMSON_STEM)));

    public static final DeferredBlock<Block> STRIPPED_EMBERWOOD_WOOD =
            BLOCKS.register("stripped_emberwood_wood",
                    () -> new RotatedPillarBlock(copy(Blocks.STRIPPED_CRIMSON_HYPHAE)));

    public static final DeferredBlock<Block> EMBERWOOD_PLANKS = BLOCKS.register("emberwood_planks",
            () -> new Block(copy(Blocks.CRIMSON_PLANKS)));

    public static final DeferredBlock<Block> EMBERWOOD_LEAVES = BLOCKS.register("emberwood_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(
                    copy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> EMBERWOOD_SAPLING = BLOCKS.register("emberwood_sapling",
            () -> new SaplingBlock(EMBERWOOD_GROWER, copy(Blocks.CRIMSON_FUNGUS)));

    public static final DeferredBlock<Block> EMBERWOOD_SLAB = BLOCKS.register("emberwood_slab",
            () -> new SlabBlock(copy(Blocks.CRIMSON_SLAB)));

    public static final DeferredBlock<Block> EMBERWOOD_STAIRS = BLOCKS.register("emberwood_stairs",
            () -> new StairBlock(EMBERWOOD_PLANKS.get().defaultBlockState(),
                    copy(Blocks.CRIMSON_STAIRS)));

    public static final DeferredBlock<Block> EMBERWOOD_FENCE = BLOCKS.register("emberwood_fence",
            () -> new FenceBlock(copy(Blocks.CRIMSON_FENCE)));

    public static final DeferredBlock<Block> EMBERWOOD_FENCE_GATE =
            BLOCKS.register("emberwood_fence_gate",
                    () -> new FenceGateBlock(WoodType.CRIMSON, copy(Blocks.CRIMSON_FENCE_GATE)));

    public static final DeferredBlock<Block> EMBERWOOD_DOOR = BLOCKS.register("emberwood_door",
            () -> new DoorBlock(BlockSetType.CRIMSON, copy(Blocks.CRIMSON_DOOR)));

    public static final DeferredBlock<Block> EMBERWOOD_TRAPDOOR =
            BLOCKS.register("emberwood_trapdoor",
                    () -> new TrapDoorBlock(BlockSetType.CRIMSON, copy(Blocks.CRIMSON_TRAPDOOR)));

    public static final DeferredBlock<Block> EMBERWOOD_BUTTON = BLOCKS.register("emberwood_button",
            () -> new ButtonBlock(BlockSetType.CRIMSON, 30, copy(Blocks.CRIMSON_BUTTON)));

    public static final DeferredBlock<Block> EMBERWOOD_PRESSURE_PLATE =
            BLOCKS.register("emberwood_pressure_plate",
                    () -> new PressurePlateBlock(BlockSetType.CRIMSON,
                            copy(Blocks.CRIMSON_PRESSURE_PLATE)));

    // --- things you can stack ---------------------------------------------

    public static final DeferredBlock<Block> DOWNBLOOM_BALE = BLOCKS.register("downbloom_bale",
            () -> new RotatedPillarBlock(copy(Blocks.HAY_BLOCK).mapColor(MapColor.TERRACOTTA_WHITE)));

    public static final DeferredBlock<Block> EMBERWOOD_CHARCOAL_BLOCK =
            BLOCKS.register("emberwood_charcoal_block",
                    () -> new Block(copy(Blocks.COAL_BLOCK)));

    /** What an axe turns into what, so stripping works the way it does elsewhere. */
    public static Map<Block, Block> stripping() {
        return Map.of(EMBERWOOD_LOG.get(), STRIPPED_EMBERWOOD_LOG.get(),
                EMBERWOOD_WOOD.get(), STRIPPED_EMBERWOOD_WOOD.get());
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
