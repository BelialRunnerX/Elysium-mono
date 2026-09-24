package com.elysium.arcana.block;

import com.elysium.arcana.registry.ArcanaAttachments;
import com.elysium.arcana.rules.ArcanaRules;
import com.elysium.arcana.transmute.Transmuting;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The transmuter: the one block this module adds.
 *
 * <h2>Two gestures and no screen to learn</h2>
 *
 * Hold something up to it and it dissolves — the whole stack, or one of it if
 * you crouch — and you now know that thing. Touch it empty-handed and it shows
 * you everything you know, with a price against each. There is no inventory to
 * fill, no fuel, and nothing to arrange.
 *
 * <h2>Why it has no power and no tier</h2>
 *
 * Because what limits it is already the strongest limit in the module: you can
 * only make what you have already held, and every round trip loses a tenth.
 * A machine that also wanted a wire would be a gate on a thing that is already
 * gated, and the pack has enough of those.
 */
public class TransmuterBlock extends Block {

    public static final MapCodec<TransmuterBlock> CODEC = simpleCodec(TransmuterBlock::new);

    public TransmuterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || held.isEmpty()) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        int count = player.isSecondaryUseActive() ? 1 : held.getCount();
        Component said = Transmuting.dissolve(player, held, count);
        player.displayClientMessage(said, true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                0.5F, 1.4F);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer asking) {
            if (ArcanaAttachments.learned(asking).isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarcana.transmuter.empty",
                                String.valueOf(MoteTable.recipesSeen())), true);
                return InteractionResult.CONSUME;
            }
            Transmuting.open(asking);
        }
        return InteractionResult.CONSUME;
    }

    /** So the block names the rule its behaviour comes from. */
    public static float roundTripLoss() {
        return 1.0F - ArcanaRules.BURN_SHARE;
    }
}
