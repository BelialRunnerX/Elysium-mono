package com.elysium.space.client;

import com.elysium.space.network.SpacePayloads;
import net.minecraft.client.Minecraft;

/** Client half of the rocket packets; only ever called on the client. */
public final class SpaceClientHooks {

    private SpaceClientHooks() {
    }

    public static void openDestinations(SpacePayloads.OpenDestinations payload) {
        Minecraft.getInstance().setScreen(new DestinationScreen(payload));
    }

    public static void openWaygates(SpacePayloads.OpenWaygates payload) {
        Minecraft.getInstance().setScreen(new WaygateScreen(payload));
    }

    /**
     * The freight list, aimed at the terminal the player is looking at.
     *
     * The block is read from the client's own hit result rather than sent in
     * the packet, and then sent back with the choice: it is the one thing the
     * client genuinely knows better, because it is what the player is standing
     * in front of. The server checks the distance before acting on it.
     */
    public static void openFreight(SpacePayloads.OpenFreight payload) {
        Minecraft client = Minecraft.getInstance();
        net.minecraft.core.BlockPos looking =
                client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                        ? hit.getBlockPos() : net.minecraft.core.BlockPos.ZERO;
        client.setScreen(new FreightScreen(payload, looking));
    }
}
