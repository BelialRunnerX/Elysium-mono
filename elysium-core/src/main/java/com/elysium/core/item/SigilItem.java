package com.elysium.core.item;

import com.elysium.lib.item.ElysiumAugments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A sigil: one use, spent on a piece a player has already decided to keep.
 *
 * <p>The item knows only which augment it is. What that augment does lives in
 * {@link ElysiumAugments}, in the library, so the Augmenting Table, a command,
 * or another mod's bench all do the same thing with it.
 */
public class SigilItem extends Item {

    private final String augment;

    public SigilItem(String augment) {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
        this.augment = augment;
    }

    public String augment() {
        return augment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("elysium.sigil." + augment + ".desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
