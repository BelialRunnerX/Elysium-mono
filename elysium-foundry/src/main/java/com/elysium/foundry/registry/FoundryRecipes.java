package com.elysium.foundry.registry;

import com.elysium.foundry.ElysiumFoundry;
import com.elysium.foundry.recipe.AlloyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Two recipe types, one per machine: alloying and refining. */
public final class FoundryRecipes {

    private FoundryRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ElysiumFoundry.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ElysiumFoundry.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyRecipe>> ALLOYING =
            TYPES.register("alloying", () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(ElysiumFoundry.MODID,
                            "alloying")));

    public static final DeferredHolder<RecipeSerializer<?>, AlloyRecipe.Serializer>
            SERIALIZER = SERIALIZERS.register("alloying", AlloyRecipe.Serializer::new);

    /** Raw ore to the metal and its companion: the refinery's (docs/SPEC_FOUNDRY.md §3). */
    public static final DeferredHolder<RecipeType<?>, RecipeType<com.elysium.foundry.recipe.RefineRecipe>> REFINING =
            TYPES.register("refining", () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(ElysiumFoundry.MODID, "refining")));

    public static final DeferredHolder<RecipeSerializer<?>, com.elysium.foundry.recipe.RefineRecipe.Serializer>
            REFINE_SERIALIZER = SERIALIZERS.register("refining",
                    com.elysium.foundry.recipe.RefineRecipe.Serializer::new);
}
