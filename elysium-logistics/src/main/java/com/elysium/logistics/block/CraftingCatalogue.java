package com.elysium.logistics.block;

import com.elysium.logistics.rules.LogisticRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every crafting recipe in the game, as something the planner understands.
 *
 * <h2>Why there are no pattern items</h2>
 *
 * Every mod this module replaces makes you encode a pattern per recipe, on an
 * item, at a machine, before the network will make anything. That is a tax
 * dressed as a mechanic: nobody has ever enjoyed encoding forty patterns, and
 * the only thing it protects against is a planner that cannot handle a large
 * recipe set. Ours can — it refuses cycles by name, caps its depth, caps its
 * steps and has a budget — so the tax is not charged.
 *
 * <p>What is charged instead is machinery. A core is an Age III load, every
 * assembler is another, and a recipe is only usable if the network actually
 * holds the ingredients. That is a better gate than paperwork, because a
 * player can see it.
 *
 * <h2>Why the choice between competing recipes is made here and made the same
 * way twice</h2>
 *
 * A dozen recipes make a torch and the planner takes one step per output, so
 * something has to choose. It chooses the recipe with the fewest different
 * ingredients, and breaks ties by name — never by whichever the recipe manager
 * happened to list first. A factory that made a torch out of coal on Monday
 * and charcoal on Tuesday, with nothing changed, is one a player cannot plan
 * around.
 */
public final class CraftingCatalogue {

    private CraftingCatalogue() {
    }

    /**
     * The whole of the game's crafting, as steps.
     *
     * <p>Built from what the server has loaded rather than from anything we
     * ship, so a recipe added by another mod, a datapack or a config is in it
     * without this module knowing that mod exists.
     */
    public static Map<String, LogisticRules.Step> of(Level level) {
        Map<String, LogisticRules.Step> best = new HashMap<>();
        Map<String, String> chosen = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(level.registryAccess());
            } catch (RuntimeException broken) {
                // A recipe whose result cannot be resolved without a crafting
                // grid in front of it. There is nothing to be done with it and
                // nothing to be gained from stopping over it.
                continue;
            }
            if (result.isEmpty()) {
                continue;
            }
            String output = idOf(result);
            Map<String, Integer> needs = needsOf(holder.value());
            if (needs == null || needs.isEmpty() || needs.containsKey(output)) {
                // A recipe that consumes what it makes is a cycle of length
                // one. The planner would refuse a plan that met it; leaving it
                // out means the plan is made out of some other recipe instead.
                continue;
            }
            String name = holder.id().toString();
            LogisticRules.Step already = best.get(output);
            if (already == null || better(needs, name, already.needs(), chosen.get(output))) {
                best.put(output, new LogisticRules.Step(output, result.getCount(), needs));
                chosen.put(output, name);
            }
        }
        return Map.copyOf(best);
    }

    /**
     * Whether the first of two recipes for the same thing is the one to keep.
     *
     * Fewest different ingredients, then by name. Both halves matter: the
     * first is a guess at which recipe a player would rather the factory used,
     * and the second is what makes the guess the same guess every time.
     */
    private static boolean better(Map<String, Integer> needs, String name,
                                  Map<String, Integer> against, String againstName) {
        if (needs.size() != against.size()) {
            return needs.size() < against.size();
        }
        return againstName == null || name.compareTo(againstName) < 0;
    }

    /**
     * What one run of a recipe consumes, or null if it cannot be said.
     *
     * <p>An ingredient that accepts a tag accepts many items, and the planner
     * works in single names, so one of them has to be picked: the first by id,
     * always, for the same reason the choice of recipe is. Returning null for
     * an ingredient that accepts nothing at all is deliberate — a recipe with
     * an unsatisfiable slot is one no plan should be built on.
     */
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
            String pick = representative(ingredient);
            if (pick == null) {
                return null;
            }
            needs.merge(pick, 1, Integer::sum);
        }
        return needs;
    }

    /** The lowest-named item an ingredient will accept. */
    private static String representative(Ingredient ingredient) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                sorted.put(idOf(stack), idOf(stack));
            }
        }
        return sorted.isEmpty() ? null : sorted.firstKey();
    }

    /** An item's registry name, which is the vocabulary the planner works in. */
    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
