package com.elysium.enchanting.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A tome: one use, on an anvil, and the tooltip says which.
 *
 * <h2>Why the line is not optional</h2>
 *
 * Nothing about an item called "Tome of Patience" tells a player what to put
 * it next to, and an anvil gives no hint until the right two things are in it.
 * A consumable whose use cannot be guessed and cannot be discovered is an item
 * players look up on a wiki, so the item carries the sentence.
 */
public class TomeItem extends Item {

    private final String tooltip;

    public TomeItem(Properties properties, String tooltip) {
        super(properties.stacksTo(16));
        this.tooltip = tooltip;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
    }
}
