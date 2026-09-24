package com.elysium.solarpunk.recipe;

import com.elysium.solarpunk.ElysiumSolarpunk;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The machines that read recipes, and the recipe books they read.
 *
 * <h2>Adding a machine of this shape</h2>
 *
 * One call to {@link #process}. It registers the type, registers a serializer
 * bound to that type, and hands back the {@link SolarpunkProcessRecipe.Kind}
 * the machine's block entity searches with. There is nowhere to register half
 * of it: a type with no serializer loads no recipes and a serializer with no
 * type produces recipes nothing will look for, and both are silent.
 */
public final class SolarpunkRecipes {

    private SolarpunkRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, ElysiumSolarpunk.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, ElysiumSolarpunk.MODID);

    /** Register a recipe book and the serializer that fills it, together. */
    private static SolarpunkProcessRecipe.Kind process(String id) {
        DeferredHolder<RecipeType<?>, RecipeType<SolarpunkProcessRecipe>> type =
                TYPES.register(id, () -> RecipeType.simple(
                        ResourceLocation.fromNamespaceAndPath(
                                ElysiumSolarpunk.MODID, id)));

        // The kind holds suppliers, not values: registration order is not
        // guaranteed and the serializer needs the type only when a recipe is
        // asked what it is, which is long after both exist.
        SolarpunkProcessRecipe.Kind[] box = new SolarpunkProcessRecipe.Kind[1];
        DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SolarpunkProcessRecipe>> serializer =
                SERIALIZERS.register(id, () ->
                        new SolarpunkProcessRecipe.Serializer(box[0]));
        box[0] = new SolarpunkProcessRecipe.Kind(type::get, serializer::get);
        return box[0];
    }

    /** Plant matter and mycelium, pressed into panels and fibre. */
    public static final SolarpunkProcessRecipe.Kind PRESSING = process("pressing");

    /** Cane and fibre, woven into cloth, cord and panels. */
    public static final SolarpunkProcessRecipe.Kind WEAVING = process("weaving");

    /** Seed and stock, grown in water and light. */
    public static final SolarpunkProcessRecipe.Kind FARMING = process("farming");

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
