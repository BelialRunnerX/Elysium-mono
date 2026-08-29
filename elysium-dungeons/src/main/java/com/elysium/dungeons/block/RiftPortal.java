package com.elysium.dungeons.block;

import com.elysium.dungeons.ElysiumDungeons;
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
 * Finding, lighting and closing a rift.
 *
 * <h2>What counts as a frame</h2>
 *
 * The same shape a Nether portal uses, and for the same reason: everybody
 * already knows it. A rectangle of Rift Frame blocks with an empty interior
 * between {@value #MIN_INNER_WIDTH}×{@value #MIN_INNER_HEIGHT} and
 * {@value #MAX_INNER}×{@value #MAX_INNER}.
 *
 * <h2>The anchor, and why a portal needs one</h2>
 *
 * A rift is identified for its whole life by one block: the lowest, then
 * northmost, then westmost block of its interior. That is the
 * {@code portalAnchor} an instance is filed under, so a party stepping through
 * arrives together and a returning player is told their dungeon was rebuilt.
 *
 * It must be the <b>same</b> block every time it is computed, from any block of
 * the portal, which is why it is a deterministic corner rather than "whichever
 * block you touched". Using the touched block would file one physical portal
 * under as many identities as it has blocks, and every single entry would look
 * like a first visit — the dungeon would reroll while you were still in it.
 */
public final class RiftPortal {

    private RiftPortal() {
    }

    public static final int MIN_INNER_WIDTH = 2;
    public static final int MIN_INNER_HEIGHT = 3;
    public static final int MAX_INNER = 21;

    /** A lit portal: where its blocks are and which block identifies it. */
    public record Frame(List<BlockPos> interior, BlockPos anchor, Direction.Axis axis) {
    }

    /**
     * Looks for an unlit frame around a struck block.
     *
     * Tries both axes because a frame can face either way and the player struck
     * a frame block, which belongs to both candidate rectangles.
     */
    public static Frame findFrame(LevelAccessor level, BlockPos struck) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Frame frame = findOnAxis(level, struck, axis);
            if (frame != null) {
                return frame;
            }
        }
        return null;
    }

    private static Frame findOnAxis(LevelAccessor level, BlockPos struck, Direction.Axis axis) {
        // Step from the struck block into the empty space it encloses, then
        // flood that space and check every edge of what was found is frame.
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == axis) {
                continue;
            }
            BlockPos inside = struck.relative(direction);
            if (!isEmpty(level, inside)) {
                continue;
            }
            Frame frame = floodInterior(level, inside, axis);
            if (frame != null) {
                return frame;
            }
        }
        return null;
    }

    /**
     * Flood-fills the empty space and confirms it is a sealed rectangle of
     * frame blocks.
     *
     * Flooding rather than measuring a rectangle, because a flood naturally
     * rejects an L-shape, a gap in the frame, or a hole in the middle — all of
     * which a width-and-height measurement happily accepts and then produces a
     * portal with a hole in it.
     */
    private static Frame floodInterior(LevelAccessor level, BlockPos start,
                                       Direction.Axis axis) {
        Set<BlockPos> interior = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        interior.add(start);

        Direction[] inPlane = axis == Direction.Axis.X
                ? new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};

        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (interior.size() > MAX_INNER * MAX_INNER) {
                return null;     // too big, or not enclosed at all
            }
            for (Direction direction : inPlane) {
                BlockPos next = at.relative(direction);
                if (isFrame(level, next)) {
                    continue;
                }
                if (!isEmpty(level, next)) {
                    return null;   // something that is neither frame nor air: not a frame
                }
                if (interior.add(next)) {
                    queue.add(next);
                }
            }
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : interior) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int width = axis == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        if (width < MIN_INNER_WIDTH || height < MIN_INNER_HEIGHT
                || width > MAX_INNER || height > MAX_INNER) {
            return null;
        }
        // A flood of a rectangle has exactly width * height cells. Anything
        // else means the space is enclosed but not rectangular.
        if (interior.size() != width * height) {
            return null;
        }

        List<BlockPos> blocks = new ArrayList<>(interior);
        // Lowest, then northmost, then westmost - a stable identity for this
        // portal, computed the same way from any block of it.
        blocks.sort((a, b) -> {
            if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            }
            if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            }
            return Integer.compare(a.getX(), b.getX());
        });
        return new Frame(blocks, blocks.get(0), axis);
    }

    /** Fills a found frame with portal blocks. */
    public static void light(LevelAccessor level, Frame frame) {
        BlockState portal = ElysiumDungeons.RIFT_PORTAL.get().defaultBlockState()
                .setValue(RiftPortalBlock.AXIS, frame.axis());
        for (BlockPos pos : frame.interior()) {
            level.setBlock(pos, portal, 2);
        }
    }

    /**
     * Removes every portal block connected to one.
     *
     * Called when a frame block is broken. Flooding from the broken block's
     * neighbours rather than recomputing the frame, because by the time this
     * runs the frame is already incomplete and would no longer be found.
     */
    public static void close(LevelAccessor level, BlockPos from) {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = from.relative(direction);
            if (isPortal(level, neighbour) && seen.add(neighbour)) {
                queue.add(neighbour);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (isPortal(level, next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private static boolean isFrame(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ElysiumDungeons.RIFT_FRAME.get());
    }

    private static boolean isPortal(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ElysiumDungeons.RIFT_PORTAL.get());
    }

    private static boolean isEmpty(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ElysiumDungeons.RIFT_PORTAL.get());
    }
}
