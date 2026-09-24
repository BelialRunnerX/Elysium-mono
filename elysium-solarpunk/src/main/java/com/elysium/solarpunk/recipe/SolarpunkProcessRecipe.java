package com.elysium.solarpunk.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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

import java.util.function.Supplier;

/**
 * One input, one output, a time and a power cost.
 *
 * <h2>Why one class serves several machines</h2>
 *
 * The press and the loom do the same shape of work: consume some of one item,
 * spend power for a while, produce some of another. Everything that differs
 * between them lives in the recipe files, not in the code. So this is one class
 * registered under two {@link RecipeType}s, and a third machine of this shape
 * costs a registration and a folder of JSON rather than a second serializer, a
 * second codec and a second set of bugs in both.
 *
 * <h2>Why they still get separate types</h2>
 *
 * Because a recipe type is what a machine searches. Sharing one type would let
 * the loom weave anything the press can press, and the two blocks would be
 * interchangeable — which is the same as having one block. Separate types, one
 * implementation: the machines stay distinct to a player and identical to a
 * maintainer, which is the right way round.
 *
 * <h2>Why this is a class and not a record</h2>
 *
 * It was a record first. The awkward part is {@link Kind} — which machine a
 * recipe belongs to — because that is not written in the recipe file; it is
 * decided by which serializer read it. A record's components are its
 * serialized shape, so carrying the kind meant an external map keyed on the
 * recipe, which leaked every recipe object across every reload and had to use
 * identity comparison because two machines may legitimately ship the same
 * conversion. A plain field costs one line and none of that.
 *
 * <h2>What is deliberately not here</h2>
 *
 * No multi-slot input, no catalyst, no chance output, no by-products. Each is
 * easy to add later to a working thing and hard to remove from a shipped one,
 * and none is needed by any recipe this mod has. The shape can grow; it should
 * not start wide.
 */
public final class SolarpunkProcessRecipe implements Recipe<SingleRecipeInput> {

    /** The pair of registry entries that says which machine reads this. */
    public record Kind(Supplier<RecipeType<SolarpunkProcessRecipe>> type,
                       Supplier<RecipeSerializer<SolarpunkProcessRecipe>> serializer) {
    }

    private final Ingredient input;
    private final int inputCount;
    private final ItemStack result;
    private final int time;
    private final int energy;
    private final String group;
    private final Kind kind;

    public SolarpunkProcessRecipe(Ingredient input, int inputCount,
                                  ItemStack result, int time, int energy,
                                  String group, Kind kind) {
        this.input = input;
        this.inputCount = inputCount;
        this.result = result;
        this.time = time;
        this.energy = energy;
        this.group = group;
        this.kind = kind;
    }

    public Ingredient input() {
        return input;
    }

    /** How many of the input one run consumes. */
    public int inputCount() {
        return inputCount;
    }

    public ItemStack result() {
        return result;
    }

    /** Ticks of work. */
    public int time() {
        return time;
    }

    /** Total energy for the whole run, not per tick. */
    public int energy() {
        return energy;
    }

    public String group() {
        return group;
    }

    /** Energy per tick. See {@link ProcessCost#perTick}, which explains why. */
    public int energyPerTick() {
        return ProcessCost.perTick(energy, time);
    }

    @Override
    public boolean matches(SingleRecipeInput given, Level level) {
        ItemStack stack = given.item();
        return input.test(stack) && stack.getCount() >= inputCount;
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(input);
        return list;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return kind.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return kind.type().get();
    }

    /**
     * One serializer class, instantiated once per machine.
     *
     * Each instance stamps its own kind onto every recipe it reads, which is
     * how a recipe that arrived over the network or off disk still knows which
     * machine asked for it.
     */
    public static final class Serializer implements RecipeSerializer<SolarpunkProcessRecipe> {

        private final Kind kind;
        private final MapCodec<SolarpunkProcessRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, SolarpunkProcessRecipe> streamCodec;

        public Serializer(Kind kind) {
            this.kind = kind;
            this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input")
                            .forGetter(SolarpunkProcessRecipe::input),
                    Codec.INT.optionalFieldOf("input_count", 1)
                            .forGetter(SolarpunkProcessRecipe::inputCount),
                    ItemStack.CODEC.fieldOf("result")
                            .forGetter(SolarpunkProcessRecipe::result),
                    Codec.INT.fieldOf("time")
                            .forGetter(SolarpunkProcessRecipe::time),
                    Codec.INT.fieldOf("energy")
                            .forGetter(SolarpunkProcessRecipe::energy),
                    Codec.STRING.optionalFieldOf("group", "")
                            .forGetter(SolarpunkProcessRecipe::group)
            ).apply(instance, this::create));

            this.streamCodec = StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, SolarpunkProcessRecipe::input,
                    ByteBufCodecs.VAR_INT, SolarpunkProcessRecipe::inputCount,
                    ItemStack.STREAM_CODEC, SolarpunkProcessRecipe::result,
                    ByteBufCodecs.VAR_INT, SolarpunkProcessRecipe::time,
                    ByteBufCodecs.VAR_INT, SolarpunkProcessRecipe::energy,
                    ByteBufCodecs.STRING_UTF8, SolarpunkProcessRecipe::group,
                    this::create);
        }

        private SolarpunkProcessRecipe create(Ingredient input, int inputCount,
                                             ItemStack result, int time,
                                             int energy, String group) {
            return new SolarpunkProcessRecipe(
                    input, inputCount, result, time, energy, group, kind);
        }

        @Override
        public MapCodec<SolarpunkProcessRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SolarpunkProcessRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
