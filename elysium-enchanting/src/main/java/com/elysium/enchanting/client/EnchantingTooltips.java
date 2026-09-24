package com.elysium.enchanting.client;

import com.elysium.enchanting.ElysiumEnchanting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The two things this module says on a tooltip.
 *
 * <h2>What an enchantment does</h2>
 *
 * Vanilla shows an enchantment's name and nothing else, which is fine for
 * Sharpness and useless for twenty enchantments nobody has met before. Held
 * shift, each enchantment on the stack that has a line written for it says it.
 *
 * <h2>What a block is worth to a table</h2>
 *
 * Every stat block this module knows ships with a sentence, and the sentence
 * lives in the language file rather than in the data pack. That is on purpose:
 * a tooltip is drawn on the client and a data pack is read on the server, so a
 * tooltip that wanted the numbers would need a packet, a sync and a version
 * check to say one line. Asking the language file instead costs nothing, works
 * in a menu before a world is loaded — and gives a pack that adds its own
 * shelf a tooltip for free, by writing the one key.
 */
@EventBusSubscriber(modid = ElysiumEnchanting.MODID, value = Dist.CLIENT)
public final class EnchantingTooltips {

    private EnchantingTooltips() {
    }

    /** The key a block carries when it is worth something to a table. */
    public static String shelfKey(ResourceLocation block) {
        return "elysiumenchanting.shelf." + block.getPath();
    }

    /** The key an enchantment carries when somebody has written down what it does. */
    public static String describes(ResourceLocation enchantment) {
        return "enchantment." + enchantment.getNamespace() + "." + enchantment.getPath()
                + ".desc";
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            ResourceLocation id = idOf(blockItem.getBlock());
            String key = shelfKey(id);
            if (id != null && I18n.exists(key)) {
                event.getToolTip().add(Component.translatable(key)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            return;
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> entry
                : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey()
                    .map(key -> key.location()).orElse(null);
            if (id == null) {
                continue;
            }
            String key = describes(id);
            if (I18n.exists(key)) {
                event.getToolTip().add(Component.translatable(key)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static ResourceLocation idOf(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
    }

}
