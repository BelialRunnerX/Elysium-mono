package com.elysium.space.gate;

import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.world.GateRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a ring is finished, and which way it faces.
 *
 * <h2>Reading a shape out of the world</h2>
 *
 * {@link GateRules} says where the blocks go relative to the anchor, in an
 * abstract plane of "across" and "up". This is the part that knows what across
 * means in a world: a gate can lie in the east–west plane or the north–south
 * one, and a builder should not have to care which. So both are tried, and the
 * first that is complete is the gate.
 *
 * <h2>Why the answer includes what is missing</h2>
 *
 * Because "this is not a gate" is the least useful thing a block can say to
 * somebody who has just built one. A ring that is fifteen blocks and one gap
 * should say so, and say where.
 */
public final class WaygateRing {

    private WaygateRing() {
    }

    /**
     * What the world has to say about a ring.
     *
     * @param complete whether it is finished
     * @param acrossX  whether it lies across the X axis
     * @param missing  the frame blocks that are not there yet
     * @param blocked  the squares of the opening that are not clear
     */
    public record Reading(boolean complete, boolean acrossX, List<BlockPos> missing,
                          List<BlockPos> blocked) {
    }

    /**
     * Reads the ring around an anchor.
     *
     * Tries both planes and returns whichever is finished; when neither is, it
     * returns whichever is closer, so the message a builder gets is about the
     * ring they were actually building rather than the one they were not.
     */
    public static Reading read(LevelAccessor level, BlockPos anchor) {
        Reading acrossX = readPlane(level, anchor, true);
        if (acrossX.complete()) {
            return acrossX;
        }
        Reading acrossZ = readPlane(level, anchor, false);
        if (acrossZ.complete()) {
            return acrossZ;
        }
        int xWrong = acrossX.missing().size() + acrossX.blocked().size();
        int zWrong = acrossZ.missing().size() + acrossZ.blocked().size();
        return xWrong <= zWrong ? acrossX : acrossZ;
    }

    /** The same, for one plane only. */
    public static Reading readPlane(LevelAccessor level, BlockPos anchor,
                                    boolean acrossX) {
        List<BlockPos> missing = new ArrayList<>();
        List<BlockPos> blocked = new ArrayList<>();
        for (GateRules.Offset at : GateRules.frame()) {
            BlockPos where = place(anchor, at, acrossX);
            if (!level.getBlockState(where).is(SpaceBlocks.WAYGATE_FRAME.get())) {
                missing.add(where);
            }
        }
        for (GateRules.Offset at : GateRules.opening()) {
            BlockPos where = place(anchor, at, acrossX);
            if (!level.getBlockState(where).isAir()) {
                blocked.add(where);
            }
        }
        return new Reading(missing.isEmpty() && blocked.isEmpty(), acrossX,
                List.copyOf(missing), List.copyOf(blocked));
    }

    /** Where one square of the ring stands in the world. */
    public static BlockPos place(BlockPos anchor, GateRules.Offset at, boolean acrossX) {
        return acrossX
                ? anchor.offset(at.across(), at.up(), 0)
                : anchor.offset(0, at.up(), at.across());
    }

    /** Where a traveller stands on arrival: inside the opening, above the anchor. */
    public static BlockPos arrival(BlockPos anchor) {
        return anchor.above(GateRules.ARRIVAL_UP);
    }

    /**
     * Which way a traveller faces on arrival: out of the gate's plane.
     *
     * Either of the two directions perpendicular to the ring would do; the one
     * chosen is the one that leaves the gate behind you, so a player arrives
     * looking at whatever they have built rather than at their own frame.
     */
    public static Direction facing(boolean acrossX) {
        return acrossX ? Direction.SOUTH : Direction.EAST;
    }
}
