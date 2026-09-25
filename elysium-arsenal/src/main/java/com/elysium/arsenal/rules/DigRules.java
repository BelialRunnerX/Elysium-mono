package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Brushing: the slowest way to get anything, and the only way to get some of it.
 *
 * <h2>What the game already has, and what is wrong with it</h2>
 *
 * The game ships one brush, two suspicious blocks and a handful of sherds. The
 * brush is deliberately slow — that is the point of it — but it never gets any
 * faster, so the hundredth site is exactly as tedious as the first and nobody
 * digs a hundredth site. A tier ladder fixes that, and it has to fix it
 * without ever making brushing <em>fast</em>, because a fast brush is a block
 * that drops its loot when you hit it and there is no reason for the mechanic
 * to exist.
 *
 * <h2>The rule the whole thing rests on</h2>
 *
 * What a site holds is decided by <em>where</em> it is, not by when it is
 * brushed. A player who brushes a block halfway, walks away and comes back has
 * to find the same thing waiting; a player who does not like what they found
 * must not be able to break the block, place it again and roll for something
 * better. {@link #whatIsBuried} is a function of the position and the seed and
 * of nothing else, and that is what makes the loot a place rather than a
 * lottery.
 */
public final class DigRules {

    private DigRules() {
    }

    // ==================================================================
    // The brushes
    // ==================================================================

    /**
     * One brush.
     *
     * @param name  what it is called
     * @param ticks how long one brushing takes
     * @param reach how many blocks out it can be used from
     */
    public record Brush(String name, int ticks, float reach) {

        public Brush {
            Objects.requireNonNull(name, "a brush with no name");
            if (ticks < FASTEST) {
                throw new IllegalArgumentException(name + " brushes in " + ticks
                        + " ticks, and the fastest a brush may ever be is " + FASTEST
                        + " — below that it is a block that drops its loot when you "
                        + "hit it, and the mechanic has no reason to exist");
            }
            if (reach <= 0.0F) {
                throw new IllegalArgumentException(name + " cannot reach anything");
            }
        }

        /** How many brushings a whole site takes at this speed, in ticks. */
        public int aWholeSite() {
            return ticks * STAGES;
        }
    }

    /**
     * The fastest any brush may be.
     *
     * <p>The game's own is ten. Half that is still four times as slow as
     * breaking the block would be, which is the band the mechanic lives in.
     */
    public static final int FASTEST = 4;

    /** How many stages a site goes through before it gives anything up. */
    public static final int STAGES = 4;

    /** The game's own brush, which ours are measured against. */
    public static final int VANILLA_TICKS = 10;

    /** Every brush, slowest first. */
    public static final List<Brush> BRUSHES = List.of(
            new Brush("iron_brush", 8, 4.0F),
            new Brush("diamond_brush", 6, 4.5F),
            new Brush("netherite_brush", 4, 5.0F));

    /** The brush of that name, or null. */
    public static Brush brush(String name) {
        for (Brush one : BRUSHES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // What can be brushed, and what comes out
    // ==================================================================

    /**
     * One thing that can be brushed.
     *
     * @param block what it is
     * @param below the block it leaves behind
     * @param pool  the name of what it holds
     */
    public record Site(String block, String below, String pool) {

        public Site {
            Objects.requireNonNull(block, "a site that is no block");
            Objects.requireNonNull(below, "a site that leaves nothing behind");
            if (block.equals(below)) {
                throw new IllegalArgumentException(block + " leaves itself behind, so "
                        + "it can be brushed for ever");
            }
        }
    }

    /** Every brushable block this module adds. */
    public static final List<Site> SITES = List.of(
            new Site("suspicious_dirt", "minecraft:dirt", "dirt"),
            new Site("suspicious_red_sand", "minecraft:red_sand", "red_sand"),
            new Site("fossiliferous_dirt", "minecraft:coarse_dirt", "fossil"));

    /**
     * One thing a site can give up, and how likely it is.
     *
     * @param item   what it is
     * @param weight how likely, against the rest of its pool
     */
    public record Find(String item, int weight) {

        public Find {
            Objects.requireNonNull(item, "a find that is nothing");
            if (weight < 1) {
                throw new IllegalArgumentException(item + " has a weight of " + weight
                        + ", so it can never be found and is a thing that does not "
                        + "exist");
            }
        }
    }

    /** What each pool holds. */
    public static final Map<String, List<Find>> POOLS = pools();

    private static Map<String, List<Find>> pools() {
        Map<String, List<Find>> made = new LinkedHashMap<>();
        made.put("dirt", List.of(
                new Find("minecraft:bone", 30),
                new Find("minecraft:flint", 24),
                new Find("minecraft:clay_ball", 18),
                new Find("elysiumarsenal:artifact_shard", 12),
                new Find("minecraft:emerald", 8),
                new Find("minecraft:music_disc_relic", 4),
                new Find("elysiumarsenal:legend_sigil", 1)));
        made.put("red_sand", List.of(
                new Find("minecraft:bone", 28),
                new Find("minecraft:gunpowder", 22),
                new Find("minecraft:gold_nugget", 18),
                new Find("elysiumarsenal:artifact_shard", 14),
                new Find("minecraft:gold_ingot", 9),
                new Find("minecraft:diamond", 5),
                new Find("elysiumarsenal:legend_sigil", 1)));
        made.put("fossil", List.of(
                new Find("minecraft:bone", 34),
                new Find("minecraft:bone_meal", 20),
                new Find("elysiumarsenal:chiseled_bone_block", 16),
                new Find("elysiumarsenal:artifact_shard", 16),
                new Find("minecraft:amethyst_shard", 8),
                new Find("minecraft:echo_shard", 5),
                new Find("elysiumarsenal:legend_sigil", 1)));
        return Map.copyOf(made);
    }

    /** The weights of a pool, added up. */
    public static int weightOf(String pool) {
        int total = 0;
        for (Find one : POOLS.getOrDefault(pool, List.of())) {
            total += one.weight();
        }
        return total;
    }

    /**
     * What is buried at a place.
     *
     * <h2>Why this takes a position and not a random number</h2>
     *
     * Because a site has to be a place rather than a lottery. A player who
     * brushes halfway, logs out and comes back must find the same thing; and a
     * player who does not like what they found must not be able to break the
     * block, put it back and roll again. Both of those are the same bug, and
     * both of them are only avoided by the answer being a function of where
     * the block is.
     *
     * <p>The mixing is deliberate rather than decorative. Multiplying the
     * three coordinates by different large odd numbers and stirring the result
     * is what stops two sites a block apart from holding the same thing — a
     * hash that simply added the coordinates would give a whole diagonal plane
     * of identical loot, which a player notices in about ten minutes.
     */
    public static Find whatIsBuried(String pool, long seed, int x, int y, int z) {
        List<Find> finds = POOLS.get(pool);
        if (finds == null || finds.isEmpty()) {
            return null;
        }
        long mixed = seed;
        mixed = mixed * 6_364_136_223_846_793_005L + (x * 3_129_871L);
        mixed = mixed * 6_364_136_223_846_793_005L + (y * 116_129_781L);
        mixed = mixed * 6_364_136_223_846_793_005L + (z * 1_296_871L);
        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);
        int landed = (int) Math.floorMod(mixed, weightOf(pool));
        for (Find one : finds) {
            landed -= one.weight();
            if (landed < 0) {
                return one;
            }
        }
        return finds.get(finds.size() - 1);
    }

    // ==================================================================
    // Whether any of it hangs together
    // ==================================================================

    /** Every brush that is not faster than the one below it. */
    public static List<String> ladderDips() {
        List<String> bad = new ArrayList<>();
        for (int rung = 1; rung < BRUSHES.size(); rung++) {
            Brush below = BRUSHES.get(rung - 1);
            Brush above = BRUSHES.get(rung);
            if (above.ticks() >= below.ticks()) {
                bad.add(above.name() + " brushes no faster than " + below.name()
                        + ", so that rung buys nothing and nobody makes one");
            }
            if (above.reach() < below.reach()) {
                bad.add(above.name() + " reaches less far than " + below.name());
            }
        }
        return List.copyOf(bad);
    }

    /** Every brush that is not slower than simply breaking the block. */
    public static List<String> tooFast() {
        List<String> bad = new ArrayList<>();
        for (Brush one : BRUSHES) {
            if (one.ticks() < FASTEST) {
                bad.add(one.name() + " brushes in " + one.ticks() + " ticks, which is "
                        + "faster than the floor, so it is a block that drops its "
                        + "loot when you hit it");
            }
        }
        return List.copyOf(bad);
    }

    /** Every brush no slower than the game's own, at the bottom of the ladder. */
    public static List<String> replacingTheGamesBrush() {
        List<String> bad = new ArrayList<>();
        Brush slowest = BRUSHES.isEmpty() ? null : BRUSHES.get(0);
        if (slowest != null && slowest.ticks() < VANILLA_TICKS / 2) {
            bad.add(slowest.name() + " is the first rung and is already more than "
                    + "twice the game's own brush, so the game's brush is a crafting "
                    + "ingredient rather than a tool");
        }
        return List.copyOf(bad);
    }

    /** Every site that holds a pool nothing fills. */
    public static List<String> emptySites() {
        List<String> bad = new ArrayList<>();
        for (Site site : SITES) {
            List<Find> finds = POOLS.get(site.pool());
            if (finds == null || finds.isEmpty()) {
                bad.add(site.block() + " holds the " + site.pool() + " pool and "
                        + "nothing is in it, so brushing it gives nothing and the "
                        + "player has no way to know that is a bug");
            }
        }
        for (String pool : POOLS.keySet()) {
            boolean used = false;
            for (Site site : SITES) {
                if (site.pool().equals(pool)) {
                    used = true;
                }
            }
            if (!used) {
                bad.add("the " + pool + " pool is filled and nothing holds it");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every pool whose rarest find is so rare nobody will ever see it.
     *
     * <p>A weight of one in a thousand is not rare, it is absent: a player
     * would have to brush a thousand sites, and brushing a site takes half a
     * minute. The floor here is what keeps "rare" meaning something a person
     * could actually reach.
     */
    public static final double RAREST_WORTH_HAVING = 1.0D / 200.0D;

    public static List<String> unreachablyRare() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, List<Find>> row : POOLS.entrySet()) {
            int total = weightOf(row.getKey());
            for (Find one : row.getValue()) {
                double chance = (double) one.weight() / total;
                if (chance < RAREST_WORTH_HAVING) {
                    bad.add(one.item() + " turns up in one site in "
                            + Math.round(1.0D / chance) + ", which at half a minute a "
                            + "site is not rare, it is absent");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every pool holding the same thing twice. */
    public static List<String> repeatedFinds() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, List<Find>> row : POOLS.entrySet()) {
            Set<String> seen = new TreeSet<>();
            for (Find one : row.getValue()) {
                if (!seen.add(one.item())) {
                    bad.add(one.item() + " is in the " + row.getKey()
                            + " pool twice, which is one entry with the wrong weight");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every name this module needs on the way into a game. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        for (Brush one : BRUSHES) {
            all.add(one.name());
        }
        for (Site site : SITES) {
            all.add(site.block());
        }
        return List.copyOf(all);
    }
}
