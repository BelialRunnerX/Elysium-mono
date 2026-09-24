package com.elysium.kinetics.recipe;

import com.elysium.kinetics.rules.KineticRules;
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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * One thing turned into another by a machine, at a speed.
 *
 * <h2>One class, four recipe types</h2>
 *
 * A millstone grinds, a press flattens, a saw cuts and a mixer stirs, and the
 * only thing that differs between those four is <em>which machine does it</em>.
 * The shape of the recipe — one thing in, one thing out, a time, a pace — is
 * the same for all of them.
 *
 * <p>So this is one class carrying a {@link Kind}, and four registered types
 * that each hold their kind. A saw recipe is still a saw recipe and will not
 * run in a press, because the recipe manager keys on the type; but there is
 * one codec, one serializer shape and one set of rules about what a recipe may
 * say, instead of four copies that drift.
 *
 * <h2>Why a pace and not an rpm</h2>
 *
 * A line's speed moves. Hang another machine off it and everything on it slows
 * down — that is the whole model. A recipe that said "41.7 rpm" would be one a
 * player could not plan against, because the number it needs changes when they
 * build something at the far end of the hall. "Needs a steady line" is a
 * sentence; 41.7 is not.
 *
 * @param input  what goes in
 * @param result what comes out
 * @param ticks  how long at {@link KineticRules#REFERENCE_RPM}; faster is quicker
 * @param pace   the slowest the line may be turning for this to happen at all
 * @param kind   which machine does it
 */
public record KineticRecipe(Ingredient input, ItemStack result, int ticks,
                            KineticRules.Pace pace, Kind kind)
        implements Recipe<SingleRecipeInput> {

    /** The four machines, and what each one's recipes are called. */
    public enum Kind {
        MILLING("milling"),
        PRESSING("pressing"),
        SAWING("sawing"),
        MIXING("mixing");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        public String id() {
            return name;
        }
    }

    /**
     * The pace, as a word in the file.
     *
     * Written here rather than on the enum because the enum lives in
     * {@code rules/}, which is JDK-only so a harness can execute it — it
     * cannot implement one of the game's interfaces without giving that up.
     */
    public static final Codec<KineticRules.Pace> PACE = Codec.STRING.xmap(
            name -> KineticRules.Pace.valueOf(name.toUpperCase(Locale.ROOT)),
            pace -> pace.name().toLowerCase(Locale.ROOT));

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
        return com.elysium.kinetics.registry.KineticRecipes.serializer(kind);
    }

    @Override
    public RecipeType<?> getType() {
        return com.elysium.kinetics.registry.KineticRecipes.type(kind);
    }

    /** How long this takes on a line turning at this speed. */
    public int ticksAt(float rpm) {
        return KineticRules.ticksFor(rpm, ticks);
    }

    /** Whether a line at this speed is turning fast enough to do this at all. */
    public boolean possibleAt(float rpm) {
        return KineticRules.enough(rpm, pace);
    }

    /**
     * One serializer per kind, all the same but for the kind they write.
     *
     * The kind is not in the file: it is implied by which recipe type the file
     * declares, so a recipe cannot say it is a sawing recipe while sitting in
     * the pressing type and quietly work in neither.
     */
    public static class Serializer implements RecipeSerializer<KineticRecipe> {

        private final Kind kind;
        private final MapCodec<KineticRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, KineticRecipe> stream;

        public Serializer(Kind kind) {
            this.kind = kind;
            this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(KineticRecipe::input),
                    ItemStack.CODEC.fieldOf("result").forGetter(KineticRecipe::result),
                    Codec.INT.optionalFieldOf("ticks", 120).forGetter(KineticRecipe::ticks),
                    PACE.optionalFieldOf("pace", KineticRules.Pace.SLOW)
                            .forGetter(KineticRecipe::pace)
            ).apply(instance, (input, result, ticks, pace) ->
                    new KineticRecipe(input, result, ticks, pace, kind)));
            this.stream = StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, KineticRecipe::input,
                    ItemStack.STREAM_CODEC, KineticRecipe::result,
                    ByteBufCodecs.VAR_INT, KineticRecipe::ticks,
                    ByteBufCodecs.STRING_UTF8.map(
                            name -> KineticRules.Pace.valueOf(name.toUpperCase(Locale.ROOT)),
                            pace -> pace.name().toLowerCase(Locale.ROOT)),
                    KineticRecipe::pace,
                    (input, result, ticks, pace) ->
                            new KineticRecipe(input, result, ticks, pace, kind));
        }

        public Kind kind() {
            return kind;
        }

        @Override
        public MapCodec<KineticRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, KineticRecipe> streamCodec() {
            return stream;
        }
    }
}
