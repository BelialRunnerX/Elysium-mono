package com.elysium.armory.block;

import com.elysium.armory.craft.ArmoryAlloy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Alloy Forge (Silent Gear's SG-80): where mixed metals start working
 * together.
 *
 * <h2>No menu, again</h2>
 *
 * Hold the part, hit the forge. There is one input, one output and no choice
 * in it — the synergy a part earns is decided by what it is already made of,
 * so a screen would show the player a number and a button, and the button
 * would be the only thing on it.
 *
 * <p>A part with nothing to gain is told so rather than silently ignored: a
 * player holding two identical ingots' worth of blade should find out here
 * that mixing is the point.
 */
public class ArmoryAlloyForgeBlock extends Block {

    public ArmoryAlloyForgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        ItemStack alloyed = ArmoryAlloy.alloy(held);
        if (alloyed.isEmpty()) {
            if (!level.isClientSide() && !held.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarmory.forge.nothing"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            held.shrink(1);
            player.getInventory().placeItemBackInInventory(alloyed);
        }
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.2F);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
