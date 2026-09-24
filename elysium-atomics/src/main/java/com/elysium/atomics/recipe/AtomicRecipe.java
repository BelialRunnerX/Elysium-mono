package com.elysium.atomics.recipe;

import com.elysium.industry.recipe.PlantRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * One thing turned into another by an Age III machine.
 *
 * <h2>The same shape as Age II's, and deliberately so</h2>
 *
 * A machine on a grid is the same thing in every age: one slot in, one slot
 * out, a job that takes a number of ticks at a full share and proportionally
 * longer at less. That is what {@code PlantRecipe} says, and it is why Age III
 * reuses Age II's machine rather than copying two hundred lines of it — the
 * only things this age adds are its own catalogue and its own name on the
 * block.
 *
 * <p>The difference that matters is not in the code at all. An Age II recipe
 * turns rock into gravel; these dissolve, react and spin, and one of them
 * makes the only thing in the pack that can be put in a reactor.
 *
 * @param input  what goes in
 * @param result what comes out, before the machine's own multiplier
 * @param ticks  how long at a full share
 * @param kind   which machine does it
 */
public record AtomicRecipe(Ingredient input, ItemStack result, int ticks, Kind kind)
        implements PlantRecipe {

    /** The three machines, and what each one's recipes are called. */
    public enum Kind {
        ENRICHING("enriching"),
        REACTING("reacting"),
        CENTRIFUGING("centrifuging");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        public String id() {
            return name;
        }
    }

    @Override
    public boolean matches(SingleRecipeInput given, Level level) {
        return input.test(given.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput given, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.elysium.atomics.registry.AtomicRecipes.serializer(kind);
    }

    @Override
    public RecipeType<?> getType() {
        return com.elysium.atomics.registry.AtomicRecipes.type(kind);
    }

    /** One serializer per kind, all the same but for the kind they write. */
    public static class Serializer implements RecipeSerializer<AtomicRecipe> {

        private final Kind kind;
        private final MapCodec<AtomicRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, AtomicRecipe> stream;

        public Serializer(Kind kind) {
            this.kind = kind;
            this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(AtomicRecipe::input),
                    ItemStack.CODEC.fieldOf("result").forGetter(AtomicRecipe::result),
                    Codec.INT.optionalFieldOf("ticks", 200).forGetter(AtomicRecipe::ticks)
            ).apply(instance, (input, result, ticks) ->
                    new AtomicRecipe(input, result, ticks, kind)));
            this.stream = StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, AtomicRecipe::input,
                    ItemStack.STREAM_CODEC, AtomicRecipe::result,
                    ByteBufCodecs.VAR_INT, AtomicRecipe::ticks,
                    (input, result, ticks) -> new AtomicRecipe(input, result, ticks, kind));
        }

        public Kind kind() {
            return kind;
        }

        @Override
        public MapCodec<AtomicRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AtomicRecipe> streamCodec() {
            return stream;
        }
    }
}
