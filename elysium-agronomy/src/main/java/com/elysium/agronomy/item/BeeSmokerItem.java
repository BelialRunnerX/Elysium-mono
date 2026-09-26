package com.elysium.agronomy.item;

import com.elysium.agronomy.event.Keeping;
import com.elysium.agronomy.rules.KeepingRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A smoker: calm bees, for long enough to rob them.
 *
 * <p>Every bee within {@link KeepingRules#SMOKE_RANGE} is calm for {@link
 * KeepingRules#SMOKE_TICKS}: it forgets whoever upset it and notices nobody
 * new. The harness holds the smoke to outlasting the anger and reaching
 * further than a robbery upsets, which is the whole reason this is worth
 * making.
 */
public class BeeSmokerItem extends Item {

    /** How long between puffs, so one smoker is not a permanent calm. */
    public static final int COOLDOWN_TICKS = 20;

    public BeeSmokerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack smoker = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            int calmed = Keeping.smoke(server, player.blockPosition());
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(),
                    player.getEyeY(), player.getZ(), 12, 0.6D, 0.3D, 0.6D, 0.01D);
            server.playSound(null, player.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("elysiumagronomy.smoker.calmed",
                    String.valueOf(calmed), String.valueOf(KeepingRules.SMOKE_TICKS / 20)), true);
            smoker.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(smoker, level.isClientSide);
    }
}
