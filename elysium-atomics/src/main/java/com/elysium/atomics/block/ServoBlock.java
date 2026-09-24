package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.SpindleBlock;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Electricity into rotation, at a speed nothing below this age can reach.
 *
 * <h2>Why it exists, given that Age II already has a motor</h2>
 *
 * Because Age I's machine halls do not stop being useful — a saw is still the
 * cheapest planks in the game — and this is what makes going back to them
 * worth doing. It drives a line at {@code KineticRules.MAX_RPM}, which is
 * faster than anything in Age I can turn and faster than brass survives. A
 * player who reaches Age III and goes back to their first mill has to rebuild
 * it in steel before they can use what they have learned, which is a nicer
 * kind of obsolescence than the sort where the old building is simply deleted.
 *
 * <h2>The loop, again</h2>
 *
 * A servo turning an alternator turning a servo is a new loop, and Age II's
 * proof covers Age II's motor and says nothing whatever about this one. So the
 * servo's stall torque is not chosen for feel: it is pinned at the value that
 * gives exactly the same return as Age II's — see
 * {@code AtomicRules.servoRoundTrip()}, which the harness proves is under one
 * in closed form, sweeps over every gearing and count, and then checks again
 * with both ages' motors mixed on a single line, settled by Age I's own solver
 * rather than by any formula.
 */
public class ServoBlock extends SpindleBlock {

    public static final MapCodec<ServoBlock> CODEC = simpleCodec(ServoBlock::new);

    public ServoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.SERVO.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServoBlockEntity servo) {
            player.displayClientMessage(servo.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A machine on the grid, and a very fast source on the line. */
    public static class ServoBlockEntity extends SpindleBlockEntity {

        public ServoBlockEntity(BlockPos pos, BlockState state) {
            super(AtomicBlockEntities.SERVO.get(), pos, state);
        }

        /**
         * What it is made of, which is not what the age below is made of.
         *
         * A line's safe speed is the rating of the weakest thing on it, and
         * the base class answers steel because Age II is steel. A servo that
         * inherited that would cap its own line below the speed it drives it
         * at, and a line that shears itself is a bug with no symptom until it
         * costs somebody a machine hall.
         */
        @Override
        public KineticRules.Shaft material() {
            return AtomicRules.MADE_OF;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return AtomicRules.SERVO.rated();
        }

        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return AtomicRules.SERVO;
        }

        @Nullable
        @Override
        public KineticRules.Source source() {
            float fed = share() * AtomicRules.SERVO_RATED_FE;
            return fed <= 0.0F ? null : AtomicRules.servoAsSource(fed);
        }

        /** The grid changed, so what this drives the line with changed. */
        @Override
        protected void told() {
            unsettleLine();
        }

        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            float fed = share();
            if (fed <= 0.0F) {
                return Component.translatable("elysiumatomics.servo.dark");
            }
            return Component.translatable("elysiumatomics.servo.turning",
                    String.format("%.0f%%", fed * 100.0F),
                    String.format("%.0f", rpm()));
        }
    }
}
