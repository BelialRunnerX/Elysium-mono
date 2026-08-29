package com.elysium.core.item;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.network.ElysiumNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Imperial Codex: the character sheet, in the hand.
 *
 * Every player is issued one on their first join, immediately after the choice
 * screen, so the sheet is never something they have to remember a command to
 * see. It is not consumed, cannot be crafted and never needs to be — losing it
 * costs nothing, because {@code /elysium sheet} does the same thing.
 */
public class ElysiumCodexItem extends Item {

    public ElysiumCodexItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            // The screen is opened by the server pushing a sheet, not by the
            // client deciding to draw one — so the numbers on it are always
            // the numbers the server holds.
            ElysiumNetwork.sendSheet(player);
        }
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysium.tooltip.codex")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Gives a player their Codex, or drops it at their feet if their inventory
     * is somehow already full on their very first tick in the world.
     */
    public static void grant(Player player, ItemStack codex) {
        if (!player.getInventory().add(codex)) {
            player.drop(codex, false);
        }
    }

    /**
     * True exactly once per character.
     *
     * This used to key off "has no race yet", on the reasoning that a second
     * join already has one. It does not: the picker can be escaped by killing
     * the client, so anyone who declined to answer collected a Codex per
     * relog. A flag of its own is the only thing that actually says "given".
     */
    public static boolean shouldGrant(Player player) {
        return !ElysiumCharacter.hasCodex(player);
    }
}
