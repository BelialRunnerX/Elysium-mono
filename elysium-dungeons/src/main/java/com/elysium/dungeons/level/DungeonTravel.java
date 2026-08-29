package com.elysium.dungeons.level;

import com.elysium.dungeons.ElysiumDungeons;
import com.elysium.dungeons.block.RiftPortal;
import com.elysium.dungeons.room.DungeonBuilder;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.entity.ElysiumScaling;
import com.elysium.dungeons.room.DungeonLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stepping in, and stepping back out.
 *
 * <h2>The rule this file exists to enforce</h2>
 *
 * <blockquote>
 * A portal leads to the dungeon it is already connected to, if anyone is still
 * inside it. Otherwise it builds a new one.
 * </blockquote>
 *
 * Everything else here is in service of that being true in the awkward cases:
 * two players stepping through together, a player who logs out in a boss room,
 * a player who dies, and the moment between the last player leaving and the
 * next one arriving.
 *
 * <h2>The cooldown</h2>
 *
 * A portal block is a block you stand in, so {@code entityInside} fires every
 * tick you are touching it — twenty times a second. Without a cooldown a player
 * would be teleported, arrive standing in the return portal, be teleported
 * back, and bounce between dimensions until they moved. So a player who has
 * just travelled is ignored for {@value #COOLDOWN_TICKS} ticks, which is long
 * enough to walk clear of the portal they arrived in.
 *
 * The cooldown is per player and kept in memory, not saved: a player who logs
 * out mid-cooldown and back in is not standing in a portal any more, and if
 * they are, one extra trip is the correct outcome.
 */
public final class DungeonTravel {

    private DungeonTravel() {
    }

    /** Ticks a player is ignored by portals after using one. */
    public static final int COOLDOWN_TICKS = 60;

    /** Rooms in a dungeon, and how many of them hold loot. */
    private static final int ROOM_COUNT = 12;
    private static final int LOOT_ROOMS = 2;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    /**
     * Called when a player is standing in a portal block.
     *
     * Decides which direction they are going by which dimension they are in,
     * which is the only fact that cannot be wrong — a portal block in the
     * dungeon is always a way out, and one anywhere else is always a way in.
     */
    public static void use(ServerPlayer player, BlockPos portalPos) {
        if (onCooldown(player)) {
            return;
        }
        if (player.level().dimension().equals(ElysiumDungeons.DUNGEON_LEVEL)) {
            leave(player);
        } else {
            enter(player, portalPos);
        }
    }

    // ------------------------------------------------------------------
    // In
    // ------------------------------------------------------------------

    private static void enter(ServerPlayer player, BlockPos portalPos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel dungeonLevel = server.getLevel(ElysiumDungeons.DUNGEON_LEVEL);
        if (dungeonLevel == null) {
            // The dimension is declared in this mod's data. If it is missing,
            // a datapack has removed it or the jar is incomplete - say so
            // rather than dropping the player through a portal to nowhere.
            ElysiumDungeons.LOGGER.error(
                    "The dungeon dimension is not loaded; a datapack may have removed "
                            + "elysiumdungeons:dungeon. Nobody can enter a dungeon.");
            player.displayClientMessage(
                    Component.translatable("elysiumdungeons.message.no_dimension")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        // The anchor identifies the portal for the rest of its life. Recomputed
        // from the frame rather than taken from the block the player happened
        // to touch - see RiftPortal for why that distinction is load-bearing.
        RiftPortal.Frame frame = RiftPortal.findFrame(player.level(), portalPos);
        BlockPos anchor = frame != null ? frame.anchor() : portalPos;

        DungeonInstances instances = DungeonInstances.get(server);
        DungeonInstances.Allocation allocation = instances.acquire(
                server, anchor, player.level().dimension(), portalPos.above());

        DungeonInstance instance = allocation.instance();
        BlockPos arrival;

        // The layout is rolled from the instance seed either way. It is a pure
        // function of that seed, so a player joining computes exactly the same
        // dungeon the first player got - which is what makes storing only the
        // seed enough, and why the seed must never come from the clock.
        DungeonLayout layout = DungeonLayout.generate(
                instance.getSeed(), ROOM_COUNT, LOOT_ROOMS);

        if (allocation.freshlyAllocated()) {
            // Work out what the dungeon is for before building it. This is the
            // only moment the answer is knowable: the builder runs with the
            // player still standing at the portal in another dimension, so
            // anything downstream that asked "who is nearby" got nobody and
            // built for level 1 - which is what every dungeon in the mod was.
            instance.setPartyLevel(
                    ElysiumScaling.levelFor(player, ElysiumFaction.EMPIRE));

            // A fresh instance is a cell of empty void until this runs.
            arrival = DungeonBuilder.build(dungeonLevel, instance, layout);
            instances.setDirty();

            // Retired instances are only bookkeeping, but there is no reason to
            // keep an unbounded pile of it. Swept here rather than on a timer
            // because this is the only moment new ones are created.
            instances.sweep(64);
        } else {
            // Joining a dungeon that is already standing. Work out where the
            // entrance is and go there - emphatically WITHOUT building, which
            // would rewrite every block and so refill the chests, respawn the
            // boss and undo whatever the people already inside had done.
            arrival = DungeonBuilder.entrancePos(instance.getOrigin(), layout);
        }

        instances.enter(instance, player.getUUID());
        teleport(player, dungeonLevel, arrival);

        player.displayClientMessage(
                Component.translatable(allocation.freshlyAllocated()
                                ? "elysiumdungeons.message.entered_new"
                                : "elysiumdungeons.message.entered_joined")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ------------------------------------------------------------------
    // Out
    // ------------------------------------------------------------------

    private static void leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DungeonInstances instances = DungeonInstances.get(server);
        DungeonInstance instance = instances.instanceAt(player.blockPosition());
        if (instance == null) {
            // Standing in a portal in the dungeon dimension but not inside any
            // known dungeon. Rather than leave them stranded in a void world,
            // put them at the overworld spawn.
            ElysiumDungeons.LOGGER.warn("{} used a rift at {} that belongs to no known dungeon; "
                    + "returning them to spawn", player.getGameProfile().getName(),
                    player.blockPosition());
            ServerLevel overworld = server.overworld();
            teleport(player, overworld, overworld.getSharedSpawnPos());
            return;
        }
        exitTo(player, server, instance);
    }

    /**
     * Puts a player back where they came in, and retires the dungeon if they
     * were the last one out.
     *
     * Shared by walking out, dying inside, and logging out inside, because all
     * three are the same event as far as the reroll rule is concerned: one
     * fewer person in the dungeon.
     */
    public static void exitTo(ServerPlayer player, MinecraftServer server,
                              DungeonInstance instance) {
        ServerLevel destination = server.getLevel(instance.getReturnDimension());
        if (destination == null) {
            destination = server.overworld();
        }
        boolean retired = instances(server).leave(instance, player.getUUID());
        teleport(player, destination, instance.getReturnPos());

        if (retired) {
            player.displayClientMessage(
                    Component.translatable("elysiumdungeons.message.collapsed")
                            .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** Records a player leaving without moving them — for death and logout. */
    public static void recordDeparture(MinecraftServer server, UUID player, BlockPos where) {
        DungeonInstances instances = instances(server);
        DungeonInstance instance = instances.instanceAt(where);
        if (instance != null) {
            instances.leave(instance, player);
        }
    }

    private static DungeonInstances instances(MinecraftServer server) {
        return DungeonInstances.get(server);
    }

    // ------------------------------------------------------------------

    private static void teleport(ServerPlayer player, ServerLevel destination, BlockPos pos) {
        cooldowns.put(player.getUUID(), COOLDOWN_TICKS);
        player.teleportTo(destination,
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();
    }

    private static boolean onCooldown(ServerPlayer player) {
        Integer remaining = cooldowns.get(player.getUUID());
        return remaining != null && remaining > 0;
    }

    /** Ticks every cooldown down. Called once a tick by the event handler. */
    public static void tickCooldowns() {
        cooldowns.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });
    }

    /** A seeded source for anything that needs one at travel time. */
    public static RandomSource randomFor(DungeonInstance instance, String label) {
        return RandomSource.create(DungeonSeed.derive(instance.getSeed(), label, 0, 0));
    }
}
