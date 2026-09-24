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
 * Rotation in, electricity out. The door into the second age.
 *
 * <p>Six times what Age I's dynamo makes, for six times the torque, which is
 * the whole of what "the second age is worth reaching" means in numbers: the
 * dynamo exists so a player meets Forge Energy before the age that is about
 * it, and this is the first time electricity is worth having.
 *
 * <p>It needs {@link IndustryRules#ALTERNATOR_LEAST_RPM} before it makes
 * anything at all, and it draws {@link IndustryRules#ALTERNATOR_DRAW} — more
 * than a single water wheel can stall-push against. The first alternator a
 * player lights is therefore the first time they have to build a
 * <em>second</em> source, which is the lesson Age I has been setting up.
 */
public class AlternatorBlock extends SpindleBlock {

    public static final MapCodec<AlternatorBlock> CODEC = simpleCodec(AlternatorBlock::new);

    public AlternatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlternatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.ALTERNATOR.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof AlternatorBlockEntity alternator) {
            player.displayClientMessage(alternator.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The one node that is a load on a line and a source on a grid. */
    public static class AlternatorBlockEntity extends SpindleBlockEntity {

        public AlternatorBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.ALTERNATOR.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return IndustryRules.ALTERNATOR.voltage();
        }

        @Nullable
        @Override
        public KineticRules.Load load() {
            return IndustryRules.ALTERNATOR_LOAD;
        }

        /**
         * What it puts into the grid, or nothing when it is not turning fast
         * enough.
         *
         * Null and not a zero generator, because a generator at zero would
         * still count as the place the grid measures its wire loss from, and a
         * dead alternator is not where the power is coming from.
         */
        @Nullable
        @Override
        public IndustryRules.Generator generator() {
            float made = IndustryRules.alternatorOutput(rpm());
            return made <= 0.0F ? null
                    : new IndustryRules.Generator(made, IndustryRules.ALTERNATOR.voltage());
        }

        /**
         * What it would say if it could talk.
         *
         * "Turning, but not fast enough" is the failure a player will actually
         * hit, and it needs its own sentence or it looks like "not turning".
         */
        public Component says() {
            if (!lineRunning()) {
                return Component.translatable("elysiumindustry.alternator.still");
            }
            float speed = rpm();
            if (speed < IndustryRules.ALTERNATOR_LEAST_RPM) {
                return Component.translatable("elysiumindustry.alternator.slow",
                        String.format("%.1f", speed),
                        String.format("%.0f", IndustryRules.ALTERNATOR_LEAST_RPM));
            }
            return Component.translatable("elysiumindustry.alternator.making",
                    String.format("%.0f", IndustryRules.alternatorOutput(speed)),
                    String.format("%.1f", speed));
        }
    }
}
