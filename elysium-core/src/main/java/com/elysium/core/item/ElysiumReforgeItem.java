package com.elysium.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * The reforge catalyst. Consumed at a Reforge Table to reroll a piece's bonus
 * stats.
 *
 * Rarity is a data component in 1.21.1, so it is declared on the properties
 * rather than by overriding {@code getRarity}, which no longer exists.
 */
public class ElysiumReforgeItem extends Item {

    public ElysiumReforgeItem() {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysium.tooltip.reforge_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Quality multiplier for a Silent Gear material grade. Higher grades roll
     * better stats during reforging.
     */
    public static float getGradeMultiplier(String materialGrade) {
        if (materialGrade == null) {
            return 1.0F;
        }
        return switch (materialGrade.toLowerCase(Locale.ROOT)) {
            case "crude" -> 0.8F;
            case "common" -> 1.0F;
            case "uncommon" -> 1.2F;
            case "rare" -> 1.5F;
            case "epic" -> 1.8F;
            case "legendary" -> 2.2F;
            default -> 1.0F;
        };
    }
}
