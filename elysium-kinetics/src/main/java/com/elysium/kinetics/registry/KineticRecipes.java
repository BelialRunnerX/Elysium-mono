package com.elysium.kinetics.registry;

import com.elysium.kinetics.ElysiumKinetics;
import com.elysium.kinetics.recipe.KineticRecipe;
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
 * Written as a loop over the kinds rather than four copies, so a fifth machine
 * is a line in an enum and nothing else. Four hand-written blocks is exactly
 * how the fifth one ends up missing its serializer.
 */
public final class KineticRecipes {

    private KineticRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ElysiumKinetics.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ElysiumKinetics.MODID);

    private static final Map<KineticRecipe.Kind, DeferredHolder<RecipeType<?>, RecipeType<KineticRecipe>>>
            TYPE_BY_KIND = new EnumMap<>(KineticRecipe.Kind.class);

    private static final Map<KineticRecipe.Kind, DeferredHolder<RecipeSerializer<?>, KineticRecipe.Serializer>>
            SERIALIZER_BY_KIND = new EnumMap<>(KineticRecipe.Kind.class);

    static {
        for (KineticRecipe.Kind kind : KineticRecipe.Kind.values()) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(), () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(ElysiumKinetics.MODID, kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> new KineticRecipe.Serializer(kind)));
        }
    }

    public static RecipeType<KineticRecipe> type(KineticRecipe.Kind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<KineticRecipe> serializer(KineticRecipe.Kind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }
}
