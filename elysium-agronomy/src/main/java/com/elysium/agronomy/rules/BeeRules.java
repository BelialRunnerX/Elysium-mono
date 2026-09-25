package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bees, and the mistake the mod this replaces makes.
 *
 * <h2>Four hundred and twenty-eight bees nobody can catch</h2>
 *
 * The jar this module replaces registers 463 bee entities. Thirty-four of them
 * name a material that exists in this pack. The other four hundred and
 * twenty-eight are bees for Allthemodium, Botania, GregTech, Thermal and a
 * dozen other mods that are not installed &mdash; an actinium bee, a bohrium
 * bee, an alfsteel bee &mdash; each a registered entity with a spawn egg, a
 * comb and a translation, and no way on earth to obtain it, because the
 * material it is named after does not exist in this world.
 *
 * <p>That is not a criticism of the mod, which is written to work in any pack.
 * It is why this file has the shape it has. A resource bee here names a
 * material, and {@code validate.py} reads the foundry's own material list out
 * of its source and fails the build if a bee names something this pack cannot
 * make. A bee nobody can obtain is not content; it is a row in a registry.
 *
 * <h2>What a bee is</h2>
 *
 * A species, a thing it produces, and the two bees it is bred from. Its tier
 * is <em>not</em> written down: it is how deep its breeding goes, computed
 * from its parents. A number that can be written down separately from the
 * thing it describes is a number that will one day disagree with it, and this
 * project has already lost an afternoon to exactly that.
 *
 * <h2>The rules this holds itself to</h2>
 *
 * Every bee is reachable from the four you can find in the world. No bee is
 * its own ancestor. Every pair of parents breeds exactly one child, so every
 * bee can be got on purpose. No two bees produce the same thing, so a comb
 * says what made it. And a hive of the best bee in the game is still worse per
 * hour than a mine, which is the rule {@link AgronomyRules} holds crops to and
 * for the same reason: the moment a farm beats a mine, the ore ladder three
 * ages are built on stops mattering.
 */
public final class BeeRules {

    private BeeRules() {
    }

    // ==================================================================
    // What a bee is
    // ==================================================================

    /** A bee you find in the world rather than breed. */
    public static final String WILD = "wild";

    /** A bee whose comb smelts down into a material. */
    public static final String RESOURCE = "resource";

    /** A bee bound to somewhere &mdash; a biome, a dimension, a depth. */
    public static final String PLACE = "place";

    /** A bee that gives something that is not a material. */
    public static final String PRODUCT = "product";

    /** The kinds there are. */
    public static final List<String> KINDS = List.of(WILD, RESOURCE, PLACE, PRODUCT);

    /**
     * One species.
     *
     * <p>There is no tier field on purpose. See {@link #tierOf}.
     *
     * @param id      what it is called, without the {@code _bee}
     * @param kind    one of {@link #KINDS}
     * @param produce what its comb gives
     * @param parents the two it is bred from, or empty if you find it
     * @param home    the place it wants, or empty if anywhere will do
     */
    public record Bee(String id, String kind, String produce,
                      List<String> parents, String home) {

        public Bee {
            Objects.requireNonNull(id, "a bee with no name");
            Objects.requireNonNull(kind, "a bee with no kind");
            Objects.requireNonNull(produce, "a bee that produces nothing is a pet");
            parents = List.copyOf(Objects.requireNonNull(parents, "null parents"));
            Objects.requireNonNull(home, "null home");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("no such kind of bee: " + kind);
            }
            if (parents.size() != 0 && parents.size() != 2) {
                throw new IllegalArgumentException("a bee is bred from two bees or "
                        + "found in the world, not from " + parents.size());
            }
            if (WILD.equals(kind) != parents.isEmpty()) {
                throw new IllegalArgumentException(
                        "a wild bee is the one you do not breed: " + id);
            }
        }

        /** Whether this is one you find without breeding. */
        public boolean found() {
            return parents.isEmpty();
        }

        /** The entity id the game knows it by. */
        public String entity() {
            return id + "_bee";
        }

        /** The comb it drops. */
        public String comb() {
            return id + "_comb";
        }

        /** The block a pile of that comb makes. */
        public String combBlock() {
            return id + "_comb_block";
        }

        /** The nest or hive it is found in or kept in. */
        public String hive() {
            return id + "_hive";
        }
    }

    // ==================================================================
    // The roster
    // ==================================================================

    private static Bee wild(String id, String produce, String home) {
        return new Bee(id, WILD, produce, List.of(), home);
    }

    private static Bee resource(String id, String first, String second) {
        return new Bee(id, RESOURCE, id, List.of(first, second), "");
    }

    private static Bee place(String id, String home, String produce,
                             String first, String second) {
        return new Bee(id, PLACE, produce, List.of(first, second), home);
    }

    private static Bee product(String id, String produce, String first, String second) {
        return new Bee(id, PRODUCT, produce, List.of(first, second), "");
    }

    /**
     * Five things a resource bee may name that the foundry does not make.
     *
     * <p>They are the game's own, and a bee for each is the point of the
     * feature. Everything else a resource bee names has to be a material the
     * foundry lists, and {@code validate.py} checks it against the foundry's
     * source rather than against a copy kept here.
     */
    public static final Set<String> THE_GAMES_OWN =
            Set.of("lapis", "quartz", "amethyst", "emerald", "diamond");

    /** Every bee there is. */
    public static final List<Bee> BEES = List.of(
            wild("meadow", "honey", "plains"),
            wild("forest", "resin", "forest"),
            wild("stone", "grit", "mountain"),
            wild("water", "brine", "river"),
            resource("iron", "stone", "meadow"),
            resource("copper", "stone", "water"),
            resource("coal_dust", "stone", "forest"),
            product("wax", "beeswax", "meadow", "forest"),
            place("marsh", "swamp", "silt", "forest", "water"),
            place("dune", "desert", "sandgrain", "meadow", "water"),
            resource("tin", "iron", "stone"),
            resource("zinc", "copper", "stone"),
            resource("lead", "coal_dust", "stone"),
            resource("redstone_dust", "coal_dust", "copper"),
            place("ember", "nether", "ash", "coal_dust", "forest"),
            place("glacier", "frozen", "rime", "coal_dust", "water"),
            place("mountainside", "mountain", "scree", "meadow", "iron"),
            place("canopy", "jungle", "sap", "forest", "marsh"),
            product("sugar", "sugar", "meadow", "marsh"),
            product("clay", "clay_lump", "water", "marsh"),
            product("bone", "bonemeal", "stone", "marsh"),
            product("glassy", "glass_dust", "dune", "stone"),
            place("cavern", "underground", "spelaean_wax", "coal_dust", "meadow"),
            resource("gold", "iron", "copper"),
            resource("silver", "iron", "lead"),
            resource("nickel", "iron", "zinc"),
            resource("aluminum", "tin", "stone"),
            resource("lithium", "zinc", "water"),
            resource("lapis", "redstone_dust", "water"),
            resource("quartz", "redstone_dust", "stone"),
            resource("crimson_iron", "iron", "ember"),
            place("tide", "ocean", "salt", "water", "dune"),
            place("mycelial", "mushroom", "spore", "marsh", "canopy"),
            place("sootfall", "volcanic", "cinder", "ember", "mountainside"),
            place("skylark", "highlands", "cirrus_wax", "mountainside", "meadow"),
            place("hollow", "deepslate", "deep_wax", "cavern", "stone"),
            product("string", "silk", "canopy", "forest"),
            product("slimy", "slime_ball", "clay", "marsh"),
            product("glowing", "glowdust", "ember", "redstone_dust"),
            resource("osmium", "nickel", "silver"),
            resource("platinum", "gold", "nickel"),
            resource("uranium", "lead", "redstone_dust"),
            resource("amethyst", "quartz", "water"),
            resource("fluorite", "quartz", "lapis"),
            resource("bort", "coal_dust", "quartz"),
            resource("rime_shard", "water", "glacier"),
            resource("azure_silver", "silver", "glacier"),
            resource("emerald", "gold", "mountainside"),
            resource("bronze", "copper", "tin"),
            resource("brass", "copper", "zinc"),
            resource("steel", "iron", "coal_dust"),
            resource("electrum", "gold", "silver"),
            resource("invar", "iron", "nickel"),
            resource("constantan", "copper", "nickel"),
            place("wither_bound", "nether_fortress", "wither_dust", "ember", "hollow"),
            product("ink", "ink_sac", "tide", "cavern"),
            product("prismarine", "prismarine_shard", "tide", "water"),
            product("gunpowder", "gunpowder", "sootfall", "bone"),
            product("blaze", "blaze_powder", "ember", "sootfall"),
            product("obsidian", "obsidian_shard", "ember", "cavern"),
            product("sculk", "sculk_flake", "hollow", "cavern"),
            resource("certus_quartz", "quartz", "amethyst"),
            resource("uraninite", "uranium", "stone"),
            resource("diamond", "coal_dust", "bort"),
            resource("cast_iron", "iron", "steel"),
            resource("stainless", "steel", "nickel"),
            resource("signal_alloy", "electrum", "redstone_dust"),
            resource("hardened_bronze", "bronze", "invar"),
            place("voidtouched", "end", "void_wax", "hollow", "wither_bound"),
            product("ender", "ender_dust", "wither_bound", "glowing"),
            resource("void_steel", "stainless", "uranium"),
            resource("starsteel", "stainless", "platinum"),
            place("aurora", "end_highlands", "chorus_wax", "voidtouched", "skylark"),
            product("experience", "experience_dust", "ender", "sculk"),
            product("netherite", "netherite_scrap", "wither_bound", "void_steel"));

    private static final Map<String, Bee> BY_ID = new LinkedHashMap<>();

    static {
        for (Bee bee : BEES) {
            BY_ID.put(bee.id(), bee);
        }
    }

    /** The bee of that name, or null. */
    public static Bee bee(String id) {
        return BY_ID.get(id);
    }

    /** Every bee, by name. */
    public static Map<String, Bee> everything() {
        return Map.copyOf(BY_ID);
    }

    /** Every bee of one kind. */
    public static List<Bee> ofKind(String kind) {
        List<Bee> found = new ArrayList<>();
        for (Bee bee : BEES) {
            if (bee.kind().equals(kind)) {
                found.add(bee);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Tier, which is computed rather than declared
    // ==================================================================

    /**
     * How deep this bee's breeding goes: zero if you find it, otherwise one
     * more than its deepest parent.
     *
     * <p>Returns {@link #A_CYCLE} for a bee that is its own ancestor, which
     * {@link #cycles} then reports. A number that loops has to be able to say
     * so rather than hanging, because this is called from a harness that runs
     * on every build.
     */
    public static int tierOf(String id) {
        return depth(id, new HashMap<>(), new HashSet<>());
    }

    /** What {@link #tierOf} answers for a bee that is its own ancestor. */
    public static final int A_CYCLE = -1;

    private static int depth(String id, Map<String, Integer> known, Set<String> open) {
        Integer had = known.get(id);
        if (had != null) {
            return had;
        }
        Bee bee = BY_ID.get(id);
        if (bee == null) {
            return A_CYCLE;
        }
        if (bee.found()) {
            known.put(id, 0);
            return 0;
        }
        if (!open.add(id)) {
            return A_CYCLE;
        }
        int deepest = 0;
        for (String parent : bee.parents()) {
            int theirs = depth(parent, known, open);
            if (theirs == A_CYCLE) {
                open.remove(id);
                return A_CYCLE;
            }
            deepest = Math.max(deepest, theirs);
        }
        open.remove(id);
        int mine = deepest + 1;
        known.put(id, mine);
        return mine;
    }

    /** Every tier that has a bee on it, lowest first. */
    public static List<Integer> tiers() {
        Set<Integer> seen = new TreeSet<>();
        for (Bee bee : BEES) {
            int tier = tierOf(bee.id());
            if (tier != A_CYCLE) {
                seen.add(tier);
            }
        }
        return List.copyOf(seen);
    }

    /** The deepest breeding chain there is. */
    public static int longestChain() {
        int deepest = 0;
        for (Bee bee : BEES) {
            deepest = Math.max(deepest, Math.max(0, tierOf(bee.id())));
        }
        return deepest;
    }

    // ==================================================================
    // How long a bee takes, and why it never beats a pickaxe
    // ==================================================================

    /** How much slower each tier is than the one below. */
    public static final float TIER_SLOWER = 1.35F;

    /** How much of a material one comb smelts down to, in the foundry's units. */
    public static final int PER_COMB = 1;

    /**
     * How long the first bee that makes a material takes over one comb.
     *
     * <p>Not a taste, and no longer a number at all: it is what makes the
     * best apiary anybody can build land exactly on {@link #HIVE_AT_MOST} of
     * {@link AgronomyRules#minedPerHour()}, solved on every build by {@link
     * ApiaryRules#ticksForTheCeiling()}.
     *
     * <p>It used to be 14,400 — four real minutes a bee, twelve bees to a
     * hive, sixty an hour against a mine's two hundred and forty. Which put a
     * <em>bare</em> hive exactly on the ceiling and left nowhere at all for an
     * upgrade to go: anything worth fitting put a hive over the line, and an
     * upgrade not worth fitting is a recipe nobody makes twice. So the ceiling
     * stayed where it was and the floor came down under it.
     */
    public static final int FIRST_RESOURCE_TICKS = ApiaryRules.ticksForTheCeiling();

    /** How long a wild bee takes over a comb of honey, which is not a material. */
    public static final int WILD_TICKS = AgronomyRules.COMB_TICKS;

    /** How long one comb takes from a bee at this tier. */
    public static int combTicks(int tier) {
        if (tier <= 0) {
            return WILD_TICKS;
        }
        float ticks = FIRST_RESOURCE_TICKS;
        for (int step = 1; step < tier; step++) {
            ticks *= TIER_SLOWER;
        }
        return Math.round(ticks);
    }

    /** Combs an hour from one full hive at this tier, with flowers enough. */
    public static float perHour(int tier) {
        return AgronomyRules.MOST_BEES
                * ((float) AgronomyRules.TICKS_PER_HOUR / combTicks(tier)) * PER_COMB;
    }

    /**
     * How much of a mine a whole hive may be worth.
     *
     * <p>Lower than the crops' share, deliberately. A hive wants no light, no
     * water, no replanting and no attention at all; the only honest way to
     * price something that runs itself is to make it slow.
     */
    public static final float HIVE_AT_MOST = 0.25F;

    /** Whether a hive at this tier stays under the margin against a mine. */
    public static boolean underTheMargin(int tier) {
        return perHour(tier) <= AgronomyRules.minedPerHour() * HIVE_AT_MOST;
    }

    /**
     * Every tier carrying a resource bee at which a hive would out-earn a mine.
     *
     * <p>Only tiers with a resource bee on them: a wild bee makes honey, and
     * honey is not something the ore ladder is priced against.
     */
    public static List<Integer> outMining() {
        Set<Integer> earning = new TreeSet<>();
        for (Bee bee : ofKind(RESOURCE)) {
            int tier = tierOf(bee.id());
            if (tier != A_CYCLE && !underTheMargin(tier)) {
                earning.add(tier);
            }
        }
        return List.copyOf(earning);
    }

    // ==================================================================
    // Whether the roster hangs together
    // ==================================================================

    /** Every bee that names a parent that is not a bee. */
    public static List<String> orphaned() {
        List<String> bad = new ArrayList<>();
        for (Bee bee : BEES) {
            for (String parent : bee.parents()) {
                if (!BY_ID.containsKey(parent)) {
                    bad.add(bee.id() + " is bred from " + parent
                            + ", which is not a bee");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every bee that is its own ancestor. */
    public static List<String> cycles() {
        List<String> bad = new ArrayList<>();
        for (Bee bee : BEES) {
            if (tierOf(bee.id()) == A_CYCLE) {
                bad.add(bee.id() + " is, however indirectly, bred from itself");
            }
        }
        return List.copyOf(bad);
    }

    /** Every bee no chain of breeding reaches from the ones you find. */
    public static List<String> unreachable() {
        Set<String> have = new HashSet<>();
        for (Bee bee : BEES) {
            if (bee.found()) {
                have.add(bee.id());
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Bee bee : BEES) {
                if (!have.contains(bee.id()) && have.containsAll(bee.parents())) {
                    have.add(bee.id());
                    grew = true;
                }
            }
        }
        List<String> lost = new ArrayList<>();
        for (Bee bee : BEES) {
            if (!have.contains(bee.id())) {
                lost.add(bee.id());
            }
        }
        return List.copyOf(lost);
    }

    /** Every pair of bees making the same thing. */
    public static List<String> sharingProduce() {
        Map<String, String> first = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        for (Bee bee : BEES) {
            String had = first.putIfAbsent(bee.produce(), bee.id());
            if (had != null) {
                clashes.add(bee.id() + " and " + had + " both produce " + bee.produce()
                        + ", so a comb no longer says what made it");
            }
        }
        return List.copyOf(clashes);
    }

    /** Every parent pair used for more than one child. */
    public static List<String> sharingParents() {
        Map<String, String> first = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        for (Bee bee : BEES) {
            if (bee.found()) {
                continue;
            }
            List<String> pair = new ArrayList<>(bee.parents());
            pair.sort(String::compareTo);
            String key = String.join(" + ", pair);
            String had = first.putIfAbsent(key, bee.id());
            if (had != null) {
                clashes.add(key + " breeds both " + had + " and " + bee.id()
                        + ", so one of them can never be got on purpose");
            }
        }
        return List.copyOf(clashes);
    }

    /** Every bee bred from two of the same bee, or from itself. */
    public static List<String> inbred() {
        List<String> bad = new ArrayList<>();
        for (Bee bee : BEES) {
            if (bee.parents().contains(bee.id())) {
                bad.add(bee.id() + " is bred from itself");
            } else if (bee.parents().size() == 2
                    && bee.parents().get(0).equals(bee.parents().get(1))) {
                bad.add(bee.id() + " is bred from two of the same bee, which is "
                        + "not breeding");
            }
        }
        return List.copyOf(bad);
    }

    /** Every bee bound to the wrong kind of place, or to none. */
    public static List<String> misplaced() {
        List<String> bad = new ArrayList<>();
        for (Bee bee : BEES) {
            boolean wants = PLACE.equals(bee.kind()) || WILD.equals(bee.kind());
            if (wants && bee.home().isEmpty()) {
                bad.add(bee.id() + " is a " + bee.kind() + " bee with nowhere to live");
            }
            if (!wants && !bee.home().isEmpty()) {
                bad.add(bee.id() + " is a " + bee.kind() + " bee and should not be "
                        + "bound to " + bee.home());
            }
        }
        return List.copyOf(bad);
    }

    /** Every material a resource bee claims, in roster order. */
    public static List<String> materialsNamed() {
        List<String> named = new ArrayList<>();
        for (Bee bee : ofKind(RESOURCE)) {
            named.add(bee.produce());
        }
        return List.copyOf(named);
    }

    /** Every place a bee is bound to, once each. */
    public static List<String> homes() {
        Set<String> seen = new TreeSet<>();
        for (Bee bee : BEES) {
            if (!bee.home().isEmpty()) {
                seen.add(bee.home());
            }
        }
        return List.copyOf(seen);
    }
}
