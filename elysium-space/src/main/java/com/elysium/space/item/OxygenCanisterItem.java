package com.elysium.space.item;

import com.elysium.space.world.SuitAir;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Use it to add {@link SuitAir#CANISTER} mB of air to the space suit chestplate you are wearing. */
public class OxygenCanisterItem extends Item {

    public OxygenCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SpaceSuitItem) || SpaceSuitItem.air(chest) >= SuitAir.CAPACITY) {
            return InteractionResultHolder.fail(held);
        }
        if (!level.isClientSide) {
            int now = SuitAir.addCanister(SpaceSuitItem.air(chest));
            SpaceSuitItem.setAir(chest, now);
            held.consume(1, player);
            player.displayClientMessage(Component.translatable("elysiumspace.suit.air", now, SuitAir.CAPACITY), true);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
