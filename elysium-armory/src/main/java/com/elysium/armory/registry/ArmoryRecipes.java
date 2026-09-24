package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.recipe.ArmoryCompoundPartRecipe;
import com.elysium.armory.recipe.ArmoryGearAssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The recipes that build modular gear and keep it: materials into parts, parts
 * into gear, a vanilla tool into its modular twin, metal into durability, and
 * dye into a colour of the player's own choosing.
 */
public final class ArmoryRecipes {

    private ArmoryRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ElysiumArmory.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ArmoryCompoundPartRecipe>>
            COMPOUND_PART = SERIALIZERS.register("compound_part",
                    () -> new SimpleCraftingRecipeSerializer<>(ArmoryCompoundPartRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ArmoryGearAssemblyRecipe>>
            GEAR_ASSEMBLY = SERIALIZERS.register("gear_assembly",
                    () -> new SimpleCraftingRecipeSerializer<>(ArmoryGearAssemblyRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>,
            SimpleCraftingRecipeSerializer<com.elysium.armory.recipe.ArmoryConversionRecipe>>
            CONVERSION = SERIALIZERS.register("conversion",
                    () -> new SimpleCraftingRecipeSerializer<>(
                            com.elysium.armory.recipe.ArmoryConversionRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>,
            SimpleCraftingRecipeSerializer<com.elysium.armory.recipe.ArmoryRepairRecipe>>
            REPAIR = SERIALIZERS.register("repair",
                    () -> new SimpleCraftingRecipeSerializer<>(
                            com.elysium.armory.recipe.ArmoryRepairRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>,
            SimpleCraftingRecipeSerializer<com.elysium.armory.recipe.ArmoryPaintRecipe>>
            PAINT = SERIALIZERS.register("paint",
                    () -> new SimpleCraftingRecipeSerializer<>(
                            com.elysium.armory.recipe.ArmoryPaintRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>,
            SimpleCraftingRecipeSerializer<com.elysium.armory.recipe.ArmoryPlanBookRecipe>>
            PLAN_BOOK = SERIALIZERS.register("plan_book",
                    () -> new SimpleCraftingRecipeSerializer<>(
                            com.elysium.armory.recipe.ArmoryPlanBookRecipe::new));

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
