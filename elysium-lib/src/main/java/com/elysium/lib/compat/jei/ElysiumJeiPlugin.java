package com.elysium.lib.compat.jei;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.recipe.ElysiumRecipeKinds;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The pack's machine recipes in JEI, when JEI is installed.
 *
 * <p>This class is the one place in the pack that names JEI. Nothing imports
 * it: JEI finds it through the {@link JeiPlugin} annotation and loads it
 * itself, so without JEI the class is bytes in the jar that are never read,
 * and the manifest declares JEI optional. Every kind comes from
 * {@link ElysiumRecipeKinds}, which the machine modules fill without knowing
 * this class exists (docs/POLISH.md P6, 28 Sep 2026).
 */
@JeiPlugin
public class ElysiumJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "machines");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /** One JEI type per kind, keyed by the kind's id, so the three registrations agree. */
    static RecipeType<RecipeHolder<Recipe<?>>> jeiType(ElysiumRecipeKinds.Kind kind) {
        return RecipeType.createRecipeHolderType(kind.id());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (ElysiumRecipeKinds.Kind kind : ElysiumRecipeKinds.all()) {
            registration.addRecipeCategories(
                    new ElysiumRecipeCategory(kind, registration.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RecipeManager manager = minecraft.level.getRecipeManager();
        for (ElysiumRecipeKinds.Kind kind : ElysiumRecipeKinds.all()) {
            registration.addRecipes(jeiType(kind), recipesOf(manager, kind.type().get()));
        }
    }

    /** Every recipe of one type, with the type's own bounds satisfied and then forgotten. */
    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<Recipe<?>>> recipesOf(
            RecipeManager manager, net.minecraft.world.item.crafting.RecipeType<?> raw) {
        net.minecraft.world.item.crafting.RecipeType<T> type = (net.minecraft.world.item.crafting.RecipeType<T>) raw;
        List<RecipeHolder<Recipe<?>>> out = new ArrayList<>();
        for (RecipeHolder<T> holder : manager.getAllRecipesFor(type)) {
            out.add((RecipeHolder<Recipe<?>>) (RecipeHolder<?>) holder);
        }
        return out;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (ElysiumRecipeKinds.Kind kind : ElysiumRecipeKinds.all()) {
            registration.addRecipeCatalysts(jeiType(kind), kind.catalyst().get());
        }
    }
}
