package com.elysium.atomics.registry;

import com.elysium.atomics.ElysiumAtomics;
import com.elysium.atomics.recipe.AtomicRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Three recipe types, one per machine, registered from one table. */
public final class AtomicRecipes {

    private AtomicRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ElysiumAtomics.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ElysiumAtomics.MODID);

    private static final Map<AtomicRecipe.Kind, DeferredHolder<RecipeType<?>, RecipeType<AtomicRecipe>>>
            TYPE_BY_KIND = new EnumMap<>(AtomicRecipe.Kind.class);

    private static final Map<AtomicRecipe.Kind, DeferredHolder<RecipeSerializer<?>, AtomicRecipe.Serializer>>
            SERIALIZER_BY_KIND = new EnumMap<>(AtomicRecipe.Kind.class);

    static {
        for (AtomicRecipe.Kind kind : AtomicRecipe.Kind.values()) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(), () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(ElysiumAtomics.MODID, kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> new AtomicRecipe.Serializer(kind)));
        }
    }

    public static RecipeType<AtomicRecipe> type(AtomicRecipe.Kind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<AtomicRecipe> serializer(AtomicRecipe.Kind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }
}
