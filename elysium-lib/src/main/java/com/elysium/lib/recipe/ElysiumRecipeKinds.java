package com.elysium.lib.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every kind of recipe a machine in the pack runs, described once so a recipe
 * browser can show it.
 *
 * <p>A module that registers a recipe type hands it here at construction:
 * the type, the machine that runs it (the catalyst, whose name titles the
 * category), how to read a recipe's inputs off it, and any output beyond
 * {@code getResultItem} (a refinery's recovered companion). Nothing here
 * knows about any recipe browser; the library's {@code compat/jei} plugin
 * reads this table when JEI is installed, and when it is not, the table sits
 * unread. That is the whole of the arrangement Dylan asked for on 28 Sep 2026:
 * "support JEI though but leave it as a non dependency" (docs/POLISH.md P6).
 *
 * <p>Inputs are read by a function rather than {@code Recipe.getIngredients},
 * because the game's default for that is an empty list and every machine
 * recipe in the pack is a record with its own field names.
 */
public final class ElysiumRecipeKinds {

    private ElysiumRecipeKinds() {
    }

    /**
     * One kind.
     *
     * @param id       the recipe type's id, which names the category
     * @param type     the recipe type, resolved when asked
     * @param catalyst the machine that runs it; its name titles the category
     * @param inputs   the recipe's inputs, in the order the machine takes them
     * @param extras   outputs beyond the result, or an empty list
     */
    public record Kind(ResourceLocation id, Supplier<RecipeType<?>> type, Supplier<? extends ItemLike> catalyst,
                       Function<Recipe<?>, List<Ingredient>> inputs, Function<Recipe<?>, List<ItemStack>> extras) {
    }

    private static final List<Kind> KINDS = new ArrayList<>();

    /** No extra outputs, for the kinds that make one thing. */
    public static final Function<Recipe<?>, List<ItemStack>> NOTHING_MORE = recipe -> List.of();

    /** Hand a kind over. Called from a module's constructor, once per recipe type it registers. */
    public static synchronized void register(ResourceLocation id, Supplier<RecipeType<?>> type,
                                             Supplier<? extends ItemLike> catalyst,
                                             Function<Recipe<?>, List<Ingredient>> inputs,
                                             Function<Recipe<?>, List<ItemStack>> extras) {
        for (Kind kind : KINDS) {
            if (kind.id().equals(id)) {
                throw new IllegalStateException("the recipe kind " + id + " was handed over twice");
            }
        }
        KINDS.add(new Kind(id, type, catalyst, inputs, extras));
    }

    /** Every kind handed over so far, in the order they arrived. */
    public static synchronized List<Kind> all() {
        return Collections.unmodifiableList(new ArrayList<>(KINDS));
    }
}
