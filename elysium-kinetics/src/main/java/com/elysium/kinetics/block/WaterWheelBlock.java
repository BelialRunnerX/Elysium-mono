package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * A water wheel. Slow, strong, and free forever.
 *
 * <h2>It has to be in moving water</h2>
 *
 * Not merely wet. A wheel standing in a pond is a wheel nothing is pushing,
 * and a mod where you flood a hole and get unlimited power is a mod with one
 * building in it. Flowing water only, and at least
 * {@link #LEAST_FLOW} blocks of it around the rim, so the thing a player
 * builds is a mill race.
 *
 * <p>Deliberately one block rather than Create's multiblock. A 1x1 wheel that
 * needs a stream around it is a shape a player can put in a river the first
 * time they try; the ceiling that a multiblock exists to provide is here
 * instead in the water itself, because a rim in a stream is a real constraint
 * on where you can build.
 */
public class WaterWheelBlock extends KineticBlock {

    public static final MapCodec<WaterWheelBlock> CODEC = simpleCodec(WaterWheelBlock::new);

    /** How many flowing-water neighbours the rim needs before the wheel turns. */
    public static final int LEAST_FLOW = 2;

    public WaterWheelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterWheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.WATER_WHEEL.get());
    }

    /**
     * How many of the six neighbours are flowing water.
     *
     * The faces along the axis are skipped: those are the ends of the pin the
     * wheel turns on, not its rim, and water there is water the wheel is not
     * dipping into.
     */
    public static int flowAround(Level level, BlockPos pos, Direction.Axis axis) {
        int count = 0;
        for (Direction face : Direction.values()) {
            if (face.getAxis() == axis) {
                continue;
            }
            FluidState fluid = level.getFluidState(pos.relative(face));
            if (fluid.getType() == Fluids.FLOWING_WATER) {
                count++;
            }
        }
        return count;
    }

    /** The wheel, which is a source exactly when the water is moving. */
    public static class WaterWheelBlockEntity extends KineticBlockEntity {

        /** Rechecked on a timer rather than every tick; water changes slowly. */
        private static final int LOOK_EVERY = 20;

        private boolean wet;
        private int sinceLooked;

        public WaterWheelBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.WATER_WHEEL.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Nullable
        @Override
        public KineticRules.Source source() {
            return wet ? KineticRules.WATER_WHEEL : null;
        }

        @Override
        protected String hum() {
            return "pump";
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++sinceLooked < LOOK_EVERY) {
                return;
            }
            sinceLooked = 0;
            boolean nowWet = flowAround(level, pos, axis()) >= LEAST_FLOW;
            if (nowWet != wet) {
                wet = nowWet;
                unsettle();
            }
        }
    }
}
