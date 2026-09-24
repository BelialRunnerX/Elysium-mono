package com.elysium.space.item;

import com.elysium.space.entity.RocketEntity;
import com.elysium.space.world.JetRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Fuel for a rocket (right-click the rocket) or, used in the air, for the jet suit you are wearing. */
public class FuelCanisterItem extends Item {

    public FuelCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack jet = JetSuitItem.worn(player);
        if (jet.isEmpty() || JetSuitItem.fuel(jet) >= JetRules.TANK) {
            return InteractionResultHolder.pass(held);
        }
        if (!level.isClientSide) {
            JetSuitItem.setFuel(jet, JetSuitItem.fuel(jet) + RocketEntity.CANISTER_MB);
            held.consume(1, player);
            player.displayClientMessage(Component.translatable("elysiumspace.jet.fuel", JetSuitItem.fuel(jet),
                    JetRules.TANK), true);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
