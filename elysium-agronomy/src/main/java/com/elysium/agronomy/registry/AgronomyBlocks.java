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

    /**
     * The rest of the housing ladder, walked from the rules.
     *
     * <p>Three more rungs, each holding more bees than the one below and
     * taking more upgrades. Registered from {@link ApiaryRules#HOUSINGS}
     * rather than listed here, so a rung added to the ladder is a rung that
     * exists the same day — and the count check at the end is what catches
     * the one somebody forgot.
     */
    public static final java.util.Map<String, DeferredBlock<Block>> APIARIES =
            registerApiaries();

    private static java.util.Map<String, DeferredBlock<Block>> registerApiaries() {
        java.util.Map<String, DeferredBlock<Block>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.agronomy.rules.ApiaryRules.Housing housing
                : com.elysium.agronomy.rules.ApiaryRules.HOUSINGS) {
            if (housing.first()) {
                continue;
            }
            made.put(housing.id(), BLOCKS.register(housing.id(),
                    () -> new HiveBlock(
                            housing.slots() >= 4 ? steel().strength(3.5F)
                                    : timber().strength(2.5F),
                            housing.id())));
        }
        int wanted = com.elysium.agronomy.rules.ApiaryRules.HOUSINGS.size() - 1;
        if (made.size() != wanted) {
            throw new IllegalStateException("the ladder has " + wanted
                    + " rung(s) above the plain hive and " + made.size()
                    + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** What a comb is actually for. */
    public static final DeferredBlock<Block> CENTRIFUGE =
            BLOCKS.register(com.elysium.agronomy.rules.ApiaryRules.CENTRIFUGE,
                    () -> new com.elysium.agronomy.block.CentrifugeBlock(
                            steel().strength(3.5F)));

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
