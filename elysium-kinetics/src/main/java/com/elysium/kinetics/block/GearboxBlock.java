package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A gearbox: the only way power turns a corner.
 *
 * <p>Age I has two sizes of cog and therefore three ratios, all of them in the
 * same plane. Without this, a line can only go one way, and a machine hall
 * becomes a very long corridor. With it, and nothing else, the vocabulary is
 * still four things — shaft, two cogs, gearbox — which is small enough that a
 * player can work out what a build will do by looking at it.
 *
 * <p>It does not change speed. Every shaft on it turns the opposite way to the
 * box, so two on opposite faces turn together and a corner reverses, and it
 * costs one gear stage like anything else with teeth in it.
 */
public class GearboxBlock extends KineticBlock {

    public static final MapCodec<GearboxBlock> CODEC = simpleCodec(GearboxBlock::new);

    public GearboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // A gearbox has no axis of its own — it joins whatever runs into it —
        // but it keeps the property so that everything on a line answers the
        // same questions. The axis decides only how it is drawn.
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GearboxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.GEARBOX.get());
    }

    /** The box, which joins shafts whichever way they run. */
    public static class GearboxBlockEntity extends KineticBlockEntity {

        public GearboxBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.GEARBOX.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Override
        public boolean couplesAnyAxis() {
            return true;
        }
    }
}
