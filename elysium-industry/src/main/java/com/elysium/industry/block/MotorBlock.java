package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
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
 * Electricity in, rotation out. The alternator run backwards.
 *
 * <h2>What it is for</h2>
 *
 * Age I's machines do not stop being useful when a player reaches Age II — a
 * saw is still the only thing that makes planks the cheap way — and a water
 * wheel is still a water wheel. The motor is how a grid drives that hall: it
 * gives {@link IndustryRules#MOTOR_FREE_RPM} of free speed, which is more than
 * anything in Age I can manage, and that is what makes it worth retro-fitting
 * rather than a sideways move.
 *
 * <h2>The loop, and why it does not pay</h2>
 *
 * A motor turning an alternator turning a motor is the obvious exploit, and it
 * is closed in arithmetic rather than by a rule: see
 * {@link IndustryRules#roundTripBest()}, which is under one for every gearing
 * and every count, and is checked by the harness on every build. A player who
 * builds the loop gets back six tenths of what they put in and learns
 * something true about machines.
 *
 * <p>What it draws is a <b>constant</b>, and deliberately: a motor that asked
 * for less when the grid was short would change what the grid could afford,
 * which would change what the motor asked for. A starved motor therefore still
 * draws its full rating and simply turns more weakly, and the fix a player
 * reaches for — unwire it, or build another turbine — is the fix that is
 * actually true.
 */
public class MotorBlock extends SpindleBlock {

    public static final MapCodec<MotorBlock> CODEC = simpleCodec(MotorBlock::new);

    public MotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MotorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.MOTOR.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MotorBlockEntity motor) {
            player.displayClientMessage(motor.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A machine on the grid, and a source on the line. */
    public static class MotorBlockEntity extends SpindleBlockEntity {

        public MotorBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.MOTOR.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return IndustryRules.Voltage.LOW;
        }

        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return MOTOR;
        }

        private static final IndustryRules.Machine MOTOR =
                new IndustryRules.Machine(IndustryRules.MOTOR_RATED_FE,
                        IndustryRules.Voltage.LOW);

        /**
         * What it drives the line with.
         *
         * Free speed is what a motor has and a water wheel does not, so the
         * speed does not fall away as the grid starves; the torque does. A
         * half-fed motor spins an empty line just as fast and gives up the
         * moment you hang anything off it, which is exactly how an
         * under-powered electric motor behaves.
         */
        @Nullable
        @Override
        public KineticRules.Source source() {
            float fed = share() * IndustryRules.MOTOR_RATED_FE;
            return fed <= 0.0F ? null : IndustryRules.motorAsSource(fed);
        }

        /**
         * The grid changed, so what this drives the line with changed.
         *
         * The two networks settle into each other over a tick or two and then
         * stop, because each of them only says anything when its own answer
         * actually moved.
         */
        @Override
        protected void told() {
            unsettleLine();
        }

        /** What it would say if it could talk. */
        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            float fed = share();
            if (fed <= 0.0F) {
                return Component.translatable("elysiumindustry.motor.dark");
            }
            return Component.translatable("elysiumindustry.motor.turning",
                    String.format("%.0f%%", fed * 100.0F),
                    String.format("%.1f", rpm()));
        }
    }
}
