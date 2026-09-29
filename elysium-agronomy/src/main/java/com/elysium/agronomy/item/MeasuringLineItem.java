package com.elysium.agronomy.item;

import com.elysium.agronomy.entity.FishEntity;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.rules.FishRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A measuring line: says how big a fish is.
 *
 * <p>Used on one of our fish in the water, or with a caught one in the other
 * hand, it says the fish's length off {@link FishRules#centimetres}. That is
 * the species' length: the game builds a fish's body and its hitbox from the
 * same number, and a caught fish has no size of its own, so the line reads
 * what the world shows rather than inventing a size nothing else agrees with.
 */
public class MeasuringLineItem extends Item {

    public MeasuringLineItem(Properties properties) {
        super(properties);
    }

    /** One of our fish in the water. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack line, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof FishEntity fish)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            player.displayClientMessage(measure(fish.rules()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** A caught fish in the other hand, or a line to say what it wants. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack line = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        FishRules.Fish fish = FishRules.fish(AgronomyComponents.ourId(other));
        if (!level.isClientSide) {
            player.displayClientMessage(fish == null
                    ? Component.translatable("elysiumagronomy.line.nothing")
                    : measure(fish), true);
        }
        return InteractionResultHolder.sidedSuccess(line, level.isClientSide);
    }

    /** What the line says about one of a species. */
    public static Component measure(FishRules.Fish fish) {
        return Component.translatable("elysiumagronomy.line.measured",
                Component.translatable("item.elysiumagronomy." + fish.id()),
                String.valueOf(FishRules.centimetres(fish)));
    }
}
