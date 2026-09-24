package com.elysium.ores.unify;

import com.elysium.ores.ElysiumOres;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The way out of holding the wrong lead.
 *
 * <h2>Added, never replaced</h2>
 *
 * One shapeless recipe per variant: one of that variant in, one of the
 * canonical out. The existing recipes are copied across untouched and ours are
 * appended, so nothing another mod shipped is edited, reordered or removed —
 * which is the promise the module makes and the reason it can be taken out of
 * a pack again without breaking the world it was in.
 *
 * <p>Injected rather than shipped as data files for the same reason the drops
 * are resolved at runtime: the list depends on which mods are installed, and a
 * recipe file naming an absent item is a datapack error rather than an absence.
 *
 * <h2>Why one-for-one and one direction</h2>
 *
 * One-for-one because a unification that cost the player material is a
 * unification they will not use, and a tax on tidying up is a strange thing to
 * charge. One direction because the canonical is in every family tag already:
 * a recipe that wanted the variant will accept the canonical, so there is
 * never a reason to convert back, and offering it would let a player loop two
 * recipes forever for no purpose.
 */
public final class UnifyRecipes {

    private UnifyRecipes() {
    }

    /** What the injected recipe ids are prefixed with, so they can be recognised. */
    public static final String PREFIX = "unify/";

    private static int injected;

    /** How many conversions were added at the last injection. */
    public static int injected() {
        return injected;
    }

    /**
     * Copies every existing recipe across and appends one conversion per
     * variant.
     *
     * Called after tags settle, which is after recipes have loaded, so the
     * collection this reads is the finished one.
     */
    public static void inject(RecipeManager recipes) {
        List<RecipeHolder<?>> all = new ArrayList<>();
        for (RecipeHolder<?> existing : recipes.getRecipes()) {
            if (!existing.id().getNamespace().equals(ElysiumOres.MODID)
                    || !existing.id().getPath().startsWith(PREFIX)) {
                all.add(existing);
            }
            // Ours from a previous injection are dropped rather than kept, so
            // reloading twice does not accumulate two copies of every recipe.
        }

        int added = 0;
        for (Map.Entry<net.minecraft.world.item.Item, net.minecraft.world.item.Item> pair
                : OreUnification.all().entrySet()) {
            ResourceLocation from = BuiltInRegistries.ITEM.getKey(pair.getKey());
            ResourceLocation to = BuiltInRegistries.ITEM.getKey(pair.getValue());
            if (from == null || to == null) {
                continue;
            }
            NonNullList<Ingredient> input = NonNullList.create();
            input.add(Ingredient.of(pair.getKey()));
            ShapelessRecipe recipe = new ShapelessRecipe(
                    "", CraftingBookCategory.MISC,
                    new ItemStack(pair.getValue()), input);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    ElysiumOres.MODID,
                    PREFIX + from.getNamespace() + "/" + from.getPath());
            all.add(new RecipeHolder<>(id, recipe));
            added++;
        }

        recipes.replaceRecipes(all);
        injected = added;
        ElysiumOres.LOGGER.info("unified {} families; {} conversion recipes, {} items "
                        + "left alone as ambiguous",
                OreUnification.families(), added, OreUnification.protectedCount());
    }
}
