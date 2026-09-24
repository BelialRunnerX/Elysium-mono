package com.elysium.transit.client;

import com.elysium.transit.network.TransitPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/** Client half of the waystone packets; only ever called on the client. */
public final class TransitClientHooks {

    private TransitClientHooks() {
    }

    /**
     * The menu, aimed at the stone the player is standing in front of.
     *
     * <p>Which block that is comes from the client's own hit result rather
     * than out of the packet, because it is the one thing the client knows
     * better than the server — and it is sent back with the choice so the
     * server can check the distance and everything else again.
     */
    public static void openWaystones(TransitPayloads.OpenWaystones payload) {
        Minecraft client = Minecraft.getInstance();
        BlockPos looking = client.hitResult instanceof BlockHitResult hit
                ? hit.getBlockPos() : BlockPos.ZERO;
        client.setScreen(new WaystoneScreen(payload, looking));
    }
}
