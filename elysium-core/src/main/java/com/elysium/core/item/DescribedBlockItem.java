package com.elysium.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * A block item that says what its block is for.
 *
 * <h2>Why this exists</h2>
 *
 * A plain {@code BlockItem} shows a name and nothing else, which is fine for an
 * ore and actively misleading for a machine whose whole effect is on a meter
 * the player cannot see. The Registry Beacon is the case that forced this: it
 * is craftable, it looks like a decorative block, and using it levies a
 * <em>permanent</em> Suspicion floor. A player who learns that after the fact
 * has been trapped by the interface, not by the design.
 *
 * The lines are translation keys rather than text so they stay translatable and
 * stay out of the Java. One key per line, because a paragraph in a single key
 * cannot be wrapped by the tooltip renderer and comes out as one very long row.
 */
public class DescribedBlockItem extends BlockItem {

    private final String[] keys;

    public DescribedBlockItem(Block block, Item.Properties properties, String... keys) {
        super(block, properties);
        this.keys = keys;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        for (int i = 0; i < keys.length; i++) {
            // The first line is what it does; anything after is the cost or the
            // caveat, which reads as secondary and should look it.
            lines.add(Component.translatable(keys[i]).withStyle(
                    i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
