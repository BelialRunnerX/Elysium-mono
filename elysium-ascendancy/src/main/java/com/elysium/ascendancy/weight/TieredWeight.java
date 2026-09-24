package com.elysium.ascendancy.weight;

import java.util.Map;
import java.util.TreeMap;

/**
 * How likely something is to be rolled, at each tier of progression.
 *
 * <h2>The rule</h2>
 *
 * Each tier has a {@code weight} and a {@code quality}. What a roll sees is
 * {@code weight + round(luck * quality)}: luck makes high-quality entries
 * likelier without touching the rest, which is how a luckier player finds
 * better things rather than just more things. A tier with no entry has weight
 * zero — an entry that is not listed for a tier cannot appear in it, which is
 * how late content stays out of early loot.
 *
 * The one floor is zero. A negative weight is not "rarer", it is nonsense —
 * it would subtract from the total and make everything else likelier — so it
 * counts as none. There is no ceiling; luck is uncapped and so is what it buys.
 *
 * Tiers are plain numbers here. What they are called, and what moves a player
 * between them, is a later decision (docs/SPEC_CLONE.md, "World tiers vs
 * standing"); this is only the arithmetic every roll will share.
 *
 * <p>Imports nothing Minecraft, and must not: it is executed by a harness.
 */
public final class TieredWeight {

    /** One tier's numbers. */
    public record Entry(int weight, float quality) {
    }

    /** The same numbers at every tier: what most entries want. */
    private final Entry everywhere;
    /** Or tier by tier; a tier missing from the map has weight zero. */
    private final Map<Integer, Entry> byTier;

    private TieredWeight(Entry everywhere, Map<Integer, Entry> byTier) {
        this.everywhere = everywhere;
        this.byTier = byTier;
    }

    public static TieredWeight everywhere(int weight, float quality) {
        return new TieredWeight(new Entry(weight, quality), null);
    }

    public static TieredWeight byTier(Map<Integer, Entry> tiers) {
        return new TieredWeight(null, new TreeMap<>(tiers));
    }

    /** The entry for a tier, or null if this cannot appear there. */
    public Entry at(int tier) {
        return everywhere != null ? everywhere : byTier.get(tier);
    }

    /** The weight a roll at this tier, with this luck, sees. Never negative. */
    public long weightAt(int tier, double luck) {
        Entry e = at(tier);
        if (e == null) {
            return 0L;
        }
        double l = Double.isNaN(luck) ? 0.0D : luck;
        double q = Float.isNaN(e.quality()) ? 0.0D : e.quality();
        double raw = e.weight() + (double) Math.round(l * q);
        if (!(raw > 0.0D)) {
            return 0L;
        }
        // Saturating: a weight past a long is "overwhelmingly likely", not a
        // wrapped negative that removes the entry.
        return raw >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) raw;
    }
}
