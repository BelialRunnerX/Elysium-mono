package com.elysium.dungeons.room;

import com.elysium.dungeons.ElysiumDungeons;
import com.elysium.dungeons.level.DungeonInstance;
import com.elysium.dungeons.level.DungeonSeed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a layout into blocks.
 *
 * <h2>Two passes, and why they are separate</h2>
 *
 * <ol>
 *   <li><b>Shells.</b> Every room gets floor, walls, ceiling and its doorways.
 *       All of it, for every room, before anything is decorated.</li>
 *   <li><b>Contents.</b> Each room's decorator fills the box it has been given.
 *       </li>
 *  </ol>
 *
 * They are separate because a doorway is cut through a wall that two rooms
 * share. Build one room completely and then the next, and the second room's
 * wall closes the doorway the first one just opened — an enclosed dungeon where
 * half the doors are bricked up, which is exactly the failure a player cannot
 * work around. Building every shell first means every wall exists before any
 * door is cut, so cutting is the last thing that happens to a wall.
 *
 * <h2>Room geometry</h2>
 *
 * A cell is {@value #CELL} blocks square. The room inside it is
 * {@value #INTERIOR} square and {@value #INTERIOR_HEIGHT} high, wrapped in a
 * one-block shell, and <b>the shell fills the entire cell</b> — the two blocks
 * of spacing beyond the interior wall are solid stone, so neighbouring rooms
 * have three blocks of stone between their interiors and cutting a doorway in
 * one never exposes the other.
 *
 * <h2>The seam, and the hole that used to be in it</h2>
 *
 * The shell originally built only {@code INTERIOR + 2} blocks square — fifteen
 * — while cells are spaced {@value #CELL} apart. The sixteenth column of every
 * cell was therefore never written at all. In a void dimension "never written"
 * is not stone, it is nothing, so a one-block-wide slot of open void ran the
 * full height of every seam in the dungeon.
 *
 * It was invisible from inside a room, because the seam sits behind the wall.
 * It was only reachable through a doorway — {@link #carveDoor} cuts three
 * blocks deep, spanning this room's wall, the seam, and the neighbour's wall,
 * at {@code y} 1 to 3. The floor at {@code y} 0 across those three blocks was
 * never part of the cut, so two thirds of it was solid and the middle third
 * was the unwritten seam. Every doorway in the dungeon had a one-block pit in
 * the middle of it, straight to the void.
 *
 * The fix is that the shell now fills {@link #CELL} squared rather than
 * {@code INTERIOR + 2}, so cells tile exactly and there is nothing left
 * unwritten. The constants below carry the relationship explicitly, and
 * {@link #checkGeometry} refuses to build if it is ever broken again, because
 * this is a bug you cannot see from inside the room it is next to.
 */
public final class DungeonBuilder {

    private DungeonBuilder() {
    }

    /** Grid spacing between room origins, and the footprint each shell fills. */
    public static final int CELL = 24;

    /** Interior floor size. */
    public static final int INTERIOR = 21;

    /** Interior height, floor to ceiling. */
    public static final int INTERIOR_HEIGHT = 9;

    /**
     * Half-width of a doorway; 2 gives a five-wide opening.
     *
     * <b>Public because {@code RoomContext} has to know it.</b> The strip of
     * floor a decorator must leave clear in front of a door is derived from
     * this, and it used to be the literal 2 sitting in another file — correct
     * for a three-wide door and quietly wrong the moment the door got wider,
     * which would have let a pillar be placed in a doorway and seal a room the
     * layout believes is connected.
     */
    public static final int DOOR_HALF = 2;

    /** Doorway height. Taller rooms want taller doors or they read as tunnels. */
    private static final int DOOR_HEIGHT = 4;

    /**
     * How deep a doorway must cut to reach the neighbour's interior.
     *
     * Derived, not chosen. From this room's wall at {@code INTERIOR + 1}, the
     * cut has to clear every solid block up to and including the neighbour's
     * wall at {@code CELL}, which is exactly {@code CELL - INTERIOR} blocks.
     * Writing it as a literal 3 is what let the geometry drift in the first
     * place: the number stayed right while the thing it described changed.
     */
    private static final int DOOR_DEPTH = CELL - INTERIOR;

    /**
     * Refuses to build a dungeon whose geometry has a hole in it.
     *
     * Cheap, run once per dungeon, and it turns a silent void-slot into a
     * startup failure with a sentence explaining itself. The alternative is
     * what actually happened: a player walking through a door and falling out
     * of the world.
     */
    private static void checkGeometry() {
        if (CELL < INTERIOR + 2) {
            throw new IllegalStateException("CELL (" + CELL + ") is smaller than the shell it "
                    + "must contain (" + (INTERIOR + 2) + "); rooms would overlap");
        }
        if (DOOR_DEPTH < CELL - INTERIOR) {
            throw new IllegalStateException("DOOR_DEPTH (" + DOOR_DEPTH + ") does not reach the "
                    + "neighbour's interior; doors would open onto stone");
        }
    }

    /**
     * Builds the whole dungeon.
     *
     * @return the world position a player entering should be put at
     */
    public static BlockPos build(ServerLevel level, DungeonInstance instance,
                                 DungeonLayout layout) {
        // The class comment says this refuses to build on broken geometry. It
        // was never called, so it refused nothing: a dead guard reads as a
        // guarantee in the file and is not one in the game.
        checkGeometry();

        long seed = instance.getSeed();
        BlockPos origin = instance.getOrigin();

        for (DungeonLayout.Cell cell : layout.cells()) {
            shell(level, roomOrigin(origin, cell));
        }

        // Doors after every shell exists - see the class comment.
        for (DungeonLayout.Cell cell : layout.cells()) {
            for (Direction door : cell.doors) {
                carveDoor(level, roomOrigin(origin, cell), door);
            }
        }

        for (DungeonLayout.Cell cell : layout.cells()) {
            DungeonRoom room = DungeonRoom.pick(cell.kind,
                    RandomSource.create(DungeonSeed.derive(seed, "pick", cell.x, cell.z)));
            if (room == null) {
                // Nothing registered for this kind. An empty room is a poorer
                // dungeon; a crash here strands a player on a loading screen.
                ElysiumDungeons.LOGGER.warn("No room registered for {} - leaving ({}, {}) empty",
                        cell.kind, cell.x, cell.z);
                continue;
            }
            RoomContext context = new RoomContext(level,
                    roomOrigin(origin, cell).offset(1, 1, 1),
                    INTERIOR, INTERIOR_HEIGHT, INTERIOR, cell,
                    instance.getPartyLevel());
            room.decorate(context,
                    RandomSource.create(DungeonSeed.derive(seed, "room", cell.x, cell.z)));
        }

        ElysiumDungeons.LOGGER.info("Built {} rooms for {} (walk to boss: {} doorways)",
                layout.size(), instance, layout.depth());

        return entrancePos(origin, layout);
    }

    /**
     * Where a player arriving should stand, without building anything.
     *
     * Separate from {@link #build} because joining a dungeon that already
     * exists must not rebuild it. A rebuild writes every block again, which
     * refills the chests, respawns the boss and undoes everything the players
     * already inside have done — so the second person through the portal would
     * silently reset the run for the first.
     */
    public static BlockPos entrancePos(BlockPos origin, DungeonLayout layout) {
        return roomOrigin(origin, layout.entrance())
                .offset(INTERIOR / 2 + 1, 1, INTERIOR / 2 + 1);
    }

    /** The shell's outer corner for a cell. */
    public static BlockPos roomOrigin(BlockPos dungeonOrigin, DungeonLayout.Cell cell) {
        return dungeonOrigin.offset(cell.x * CELL, 0, cell.z * CELL);
    }

    /**
     * Floor, four walls and a ceiling: a sealed box with nothing in it.
     *
     * Built as a solid block and then hollowed rather than as six faces. Six
     * faces is fewer writes and gets the edges wrong — the seams where two
     * faces meet are the places a stray gap appears, and a gap in an enclosed
     * dungeon is a player falling into the void.
     */
    private static void shell(ServerLevel level, BlockPos corner) {
        // The full cell, not INTERIOR + 2. Anything short of CELL leaves a
        // column of unwritten void at the seam — see the class javadoc.
        int outerHeight = INTERIOR_HEIGHT + 2;
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = 0; x < CELL; x++) {
            for (int z = 0; z < CELL; z++) {
                for (int y = 0; y < outerHeight; y++) {
                    // Hollow only the interior. Everything else — including the
                    // spacing beyond the wall — is solid, so a doorway cut
                    // through it always has a floor under it.
                    boolean inside = x >= 1 && x <= INTERIOR
                            && z >= 1 && z <= INTERIOR
                            && y >= 1 && y <= INTERIOR_HEIGHT;
                    BlockState state = inside ? air : (y == 0 ? floor : wall);
                    level.setBlock(corner.offset(x, y, z), state, 2);
                }
            }
        }
    }

    /**
     * Cuts a doorway through one wall.
     *
     * Cuts through <b>both</b> the wall of this room and the wall of the room
     * beyond it, which is why the loop runs two blocks deep. Cutting only this
     * room's wall leaves the neighbour's wall standing one block further on,
     * and the door opens onto stone — a dungeon that looks connected and is
     * not, which is the worst version of this bug because the layout is right
     * and only the blocks are wrong.
     */
    private static void carveDoor(ServerLevel level, BlockPos corner, Direction door) {
        int outer = INTERIOR + 2;
        int mid = outer / 2;
        // The cut starts at this room's wall and must clear the seam and the
        // neighbour's wall too — DOOR_DEPTH is derived from CELL so it cannot
        // fall out of step with the geometry again.
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = 1; y <= DOOR_HEIGHT; y++) {
            for (int offset = -DOOR_HALF; offset <= DOOR_HALF; offset++) {
                for (int depth = 0; depth < DOOR_DEPTH; depth++) {
                    int x;
                    int z;
                    switch (door) {
                        case NORTH -> {
                            x = mid + offset;
                            z = -depth;
                        }
                        case SOUTH -> {
                            x = mid + offset;
                            z = outer - 1 + depth;
                        }
                        case WEST -> {
                            x = -depth;
                            z = mid + offset;
                        }
                        case EAST -> {
                            x = outer - 1 + depth;
                            z = mid + offset;
                        }
                        default -> {
                            return;
                        }
                    }
                    level.setBlock(corner.offset(x, y, z), air, 2);
                }
            }
        }
    }
}
