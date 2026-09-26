package com.elysium.agronomy.item;

import com.elysium.agronomy.entity.BeeEntity;
import com.elysium.agronomy.event.Keeping;
import com.elysium.agronomy.registry.BeeItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A comb scoop: the comb straight off a bee.
 *
 * <p>Used on one of our bees that has finished a comb ({@link
 * BeeEntity#harvest}), it takes that species' own comb &mdash; which is the
 * one thing a centrifuge turns into what the bee is for. A bee that is not
 * done yet says how far along it is. Taking a comb upsets the bees around,
 * unless they have been smoked.
 */
public class BeeScoopItem extends Item {

    public BeeScoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack scoop, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof BeeEntity bee)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        float along = bee.progress();
        if (!bee.harvest()) {
            player.displayClientMessage(Component.translatable("elysiumagronomy.scoop.not_ready",
                    String.valueOf(Math.round(along * 100.0F))), true);
            return InteractionResult.CONSUME;
        }
        ItemStack comb = new ItemStack(BeeItems.COMBS.get(bee.species()).get());
        player.getInventory().placeItemBackInInventory(comb);
        scoop.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        Keeping.robbed(player.level(), bee.blockPosition(), player);
        return InteractionResult.SUCCESS;
    }
}
