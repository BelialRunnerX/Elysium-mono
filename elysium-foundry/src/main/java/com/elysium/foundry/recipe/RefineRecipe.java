package com.elysium.foundry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Raw ore in, the metal and its companion out (docs/SPEC_FOUNDRY.md §3).
 *
 * <p>The result is exactly what Age III's enricher gives — three units — and
 * never more; the fourth age does not extend the ore ladder. The byproduct is
 * the recovery: a third of a unit of the companion metal every earlier age
 * threw away, stated once in {@code FoundryRules.RECOVERY_NUGGETS}.
 *
 * <p>The input is a raw form, and only a raw form. That is what keeps recovery
 * from being matter out of nothing: no recipe in the chain produces raw ore,
 * so nothing this makes can be fed back to it.
 *
 * @param input     the raw ore, by tag
 * @param result    the metal, three of it
 * @param byproduct the companion, as nuggets
 * @param ticks     how long at a full share; less share is proportionally longer
 */
public record RefineRecipe(Ingredient input, ItemStack result, ItemStack byproduct, int ticks)
        implements Recipe<RefineRecipe.Input> {

    /** The one slot a refinery reads. */
    public record Input(ItemStack ore) implements RecipeInput {

        @Override
        public ItemStack getItem(int slot) {
            return ore;
        }

        @Override
        public int size() {
            return 1;
        }
    }

    @Override
    public boolean matches(Input given, Level level) {
        return input.test(given.ore());
    }

    @Override
    public ItemStack assemble(Input given, HolderLookup.Provider registries) {
        return result.copy();
    }

    /** What comes out beside the metal. */
    public ItemStack recovered() {
        return byproduct.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.elysium.foundry.registry.FoundryRecipes.REFINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return com.elysium.foundry.registry.FoundryRecipes.REFINING.get();
    }

    public static class Serializer implements RecipeSerializer<RefineRecipe> {

        private static final MapCodec<RefineRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(RefineRecipe::input),
                        ItemStack.CODEC.fieldOf("result").forGetter(RefineRecipe::result),
                        ItemStack.CODEC.fieldOf("byproduct").forGetter(RefineRecipe::byproduct),
                        Codec.INT.optionalFieldOf("ticks", 200).forGetter(RefineRecipe::ticks)
                ).apply(instance, RefineRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, RefineRecipe> STREAM =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, RefineRecipe::input,
                        ItemStack.STREAM_CODEC, RefineRecipe::result,
                        ItemStack.STREAM_CODEC, RefineRecipe::byproduct,
                        ByteBufCodecs.VAR_INT, RefineRecipe::ticks,
                        RefineRecipe::new);

        @Override
        public MapCodec<RefineRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RefineRecipe> streamCodec() {
            return STREAM;
        }
    }
}
