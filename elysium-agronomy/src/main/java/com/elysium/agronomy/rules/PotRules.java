package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pots, what goes in them, and what comes out.
 *
 * <h2>What the mod this replaces actually is</h2>
 *
 * A hundred and eighty-three blocks and no items: sixteen dyes times three
 * fired clays times three fittings, and a handful of stones. It is a catalogue
 * with a mechanism behind it, and the mechanism is the interesting half — a
 * pot holds a soil, a soil grows some kinds of thing and not others, and a
 * fitting decides whether you have to come back for the harvest.
 *
 * <p>{@link AgronomyRules} already holds how fast a pot grows: light, the
 * accelerator, and work per tick. What is here is the rest of it — which soils
 * exist, what each will grow, and the catalogue of pots themselves, derived
 * from three lists rather than written out a hundred and sixty-five times.
 *
 * <h2>The rules this holds itself to</h2>
 *
 * Every material has all three fittings, or half the catalogue is missing and
 * only the player who wanted that one colour finds out. Every sprout has at
 * least one soil that will grow it. No soil grows everything, or there is only
 * one soil and the rest are decoration. Soil gets faster as it gets dearer.
 * And a waxed pot grows at exactly the speed a plain one does, because it is
 * a finish rather than an upgrade — the one claim here that is easy to break
 * by accident and impossible to notice in play.
 */
public final class PotRules {

    private PotRules() {
    }

    // ==================================================================
    // What a pot is
    // ==================================================================

    /** A pot you plant in and harvest by hand. */
    public static final String PLAIN = "plain";

    /** A pot that drops its harvest into whatever is beneath it. */
    public static final String HOPPER = "hopper";

    /** A plain pot with a wax finish. Prettier, not faster. */
    public static final String WAXED = "waxed";

    /** The three fittings, in the order they read best. */
    public static final List<String> FITTINGS = List.of(PLAIN, HOPPER, WAXED);

    /** One pot: what it is made of and what is fitted to it. */
    public record Pot(String material, String fitting) {

        public Pot {
            Objects.requireNonNull(material, "a pot made of nothing");
            if (!FITTINGS.contains(fitting)) {
                throw new IllegalArgumentException("no such fitting: " + fitting);
            }
        }

        /** The block id the game will know it by. */
        public String id() {
            return PLAIN.equals(fitting) ? material + "_botany_pot"
                    : material + "_" + fitting + "_botany_pot";
        }

        /** Whether this one empties itself into what is below. */
        public boolean feeds() {
            return HOPPER.equals(fitting);
        }
    }

    /** One soil: what it grows, how fast, and how far up the ladder it is. */
    public record Soil(String id, int tier, float speed, List<String> grows) {

        public Soil {
            Objects.requireNonNull(id, "a soil with no name");
            grows = List.copyOf(Objects.requireNonNull(grows, "null kinds"));
            if (tier < 0) {
                throw new IllegalArgumentException(id + " sits below the ground floor");
            }
            if (speed <= 0.0F) {
                throw new IllegalArgumentException(id + " grows nothing at any speed");
            }
            if (grows.isEmpty()) {
                throw new IllegalArgumentException(id + " grows nothing at all");
            }
        }

        /** Whether this soil will grow that kind of thing. */
        public boolean takes(String kind) {
            return grows.contains(kind);
        }
    }

    /** One thing that grows in a pot. */
    public record Sprout(String id, String needs, int ticks, String drop, int amount) {

        public Sprout {
            Objects.requireNonNull(id, "a sprout with no name");
            Objects.requireNonNull(needs, "a sprout that needs no soil");
            Objects.requireNonNull(drop, "a sprout that drops nothing");
            if (ticks <= 0) {
                throw new IllegalArgumentException(id + " grows instantly, which is a "
                        + "crafting recipe rather than a plant");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException(id + " drops nothing when harvested");
            }
        }

        /** How long this takes in a given soil, at a given pot speed. */
        public int ticksIn(Soil soil, float potSpeed) {
            float speed = Math.max(0.0001F, soil.speed() * Math.max(0.0F, potSpeed));
            return Math.max(1, Math.round(ticks / speed));
        }
    }

    /** The sixteen dyes, in the order the game lists them. */
    public static final List<String> COLOURS = List.of(
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black");

    /** The fired clays a coloured pot can be made of. */
    public static final List<String> CERAMICS = List.of("terracotta", "glazed_terracotta", "concrete");

    /** The stones a plain pot can be cut from. */
    public static final List<String> STONES = List.of(
            "stone", "deepslate", "tuff", "blackstone", "calcite", "quartz", "obsidian");

    /** Every soil a pot can be filled with. */
    public static final List<Soil> SOILS = List.of(
            new Soil("dirt", 0, 1.00F, List.of("common")),
            new Soil("coarse_dirt", 0, 0.85F, List.of("common", "hardy")),
            new Soil("farmland", 1, 1.25F, List.of("common", "crop")),
            new Soil("podzol", 1, 1.10F, List.of("common", "fungal", "hardy")),
            new Soil("mycelium", 1, 1.10F, List.of("fungal")),
            new Soil("sand", 1, 0.80F, List.of("desert")),
            new Soil("red_sand", 1, 0.80F, List.of("desert", "hardy")),
            new Soil("soul_soil", 2, 1.00F, List.of("nether", "fungal")),
            new Soil("end_stone", 2, 0.90F, List.of("end")),
            new Soil("clay_bed", 2, 1.35F, List.of("common", "crop", "aquatic")),
            new Soil("gravel_bed", 2, 0.95F, List.of("aquatic", "hardy")),
            new Soil("rich_loam", 3, 1.70F, List.of("common", "crop", "hardy", "desert")),
            new Soil("ashen_bed", 3, 1.55F, List.of("nether", "hardy")),
            new Soil("void_silt", 4, 1.90F, List.of("end", "fungal", "aquatic")));

    /** Every sprout a pot can grow. */
    public static final List<Sprout> SPROUTS = List.of(
            new Sprout("wheat", "crop", 1200, "wheat", 2),
            new Sprout("carrot", "crop", 1200, "carrot", 2),
            new Sprout("potato", "crop", 1200, "potato", 2),
            new Sprout("beetroot", "crop", 1300, "beetroot", 2),
            new Sprout("melon", "crop", 2400, "melon_slice", 3),
            new Sprout("pumpkin", "crop", 2400, "pumpkin", 2),
            new Sprout("sugar_cane", "common", 1600, "sugar_cane", 2),
            new Sprout("bamboo", "common", 1400, "bamboo", 3),
            new Sprout("oak_sapling", "common", 3600, "oak_log", 1),
            new Sprout("spruce_sapling", "common", 3600, "spruce_log", 1),
            new Sprout("birch_sapling", "common", 3600, "birch_log", 1),
            new Sprout("jungle_sapling", "common", 4000, "jungle_log", 1),
            new Sprout("cactus", "desert", 2000, "cactus", 2),
            new Sprout("dead_bush", "desert", 1800, "stick", 2),
            new Sprout("brown_mushroom", "fungal", 2000, "brown_mushroom", 2),
            new Sprout("red_mushroom", "fungal", 2000, "red_mushroom", 2),
            new Sprout("crimson_fungus", "fungal", 2600, "crimson_stem", 1),
            new Sprout("warped_fungus", "fungal", 2600, "warped_stem", 1),
            new Sprout("nether_wart", "nether", 2200, "nether_wart", 2),
            new Sprout("soul_flower", "nether", 2800, "soul_sand", 1),
            new Sprout("chorus_flower", "end", 4200, "chorus_fruit", 2),
            new Sprout("ender_bloom", "end", 4600, "ender_pearl", 1),
            new Sprout("kelp", "aquatic", 1500, "dried_kelp", 2),
            new Sprout("sea_pickle", "aquatic", 2600, "sea_pickle", 2),
            new Sprout("lily_pad", "aquatic", 2000, "lily_pad", 2),
            new Sprout("sweet_berry", "hardy", 2400, "sweet_berries", 3),
            new Sprout("glow_berry", "hardy", 2800, "glow_berries", 2),
            new Sprout("moss_tuft", "hardy", 1600, "moss_block", 1));

    // ==================================================================
    // The catalogue, which is derived rather than written out
    // ==================================================================

    /** Every material a pot can be made of. */
    public static List<String> materials() {
        List<String> all = new ArrayList<>();
        for (String ceramic : CERAMICS) {
            for (String colour : COLOURS) {
                all.add(colour + "_" + ceramic);
            }
        }
        all.addAll(STONES);
        return List.copyOf(all);
    }

    /** Every pot there is. */
    public static List<Pot> catalogue() {
        List<Pot> made = new ArrayList<>();
        for (String material : materials()) {
            for (String fitting : FITTINGS) {
                made.add(new Pot(material, fitting));
            }
        }
        return List.copyOf(made);
    }

    private static final Map<String, Soil> SOIL_BY_ID = new LinkedHashMap<>();

    static {
        for (Soil soil : SOILS) {
            SOIL_BY_ID.put(soil.id(), soil);
        }
    }

    /** The soil of that name, or null. */
    public static Soil soil(String id) {
        return SOIL_BY_ID.get(id);
    }

    /** Every kind of thing any soil will grow. */
    public static List<String> kinds() {
        Set<String> seen = new TreeSet<>();
        for (Soil soil : SOILS) {
            seen.addAll(soil.grows());
        }
        return List.copyOf(seen);
    }

    /** Every soil that will grow a given sprout. */
    public static List<Soil> soilsFor(Sprout sprout) {
        List<Soil> found = new ArrayList<>();
        for (Soil soil : SOILS) {
            if (soil.takes(sprout.needs())) {
                found.add(soil);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Whether the catalogue hangs together
    // ==================================================================

    /** Every material missing one of the three fittings. */
    public static List<String> halfMade() {
        Map<String, Set<String>> seen = new LinkedHashMap<>();
        for (Pot pot : catalogue()) {
            seen.computeIfAbsent(pot.material(), any -> new TreeSet<>())
                    .add(pot.fitting());
        }
        List<String> bad = new ArrayList<>();
        for (String material : materials()) {
            Set<String> got = seen.getOrDefault(material, Set.of());
            for (String fitting : FITTINGS) {
                if (!got.contains(fitting)) {
                    bad.add(material + " has no " + fitting + " pot");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every pot id that another pot also answers to. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Pot pot : catalogue()) {
            if (!seen.add(pot.id())) {
                twice.add(pot.id());
            }
        }
        return List.copyOf(twice);
    }

    /** Every sprout no soil will grow. */
    public static List<String> ungrowable() {
        List<String> bad = new ArrayList<>();
        for (Sprout sprout : SPROUTS) {
            if (soilsFor(sprout).isEmpty()) {
                bad.add(sprout.id() + " needs " + sprout.needs()
                        + " soil and nothing here is any");
            }
        }
        return List.copyOf(bad);
    }

    /** Every soil that grows everything, leaving the others no reason to exist. */
    public static List<String> growsEverything() {
        List<String> bad = new ArrayList<>();
        List<String> all = kinds();
        for (Soil soil : SOILS) {
            if (soil.grows().containsAll(all)) {
                bad.add(soil.id() + " grows every kind there is, so it is the only "
                        + "soil and the rest are decoration");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind of soil no sprout ever asks for. */
    public static List<String> unwanted() {
        Set<String> asked = new TreeSet<>();
        for (Sprout sprout : SPROUTS) {
            asked.add(sprout.needs());
        }
        List<String> spare = new ArrayList<>();
        for (String kind : kinds()) {
            if (!asked.contains(kind)) {
                spare.add(kind);
            }
        }
        return List.copyOf(spare);
    }

    /** Every rung of the soil ladder that is no faster than the one below. */
    public static List<String> flatLadder() {
        Map<Integer, Float> best = new LinkedHashMap<>();
        for (Soil soil : SOILS) {
            best.merge(soil.tier(), soil.speed(), Math::max);
        }
        List<Integer> rungs = new ArrayList<>(new TreeSet<>(best.keySet()));
        List<String> bad = new ArrayList<>();
        for (int step = 1; step < rungs.size(); step++) {
            int rung = rungs.get(step);
            int below = rungs.get(step - 1);
            if (best.get(rung) <= best.get(below)) {
                bad.add("the best tier " + rung + " soil is no faster than the best "
                        + "tier " + below + " one, so that rung buys nothing");
            }
            if (rung != below + 1) {
                bad.add("nothing at all sits at tier " + (below + 1));
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Whether a wax finish quietly changes how fast a pot grows.
     *
     * <p>The one claim in this file that is easy to break by accident and
     * impossible to notice in play: waxing is a finish. If it ever becomes an
     * upgrade, every pot in every base is the wrong one and nobody can say
     * why.
     */
    public static boolean waxIsCosmetic() {
        return speedOf(new Pot("stone", WAXED)) == speedOf(new Pot("stone", PLAIN));
    }

    /**
     * What a fitting does to how fast a pot grows.
     *
     * <p>Nothing, in every case. A hopper saves a walk, not a wait; that is
     * what makes it an upgrade worth paying for without being a speed ladder
     * hiding inside a cosmetic one.
     */
    public static float speedOf(Pot pot) {
        return 1.0F;
    }

    /** Every fitting that changes the speed rather than the convenience. */
    public static List<String> fittingsThatHurry() {
        List<String> bad = new ArrayList<>();
        for (String fitting : FITTINGS) {
            if (speedOf(new Pot("stone", fitting)) != 1.0F) {
                bad.add("a " + fitting + " pot grows at a different speed, so the "
                        + "fitting is a hidden upgrade");
            }
        }
        return List.copyOf(bad);
    }

    /** Every sprout whose drop is the thing you planted, with nothing gained. */
    public static List<String> pointless() {
        List<String> bad = new ArrayList<>();
        for (Sprout sprout : SPROUTS) {
            if (sprout.drop().equals(sprout.id()) && sprout.amount() <= 1) {
                bad.add(sprout.id() + " drops one of itself, so growing it is a way "
                        + "of waiting");
            }
        }
        return List.copyOf(bad);
    }
}
