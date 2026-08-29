package com.elysium.dungeons.level;

import com.elysium.dungeons.ElysiumDungeons;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who has a dungeon, where it is, and what the next one will be.
 *
 * <h2>Where instances live</h2>
 *
 * On a grid. Instance <i>n</i> is built at cell <i>n</i> of a square spiral
 * centred on the origin of the dungeon dimension, {@value #CELL_SPAN} blocks
 * apart. A spiral rather than rows so that the first few dungeons are near the
 * origin and near each other, which keeps the region files a server actually
 * touches small; row-major would march off along +X forever and scatter them
 * across hundreds of region files.
 *
 * {@value #CELL_SPAN} blocks is far more than a dungeon needs. The margin is
 * the point: two dungeons must never share a chunk, because a chunk is the unit
 * the game loads and unloads, and a player standing in one dungeon would
 * otherwise keep a stranger's dungeon in memory — or, worse, a room built into
 * a chunk that is still being written by someone else's generation.
 *
 * <h2>The counter never goes backwards</h2>
 *
 * {@link #nextIndex} only ever increases, for the life of the world. Reusing a
 * cell would mean building on top of a dungeon that might still be loaded, and
 * the failure would be intermittent and appalling to debug. At one dungeon
 * every ten seconds for a year the counter reaches about three million, and the
 * furthest cell is roughly 1.7 million blocks out — well inside the world
 * border. This is not a limit anyone will reach.
 */
public final class DungeonInstances extends SavedData {

    /** Distance between neighbouring dungeon cells, in blocks. */
    public static final int CELL_SPAN = 1024;

    /** The Y level every dungeon is built from. */
    public static final int FLOOR_Y = 64;

    private static final String NAME = "elysium_dungeons";

    /** The next cell to hand out. Only ever increases. */
    private long nextIndex;

    /** Live and retired instances, by index. */
    private final Map<Long, DungeonInstance> instances = new HashMap<>();

    /**
     * Which instance each portal currently points at.
     *
     * Keyed by the portal's anchor block, packed as a long. A portal whose
     * instance has retired keeps its entry until the next entry replaces it —
     * so the record of "you have been here before" survives, which is what
     * lets the mod tell a returning player their dungeon was rebuilt rather
     * than silently behaving as if it were new.
     */
    private final Map<Long, Long> portalToInstance = new HashMap<>();

    public DungeonInstances() {
    }

    public static DungeonInstances get(MinecraftServer server) {
        // Stored on the overworld rather than on the dungeon dimension,
        // because the overworld always exists and is always loaded; a
        // dimension that has never been visited has no save file to attach to.
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DungeonInstances::new, DungeonInstances::load),
                NAME);
    }

    // ------------------------------------------------------------------
    // Allocation
    // ------------------------------------------------------------------

    /**
     * The dungeon a player stepping through this portal should arrive in.
     *
     * Returns the live one if the portal has a live one — that is what makes a
     * party entering together land in the same place — and otherwise allocates
     * a fresh cell with a fresh seed. The caller is told which happened,
     * because a newly allocated instance has no blocks in it yet and has to be
     * built before anyone can be sent there.
     */
    public Allocation acquire(MinecraftServer server, BlockPos portalAnchor,
                              ResourceKey<Level> returnDimension, BlockPos returnPos) {
        Long existingIndex = portalToInstance.get(portalAnchor.asLong());
        if (existingIndex != null) {
            DungeonInstance existing = instances.get(existingIndex);
            if (existing != null && existing.acceptsEntry()) {
                return new Allocation(existing, false);
            }
        }

        long index = nextIndex++;
        BlockPos origin = cellOrigin(index);
        long seed = DungeonSeed.forInstance(server, index, portalAnchor);

        DungeonInstance instance = new DungeonInstance(
                index, origin, seed, returnDimension, returnPos, portalAnchor);
        instances.put(index, instance);
        portalToInstance.put(portalAnchor.asLong(), index);
        setDirty();

        ElysiumDungeons.LOGGER.info("Allocated {} for portal at {}", instance, portalAnchor);
        return new Allocation(instance, true);
    }

    /** What {@link #acquire} did, and to what. */
    public record Allocation(DungeonInstance instance, boolean freshlyAllocated) {
    }

    /**
     * Cell <i>n</i> of a square spiral, in blocks.
     *
     * The spiral is walked rather than solved because the closed form for a
     * square spiral is fiddly and this is called once per dungeon — a loop of a
     * few thousand iterations at the very worst is free, and a loop is
     * obviously correct in a way the closed form is not.
     */
    public static BlockPos cellOrigin(long index) {
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int stepsInLeg = 1;
        int stepsTaken = 0;
        int legsAtThisLength = 0;

        for (long i = 0; i < index; i++) {
            x += dx;
            z += dz;
            stepsTaken++;
            if (stepsTaken == stepsInLeg) {
                stepsTaken = 0;
                int turnX = -dz;      // turn left
                dz = dx;
                dx = turnX;
                legsAtThisLength++;
                if (legsAtThisLength == 2) {
                    legsAtThisLength = 0;
                    stepsInLeg++;
                }
            }
        }
        return new BlockPos(x * CELL_SPAN, FLOOR_Y, z * CELL_SPAN);
    }

    // ------------------------------------------------------------------
    // Occupancy
    // ------------------------------------------------------------------

    public void enter(DungeonInstance instance, UUID player) {
        instance.enter(player);
        setDirty();
    }

    /**
     * Records a player leaving, and retires the dungeon if they were the last.
     *
     * @return true when this retired the instance
     */
    public boolean leave(DungeonInstance instance, UUID player) {
        boolean retired = instance.leave(player);
        setDirty();
        if (retired) {
            ElysiumDungeons.LOGGER.info("{} is empty and will not be re-entered; "
                    + "the next trip through that portal builds a new one", instance);
        }
        return retired;
    }

    /** The instance a player standing at these coordinates is inside, if any. */
    public DungeonInstance instanceAt(BlockPos pos) {
        for (DungeonInstance instance : instances.values()) {
            BlockPos origin = instance.getOrigin();
            if (Math.abs(pos.getX() - origin.getX()) < CELL_SPAN / 2
                    && Math.abs(pos.getZ() - origin.getZ()) < CELL_SPAN / 2) {
                return instance;
            }
        }
        return null;
    }

    public DungeonInstance byIndex(long index) {
        return instances.get(index);
    }

    /** Every instance, live and retired. */
    public List<DungeonInstance> all() {
        return new ArrayList<>(instances.values());
    }

    /**
     * Forgets retired instances once there are enough to be worth forgetting.
     *
     * Only the bookkeeping is dropped; the blocks stay where they are, in
     * chunks nothing will ever load again. Deleting them would mean unloading
     * and removing region files at runtime, which is exactly the operation this
     * design exists to avoid — and the cost of leaving them is disk, which is
     * cheap, rather than correctness, which is not.
     */
    public int sweep(int keepRetired) {
        List<Long> retired = new ArrayList<>();
        for (Map.Entry<Long, DungeonInstance> entry : instances.entrySet()) {
            if (entry.getValue().isRetired()) {
                retired.add(entry.getKey());
            }
        }
        if (retired.size() <= keepRetired) {
            return 0;
        }
        retired.sort(Long::compareTo);
        int removing = retired.size() - keepRetired;
        for (int i = 0; i < removing; i++) {
            long index = retired.get(i);
            DungeonInstance instance = instances.remove(index);
            if (instance != null) {
                portalToInstance.remove(instance.getPortalAnchor().asLong(), index);
            }
        }
        setDirty();
        return removing;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putLong("NextIndex", nextIndex);

        ListTag list = new ListTag();
        for (DungeonInstance instance : instances.values()) {
            list.add(instance.save());
        }
        tag.put("Instances", list);

        ListTag portals = new ListTag();
        for (Map.Entry<Long, Long> entry : portalToInstance.entrySet()) {
            CompoundTag link = new CompoundTag();
            link.putLong("Portal", entry.getKey());
            link.putLong("Instance", entry.getValue());
            portals.add(link);
        }
        tag.put("Portals", portals);
        return tag;
    }

    public static DungeonInstances load(CompoundTag tag,
                                        net.minecraft.core.HolderLookup.Provider registries) {
        DungeonInstances data = new DungeonInstances();
        data.nextIndex = tag.getLong("NextIndex");

        ListTag list = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DungeonInstance instance = DungeonInstance.load(list.getCompound(i));
            data.instances.put(instance.getIndex(), instance);
        }

        ListTag portals = tag.getList("Portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < portals.size(); i++) {
            CompoundTag link = portals.getCompound(i);
            data.portalToInstance.put(link.getLong("Portal"), link.getLong("Instance"));
        }

        // Nothing is inside anything after a restart: occupants are not saved
        // on purpose. Every instance that was live is therefore empty, and an
        // empty instance must retire or its portal would never reroll again.
        for (DungeonInstance instance : data.instances.values()) {
            if (instance.isEmpty()) {
                instance.leave(new UUID(0L, 0L));
            }
        }
        return data;
    }
}
