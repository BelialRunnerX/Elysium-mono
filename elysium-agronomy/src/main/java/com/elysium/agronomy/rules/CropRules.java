package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The crops behind the kitchen, and the hole they were filling.
 *
 * <h2>Twenty-nine meals made of nothing</h2>
 *
 * {@link KitchenRules} has twenty-nine meals, twenty-four ingredients and a
 * domination rule that none of them is pointless. Eight of those ingredients
 * — cabbage, onion, tomato, rice, barley, chili, garlic, ginger — were items
 * with a sprite, a crate to store them in, a place in a dozen recipes, and no
 * way on earth to obtain a single one. Not a crash, not a warning: a cookbook
 * for a cupboard nobody can fill.
 *
 * <p>Which is the same hole as a bee for a metal nothing makes, and it is
 * found the same way: by asking, of every ingredient, where it comes from,
 * and failing the build when the answer is nowhere.
 *
 * <h2>What a crop is priced against</h2>
 *
 * Not the mine. A cabbage is not competing with a pickaxe and pretending it
 * is would be the wrong rule applied precisely. What a food crop competes
 * with is <b>wheat</b>, which the game already gives away, and the rule is
 * the one the arsenal uses for weapons: nothing may be at least as good as
 * wheat on every axis and better on one. A crop that grows faster than wheat,
 * yields more than wheat and feeds better than wheat is not a new crop, it is
 * a reason never to plant wheat again.
 *
 * <h2>And where the first seed comes from</h2>
 *
 * A wild patch. Every crop generates somewhere as an untended clump, or the
 * whole kitchen is locked behind a village that may not exist — which is the
 * same hole again, one step further back.
 */
public final class CropRules {

    private CropRules() {
    }

    // ==================================================================
    // What a crop is
    // ==================================================================

    /** Ground a crop wants. */
    public static final String TILLED = "tilled";

    /** Shallow water, which is what rice wants and nothing else does. */
    public static final String PADDY = "paddy";

    /** The dark, which is what a mushroom wants. */
    public static final String DARK = "dark";

    /** Every kind of ground there is. */
    public static final List<String> GROUNDS = List.of(TILLED, PADDY, DARK);

    /**
     * One crop.
     *
     * @param id        what it is called
     * @param ground    what it grows in
     * @param stages    how many stages it goes through
     * @param ticks     how long one stage takes at full light
     * @param yield     how many it gives when it is cut
     * @param nutrition how much one of them feeds
     * @param biomes    what sort of place its wild patch turns up in
     */
    public record Crop(String id, String ground, int stages, int ticks,
                       int yield, int nutrition, String biomes) {

        public Crop {
            Objects.requireNonNull(id, "a crop with no name");
            if (!GROUNDS.contains(ground)) {
                throw new IllegalArgumentException(id + " grows in " + ground
                        + ", which is not ground");
            }
            if (stages < 2) {
                throw new IllegalArgumentException(id + " has " + stages
                        + " stage(s), so it is ripe the moment it is planted");
            }
            if (ticks <= 0 || yield <= 0 || nutrition <= 0) {
                throw new IllegalArgumentException(id + " grows instantly, gives "
                        + "nothing, or feeds nobody");
            }
        }

        /** The seed that plants it. */
        public String seed() {
            return id + "_seeds";
        }

        /** The wild patch it turns up in. */
        public String patch() {
            return "wild_" + id;
        }

        /** How long it takes from planting to ripe. */
        public int growing() {
            return ticks * (stages - 1);
        }

        /** How much it feeds an hour, per plot. */
        public float feedsPerHour() {
            return (float) AgronomyRules.TICKS_PER_HOUR / growing()
                    * yield * nutrition;
        }
    }

    /**
     * What the game's own wheat is, in the same terms.
     *
     * <p>Eight stages and the game's own average growth time, and a nutrition
     * of two rather than three — because wheat is not food. Three wheat make
     * a loaf worth five, which is one and two thirds a wheat, and two is that
     * rounded the way that favours wheat. Rounding the other way would let
     * every crop here dominate it by arithmetic rather than by design.
     */
    public static final Crop WHEAT =
            new Crop("wheat", TILLED, 8, 2_400, 1, 2, "plains");

    /**
     * Every crop this module grows.
     *
     * <p>All of them feed <b>one</b> raw, and that is the design rather than
     * an oversight. These are ingredients: what they are for is the twenty-nine
     * meals in {@link KitchenRules} and the thirty-different-foods diet in
     * {@link AgronomyRules}, both of which reward <em>variety</em> rather than
     * calories. A crop that fed well on its own would be a reason never to
     * cook, and cooking is the whole of what this half of the module is.
     *
     * <p>Which leaves two axes — how long it takes and how much it gives —
     * and every one of these sits on the frontier of them: strictly slower and
     * strictly more generous than the one before. So no crop is at least as
     * good as another on every axis and better on one, and there is never a
     * crop nobody would plant.
     */
    public static final List<Crop> CROPS = List.of(
            new Crop("onion", TILLED, 4, 1_800, 1, 1, "plains"),
            new Crop("barley", TILLED, 5, 1_800, 2, 1, "plains"),
            new Crop("garlic", TILLED, 4, 3_000, 3, 1, "forest"),
            new Crop("cabbage", TILLED, 5, 2_700, 4, 1, "plains"),
            new Crop("tomato", TILLED, 5, 3_000, 5, 1, "savanna"),
            new Crop("chili", TILLED, 6, 2_640, 6, 1, "desert"),
            new Crop("ginger", TILLED, 5, 3_600, 7, 1, "jungle"),
            new Crop("rice", PADDY, 6, 2_200, 2, 1, "swamp"));

    /** A crop by name, or null. */
    public static Crop crop(String id) {
        for (Crop one : CROPS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** How many crops that is. */
    public static int howMany() {
        return CROPS.size();
    }

    // ==================================================================
    // The mushroom colony, which is the one that is not a crop
    // ==================================================================

    /** What a colony block is called. */
    public static final String COLONY = "mushroom_colony";

    /** The two it can be a colony of. */
    public static final List<String> MUSHROOMS =
            List.of("brown_mushroom", "red_mushroom");

    /** How long a colony takes over one mushroom. */
    public static final int COLONY_TICKS = 4_000;

    /** How many it holds before it stops. */
    public static final int COLONY_MOST = 3;

    /**
     * How many mushrooms an hour one colony gives.
     *
     * <p>Held to a ceiling for the same reason bait is: mushroom stew is a
     * meal and a block that hands out unlimited mushrooms is a block that
     * ends hunger. Slow enough to be a convenience.
     */
    public static float coloniesPerHour() {
        return (float) AgronomyRules.TICKS_PER_HOUR / COLONY_TICKS;
    }

    /** The most a colony may give in an hour. */
    public static final float MOST_MUSHROOMS_PER_HOUR = 24.0F;

    /**
     * How often the game's random tick reaches one block, on average, in ticks.
     *
     * <p>Three blocks in each sixteen-cube section of four thousand and
     * ninety-six, every tick, at the game's default: one block in about
     * thirteen hundred and sixty-five ticks. A colony is a random-ticking
     * block, so this is the clock it has, and {@link #COLONY_TICKS} is turned
     * into a chance on that clock rather than being quietly a third of itself.
     */
    public static final float RANDOM_TICK_EVERY = 4096.0F / 3.0F;

    /**
     * The chance one random tick moves a colony on by one mushroom.
     *
     * <p>So that the mean wait for a mushroom is {@link #COLONY_TICKS}, which
     * is what the hourly ceiling above is worked out from.
     */
    public static float colonyChance() {
        return Math.min(1.0F, RANDOM_TICK_EVERY / COLONY_TICKS);
    }

    /**
     * What a colony at {@code age} becomes after one random tick that rolled
     * {@code roll} (between zero and one).
     */
    public static int colonyAfter(int age, float roll) {
        if (age >= COLONY_MOST) {
            return COLONY_MOST;
        }
        return roll < colonyChance() ? age + 1 : Math.max(0, age);
    }

    // ==================================================================
    // The larder: what the kitchen makes out of what
    // ==================================================================

    /**
     * One thing the kitchen makes rather than grows or finds.
     *
     * <p>Six of these were missing and nothing said so. Bacon, ham, pasta,
     * stock, a wheel of cheese and a pie crust each had a sprite, a
     * nutrition, a saturation and a place in three or four meals, and no
     * recipe anywhere — a cupboard shelf with a label and nothing on it. The
     * check below is what found them, and it found them by asking the boring
     * question of all twenty-four at once rather than of the ones somebody
     * happened to think about.
     *
     * @param id      what comes out
     * @param station which of the kitchen's four it is made at
     * @param of      what goes in
     */
    public record Larder(String id, String station, List<String> of) {

        public Larder {
            Objects.requireNonNull(id, "a larder item with no name");
            of = List.copyOf(Objects.requireNonNull(of, "made out of nothing"));
            if (of.isEmpty()) {
                throw new IllegalArgumentException(id + " is made out of nothing "
                        + "at all, which is a recipe for something out of air");
            }
            if (of.contains(id)) {
                throw new IllegalArgumentException(id + " is made out of itself");
            }
        }
    }

    /** Everything the kitchen makes out of other things. */
    public static final List<Larder> LARDER = List.of(
            new Larder("bacon", KitchenRules.OVEN, List.of("minced_pork")),
            new Larder("ham", KitchenRules.OVEN, List.of("porkchop")),
            new Larder("pasta", KitchenRules.BOARD, List.of("dough")),
            new Larder("stock", KitchenRules.POT,
                    List.of("bone", "onion", "carrot")),
            new Larder("cheese_wheel", KitchenRules.POT, List.of("milk_bucket")),
            new Larder("pie_crust", KitchenRules.BOARD, List.of("dough", "wheat")));

    /** What the larder puts on the shelf. */
    public static List<String> larder() {
        List<String> made = new ArrayList<>();
        for (Larder one : LARDER) {
            made.add(one.id());
        }
        return List.copyOf(made);
    }

    /** The larder as what its stations are asked for. */
    public static List<KitchenRules.Order> orders(List<Larder> larder) {
        List<KitchenRules.Order> made = new ArrayList<>();
        for (Larder one : larder) {
            made.add(new KitchenRules.Order(one.id(), one.station(), one.of(),
                    KitchenRules.PREP_TICKS, 1));
        }
        return List.copyOf(made);
    }

    /** Every meal and every larder item as orders: the whole of what a station knows. */
    public static List<KitchenRules.Order> allOrders() {
        List<KitchenRules.Order> all = new ArrayList<>(KitchenRules.menu());
        all.addAll(orders(LARDER));
        return List.copyOf(all);
    }

    /** The larder: named once, made at a real station, out of real things. */
    public static List<String> faultsInLarder(List<Larder> larder) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Larder one : larder) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " is made two different ways, so one of the "
                        + "two is a recipe nobody would ever use");
            }
            if (!KitchenRules.STATIONS.contains(one.station())) {
                bad.add(one.id() + " is made at " + one.station()
                        + ", which is not one of the kitchen's stations");
            }
        }
        // Against the meals as well as against itself: a larder item made
        // of exactly what a meal is made of, at the same station, is a
        // station that has to guess which the player meant.
        List<KitchenRules.Order> all = new ArrayList<>(KitchenRules.menu());
        for (Larder one : larder) {
            if (KitchenRules.STATIONS.contains(one.station()) && !one.of().isEmpty()) {
                all.add(new KitchenRules.Order(one.id(), one.station(), one.of(),
                        KitchenRules.PREP_TICKS, 1));
            }
        }
        bad.addAll(KitchenRules.clashing(all));
        return bad;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the crops. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInLarder(LARDER));
        bad.addAll(faultsInRoster(CROPS));
        bad.addAll(dominatingWheat(CROPS));
        bad.addAll(feedingTooWell(CROPS));
        bad.addAll(faultsInColony());
        return List.copyOf(bad);
    }

    /** The roster: named once, in real ground, each with a patch of its own. */
    public static List<String> faultsInRoster(List<Crop> crops) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> places = new LinkedHashSet<>();
        for (Crop one : crops) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two crops");
            }
            if (one.biomes().isEmpty()) {
                bad.add(one.id() + " has no sort of place its wild patch turns "
                        + "up in, so the first seed of it comes from nowhere");
            }
            places.add(one.biomes());
        }
        if (crops.size() > 2 && places.size() < 2) {
            bad.add("every crop's wild patch turns up in the same sort of place, "
                    + "so a player who starts anywhere else starts with none of "
                    + "them");
        }
        int paddies = 0;
        for (Crop one : crops) {
            if (PADDY.equals(one.ground())) {
                paddies++;
            }
        }
        if (!crops.isEmpty() && paddies == crops.size()) {
            bad.add("every crop wants shallow water, which is one crop with "
                    + crops.size() + " names");
        }
        return bad;
    }

    /**
     * Every crop at least as good as another on every axis and better on one.
     *
     * <p>The rule the arsenal holds its weapons to, applied to a field.
     * Wheat is in the comparison as one more crop, so "nothing here obsoletes
     * wheat" and "nothing here obsoletes anything else" are one rule rather
     * than two that can disagree.
     *
     * <p>The categorical axis is the ground: a crop that wants shallow water
     * is not competing with one that wants a field, and without that the rule
     * would demand that rice and barley be comparable.
     *
     * <p>Handed the roster so a control can pass in a crop built to be caught.
     */
    public static List<String> dominatingWheat(List<Crop> crops) {
        List<String> bad = new ArrayList<>();
        List<Crop> everything = new ArrayList<>(crops);
        everything.add(WHEAT);
        for (Crop one : everything) {
            for (Crop other : everything) {
                if (one == other || !one.ground().equals(other.ground())) {
                    continue;
                }
                boolean asGood = one.growing() <= other.growing()
                        && one.yield() >= other.yield()
                        && one.nutrition() >= other.nutrition();
                boolean better = one.growing() < other.growing()
                        || one.yield() > other.yield()
                        || one.nutrition() > other.nutrition();
                if (asGood && better) {
                    bad.add(one.id() + " grows in " + one.growing()
                            + " ticks for " + one.yield() + " at "
                            + one.nutrition() + " nutrition and " + other.id()
                            + " takes " + other.growing() + " for "
                            + other.yield() + " at " + other.nutrition()
                            + "; it is as good on every axis and better on one, "
                            + "which is a reason never to plant " + other.id()
                            + " again");
                }
            }
        }
        return bad;
    }

    /** Every crop that feeds well enough on its own to make cooking pointless. */
    public static List<String> feedingTooWell(List<Crop> crops) {
        List<String> bad = new ArrayList<>();
        for (Crop one : crops) {
            if (one.nutrition() > 1) {
                bad.add(one.id() + " feeds " + one.nutrition() + " eaten raw and "
                        + "an ingredient feeds one; a crop worth eating on its "
                        + "own is a reason never to cook, and cooking is what "
                        + "this half of the module is");
            }
        }
        return bad;
    }

    /** The colony, which is a convenience and not an end to hunger. */
    public static List<String> faultsInColony() {
        List<String> bad = new ArrayList<>();
        if (coloniesPerHour() > MOST_MUSHROOMS_PER_HOUR) {
            bad.add("a colony gives " + coloniesPerHour() + " mushroom(s) an hour "
                    + "and the ceiling is " + MOST_MUSHROOMS_PER_HOUR
                    + "; a block that hands out unlimited mushrooms is a block "
                    + "that ends hunger");
        }
        if (COLONY_MOST <= 0) {
            bad.add("a colony holds nothing");
        }
        if (MUSHROOMS.size() < 2) {
            bad.add("there is one kind of colony, and mushroom stew wants two");
        }
        return bad;
    }

    /**
     * Every ingredient the kitchen asks for that nothing in the world gives.
     *
     * <p>The check this file exists for. Taken as arguments rather than read
     * off the rosters so a control can hand it a kitchen built to be caught —
     * over a healthy pair it can only ever answer no, and a rule that has only
     * ever answered no is a rule nobody has shown can answer yes.
     *
     * @param wanted   what the kitchen asks for
     * @param grown    what this module grows
     * @param made     what the kitchen makes out of other things
     * @param vanilla  what the game already has
     */
    public static List<String> fromNowhere(List<String> wanted,
                                           List<String> grown,
                                           List<String> made,
                                           List<String> vanilla) {
        List<String> bad = new ArrayList<>();
        for (String one : wanted) {
            if (grown.contains(one) || made.contains(one)
                    || vanilla.contains(one)) {
                continue;
            }
            bad.add(one + " is in the kitchen's cupboard and nothing in the "
                    + "world puts it there: it is not grown here, not made out "
                    + "of anything, and not something the game already has");
        }
        return List.copyOf(bad);
    }

    /** What this module grows, as the kitchen names it. */
    public static List<String> grown() {
        List<String> made = new ArrayList<>();
        for (Crop one : CROPS) {
            made.add(one.id());
        }
        return List.copyOf(made);
    }

    /** Every block and item the crops add. */
    public static List<String> everything() {
        List<String> made = new ArrayList<>();
        for (Crop one : CROPS) {
            made.add(one.id());
            made.add(one.seed());
        }
        made.add(COLONY);
        return List.copyOf(made);
    }

    /** Every wild patch there is. */
    public static List<String> patches() {
        List<String> made = new ArrayList<>();
        for (Crop one : CROPS) {
            made.add(one.patch());
        }
        return List.copyOf(made);
    }
}
