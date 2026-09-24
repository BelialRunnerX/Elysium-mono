package com.elysium.wilds.client;

import com.elysium.wilds.network.WildPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Where the two server messages land.
 *
 * <p>In its own class, and referenced from the network registration rather
 * than the other way about, so that a dedicated server loading
 * {@code WildNetwork} never touches a client class by accident. That mistake
 * is a crash on server start and it is one nobody finds in single player.
 */
public final class WildsClientHooks {

    private WildsClientHooks() {
    }

    public static void offer(WildPayloads.OfferPlaces places) {
        Minecraft.getInstance().setScreen(new CompassScreen(places.places()));
    }

    public static void found(WildPayloads.Found answer) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null) {
            return;
        }
        if (!answer.found()) {
            game.player.displayClientMessage(Component.translatable(
                    "elysiumwilds.compass.nowhere", answer.place()), true);
            return;
        }
        game.player.displayClientMessage(Component.translatable(
                "elysiumwilds.compass.found", answer.place(),
                String.valueOf(answer.where().getX()),
                String.valueOf(answer.where().getZ()),
                String.valueOf(answer.blocks())), true);
    }
}
