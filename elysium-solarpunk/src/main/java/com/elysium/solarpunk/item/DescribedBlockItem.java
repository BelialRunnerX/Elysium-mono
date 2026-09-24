package com.elysium.solarpunk.item;

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
 * <h2>Why this mod has its own</h2>
 *
 * elysium-core has a class of the same name doing the same job. Sharing it
 * would mean depending on elysium-core, which would mean this set could not be
 * installed on its own — and the whole argument for the set being its own mod
 * is that it needs nothing else in Elysium. Twenty lines is a cheap price for
 * that, and the alternative — moving it into elysium-lib — would put an item
 * class in the library, which ships no items on purpose.
 *
 * <h2>What it is carrying here</h2>
 *
 * Two different things, and the second is the one that matters:
 *
 * <ul>
 *   <li>For a building block, one line saying what it is made of or what it is
 *       good for. A set of thirty-three decorative blocks is a wall of names
 *       in a creative tab otherwise.</li>
 *   <li>For a machine, the behaviour it <em>would</em> have, plus a second line
 *       saying the machinery is not built yet. A block shaped like a
 *       bioreactor, craftable from brass and kelp, that silently does nothing
 *       is the interface lying to the player. Saying so on the tooltip is the
 *       cheapest possible honesty.</li>
 * </ul>
 *
 * <p>The lines are translation keys rather than text, so they stay
 * translatable and stay out of the Java. One key per line: a paragraph in a
 * single key cannot be wrapped by the tooltip renderer and comes out as one
 * very long row.
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
            // The first line is what it is; anything after is the caveat,
            // which reads as secondary and should look it.
            lines.add(Component.translatable(keys[i]).withStyle(
                    i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
