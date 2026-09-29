package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * Seeds that grow metal, and the one thing they are not allowed to grow.
 *
 * <h2>The rule that keeps three ages standing</h2>
 *
 * A seed that yields iron is a problem this module already answers: it has to
 * be worse than a mine, by a stated margin, and {@link AgronomyRules} proves
 * it on every build. But there is a second way to break the game with seeds
 * and it is quieter: <b>growing something that is made rather than mined</b>.
 *
 * <p>An ore seed competes with a pickaxe, which is priced. A <em>steel</em>
 * seed competes with the whole foundry — the furnace, the alloy recipe, the
 * age that was built to supply it — and it wins, because a field needs no
 * machines. So the rule here is that a seed may only grow something the ground
 * gives you. {@link #growingWhatIsMade} is the check, and {@code validate.py}
 * reads the foundry's own material list to run it, exactly as the bees do.
 *
 * <h2>What is in here</h2>
 *
 * Thirty seeds: twenty metals and stones the ground has, and ten of the game's
 * own. No alloy, no processed material, nothing a machine makes. The tier is
 * how far up the essence ladder a material sits, and the ladder is made of
 * time rather than yield, so wanting more is always answered by a bigger farm.
 *
 * <h2>The way onto the ladder</h2>
 *
 * A pot grows a rung's essence only out of a seed of that rung. So what the
 * first rung's seeds cost decides whether the ladder can be climbed at all:
 * {@link #FIRST_RUNG_PRICE} is the game's own wheat seeds, every rung above
 * pays in its own essence, and {@link #unreachableRungs} walks the ladder
 * from nothing it makes and names any rung it cannot reach.
 */
public final class SeedRules {

    private SeedRules() {
    }

    /**
     * One seed.
     *
     * @param material what it grows
     * @param tier     which rung of the essence ladder it sits on
     * @param makes    the item enough essence turns into
     */
    public record Seed(String material, int tier, String makes) {

        public Seed {
            Objects.requireNonNull(material, "a seed that grows nothing");
            Objects.requireNonNull(makes, "a seed whose essence becomes nothing");
            if (tier < 1 || tier > AgronomyRules.TIERS) {
                throw new IllegalArgumentException(material + " sits at tier " + tier
                        + " and the ladder is 1 to " + AgronomyRules.TIERS);
            }
        }

        /** The seed item's name. */
        public String id() {
            return material + "_seed";
        }
    }

    private static Seed mined(String material, int tier, String makes) {
        return new Seed(material, tier, makes);
    }

    /**
     * Every seed there is.
     *
     * <p>Twenty of these name a material the foundry lists as <em>mined</em>,
     * and ten name something the game itself digs up. Nothing here names an
     * alloy, and {@code validate.py} refuses the build if one ever does.
     */
    public static final List<Seed> SEEDS = List.of(
            // Tier 1 — things you find in the first hour.
            mined("coal", 1, "minecraft:coal"),
            mined("copper", 1, "minecraft:copper_ingot"),
            mined("tin", 1, "elysiumagronomy:tin_chunk"),
            mined("zinc", 1, "elysiumagronomy:zinc_chunk"),
            mined("iron", 1, "minecraft:iron_ingot"),
            mined("lead", 1, "elysiumagronomy:lead_chunk"),
            mined("redstone", 1, "minecraft:redstone"),

            // Tier 2 — a little deeper, or a little rarer.
            mined("nickel", 2, "elysiumagronomy:nickel_chunk"),
            mined("silver", 2, "elysiumagronomy:silver_chunk"),
            mined("aluminum", 2, "elysiumagronomy:aluminum_chunk"),
            mined("lithium", 2, "elysiumagronomy:lithium_chunk"),
            mined("lapis", 2, "minecraft:lapis_lazuli"),
            mined("quartz", 2, "minecraft:quartz"),
            mined("glowstone", 2, "minecraft:glowstone_dust"),

            // Tier 3 — the ones worth a trip.
            mined("gold", 3, "minecraft:gold_ingot"),
            mined("osmium", 3, "elysiumagronomy:osmium_chunk"),
            mined("amethyst", 3, "minecraft:amethyst_shard"),
            mined("fluorite", 3, "elysiumagronomy:fluorite_chunk"),
            mined("certus_quartz", 3, "elysiumagronomy:certus_quartz_chunk"),
            mined("obsidian", 3, "minecraft:obsidian"),
            mined("crimson_iron", 3, "elysiumagronomy:crimson_iron_chunk"),

            // Tier 4 — deep, cold, or somewhere else entirely.
            mined("platinum", 4, "elysiumagronomy:platinum_chunk"),
            mined("uranium", 4, "elysiumagronomy:uranium_chunk"),
            mined("uraninite", 4, "elysiumagronomy:uraninite_chunk"),
            mined("bort", 4, "elysiumagronomy:bort_chunk"),
            mined("rime_shard", 4, "elysiumagronomy:rime_shard_chunk"),
            mined("azure_silver", 4, "elysiumagronomy:azure_silver_chunk"),
            mined("diamond", 4, "minecraft:diamond"),
            mined("emerald", 4, "minecraft:emerald"),

            // Tier 5 — the top, and the only thing up here.
            mined("netherite", 5, "minecraft:netherite_scrap"));

    /**
     * The materials whose essence turns into something this module has to make
     * itself.
     *
     * <p>A recipe result must be a concrete item; a tag will not do, and a
     * recipe whose result is {@code c:ingots/tin} simply fails to load. The
     * foundry has the ingots but the foundry sits <em>above</em> this module,
     * so this cannot reach them. So the essence becomes a chunk of its own,
     * tagged {@code c:raw_materials/<material>} — which is what the foundry
     * already smelts.
     */
    public static List<String> chunked() {
        List<String> ours = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (seed.makes().startsWith("elysiumagronomy:")) {
                ours.add(seed.material());
            }
        }
        return List.copyOf(ours);
    }

    /** The chunk item for a material, whether or not one is needed. */
    public static String chunkOf(String material) {
        return material + "_chunk";
    }

    private static final Map<String, Seed> BY_MATERIAL = new LinkedHashMap<>();

    static {
        for (Seed seed : SEEDS) {
            BY_MATERIAL.put(seed.material(), seed);
        }
    }

    /** The seed for that material, or null. */
    public static Seed seed(String material) {
        return BY_MATERIAL.get(material);
    }

    /** Every material a seed grows. */
    public static List<String> materials() {
        List<String> all = new ArrayList<>();
        for (Seed seed : SEEDS) {
            all.add(seed.material());
        }
        return List.copyOf(all);
    }

    /** Every seed at one rung of the ladder. */
    public static List<Seed> atTier(int tier) {
        List<Seed> found = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (seed.tier() == tier) {
                found.add(seed);
            }
        }
        return List.copyOf(found);
    }

    /**
     * The things the game digs up that are not in the foundry's list.
     *
     * <p>Declared here so {@code validate.py} can tell "a seed for something
     * the ground gives you" from "a seed for something a machine makes"
     * without a second copy of the foundry's table.
     */
    public static final Set<String> THE_GAMES_OWN = Set.of(
            "coal", "redstone", "lapis", "quartz", "amethyst", "glowstone",
            "obsidian", "diamond", "emerald", "netherite");

    /**
     * Every seed that grows something a machine makes rather than something
     * the ground gives you.
     *
     * <p>Takes the foundry's mined list from outside rather than keeping a
     * copy: the validator reads it out of the foundry's own source, so the two
     * cannot drift.
     *
     * @param mined every material the foundry says comes out of the ground
     * @param made  every material the foundry says is made from others
     */
    public static List<String> growingWhatIsMade(Set<String> mined, Set<String> made) {
        List<String> bad = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (made.contains(seed.material())) {
                bad.add(seed.material() + " is made rather than mined, and a seed for "
                        + "it competes with the whole foundry rather than with a "
                        + "pickaxe");
            } else if (!mined.contains(seed.material())
                    && !THE_GAMES_OWN.contains(seed.material())) {
                bad.add(seed.material() + " is neither mined nor one of the game's "
                        + "own, so nothing in this pack can supply it and the seed "
                        + "grows something that does not exist");
            }
        }
        return List.copyOf(bad);
    }

    /** Every rung of the ladder with nothing on it. */
    public static List<String> emptyRungs() {
        List<String> bad = new ArrayList<>();
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            if (atTier(tier).isEmpty()) {
                bad.add("nothing at all grows at tier " + tier + ", so that rung of "
                        + "the ladder is a length of time nobody ever waits");
            }
        }
        return List.copyOf(bad);
    }

    /** Every material two seeds grow. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (!seen.add(seed.material())) {
                twice.add(seed.material());
            }
        }
        return List.copyOf(twice);
    }

    // ==================================================================
    // What a seed costs, and the way onto the ladder
    // ==================================================================

    /**
     * What a seed on the first rung is paid in: four of the game's wheat
     * seeds, where a seed on any rung above costs four of its own rung's
     * essence ({@link AgronomyRules#ESSENCE_PER_STEP} either way), besides a
     * sample of what it grows.
     *
     * <p>The first rung was paid in faint essence, and faint essence comes
     * only out of a pot growing a first-rung seed. The first faint essence
     * needed a seed that cost faint essence, so the whole ladder, the altar
     * and every awakened seed waited on something no world could make. Wheat
     * seeds are the way in; the pot keeps the seed, so one bought seed is a
     * rung for good, and every rung above still costs what the rung below
     * grows.
     */
    public static final String FIRST_RUNG_PRICE = "minecraft:wheat_seeds";

    /** Whether a seed of this tier is paid in its own rung's essence. The first rung is not. */
    public static boolean paidInEssence(int tier) {
        return tier > 1;
    }

    /**
     * Every rung of the essence ladder a player cannot reach starting from
     * nothing the ladder makes. Must be empty.
     */
    public static List<String> unreachableRungs() {
        return unreachableRungs(SEEDS, SeedRules::paidInEssence);
    }

    /**
     * The same walk, over seeds and a price handed in, so a control can hand
     * it the loop this ladder used to be.
     *
     * <p>A rung is reached when a seed on it can be bought without its own
     * essence, because a pot grows the rung's essence out of that seed; or
     * when the rung below is reached, because {@link
     * AgronomyRules#ESSENCE_PER_STEP} of one rung climb to one of the next. A
     * seed paid in its own rung's essence buys nothing on a rung nothing
     * else reaches: that is the loop.
     */
    public static List<String> unreachableRungs(List<Seed> seeds, IntPredicate paidInEssence) {
        List<String> lost = new ArrayList<>();
        boolean below = false;
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            boolean bought = false;
            for (Seed seed : seeds) {
                if (seed.tier() == tier && !paidInEssence.test(tier)) {
                    bought = true;
                }
            }
            boolean reached = below || bought;
            if (!reached) {
                lost.add("rung " + tier + " cannot be reached from nothing the ladder makes: "
                        + "no rung under it is reached, and every seed on it costs its own "
                        + "essence, which only a pot growing one of those seeds makes");
            }
            below = reached;
        }
        return List.copyOf(lost);
    }

    /**
     * Every rung above the first that is paid for from outside the ladder.
     *
     * <p>Must be empty. A top-rung seed bought with wheat is a ladder whose
     * rungs are all on the floor: the time the tiers are made of would be
     * skipped by a recipe.
     */
    public static List<String> faultsInPrice() {
        List<String> bad = new ArrayList<>();
        for (int tier = 2; tier <= AgronomyRules.TIERS; tier++) {
            if (!paidInEssence(tier)) {
                bad.add("a tier " + tier + " seed is paid for from outside the ladder, so "
                        + "the rungs under it are time nobody has to spend");
            }
        }
        return List.copyOf(bad);
    }

    /** Every seed whose essence turns into nothing anybody can name. */
    public static List<String> makingNothing() {
        List<String> bad = new ArrayList<>();
        for (Seed seed : SEEDS) {
            String makes = seed.makes();
            // A recipe result has to be a concrete item. Ours and the game's
            // both are; a tag is not, and a recipe whose result is a tag
            // simply fails to load.
            boolean named = makes.startsWith("minecraft:")
                    || makes.startsWith("elysiumagronomy:");
            if (!named || makes.endsWith(":")) {
                bad.add(seed.material() + "'s essence turns into " + makes
                        + ", which is not something anything can be asked for");
            }
        }
        return List.copyOf(bad);
    }
}
