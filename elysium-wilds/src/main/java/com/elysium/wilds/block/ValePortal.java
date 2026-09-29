package com.elysium.wilds.block;

import com.elysium.wilds.registry.WildBlocks;
import com.elysium.wilds.rules.PortalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Finding, lighting, closing and building a way into the Vale.
 *
 * <p>What counts as a ring is {@link PortalRules}', proved by its harness.
 * This file only says which blocks are frames and which are open, reads the
 * world into the rules' plane on each axis in turn, and turns the ring they
 * find back into blocks.
 */
public final class ValePortal {

    private ValePortal() {
    }

    /**
     * A ring in the world: the blocks of its opening, the block it is known
     * by, and which way it runs.
     */
    public record Frame(List<BlockPos> interior, BlockPos anchor, Direction.Axis axis) {
    }

    /**
     * The ring a used block belongs to, or null.
     *
     * <p>Both axes, because a frame belongs to a ring either way it could run.
     * A lit gate block says which way its own ring runs, so that axis is asked
     * first.
     */
    public static Frame findFrame(LevelAccessor level, BlockPos used) {
        BlockState state = level.getBlockState(used);
        Direction.Axis first = state.is(WildBlocks.VALE_PORTAL.get())
                ? state.getValue(ValePortalBlock.AXIS) : Direction.Axis.X;
        Direction.Axis second = first == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        for (Direction.Axis axis : new Direction.Axis[] {first, second}) {
            Direction along = across(axis);
            PortalRules.Ring ring = PortalRules.around(
                    (a, u) -> cell(level.getBlockState(used.relative(along, a).above(u))), 0, 0);
            if (ring == null) {
                continue;
            }
            List<BlockPos> interior = new ArrayList<>();
            for (PortalRules.Spot spot : ring.opening()) {
                interior.add(at(used, axis, spot));
            }
            return new Frame(List.copyOf(interior), at(used, axis, ring.anchor()), axis);
        }
        return null;
    }

    /** Which way "across" runs in a ring on this axis: along it. */
    public static Direction across(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }

    /** Which way "out" of a ring on this axis runs: through it. */
    public static Direction out(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    /** A cell of a ring's plane as a block, the plane's (0, 0) at the origin. */
    private static BlockPos at(BlockPos origin, Direction.Axis axis, PortalRules.Spot spot) {
        return origin.relative(across(axis), spot.across()).above(spot.up());
    }

    private static PortalRules.Cell cell(BlockState state) {
        if (state.is(WildBlocks.VALE_FRAME.get())) {
            return PortalRules.Cell.FRAME;
        }
        if (state.isAir() || state.is(WildBlocks.VALE_PORTAL.get())) {
            return PortalRules.Cell.OPEN;
        }
        return PortalRules.Cell.OTHER;
    }

    /** Fills a found ring with the gate. */
    public static void light(LevelAccessor level, Frame frame) {
        BlockState gate = WildBlocks.VALE_PORTAL.get().defaultBlockState()
                .setValue(ValePortalBlock.AXIS, frame.axis());
        for (BlockPos pos : frame.interior()) {
            level.setBlock(pos, gate, 2);
        }
    }

    /**
     * Takes away every gate block joined to one: called when a frame is broken.
     *
     * <p>Flooded from the broken frame's neighbours rather than by finding the
     * ring again, because by the time this runs the ring has a gap in it and
     * would not be found.
     */
    public static void close(LevelAccessor level, BlockPos from) {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = from.relative(direction);
            if (isGate(level, neighbour) && seen.add(neighbour)) {
                queue.add(neighbour);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (isGate(level, next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private static boolean isGate(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(WildBlocks.VALE_PORTAL.get());
    }

    /** Whether a lit gate still fills a ring's anchor. */
    public static boolean stands(LevelAccessor level, BlockPos anchor) {
        return isGate(level, anchor);
    }

    /**
     * A lit gate near a spot, or null. A new Vale side that would go this
     * close to a ring already standing uses that ring instead, since building
     * over it would break it.
     */
    public static BlockPos near(LevelAccessor level, BlockPos spot) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int up = -PortalRules.NEAR_UP; up <= PortalRules.NEAR_UP; up++) {
            for (int x = -PortalRules.NEAR_ACROSS; x <= PortalRules.NEAR_ACROSS; x++) {
                for (int z = -PortalRules.NEAR_ACROSS; z <= PortalRules.NEAR_ACROSS; z++) {
                    at.set(spot.getX() + x, spot.getY() + up, spot.getZ() + z);
                    if (isGate(level, at)) {
                        return at.immutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds the ring a player arrives beside: the smallest ring, corners and
     * all, its opening's low corner at the anchor, lit.
     *
     * <p>In front of it and behind it: room to stand, cleared up to the top of
     * the opening, and a floor of mossy stone brick wherever the ground will not
     * hold a player up, so an arrival over water stands on a landing rather than
     * in the sea. The gate goes in last, so nothing cleared afterwards can close
     * it. Nothing that holds things is ever replaced: a ring rebuilt where a
     * player has since put a chest leaves the chest, and a gap in the ring,
     * rather than their belongings gone.
     */
    public static void build(LevelAccessor level, BlockPos anchor, Direction.Axis axis) {
        Direction along = across(axis);
        Direction through = out(axis);
        BlockState floor = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int side : new int[] {-PortalRules.LANDING_OUT, PortalRules.LANDING_OUT}) {
            for (int a = -1; a <= PortalRules.LEAST_ACROSS; a++) {
                BlockPos under = anchor.relative(along, a).relative(through, side).below();
                if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
                    place(level, under, floor, 3);
                }
                if (a < 0 || a >= PortalRules.LEAST_ACROSS) {
                    continue;
                }
                for (int u = 0; u < PortalRules.LEAST_UP; u++) {
                    BlockPos room = anchor.relative(along, a).relative(through, side).above(u);
                    if (!level.getBlockState(room).isAir()) {
                        place(level, room, air, 3);
                    }
                }
            }
        }
        BlockState frame = WildBlocks.VALE_FRAME.get().defaultBlockState();
        for (PortalRules.Spot spot : PortalRules.builtFrames()) {
            place(level, at(anchor, axis, spot), frame, 3);
        }
        BlockState gate = WildBlocks.VALE_PORTAL.get().defaultBlockState()
                .setValue(ValePortalBlock.AXIS, axis);
        for (PortalRules.Spot spot : PortalRules.builtOpening()) {
            place(level, at(anchor, axis, spot), gate, 2);
        }
    }

    /** Sets a block, unless what is there holds something. */
    private static void place(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
        if (level.getBlockState(pos).hasBlockEntity()) {
            return;
        }
        level.setBlock(pos, state, flags);
    }
}
