package com.elysium.kinetics.registry;

import com.elysium.kinetics.ElysiumKinetics;
import com.elysium.kinetics.block.CogwheelBlock;
import com.elysium.kinetics.block.GearboxBlock;
import com.elysium.kinetics.block.HandCrankBlock;
import com.elysium.kinetics.block.MillstoneBlock;
import com.elysium.kinetics.block.MixerBlock;
import com.elysium.kinetics.block.PressBlock;
import com.elysium.kinetics.block.RefineryBlock;
import com.elysium.kinetics.block.SawBlock;
import com.elysium.kinetics.block.ShaftBlock;
import com.elysium.kinetics.block.WaterWheelBlock;
import com.elysium.kinetics.block.WindmillBearingBlock;
import com.elysium.kinetics.rules.KineticRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block Age I is made of.
 *
 * Six, which is the whole first rung: something to carry torque, two things to
 * change its speed, two things to make it, and one thing to spend it on.
 */
public final class KineticBlocks {

    private KineticBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumKinetics.MODID);

    private static BlockBehaviour.Properties timber() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.5F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties brass() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                .mapColor(MapColor.GOLD)
                .sound(SoundType.COPPER)
                .strength(2.5F)
                .noOcclusion();
    }

    public static final DeferredBlock<Block> TIMBER_SHAFT = BLOCKS.register("timber_shaft",
            () -> new ShaftBlock(timber(), KineticRules.Shaft.TIMBER));

    public static final DeferredBlock<Block> BRASS_SHAFT = BLOCKS.register("brass_shaft",
            () -> new ShaftBlock(brass(), KineticRules.Shaft.BRASS));

    public static final DeferredBlock<Block> SMALL_COGWHEEL = BLOCKS.register("small_cogwheel",
            () -> new CogwheelBlock(timber(), KineticRules.SMALL_TEETH));

    public static final DeferredBlock<Block> LARGE_COGWHEEL = BLOCKS.register("large_cogwheel",
            () -> new CogwheelBlock(timber(), KineticRules.LARGE_TEETH));

    public static final DeferredBlock<Block> HAND_CRANK = BLOCKS.register("hand_crank",
            () -> new HandCrankBlock(timber()));

    public static final DeferredBlock<Block> WATER_WHEEL = BLOCKS.register("water_wheel",
            () -> new WaterWheelBlock(timber().strength(2.0F)));

    public static final DeferredBlock<Block> GEARBOX = BLOCKS.register("gearbox",
            () -> new GearboxBlock(timber().strength(2.0F)));

    public static final DeferredBlock<Block> WINDMILL_BEARING =
            BLOCKS.register("windmill_bearing",
                    () -> new WindmillBearingBlock(brass().strength(2.5F)));

    /** Not kinetic: a sail is cloth on a frame, and the bearing counts them. */
    public static final DeferredBlock<Block> SAIL = BLOCKS.register("sail",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)
                    .strength(0.8F)));

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(3.0F).noOcclusion();
    }

    public static final DeferredBlock<Block> MILLSTONE = BLOCKS.register("millstone",
            () -> new MillstoneBlock(stone()));

    public static final DeferredBlock<Block> PRESS = BLOCKS.register("press",
            () -> new PressBlock(brass().strength(3.5F)));

    public static final DeferredBlock<Block> SAW = BLOCKS.register("saw",
            () -> new SawBlock(timber().strength(2.5F)));

    /**
     * The engines, walked out of the rules.
     *
     * <p>Three of them, and a refinery that turns one fuel into the next.
     * Registered from {@link com.elysium.kinetics.rules.EngineRules#ENGINES}
     * rather than listed here, so an engine added to the ladder is a block the
     * same day, and the count check at the end catches the one somebody
     * forgot.
     */
    public static final java.util.Map<String, DeferredBlock<Block>> ENGINES =
            registerEngines();

    private static java.util.Map<String, DeferredBlock<Block>> registerEngines() {
        java.util.Map<String, DeferredBlock<Block>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.kinetics.rules.EngineRules.Engine engine
                : com.elysium.kinetics.rules.EngineRules.ENGINES) {
            made.put(engine.id(), BLOCKS.register(engine.id(),
                    () -> new com.elysium.kinetics.block.EngineBlock(
                            brass(), engine.id())));
        }
        if (made.size()
                != com.elysium.kinetics.rules.EngineRules.ENGINES.size()) {
            throw new IllegalStateException("the rules have "
                    + com.elysium.kinetics.rules.EngineRules.ENGINES.size()
                    + " engine(s) and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** What turns one fuel into the next. */
    public static final DeferredBlock<Block> REFINERY = BLOCKS.register(
            com.elysium.kinetics.rules.EngineRules.REFINERY,
            () -> new RefineryBlock(brass()));

    public static final DeferredBlock<Block> MIXER = BLOCKS.register("mixer",
            () -> new MixerBlock(brass().strength(3.5F)));

    /** The one place this age makes electricity, and it is bad at it. */
    public static final DeferredBlock<Block> DYNAMO = BLOCKS.register(
            com.elysium.kinetics.block.DynamoBlock.NAME,
            () -> new com.elysium.kinetics.block.DynamoBlock(brass()));
}
