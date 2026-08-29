package com.elysium.lib.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one place a packet leaves the server.
 *
 * Wrapped rather than called inline for a boring reason that matters:
 * {@code sendToPlayer} takes a {@link ServerPlayer}, and every call site
 * upstream holds a plain {@link Player}. Doing the check once here means the
 * cast is written once, and a logical-client player quietly does nothing
 * rather than throwing halfway through a tick.
 */
public final class ElysiumPacketSender {

    private ElysiumPacketSender() {
    }

    public static void toPlayer(Player player, CustomPacketPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }
}
