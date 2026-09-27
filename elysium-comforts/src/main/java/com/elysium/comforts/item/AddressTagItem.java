package com.elysium.comforts.item;

import com.elysium.comforts.registry.ComfortComponents;
import com.elysium.comforts.rules.PostRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A name, on a piece of card.
 *
 * <p>It is not mail — it is never posted and it costs nothing to send, because
 * it is the thing that makes sending possible rather than a thing you send.
 * {@link PostRules#faultsInPosted} is the rule that would catch this item being
 * charged postage for a journey it can never make.
 */
public class AddressTagItem extends Item {

    public AddressTagItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    /** What is written on it, or the empty string. */
    public static String written(ItemStack stack) {
        String found = stack.get(ComfortComponents.ADDRESS.get());
        return found == null ? "" : found;
    }

    /**
     * Write on it.
     *
     * @return whether the post will carry that name
     */
    public static boolean write(ItemStack stack, String name) {
        if (!PostRules.addressable(name)) {
            return false;
        }
        stack.set(ComfortComponents.ADDRESS.get(), name);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        String name = written(stack);
        lines.add(name.isEmpty()
                ? Component.translatable("item.elysiumcomforts.blank")
                : Component.translatable("item.elysiumcomforts.to", name));
    }
}
