package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.block.AcceleratorBlock;
import com.elysium.agronomy.block.HiveBlock;
import com.elysium.agronomy.block.PotBlock;
import com.elysium.agronomy.block.RockGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block that grows something.
 *
 * Six: a pot, the accelerator that hurries it, a hive, and three rungs of rock
 * generator of which only the first needs no power.
 */
public final class AgronomyBlocks {

    private AgronomyBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAgronomy.MODID);

    private static BlockBehaviour.Properties clay() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.TERRACOTTA)
                .mapColor(MapColor.COLOR_BROWN)
                .sound(SoundType.STONE)
                .strength(1.5F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties timber() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(4.0F);
    }

    public static final DeferredBlock<Block> POT = BLOCKS.register("pot",
            () -> new PotBlock(clay()));

    public static final DeferredBlock<Block> ACCELERATOR = BLOCKS.register("accelerator",
            () -> new AcceleratorBlock(steel().strength(3.5F)));

    public static final DeferredBlock<Block> HIVE = BLOCKS.register("hive",
            () -> new HiveBlock(timber()));

    public static final DeferredBlock<Block> ROCK_GENERATOR =
            BLOCKS.register("rock_generator",
                    () -> new RockGeneratorBlock(BlockBehaviour.Properties
                            .ofFullCopy(Blocks.STONE).strength(3.0F), 0));

    public static final DeferredBlock<Block> DRIVEN_ROCK_GENERATOR =
            BLOCKS.register("driven_rock_generator",
                    () -> new RockGeneratorBlock(steel().strength(3.5F), 1));

    /**
     * One generator per vein.
     *
     * <p>Seven blocks from a seven-row table, because the mod this replaces
     * writes out seven block classes that differ in one field. The guard below
     * is the same one every registry in this module carries: six of seven is a
     * module that works until somebody builds the seventh.
     */
    public static final java.util.Map<String, DeferredBlock<Block>> VEINS =
            registerVeins();

    private static java.util.Map<String, DeferredBlock<Block>> registerVeins() {
        java.util.Map<String, DeferredBlock<Block>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.agronomy.rules.AgronomyRules.Vein vein
                : com.elysium.agronomy.rules.AgronomyRules.VEINS) {
            made.put(vein.material(), BLOCKS.register(vein.material() + "_generator",
                    () -> new RockGeneratorBlock(steel(), vein)));
        }
        if (made.size() != com.elysium.agronomy.rules.AgronomyRules.VEINS.size()) {
            throw new IllegalStateException("there are "
                    + com.elysium.agronomy.rules.AgronomyRules.VEINS.size()
                    + " veins and " + made.size() + " generator(s) were registered");
        }
        return java.util.Map.copyOf(made);
    }

    public static final DeferredBlock<Block> INDUSTRIAL_ROCK_GENERATOR =
            BLOCKS.register("industrial_rock_generator",
                    () -> new RockGeneratorBlock(steel().strength(4.5F), 2));
}
