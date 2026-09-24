package com.elysium.lib.room;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which room a position is in, remembered for a moment.
 *
 * <h2>Why this is not optional</h2>
 *
 * The hazard tick asks about five hazards a second for every player, and a room
 * scan walks up to five hundred blocks. Without a cache that is two and a half
 * thousand block reads per player per second for a question whose answer is the
 * same all five times — and on a server with thirty players it is the kind of
 * cost that does not show up until the server it shows up on is somebody's.
 *
 * <h2>Why a time-to-live and not an invalidation</h2>
 *
 * Because the honest alternative is listening to every block placement and
 * breakage in the world and working out which cached volumes they could have
 * touched, which is a correctness problem with no cheap answer — a block broken
 * forty blocks away can unseal a room if the room is large enough.
 *
 * Two seconds of staleness costs a player at most two ticks of damage they had
 * just earned protection from, or two they had just lost it. It is the right
 * trade for a system whose whole input is architecture, which changes on the
 * timescale of somebody placing blocks by hand.
 */
public final class ElysiumRoomCache {

    private ElysiumRoomCache() {
    }

    /** How long an answer is trusted, in ticks. */
    public static final int TTL = 40;

    /**
     * How many positions are remembered at once.
     *
     * Bounded because the key is a position and positions are unbounded: a
     * player walking in a straight line would otherwise add one entry per block
     * forever. Two hundred and fifty-six is far more than the handful of
     * distinct positions a server's players occupy in any two-second window,
     * and the eldest is dropped rather than any cleverer policy — an entry that
     * falls out is re-scanned, which is the same work as a miss.
     */
    public static final int CAPACITY = 256;

    private record Entry(long expiresAt, ElysiumRoom room) {
    }

    private static final Map<String, Entry> CACHE =
            new LinkedHashMap<>(CAPACITY, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > CAPACITY;
                }
            };

    /**
     * The room this position is in, or null.
     *
     * Server thread only — the hazard tick is the sole caller, and a
     * LinkedHashMap in access order is not safe for two of them.
     */
    public static ElysiumRoom at(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide()) {
            return null;
        }
        String key = keyOf(level, pos);
        long now = level.getGameTime();
        Entry cached = CACHE.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.room();
        }
        ElysiumRoom found = ElysiumRoom.classify(ElysiumRoomScan.survey(level, pos));
        CACHE.put(key, new Entry(now + TTL, found));
        return found;
    }

    /** The key, which has to carry the dimension or two worlds share an answer. */
    static String keyOf(Level level, BlockPos pos) {
        ResourceLocation dimension = level.dimension().location();
        return dimension + "@" + pos.asLong();
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
