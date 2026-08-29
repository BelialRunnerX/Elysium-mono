package com.elysium.dungeons.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * Where a dungeon's randomness comes from.
 *
 * <h2>The requirement, stated exactly</h2>
 *
 * Two dungeons must never be the same, and one dungeon must always be itself.
 * Those pull in opposite directions and both matter:
 *
 * <ul>
 *   <li><b>Never the same.</b> The whole mod is the promise that stepping back
 *       through gives you somewhere new. If the seed repeated, that promise
 *       breaks in the most visible way possible.</li>
 *   <li><b>Always itself.</b> A dungeon is not built in one go — rooms
 *       generate, the layout is walked, loot is rolled. Every one of those has
 *       to agree about what dungeon this is, and a bug report has to be
 *       reproducible from a number.</li>
 * </ul>
 *
 * So the seed is a pure function of things that are fixed for an instance, and
 * never of the clock. {@code System.nanoTime()} would satisfy the first
 * requirement and destroy the second: regenerate the same dungeon twice and get
 * two different places, with no way to ever see the one in the screenshot
 * again.
 *
 * <h2>The ingredients</h2>
 *
 * <ul>
 *   <li><b>The world seed</b>, so two worlds do not play the same dungeons in
 *       the same order.</li>
 *   <li><b>The instance index</b>, which increments and never repeats. This is
 *       what actually guarantees a new dungeon: the index differs, so the seed
 *       differs, so the layout differs.</li>
 *   <li><b>The portal position</b>, so two portals opened at the same moment in
 *       the same world still differ — and so a portal has a faint character of
 *       its own without ever repeating itself.</li>
 * </ul>
 *
 * <h2>Deriving without correlating</h2>
 *
 * The layout, each room and the loot all need randomness, and they must not be
 * the same randomness — a room that rolls the same numbers as the layout
 * produces visible patterns, like every third room being identical. Each asks
 * for its own derived seed through {@link #derive}, which mixes the instance
 * seed with a label and coordinates. The mixing is a 64-bit avalanche
 * (SplitMix64's finaliser), which is cheap and turns neighbouring inputs into
 * unrelated outputs — the property that matters when the inputs are grid
 * coordinates that differ by one.
 */
public final class DungeonSeed {

    private DungeonSeed() {
    }

    /**
     * The seed for a brand new instance.
     *
     * @param index the allocator's monotonic counter — the part that
     *              guarantees this differs from every dungeon before it
     */
    public static long forInstance(MinecraftServer server, long index, BlockPos portalAnchor) {
        long worldSeed = server.overworld().getSeed();
        return mix(mix(worldSeed ^ 0x9E3779B97F4A7C15L)
                ^ mix(index * 0xD1B54A32D192ED03L)
                ^ mix(portalAnchor.asLong() * 0xA24BAED4963EE407L));
    }

    /**
     * A sub-seed for one part of one dungeon.
     *
     * @param label what is asking — "layout", "room", "loot". Two different
     *              labels at the same coordinates get unrelated numbers, which
     *              is what stops a room's decoration from correlating with the
     *              loot standing in it.
     */
    public static long derive(long instanceSeed, String label, int x, int y) {
        long h = instanceSeed;
        for (int i = 0; i < label.length(); i++) {
            h = mix(h ^ label.charAt(i));
        }
        return mix(h ^ mix(((long) x << 32) ^ (y & 0xFFFFFFFFL)));
    }

    /** A source seeded from {@link #derive}, for code that wants one. */
    public static RandomSource source(long instanceSeed, String label, int x, int y) {
        return RandomSource.create(derive(instanceSeed, label, x, y));
    }

    /**
     * SplitMix64's finaliser: a 64-bit avalanche.
     *
     * Chosen because the inputs here are adjacent small integers — grid
     * coordinates, an index that goes up by one — and a weaker mix leaves
     * neighbouring cells visibly correlated. Every output bit depends on every
     * input bit after this.
     */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
