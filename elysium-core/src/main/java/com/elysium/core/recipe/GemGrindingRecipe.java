package com.elysium.core.recipe;

import com.elysium.core.Elysium;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.lib.item.ElysiumPurity;
import com.elysium.lib.item.ElysiumRuneStacks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Grinding a rune down (Apotheosis' AP-12).
 *
 * <h2>Where dust comes from</h2>
 *
 * A rune of purity N cost {@code 2^(N-1)} base runes to reach, and grinding
 * one returns that in dust less a quarter. So dust moves value between runes
 * and never makes it: grinding a rune never pays for cutting one back up,
 * which the harness checks at every purity.
 *
 * <p>In the grid rather than at a bench, because grinding is destruction and
 * destruction should not need a building. The benches are for the decisions a
 * player wants to be careful about.
 */
public class GemGrindingRecipe extends CustomRecipe {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Elysium.MODID);

    public static final DeferredHolder<RecipeSerializer<?>,
            SimpleCraftingRecipeSerializer<GemGrindingRecipe>> SERIALIZER =
            SERIALIZERS.register("gem_grinding",
                    () -> new SimpleCraftingRecipeSerializer<>(GemGrindingRecipe::new));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    public GemGrindingRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static ItemStack single(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty() || !(stack.getItem() instanceof ElysiumRuneItem)) {
                return ItemStack.EMPTY;     // one rune at a time, and nothing else
            }
            found = stack;
        }
        return found;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !single(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack rune = single(input);
        if (rune.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int dust = ElysiumPurity.dustFrom(ElysiumRuneStacks.tierOf(rune));
        // A crafting result is one stack, so a rune worth more dust than a
        // stack holds yields a stack; anything more would be silently lost.
        return new ItemStack(Elysium.GEM_DUST.get(), Math.min(dust, 64));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
