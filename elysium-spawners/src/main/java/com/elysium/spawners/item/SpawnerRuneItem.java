package com.elysium.spawners.item;

import com.elysium.spawners.data.SpawnerModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A rune: something to hold while clicking a spawner.
 *
 * <h2>The tooltip is the recipe book</h2>
 *
 * What a rune does is a data file, and a data file is invisible. The item
 * carries its own sentence instead, so a player holding one knows what it is
 * for without a wiki — and a pack that retunes the file and rewrites the line
 * has told the player the truth in both places.
 */
public class SpawnerRuneItem extends Item {

    private final String tooltip;

    public SpawnerRuneItem(Properties properties, String tooltip) {
        super(properties);
        this.tooltip = tooltip;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
        SpawnerModifiers.Modifier modifier = SpawnerModifiers.get().forItem(stack);
        if (modifier != null && modifier.maxSteps() > 0) {
            lines.add(Component.translatable("elysiumspawners.tooltip.limit",
                            Component.literal(String.valueOf(modifier.maxSteps())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
