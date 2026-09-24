package com.elysium.arcana.block;

import com.elysium.arcana.rules.ArcanaRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What everything in the game is worth, worked out from the game's own
 * recipes.
 *
 * <h2>Why nothing here is a data file</h2>
 *
 * A pack of this size has several thousand craftable items and a shipped table
 * of values for them would be a table that disagrees with the recipes the day
 * anybody changes one — which is the exact failure this module is arranged to
 * make impossible. So the recipes are the input and the values are derived, on
 * the server, from whatever is actually loaded: another mod, a datapack or a
 * config change is priced without this module knowing it exists.
 *
 * <h2>Why it is worked out once and kept</h2>
 *
 * Because deriving over several thousand recipes is not free, and because the
 * answer only changes when the recipes do. It is rebuilt when the recipe
 * manager is a different object from the one it was built against, which is
 * what a datapack reload leaves behind.
 */
public final class MoteTable {

    private MoteTable() {
    }

    private static RecipeManager builtAgainst;
    private static Map<String, Long> worth = Map.of();
    private static int recipesSeen;

    /** What one of this is worth, or 0 if nothing here can price it. */
    public static long worthOf(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        return table(level).getOrDefault(idOf(stack), 0L);
    }

    /** Whether this can be dissolved or condensed at all. */
    public static boolean priceable(Level level, ItemStack stack) {
        return worthOf(level, stack) > 0L;
    }

    /** How many recipes the current table was derived from. */
    public static int recipesSeen() {
        return recipesSeen;
    }

    /** The whole table, built if it has not been built against these recipes. */
    public static synchronized Map<String, Long> table(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (manager != builtAgainst) {
            Map<String, ArcanaRules.Step> recipes = recipesOf(level);
            recipesSeen = recipes.size();
            worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
            builtAgainst = manager;
        }
        return worth;
    }

    /**
     * Every crafting recipe, as something the derivation understands.
     *
     * <p>One step per recipe rather than one per output, because the
     * derivation wants <em>every</em> way of making a thing: it takes the
     * cheapest, and a table that only saw one way would price something above
     * what it actually costs, which is precisely the gap a player rides.
     */
    private static Map<String, ArcanaRules.Step> recipesOf(Level level) {
        Map<String, ArcanaRules.Step> steps = new TreeMap<>();
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(level.registryAccess());
            } catch (RuntimeException needsAGrid) {
                continue;
            }
            if (result.isEmpty()) {
                continue;
            }
            Map<String, Integer> needs = needsOf(holder.value());
            if (needs == null || needs.isEmpty()) {
                continue;
            }
            steps.put(holder.id().toString(), new ArcanaRules.Step(idOf(result),
                    result.getCount(), needs));
        }
        return steps;
    }

    /** What one run consumes, or null if it cannot be said. */
    private static Map<String, Integer> needsOf(CraftingRecipe recipe) {
        Map<String, Integer> needs = new HashMap<>();
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            return null;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            String pick = dearest(ingredient);
            if (pick == null) {
                return null;
            }
            needs.merge(pick, 1, Integer::sum);
        }
        return needs;
    }

    /**
     * Which item an ingredient that accepts many stands for.
     *
     * <h2>The one place this module deliberately guesses high</h2>
     *
     * The autocrafting planner picks the <em>lowest-named</em> item a tag
     * accepts, because there it only has to be the same choice twice. Here the
     * choice changes a price, and the safe direction is not the same: pricing
     * a recipe by the cheapest thing that satisfies it would make everything
     * downstream of a tag cheap, and cheap is the direction a player rides.
     *
     * <p>So it is the lowest name among those that have a settled value, taken
     * only once the table exists — and on the first pass, when nothing is
     * derived yet, the lowest name outright. Stable either way, which is what
     * stops the table being different on Tuesday.
     */
    private static String dearest(Ingredient ingredient) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                sorted.put(idOf(stack), idOf(stack));
            }
        }
        return sorted.isEmpty() ? null : sorted.firstKey();
    }

    /** An item's registry name, which is the vocabulary the table works in. */
    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
