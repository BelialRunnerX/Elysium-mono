package com.elysium.industry.registry;

import com.elysium.industry.ElysiumIndustry;
import com.elysium.industry.recipe.IndustryRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Four recipe types, one per machine, registered from one table.
 *
 * Written as a loop over the kinds rather than four copies, for the reason
 * Age I's is: four hand-written blocks is exactly how the fifth one ends up
 * missing its serializer.
 */
public final class IndustryRecipes {

    private IndustryRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ElysiumIndustry.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ElysiumIndustry.MODID);

    private static final Map<IndustryRecipe.Kind, DeferredHolder<RecipeType<?>, RecipeType<IndustryRecipe>>>
            TYPE_BY_KIND = new EnumMap<>(IndustryRecipe.Kind.class);

    private static final Map<IndustryRecipe.Kind, DeferredHolder<RecipeSerializer<?>, IndustryRecipe.Serializer>>
            SERIALIZER_BY_KIND = new EnumMap<>(IndustryRecipe.Kind.class);

    static {
        for (IndustryRecipe.Kind kind : IndustryRecipe.Kind.values()) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(), () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(ElysiumIndustry.MODID, kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> new IndustryRecipe.Serializer(kind)));
        }
    }

    public static RecipeType<IndustryRecipe> type(IndustryRecipe.Kind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<IndustryRecipe> serializer(IndustryRecipe.Kind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }
}
