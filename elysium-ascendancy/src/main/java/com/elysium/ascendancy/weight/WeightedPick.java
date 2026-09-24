package com.elysium.ascendancy.weight;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Picking one thing by weight.
 *
 * Takes the random roll as an argument — a number in [0, 1) — rather than a
 * Random, so the same pick is reproducible, and so a harness can walk the
 * whole range of rolls and measure each entry's share exactly instead of
 * sampling and hoping.
 *
 * <p>Imports nothing Minecraft, and must not: it is executed by a harness.
 */
public final class WeightedPick {

    private WeightedPick() {
    }

    /** The total weight, saturating rather than overflowing. */
    public static <T> long total(List<T> entries, ToLongFunction<T> weight) {
        long total = 0L;
        for (T entry : entries) {
            long w = Math.max(0L, weight.applyAsLong(entry));
            total = total > Long.MAX_VALUE - w ? Long.MAX_VALUE : total + w;
        }
        return total;
    }

    /**
     * The entry this roll lands on, or null when nothing has any weight.
     *
     * An entry with weight w owns exactly w / total of the rolls. The roll is
     * clamped into [0, 1) first, so a caller's off-by-one — a roll of exactly
     * 1.0 — lands on the last entry instead of falling off the end.
     */
    public static <T> T pick(List<T> entries, ToLongFunction<T> weight, double roll) {
        long total = total(entries, weight);
        if (total <= 0L) {
            return null;
        }
        double r = Double.isNaN(roll) ? 0.0D : Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        // Integer arithmetic for the target, so an entry's share is exact
        // rather than subject to how a double spreads over a large total.
        long target = (long) Math.floor(r * total);
        if (target >= total) {
            target = total - 1;
        }
        long running = 0L;
        T last = null;
        for (T entry : entries) {
            long w = Math.max(0L, weight.applyAsLong(entry));
            if (w == 0L) {
                continue;
            }
            last = entry;
            running = running > Long.MAX_VALUE - w ? Long.MAX_VALUE : running + w;
            if (target < running) {
                return entry;
            }
        }
        return last;
    }
}
