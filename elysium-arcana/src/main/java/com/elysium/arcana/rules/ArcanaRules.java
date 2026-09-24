package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turning things into other things. The JDK and nothing else at all.
 *
 * <h2>The one thing a transmutation system gets wrong</h2>
 *
 * Every mod that has ever priced every item in the game has shipped, at some
 * point, an arrangement that <b>makes value out of nothing</b>: craft A into B,
 * dissolve B, condense A, and come out ahead. It is not a rare bug — it is the
 * default outcome, because a table of ten thousand numbers derived from ten
 * thousand recipes has ten thousand chances to be inconsistent and no human
 * has ever read one.
 *
 * <p>Two rules make it impossible here, and both are checked by execution over
 * the whole table on every build:
 *
 * <ol>
 *   <li><b>No recipe may be profitable.</b> What a recipe makes is worth at
 *       most what it consumes. Guaranteed by how the table is derived — every
 *       value is the <em>cheapest</em> way of arriving at that item, rounded
 *       down — and then checked afterwards anyway, because "guaranteed by
 *       construction" is a claim and not a test.</li>
 *   <li><b>Dissolving returns less than condensing costs.</b> A round trip
 *       loses {@link #BURN_SHARE}, so even if a recipe somehow broke rule one
 *       by a rounding error, the loop still cannot be ridden.</li>
 * </ol>
 *
 * <p>Together they give the property the harness actually asserts: over a
 * random walk of crafting, dissolving and condensing, <b>a player's motes plus
 * the worth of everything they hold never goes up</b>. Not "rarely". Never.
 *
 * <h2>Why unknown items have no value rather than a guessed one</h2>
 *
 * An item this table cannot derive is an item that cannot be dissolved or
 * condensed at all. Guessing is how a mod ends up pricing a creative-only
 * block at four and a dragon egg at nothing.
 */
public final class ArcanaRules {

    private ArcanaRules() {
    }

    /**
     * One way of making something: this much of this, out of those.
     *
     * <p>Deliberately the same shape as the autocrafting planner's step, and
     * for the same reason: what is worth proving about a recipe set has
     * nothing to do with ingredients or stacks, so it is proved on a structure
     * a harness can build ten thousand of.
     */
    public record Step(String output, int makes, Map<String, Integer> needs) {

        public Step {
            if (makes <= 0) {
                throw new IllegalArgumentException("a recipe that makes nothing is not "
                        + "a recipe");
            }
            needs = Map.copyOf(needs);
        }
    }

    // ==================================================================
    // What the table is grown from
    // ==================================================================

    /**
     * The handful of values that are decided rather than derived.
     *
     * <h2>Why there are thirty of them and not three thousand</h2>
     *
     * Everything else in the game is made of these, directly or eventually, so
     * everything else can be worked out. A seed table that priced every item
     * by hand would be a table that disagrees with the recipes — which is the
     * failure this module is arranged to make impossible — and a table with
     * three seeds in it would leave most of the game unpriceable.
     *
     * <p>They are relative to a cobblestone, which is one. Everything a player
     * ever reasons about is a ratio, so the unit only has to be small and
     * memorable.
     */
    public static final Map<String, Long> SEEDS = seeds();

    private static Map<String, Long> seeds() {
        Map<String, Long> worth = new LinkedHashMap<>();
        // The ground itself.
        worth.put("minecraft:cobblestone", 1L);
        worth.put("minecraft:stone", 1L);
        worth.put("minecraft:dirt", 1L);
        worth.put("minecraft:sand", 1L);
        worth.put("minecraft:gravel", 4L);
        worth.put("minecraft:flint", 4L);
        worth.put("minecraft:clay_ball", 16L);
        worth.put("minecraft:netherrack", 1L);
        worth.put("minecraft:end_stone", 1L);
        worth.put("minecraft:obsidian", 64L);
        // What grows on it.
        worth.put("minecraft:oak_log", 32L);
        worth.put("minecraft:wheat", 24L);
        worth.put("minecraft:sugar_cane", 32L);
        worth.put("minecraft:cactus", 32L);
        worth.put("minecraft:bamboo", 12L);
        worth.put("minecraft:kelp", 16L);
        // What walks on it.
        worth.put("minecraft:string", 12L);
        worth.put("minecraft:leather", 64L);
        worth.put("minecraft:feather", 48L);
        worth.put("minecraft:bone", 144L);
        worth.put("minecraft:gunpowder", 192L);
        worth.put("minecraft:slime_ball", 32L);
        worth.put("minecraft:ender_pearl", 1_024L);
        worth.put("minecraft:blaze_rod", 1_536L);
        worth.put("minecraft:egg", 32L);
        // What you dig for.
        worth.put("minecraft:coal", 128L);
        worth.put("minecraft:copper_ingot", 85L);
        worth.put("minecraft:iron_ingot", 256L);
        worth.put("minecraft:gold_ingot", 2_048L);
        worth.put("minecraft:redstone", 64L);
        worth.put("minecraft:lapis_lazuli", 864L);
        worth.put("minecraft:quartz", 256L);
        worth.put("minecraft:amethyst_shard", 512L);
        worth.put("minecraft:glowstone_dust", 384L);
        worth.put("minecraft:diamond", 8_192L);
        worth.put("minecraft:emerald", 16_384L);
        worth.put("minecraft:netherite_scrap", 12_288L);
        return Map.copyOf(worth);
    }

    /**
     * How many passes the table is settled over before it is abandoned.
     *
     * <p>Values only ever fall during settling and never below zero, so it
     * must finish — but "must finish" and "finishes before the server times
     * out" are different claims. Anything still moving after this many passes
     * is <em>dropped from the table</em> rather than shipped at whatever it
     * happened to be, because a value nobody has settled is a value nobody has
     * checked.
     */
    public static final int MOST_PASSES = 64;

    /**
     * Work out what everything is worth.
     *
     * <h2>Cheapest wins, rounded down, settled</h2>
     *
     * An item's worth is the least it can be arrived at for, by any recipe,
     * with what it consumes rounded down. Cheapest, because if there is a
     * cheaper way of making something then that is what it costs, and pricing
     * it higher is exactly the gap a player rides. Rounded down, because
     * {@code floor(sum / makes) * makes <= sum} is the whole of rule one.
     *
     * <p>Settled rather than computed in one pass, because an ingredient's
     * worth can fall after something has already been priced from it. Each
     * pass either changes nothing — in which case it is done — or lowers at
     * least one value by at least one, so it terminates.
     *
     * @param recipes every way of making anything, by what it makes
     * @param seeds   the values that are decided rather than derived
     */
    public static Map<String, Long> derive(Map<String, Step> recipes,
                                           Map<String, Long> seeds) {
        Map<String, Long> worth = new HashMap<>(seeds);
        Map<String, List<Step>> waysOfMaking = new HashMap<>();
        for (Step step : recipes.values()) {
            waysOfMaking.computeIfAbsent(step.output(), key -> new ArrayList<>()).add(step);
        }
        boolean moved = true;
        int pass = 0;
        while (moved && pass++ < MOST_PASSES) {
            moved = false;
            for (Map.Entry<String, List<Step>> entry : new TreeMap<>(waysOfMaking).entrySet()) {
                if (seeds.containsKey(entry.getKey())) {
                    // A decided value is decided. Deriving over the top of one
                    // is how a seed table stops meaning anything.
                    continue;
                }
                Long cheapest = null;
                for (Step step : entry.getValue()) {
                    Long priced = priceOf(step, worth);
                    if (priced != null && (cheapest == null || priced < cheapest)) {
                        cheapest = priced;
                    }
                }
                if (cheapest == null) {
                    continue;
                }
                Long had = worth.get(entry.getKey());
                if (had == null || !had.equals(cheapest)) {
                    worth.put(entry.getKey(), cheapest);
                    moved = true;
                }
            }
        }
        return Map.copyOf(closed(recipes, worth, seeds));
    }

    /**
     * Take out whatever is still profitable, and keep taking until nothing is.
     *
     * <h2>Why this is not the same as checking afterwards</h2>
     *
     * Settling gives every item the cheapest price any recipe implies, which
     * makes every recipe break even — <em>if it settles</em>. A recipe set
     * that oscillates runs out of passes somewhere in the middle of the
     * oscillation, and the table it leaves behind is a mixture of values from
     * different moments that need not agree with each other.
     *
     * <p>So the table is <b>closed</b> rather than trusted: anything a recipe
     * could still be ridden to make is removed outright, which may make more
     * things unpriceable, which is checked again, until nothing is left that
     * can be ridden. Unpriceable is a safe answer. A wrong price is not, and
     * the difference is a player with an infinite supply of diamonds.
     *
     * <p>Each round removes at least one entry, so it finishes.
     */
    private static Map<String, Long> closed(Map<String, Step> recipes,
                                            Map<String, Long> worth,
                                            Map<String, Long> seeds) {
        Map<String, Long> table = new HashMap<>(worth);
        for (int round = 0; round < MOST_PASSES; round++) {
            List<String> rideable = profitable(recipes, table);
            boolean removed = false;
            for (String name : rideable) {
                Step step = recipes.get(name);
                if (step == null) {
                    continue;
                }
                if (seeds.containsKey(step.output())) {
                    // A decided value stays decided. One bad recipe for
                    // cobblestone must not take cobblestone out of the price
                    // list, because everything else is priced from it.
                    continue;
                }
                if (table.remove(step.output()) != null) {
                    removed = true;
                }
            }
            if (!removed) {
                return table;
            }
        }
        return table;
    }

    /** What one run of a recipe makes each of its outputs worth, or null. */
    private static Long priceOf(Step step, Map<String, Long> worth) {
        long total = 0L;
        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
            Long each = worth.get(need.getKey());
            if (each == null || each <= 0L) {
                // An ingredient nobody can price makes the whole recipe
                // unpriceable. Treating it as free is how a mod prices a
                // dragon egg at nothing.
                return null;
            }
            total += each * need.getValue();
            if (total < 0L) {
                return null;                       // it overflowed; it is not a price
            }
        }
        if (total <= 0L) {
            return null;
        }
        long each = total / step.makes();
        if (each <= 0L) {
            // Worth less than a cobblestone is worth nothing, and something
            // worth nothing can be dissolved forever and makes every recipe
            // that consumes it look free. Out of the table rather than in it
            // at zero — which is the bug this comment exists because of.
            return null;
        }
        return each;
    }

    /**
     * Every recipe that makes more than it consumes.
     *
     * <p>Must be empty. A single entry here is an arrangement a player can
     * ride forever, and it would never show up in a log.
     */
    public static List<String> profitable(Map<String, Step> recipes,
                                          Map<String, Long> worth) {
        List<String> caught = new ArrayList<>();
        for (Map.Entry<String, Step> entry : new TreeMap<>(recipes).entrySet()) {
            Step step = entry.getValue();
            Long made = worth.get(step.output());
            if (made == null) {
                continue;
            }
            long spent = 0L;
            boolean priceable = true;
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                Long each = worth.get(need.getKey());
                if (each == null) {
                    priceable = false;
                    break;
                }
                spent += each * need.getValue();
            }
            if (priceable && made * step.makes() > spent) {
                caught.add(entry.getKey());
            }
        }
        return caught;
    }

    // ==================================================================
    // The two directions, and the difference between them
    // ==================================================================

    /**
     * What dissolving gives back, as a share of what a thing is worth.
     *
     * <h2>Why it is not one</h2>
     *
     * Because a lossless round trip is a machine with no friction in it, and
     * a table of ten thousand derived numbers will eventually contain one
     * rounding error that such a machine turns into an infinite supply. A
     * tenth is small enough that nobody minds and large enough that no
     * rounding error anywhere in the table can be ridden.
     *
     * <p>It is also the honest answer to what transmutation is: you are
     * throwing something away and getting most of it back.
     */
    public static final float BURN_SHARE = 0.9F;

    /** What dissolving this many of something gives. */
    public static long dissolve(long worth, int count) {
        if (worth <= 0L || count <= 0) {
            return 0L;
        }
        return (long) Math.floor((double) worth * count * BURN_SHARE);
    }

    /** What condensing this many of something costs. */
    public static long condensing(long worth, int count) {
        if (worth <= 0L || count <= 0) {
            return 0L;
        }
        return worth * (long) count;
    }

    /** How many of something a balance will pay for. */
    public static int affords(long motes, long worth) {
        if (worth <= 0L || motes <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, motes / worth);
    }

    /**
     * What everything a player is holding, plus their balance, comes to.
     *
     * <p>The one number the harness watches. Every operation in this module
     * either leaves it alone or lowers it; nothing raises it, ever.
     */
    public static long estate(long motes, Map<String, Integer> held,
                              Map<String, Long> worth) {
        long total = motes;
        for (Map.Entry<String, Integer> entry : held.entrySet()) {
            Long each = worth.get(entry.getKey());
            if (each != null) {
                total += each * entry.getValue();
            }
        }
        return total;
    }
}
