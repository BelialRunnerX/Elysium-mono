package com.elysium.dungeons.room;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The footprint of a room, so that not every room is the same square.
 *
 * <h2>What this is for</h2>
 *
 * Every room was the interior of its cell: a 22-by-22 square, wall to wall.
 * Twelve of those in a row is a dungeon that reads as one room repeated, and no
 * amount of decoration fixes a silhouette — a player remembers the shape of a
 * space long after they have stopped looking at its walls.
 *
 * So a shape is a <b>mask</b> over the floor, and whatever the mask excludes is
 * filled back in as wall. The room decorators then run exactly as before, on
 * whatever floor is left.
 *
 * <h2>The danger, and the only honest way to handle it</h2>
 *
 * A dungeon of sealed rooms has no second route anywhere. Fill in the wrong
 * square and a doorway is behind a wall, the room beyond it is unreachable, and
 * the player is standing in a dead end that the layout is certain is connected.
 * {@link RoomContext#blocksDoorway} protects the tile in front of each door and
 * cannot protect the <em>path</em> from that tile to the rest of the room — a
 * mask can leave every doorway clear and still cut the room in half between
 * them.
 *
 * Reasoning carefully about each mask is not good enough, because the masks are
 * combined with four possible door arrangements and a random rotation. So every
 * mask is <b>checked before it is used</b>: flood fill the open floor from one
 * doorway, confirm every other doorway is in the same region, and fall back to
 * the plain square if it is not. The check is a 22-by-22 flood — cheaper than
 * one block placement — and it converts a class of bug that strands a player
 * behind a wall into a room that is merely less interesting than it could have
 * been.
 */
public enum RoomShape {

    /** Wall to wall. The shape every room used to be, and the fallback. */
    SQUARE,

    /** Corners cut back, so the room reads as a rotunda rather than a box. */
    OCTAGON,

    /** A cross: the middle of each wall reaches out, the corners are solid. */
    CROSS,

    /**
     * A square with a solid island in the middle — the floor is a ring.
     *
     * The one shape that changes how a fight goes rather than only how the room
     * looks: there is something to put between yourself and a Lictor.
     */
    RING,

    /** One corner squared off, so the room is an L. */
    ELL,

    /** The walls stepped in at intervals, leaving alcoves down two sides. */
    ALCOVES;

    /**
     * Rolls a shape for a room.
     *
     * Weighted heavily toward the square. A dungeon where every room is an
     * unusual shape has the same problem as one where none is — the shapes stop
     * being a signal and become the wallpaper. Roughly two rooms in five are
     * something other than a box.
     */
    public static RoomShape roll(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 60) {
            return SQUARE;
        }
        if (roll < 70) {
            return OCTAGON;
        }
        if (roll < 78) {
            return CROSS;
        }
        if (roll < 88) {
            return RING;
        }
        if (roll < 95) {
            return ELL;
        }
        return ALCOVES;
    }

    /**
     * Whether this tile is floor under this shape.
     *
     * @param quarter a rotation, 0-3, so an L or an alcove is not always in the
     *                same corner of every room that uses one
     */
    public boolean open(int x, int z, int w, int d, int quarter) {
        int rx = x;
        int rz = z;
        for (int i = 0; i < quarter; i++) {
            int nx = d - 1 - rz;
            rz = rx;
            rx = nx;
        }
        return openUnrotated(rx, rz, w, d);
    }

    private boolean openUnrotated(int x, int z, int w, int d) {
        int midX = (w - 1) / 2;
        int midZ = (d - 1) / 2;
        return switch (this) {
            case SQUARE -> true;
            // A diamond constraint wide enough that only the corners fail it.
            case OCTAGON -> Math.abs(x - midX) + Math.abs(z - midZ)
                    <= (w + d) / 2 - 5;
            // Arms four wide out of the middle of each wall.
            case CROSS -> Math.abs(x - midX) <= 4 || Math.abs(z - midZ) <= 4;
            // Everything but a solid block in the middle.
            case RING -> Math.abs(x - midX) > 3 || Math.abs(z - midZ) > 3;
            // A quarter filled in, leaving a generous L.
            case ELL -> !(x > midX + 2 && z > midZ + 2);
            // Every fourth column steps in from two opposite walls.
            case ALCOVES -> !((x % 4 == 0) && (z < 3 || z > d - 4));
        };
    }

    // ------------------------------------------------------------------

    /**
     * Fills in whatever the shape excludes, if the result is still connected.
     *
     * @return the shape actually applied, which is {@link #SQUARE} when the
     *         proposed one would have stranded a doorway
     */
    public static RoomShape apply(RoomContext context, RoomShape shape, int quarter) {
        boolean[][] open = mask(shape, quarter, context.width(), context.depth(),
                context::blocksDoorway);
        if (open == null) {
            return SQUARE;
        }

        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        for (int x = 0; x < context.width(); x++) {
            for (int z = 0; z < context.depth(); z++) {
                if (!open[x][z]) {
                    context.column(x, z, 0, context.height() - 1, wall);
                }
            }
        }
        return shape;
    }

    /**
     * Which tiles a shape leaves open, or null when it would strand a doorway.
     *
     * <b>Pure.</b> No level, no block states, no {@link RoomContext} — it takes
     * the room's size and a predicate saying which tiles are doorway approaches,
     * and that is the whole input. That is not tidiness: it is what lets the
     * layout harness run every shape against every door arrangement and every
     * rotation outside the game, which is the only way this gets checked at all.
     * The version of this that reached into a RoomContext could only ever be
     * tested by building a dungeon and walking round it.
     */
    public static boolean[][] mask(RoomShape shape, int quarter, int w, int d,
                                   DoorwayTest doorway) {
        boolean[][] open = new boolean[w][d];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                // A doorway approach is always floor, whatever the mask says.
                // Without this the check below would reject nearly every shape
                // rather than repairing the one tile that matters.
                open[x][z] = doorway.isApproach(x, z)
                        || shape.open(x, z, w, d, quarter);
            }
        }
        return connected(open, w, d, doorway) ? open : null;
    }

    /** Which tiles are the approach to a doorway. */
    @FunctionalInterface
    public interface DoorwayTest {
        boolean isApproach(int x, int z);
    }

    /**
     * Can a player still walk from every doorway to every other?
     *
     * The flood starts at a doorway rather than at the centre, because a
     * {@link #RING} has a solid centre and starting there would report every
     * ring room as broken.
     */
    private static boolean connected(boolean[][] open, int w, int d,
                                     DoorwayTest doorway) {
        int[] start = null;
        for (int x = 0; x < w && start == null; x++) {
            for (int z = 0; z < d; z++) {
                if (doorway.isApproach(x, z)) {
                    start = new int[]{x, z};
                    break;
                }
            }
        }
        if (start == null) {
            return true;            // no doors: nothing to strand
        }

        boolean[][] seen = new boolean[w][d];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start[0]][start[1]] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int nx = at[0] + dir.getStepX();
                int nz = at[1] + dir.getStepZ();
                if (nx < 0 || nz < 0 || nx >= w || nz >= d
                        || seen[nx][nz] || !open[nx][nz]) {
                    continue;
                }
                seen[nx][nz] = true;
                queue.add(new int[]{nx, nz});
            }
        }

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (doorway.isApproach(x, z) && !seen[x][z]) {
                    return false;
                }
            }
        }
        return true;
    }
}
