package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The library's one key (Silent Gear's SG-100).
 *
 * <h2>Why a key rather than shift</h2>
 *
 * Shift already means three things in an inventory — quick-move, split, and
 * "show me more" — and a player holding it to read a tooltip is a player who
 * has just moved the item. A key of its own is rebindable, does nothing else,
 * and can be held while dragging.
 *
 * <p>Held rather than toggled, because a detailed tooltip is something a
 * player wants for a moment and a toggle is a state they will forget they
 * left on.
 */
public final class ElysiumKeys {

    private ElysiumKeys() {
    }

    /** GLFW_KEY_LEFT_ALT. Written as its value: LWJGL is not on the compile classpath. */
    static final int KEY_LEFT_ALT = 342;

    /** Held to see everything a piece of gear is doing rather than the summary. */
    public static final KeyMapping DETAIL = new KeyMapping("key.elysiumlib.detail",
            InputConstants.Type.KEYSYM, KEY_LEFT_ALT, "key.categories.elysiumlib");

    /**
     * Whether the player is asking for detail right now.
     *
     * Asked from tooltip rendering, which happens outside the key-handling
     * tick, so this reads the hardware state rather than {@code consumeClick}
     * — a click consumed here would be a key press that the rest of the game
     * never sees.
     */
    public static boolean detailWanted() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(client.getWindow().getWindow(),
                DETAIL.getKey().getValue());
    }

    @EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(DETAIL);
        }
    }
}
