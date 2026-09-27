package com.elysium.lib.run;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every run joined to a run, and every device face the runs touch, read off
 * the six link properties. Because the links already obey the dye rule, a
 * walk along them is a walk along what the player sees: a red cable's
 * network stops where the red stops.
 *
 * <p>The walk is bounded by {@link #MOST} runs so a loop of cable across a
 * whole base costs the same every tick as a short one.
 */
public final class RunNetwork {

    private RunNetwork() {
    }

    /** The most runs one walk visits before it stops counting. */
    public static final int MOST = 512;

    /** A device face a run touches: the device's position, and the face of it the run is against. */
    public record Device(BlockPos pos, Direction face) {
    }

    /** What a walk found: the runs, and the device faces, in the order they were met. */
    public record Found(List<BlockPos> runs, List<Device> devices, boolean cut) {
    }

    /** Whether the run at {@code from} is joined to a run through {@code face}, read from both sides. */
    public static boolean joined(LevelAccessor level, BlockPos from, Direction face) {
        BlockState here = level.getBlockState(from);
        if (!(here.getBlock() instanceof RunBlock) || here.getValue(RunSides.BY_SIDE.get(face)) != RunLink.RUN) {
            return false;
        }
        BlockState there = level.getBlockState(from.relative(face));
        return there.is(here.getBlock())
                && there.getValue(RunSides.BY_SIDE.get(face.getOpposite())) == RunLink.RUN;
    }

    /** Walks the network the run at {@code start} belongs to. Empty when there is no run there. */
    public static Found walk(LevelAccessor level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        List<BlockPos> runs = new ArrayList<>();
        List<Device> devices = new ArrayList<>();
        if (!(first.getBlock() instanceof RunBlock)) {
            return new Found(runs, devices, false);
        }
        Block kind = first.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            BlockState state = level.getBlockState(here);
            if (!state.is(kind)) {
                continue;
            }
            runs.add(here);
            for (Direction face : Direction.values()) {
                RunLink link = state.getValue(RunSides.BY_SIDE.get(face));
                BlockPos next = here.relative(face);
                if (link == RunLink.DEVICE) {
                    devices.add(new Device(next, face.getOpposite()));
                } else if (link == RunLink.RUN && joined(level, here, face) && seen.add(next.immutable())) {
                    if (seen.size() > MOST) {
                        return new Found(runs, devices, true);
                    }
                    queue.add(next.immutable());
                }
            }
        }
        return new Found(runs, devices, false);
    }
}
