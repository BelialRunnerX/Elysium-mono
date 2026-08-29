import com.elysium.dungeons.room.DungeonLayout;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Runs the layout generator and checks that what it produced is a dungeon.
 *
 * The generator is the one piece of this mod that is a real algorithm rather
 * than a set of registrations, so it is the one piece worth executing outside
 * the game. Every property below is something that, if broken, produces a
 * dungeon a player cannot finish — and none of them would fail to compile.
 *
 *   1. Every room is reachable from the entrance by walking doorways.
 *      A room behind a solid wall is a dead dungeon.
 *   2. Doors are symmetric: if A has a door east, B to its east has one west.
 *      A one-way door is a room you can enter and not leave.
 *   3. Exactly one entrance and exactly one boss room.
 *   4. The boss is at the deepest point, so the longest walk ends in the fight.
 *   5. The requested number of loot rooms is placed, or as many as the dungeon
 *      has room for.
 *   6. Different seeds give different dungeons — the mod's central promise.
 *   7. The same seed gives the same dungeon — so a bug is reproducible.
 *
 * Compile against the stubs and run:
 *   javac -cp stubs:outdungeons -d /tmp/h dungeons/tools/LayoutHarness.java
 *   java  -cp stubs:outdungeons:/tmp/h LayoutHarness
 */
public final class LayoutHarness {

    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    public static void main(String[] args) {
        int trials = 400;
        int rooms = 12;
        int loot = 2;

        Set<String> fingerprints = new HashSet<>();
        int totalDepth = 0;
        int minRooms = Integer.MAX_VALUE;
        int maxRooms = 0;

        for (int trial = 0; trial < trials; trial++) {
            long seed = 0x5EEDL * trial + 17L;
            DungeonLayout layout = DungeonLayout.generate(seed, rooms, loot);

            // 3. one entrance, one boss
            check(layout.count(DungeonLayout.Kind.ENTRANCE) == 1,
                    "seed " + seed + ": " + layout.count(DungeonLayout.Kind.ENTRANCE)
                            + " entrances");
            check(layout.count(DungeonLayout.Kind.BOSS) == 1,
                    "seed " + seed + ": " + layout.count(DungeonLayout.Kind.BOSS) + " boss rooms");

            // 5. loot rooms placed
            int lootPlaced = layout.count(DungeonLayout.Kind.LOOT);
            check(lootPlaced == Math.min(loot, Math.max(0, layout.size() - 2)),
                    "seed " + seed + ": " + lootPlaced + " loot rooms in a "
                            + layout.size() + "-room dungeon");

            // 2. door symmetry
            for (DungeonLayout.Cell cell : layout.cells()) {
                for (net.minecraft.core.Direction dir : cell.doors) {
                    DungeonLayout.Cell other =
                            layout.at(cell.x + dir.getStepX(), cell.z + dir.getStepZ());
                    check(other != null,
                            "seed " + seed + ": door at (" + cell.x + "," + cell.z + ") "
                                    + dir + " leads nowhere");
                    if (other != null) {
                        check(other.doors.contains(dir.getOpposite()),
                                "seed " + seed + ": one-way door between (" + cell.x + ","
                                        + cell.z + ") and (" + other.x + "," + other.z + ")");
                    }
                }
            }

            // 1. reachability
            Set<Long> seen = new HashSet<>();
            Queue<DungeonLayout.Cell> queue = new ArrayDeque<>();
            queue.add(layout.entrance());
            seen.add(layout.entrance().key());
            while (!queue.isEmpty()) {
                DungeonLayout.Cell at = queue.poll();
                for (net.minecraft.core.Direction dir : at.doors) {
                    DungeonLayout.Cell next =
                            layout.at(at.x + dir.getStepX(), at.z + dir.getStepZ());
                    if (next != null && seen.add(next.key())) {
                        queue.add(next);
                    }
                }
            }
            check(seen.size() == layout.size(),
                    "seed " + seed + ": only " + seen.size() + " of " + layout.size()
                            + " rooms are reachable from the entrance");

            // 4. boss is deepest
            check(layout.boss().depth == layout.depth(),
                    "seed " + seed + ": boss at depth " + layout.boss().depth
                            + " but the dungeon goes to " + layout.depth());

            totalDepth += layout.depth();
            minRooms = Math.min(minRooms, layout.size());
            maxRooms = Math.max(maxRooms, layout.size());

            StringBuilder fingerprint = new StringBuilder();
            for (DungeonLayout.Cell cell : layout.cells()) {
                fingerprint.append(cell.x).append(',').append(cell.z)
                        .append(':').append(cell.kind).append(';');
            }
            fingerprints.add(fingerprint.toString());
        }

        // 6. variety
        check(fingerprints.size() > trials * 0.95,
                "only " + fingerprints.size() + " distinct dungeons in " + trials
                        + " seeds - the reroll promise is not being kept");

        // 7. determinism
        DungeonLayout a = DungeonLayout.generate(12345L, rooms, loot);
        DungeonLayout b = DungeonLayout.generate(12345L, rooms, loot);
        check(a.size() == b.size() && a.boss().x == b.boss().x && a.boss().z == b.boss().z,
                "the same seed produced two different dungeons - nothing is reproducible");

        System.out.println();
        System.out.printf("trials             : %d%n", trials);
        System.out.printf("rooms per dungeon  : %d requested, %d..%d placed%n",
                rooms, minRooms, maxRooms);
        System.out.printf("mean walk to boss  : %.1f doorways%n", totalDepth / (double) trials);
        System.out.printf("distinct layouts   : %d / %d%n", fingerprints.size(), trials);
        System.out.println();

        if (failures == 0) {
            System.out.println("all layout properties hold");
        } else {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
    }
}
