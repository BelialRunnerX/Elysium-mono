package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A hundred and forty biomes in an overworld that belongs to somebody else.
 *
 * <h2>The problem this file is the answer to</h2>
 *
 * The Vale was easy: our dimension, our climate, our biomes, and a partition
 * of the whole plane. The overworld is not ours. Its biome source is
 * vanilla's own parameter list, built in Java inside {@code
 * OverworldBiomeBuilder}, and a data pack cannot append to it — the registry
 * that looks like the place to do it takes the <em>name</em> of a preset and
 * nothing else.
 *
 * <p>So we write the list out in full: ours first, then vanilla's 7,593
 * entries exactly as the game itself hands them over. {@code
 * docs/CONVERSION.md} §12 has the three ways in and why this is the one.
 *
 * <h2>Why being first settles the argument</h2>
 *
 * Every column of the world goes to the nearest box, ties broken by whoever
 * was declared first. Our entries are first. So:
 *
 * <ul>
 *   <li>a climate <em>inside</em> one of our boxes is ours — our distance is
 *       the depth term and nothing more, vanilla's is the depth term or
 *       worse, and the tie is ours;</li>
 *   <li>a climate outside every one of them is vanilla's, exactly as it was,
 *       because not one of vanilla's entries was touched.</li>
 * </ul>
 *
 * Which means "does our biome generate" is not a question worth sampling for.
 * The question worth asking is <b>what we took</b>, and the answer is a
 * number rather than a hope: the region below is 47.5% of the inland surface,
 * cut 125 ways with no gaps and no overlaps.
 *
 * <h2>What is <em>not</em> proved here</h2>
 *
 * That no vanilla biome lost all of its ground. That one cannot be settled by
 * arithmetic over this file, because it is about vanilla's 7,593 entries, and
 * those live in {@code tools/data/overworld-vanilla.tsv}. {@code validate.py}
 * checks it entry by entry against the dump.
 */
public final class OverworldRules {

    private OverworldRules() {
    }

    // ==================================================================
    // The bands, which are vanilla's own
    // ==================================================================

    /**
     * Vanilla's five temperature bands, coldest first.
     *
     * <p>Not five bands of our choosing. These are the edges {@code
     * OverworldBiomeBuilder} cuts on, read off the dump, and using them means
     * one of our boxes never straddles the line between two of vanilla's — so
     * "we took the whole of this cell" is true of a cell vanilla also thinks
     * is a cell.
     */
    public static final List<WildRules.Span> WARMTHS = List.of(
            new WildRules.Span(-1.00D, -0.45D),
            new WildRules.Span(-0.45D, -0.15D),
            new WildRules.Span(-0.15D, 0.20D),
            new WildRules.Span(0.20D, 0.55D),
            new WildRules.Span(0.55D, 1.00D));

    /** Vanilla's five humidity bands, driest first. */
    public static final List<WildRules.Span> WETS = List.of(
            new WildRules.Span(-1.00D, -0.35D),
            new WildRules.Span(-0.35D, -0.10D),
            new WildRules.Span(-0.10D, 0.10D),
            new WildRules.Span(0.10D, 0.30D),
            new WildRules.Span(0.30D, 1.00D));

    /**
     * Five bands of erosion, least eroded first.
     *
     * <p>Vanilla cuts erosion seven ways; the last three are all flat ground
     * and we take them as one, so these five are vanilla's edges with the
     * three flattest merged. Low erosion is mountain, high erosion is plain,
     * which is why the names run crag to flat and not the other way about.
     */
    public static final List<WildRules.Span> RELIEFS = List.of(
            new WildRules.Span(-1.0000D, -0.7799D),
            new WildRules.Span(-0.7799D, -0.3750D),
            new WildRules.Span(-0.3750D, -0.2225D),
            new WildRules.Span(-0.2225D, 0.0500D),
            new WildRules.Span(0.0500D, 1.0000D));

    /**
     * The six bands of weirdness we take, which is every other one of
     * vanilla's twelve.
     *
     * <p>Weirdness is the axis vanilla uses for its peaks and valleys, and it
     * is the one to take a share on: it is fine-grained, so taking every
     * other band scatters our biomes evenly through the world rather than
     * putting all of them in one corner of it. Taking bands whole — never
     * half of one — is what keeps a claim of ours from cutting a vanilla
     * entry in two.
     */
    public static final List<WildRules.Span> STRANGENESS = List.of(
            new WildRules.Span(-0.9333D, -0.7666D),
            new WildRules.Span(-0.5666D, -0.4000D),
            new WildRules.Span(-0.2666D, -0.0500D),
            new WildRules.Span(0.2666D, 0.4000D),
            new WildRules.Span(0.5666D, 0.7666D),
            new WildRules.Span(0.9333D, 1.0000D));

    /**
     * How far inland we go.
     *
     * <p>From vanilla's near-inland edge up. Below it is shore, and below
     * that is ocean, and a land biome that wins in the sea is a beach made of
     * podzol. Vanilla keeps all of it.
     */
    public static final WildRules.Span INLAND = new WildRules.Span(-0.11D, 1.00D);

    /** The surface, as vanilla's own surface entries state it. */
    public static final WildRules.Span SURFACE = WildRules.Span.at(0.0D);

    /** The floor of the caves, likewise. */
    public static final WildRules.Span UNDERGROUND = WildRules.Span.at(1.0D);

    // ==================================================================
    // What the crossings are called
    // ==================================================================

    /** The five temperature bands, by name, coldest first. */
    public static final List<String> WARMTH_NAMES =
            List.of("frozen", "cold", "mild", "warm", "hot");

    /** The five humidity bands, by name, driest first. */
    public static final List<String> WET_NAMES =
            List.of("arid", "dry", "fair", "damp", "wet");

    /** The five erosion bands, by name, steepest first. */
    public static final List<String> RELIEF_NAMES =
            List.of("crag", "scarp", "hill", "wold", "flat");

    /**
     * What each of the twenty-five climates is called.
     *
     * <p>Read as a table: five rows of warmth, five columns of wet. "Frozen
     * and dry" is not a place and "hoar" is, which is the same argument the
     * Vale's fifteen names make. Written out rather than made up out of the
     * band names, and counted against the crossing, so a name that goes
     * missing is a build failure rather than a biome called {@code
     * frozen_dry_hill}.
     */
    public static final List<String> CLIMATE_NAMES = List.of(
            "rime",   "hoar",   "frost",   "sleet",  "glacier",
            "ash",    "pine",   "boreal",  "fen",    "mire",
            "chalk",  "heath",  "green",   "clover", "marsh",
            "amber",  "acacia", "gold",    "reed",   "bayou",
            "cinder", "ember",  "sun",     "palm",   "steam");

    /** The fifteen names underground, five warmths by three wets. */
    public static final List<String> CAVE_NAMES = List.of(
            "frostvault",   "rimegrotto",   "icewell",
            "slatecavern",  "pinehollow",   "springvault",
            "chalkcavern",  "mossgrotto",   "dripwell",
            "ochrecavern",  "rootgrotto",   "steamwell",
            "basaltcavern", "emberhollow",  "geyserwell");

    /** How many bands of wet a cave is cut into. Three, not five. */
    public static final int CAVE_WETS = 3;

    // ==================================================================
    // What each one decorates like
    // ==================================================================

    /**
     * The vanilla biome each climate decorates like, as a table of five
     * warmths by five wets.
     *
     * <p>Our biomes do not compose a feature list. They <em>copy</em> one.
     *
     * <p>Which is not laziness, it is the only way to be sure the world will
     * start. The game sorts every placed feature in the world into one order
     * and refuses to build a chunk generator if two biomes disagree about
     * which of two features comes first — a cycle, reported as a wall of text
     * naming features and no biome at all. A list copied whole from a biome
     * the game already ships cannot disagree with anything, because it is
     * already in the order the game sorted last time.
     */
    public static final List<List<String>> MODELS = List.of(
            List.of("ice_spikes", "snowy_plains", "snowy_slopes",
                    "snowy_taiga", "grove"),
            List.of("windswept_gravelly_hills", "taiga", "old_growth_pine_taiga",
                    "old_growth_spruce_taiga", "windswept_forest"),
            List.of("windswept_hills", "plains", "meadow", "forest",
                    "dark_forest"),
            List.of("savanna", "sunflower_plains", "birch_forest",
                    "cherry_grove", "swamp"),
            List.of("badlands", "desert", "savanna_plateau", "sparse_jungle",
                    "jungle"));

    /**
     * What the steepest band decorates like instead, by warmth.
     *
     * <p>A crag is a mountain whatever the rain does, and a mountain
     * decorated like a swamp is a swamp somebody tilted.
     */
    public static final List<String> CRAG_MODELS = List.of(
            "jagged_peaks", "frozen_peaks", "stony_peaks", "windswept_hills",
            "windswept_savanna");

    /**
     * The game's own biomes that are mountains.
     *
     * <p>Here because the check that used to stand in for this one was the
     * definition read back to itself: a crag decorates like {@code
     * CRAG_MODELS.get(warmth)}, and asking whether it decorates like {@code
     * CRAG_MODELS.get(warmth)} is a check that cannot fail. It passed happily
     * with a meadow in the list. What is actually being claimed is that the
     * steepest band decorates like somewhere steep, and that needs a second
     * list to be a claim at all.
     */
    public static final List<String> MOUNTAINS = List.of(
            "jagged_peaks", "frozen_peaks", "stony_peaks", "snowy_slopes",
            "windswept_hills", "windswept_gravelly_hills", "windswept_forest",
            "windswept_savanna", "grove");

    /** What a cave decorates like, by how wet it is. */
    public static final List<String> CAVE_MODELS =
            List.of("dripstone_caves", "deep_dark", "lush_caves");

    // ==================================================================
    // A biome
    // ==================================================================

    /**
     * One of ours on the surface.
     *
     * @param warmth which temperature band, 0 the coldest
     * @param wet    which humidity band, 0 the driest
     * @param relief which erosion band, 0 the steepest
     */
    public record Land(int warmth, int wet, int relief) {

        public Land {
            check(warmth, WARMTHS.size(), "temperature");
            check(wet, WETS.size(), "humidity");
            check(relief, RELIEFS.size(), "erosion");
        }

        private static void check(int at, int bands, String axis) {
            if (at < 0 || at >= bands) {
                throw new IllegalArgumentException("band " + at + " of " + axis
                        + " and there are " + bands);
            }
        }

        /**
         * What it is called: the climate, then the shape of the ground.
         *
         * <p>Names itself rather than throwing when the lists are short. A
         * roster built in a static initialiser that throws takes the whole
         * class with it, and what reaches the harness is
         * ExceptionInInitializerError with no line saying which list ran out
         * — where what is wanted is the check below saying there are
         * twenty-four names for twenty-five crossings.
         */
        public String name() {
            String climate = warmth * WETS.size() + wet < CLIMATE_NAMES.size()
                    ? CLIMATE_NAMES.get(warmth * WETS.size() + wet)
                    : "unnamed_climate_" + warmth + "_" + wet;
            String shape = relief < RELIEF_NAMES.size()
                    ? RELIEF_NAMES.get(relief) : "unnamed_relief_" + relief;
            return climate + "_" + shape;
        }

        /** The vanilla biome it decorates like. */
        public String model() {
            return relief == 0 ? CRAG_MODELS.get(warmth)
                    : MODELS.get(warmth).get(wet);
        }

        /** Every box it claims: one per band of weirdness we take. */
        public List<WildRules.Region> claim() {
            List<WildRules.Region> made = new ArrayList<>();
            for (WildRules.Span strange : STRANGENESS) {
                made.add(new WildRules.Region(name(), List.of(
                        WARMTHS.get(warmth), WETS.get(wet), INLAND,
                        RELIEFS.get(relief), SURFACE, strange), 0.0D));
            }
            return List.copyOf(made);
        }

        /** How warm the biome file says it is. */
        public float warmthValue() {
            return warmthOf(warmth);
        }

        /** How much rain the biome file says it gets. */
        public float wetValue() {
            return wet / (float) (WETS.size() - 1);
        }

        /** Whether anything falls out of the sky here. */
        public boolean rains() {
            return wet > 0;
        }

        /** The colour of the sky: from warmth and the shape of the ground. */
        public int sky() {
            return rgb(mix(0x7A, 0xE0, warmth, WARMTHS.size()),
                    mix(0xB4, 0xA8, warmth, WARMTHS.size()),
                    mix(0xF4, 0x60, relief, RELIEFS.size()));
        }

        /** The colour of the fog: from wet and the shape of the ground. */
        public int fog() {
            return rgb(mix(0xD8, 0x9C, wet, WETS.size()),
                    mix(0xD0, 0xB4, relief, RELIEFS.size()),
                    mix(0xC0, 0xC8, wet, WETS.size()));
        }

        /** The colour of the water: from warmth and wet. */
        public int water() {
            return rgb(mix(0x2C, 0x4E, warmth, WARMTHS.size()),
                    mix(0x5E, 0x8A, wet, WETS.size()),
                    mix(0xB8, 0x74, warmth, WARMTHS.size()));
        }

        /** The colour of the grass: from wet and the shape of the ground. */
        public int grass() {
            return rgb(mix(0xC4, 0x4A, wet, WETS.size()),
                    mix(0xA8, 0x9E, wet, WETS.size()),
                    mix(0x6C, 0x38, relief, RELIEFS.size()));
        }

        /** The colour of the leaves: from wet and warmth. */
        public int foliage() {
            return rgb(mix(0xAC, 0x3A, wet, WETS.size()),
                    mix(0x98, 0x8E, warmth, WARMTHS.size()),
                    mix(0x70, 0x2C, wet, WETS.size()));
        }

        /** Every colour it is painted in, in one list, for comparing. */
        public List<Integer> paint() {
            return List.of(sky(), fog(), water(), grass(), foliage());
        }
    }

    /**
     * One of ours underground.
     *
     * @param warmth which temperature band, 0 the coldest
     * @param wet    which of the three damp bands, 0 the driest
     */
    public record Cave(int warmth, int wet) {

        public Cave {
            if (warmth < 0 || warmth >= WARMTHS.size()) {
                throw new IllegalArgumentException("a cave in temperature band "
                        + warmth + " and there are " + WARMTHS.size());
            }
            if (wet < 0 || wet >= CAVE_WETS) {
                throw new IllegalArgumentException("a cave in damp band " + wet
                        + " and there are " + CAVE_WETS);
            }
        }

        /** What it is called, or a name saying it has none. */
        public String name() {
            int at = warmth * CAVE_WETS + wet;
            return at < CAVE_NAMES.size() ? CAVE_NAMES.get(at)
                    : "unnamed_cave_" + warmth + "_" + wet;
        }

        /** The vanilla cave it decorates like. */
        public String model() {
            return CAVE_MODELS.get(wet);
        }

        /**
         * The humidity band it claims.
         *
         * <p>Three bands out of five: the driest two of vanilla's, the middle
         * one, and the wettest two. Whole bands again, and they cover the
         * axis end to end between them.
         */
        public WildRules.Span wetSpan() {
            return switch (wet) {
                case 0 -> new WildRules.Span(WETS.get(0).low(), WETS.get(1).high());
                case 1 -> WETS.get(2);
                default -> new WildRules.Span(WETS.get(3).low(), WETS.get(4).high());
            };
        }

        /** Every box it claims: one per band of weirdness we take. */
        public List<WildRules.Region> claim() {
            List<WildRules.Region> made = new ArrayList<>();
            for (WildRules.Span strange : STRANGENESS) {
                made.add(new WildRules.Region(name(), List.of(
                        WARMTHS.get(warmth), wetSpan(), INLAND,
                        new WildRules.Span(-1.0D, 1.0D), UNDERGROUND, strange),
                        0.0D));
            }
            return List.copyOf(made);
        }

        /** How warm the biome file says it is. */
        public float warmthValue() {
            return warmthOf(warmth);
        }

        /** How much rain: none. It is a cave. */
        public float wetValue() {
            return wet / (float) (CAVE_WETS - 1);
        }

        /** The colour of the sky nobody down here can see. */
        public int sky() {
            return rgb(mix(0x12, 0x3C, warmth, WARMTHS.size()),
                    mix(0x16, 0x2A, wet, CAVE_WETS),
                    mix(0x28, 0x1C, warmth, WARMTHS.size()));
        }

        /** The colour of the fog, which is the one a player actually sees. */
        public int fog() {
            return rgb(mix(0x28, 0x6A, warmth, WARMTHS.size()),
                    mix(0x30, 0x54, wet, CAVE_WETS),
                    mix(0x44, 0x30, warmth, WARMTHS.size()));
        }

        /** The colour of the water. */
        public int water() {
            return rgb(mix(0x1E, 0x46, warmth, WARMTHS.size()),
                    mix(0x48, 0x7E, wet, CAVE_WETS),
                    mix(0x8C, 0x5C, warmth, WARMTHS.size()));
        }

        /** The colour of what little grass grows here. */
        public int grass() {
            return rgb(mix(0x5C, 0x3E, wet, CAVE_WETS),
                    mix(0x78, 0x92, wet, CAVE_WETS),
                    mix(0x4C, 0x30, warmth, WARMTHS.size()));
        }

        /** The colour of the leaves, for the azalea. */
        public int foliage() {
            return rgb(mix(0x4E, 0x34, wet, CAVE_WETS),
                    mix(0x6E, 0x86, warmth, WARMTHS.size()),
                    mix(0x40, 0x28, wet, CAVE_WETS));
        }

        /** Every colour it is painted in, in one list, for comparing. */
        public List<Integer> paint() {
            return List.of(sky(), fog(), water(), grass(), foliage());
        }
    }

    /** How warm a band is, in the number a biome file carries. */
    public static float warmthOf(int warmth) {
        return -0.5F + warmth * (2.5F / (WARMTHS.size() - 1));
    }

    private static int rgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    private static int mix(int from, int to, int at, int bands) {
        if (bands < 2) {
            return from;
        }
        return from + (to - from) * at / (bands - 1);
    }

    // ==================================================================
    // The roster
    // ==================================================================

    /** Every biome of ours on the surface, coldest and driest and steepest first. */
    public static final List<Land> LANDS = lands();

    /** Every biome of ours underground. */
    public static final List<Cave> CAVES = caves();

    private static List<Land> lands() {
        List<Land> made = new ArrayList<>();
        for (int warmth = 0; warmth < WARMTHS.size(); warmth++) {
            for (int wet = 0; wet < WETS.size(); wet++) {
                for (int relief = 0; relief < RELIEFS.size(); relief++) {
                    made.add(new Land(warmth, wet, relief));
                }
            }
        }
        return List.copyOf(made);
    }

    private static List<Cave> caves() {
        List<Cave> made = new ArrayList<>();
        for (int warmth = 0; warmth < WARMTHS.size(); warmth++) {
            for (int wet = 0; wet < CAVE_WETS; wet++) {
                made.add(new Cave(warmth, wet));
            }
        }
        return List.copyOf(made);
    }

    /** Every name we add to the overworld, surface first. */
    public static List<String> names() {
        List<String> made = new ArrayList<>();
        for (Land one : LANDS) {
            made.add(one.name());
        }
        for (Cave one : CAVES) {
            made.add(one.name());
        }
        return List.copyOf(made);
    }

    /** How many biomes that is. */
    public static int howMany() {
        return LANDS.size() + CAVES.size();
    }

    /** Every box we claim, in the order the list declares them. */
    public static List<WildRules.Region> claims() {
        List<WildRules.Region> made = new ArrayList<>();
        for (Land one : LANDS) {
            made.addAll(one.claim());
        }
        for (Cave one : CAVES) {
            made.addAll(one.claim());
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What we add of our own
    // ==================================================================

    /**
     * The features of ours, in the one order every biome lists them in.
     *
     * <p>The order is the whole of why this is a list and not a set. Two
     * biomes that disagree about which of two features comes first is a world
     * that will not start, so ours go at the end of their step, always in
     * this order, in every biome that has them. Nothing of vanilla's ever
     * follows one of ours, so no edge can ever point backwards and no cycle
     * can ever close.
     */
    public static final List<String> OURS = List.of(
            "boulder_stone", "boulder_mossy", "boulder_basalt",
            "patch_scrub", "patch_bloom", "patch_thicket");

    /** Which generation step each of ours belongs in. */
    public static int stepOf(String feature) {
        return feature.startsWith("boulder_") ? LOCAL_MODIFICATIONS
                : VEGETAL_DECORATION;
    }

    /** The game's step for a thing that sits on the ground rather than in it. */
    public static final int LOCAL_MODIFICATIONS = 2;

    /** The game's step for anything that grows. */
    public static final int VEGETAL_DECORATION = 9;

    /** How many steps a biome file has room for. */
    public static final int STEPS = 11;

    /**
     * What one of our surface biomes adds of its own, in {@link #OURS} order.
     *
     * <p>Every one of them gets exactly one patch — scrub where it is dry,
     * bloom in the middle, thicket where it is wet — so "a biome of ours with
     * nothing of ours in it" is not a thing that can happen quietly.
     */
    public static List<String> extras(Land one) {
        List<String> made = new ArrayList<>();
        if (one.relief() <= 1) {
            made.add(one.warmth() == WARMTHS.size() - 1
                    ? "boulder_basalt" : "boulder_stone");
        }
        if (one.wet() >= 3 && one.relief() >= 2) {
            made.add("boulder_mossy");
        }
        made.add(one.wet() <= 1 ? "patch_scrub"
                : one.wet() == 2 ? "patch_bloom" : "patch_thicket");
        made.sort((left, right) -> OURS.indexOf(left) - OURS.indexOf(right));
        return List.copyOf(made);
    }

    /** What one of our caves adds of its own. */
    public static List<String> extras(Cave one) {
        List<String> made = new ArrayList<>(List.of("boulder_stone"));
        if (one.wet() == CAVE_WETS - 1) {
            made.add("boulder_mossy");
        }
        made.sort((left, right) -> OURS.indexOf(left) - OURS.indexOf(right));
        return List.copyOf(made);
    }

    // ==================================================================
    // What lives there
    // ==================================================================

    /**
     * What spawns in a band of warmth, as {@code what, most, fewest, weight}.
     *
     * <p>Those two numbers in that order. The other way round is not a crash:
     * the game logs a line, drops the spawner, and the biome is empty with
     * nothing in the world saying why. It shipped that way once, for three
     * biomes of the Vale, and only the load test noticed.
     */
    public static final List<List<Object[]>> CREATURES = List.of(
            List.of(new Object[] {"minecraft:polar_bear", 2, 1, 4},
                    new Object[] {"minecraft:rabbit", 3, 2, 8}),
            List.of(new Object[] {"minecraft:fox", 4, 2, 8},
                    new Object[] {"minecraft:wolf", 4, 4, 5},
                    new Object[] {"minecraft:rabbit", 3, 2, 4}),
            List.of(new Object[] {"minecraft:sheep", 4, 4, 12},
                    new Object[] {"minecraft:cow", 4, 4, 8},
                    new Object[] {"minecraft:chicken", 4, 4, 10},
                    new Object[] {"minecraft:pig", 4, 4, 10}),
            List.of(new Object[] {"minecraft:horse", 6, 2, 5},
                    new Object[] {"minecraft:donkey", 3, 1, 1},
                    new Object[] {"minecraft:llama", 4, 4, 5}),
            List.of(new Object[] {"minecraft:camel", 1, 1, 2},
                    new Object[] {"minecraft:rabbit", 3, 2, 4},
                    new Object[] {"minecraft:ocelot", 3, 2, 2}));

    /** The monsters, which are the same everywhere above ground. */
    public static final List<Object[]> MONSTERS = List.of(
            new Object[] {"minecraft:spider", 4, 4, 100},
            new Object[] {"minecraft:zombie", 4, 4, 95},
            new Object[] {"minecraft:skeleton", 4, 4, 100},
            new Object[] {"minecraft:creeper", 4, 4, 100},
            new Object[] {"minecraft:slime", 4, 4, 100},
            new Object[] {"minecraft:enderman", 4, 1, 10},
            new Object[] {"minecraft:witch", 1, 1, 5});

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /**
     * Everything wrong with the roster. Must be empty.
     *
     * <p>Seven things, and every one of them has bitten somebody: the
     * crossings have to be the whole roster, the names have to be distinct,
     * the bands have to meet edge to edge and never overlap, every claim has
     * to be six axes of real spans, no two biomes may be painted the same, no
     * claim may sit inside another's, and every biome has to have a model the
     * game actually ships.
     */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames());
        bad.addAll(faultsInBands());
        bad.addAll(faultsInClaims(claims()));
        bad.addAll(faultsInPaint());
        bad.addAll(faultsInExtras());
        bad.addAll(faultsInModels());
        return List.copyOf(bad);
    }

    /** The names, counted against the crossings and against each other. */
    public static List<String> faultsInNames() {
        List<String> bad = new ArrayList<>();
        int crossings = WARMTHS.size() * WETS.size();
        if (CLIMATE_NAMES.size() != crossings) {
            bad.add("there are " + CLIMATE_NAMES.size() + " climate names for "
                    + crossings + " crossings, so a climate is unnamed or a name "
                    + "is for nothing");
        }
        if (CAVE_NAMES.size() != WARMTHS.size() * CAVE_WETS) {
            bad.add("there are " + CAVE_NAMES.size() + " cave names for "
                    + WARMTHS.size() * CAVE_WETS + " crossings");
        }
        if (WARMTH_NAMES.size() != WARMTHS.size()
                || WET_NAMES.size() != WETS.size()
                || RELIEF_NAMES.size() != RELIEFS.size()) {
            bad.add("a set of bands and the names for them are different lengths");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String name : names()) {
            if (!seen.add(name)) {
                bad.add(name + " names two biomes, so one of them never generates "
                        + "and nothing anywhere reports it");
            }
        }
        return bad;
    }

    /** Every crag decorates like somewhere steep. */
    public static List<String> faultsInModels() {
        List<String> bad = new ArrayList<>();
        for (int warmth = 0; warmth < CRAG_MODELS.size(); warmth++) {
            String model = CRAG_MODELS.get(warmth);
            if (!MOUNTAINS.contains(model)) {
                bad.add("the steepest band of " + WARMTH_NAMES.get(warmth)
                        + " decorates like " + model + ", which is not one of "
                        + "the game's mountains, so a cliff face is covered in "
                        + "whatever grows on flat ground");
            }
        }
        for (String model : CRAG_MODELS) {
            if (model.isBlank()) {
                bad.add("a crag decorates like nothing at all");
            }
        }
        return bad;
    }

    /** The bands: in order, meeting, and covering the axis end to end. */
    public static List<String> faultsInBands() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInAxis("temperature", WARMTHS, true));
        bad.addAll(faultsInAxis("humidity", WETS, true));
        bad.addAll(faultsInAxis("erosion", RELIEFS, true));
        bad.addAll(faultsInAxis("weirdness", STRANGENESS, false));
        return bad;
    }

    /**
     * One axis of bands.
     *
     * @param whole whether the bands have to cover the axis end to end.
     *              Temperature, humidity and erosion do, because every
     *              climate on them belongs to one of ours. Weirdness does
     *              not: we take every other band and vanilla keeps the rest,
     *              and that is the whole design.
     */
    public static List<String> faultsInAxis(String axis, List<WildRules.Span> bands,
                                            boolean whole) {
        List<String> bad = new ArrayList<>();
        if (bands.isEmpty()) {
            bad.add(axis + " is cut into no bands at all");
            return bad;
        }
        for (int at = 1; at < bands.size(); at++) {
            WildRules.Span under = bands.get(at - 1);
            WildRules.Span over = bands.get(at);
            if (over.low() < under.high()) {
                bad.add("band " + at + " of " + axis + " starts at " + over.low()
                        + " and the one below it runs to " + under.high()
                        + ", so a climate in the overlap belongs to two biomes "
                        + "of ours and one of them is the one that never wins");
            }
            if (whole && over.low() > under.high()) {
                bad.add("band " + at + " of " + axis + " starts at " + over.low()
                        + " and the one below it stops at " + under.high()
                        + ", so climate in the gap goes to whatever box happens "
                        + "to be nearest");
            }
        }
        if (whole) {
            if (bands.get(0).low() != -1.0D) {
                bad.add(axis + " starts at " + bands.get(0).low()
                        + " and the axis starts at -1");
            }
            if (bands.get(bands.size() - 1).high() != 1.0D) {
                bad.add(axis + " ends at " + bands.get(bands.size() - 1).high()
                        + " and the axis ends at 1");
            }
        }
        return bad;
    }

    /**
     * The claims themselves, against each other.
     *
     * <p>Taken as an argument rather than read off the roster, so a control
     * can hand this something built to be caught. A check that walks a healthy
     * roster and can only ever answer "no" is a check that has never been
     * shown to be able to answer "yes".
     */
    public static List<String> faultsInClaims(List<WildRules.Region> claims) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInContainment(claims));
        bad.addAll(faultsInOverlap(claims));
        return bad;
    }

    /**
     * A claim swallowed by one declared before it.
     *
     * <p>Separate from the overlap rule, and separate on purpose. Anything
     * contained also overlaps, so a control built to prove the containment
     * rule reports was being answered by the overlap rule instead — and the
     * containment rule could have been deleted outright with every check
     * still green. Two rules that can only be asked together are one rule
     * with a spare.
     */
    public static List<String> faultsInContainment(List<WildRules.Region> claims) {
        List<String> bad = new ArrayList<>();
        for (int at = 0; at < claims.size(); at++) {
            WildRules.Region one = claims.get(at);
            if (one.spans().size() != WildRules.AXES) {
                bad.add(one.biome() + " claims " + one.spans().size() + " axes");
                continue;
            }
            for (int before = 0; before < at; before++) {
                WildRules.Region earlier = claims.get(before);
                if (earlier.biome().equals(one.biome())) {
                    continue;
                }
                if (one.inside(earlier)) {
                    bad.add(one.biome() + "'s claim sits inside " + earlier.biome()
                            + "'s, which was declared first, so it wins nothing "
                            + "anywhere, ever");
                }
            }
        }
        return bad;
    }

    /** Two claims that share climate, where the second of them loses it. */
    public static List<String> faultsInOverlap(List<WildRules.Region> claims) {
        List<String> bad = new ArrayList<>();
        for (int at = 0; at < claims.size(); at++) {
            WildRules.Region one = claims.get(at);
            if (one.spans().size() != WildRules.AXES) {
                continue;
            }
            for (int before = 0; before < at; before++) {
                WildRules.Region earlier = claims.get(before);
                if (earlier.biome().equals(one.biome())) {
                    continue;
                }
                if (overlap(one, earlier)) {
                    bad.add(one.biome() + " and " + earlier.biome() + " claim "
                            + "climate in common, and the one declared second "
                            + "loses all of it");
                }
            }
        }
        return bad;
    }

    /**
     * Whether two claims share any climate at all.
     *
     * <p>Two bands that meet at a face do not overlap: every band on every
     * axis in this file ends exactly where the next one starts, and a rule
     * that called that an overlap would call the whole design one.
     *
     * <p>A <em>point</em> axis is the exception and it is not a detail. Depth
     * is a point — the surface is 0 and the cave floor is 1, exactly as
     * vanilla's own entries state them — so two surface claims share that
     * axis completely while touching at a single value. Reading that as a
     * face touch says every pair of surface biomes is disjoint on depth, and
     * then nothing overlaps anything and this returns false forever.
     */
    public static boolean overlap(WildRules.Region one, WildRules.Region other) {
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            WildRules.Span mine = one.spans().get(axis);
            WildRules.Span theirs = other.spans().get(axis);
            double low = Math.max(mine.low(), theirs.low());
            double high = Math.min(mine.high(), theirs.high());
            if (low > high) {
                return false;
            }
            boolean bothPoints = mine.low() == mine.high()
                    && theirs.low() == theirs.high();
            if (low == high && !bothPoints) {
                return false;
            }
        }
        return true;
    }

    /** No two biomes painted the same, which is two biomes under two names. */
    public static List<String> faultsInPaint() {
        List<String> bad = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<List<Integer>> painted = new ArrayList<>();
        for (Land one : LANDS) {
            names.add(one.name());
            painted.add(one.paint());
        }
        for (Cave one : CAVES) {
            names.add(one.name());
            painted.add(one.paint());
        }
        for (int at = 0; at < painted.size(); at++) {
            for (int before = 0; before < at; before++) {
                if (painted.get(at).equals(painted.get(before))) {
                    bad.add(names.get(at) + " is painted exactly like "
                            + names.get(before) + ", so they are one biome under "
                            + "two names");
                }
            }
        }
        return bad;
    }

    /**
     * What we add of our own: in one order, in a step that exists, and never
     * nothing.
     */
    public static List<String> faultsInExtras() {
        List<String> bad = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>(OURS);
        if (known.size() != OURS.size()) {
            bad.add("a feature of ours is listed twice, so its place in the order "
                    + "is two places");
        }
        for (String feature : OURS) {
            int step = stepOf(feature);
            if (step < 0 || step >= STEPS) {
                bad.add(feature + " goes in step " + step + " and a biome file has "
                        + STEPS);
            }
        }
        for (Land one : LANDS) {
            bad.addAll(faultsInExtras(one.name(), extras(one)));
        }
        for (Cave one : CAVES) {
            bad.addAll(faultsInExtras(one.name(), extras(one)));
        }
        return bad;
    }

    /** One biome's own additions, in {@link #OURS} order and never empty. */
    public static List<String> faultsInExtras(String name, List<String> added) {
        List<String> bad = new ArrayList<>();
        if (added.isEmpty()) {
            bad.add(name + " adds nothing of ours at all, so it is a vanilla biome "
                    + "under a name of ours");
        }
        int last = -1;
        for (String feature : added) {
            int at = OURS.indexOf(feature);
            if (at < 0) {
                bad.add(name + " adds " + feature + ", which is not one of ours");
                continue;
            }
            if (at <= last) {
                bad.add(name + " lists " + feature + " out of the one order every "
                        + "biome lists them in, and two biomes that disagree about "
                        + "which feature comes first is a world that will not start");
            }
            last = at;
        }
        return bad;
    }

    /**
     * How much of the inland surface we claim, worked out rather than sampled.
     *
     * <p>Temperature, humidity and erosion are covered end to end, so they
     * contribute all of themselves. Weirdness contributes the bands we take.
     */
    public static double shareOfInlandSurface() {
        double taken = 0.0D;
        for (WildRules.Span band : STRANGENESS) {
            taken += band.high() - band.low();
        }
        return taken / 2.0D;
    }

    /** The most of the inland surface this design is allowed to take. */
    public static final double MOST_OF_THE_LAND = 0.55D;

    /** The least, below which there is no point having done any of it. */
    public static final double LEAST_OF_THE_LAND = 0.25D;

    /** Every vanilla biome our roster decorates like. */
    public static List<String> models() {
        Set<String> made = new LinkedHashSet<>();
        for (Land one : LANDS) {
            made.add(one.model());
        }
        for (Cave one : CAVES) {
            made.add(one.model());
        }
        return List.copyOf(new ArrayList<>(made));
    }

    /** A surface biome by name, or null. */
    public static Land land(String name) {
        for (Land one : LANDS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /** A cave by name, or null. */
    public static Cave cave(String name) {
        for (Cave one : CAVES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /**
     * Which of ours a climate lands in, or null if it belongs to vanilla.
     *
     * <p>The game's own rule, restated only as far as it has to be: our boxes
     * do not overlap, so "inside one of them" has one answer.
     */
    public static String at(double[] climate) {
        if (climate.length != WildRules.AXES) {
            throw new IllegalArgumentException("a climate of " + climate.length
                    + " axes and the space has " + WildRules.AXES);
        }
        for (WildRules.Region claim : claims()) {
            boolean in = true;
            for (int axis = 0; axis < WildRules.AXES && in; axis++) {
                WildRules.Span span = claim.spans().get(axis);
                in = climate[axis] >= span.low() && climate[axis] <= span.high();
            }
            if (in) {
                return claim.biome();
            }
        }
        return null;
    }

    /** The six axes of a climate, for a harness that wants to build one. */
    public static double[] climate(double temperature, double humidity,
                                   double continentalness, double erosion,
                                   double depth, double weirdness) {
        return new double[] {temperature, humidity, continentalness, erosion,
                depth, weirdness};
    }

    /** The names of the axes, in the order a claim lists them. */
    public static final List<String> AXIS_NAMES =
            List.copyOf(WildRules.PARAMETERS);

    static {
        Objects.requireNonNull(AXIS_NAMES);
        if (!Arrays.asList("temperature", "humidity", "continentalness",
                "erosion", "depth", "weirdness").equals(AXIS_NAMES)) {
            throw new IllegalStateException("the axes are not in the order the "
                    + "game stores them, and every claim in this file is written "
                    + "in that order");
        }
    }
}
