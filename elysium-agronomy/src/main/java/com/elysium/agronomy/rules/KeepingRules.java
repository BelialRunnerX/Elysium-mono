package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeping bees: finding them, catching them, breeding them, and not being
 * stung while you do it.
 *
 * <h2>The route to every comb</h2>
 *
 * {@link BeeRules} proves that every bee can be <em>bred</em> from the four
 * you find. That is a claim about a graph. This file is the claim about the
 * game: the four wild bees have a biome to appear in and a nest to be taken
 * from, a jar takes one out of the world and into a hive, a hive of one
 * species treated with a jar of another gives the child the rules name, and
 * so every one of the roster's combs is a thing a player can hold. {@link
 * #unobtainable} walks that route rather than the graph, and the harness
 * runs it on every build.
 *
 * <h2>Why a bee stings</h2>
 *
 * A hive you can rob for nothing is a chest. Taking a comb off a bee or a bee
 * out of the air upsets the ones around it for a stated while, and a stung
 * player loses a stated amount &mdash; unless the smoker has been used first,
 * which is the whole reason a smoker is an item worth making. Every one of
 * those numbers is here, and the harness holds the smoke to outlasting the
 * anger and reaching everything the anger would.
 */
public final class KeepingRules {

    private KeepingRules() {
    }

    // ==================================================================
    // Where each home is, as the game spells it
    // ==================================================================

    private static final Map<String, String> BIOMES = new LinkedHashMap<>();

    private static void biome(String home, String tag) {
        if (home == null || home.isEmpty() || tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("a home with no name or no biome");
        }
        if (BIOMES.put(home, tag) != null) {
            throw new IllegalArgumentException(home + " is given two biomes");
        }
    }

    static {
        biome("plains", "#c:is_plains");
        biome("forest", "#minecraft:is_forest");
        biome("mountain", "#minecraft:is_mountain");
        biome("river", "#minecraft:is_river");
        biome("swamp", "#c:is_swamp");
        biome("desert", "#c:is_desert");
        biome("nether", "#minecraft:is_nether");
        biome("frozen", "#c:is_icy");
        biome("jungle", "#minecraft:is_jungle");
        biome("underground", "#c:is_cave");
        biome("ocean", "#minecraft:is_ocean");
        biome("mushroom", "#c:is_mushroom");
        biome("volcanic", "minecraft:basalt_deltas");
        biome("highlands", "#c:is_plateau");
        biome("deepslate", "minecraft:deep_dark");
        biome("nether_fortress", "minecraft:nether_wastes");
        biome("end", "#minecraft:is_end");
        biome("end_highlands", "minecraft:end_highlands");
    }

    /** The biome tag (or biome) a home means, or empty if nobody has said. */
    public static String biomeOf(String home) {
        String found = BIOMES.get(home);
        return found == null ? "" : found;
    }

    /** Every home a bee has that no biome has been given, and every biome given to no home. */
    public static List<String> homeless() {
        List<String> bad = new ArrayList<>();
        for (String home : BeeRules.homes()) {
            if (biomeOf(home).isEmpty()) {
                bad.add(home + " is a home some bee is bound to and no biome means it");
            }
        }
        for (String home : BIOMES.keySet()) {
            if (!BeeRules.homes().contains(home)) {
                bad.add(home + " is given a biome and no bee lives there");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The wild bees, and the nests they are taken from
    // ==================================================================

    /** The wild bee bound to a home, or null if that home's bees are bred. */
    public static BeeRules.Bee wildAt(String home) {
        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.WILD)) {
            if (bee.home().equals(home)) {
                return bee;
            }
        }
        return null;
    }

    /** Every home a wild bee lives in, once each, sorted. A nest is generated in each. */
    public static List<String> nested() {
        Set<String> seen = new TreeSet<>();
        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.WILD)) {
            seen.add(bee.home());
        }
        return List.copyOf(seen);
    }

    /** How often a wild bee turns up, against the other creatures of its biome. */
    public static final int WILD_SPAWN_WEIGHT = 6;

    /** How few come at once. */
    public static final int WILD_SPAWN_LEAST = 1;

    /** How many at most. */
    public static final int WILD_SPAWN_MOST = 3;

    /** How many bees a nest holds when it is full. */
    public static final int NEST_BEES = 3;

    /** How long a nest takes to grow one bee back. */
    public static final int NEST_REFILL_TICKS = 6_000;

    /** One nest in this many chunks of its biome. */
    public static final int NEST_CHUNKS = 24;

    /**
     * The most bees an hour one nest may give back.
     *
     * <p>A nest that refills faster than a hive works is a hive nobody builds:
     * the wild bee is free, and free is the one price a farm must not have.
     */
    public static final float MOST_NEST_BEES_PER_HOUR = 12.0F;

    /** How many bees an hour a nest grows back. */
    public static float nestBeesPerHour() {
        return (float) AgronomyRules.TICKS_PER_HOUR / NEST_REFILL_TICKS;
    }

    /** The same rate against the same ceiling, handed in so a control can be built to be caught. */
    public static List<String> faultsInNest(float perHour, float ceiling) {
        List<String> bad = new ArrayList<>();
        if (perHour > ceiling) {
            bad.add("a nest grows " + perHour + " bees an hour back and the ceiling is "
                    + ceiling + "; a nest that outpaces a hive is a hive nobody builds");
        }
        return bad;
    }

    // ==================================================================
    // Breeding, which is the hive's job
    // ==================================================================

    /**
     * The bee bred from these two, in either order, or null if that pair
     * breeds nothing.
     *
     * <p>Walked from the roster rather than kept as a table: {@link
     * BeeRules#sharingParents} has already proved no pair breeds two things,
     * so the first match is the only match.
     */
    public static String childOf(String first, String second) {
        if (first == null || second == null) {
            return null;
        }
        for (BeeRules.Bee bee : BeeRules.BEES) {
            if (bee.found()) {
                continue;
            }
            List<String> parents = bee.parents();
            if ((parents.get(0).equals(first) && parents.get(1).equals(second))
                    || (parents.get(0).equals(second) && parents.get(1).equals(first))) {
                return bee.id();
            }
        }
        return null;
    }

    /** Every place breeding through the hive disagrees with the roster. */
    public static List<String> faultsInBreeding() {
        List<String> bad = new ArrayList<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            if (bee.found()) {
                for (BeeRules.Bee other : BeeRules.BEES) {
                    if (bee.id().equals(childOf(other.id(), bee.id()))) {
                        bad.add(bee.id() + " is wild and is also bred, so the tree has a "
                                + "root that is not a root");
                    }
                }
                continue;
            }
            String forwards = childOf(bee.parents().get(0), bee.parents().get(1));
            String backwards = childOf(bee.parents().get(1), bee.parents().get(0));
            if (!bee.id().equals(forwards)) {
                bad.add(bee.id() + " is bred from " + bee.parents() + " and the hive "
                        + "would give " + forwards);
            }
            if (!bee.id().equals(backwards)) {
                bad.add(bee.id() + "'s parents in the other order give " + backwards
                        + ", so which jar is in which hand decides the child");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Stings, and the smoke that stops them
    // ==================================================================

    /** What one sting costs, in health. One heart. */
    public static final float STING_DAMAGE = 2.0F;

    /** How long an upset bee stays upset. */
    public static final int ANGER_TICKS = 400;

    /** How long a smoked bee stays calm. */
    public static final int SMOKE_TICKS = 1_200;

    /** How far the smoke reaches, in blocks. */
    public static final int SMOKE_RANGE = 8;

    /** How far a robbed bee's friends notice, in blocks. */
    public static final int UPSET_RANGE = 6;

    /** The same numbers handed in, so a control can be built to be caught. */
    public static List<String> faultsInTemper(float sting, int anger, int smoke,
                                              int smokeRange, int upsetRange) {
        List<String> bad = new ArrayList<>();
        if (sting <= 0.0F) {
            bad.add("a sting costs nothing, so a smoker is an item with no reason");
        }
        if (sting >= 20.0F) {
            bad.add("a sting costs " + sting + ", which is a full player's health, so "
                    + "a bee is a fight rather than a warning");
        }
        if (anger <= 0) {
            bad.add("a bee is never upset for any time at all");
        }
        if (smoke <= anger) {
            bad.add("smoke lasts " + smoke + " ticks and anger " + anger + ", so the "
                    + "bees come round before the smoke does and the smoker buys nothing");
        }
        if (smokeRange < upsetRange) {
            bad.add("smoke reaches " + smokeRange + " blocks and a robbery upsets bees "
                    + upsetRange + " away, so the bees outside the smoke sting anyway");
        }
        if (upsetRange <= 0) {
            bad.add("no bee anywhere notices a robbery");
        }
        return bad;
    }

    // ==================================================================
    // Whether every comb is a thing a player can hold
    // ==================================================================

    /**
     * Every bee a player cannot get by the game's own route: caught wild in
     * a biome that has been named, or bred in a hive from two bees they can
     * get.
     *
     * <p>This is {@link BeeRules#unreachable} asked of the mechanics rather
     * than of the parent lists, and it is the check the whole file is for.
     */
    public static List<String> unobtainable() {
        Set<String> have = new HashSet<>();
        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.WILD)) {
            if (!biomeOf(bee.home()).isEmpty() && wildAt(bee.home()) != null) {
                have.add(bee.id());
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (BeeRules.Bee bee : BeeRules.BEES) {
                if (have.contains(bee.id()) || bee.found()) {
                    continue;
                }
                String first = bee.parents().get(0);
                String second = bee.parents().get(1);
                if (have.contains(first) && have.contains(second)
                        && bee.id().equals(childOf(first, second))) {
                    have.add(bee.id());
                    grew = true;
                }
            }
        }
        List<String> lost = new ArrayList<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            if (!have.contains(bee.id())) {
                lost.add(bee.id());
            }
        }
        return List.copyOf(lost);
    }

    /** Everything wrong with keeping bees. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(homeless());
        bad.addAll(faultsInNest(nestBeesPerHour(), MOST_NEST_BEES_PER_HOUR));
        bad.addAll(faultsInBreeding());
        bad.addAll(faultsInTemper(STING_DAMAGE, ANGER_TICKS, SMOKE_TICKS,
                SMOKE_RANGE, UPSET_RANGE));
        bad.addAll(unobtainable());
        if (NEST_BEES < 1) {
            bad.add("a nest holds no bees");
        }
        if (NEST_CHUNKS < 1) {
            bad.add("a nest in every chunk is a biome that is nests");
        }
        if (WILD_SPAWN_WEIGHT < 1 || WILD_SPAWN_LEAST < 1
                || WILD_SPAWN_MOST < WILD_SPAWN_LEAST) {
            bad.add("wild bees spawn with weight " + WILD_SPAWN_WEIGHT + " in groups of "
                    + WILD_SPAWN_LEAST + " to " + WILD_SPAWN_MOST);
        }
        if (nested().isEmpty()) {
            bad.add("no home has a wild bee, so no nest is ever generated");
        }
        return List.copyOf(bad);
    }
}
