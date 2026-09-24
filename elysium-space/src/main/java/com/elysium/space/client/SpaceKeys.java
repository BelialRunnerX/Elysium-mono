package com.elysium.space.client;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.RocketEntity;
import com.elysium.space.network.SpacePayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Launch key: G by default, rebindable under Controls, Elysium Space. */
public final class SpaceKeys {

    private SpaceKeys() {
    }

    /** GLFW_KEY_G. Written as its value: LWJGL is not on the module's compile classpath. */
    static final int KEY_G = 71;

    /** GLFW_KEY_J. */
    static final int KEY_J = 74;

    public static final KeyMapping JET_MODE = new KeyMapping("key.elysiumspace.jet_mode",
            InputConstants.Type.KEYSYM, KEY_J, "key.categories.elysiumspace");

    private static boolean wasThrusting;

    public static final KeyMapping LAUNCH = new KeyMapping("key.elysiumspace.launch",
            InputConstants.Type.KEYSYM, KEY_G, "key.categories.elysiumspace");

    @EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(LAUNCH);
            event.register(JET_MODE);
        }
    }

    @EventBusSubscriber(modid = ElysiumSpace.MODID, value = Dist.CLIENT)
    public static final class Ticking {
        private Ticking() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            while (JET_MODE.consumeClick()) {
                if (client.player != null && !com.elysium.space.item.JetSuitItem.worn(client.player).isEmpty()) {
                    PacketDistributor.sendToServer(new SpacePayloads.JetMode());
                }
            }
            jet(client);
            while (LAUNCH.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getVehicle() instanceof RocketEntity) {
                    PacketDistributor.sendToServer(new SpacePayloads.Launch());
                }
            }
        }
    }

    /**
     * The jet suit's movement, on the client where the player is moved. Tells
     * the server when thrust starts and stops so it can burn fuel.
     */
    static void jet(Minecraft client) {
        net.minecraft.client.player.LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack jet = com.elysium.space.item.JetSuitItem.worn(player);
        com.elysium.space.world.JetRules.Mode mode = jet.isEmpty() ? com.elysium.space.world.JetRules.Mode.OFF
                : com.elysium.space.item.JetSuitItem.mode(jet);
        int fuel = jet.isEmpty() ? 0 : com.elysium.space.item.JetSuitItem.fuel(jet);
        boolean thrusting = mode != com.elysium.space.world.JetRules.Mode.OFF && fuel > 0
                && client.options.keyJump.isDown() && !player.getAbilities().flying && player.getVehicle() == null;
        if (thrusting != wasThrusting) {
            PacketDistributor.sendToServer(new SpacePayloads.JetThrust(thrusting));
            wasThrusting = thrusting;
        }
        net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
        double vy = com.elysium.space.world.JetRules.vertical(mode, v.y, thrusting, player.onGround(), fuel);
        if (vy != v.y) {
            player.setDeltaMovement(v.x, vy, v.z);
        }
    }
}
