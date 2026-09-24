package com.elysium.core.compat;

import com.elysium.core.Elysium;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Converting gear at a smithing table.
 *
 * <h2>Why the smithing table and not a block of Elysium's own</h2>
 *
 * Because a player already knows where it is. A conversion bench would have to
 * be discovered, crafted and learned before it could be used once, and the
 * thing it does — put a template, a piece of gear and a material together and
 * get a changed piece back — is exactly what the smithing table already means
 * in this version of the game. The three slots were already the right three
 * slots.
 *
 * <h2>Why one recipe and not four</h2>
 *
 * The direction is read off the inputs. Elysium gear plus the seam template
 * goes out to Silent Gear; a Silent Gear item plus the same template comes
 * back. Four recipes would mean four places for the same question to be
 * answered, and a player holding the wrong combination would get no feedback
 * beyond an empty output slot either way.
 *
 * {@link GearConvergence#directionFor} is the single answer, and it returns
 * null for a piece that already carries both systems — so the table refuses
 * rather than charging a template and a shard for an item that comes back
 * identical.
 *
 * <h2>Why it is special</h2>
 *
 * {@code isSpecial()} keeps it out of the recipe book, which is right for a
 * recipe whose output depends on what you put in: the book would have to show
 * a result, and any result it showed would be a lie for most inputs.
 */
public class ConvergenceSmithingRecipe implements SmithingRecipe {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Elysium.MODID);

    /**
     * The one instance, shared by both codecs.
     *
     * <h3>Why a singleton and not two {@code unit}s</h3>
     *
     * Because this recipe carries no data, both codecs were written as units —
     * {@code MapCodec.unit(ConvergenceSmithingRecipe::new)} to read it from
     * JSON and {@code StreamCodec.unit(new ConvergenceSmithingRecipe())} to
     * send it to a client. That reads as two statements of the same fact and is
     * in practice a disagreement: the map codec's supplier makes a <em>new</em>
     * object when the recipe file loads, and {@code StreamCodec.unit} refuses to
     * encode anything that is not {@code equals} to the value it captured.
     *
     * So every client that joined was disconnected while the server wrote the
     * recipe list:
     *
     * <pre>
     *   EncoderException: Failed to encode packet 'clientbound/update_recipes'
     *   Caused by: IllegalStateException: Can't encode
     *       'ConvergenceSmithingRecipe@ed0714d', expected
     *       'ConvergenceSmithingRecipe@24a731ce'
     * </pre>
     *
     * Nothing about it is visible at compile time, in a single-player world
     * that never reaches the recipe sync, or in any check that reads the source
     * — the two lines look right next to each other. RecipeCodecHarness runs
     * the round trip.
     */
    public static final ConvergenceSmithingRecipe INSTANCE = new ConvergenceSmithingRecipe();

    /** Read from JSON. Always the singleton, because there is nothing to read. */
    public static final MapCodec<ConvergenceSmithingRecipe> MAP_CODEC =
            MapCodec.unit(INSTANCE);

    /** Sent to a client. Writes nothing, and now accepts what MAP_CODEC produces. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvergenceSmithingRecipe>
            STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ConvergenceSmithingRecipe>>
            SERIALIZER = SERIALIZERS.register("convergence",
                    () -> new RecipeSerializer<ConvergenceSmithingRecipe>() {
                        @Override
                        public MapCodec<ConvergenceSmithingRecipe> codec() {
                            return MAP_CODEC;
                        }

                        @Override
                        public StreamCodec<RegistryFriendlyByteBuf, ConvergenceSmithingRecipe> streamCodec() {
                            return STREAM_CODEC;
                        }
                    });

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    /** Whether this template asks for the Silent Gear crossing. */
    private static boolean isSilentTemplate(ItemStack stack) {
        return stack.is(Elysium.SILENT_CONVERGENCE_TEMPLATE.get());
    }

    private static boolean isAffixTemplate(ItemStack stack) {
        return stack.is(Elysium.AFFIX_CONVERGENCE_TEMPLATE.get());
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return isSilentTemplate(stack) || isAffixTemplate(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        // Without knowing the template this can only say "some crossing might
        // apply". matches() is where the real answer is, and this is only used
        // to decide whether the slot glows.
        return GearConvergence.directionFor(stack, true) != null
                || GearConvergence.directionFor(stack, false) != null;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return stack.is(Items.AMETHYST_SHARD);
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return directionOf(input) != null;
    }

    private GearConvergence.Direction directionOf(SmithingRecipeInput input) {
        if (!isTemplateIngredient(input.template())
                || !isAdditionIngredient(input.addition())) {
            return null;
        }
        return GearConvergence.directionFor(input.base(),
                isSilentTemplate(input.template()));
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        GearConvergence.Direction direction = directionOf(input);
        if (direction == null) {
            return ItemStack.EMPTY;
        }
        return GearConvergence.convert(input.base(), direction);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        // There is no one result. Returning the base unchanged would have the
        // recipe book advertise a no-op craft.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    /**
     * Any two of these are the same recipe, because none of them carries
     * anything.
     *
     * Belt and braces on top of the singleton. The singleton is what fixes the
     * bug; this is what keeps it fixed if some future path — a datapack reload
     * that reconstructs recipes, a codec rewritten to take a supplier again —
     * produces a second instance. {@code StreamCodec.unit} asks {@code equals},
     * and the default answer for a stateless object is the wrong one.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ConvergenceSmithingRecipe;
    }

    @Override
    public int hashCode() {
        return ConvergenceSmithingRecipe.class.hashCode();
    }
}
