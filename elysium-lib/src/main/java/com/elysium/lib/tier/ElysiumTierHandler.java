package com.elysium.lib.tier;

import com.elysium.lib.ElysiumLib;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Remembering where a player has been.
 *
 * <p>Two moments, and both are cheap: arriving somewhere, and logging in — the
 * second because a world may have been declared by a module added after the
 * player last played there, and a floor they have already earned should not
 * depend on when the pack was assembled.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumTierHandler {

    private ElysiumTierHandler() {
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            announce(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            announce(player);
        }
    }

    private static void announce(ServerPlayer player) {
        int before = ElysiumTiers.of(player);
        if (!ElysiumTiers.visited(player, player.level())) {
            return;
        }
        int after = ElysiumTiers.of(player);
        if (after > before) {
            player.sendSystemMessage(Component.translatable("elysium.world_tier.raised",
                    Component.translatable(ElysiumWorldTier.nameKey(after),
                            after - ElysiumWorldTier.MAX_NAMED)));
        }
    }
}
