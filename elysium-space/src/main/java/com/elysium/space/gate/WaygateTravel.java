package com.elysium.space.gate;

import com.elysium.space.world.GateRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Going through.
 *
 * <h2>What is checked, and why it is checked again</h2>
 *
 * The client sent an address. Everything about that address is re-read here —
 * that the gate still exists, that its world is loaded, that its ring is still
 * closed, that the traveller is still standing at a gate of their own — because
 * a packet is a request and not a fact, and because a player can knock a block
 * out of a frame while a screen is open.
 *
 * <h2>Arriving</h2>
 *
 * Inside the far gate's opening, one block above its anchor, facing out of the
 * plane of the ring. The destination chunk is forced loaded for a moment
 * first: arriving in an unloaded chunk is how a player ends up inside the
 * ground on a world nobody has visited for a month.
 */
public final class WaygateTravel {

    private WaygateTravel() {
    }

    /**
     * Sends a traveller to the gate with this address.
     *
     * @return null on success, or the reason it did not happen
     */
    public static Component send(ServerPlayer traveller, String address) {
        ServerLevel from = traveller.serverLevel();
        WaygateNetwork network = WaygateNetwork.of(from);

        // Still standing at a gate of their own? Not a formality: without it,
        // a kept packet is a teleport from anywhere.
        BlockPos standing = nearestAnchor(from, traveller.blockPosition());
        if (standing == null) {
            return Component.translatable("elysiumspace.waygate.not_here");
        }
        if (!WaygateRing.read(from, standing).complete()) {
            return Component.translatable("elysiumspace.waygate.unbuilt");
        }

        WaygateNetwork.Gate going = null;
        for (WaygateNetwork.Gate gate : network.all()) {
            if (gate.key().equals(address)) {
                going = gate;
                break;
            }
        }
        if (going == null) {
            return Component.translatable("elysiumspace.waygate.gone");
        }

        ResourceKey<Level> world = WaygateNetwork.worldOf(going.dimension());
        ServerLevel there = traveller.getServer().getLevel(world);
        if (there == null) {
            return Component.translatable("elysiumspace.waygate.no_world",
                    going.dimension().toString());
        }

        BlockPos anchor = going.anchor();
        // Hold the far chunk open for a moment. Without this the teleport can
        // land in a chunk that is still being generated, and the traveller
        // arrives inside the ground.
        there.getChunkSource().addRegionTicket(
                net.minecraft.server.level.TicketType.POST_TELEPORT,
                new net.minecraft.world.level.ChunkPos(anchor), 3,
                traveller.getId());

        if (!WaygateRing.read(there, anchor).complete()) {
            network.forget(going.dimension(), anchor);
            return Component.translatable("elysiumspace.waygate.broken_there",
                    going.shown());
        }

        BlockPos landing = WaygateRing.arrival(anchor);
        float yaw = WaygateRing.facing(going.acrossX()).toYRot();

        from.playSound(null, standing, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 0.6F, 1.4F);
        traveller.teleportTo(there, landing.getX() + 0.5D, landing.getY(),
                landing.getZ() + 0.5D, Set.of(), yaw, 0.0F);
        // Falling is the one thing a gate should never do to somebody: a
        // traveller stepping through a doorway has not jumped off anything.
        traveller.resetFallDistance();
        there.playSound(null, anchor, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.BLOCKS, 0.6F, 1.2F);
        traveller.displayClientMessage(Component.translatable(
                "elysiumspace.waygate.arrived", going.shown()), true);
        return null;
    }

    /**
     * The anchor the traveller is standing at, if any.
     *
     * Within a couple of blocks of the gate they clicked, which is as far as
     * anybody gets between opening a screen and choosing from it.
     */
    private static BlockPos nearestAnchor(ServerLevel level, BlockPos at) {
        for (BlockPos near : BlockPos.betweenClosed(at.offset(-2, -2, -2),
                at.offset(2, 2, 2))) {
            if (level.getBlockState(near)
                    .is(com.elysium.space.registry.SpaceBlocks.WAYGATE_ANCHOR.get())) {
                return near.immutable();
            }
        }
        return null;
    }

    /** How far a traveller may have wandered. Read by nothing else; stated for the harness. */
    public static final int REACH = 2;

    /** The ticks a gate waits before answering again. */
    public static final int SETTLE = GateRules.SETTLE;
}
