package com.elysium.ascendancy.weight;

import java.util.List;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Everything a roll needs to know about where and for whom it happens
 * (Apotheosis's GenContext, AP-2). It holds the random source, the world
 * tier, the luck, and where the roll happens. Rarities, affixes, gems and
 * invaders all pick through {@link #pick}, so the same tier and luck always
 * mean the same odds.
 *
 * <p>Uses no game types. The game side builds one from a player and a level
 * (dimension and biome as their id strings). {@link #constrained} is the
 * dimension/biome filter that Apotheosis calls {@code Constraints}.
 */
public record GenContext(RandomGenerator random, int tier, double luck,
                         String dimension, String biome, int x, int y, int z) {

    /** Picks one entry by its tiered weight at this tier and luck; null when all weigh nothing. */
    public <T> T pick(List<T> entries, Function<T, TieredWeight> weight) {
        return WeightedPick.pick(entries, e -> {
            TieredWeight w = weight.apply(e);
            return w == null ? 0L : w.weightAt(tier, luck);
        }, random.nextDouble());
    }

    /**
     * Whether a dimension/biome constraint allows this place. An empty or
     * null list allows everything, as in Apotheosis.
     */
    public boolean constrained(List<String> dimensions, List<String> biomes) {
        boolean dim = dimensions == null || dimensions.isEmpty() || dimensions.contains(dimension);
        boolean bio = biomes == null || biomes.isEmpty() || biomes.contains(biome);
        return dim && bio;
    }
}
