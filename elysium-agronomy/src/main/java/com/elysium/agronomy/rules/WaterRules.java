package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The water a cast actually lands in, and the fish that swim there on their
 * own.
 *
 * <h2>Two halves of one habitat</h2>
 *
 * {@link FishRules} says where a fish lives in the abstract: a kind of water,
 * a depth band and a time of day. A rod in somebody's hands lands in a real
 * pond, and the game has to decide which of those abstract waters that pond
 * is &mdash; how many blocks of water are under the float, whether the biome
 * is the sea, whether the liquid is lava at all. And a fish that is only ever
 * seen on the end of a line is half a fish: it should also be <em>in</em>
 * the water, swimming, where a bucket can reach it.
 *
 * <p>So every number that turns a real pond into a {@link FishRules.Water},
 * and every number that puts a fish in the water without a rod, is here. The
 * game reads them; the harness proves them; neither is a number typed into
 * an event handler that the arithmetic has never seen.
 *
 * <h2>The void</h2>
 *
 * The End has no water. Its fish are the one family that is rod-only: a hook
 * cast off the edge of an island falls into the void and, {@link #VOID_DEPTH}
 * blocks under the world's floor, floats. Nothing swims there on its own, so
 * no spawn is written for it, and {@link #spawnsWild} is what says so rather
 * than a missing file.
 */
public final class WaterRules {

    private WaterRules() {
    }

    // ==================================================================
    // Where each family swims on its own
    // ==================================================================

    private static final Map<FishRules.Family, String> HAUNTS = new LinkedHashMap<>();

    private static void haunt(FishRules.Family family, String biomes) {
        Objects.requireNonNull(family, "a haunt for no family");
        if (biomes == null || biomes.isEmpty()) {
            throw new IllegalArgumentException(family.name() + " haunts nowhere");
        }
        if (HAUNTS.put(family, biomes) != null) {
            throw new IllegalArgumentException(family.name() + " is given two haunts");
        }
    }

    static {
        haunt(FishRules.RIVER, "#minecraft:is_river");
        haunt(FishRules.LAKE, "#c:is_plains");
        haunt(FishRules.MARSH, "#c:is_swamp");
        haunt(FishRules.REEF, "#c:is_shallow_ocean");
        haunt(FishRules.OCEAN, "#minecraft:is_ocean");
        haunt(FishRules.DEEP, "#minecraft:is_deep_ocean");
        haunt(FishRules.ABYSS, "#minecraft:is_deep_ocean");
        haunt(FishRules.ARCTIC, "#c:is_aquatic_icy");
        haunt(FishRules.CAVE, "#c:is_cave");
        haunt(FishRules.NETHER, "#minecraft:is_nether");
    }

    /** The biome tag a family's fish are put into, or empty for a rod-only family. */
    public static String hauntOf(FishRules.Family family) {
        String found = HAUNTS.get(family);
        return found == null ? "" : found;
    }

    /** Whether a family's fish swim on their own, or only ever come up on a line. */
    public static boolean spawnsWild(FishRules.Family family) {
        return !FishRules.VOID.equals(family.water());
    }

    /** Every family that spawns on its own, in roster order. */
    public static List<FishRules.Family> wildFamilies() {
        List<FishRules.Family> made = new ArrayList<>();
        for (FishRules.Family family : FishRules.FAMILIES) {
            if (spawnsWild(family)) {
                made.add(family);
            }
        }
        return List.copyOf(made);
    }

    /**
     * Every family that should be in the water and is nowhere, and every
     * rod-only family that has been given somewhere to be.
     */
    public static List<String> homeless() {
        List<String> bad = new ArrayList<>();
        for (FishRules.Family family : FishRules.FAMILIES) {
            boolean wild = spawnsWild(family);
            boolean placed = !hauntOf(family).isEmpty();
            if (wild && !placed) {
                bad.add(family.name() + " fish swim on their own and have no biome "
                        + "to swim in, so they are only ever seen on a line");
            }
            if (!wild && placed) {
                bad.add(family.name() + " is a rod-only family given a haunt, which "
                        + "would spawn fish into a dimension with no water");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Which biomes are the sea
    // ==================================================================

    /**
     * The biome tags whose water is salt. Anything else with water in it is
     * fresh, which is why the sea is the list and the land is not.
     */
    public static final List<String> SALT_BIOMES = List.of(
            "#minecraft:is_ocean", "#minecraft:is_deep_ocean", "#minecraft:is_beach");

    // ==================================================================
    // How deep a pond is
    // ==================================================================

    /**
     * How many blocks of water under the float make each band: surface,
     * shallow, deep, abyss. A column of at least the floor is that band.
     */
    public static final int[] BAND_FLOORS = {0, 3, 7, 15};

    /** The band a column this many blocks deep is in. */
    public static int bandOf(int depth) {
        int band = 0;
        for (int at = 0; at < BAND_FLOORS.length && at < FishRules.BANDS; at++) {
            if (depth >= BAND_FLOORS[at]) {
                band = at;
            }
        }
        return band;
    }

    /** The deepest a game has to look before it knows the band. */
    public static int deepestFloor() {
        return BAND_FLOORS[BAND_FLOORS.length - 1];
    }

    /** Everything wrong with the bands. */
    public static List<String> faultsInBands(int[] floors) {
        List<String> bad = new ArrayList<>();
        if (floors.length != FishRules.BANDS) {
            bad.add("there are " + FishRules.BANDS + " bands and " + floors.length
                    + " floor(s), so some band is one no pond can ever be");
        }
        if (floors.length > 0 && floors[0] != 0) {
            bad.add("the surface band starts " + floors[0] + " blocks down, so a "
                    + "puddle is in no band at all");
        }
        for (int at = 1; at < floors.length; at++) {
            if (floors[at] <= floors[at - 1]) {
                bad.add("band " + at + " starts at " + floors[at] + " blocks and band "
                        + (at - 1) + " at " + floors[at - 1] + ", so a deeper pond is "
                        + "not a deeper band");
            }
        }
        return List.copyOf(bad);
    }

    /** How far under the world's floor the End's void counts as a surface. */
    public static final int VOID_DEPTH = 8;

    // ==================================================================
    // How many fish are in the water without a rod
    // ==================================================================

    /**
     * How much of a cast's base weight becomes a spawn weight.
     *
     * <p>The cast weights start at sixty-four, which as spawn weights would
     * put a river's worth of dace in every puddle. Divided down, the rarity
     * ladder keeps its shape and the water keeps its space.
     */
    public static final double SPAWN_WEIGHT_DIVISOR = 4.0D;

    /** The spawn weight of a fish of this rarity. Never zero: a fish with no weight never spawns. */
    public static int spawnWeight(int rarity) {
        return Math.max(1, (int) Math.round(FishRules.baseWeight(rarity) / SPAWN_WEIGHT_DIVISOR));
    }

    /** How many turn up together. */
    public static final int SCHOOL_LEAST = 2;

    /** And at most. */
    public static final int SCHOOL_MOST = 4;

    /** Every rarity whose spawn weight is not below the rarity before it. */
    public static List<String> weightsThatHurt() {
        List<String> bad = new ArrayList<>();
        for (int rarity = FishRules.COMMONEST + 1; rarity <= FishRules.RAREST; rarity++) {
            if (spawnWeight(rarity) >= spawnWeight(rarity - 1)) {
                bad.add("rarity " + rarity + " spawns with weight " + spawnWeight(rarity)
                        + " and rarity " + (rarity - 1) + " with " + spawnWeight(rarity - 1)
                        + ", so the rarer fish is not the rarer sight");
            }
        }
        if (SCHOOL_LEAST < 1 || SCHOOL_MOST < SCHOOL_LEAST) {
            bad.add("a school is " + SCHOOL_LEAST + " to " + SCHOOL_MOST + " fish");
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // How long a cast takes
    // ==================================================================

    /** The shortest wait before anything comes near the float, in ticks. The game's own. */
    public static final int WAIT_LEAST_TICKS = 100;

    /** The longest. */
    public static final int WAIT_MOST_TICKS = 600;

    /** How short a bite is at its shortest, in ticks. */
    public static final int NIBBLE_LEAST_TICKS = 20;

    /** And at its longest. */
    public static final int NIBBLE_MOST_TICKS = 40;

    /** How long a bite lasts on a hook that holds: an hour, which is to say until the player reels in. */
    public static final int HELD_TICKS = AgronomyRules.TICKS_PER_HOUR;

    /** The wait a rolled wait becomes with this bobber on. Never nothing. */
    public static int waitFor(int rolled, TackleRules.Bobber bobber) {
        return Math.max(1, Math.round(rolled * (1.0F - bobber.sooner())));
    }

    /** How long a rolled bite lasts with this hook on. */
    public static int nibbleFor(int rolled, TackleRules.Hook hook) {
        return hook.holds() ? HELD_TICKS : Math.max(1, rolled);
    }

    /** Everything wrong with the timing. */
    public static List<String> faultsInTiming() {
        List<String> bad = new ArrayList<>();
        if (WAIT_LEAST_TICKS <= 0 || WAIT_MOST_TICKS < WAIT_LEAST_TICKS) {
            bad.add("a cast waits " + WAIT_LEAST_TICKS + " to " + WAIT_MOST_TICKS + " ticks");
        }
        if (NIBBLE_LEAST_TICKS <= 0 || NIBBLE_MOST_TICKS < NIBBLE_LEAST_TICKS) {
            bad.add("a bite lasts " + NIBBLE_LEAST_TICKS + " to " + NIBBLE_MOST_TICKS + " ticks");
        }
        if (HELD_TICKS <= NIBBLE_MOST_TICKS) {
            bad.add("a hook that holds holds for " + HELD_TICKS + " ticks, which is no "
                    + "longer than a bite anyway");
        }
        for (TackleRules.Bobber bobber : TackleRules.BOBBERS) {
            int with = waitFor(WAIT_MOST_TICKS, bobber);
            if (bobber.sooner() > 0.0F && with >= WAIT_MOST_TICKS) {
                bad.add(bobber.id() + " takes nothing off the wait");
            }
            if (with <= 0) {
                bad.add(bobber.id() + " takes the whole wait off, so a fish is on the "
                        + "line the moment it lands");
            }
        }
        for (TackleRules.Hook hook : TackleRules.HOOKS) {
            int lasts = nibbleFor(NIBBLE_LEAST_TICKS, hook);
            if (hook.holds() && lasts <= NIBBLE_MOST_TICKS) {
                bad.add(hook.id() + " holds a fish no longer than a bare line does");
            }
            if (!hook.holds() && lasts != NIBBLE_LEAST_TICKS) {
                bad.add(hook.id() + " changes how long a bite lasts without holding");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // What a rod carries
    // ==================================================================

    /** The most bait a rod carries at once. */
    public static final int MOST_BAIT_ON_ROD = 64;

    /** How much bait one catch uses up. */
    public static final int BAIT_PER_CATCH = 1;

    // ==================================================================
    // Whether the whole of it hangs together
    // ==================================================================

    /** Every family no rod, in any water, with any bait, ever brings up a fish of. */
    public static List<String> familiesNeverFished() {
        Set<String> seen = new LinkedHashSet<>();
        for (FishRules.Water where : FishRules.fishableWaters()) {
            for (int rod = 0; rod < FishRules.RODS; rod++) {
                for (TackleRules.Tackle tackle : TackleRules.everyTackle()) {
                    for (FishRules.Chance chance
                            : TackleRules.table(where, rod, FishRules.NOTHING, tackle)) {
                        FishRules.Fish one = FishRules.fish(chance.fish());
                        if (one != null && chance.share() > 0.0D) {
                            seen.add(one.family().name());
                        }
                    }
                }
            }
        }
        List<String> lost = new ArrayList<>();
        for (FishRules.Family family : FishRules.FAMILIES) {
            if (!seen.contains(family.name())) {
                lost.add(family.name());
            }
        }
        return List.copyOf(lost);
    }

    /** Everything wrong with the water. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(homeless());
        bad.addAll(faultsInBands(BAND_FLOORS));
        bad.addAll(weightsThatHurt());
        bad.addAll(faultsInTiming());
        bad.addAll(familiesNeverFished());
        if (VOID_DEPTH <= 0) {
            bad.add("the void's surface is at or above the world's floor, so a hook "
                    + "floats on end stone");
        }
        if (MOST_BAIT_ON_ROD < 1 || BAIT_PER_CATCH < 1) {
            bad.add("a rod carries " + MOST_BAIT_ON_ROD + " bait and a catch uses "
                    + BAIT_PER_CATCH + ", so bait is either nothing or free");
        }
        return List.copyOf(bad);
    }
}
