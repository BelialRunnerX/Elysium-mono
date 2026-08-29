package com.elysium.lib.stats;

import com.mojang.serialization.Codec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable bag of stat points.
 *
 * Used for four things that all want the same shape: a race's starting stats, a
 * race or class's growth per level, the points a player has spent, and what a
 * piece of gear grants. One type for all four means adding them together is the
 * only operation the rest of the mod needs.
 *
 * <h2>Keyed by object, serialised by id</h2>
 *
 * Stats used to be an enum, so this was an {@code EnumMap} and serialisation
 * was the constant's name. Now that an add-on can register a stat, the key is
 * the stat object and the wire form is its namespaced id.
 *
 * A saved id that no longer resolves is dropped on load rather than failing:
 * uninstalling the mod that added a stat costs you the points in that stat and
 * nothing else. The points are gone, not the character.
 */
public final class ElysiumStatBlock {

    public static final ElysiumStatBlock EMPTY = new ElysiumStatBlock(Map.of());

    public static final Codec<Map<String, Integer>> MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    private final Map<ElysiumStat, Integer> values;

    private ElysiumStatBlock(Map<ElysiumStat, Integer> values) {
        this.values = values;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Builds from alternating stat/amount pairs, which reads well at a call
     * site defining a race:
     *
     * <pre>{@code ElysiumStatBlock.of(STRENGTH, 8, FORTITUDE, 7) }</pre>
     */
    public static ElysiumStatBlock of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "ElysiumStatBlock.of takes stat/amount pairs; got an odd number of arguments.");
        }
        Map<ElysiumStat, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((ElysiumStat) pairs[i], (Integer) pairs[i + 1]);
        }
        return new ElysiumStatBlock(Map.copyOf(map));
    }

    public static ElysiumStatBlock fromMap(Map<String, Integer> raw) {
        Map<ElysiumStat, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            ElysiumStat stat = ElysiumStat.REGISTRY.get(entry.getKey());
            if (stat != null) {
                map.put(stat, entry.getValue());
            }
        }
        return new ElysiumStatBlock(Map.copyOf(map));
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Map.Entry<ElysiumStat, Integer> entry : values.entrySet()) {
            if (entry.getValue() != 0) {
                raw.put(entry.getKey().getSerialisedName(), entry.getValue());
            }
        }
        return raw;
    }

    // ------------------------------------------------------------------
    // Reading and combining
    // ------------------------------------------------------------------

    public int get(ElysiumStat stat) {
        Integer value = values.get(stat);
        return value == null ? 0 : value;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** The total across every stat — used to check balance conventions. */
    public int sum() {
        int total = 0;
        for (int value : values.values()) {
            total += value;
        }
        return total;
    }

    public ElysiumStatBlock plus(ElysiumStatBlock other) {
        Map<ElysiumStat, Integer> map = new LinkedHashMap<>(values);
        for (Map.Entry<ElysiumStat, Integer> entry : other.values.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        map.values().removeIf(value -> value == 0);
        return new ElysiumStatBlock(Map.copyOf(map));
    }

    /** Every entry multiplied — how a per-level growth block becomes a total. */
    public ElysiumStatBlock times(int factor) {
        if (factor == 0) {
            return EMPTY;
        }
        Map<ElysiumStat, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<ElysiumStat, Integer> entry : values.entrySet()) {
            int scaled = entry.getValue() * factor;
            if (scaled != 0) {
                map.put(entry.getKey(), scaled);
            }
        }
        return new ElysiumStatBlock(Map.copyOf(map));
    }

    /** Adds to a stat rather than setting it — every call site relies on this. */
    public ElysiumStatBlock with(ElysiumStat stat, int amount) {
        Map<ElysiumStat, Integer> map = new LinkedHashMap<>(values);
        map.merge(stat, amount, Integer::sum);
        map.values().removeIf(value -> value == 0);
        return new ElysiumStatBlock(Map.copyOf(map));
    }
}
