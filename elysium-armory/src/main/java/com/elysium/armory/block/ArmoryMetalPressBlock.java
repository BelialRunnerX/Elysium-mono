package com.elysium.armory.block;

import com.elysium.armory.registry.ArmoryItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Metal Press (Silent Gear's SG-84): an ingot goes flat.
 *
 * <h2>No menu on purpose</h2>
 *
 * Pressing is one input and one output with no choice in it, and a screen for
 * that is a screen that exists to be closed again. Hold ingots, hit the press,
 * get plates. The cooldown is what stops it being a click-to-convert loop that
 * empties an inventory in a second.
 */
public class ArmoryMetalPressBlock extends Block {

    /** What counts as an ingot: the convention tag every mod's metals are in. */
    public static final TagKey<Item> INGOTS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots"));

    /** How long the press takes between plates, in ticks. */
    public static final int COOLDOWN = 10;

    public ArmoryMetalPressBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(INGOTS)) {
            if (!level.isClientSide() && !held.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarmory.press.only_ingots"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (player.getCooldowns().isOnCooldown(held.getItem())) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            held.shrink(1);
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(ArmoryItems.SHEET_METAL.get()));
            player.getCooldowns().addCooldown(held.getItem(), COOLDOWN);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
