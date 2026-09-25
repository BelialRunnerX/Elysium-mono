package com.elysium.kinetics.block;

import com.elysium.kinetics.recipe.KineticRecipe;
import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.EngineRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A refinery: the one machine whose product is what drives the machines.
 *
 * <p>It is a {@link MachineBlock} like the other four and differs from them
 * in exactly the three ways they differ from each other — how much torque it
 * draws, how fast it must turn, and which recipes it reads. That is the
 * point of that class existing.
 *
 * <p>What makes it worth its own file at all is what it is <em>for</em>: it
 * turns one fuel into the next one up, which is the only reason anybody
 * builds the second and third engines. A refinery driven by the engine that
 * burns what it makes is the first loop in the age that a player closes on
 * purpose.
 */
public class RefineryBlock extends MachineBlock {

    public static final MapCodec<RefineryBlock> CODEC = simpleCodec(RefineryBlock::new);

    public RefineryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public KineticRules.Load load() {
        return EngineRules.REFINERY_LOAD;
    }

    @Override
    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.REFINING;
    }

    @Override
    public String saying() {
        return "elysiumkinetics.refinery";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(KineticBlockEntities.REFINERY.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.REFINERY.get());
    }
}
