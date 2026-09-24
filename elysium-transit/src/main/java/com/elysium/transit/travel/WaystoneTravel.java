package com.elysium.transit.travel;

import com.elysium.transit.block.WaystoneBlock;
import com.elysium.transit.network.TransitPayloads;
import com.elysium.transit.registry.TransitAttachments;
import com.elysium.transit.registry.TransitBlocks;
import com.elysium.transit.rules.TransitRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sending somebody somewhere, and deciding whether they may go.
 *
 * <h2>Everything is decided here and nothing is decided by the packet</h2>
 *
 * The client is sent a list of rows with prices on them so it can draw a menu.
 * What comes back is <em>which row</em>, and every single thing that made that
 * row legal is worked out again on this side: that the player is standing at a
 * waystone, that it is a waystone, that they have bound the place they are
 * asking for, and that the stone can pay. A menu is a convenience; it is never
 * an authority.
 */
public final class WaystoneTravel {

    private WaystoneTravel() {
    }

    /** How far from a waystone a player may be and still use it. */
    public static final double WITHIN = 8.0;

    /** Where a block is, in the vocabulary the price is written in. */
    public static TransitRules.Waypoint placeOf(Level level, BlockPos pos) {
        return new TransitRules.Waypoint(level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    /** What the waystone at a position is called. */
    public static String nameOf(Level level, BlockPos pos) {
        return level.getBlockEntity(pos)
                instanceof WaystoneBlock.WaystoneBlockEntity waystone
                ? waystone.name(level, pos)
                : pos.getX() + ", " + pos.getZ();
    }

    /**
     * Build the menu and send it.
     *
     * <p>Everywhere the player has bound except the stone they are standing
     * at, each with what it would cost from here and — when that is more than
     * the stone is holding — how long until it is not.
     */
    public static void open(ServerPlayer player, BlockPos pos,
                            WaystoneBlock.WaystoneBlockEntity waystone) {
        TransitRules.Waypoint here = placeOf(player.level(), pos);
        List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> worlds = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        List<Integer> waits = new ArrayList<>();

        for (String entry : TransitAttachments.known(player)) {
            TransitRules.Waypoint there = TransitAttachments.placeOf(entry);
            if (there == null || there.pack().equals(here.pack())) {
                continue;
            }
            keys.add(there.pack());
            names.add(TransitAttachments.nameOf(entry));
            worlds.add(there.world());
            prices.add((int) Math.ceil(TransitRules.cost(here, there)));
            waits.add(TransitRules.readyIn(waystone.held(), waystone.share(), here, there));
        }
        PacketDistributor.sendToPlayer(player, new TransitPayloads.OpenWaystones(
                keys, names, worlds, prices, waits));
    }

    /**
     * Go, or say why not.
     *
     * @return what to tell the player, or null when they have gone
     */
    @Nullable
    public static Component jump(ServerPlayer player, BlockPos from, String key) {
        Level level = player.level();
        if (player.blockPosition().distSqr(from) > WITHIN * WITHIN) {
            return Component.translatable("elysiumtransit.waystone.far");
        }
        if (!level.getBlockState(from).is(TransitBlocks.WAYSTONE.get())
                || !(level.getBlockEntity(from)
                        instanceof WaystoneBlock.WaystoneBlockEntity waystone)) {
            return Component.translatable("elysiumtransit.waystone.gone");
        }
        TransitRules.Waypoint here = placeOf(level, from);
        TransitRules.Waypoint there = remembered(player, key);
        if (there == null) {
            // Asking for somewhere they have never bound. The menu could not
            // have offered it, so something else sent this.
            return Component.translatable("elysiumtransit.waystone.unknown");
        }
        ServerLevel destination = player.getServer() == null ? null
                : player.getServer().getLevel(ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(there.world())));
        if (destination == null) {
            return Component.translatable("elysiumtransit.waystone.noworld",
                    there.world());
        }
        BlockPos landing = new BlockPos(there.x(), there.y(), there.z());
        if (!destination.getBlockState(landing).is(TransitBlocks.WAYSTONE.get())) {
            // The far stone has been mined. Forget it rather than sending
            // somebody into a wall, and say so.
            TransitAttachments.forget(player, there);
            return Component.translatable("elysiumtransit.waystone.vanished");
        }
        float price = TransitRules.cost(here, there);
        if (!waystone.spend(price)) {
            int wait = TransitRules.readyIn(waystone.held(), waystone.share(), here, there);
            return Component.translatable("elysiumtransit.waystone.poor",
                    String.format("%.0f", price), String.format("%.0f", waystone.held()),
                    String.valueOf(Math.max(1, wait / 20)));
        }
        player.teleportTo(destination, landing.getX() + 0.5, landing.getY() + 1.0,
                landing.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        return null;
    }

    /** The waypoint behind a key, only if this player has bound it. */
    @Nullable
    private static TransitRules.Waypoint remembered(ServerPlayer player, String key) {
        for (String entry : TransitAttachments.known(player)) {
            TransitRules.Waypoint there = TransitAttachments.placeOf(entry);
            if (there != null && there.pack().equals(key)) {
                return there;
            }
        }
        return null;
    }
}
