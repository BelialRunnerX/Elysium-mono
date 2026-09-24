package com.elysium.almanac.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * The book itself.
 *
 * <p>It opens on the client and nothing is sent anywhere. The graph is in the
 * jar and identical on both sides, and the one thing that differs between
 * players — what they have finished — is already synced to them, so there is
 * no round trip, no packet, and no way for the page to be stale.
 *
 * <p>Losing it costs nothing: it is craftable out of a book and a feather, it is
 * handed out on first join, and it holds no state of its own.
 */
public class AlmanacItem extends Item {

    public AlmanacItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide() && FMLEnvironment.dist == Dist.CLIENT) {
            // Behind the dist check because a dedicated server has no screens
            // at all and loading the class that draws one would be a crash on
            // the one machine nobody is watching.
            com.elysium.almanac.client.AlmanacScreens.open();
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysiumalmanac.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
