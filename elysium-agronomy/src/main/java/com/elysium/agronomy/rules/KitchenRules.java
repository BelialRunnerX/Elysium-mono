package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A kitchen: what it turns into what, and why any of it is worth making.
 *
 * <h2>The question a cooking mod has to answer</h2>
 *
 * Not "how many recipes are there" — that is the easy half and the half that
 * gets counted. The question is whether a player ever makes more than three of
 * them. A cooking system with twenty-nine meals of which six are worth cooking
 * is a system with six meals and twenty-three entries in a recipe book.
 *
 * <p>So the rule here is the one the weapons carry: <b>nothing is dominated</b>.
 * No meal may be at least as good as another in every way and better in one —
 * counting what it feeds, how long it lasts, how long it takes, how many it
 * serves and how many things go in it. Where two meals would otherwise be
 * interchangeable they carry different effects, and a meal with an effect
 * nothing else gives can never be dominated at all. That is what makes the
 * effects a design device rather than decoration.
 *
 * <h2>And the one about free food</h2>
 *
 * Cooking must be worth doing and must not be worth doing infinitely: a meal
 * is worth more than half its ingredients eaten raw and less than three times
 * them plus a little. Without the first nobody cooks; without the second a
 * cooking pot is a food multiplier and hunger stops existing.
 */
public final class KitchenRules {

    private KitchenRules() {
    }

    // ==================================================================
    // Where cooking happens
    // ==================================================================

    /** A pot over a fire: slow, several servings, the soups and stews. */
    public static final String POT = "pot";

    /** A pan: fast, one serving, the fried things. */
    public static final String SKILLET = "skillet";

    /** A board and a knife: no fire at all, the sandwiches. */
    public static final String BOARD = "board";

    /** An oven: slowest, the baked things. */
    public static final String OVEN = "oven";

    /** Every station there is. */
    public static final List<String> STATIONS = List.of(POT, SKILLET, BOARD, OVEN);

    // ==================================================================
    // What goes in and what comes out
    // ==================================================================

    /** Something this module makes that is not itself a meal. */
    public record Ingredient(String id, int nutrition, float saturation) {

        public Ingredient {
            Objects.requireNonNull(id, "an ingredient with no name");
            if (nutrition <= 0) {
                throw new IllegalArgumentException(id + " feeds nobody");
            }
            if (saturation <= 0.0F) {
                throw new IllegalArgumentException(id + " has no saturation");
            }
        }
    }

    /**
     * One meal.
     *
     * @param id         what it is called
     * @param of         what goes into it
     * @param nutrition  what eating it is worth
     * @param saturation how long that lasts
     * @param ticks      how long it takes to make
     * @param servings   how many it makes at once
     * @param station    where it is made
     * @param effect     what it does besides feeding you, or empty
     */
    public record Meal(String id, List<String> of, int nutrition, float saturation,
                       int ticks, int servings, String station, String effect) {

        public Meal {
            Objects.requireNonNull(id, "a meal with no name");
            of = List.copyOf(Objects.requireNonNull(of, "a meal made of nothing"));
            Objects.requireNonNull(effect, "null effect");
            if (!STATIONS.contains(station)) {
                throw new IllegalArgumentException("no such station: " + station);
            }
            if (of.isEmpty()) {
                throw new IllegalArgumentException(id + " is made of nothing");
            }
            if (nutrition <= 0) {
                throw new IllegalArgumentException(id + " feeds nobody");
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException(id + " cooks instantly, which is "
                        + "a crafting recipe rather than a meal");
            }
            if (servings <= 0) {
                throw new IllegalArgumentException(id + " serves nobody");
            }
        }

        /**
         * Whether this meal makes another one pointless.
         *
         * <p>Only within a station and an effect: a meal with an effect nothing
         * else gives is never pointless whatever its numbers, which is the
         * whole reason the effects are here.
         */
        public boolean dominates(Meal other) {
            if (!station.equals(other.station()) || !effect.equals(other.effect())) {
                return false;
            }
            boolean asGood = nutrition >= other.nutrition()
                    && saturation >= other.saturation()
                    && ticks <= other.ticks()
                    && servings >= other.servings()
                    && of.size() <= other.of().size();
            boolean better = nutrition > other.nutrition()
                    || saturation > other.saturation()
                    || ticks < other.ticks()
                    || servings > other.servings()
                    || of.size() < other.of().size();
            return asGood && better;
        }
    }

    /** Something a knife turns into something else. */
    public record Cut(String input, String tool, Map<String, Integer> yields) {

        public Cut {
            Objects.requireNonNull(input, "cutting nothing");
            Objects.requireNonNull(tool, "cutting with nothing");
            yields = Map.copyOf(Objects.requireNonNull(yields, "yielding nothing"));
            if (yields.isEmpty()) {
                throw new IllegalArgumentException("cutting " + input
                        + " yields nothing, so it is a way of destroying it");
            }
        }
    }

    /** Everything this module makes that is not itself a meal. */
    public static final List<Ingredient> INGREDIENTS = List.of(
            new Ingredient("cabbage", 2, 0.4F),
            new Ingredient("onion", 2, 0.4F),
            new Ingredient("tomato", 2, 0.4F),
            new Ingredient("rice", 1, 0.3F),
            new Ingredient("barley", 1, 0.3F),
            new Ingredient("chili", 1, 0.2F),
            new Ingredient("garlic", 1, 0.2F),
            new Ingredient("ginger", 1, 0.2F),
            new Ingredient("cabbage_leaf", 1, 0.2F),
            new Ingredient("tomato_paste", 2, 0.3F),
            new Ingredient("minced_beef", 3, 0.6F),
            new Ingredient("minced_pork", 3, 0.6F),
            new Ingredient("minced_mutton", 3, 0.6F),
            new Ingredient("minced_chicken", 2, 0.5F),
            new Ingredient("fish_fillet", 3, 0.6F),
            new Ingredient("bacon", 3, 0.7F),
            new Ingredient("ham", 3, 0.7F),
            new Ingredient("dough", 2, 0.3F),
            new Ingredient("pasta", 2, 0.4F),
            new Ingredient("stock", 1, 0.4F),
            new Ingredient("cheese_wheel", 4, 0.8F),
            new Ingredient("butter", 2, 0.5F),
            new Ingredient("cream", 2, 0.5F),
            new Ingredient("pie_crust", 3, 0.5F));

    /** Every meal there is. */
    public static final List<Meal> MEALS = List.of(
            new Meal("vegetable_soup", List.of("cabbage", "onion", "tomato", "stock"),
                    8, 0.7F, 400, 4, POT, "comfort"),
            new Meal("beef_stew", List.of("minced_beef", "onion", "stock", "carrot"),
                    10, 0.8F, 600, 4, POT, "nourishment"),
            new Meal("chicken_broth", List.of("minced_chicken", "stock", "garlic"),
                    7, 0.7F, 400, 4, POT, "regeneration"),
            new Meal("fish_chowder", List.of("fish_fillet", "cream", "onion", "stock"),
                    9, 0.8F, 500, 4, POT, "water_breathing"),
            new Meal("pasta_with_tomato", List.of("pasta", "tomato", "garlic", "butter"),
                    9, 0.7F, 450, 4, POT, "haste"),
            new Meal("rice_bowl", List.of("rice", "cabbage", "onion"),
                    7, 0.6F, 380, 4, POT, "speed"),
            new Meal("noodle_soup", List.of("pasta", "stock", "minced_pork", "ginger"),
                    10, 0.8F, 600, 4, POT, "comfort"),
            new Meal("mushroom_stew", List.of("brown_mushroom", "red_mushroom", "cream", "stock"),
                    8, 0.7F, 420, 4, POT, "resistance"),
            new Meal("pumpkin_soup", List.of("pumpkin", "cream", "stock", "butter"),
                    8, 0.7F, 420, 4, POT, "night_vision"),
            new Meal("bone_broth", List.of("bone", "stock", "onion"),
                    6, 0.9F, 360, 4, POT, "absorption"),
            new Meal("chili_bowl", List.of("minced_beef", "chili", "tomato", "onion"),
                    10, 0.7F, 620, 4, POT, "strength"),
            new Meal("curry", List.of("rice", "ginger", "garlic", "chili", "stock"),
                    11, 0.8F, 700, 4, POT, "fire_resistance"),
            new Meal("barley_porridge", List.of("barley", "cream", "butter"),
                    7, 0.9F, 340, 4, POT, "nourishment"),
            new Meal("bacon_and_eggs", List.of("bacon", "egg"),
                    8, 0.8F, 240, 1, SKILLET, "strength"),
            new Meal("steak_and_potatoes", List.of("minced_beef", "potato", "butter"),
                    11, 0.9F, 300, 1, SKILLET, "resistance"),
            new Meal("fried_rice", List.of("rice", "egg", "onion", "cabbage"),
                    9, 0.7F, 280, 1, SKILLET, "haste"),
            new Meal("grilled_fish", List.of("fish_fillet", "butter", "garlic"),
                    9, 0.8F, 260, 1, SKILLET, "water_breathing"),
            new Meal("ham_sandwich", List.of("bread", "ham", "cabbage_leaf"),
                    9, 0.8F, 200, 1, BOARD, "speed"),
            new Meal("bacon_sandwich", List.of("bread", "bacon", "tomato_paste"),
                    9, 0.8F, 200, 1, BOARD, "strength"),
            new Meal("cheese_sandwich", List.of("bread", "cheese_wheel", "butter"),
                    8, 0.8F, 200, 1, BOARD, "comfort"),
            new Meal("cabbage_rolls", List.of("cabbage_leaf", "rice", "minced_pork"),
                    7, 0.7F, 180, 1, BOARD, "nourishment"),
            new Meal("apple_pie", List.of("pie_crust", "apple", "sugar"),
                    10, 0.6F, 500, 4, OVEN, "nourishment"),
            new Meal("sweet_berry_pie", List.of("pie_crust", "sweet_berries", "sugar"),
                    10, 0.6F, 500, 4, OVEN, "speed"),
            new Meal("chocolate_pie", List.of("pie_crust", "cocoa_beans", "cream"),
                    11, 0.6F, 540, 4, OVEN, "haste"),
            new Meal("shepherds_pie", List.of("pie_crust", "minced_mutton", "potato", "butter"),
                    12, 0.9F, 640, 4, OVEN, "resistance"),
            new Meal("bread_loaf", List.of("dough"),
                    6, 0.6F, 300, 2, OVEN, ""),
            new Meal("honey_cookie", List.of("dough", "honey_bottle"),
                    5, 0.4F, 200, 4, OVEN, "regeneration"),
            new Meal("roast_chicken", List.of("minced_chicken", "onion", "butter"),
                    12, 0.9F, 700, 3, OVEN, "strength"),
            new Meal("stuffed_pumpkin", List.of("pumpkin", "rice", "onion", "minced_beef"),
                    13, 0.9F, 800, 4, OVEN, "comfort"));

    /** Everything a knife turns into something else. */
    public static final List<Cut> CUTS = List.of(
            new Cut("beef", "knife", Map.of("minced_beef", 2)),
            new Cut("porkchop", "knife", Map.of("minced_pork", 2)),
            new Cut("mutton", "knife", Map.of("minced_mutton", 2)),
            new Cut("chicken", "knife", Map.of("minced_chicken", 2)),
            new Cut("cod", "knife", Map.of("fish_fillet", 2)),
            new Cut("salmon", "knife", Map.of("fish_fillet", 2)),
            new Cut("cabbage", "knife", Map.of("cabbage_leaf", 3)),
            new Cut("tomato", "knife", Map.of("tomato_paste", 2)),
            new Cut("wheat", "knife", Map.of("dough", 1)),
            new Cut("milk_bucket", "knife", Map.of("cream", 2, "butter", 1)),
            // A bale is bound with twine, so a knife is exactly the tool, and
            // what falls out of it is the two grains this module grows.
            // Nine wheat went into it and three of ours come out, so it is a
            // way of turning a harvest into a different harvest rather than a
            // way of getting more of one.
            new Cut("hay_block", "knife", Map.of("barley", 2, "rice", 1)));

    /**
     * Things the game already has that a recipe here may name.
     *
     * <p>Every one of these is checked against the game's own item list by
     * {@code validate.py} rather than taken on trust. It was taken on trust
     * for a while, and three of the names in it — a barley sheaf, a bread
     * slice and a pumpkin slice — were items from a mod this pack does not
     * have. The three recipes naming them could never fire, and nothing said
     * so, because an allowlist that is never checked is a list of whatever
     * somebody believed on the day they wrote it.
     */
    public static final Set<String> THE_GAMES_OWN = Set.of(
            "apple", "beef", "bone",
            "bread", "brown_mushroom", "carrot",
            "chicken", "cocoa_beans", "cod", "egg",
            "hay_block", "honey_bottle", "milk_bucket", "mutton", "porkchop",
            "potato", "pumpkin", "red_mushroom",
            "salmon", "sugar", "sweet_berries", "wheat");

    private static final Map<String, Ingredient> INGREDIENT_BY_ID =
            new LinkedHashMap<>();
    private static final Map<String, Meal> MEAL_BY_ID = new LinkedHashMap<>();

    static {
        for (Ingredient one : INGREDIENTS) {
            INGREDIENT_BY_ID.put(one.id(), one);
        }
        for (Meal meal : MEALS) {
            MEAL_BY_ID.put(meal.id(), meal);
        }
    }

    /** The ingredient of that name, or null. */
    public static Ingredient ingredient(String id) {
        return INGREDIENT_BY_ID.get(id);
    }

    /** The meal of that name, or null. */
    public static Meal meal(String id) {
        return MEAL_BY_ID.get(id);
    }

    /** Every meal made at one station. */
    public static List<Meal> at(String station) {
        List<Meal> found = new ArrayList<>();
        for (Meal meal : MEALS) {
            if (meal.station().equals(station)) {
                found.add(meal);
            }
        }
        return List.copyOf(found);
    }

    /** Every effect any meal gives. */
    public static List<String> effects() {
        Set<String> seen = new TreeSet<>();
        for (Meal meal : MEALS) {
            if (!meal.effect().isEmpty()) {
                seen.add(meal.effect());
            }
        }
        return List.copyOf(seen);
    }

    // ==================================================================
    // Whether any of it is worth making
    // ==================================================================

    /** Every meal another meal makes pointless. */
    public static List<String> dominated() {
        List<String> bad = new ArrayList<>();
        for (Meal better : MEALS) {
            for (Meal worse : MEALS) {
                if (!better.id().equals(worse.id()) && better.dominates(worse)) {
                    bad.add(worse.id() + " is made pointless by " + better.id()
                            + " — same station, same effect, and worse or equal in "
                            + "every way");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every meal that names something nothing makes. */
    public static List<String> impossible() {
        List<String> bad = new ArrayList<>();
        for (Meal meal : MEALS) {
            for (String part : meal.of()) {
                if (!INGREDIENT_BY_ID.containsKey(part)
                        && !THE_GAMES_OWN.contains(part)) {
                    bad.add(meal.id() + " wants " + part + ", which neither this "
                            + "module nor the game makes");
                }
            }
        }
        for (Cut cut : CUTS) {
            if (!INGREDIENT_BY_ID.containsKey(cut.input())
                    && !THE_GAMES_OWN.contains(cut.input())) {
                bad.add("cutting " + cut.input() + ", which nothing makes");
            }
        }
        return List.copyOf(bad);
    }

    /** How little of its parts a meal may be worth before cooking is pointless. */
    public static final float LEAST_OF_ITS_PARTS = 0.5F;

    /** How much more than its parts a meal may be worth before food is free. */
    public static final float MOST_OF_ITS_PARTS = 3.0F;

    /** What a meal's ingredients are worth eaten raw. */
    public static int rawWorth(Meal meal) {
        int total = 0;
        for (String part : meal.of()) {
            Ingredient one = INGREDIENT_BY_ID.get(part);
            total += one != null ? one.nutrition() : 2;
        }
        return total;
    }

    /** Every meal that is not worth cooking, or worth far too much. */
    public static List<String> badValue() {
        List<String> bad = new ArrayList<>();
        for (Meal meal : MEALS) {
            int raw = rawWorth(meal);
            if (meal.nutrition() <= raw * LEAST_OF_ITS_PARTS) {
                bad.add(meal.id() + " feeds a player less than half what its parts do "
                        + "raw, so nobody cooks it");
            }
            if (meal.nutrition() > raw * MOST_OF_ITS_PARTS + 6) {
                bad.add(meal.id() + " feeds a player far more than what went into it, "
                        + "so a pot is a food multiplier and hunger stops existing");
            }
        }
        return List.copyOf(bad);
    }

    /** Every ingredient nothing ever uses. */
    public static List<String> unused() {
        Set<String> wanted = new TreeSet<>();
        for (Meal meal : MEALS) {
            wanted.addAll(meal.of());
        }
        for (Cut cut : CUTS) {
            wanted.addAll(cut.yields().keySet());
        }
        List<String> spare = new ArrayList<>();
        for (Ingredient one : INGREDIENTS) {
            if (!wanted.contains(one.id())) {
                spare.add(one.id() + " is made and nothing uses it");
            }
        }
        return List.copyOf(spare);
    }

    /** Every station with too little on it to be worth building. */
    public static List<String> emptyStations() {
        List<String> bad = new ArrayList<>();
        for (String station : STATIONS) {
            int count = at(station).size();
            if (count < 2) {
                bad.add(station + " has " + count + " meal(s) on it, so it is a block "
                        + "you build once and never use");
            }
        }
        return List.copyOf(bad);
    }

    /** Every cut that gives back more food than it consumed. */
    public static List<String> cutsThatCreate() {
        List<String> bad = new ArrayList<>();
        for (Cut cut : CUTS) {
            Ingredient from = INGREDIENT_BY_ID.get(cut.input());
            int before = from != null ? from.nutrition() : 4;
            int after = 0;
            for (Map.Entry<String, Integer> made : cut.yields().entrySet()) {
                Ingredient one = INGREDIENT_BY_ID.get(made.getKey());
                after += (one != null ? one.nutrition() : 1) * made.getValue();
            }
            if (after > before * 2) {
                bad.add("cutting " + cut.input() + " more than doubles what it feeds, "
                        + "so a knife is a food multiplier");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every pair of meals that are the same meal under two names.
     *
     * <p>Domination does not catch this: two meals identical in every number
     * neither dominates nor is dominated, because "better in at least one way"
     * is false both ways round. A tie is as bad as a domination — one of the
     * two is a recipe nobody has a reason to pick — and it took a negative
     * test to notice the check had that hole.
     */
    public static List<String> indistinguishable() {
        List<String> bad = new ArrayList<>();
        for (int first = 0; first < MEALS.size(); first++) {
            for (int second = first + 1; second < MEALS.size(); second++) {
                Meal one = MEALS.get(first);
                Meal other = MEALS.get(second);
                if (one.station().equals(other.station())
                        && one.effect().equals(other.effect())
                        && one.nutrition() == other.nutrition()
                        && one.saturation() == other.saturation()
                        && one.ticks() == other.ticks()
                        && one.servings() == other.servings()
                        && one.of().size() == other.of().size()) {
                    bad.add(one.id() + " and " + other.id() + " are the same meal "
                            + "under two names — same station, same effect, same "
                            + "numbers — so one of them is a recipe nobody has a "
                            + "reason to pick");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every meal sharing a name with another. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Meal meal : MEALS) {
            if (!seen.add(meal.id())) {
                twice.add(meal.id());
            }
        }
        return List.copyOf(twice);
    }
}
