package com.elysium.space.event;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.item.JetSuitItem;
import com.elysium.space.world.JetRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's half of the jet suit. Movement is the client's (players move
 * themselves in Minecraft); the server burns the fuel and cancels fall damage
 * while the suit holds the wearer up. It learns whether they are thrusting from
 * {@code JetThrust}, and burns nothing it cannot see paid for.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID)
public final class JetEvents {

    private JetEvents() {
    }

    private static final Map<UUID, Boolean> THRUSTING = new ConcurrentHashMap<>();

    public static void setThrusting(Player player, boolean thrusting) {
        if (thrusting) {
            THRUSTING.put(player.getUUID(), true);
        } else {
            THRUSTING.remove(player.getUUID());
        }
    }

    public static boolean thrusting(Player player) {
        return THRUSTING.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        ItemStack jet = JetSuitItem.worn(player);
        if (jet.isEmpty()) {
            THRUSTING.remove(player.getUUID());
            return;
        }
        JetRules.Mode mode = JetSuitItem.mode(jet);
        boolean thrust = thrusting(player);
        int fuel = JetSuitItem.fuel(jet);
        int burn = player.isCreative() ? 0 : JetRules.burn(mode, thrust, player.onGround(), fuel, player.tickCount);
        if (burn > 0) {
            JetSuitItem.setFuel(jet, fuel - burn);
        }
        if (JetRules.flying(mode, thrust, player.onGround(), fuel)) {
            player.resetFallDistance();
        }
    }
}
