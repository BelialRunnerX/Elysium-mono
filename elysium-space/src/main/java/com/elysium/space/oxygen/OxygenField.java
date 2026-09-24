package com.elysium.space.oxygen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the air is, per dimension (Stellaris's Global/DimensionOxygenManager).
 *
 * Each distributor reports the room it pressurises; a position is oxygenated
 * while at least one distributor covers it. Server-side and rebuilt from the
 * distributors as they tick, so nothing is saved: a distributor in a loaded
 * chunk refills its room within one scan of the chunk loading.
 */
public final class OxygenField {

    private OxygenField() {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<BlockPos>>> ROOMS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> COVER =
            new ConcurrentHashMap<>();

    /** Replace what the distributor at {@code source} pressurises. */
    public static synchronized void set(Level level, BlockPos source, Set<BlockPos> room) {
        ResourceKey<Level> dim = level.dimension();
        clear(level, source);
        if (room.isEmpty()) {
            return;
        }
        ROOMS.computeIfAbsent(dim, k -> new HashMap<>()).put(source.immutable(), Set.copyOf(room));
        Map<BlockPos, Integer> cover = COVER.computeIfAbsent(dim, k -> new HashMap<>());
        for (BlockPos p : room) {
            cover.merge(p, 1, Integer::sum);
        }
    }

    /** The distributor at {@code source} stopped (broken, unloaded, open room). */
    public static synchronized void clear(Level level, BlockPos source) {
        ResourceKey<Level> dim = level.dimension();
        Map<BlockPos, Set<BlockPos>> rooms = ROOMS.get(dim);
        Set<BlockPos> old = rooms == null ? null : rooms.remove(source);
        if (old == null) {
            return;
        }
        Map<BlockPos, Integer> cover = COVER.get(dim);
        for (BlockPos p : old) {
            cover.computeIfPresent(p, (k, n) -> n <= 1 ? null : n - 1);
        }
    }

    public static synchronized boolean isOxygenated(Level level, BlockPos pos) {
        Map<BlockPos, Integer> cover = COVER.get(level.dimension());
        return cover != null && cover.containsKey(pos);
    }

    /** How many positions are pressurised in a dimension; for the command and tests. */
    public static synchronized int volume(Level level) {
        Map<BlockPos, Integer> cover = COVER.get(level.dimension());
        return cover == null ? 0 : cover.size();
    }

    /** Forget everything, when the server stops. */
    public static synchronized void reset() {
        ROOMS.clear();
        COVER.clear();
    }
}
