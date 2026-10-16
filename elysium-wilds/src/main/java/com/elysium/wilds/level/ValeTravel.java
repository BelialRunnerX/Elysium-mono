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
import java.util.Locale;
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
 * counts its rings from), builds a ring on the nearest dry ground there, or
 * on the nearest shore when that ground is all sea, walked on inland until
 * the ring's sill and both landings are dry, and files it; every trip
 * after goes to the same ring, rebuilt if somebody broke it. The player is put
 * down beside it on the far side from where they walked in, facing on, so
 * walking forward is walking away from it.
 *
 * <h2>The first trip, in the log</h2>
 *
 * A ring's first trip says so in one INFO line of this module's logger, and no
 * other trip logs anything at INFO. The line is fixed, one space between each
 * word, so the live stages can read it whole (FIRST_TRIP):
 *
 * <pre>Vale first trip: player NAME ring DIMENSION X Y Z side X Y Z axis x|z built|standing</pre>
 *
 * The player by their profile name, the ring by the dimension it stands in and
 * its anchor, the Vale side by its anchor and axis, and last whether that side
 * was built new for this trip or was a ring already standing where the search
 * ended (the settling uses one it finds there rather than build over it). That
 * last word is the settling's own (16 Oct 2026, round 4): standing when the
 * side came from a lit gate the search found near where it went, whatever the
 * side's anchor cell holds, since a ring with its anchor cell broken by hand is
 * still found and used, and asking the anchor alone called it built. It is how
 * the live footing stage (tools/livetest/stages/stage_vale_footing_client.ps1)
 * knows a trip was a ring's first, and that the footing it asserts is one a
 * fresh search chose: nothing it could keep itself says that.
 *
 * <p>The same line is kept in the world, where no log line can write it: in
 * {@link ValeLinks}, the mod's own saved data, which counts the first trips the
 * world has had and keeps the line the latest of them logged, word for word.
 * The integrated server logs some text as it is, line breaks and all (a named
 * entity's death, a /say naming it), so a custom name can write a whole line of
 * its own into the client's log, this line included. The footing stage reads
 * the record before the gate and after the crossing, with {@code /elysium vale
 * trips} ({@link com.elysium.wilds.command.ValeCommand}), and takes the line
 * only when the count went up by one and the line kept is that line. Until 16
 * October 2026 (round 5, OP1) the record was command storage
 * elysiumwilds:trips, and the operator who can name a mob with line breaks
 * (only a command can) could set that to agree with {@code /data modify}; no
 * command writes saved data, and the one that reads it writes nothing.
 *
 * <h2>Out</h2>
 *
 * A gate in the Vale is a way out, whoever built it. It puts a player back in
 * front of the ring they came in through, on the side they came from, facing
 * away from it. Somebody who reached the Vale some other way, and so has no way
 * in on record, is put at the world's spawn, as the rift does; that is logged,
 * and counted for them in {@link ValeLinks}, so the live vale stage can tell
 * it from the way back even when the log rolled over before it read the line
 * (16 October 2026, round 5, RZ1): in a world whose spawn is by the ring, the
 * spawn is where the way back puts a player too.
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
     * The line a ring's first trip logs, and the only one (see the class
     * comment). The live stages match it word for word, so it is never to be
     * reworded without them.
     */
    private static final String FIRST_TRIP = "Vale first trip: player %s ring %s %d %d %d side %d %d %d axis %s %s";

    /**
     * A Vale side as the settling found it: where it goes, and whether it is a
     * ring the search found standing there (by a lit gate near where the side
     * went) rather than one this trip builds.
     */
    private record Settled(ValeLinks.Arrival arrival, boolean standing) {
    }

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
            Settled settled = settle(vale, column != null ? column : anchor, axis);
            arrival = settled.arrival();
            links.link(here.dimension(), anchor, arrival);
            // Said once, here, in the settling's own word: a side it found by a
            // lit gate near where it went is a ring already standing, even when
            // its anchor cell is empty and the build below lights it again.
            boolean standing = settled.standing();
            String said = String.format(Locale.ROOT, FIRST_TRIP, player.getGameProfile().getName(),
                    here.dimension().location(), anchor.getX(), anchor.getY(), anchor.getZ(),
                    arrival.anchor().getX(), arrival.anchor().getY(), arrival.anchor().getZ(),
                    arrival.axis().getName(), standing ? "standing" : "built");
            ElysiumWilds.LOGGER.info("{}", said);
            links.firstTrip(said);
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
     * is one, since building over it would break it; otherwise on the ground at
     * the nearest dry column, which is the column itself when it is dry; when
     * everything within the spiral's reach is water, by the nearest shore the
     * rules' eight compass lines meet, settled the same way; and on the column
     * anyway when they meet none; low enough that the lintel is inside the
     * world.
     *
     * <p>Dry, because the ground is the heightmap that stops at water, and the
     * search finds a biome rather than a block: the nearest verdant column,
     * when it was a lake, a river or the sea, had the ring built on the water
     * and the player landing in it (1 October 2026: the live stage arrived at
     * sea level in a bay). Far, because the Vale has no ocean biome, so its
     * seas are low ground of the land biomes, and a fresh ring's first trip
     * landed in one where every column within sixty-four was water but the
     * ring's own pads, the shore a hundred blocks off (1 October, late). And
     * settled on from the dry column either stage finds, because the first
     * dry column met is at the waterline: a fresh ring's first trip landed at
     * a shore with the ring astride it, its landing a block out over the
     * water and the player on a built pad with land a step east (2 October).
     * The spiral, the lines, the dryness test, the footprint and the walk
     * inland are the rules', and the harness proves them; what is the
     * world's is what stands at each column's top.
     */
    private static Settled settle(ServerLevel vale, BlockPos column, Direction.Axis axis) {
        Settled near = near(vale, column, axis);
        if (near != null) {
            return near;
        }
        // The spiral found only water. The shore the compass lines meet is
        // walked on along its line until the ring's footing is dry, then
        // settled as the column was: a ring standing by it is used, and the
        // side goes to the nearest dry column to it, which, dry by the same
        // test, is the shore itself after one look.
        PortalRules.Column far = PortalRules.nearestFarDry(footing(vale, column));
        if (far != null) {
            PortalRules.Column shore = PortalRules.settled(footing(vale, column), far, axisOf(axis));
            near = near(vale, column.offset(shore.east(), 0, shore.south()), axis);
            if (near != null) {
                return near;
            }
        }
        // Sea past the reach of both: a ring on the water is still a way home.
        return new Settled(on(vale, column, axis), false);
    }

    /**
     * The near stage at a column: a ring already standing by it; else the Vale
     * side at the nearest dry column within
     * {@link PortalRules#DRY_SEARCH_BLOCKS}, walked on inland until the
     * ring's sill and both landings are dry, by a ring already standing there
     * if there is one; null when everything within reach is water. It comes
     * first, and runs again at the shore the far stage finds, because it is
     * cheap and keeps a verdant landing verdant.
     */
    private static Settled near(ServerLevel vale, BlockPos column, Direction.Axis axis) {
        Settled standing = standingBy(vale, groundAt(vale, column));
        if (standing != null) {
            return standing;
        }
        PortalRules.Column dry = PortalRules.nearestDry(footing(vale, column));
        if (dry == null) {
            return null;
        }
        // The first dry column met is at the waterline: the ring goes on
        // along the line it was found on until its whole footing is dry.
        PortalRules.Column site = PortalRules.settled(footing(vale, column), dry, axisOf(axis));
        BlockPos spot = column.offset(site.east(), 0, site.south());
        // A ring standing by the settled column is used too, for the same reason.
        standing = standingBy(vale, groundAt(vale, spot));
        return standing != null ? standing : new Settled(on(vale, spot, axis), false);
    }

    /** Which way a ring runs, as the rules name it: the game's axis, read at the door. */
    private static PortalRules.Axis axisOf(Direction.Axis axis) {
        return axis == Direction.Axis.X ? PortalRules.Axis.X : PortalRules.Axis.Z;
    }

    /** The Vale side built on the ground at a spot, low enough that the lintel is inside the world. */
    private static ValeLinks.Arrival on(ServerLevel vale, BlockPos spot, Direction.Axis axis) {
        BlockPos ground = groundAt(vale, spot);
        int y = Math.max(vale.getMinBuildHeight() + 1,
                Math.min(ground.getY(), vale.getMaxBuildHeight() - PortalRules.LEAST_UP - 2));
        return new ValeLinks.Arrival(new BlockPos(spot.getX(), y, spot.getZ()), axis);
    }

    /**
     * The ring already standing by a spot, as the arrival it would be, or null.
     * Found by any lit gate near the spot, and its frame found again from that
     * gate, so it is standing even when its own anchor cell is empty (a gate
     * block broken by hand does not close a ring): the first-trip line says so.
     */
    private static Settled standingBy(ServerLevel vale, BlockPos ground) {
        BlockPos standing = ValePortal.near(vale, ground);
        if (standing == null) {
            return null;
        }
        ValePortal.Frame there = ValePortal.findFrame(vale, standing);
        return there == null ? null : new Settled(new ValeLinks.Arrival(there.anchor(), there.axis()), true);
    }

    /**
     * The first block above a column's surface, water and lava counted as
     * surface, which is what the ring's sill is set on. The chunk is generated
     * first, so the heightmap is real.
     */
    private static BlockPos groundAt(ServerLevel vale, BlockPos column) {
        vale.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    /**
     * The ground round a column, read for the rules one column at a time: what
     * stands at the top of the one so many blocks east and south of it.
     */
    private static PortalRules.Surface footing(ServerLevel vale, BlockPos column) {
        return (east, south) -> top(vale, column.offset(east, 0, south));
    }

    /**
     * What stands at a column's top, for the rules' dryness test: the block
     * its heightmap stopped under, whether a fluid stands in it and whether it
     * would hold a player up. Both, because the heightmap counts water as
     * ground, and counts a fence post, a bottom slab or a chest's lid as
     * ground too; a ring built on either puts a player down through it.
     */
    private static PortalRules.Footing top(ServerLevel vale, BlockPos column) {
        BlockPos under = groundAt(vale, column).below();
        return new PortalRules.Footing(wet(vale, under), firm(vale, under));
    }

    /** Whether a fluid stands in a block: water, or lava. */
    private static boolean wet(ServerLevel vale, BlockPos under) {
        return !vale.getFluidState(under).isEmpty();
    }

    /** Whether a block would hold a player up: its upper face is sturdy, as a block a player stands on is. */
    private static boolean firm(ServerLevel vale, BlockPos under) {
        return vale.getBlockState(under).isFaceSturdy(vale, under, Direction.UP);
    }

    // ------------------------------------------------------------------
    // Out
    // ------------------------------------------------------------------

    private static void leave(ServerPlayer player, MinecraftServer server) {
        ValeLinks links = ValeLinks.get(server);
        ValeLinks.Return back = links.returnFor(player.getUUID());
        ServerLevel home = back == null ? null : server.getLevel(back.dimension());
        if (back == null || home == null || home.dimension().equals(VALE)) {
            // Counted before anything else (see the class comment): the world's
            // own word that this way out was to the spawn, which no rollover of
            // the log can lose.
            links.outToSpawn(player.getUUID());
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
