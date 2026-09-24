package com.elysium.lib.affix;

import java.util.ArrayList;
import java.util.List;

/**
 * How an affix is chosen and how strong it comes out.
 *
 * <h2>What an affix is here</h2>
 *
 * A named bundle of item effects — the same effects a trait states, in the
 * same words — that is rolled onto a finished item rather than built into it.
 * Traits are what a piece is made of; affixes are what happened to it.
 *
 * <h2>Pools</h2>
 *
 * An affix names the kinds of gear it can land on, and a pool is every affix
 * that accepts the item in hand. Nothing decides that here — {@code
 * ElysiumGearTree} does — so this is only the arithmetic: weights, rolls and
 * levels, which is the part worth executing without a game.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumAffixRules {

    private ElysiumAffixRules() {
    }

    /** An affix in a pool, with the weight that decides how often it is the one. */
    public record Candidate(String id, int weight) {
    }

    /** Where in an item's name an affix sits. */
    public static final String PREFIX = "prefix";
    public static final String SUFFIX = "suffix";

    /**
     * One affix from a pool, by weight.
     *
     * @param roll anywhere in [0, 1); a roll at or past 1 picks the last
     * @return the chosen id, or null for an empty pool or one that weighs nothing
     */
    public static String pick(List<Candidate> pool, double roll) {
        int total = 0;
        for (Candidate candidate : pool) {
            if (candidate.weight() > 0) {
                total += candidate.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        double at = Math.max(0.0D, Math.min(1.0D, roll)) * total;
        double seen = 0.0D;
        String last = null;
        for (Candidate candidate : pool) {
            if (candidate.weight() <= 0) {
                continue;
            }
            last = candidate.id();
            seen += candidate.weight();
            if (at < seen) {
                return candidate.id();
            }
        }
        return last;               // a roll of exactly 1 lands on the last one
    }

    /**
     * The level an affix rolls at: 1 at the weakest, {@code maxLevel} at the
     * strongest, and never zero — an affix that rolled nothing would be a name
     * on an item that does nothing.
     */
    public static int level(int maxLevel, double roll) {
        if (maxLevel <= 1) {
            return 1;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, roll));
        int level = (int) Math.floor(clamped * maxLevel) + 1;
        return Math.max(1, Math.min(maxLevel, level));
    }

    /**
     * A value interpolated between the weakest and strongest roll.
     *
     * Uncapped on purpose in one direction only: a maximum below a minimum is
     * data stating a range backwards, and reading it backwards is better than
     * silently giving the player the smaller number every time.
     */
    public static double between(double min, double max, double roll) {
        double clamped = Math.max(0.0D, Math.min(1.0D, roll));
        return min + (max - min) * clamped;
    }

    /**
     * The order the parts of a name go in: one prefix, the item, one suffix.
     *
     * A list rather than a formatted string, because the pieces are
     * translation keys and the game puts them together — "of Burning" is a
     * suffix in English and a prefix in languages that do not have one.
     */
    public static List<String> nameParts(String prefix, String suffix) {
        List<String> out = new ArrayList<>();
        if (prefix != null && !prefix.isEmpty()) {
            out.add(prefix);
        }
        out.add("");                           // where the item's own name goes
        if (suffix != null && !suffix.isEmpty()) {
            out.add(suffix);
        }
        return out;
    }

    /**
     * How many affixes an item of this rarity carries: one more for each step
     * up, starting at none.
     *
     * Kept here rather than in the rarity table because it is arithmetic, and
     * because P5 replaces the source of {@code rarity} without changing this.
     */
    public static int count(int rarity) {
        return Math.max(0, rarity);
    }

    /** Whether a type is one an item's name has room for. */
    public static boolean isPlacement(String type) {
        return PREFIX.equals(type) || SUFFIX.equals(type);
    }
}
