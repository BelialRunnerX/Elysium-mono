package com.elysium.arcana.client;

import com.elysium.arcana.network.ArcanaPayloads;
import net.minecraft.client.Minecraft;

/** Client half of the transmuter packets; only ever called on the client. */
public final class ArcanaClientHooks {

    private ArcanaClientHooks() {
    }

    public static void openTransmutations(ArcanaPayloads.OpenTransmutations payload) {
        Minecraft.getInstance().setScreen(new TransmuterScreen(payload));
    }
}
