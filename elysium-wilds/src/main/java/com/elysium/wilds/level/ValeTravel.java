package com.elysium.wilds.level;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.block.ValePortal;
import com.elysium.wilds.rules.PortalRules;
import com.elysium.wilds.rules.ValeRules;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stepping into the Vale, and back out of it.
 *
 * <h2>In</h2>
 *
 * A gate anywhere but the Vale is a way in. Its ring is known by its anchor,
 * and the Vale side is filed under that anchor: the first trip looks for the
 * verdant vale nearest the ring (the gentlest crossing, where the bestiary
 * counts its rings from), builds a ring there and files it; every trip after
 * goes to the same ring, rebuilt if somebody broke it. The player is put down
 * beside it on the far side from where they walked in, facing on, so walking
 * forward is walking away from it.
 *
 * <h2>Out</h2>
 *
 * A gate in the Vale is a way out, whoever built it. It puts a player back in
 * front of the ring they came in through, on the side they came from, facing
 * away from it. Somebody who reached the Vale some other way, and so has no way
 * in on record, is put at the world's spawn, as the rift does.
 *
 * <h2>The cooldown</h2>
 *
 * A gate is asked every tick a player touches it, so a player who has just
 * travelled is ignored by every gate for {@link PortalRules#COOLDOWN_TICKS}
 * ticks. Kept in memory as the rift's is: a player who logs out mid-cooldown
 * is not standing in a gate when they come back.
 */
public final class ValeTravel {

    private ValeTravel() {
    }

    /** The Vale. Declared in data/elysiumwilds/dimension/vale.json. */
    public static final ResourceKey<Level> VALE = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, ValeRules.VALE));

    /** The server tick each player may use a gate again. */
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    /**
     * Called while a player stands in a gate. Which way they are going is read
     * off the world they are in, the one fact that cannot be wrong: a gate in
     * the Vale always leads out and one anywhere else always leads in.
     */
    public static void use(ServerPlayer player, BlockPos gatePos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (onCooldown(player, server)) {
            return;
        }
        if (player.level().dimension().equals(VALE)) {
            leave(player, server);
        } else {
            enter(player, server, gatePos);
        }
    }

    // ------------------------------------------------------------------
    // In
    // ------------------------------------------------------------------

    private static void enter(ServerPlayer player, MinecraftServer server, BlockPos gatePos) {
        ServerLevel vale = server.getLevel(VALE);
        if (vale == null) {
            // Declared in this module's data. Missing means a datapack took it
            // out: say so, rather than let a gate lead nowhere in silence.
            ElysiumWilds.LOGGER.error("The Vale is not loaded; a datapack may have removed "
                    + "elysiumwilds:vale. Nobody can go through a Vale gate.");
            player.displayClientMessage(Component.translatable("elysiumwilds.gate.no_vale")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel here = player.serverLevel();
        // The ring is found again from the gate rather than taken from the
        // block touched: the anchor is what its Vale side is filed under.
        ValePortal.Frame frame = ValePortal.findFrame(here, gatePos);
        BlockPos anchor = frame != null ? frame.anchor() : gatePos;
        Direction.Axis axis = frame != null ? frame.axis() : Direction.Axis.X;

        int side = PortalRules.side(past(player, anchor, axis));
        ValeLinks links = ValeLinks.get(server);
        links.remember(player.getUUID(), new ValeLinks.Return(here.dimension(),
                back(player, anchor, axis, side), facing(axis, side)));

        ValeLinks.Arrival arrival = links.arrival(here.dimension(), anchor);
        if (arrival == null) {
            // This ring's first trip, and its only search.
            BlockPos column = nearestVerdant(vale, anchor);
            arrival = settle(vale, column != null ? column : anchor, axis);
            links.link(here.dimension(), anchor, arrival);
        }
        if (!ValePortal.stands(vale, arrival.anchor())) {
            ValePortal.build(vale, arrival.anchor(), arrival.axis());
        }
        int onward = -side;
        BlockPos landing = arrival.anchor().relative(ValePortal.out(arrival.axis()),
                onward * PortalRules.LANDING_OUT);
        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
        // Said from where they stand rather than from how the search went, so
        // a ring whose first trip found no verdant vale says so every time.
        boolean gentle = vale.getBiome(landing).is(gentlest());
        player.displayClientMessage(Component.translatable(gentle
                        ? "elysiumwilds.gate.arrived" : "elysiumwilds.gate.arrived_far")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** The crossing a way in looks for. */
    private static ResourceKey<net.minecraft.world.level.biome.Biome> gentlest() {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(
                ElysiumWilds.MODID, PortalRules.arrivalBiome()));
    }

    /**
     * The verdant vale nearest a ring, or null.
     *
     * <p>The compass's own search, at its ceiling and on its grid, so one trip
     * costs what one search does, and at one height, the middle of the world's:
     * every crossing of the Vale claims the same depth, so a column is one biome
     * from top to bottom and a second height would only find the first again.
     * A height outside the world would find nothing at all, which is why it is
     * not the height of the ring the player walked through.
     */
    private static BlockPos nearestVerdant(ServerLevel vale, BlockPos from) {
        ResourceKey<net.minecraft.world.level.biome.Biome> wanted = gentlest();
        int radius = (int) PortalRules.ARRIVAL_SEARCH_BLOCKS;
        int step = (int) Math.round(PortalRules.arrivalStep());
        int middle = vale.getMinBuildHeight() + vale.getHeight() / 2;
        Pair<BlockPos, ?> hit = vale.findClosestBiome3d(holder -> holder.is(wanted),
                new BlockPos(from.getX(), middle, from.getZ()), radius, step, vale.getHeight());
        return hit == null ? null : hit.getFirst();
    }

    /**
     * Where a ring's Vale side goes: by a ring already standing there if there
     * is one, since building over it would break it, and otherwise on the
     * ground at the column, low enough that the lintel is inside the world.
     */
    private static ValeLinks.Arrival settle(ServerLevel vale, BlockPos column, Direction.Axis axis) {
        vale.getChunk(column.getX() >> 4, column.getZ() >> 4);     // generate it, so the heightmap is real
        BlockPos ground = vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos standing = ValePortal.near(vale, ground);
        if (standing != null) {
            ValePortal.Frame there = ValePortal.findFrame(vale, standing);
            if (there != null) {
                return new ValeLinks.Arrival(there.anchor(), there.axis());
            }
        }
        int y = Math.max(vale.getMinBuildHeight() + 1,
                Math.min(ground.getY(), vale.getMaxBuildHeight() - PortalRules.LEAST_UP - 2));
        return new ValeLinks.Arrival(new BlockPos(column.getX(), y, column.getZ()), axis);
    }

    // ------------------------------------------------------------------
    // Out
    // ------------------------------------------------------------------

    private static void leave(ServerPlayer player, MinecraftServer server) {
        ValeLinks.Return back = ValeLinks.get(server).returnFor(player.getUUID());
        ServerLevel home = back == null ? null : server.getLevel(back.dimension());
        if (back == null || home == null || home.dimension().equals(VALE)) {
            ServerLevel overworld = server.overworld();
            ElysiumWilds.LOGGER.info("{} left the Vale by a gate with no way in on record; "
                    + "putting them at the world's spawn", player.getGameProfile().getName());
            player.displayClientMessage(Component.translatable("elysiumwilds.gate.no_way_back")
                    .withStyle(ChatFormatting.GOLD), true);
            teleport(player, server, overworld,
                    Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot());
            return;
        }
        teleport(player, server, home, back.where(), back.facing());
        player.displayClientMessage(Component.translatable("elysiumwilds.gate.home")
                .withStyle(ChatFormatting.GREEN), true);
    }

    // ------------------------------------------------------------------

    /** How far past the low edge of the ring plane's own block a player stands. */
    private static double past(ServerPlayer player, BlockPos anchor, Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? player.getZ() - anchor.getZ()
                : player.getX() - anchor.getX();
    }

    /**
     * Where to put a player back: where they stood as they walked in, moved out
     * of the ring's plane to the middle of the block in front of it on their
     * side.
     */
    private static Vec3 back(ServerPlayer player, BlockPos anchor, Direction.Axis axis, int side) {
        double out = (axis == Direction.Axis.X ? anchor.getZ() : anchor.getX())
                + 0.5D + side * PortalRules.LANDING_OUT;
        return axis == Direction.Axis.X
                ? new Vec3(player.getX(), player.getY(), out)
                : new Vec3(out, player.getY(), player.getZ());
    }

    /** Which way faces away from a ring, on one side of it. */
    private static float facing(Direction.Axis axis, int side) {
        Direction away = axis == Direction.Axis.X
                ? (side > 0 ? Direction.SOUTH : Direction.NORTH)
                : (side > 0 ? Direction.EAST : Direction.WEST);
        return away.toYRot();
    }

    private static void teleport(ServerPlayer player, MinecraftServer server, ServerLevel destination,
                                 Vec3 where, float yaw) {
        cooldowns.put(player.getUUID(), server.getTickCount() + PortalRules.COOLDOWN_TICKS);
        player.teleportTo(destination, where.x, where.y, where.z, yaw, player.getXRot());
        player.resetFallDistance();
    }

    /**
     * Whether a player is still being ignored by gates.
     *
     * <p>An entry further off than one cooldown is from an earlier run of the
     * server rather than from this one: in a single-player game the tick count
     * starts again from nought with every world loaded, and a stale entry would
     * otherwise shut a player out of every gate for as long as the last world
     * had been running.
     */
    private static boolean onCooldown(ServerPlayer player, MinecraftServer server) {
        Integer until = cooldowns.get(player.getUUID());
        if (until == null) {
            return false;
        }
        int left = until - server.getTickCount();
        if (left <= 0 || left > PortalRules.COOLDOWN_TICKS) {
            cooldowns.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
