package com.elysium.dungeons.room;

import com.elysium.dungeons.level.DungeonSeed;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Which cells of the grid are rooms, what each room is for, and where the doors
 * go.
 *
 * <h2>The shape of a dungeon</h2>
 *
 * A grid of cells. A cell is either empty or one room; rooms are sealed boxes
 * and the only way between two of them is a doorway punched through the wall
 * they share. There are no corridors, because with enclosed rooms a corridor is
 * just a long thin room that wastes a cell.
 *
 * <h2>How the shape is chosen</h2>
 *
 * A random growth from the entrance. Start with one cell, then repeatedly pick
 * an occupied cell and push into an empty neighbour, until the target room
 * count is reached.
 *
 * The choice of <i>which</i> occupied cell to grow from is the only real knob,
 * and it decides whether a dungeon feels like a warren or a corridor:
 *
 * <ul>
 *   <li>Always the newest cell gives a long snake with almost no branching —
 *       one path, no decisions.</li>
 *   <li>Always a uniformly random cell gives a fat blob with everything
 *       adjacent to everything — no depth, and the boss is four steps from the
 *       door.</li>
 * </ul>
 *
 * So it is weighted: mostly the newest, sometimes an older one. That produces a
 * dungeon with a spine and a few branches hanging off it, which is the shape
 * that gives dead ends worth putting loot in and a boss worth walking to.
 *
 * <h2>Assigning the rooms</h2>
 *
 * In this order, and the order matters:
 *
 * <ol>
 *   <li><b>Entrance</b> at the starting cell.</li>
 *   <li><b>Boss</b> at the cell furthest from the entrance, measured by walking
 *       the doorways rather than by straight-line distance — the point is how
 *       far you have to walk, not how far away it looks.</li>
 *   <li><b>Loot</b> at dead ends, preferring the ones furthest from the
 *       entrance. Dead ends because a reward should cost a detour, and if there
 *       are not enough dead ends the remaining loot rooms fall back to the
 *       furthest cells left.</li>
 *   <li><b>Filler</b> everywhere else.</li>
 * </ol>
 *
 * The boss is placed before the loot so it always gets the furthest cell. The
 * other way round, a loot room could take it, and the longest walk in the
 * dungeon would end in a chest rather than a fight.
 */
public final class DungeonLayout {

    /** How many cells across the grid may be. Not how big a dungeon is. */
    public static final int GRID = 9;

    /** What a cell is for. */
    public enum Kind {
        ENTRANCE,
        FILLER,
        LOOT,
        BOSS
    }

    /** One placed room. */
    public static final class Cell {
        public final int x;
        public final int z;
        public final Kind kind;

        /** The walls that have a doorway in them. */
        public final Set<Direction> doors = EnumSet.noneOf(Direction.class);

        /** Doorways from the entrance, by the shortest route. */
        public final int depth;

        Cell(int x, int z, Kind kind, int depth) {
            this.x = x;
            this.z = z;
            this.kind = kind;
            this.depth = depth;
        }

        public long key() {
            return pack(x, z);
        }
    }

    private final Map<Long, Cell> cells = new LinkedHashMap<>();
    private final Cell entrance;
    private final Cell boss;

    private DungeonLayout(Map<Long, Cell> cells, Cell entrance, Cell boss) {
        this.cells.putAll(cells);
        this.entrance = entrance;
        this.boss = boss;
    }

    // ------------------------------------------------------------------

    /**
     * Rolls a dungeon.
     *
     * @param seed      the instance seed; the same seed always gives the same
     *                  dungeon, which is what makes a bug reproducible
     * @param roomCount how many rooms to place, entrance and boss included
     * @param lootRooms how many loot rooms to try for
     */
    public static DungeonLayout generate(long seed, int roomCount, int lootRooms) {
        RandomSource random = RandomSource.create(DungeonSeed.derive(seed, "layout", 0, 0));

        // --- 1. grow a connected blob -----------------------------------
        int startX = GRID / 2;
        int startZ = GRID / 2;
        List<long[]> occupied = new ArrayList<>();
        Set<Long> taken = new java.util.HashSet<>();
        occupied.add(new long[]{startX, startZ});
        taken.add(pack(startX, startZ));

        int guard = 0;
        while (occupied.size() < roomCount && guard++ < roomCount * 200) {
            // Mostly the newest cell, sometimes an older one: see the class
            // comment for why this particular mix is the whole character of
            // the dungeon.
            int pick = random.nextFloat() < 0.72F
                    ? occupied.size() - 1
                    : random.nextInt(occupied.size());
            long[] from = occupied.get(pick);

            Direction dir = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
            int nx = (int) from[0] + dir.getStepX();
            int nz = (int) from[1] + dir.getStepZ();
            if (nx < 0 || nz < 0 || nx >= GRID || nz >= GRID) {
                continue;
            }
            long key = pack(nx, nz);
            if (!taken.add(key)) {
                continue;
            }
            occupied.add(new long[]{nx, nz});
        }

        // --- 2. depth from the entrance, by walking doorways -------------
        Map<Long, Integer> depth = new HashMap<>();
        depth.put(pack(startX, startZ), 0);
        Queue<long[]> queue = new ArrayDeque<>();
        queue.add(new long[]{startX, startZ});
        while (!queue.isEmpty()) {
            long[] at = queue.poll();
            int here = depth.get(pack((int) at[0], (int) at[1]));
            for (Direction dir : HORIZONTAL) {
                int nx = (int) at[0] + dir.getStepX();
                int nz = (int) at[1] + dir.getStepZ();
                long key = pack(nx, nz);
                if (!taken.contains(key) || depth.containsKey(key)) {
                    continue;
                }
                depth.put(key, here + 1);
                queue.add(new long[]{nx, nz});
            }
        }

        // Anything the growth left unreachable is dropped rather than kept as
        // a room nobody can get to. With this growth rule that should never
        // happen - every cell is added adjacent to an existing one - but a
        // room with no route to it is the kind of bug that only shows up when
        // a player is standing in front of a blank wall, so it is removed here
        // rather than trusted not to occur.
        taken.removeIf(key -> !depth.containsKey(key));

        // --- 3. neighbours, so dead ends can be found --------------------
        Map<Long, List<Direction>> exits = new HashMap<>();
        for (long key : taken) {
            int cx = unpackX(key);
            int cz = unpackZ(key);
            List<Direction> found = new ArrayList<>();
            for (Direction dir : HORIZONTAL) {
                if (taken.contains(pack(cx + dir.getStepX(), cz + dir.getStepZ()))) {
                    found.add(dir);
                }
            }
            exits.put(key, found);
        }

        // --- 4. assign kinds ---------------------------------------------
        long entranceKey = pack(startX, startZ);

        // The boss goes furthest away, measured in doorways walked. Ties are
        // broken by the key so the result is stable for a given seed.
        long bossKey = taken.stream()
                .filter(key -> key != entranceKey)
                .max(Comparator.<Long>comparingInt(key -> depth.getOrDefault(key, 0))
                        .thenComparingLong(key -> key))
                .orElse(entranceKey);

        Set<Long> lootKeys = new java.util.LinkedHashSet<>();
        List<Long> deadEnds = taken.stream()
                .filter(key -> key != entranceKey && key != bossKey)
                .filter(key -> exits.get(key).size() == 1)
                .sorted(Comparator.<Long>comparingInt(key -> -depth.getOrDefault(key, 0))
                        .thenComparingLong(key -> key))
                .toList();
        for (Long key : deadEnds) {
            if (lootKeys.size() >= lootRooms) {
                break;
            }
            lootKeys.add(key);
        }
        // Not enough dead ends: take the deepest cells that are left. A dungeon
        // that grew into a straight line has exactly one dead end, and it is
        // the boss - so without this fallback such a dungeon would have no loot
        // at all, which is a worse outcome than a loot room on the main path.
        if (lootKeys.size() < lootRooms) {
            List<Long> rest = taken.stream()
                    .filter(key -> key != entranceKey && key != bossKey)
                    .filter(key -> !lootKeys.contains(key))
                    .sorted(Comparator.<Long>comparingInt(key -> -depth.getOrDefault(key, 0))
                            .thenComparingLong(key -> key))
                    .toList();
            for (Long key : rest) {
                if (lootKeys.size() >= lootRooms) {
                    break;
                }
                lootKeys.add(key);
            }
        }

        Map<Long, Cell> built = new LinkedHashMap<>();
        for (long key : taken) {
            Kind kind = key == entranceKey ? Kind.ENTRANCE
                    : key == bossKey ? Kind.BOSS
                    : lootKeys.contains(key) ? Kind.LOOT
                    : Kind.FILLER;
            Cell cell = new Cell(unpackX(key), unpackZ(key), kind, depth.getOrDefault(key, 0));
            cell.doors.addAll(exits.get(key));
            built.put(key, cell);
        }

        return new DungeonLayout(built, built.get(entranceKey), built.get(bossKey));
    }

    // ------------------------------------------------------------------

    public Iterable<Cell> cells() {
        return cells.values();
    }

    public int size() {
        return cells.size();
    }

    public Cell entrance() {
        return entrance;
    }

    public Cell boss() {
        return boss;
    }

    public Cell at(int x, int z) {
        return cells.get(pack(x, z));
    }

    public int count(Kind kind) {
        int found = 0;
        for (Cell cell : cells.values()) {
            if (cell.kind == kind) {
                found++;
            }
        }
        return found;
    }

    /** The deepest cell's depth — how long the walk to the boss is. */
    public int depth() {
        int deepest = 0;
        for (Cell cell : cells.values()) {
            deepest = Math.max(deepest, cell.depth);
        }
        return deepest;
    }

    // ------------------------------------------------------------------

    static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * Grid coordinates packed into a long.
     *
     * The offset keeps the packed value positive for the whole legal grid, so
     * it can be used as a map key and sorted without surprises.
     */
    static long pack(int x, int z) {
        return ((long) (x + 512) << 20) | (z + 512);
    }

    static int unpackX(long key) {
        return (int) (key >> 20) - 512;
    }

    static int unpackZ(long key) {
        return (int) (key & 0xFFFFF) - 512;
    }
}
