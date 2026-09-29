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
 * <h2>A nest for every home</h2>
 *
 * Every home has a nest block, and every nest is generated in its home's
 * biome, on the ground {@link #groundOf} names: the land, a cave floor, the
 * nether's floor or the sea bed. A home a wild bee lives in gets its nest
 * full ({@link #NEST_BEES}); a home only bred bees live in gets it empty,
 * so the block can be found and taken home, and the tree still has no
 * shortcut.
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

    /** Every home a wild bee lives in, once each, sorted. Its nest is generated full. */
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

    /**
     * How many bees the nest a home is generated with holds: a full nest
     * where a wild bee lives, and an empty one where every bee is bred.
     *
     * <p>Empty, not missing. A nest nothing generates is a block no survival
     * player can have; a nest generated full of a bred bee is a breeding tree
     * with a shortcut. An empty one is neither.
     */
    public static int nestBees(String home) {
        return wildAt(home) == null ? 0 : NEST_BEES;
    }

    /** How long a nest takes to grow one bee back. */
    public static final int NEST_REFILL_TICKS = 6_000;

    /** One nest in this many chunks of its biome. */
    public static final int NEST_CHUNKS = 24;

    // ==================================================================
    // What ground a nest is built on
    // ==================================================================

    /** On the land under the open sky: the top block, by the game's own surface map. */
    public static final String SURFACE = "surface";

    /** On the floor of a cave: found by walking down through air from a point in {@link #CAVE_LOWEST} to {@link #CAVE_HIGHEST}. */
    public static final String CAVE_FLOOR = "cave_floor";

    /**
     * On the nether's floor: found the same way from a point in {@link
     * #NETHER_LOWEST} to {@link #NETHER_HIGHEST}. The nether's surface map is
     * its bedrock roof, so a nest built on the "surface" there sits on the
     * ceiling of the world.
     */
    public static final String NETHER_FLOOR = "nether_floor";

    /** On the sea bed, under the water, by the game's ocean-floor map. */
    public static final String SEA_BED = "sea_bed";

    /** Every ground there is. */
    public static final List<String> GROUNDS = List.of(SURFACE, CAVE_FLOOR, NETHER_FLOOR, SEA_BED);

    private static final Map<String, String> GROUND = new LinkedHashMap<>();

    private static void ground(String home, String where) {
        if (!GROUNDS.contains(where)) {
            throw new IllegalArgumentException(home + " is built on " + where
                    + ", which is no ground");
        }
        if (GROUND.put(home, where) != null) {
            throw new IllegalArgumentException(home + " is given two grounds");
        }
    }

    static {
        ground("plains", SURFACE);
        ground("forest", SURFACE);
        ground("mountain", SURFACE);
        ground("river", SURFACE);
        ground("swamp", SURFACE);
        ground("desert", SURFACE);
        ground("nether", NETHER_FLOOR);
        ground("frozen", SURFACE);
        ground("jungle", SURFACE);
        ground("underground", CAVE_FLOOR);
        ground("ocean", SEA_BED);
        ground("mushroom", SURFACE);
        ground("volcanic", NETHER_FLOOR);
        ground("highlands", SURFACE);
        ground("deepslate", CAVE_FLOOR);
        ground("nether_fortress", NETHER_FLOOR);
        ground("end", SURFACE);
        ground("end_highlands", SURFACE);
    }

    /** The ground a home's nest is built on, or empty if nobody has said. */
    public static String groundOf(String home) {
        String found = GROUND.get(home);
        return found == null ? "" : found;
    }

    /** The overworld's bedrock floor. */
    public static final int OVERWORLD_FLOOR = -64;

    /** The nether's lava sea, which a nest must not be built under. */
    public static final int NETHER_LAVA_SEA = 31;

    /** The nether's bedrock roof. */
    public static final int NETHER_ROOF = 128;

    /**
     * The lowest point a cave nest is looked for from: ten above the bedrock
     * floor, so a nest is never built into it.
     */
    public static final int CAVE_LOWEST = -54;

    /**
     * The highest: y 0, where the deepslate starts. Under it the deep dark
     * lies, and the lush and dripstone caves are at their widest.
     */
    public static final int CAVE_HIGHEST = 0;

    /** The lowest point a nether nest is looked for from: one above the lava sea. */
    public static final int NETHER_LOWEST = 32;

    /** The highest: well under the roof, in the caverns a player walks. */
    public static final int NETHER_HIGHEST = 100;

    /** How far down through air a nest below the surface looks for a floor. The game allows 32. */
    public static final int FLOOR_SCAN = 24;

    /**
     * How many points a nest below the surface is looked for from, in the
     * one chunk in {@link #NEST_CHUNKS} chosen for it.
     *
     * <p>Underground, a point picked at random is mostly rock, and a look
     * for a floor that starts in rock finds nothing. Four tries is what keeps
     * one nest in twenty-four chunks roughly true below the surface as well
     * as on it.
     */
    public static final int NEST_TRIES_BELOW = 4;

    /** Everything wrong with where nests are built. Must be empty. */
    public static List<String> faultsInGrounds() {
        List<String> bad = new ArrayList<>(faultsInBands(CAVE_LOWEST, CAVE_HIGHEST,
                NETHER_LOWEST, NETHER_HIGHEST, FLOOR_SCAN, NEST_TRIES_BELOW));
        for (String home : BeeRules.homes()) {
            if (groundOf(home).isEmpty()) {
                bad.add(home + " has a nest block and no ground to build it on, so no "
                        + "world ever makes one and no survival player can have it");
            }
        }
        for (String home : GROUND.keySet()) {
            if (!BeeRules.homes().contains(home)) {
                bad.add(home + " is given a ground and no bee lives there");
            }
        }
        return List.copyOf(bad);
    }

    /** The bands and the looking handed in, so a control can be built to be caught. */
    public static List<String> faultsInBands(int caveLowest, int caveHighest, int netherLowest,
                                             int netherHighest, int scan, int tries) {
        List<String> bad = new ArrayList<>();
        if (caveLowest <= OVERWORLD_FLOOR || caveLowest >= caveHighest) {
            bad.add("a cave nest is looked for from " + caveLowest + " to " + caveHighest
                    + ", which is no band above the bedrock floor at " + OVERWORLD_FLOOR);
        }
        if (netherLowest <= NETHER_LAVA_SEA || netherHighest >= NETHER_ROOF
                || netherLowest >= netherHighest) {
            bad.add("a nether nest is looked for from " + netherLowest + " to "
                    + netherHighest + ", which is not between the lava sea at "
                    + NETHER_LAVA_SEA + " and the roof at " + NETHER_ROOF);
        }
        if (scan < 1 || scan > 32) {
            bad.add("a nest looks " + scan + " block(s) down for a floor, and the game "
                    + "looks 1 to 32");
        }
        if (tries < 1) {
            bad.add("a nest below the surface is looked for from " + tries
                    + " point(s), so none is ever built");
        }
        return bad;
    }

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
        bad.addAll(faultsInGrounds());
        return List.copyOf(bad);
    }
}
