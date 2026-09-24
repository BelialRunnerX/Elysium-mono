package com.elysium.agronomy.item;

import com.elysium.agronomy.registry.DietAttachments;
import com.elysium.agronomy.rules.AgronomyRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A record of everything you have eaten, and what it is worth.
 *
 * <p>Right-clicking says the count and the bonus in the action bar rather than
 * opening a screen. The screen would be nicer and is not what this mechanic
 * needs: the only question a player ever asks it is "how far along am I", and
 * a line of text answers that without taking the world away from them.
 */
public class FoodBook extends Item {

    public FoodBook(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int variety = DietAttachments.variety(player);
            player.displayClientMessage(Component.translatable(
                    "elysiumagronomy.diet.read", variety,
                    AgronomyRules.FOODS_FOR_FULL,
                    String.format("%.1f", AgronomyRules.extraHearts(variety))), true);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("elysiumagronomy.diet.tip",
                        AgronomyRules.FOODS_FOR_FULL,
                        AgronomyRules.MOST_EXTRA_HEARTS)
                .withStyle(ChatFormatting.GRAY));
    }
}
