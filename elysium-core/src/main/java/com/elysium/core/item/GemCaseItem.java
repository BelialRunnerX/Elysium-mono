package com.elysium.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;

/**
 * The Gem Case (Apotheosis' AP-14): where the tablets are kept.
 *
 * <h2>A bundle, on purpose</h2>
 *
 * Vanilla already has a container item with a tooltip, a fill bar, a
 * right-click to empty and a shift-click to fill, and every player already
 * knows how it behaves. Writing a second one with its own screen would be a
 * new thing to learn that does the same job worse.
 *
 * <p>What makes it a gem case rather than a bundle is what it refuses. A
 * container that will hold anything is a backpack, and this mod is not adding
 * a backpack by the side door — so anything that is not a rune, a sigil or
 * dust is turned away at the two places an item can go in.
 */
public class GemCaseItem extends BundleItem {

    public GemCaseItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
                .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
    }

    /** Whether the case will take this. */
    public static boolean accepts(ItemStack stack) {
        return stack.isEmpty()
                || stack.getItem() instanceof ElysiumRuneItem
                || stack.getItem() instanceof SigilItem
                || stack.is(com.elysium.core.Elysium.GEM_DUST.get());
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack caseStack, Slot slot, ClickAction action,
                                          Player player) {
        // Putting the case onto something: only if that something belongs in it.
        if (action == ClickAction.SECONDARY && !accepts(slot.getItem())) {
            return false;
        }
        return super.overrideStackedOnOther(caseStack, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack caseStack, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (action == ClickAction.SECONDARY && !accepts(other)) {
            return false;
        }
        return super.overrideOtherStackedOnMe(caseStack, other, slot, action, player, access);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("elysium.gem_case.desc").withStyle(ChatFormatting.GRAY));
    }
}
