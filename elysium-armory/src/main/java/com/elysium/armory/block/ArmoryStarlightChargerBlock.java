package com.elysium.armory.block;

import com.elysium.armory.craft.ArmoryGrading;
import com.elysium.armory.registry.ArmoryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

/**
 * The Starlight Charger (Silent Gear's SG-82): a part left out under the sky.
 *
 * <h2>Why this one has a block entity and the others do not</h2>
 *
 * The Alloy Forge and the Material Grader answer in the moment a player asks.
 * This one does not answer at all — it waits. A part lies on it through the
 * night, and what makes the charge worth anything is exactly that the player
 * had to leave it somewhere and come back. That needs the block to hold
 * something while nobody is looking, which is what a block entity is for.
 *
 * <p>It only counts nights it can see: a charger under a roof takes forever,
 * which is the rule stated rather than a warning in a tooltip.
 */
public class ArmoryStarlightChargerBlock extends BaseEntityBlock {

    public static final MapCodec<ArmoryStarlightChargerBlock> CODEC =
            simpleCodec(ArmoryStarlightChargerBlock::new);

    public ArmoryStarlightChargerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmoryStarlightChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ArmoryBlockEntities.STARLIGHT_CHARGER.get(),
                        ArmoryStarlightChargerBlockEntity::tick);
    }

    /** Putting a part on it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ArmoryStarlightChargerBlockEntity charger)) {
            return ItemInteractionResult.FAIL;
        }
        if (!charger.held().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!ArmoryGrading.canCharge(held)) {
            if (!level.isClientSide() && !held.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarmory.charger.nothing"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            charger.put(held.split(1));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Taking it back, charged or not. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ArmoryStarlightChargerBlockEntity charger)
                || charger.held().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.getInventory().placeItemBackInInventory(charger.take());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.7F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** What is on it when it is broken is not swallowed by the block. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                            boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof ArmoryStarlightChargerBlockEntity charger
                && !charger.held().isEmpty()) {
            Block.popResource(level, pos, charger.take());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
