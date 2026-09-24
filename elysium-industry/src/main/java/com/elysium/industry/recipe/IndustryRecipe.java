package com.elysium.industry.recipe;

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
 * One thing turned into another by an Age II machine, at a share of full speed.
 *
 * <h2>The same shape as Age I's, minus one field</h2>
 *
 * {@code KineticRecipe} carries a {@code Pace} — the slowest a line may be
 * turning for the recipe to happen at all — because an Age I line's speed
 * moves as you hang things off it, and a machine on a slow line should stop
 * rather than take an hour.
 *
 * <p>A grid does not work that way. It divides: every machine on it gets the
 * same <em>share</em> of full speed, and a starved grid makes everything slow
 * rather than making anything impossible. So there is no floor to state, and
 * the recipe says only how long the job takes when the machine has everything
 * it wants. Half a share is twice as long. That is the whole rule, and it is
 * the reason Age II reads as patient where Age I reads as brittle.
 *
 * <h2>How much comes out</h2>
 *
 * What the file says, and the machine may multiply it — which is how the
 * separator's doubling lives in {@code IndustryRules.SEPARATOR_MULTIPLIER} and
 * nowhere else. A separating recipe that wrote "two ingots" would be the
 * material ladder decided a second time, in data, where no harness can see it.
 *
 * @param input  what goes in
 * @param result what comes out, before the machine's own multiplier
 * @param ticks  how long at full share; less share is proportionally longer
 * @param kind   which machine does it
 */
public record IndustryRecipe(Ingredient input, ItemStack result, int ticks, Kind kind)
        implements PlantRecipe {

    /** The four machines, and what each one's recipes are called. */
    public enum Kind {
        CRUSHING("crushing"),
        ROLLING("rolling"),
        SMELTING("smelting"),
        SEPARATING("separating");

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
        return com.elysium.industry.registry.IndustryRecipes.serializer(kind);
    }

    @Override
    public RecipeType<?> getType() {
        return com.elysium.industry.registry.IndustryRecipes.type(kind);
    }

    /**
     * One serializer per kind, all the same but for the kind they write.
     *
     * The kind is not in the file: it is implied by which recipe type the file
     * declares, so a recipe cannot say it is a rolling recipe while sitting in
     * the crushing type and quietly work in neither.
     */
    public static class Serializer implements RecipeSerializer<IndustryRecipe> {

        private final Kind kind;
        private final MapCodec<IndustryRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, IndustryRecipe> stream;

        public Serializer(Kind kind) {
            this.kind = kind;
            this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(IndustryRecipe::input),
                    ItemStack.CODEC.fieldOf("result").forGetter(IndustryRecipe::result),
                    Codec.INT.optionalFieldOf("ticks", 120).forGetter(IndustryRecipe::ticks)
            ).apply(instance, (input, result, ticks) ->
                    new IndustryRecipe(input, result, ticks, kind)));
            this.stream = StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, IndustryRecipe::input,
                    ItemStack.STREAM_CODEC, IndustryRecipe::result,
                    ByteBufCodecs.VAR_INT, IndustryRecipe::ticks,
                    (input, result, ticks) -> new IndustryRecipe(input, result, ticks, kind));
        }

        public Kind kind() {
            return kind;
        }

        @Override
        public MapCodec<IndustryRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, IndustryRecipe> streamCodec() {
            return stream;
        }
    }
}
