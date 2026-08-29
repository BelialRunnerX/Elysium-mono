package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs the behaviour half of the rune system.
 *
 * NeoForge removed {@code IItemExtension#onArmorTick} in 1.21, so a worn item
 * gets no tick of its own and a player tick handler is the supported
 * replacement.
 *
 * Every socketed rune with an effect is called once per copy, so a rune in four
 * pieces runs four times — which is what makes stacking one worthwhile. The
 * effect is told whether its copy is aligned, and decides for itself what that
 * is worth.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumRuneTickHandler {

    private ElysiumRuneTickHandler() {
    }

    /** Effects are short and refreshed on a cadence rather than every tick. */
    private static final int INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % INTERVAL != 0) {
            return;
        }

        for (ItemStack stack : player.getArmorSlots()) {
            run(player, stack);
        }
        run(player, player.getMainHandItem());
    }

    private static void run(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof ElysiumSocketable gear)) {
            return;
        }
        for (ElysiumRune rune : gear.getSocketedRunes(stack)) {
            ElysiumRune.RuneEffect effect = rune.getEffect();
            if (effect != null) {
                effect.apply(player, stack, ElysiumSockets.isAligned(rune, gear.getElement()));
            }
        }
    }
}
