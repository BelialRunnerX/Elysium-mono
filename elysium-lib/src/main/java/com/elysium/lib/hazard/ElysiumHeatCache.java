package com.elysium.lib.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How much fire is within reach of a position, remembered for a moment.
 *
 * <h2>Why this is not optional</h2>
 *
 * {@link ElysiumHazardField#measureFire} walks a lattice of four hundred blocks,
 * and the hazard layer asks about thermal twice a second for every player — once
 * to deal damage and once to decide whether the warning can be cleared. Without
 * a cache that is eight hundred block reads per player per second for a question
 * whose answer cannot have changed between the two, and on a server with thirty
 * players it is the kind of cost that does not show up until the server it shows
 * up on is somebody's.
 *
 * <h2>Why a time-to-live and not an invalidation</h2>
 *
 * The same reason the room cache has one: listening for every block placement in
 * the world and working out which cached positions it could have affected is a
 * correctness problem with no cheap answer. Two seconds of staleness costs a
 * player at most two ticks of damage after they have already stepped back from
 * the lava — which is the right way round, because the alternative is two ticks
 * of damage before they walked into it.
 */
public final class ElysiumHeatCache {

    private ElysiumHeatCache() {
    }

    /** How long an answer is trusted, in ticks. */
    public static final int TTL = 40;

    /**
     * How many positions are remembered at once.
     *
     * Bounded because the key is a position and positions are unbounded: a
     * player walking in a straight line would otherwise add one entry a second
     * forever. The eldest is dropped rather than anything cleverer — an entry
     * that falls out is re-scanned, which is the same work as a miss.
     */
    public static final int CAPACITY = 256;

    private record Entry(long expiresAt, float heat) {
    }

    private static final Map<String, Entry> CACHE =
            new LinkedHashMap<>(CAPACITY, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > CAPACITY;
                }
            };

    /**
     * How much fire is in reach, 0..1.
     *
     * Server thread only — the hazard tick is the sole caller, and a
     * LinkedHashMap in access order is not safe for two of them.
     */
    public static float at(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide()) {
            return 0.0F;
        }
        String key = level.dimension().location() + "@" + pos.asLong();
        long now = level.getGameTime();
        Entry cached = CACHE.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.heat();
        }
        float heat = ElysiumHazardField.measureFire(level, pos);
        CACHE.put(key, new Entry(now + TTL, heat));
        return heat;
    }

    /** Forget everything. For a world unload, and for the tests. */
    public static void clear() {
        CACHE.clear();
    }

    /** How many answers are being held. Reported, and checked. */
    public static int size() {
        return CACHE.size();
    }
}
