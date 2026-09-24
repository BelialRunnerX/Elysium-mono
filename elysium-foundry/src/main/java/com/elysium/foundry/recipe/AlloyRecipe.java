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

import java.util.List;

/**
 * Two things melted into a third.
 *
 * <h2>Why this is not {@code PlantRecipe}</h2>
 *
 * Every machine in the ages below takes one thing and gives another, and that
 * shape carried three modules. An alloy is the first thing in the pack that
 * genuinely needs two inputs at once — bronze is not copper that has been
 * processed, it is copper <em>and</em> tin — and pretending otherwise would
 * mean a fictional intermediate item for every alloy in the chain.
 *
 * <p>It is still deliberately only two. Three-input recipes are where a
 * material chain stops being something a player can hold and starts being
 * something they look up, and every alloy worth having can be written as a
 * pair.
 *
 * @param first  one of the two, in whatever amount the count says
 * @param second the other
 * @param result what comes out
 * @param ticks  how long at a full share; less share is proportionally longer
 */
public record AlloyRecipe(Ingredient first, int firstCount,
                          Ingredient second, int secondCount,
                          ItemStack result, int ticks)
        implements Recipe<AlloyRecipe.Input> {

    /** The two slots a smelter offers, in the order it reads them. */
    public record Input(ItemStack left, ItemStack right) implements RecipeInput {

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? left : right;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    /**
     * Whether these two, in either order, are what this wants.
     *
     * Either order because a player who put the tin in first has not made a
     * mistake, and a recipe book that required an order would be a recipe book
     * that has to say so on every entry.
     */
    @Override
    public boolean matches(Input given, Level level) {
        return oneWay(given.left(), given.right()) || oneWay(given.right(), given.left());
    }

    private boolean oneWay(ItemStack left, ItemStack right) {
        return first.test(left) && left.getCount() >= firstCount
                && second.test(right) && right.getCount() >= secondCount;
    }

    /** How much of each slot a run consumes, given what is in them. */
    public int takeFromLeft(Input given) {
        return first.test(given.left()) && second.test(given.right())
                ? firstCount : secondCount;
    }

    public int takeFromRight(Input given) {
        return first.test(given.left()) && second.test(given.right())
                ? secondCount : firstCount;
    }

    @Override
    public ItemStack assemble(Input given, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.elysium.foundry.registry.FoundryRecipes.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return com.elysium.foundry.registry.FoundryRecipes.ALLOYING.get();
    }

    /** The one serializer this module has. */
    public static class Serializer implements RecipeSerializer<AlloyRecipe> {

        private static final MapCodec<AlloyRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("first")
                                .forGetter(AlloyRecipe::first),
                        Codec.INT.optionalFieldOf("first_count", 1)
                                .forGetter(AlloyRecipe::firstCount),
                        Ingredient.CODEC_NONEMPTY.fieldOf("second")
                                .forGetter(AlloyRecipe::second),
                        Codec.INT.optionalFieldOf("second_count", 1)
                                .forGetter(AlloyRecipe::secondCount),
                        ItemStack.CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
                        Codec.INT.optionalFieldOf("ticks", 200)
                                .forGetter(AlloyRecipe::ticks)
                ).apply(instance, AlloyRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> STREAM =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, AlloyRecipe::first,
                        ByteBufCodecs.VAR_INT, AlloyRecipe::firstCount,
                        Ingredient.CONTENTS_STREAM_CODEC, AlloyRecipe::second,
                        ByteBufCodecs.VAR_INT, AlloyRecipe::secondCount,
                        ItemStack.STREAM_CODEC, AlloyRecipe::result,
                        ByteBufCodecs.VAR_INT, AlloyRecipe::ticks,
                        AlloyRecipe::new);

        @Override
        public MapCodec<AlloyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() {
            return STREAM;
        }
    }

    /** Every alloy recipe there is, for anything that wants to list them. */
    public static List<AlloyRecipe> none() {
        return List.of();
    }
}
