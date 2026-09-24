package com.elysium.trinkets.event;

import com.elysium.trinkets.ElysiumTrinkets;
import com.elysium.trinkets.item.PotionCharmItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.elysium.trinkets.CuriosSlots;


/**
 * Keeping a worn charm's effect topped up.
 *
 * <h2>Why this is not an ElysiumPassive</h2>
 *
 * Everything else a trinket does goes through the library, and the library's
 * seam carries which trinket is worn and at what tier — deliberately not the
 * stack. A charm's whole content is on the stack, so it cannot be asked for
 * through that seam, and widening the seam would put a stack list into the
 * damage path for the sake of one item.
 *
 * <p>So this handler asks {@code CuriosSlots}, which is the one file in this
 * module that has heard of Curios, once a second and off the damage path
 * entirely.
 *
 * <h2>What it costs, and when</h2>
 *
 * A point of durability each time it actually applies something. A charm whose
 * effect the player already has from elsewhere spends nothing: stacking a
 * potion with a charm should be a player's choice, not a punishment.
 */
@EventBusSubscriber(modid = ElysiumTrinkets.MODID)
public final class PotionCharmHandler {

    private PotionCharmHandler() {
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)
                || player.tickCount % PotionCharmItem.INTERVAL != 0) {
            return;
        }
        for (ItemStack stack : CuriosSlots.wornCharms(player)) {
            if (!PotionCharmItem.working(stack)) {
                continue;
            }
            if (apply(player, stack)) {
                // Damaged rather than broken when it runs out: hurt(…) would
                // destroy it, and a charm that vanishes is an investment nobody
                // makes twice. working() is what stops a spent one acting.
                stack.setDamageValue(Math.min(stack.getMaxDamage(),
                        stack.getDamageValue() + 1));
            }
        }
    }

    /**
     * Top the wearer up, and say whether anything was actually done.
     *
     * Only when what they have is running out, so one charm is one application
     * every several seconds rather than one a second.
     */
    private static boolean apply(Player player, ItemStack stack) {
        PotionContents contents = PotionCharmItem.contentsOf(stack);
        boolean did = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (!PotionCharmItem.allowed(effect)) {
                continue;
            }
            MobEffectInstance had = player.getEffect(effect.getEffect());
            if (had != null && (had.getAmplifier() > effect.getAmplifier()
                    || had.getDuration() > PotionCharmItem.REFRESH_AT)) {
                continue;
            }
            player.addEffect(new MobEffectInstance(effect.getEffect(),
                    PotionCharmItem.DURATION, effect.getAmplifier(), true, false, true));
            did = true;
        }
        return did;
    }
}
