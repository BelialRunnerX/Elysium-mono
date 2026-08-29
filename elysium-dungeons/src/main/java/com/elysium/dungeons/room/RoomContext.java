package com.elysium.dungeons.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * The room a decorator is standing in, and every way to put something in it.
 *
 * <h2>Coordinates are local, always</h2>
 *
 * Everything here is in room space: (0, 0, 0) is the inside corner of the
 * floor, and {@link #interior} is how big the inside is. A decorator never sees
 * a world coordinate.
 *
 * That is not tidiness. A dungeon is built at whatever cell the allocator
 * handed out — a different place every single time — so a room written against
 * world coordinates would work in the first dungeon and be wrong in every one
 * after it. Local coordinates make that class of bug impossible rather than
 * unlikely.
 *
 * <h2>Nothing here can escape the room</h2>
 *
 * {@link #set} silently ignores anything outside the interior. A decorator
 * cannot punch a hole in a wall, cannot leak into the next room, and cannot
 * write into the void — which matters because the dungeon is enclosed and a
 * single stray block breaks that promise for the whole run. Clamping rather
 * than throwing is deliberate: a decorator that reaches one block too far
 * should produce a slightly plainer room, not a crash in the middle of
 * generation with a player already waiting on a loading screen.
 */
public final class RoomContext {

    private final ServerLevel level;

    /** World position of the interior's (0,0,0). */
    private final BlockPos origin;

    /** Interior size, in blocks. */
    private final int width;
    private final int height;
    private final int depth;

    private final DungeonLayout.Cell cell;

    /**
     * The character level everything spawned in this room is built for.
     *
     * Carried down from the instance rather than worked out here, because the
     * only way to work it out here is to ask who is standing nearby - and the
     * whole dungeon is generated before its owner has been teleported into the
     * dimension, so the answer is always nobody and the level is always 1.
     */
    private final int mobLevel;

    public RoomContext(ServerLevel level, BlockPos origin,
                       int width, int height, int depth, DungeonLayout.Cell cell,
                       int mobLevel) {
        this.level = level;
        this.origin = origin;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cell = cell;
        this.mobLevel = mobLevel;
    }

    /** The level anything spawned here should be built for; 0 when unknown. */
    public int mobLevel() {
        return mobLevel;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    /** Sets one block, in room coordinates. Outside the interior does nothing. */
    public void set(int x, int y, int z, BlockState state) {
        if (!inside(x, y, z)) {
            return;
        }
        // Flag 2 is "send to clients, do not trigger neighbour updates". Block
        // updates during generation are the difference between a dungeon that
        // builds instantly and one that cascades - sand falls, water spreads,
        // redstone recalculates, all while the rest of the room is still being
        // written.
        level.setBlock(origin.offset(x, y, z), state, 2);
    }

    public void set(BlockPos local, BlockState state) {
        set(local.getX(), local.getY(), local.getZ(), state);
    }

    /** Fills a box, inclusive, in room coordinates. */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** A vertical column, for pillars. */
    public void column(int x, int z, int fromY, int toY, BlockState state) {
        fill(x, fromY, z, x, toY, z, state);
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    public ServerLevel level() {
        return level;
    }

    /** The world position of a room coordinate — for spawning, not for building. */
    public BlockPos world(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /** The middle of the floor, which is where most things want to go. */
    public BlockPos centre() {
        return new BlockPos(width / 2, 0, depth / 2);
    }

    /** Which walls have doorways in them. */
    public Set<Direction> doors() {
        return cell.doors;
    }

    public DungeonLayout.Cell cell() {
        return cell;
    }

    /**
     * A floor position that is not in a doorway and not against a wall.
     *
     * Decorators use this for anything free-standing. Keeping one block clear
     * of the walls is what stops a pillar or a chest from being placed in front
     * of a door, which in an enclosed dungeon can seal a room that the layout
     * believes is connected.
     */
    public BlockPos randomFloorPos(RandomSource random) {
        int x = 1 + random.nextInt(Math.max(1, width - 2));
        int z = 1 + random.nextInt(Math.max(1, depth - 2));
        return new BlockPos(x, 0, z);
    }

    /**
     * True when this spot would block a doorway.
     *
     * A door is a three-wide opening in the middle of a wall, and its approach
     * is the strip of floor in front of it. Anything solid there is a wall a
     * player cannot walk through, in a dungeon where every other route is
     * sealed.
     */
    public boolean blocksDoorway(int x, int z) {
        int midX = width / 2;
        int midZ = depth / 2;
        // Derived from the door, not chosen. One block of margin either side of
        // the opening, so a decorator cannot place a block a player has to
        // squeeze past. Written as a literal 2 until the doors got wider, at
        // which point it was silently a block and a half too narrow.
        int clear = DungeonBuilder.DOOR_HALF + 1;
        for (Direction door : doors()) {
            switch (door) {
                case NORTH -> {
                    if (z <= 1 && Math.abs(x - midX) <= clear) {
                        return true;
                    }
                }
                case SOUTH -> {
                    if (z >= depth - 2 && Math.abs(x - midX) <= clear) {
                        return true;
                    }
                }
                case WEST -> {
                    if (x <= 1 && Math.abs(z - midZ) <= clear) {
                        return true;
                    }
                }
                case EAST -> {
                    if (x >= width - 2 && Math.abs(z - midZ) <= clear) {
                        return true;
                    }
                }
                default -> {
                    // Up and down are not doors in a flat grid of rooms.
                }
            }
        }
        return false;
    }

    /** Places only where it will not seal a doorway. */
    public void setClear(int x, int y, int z, BlockState state) {
        if (blocksDoorway(x, z)) {
            return;
        }
        set(x, y, z, state);
    }
}
