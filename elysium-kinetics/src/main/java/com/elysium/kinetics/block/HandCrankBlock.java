package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * A handle, and your own arms.
 *
 * <p>The first source in the game and the only one that costs attention rather
 * than materials. One turn drives the line for {@link #WOUND_TICKS} and then
 * it winds down, which is exactly enough to grind a stack of something while
 * standing there and nowhere near enough to run a workshop.
 *
 * <p>That is the point of it. A crank is not a bad water wheel; it is the
 * thing that makes a water wheel obviously worth building.
 */
public class HandCrankBlock extends KineticBlock {

    public static final MapCodec<HandCrankBlock> CODEC = simpleCodec(HandCrankBlock::new);

    /** How long one turn of the handle keeps the line going. */
    public static final int WOUND_TICKS = 100;

    public HandCrankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HandCrankBlockEntity crank)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            crank.turn();
            level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 0.6F, 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HandCrankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.HAND_CRANK.get());
    }

    /** The crank, which is a source for as long as it is still wound. */
    public static class HandCrankBlockEntity extends KineticBlockEntity {

        private int wound;

        public HandCrankBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.HAND_CRANK.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Nullable
        @Override
        public KineticRules.Source source() {
            return wound > 0 ? KineticRules.HAND_CRANK : null;
        }

        /** Somebody turned the handle. */
        public void turn() {
            boolean wasStopped = wound <= 0;
            wound = WOUND_TICKS;
            if (wasStopped) {
                unsettle();
            }
            setChanged();
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (wound <= 0) {
                return;
            }
            if (--wound == 0) {
                unsettle();
            }
        }

        @Override
        protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            wound = tag.getInt("wound");
        }

        @Override
        protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("wound", wound);
        }
    }
}
