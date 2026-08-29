package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.network.ElysiumNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each client's copy of its own sheet current, and says nothing when
 * nothing has changed.
 *
 * <h2>Why a diff rather than a timer</h2>
 *
 * The obvious version sends the sheet every second. On a twenty-player server
 * that is twenty packets a second, forever, almost all of them identical to the
 * one before — for a HUD that only redraws when a number moves. So the packed
 * string is compared against the last one sent to that player, and the packet
 * goes out only on a difference. A character that is not gaining XP, spending
 * points or moving either meter costs nothing at all.
 *
 * Packing a sheet is a string build over twelve stats, which is why the compare
 * runs on an interval rather than every tick. A quarter-second is well under
 * the point where a meter looks laggy and well over the point where the packing
 * shows up in a profile.
 *
 * <h2>Why the map is keyed by UUID and cleared on logout</h2>
 *
 * Holding the {@link Player} itself would keep a disconnected player's entity
 * alive for as long as the server ran. Holding the string against a UUID leaks
 * a few dozen bytes per player instead — and even that is given back on logout,
 * which also guarantees a reconnecting player gets a full sheet on their first
 * tick rather than being diffed against the session they just left.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumSyncHandler {

    private ElysiumSyncHandler() {
    }

    /** How often the sheet is packed and compared. Five ticks is a quarter second. */
    private static final int CHECK_INTERVAL = 5;

    private static final Map<UUID, String> LAST_SENT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        String packed = CharacterSheet.pack(player);
        String previous = LAST_SENT.get(player.getUUID());
        if (packed.equals(previous)) {
            return;
        }
        LAST_SENT.put(player.getUUID(), packed);
        ElysiumNetwork.syncSheet(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    /**
     * Forgets a player's last-sent sheet, forcing the next tick to resend.
     *
     * Called after anything that replaces the player entity — a respawn, a
     * dimension change — because the new entity carries the same UUID but the
     * client's copy may have been thrown away with the old one. Sending a
     * duplicate sheet is free; failing to send a needed one leaves the HUD
     * showing the character the player had before they died.
     */
    public static void forget(Player player) {
        LAST_SENT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forget(event.getEntity());
    }
}
