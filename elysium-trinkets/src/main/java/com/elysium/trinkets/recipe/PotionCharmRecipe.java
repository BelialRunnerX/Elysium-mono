package com.elysium.trinkets.recipe;

import com.elysium.trinkets.ElysiumTrinkets;
import com.elysium.trinkets.item.PotionCharmItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Filling a charm, and refilling one.
 *
 * <h2>Why this is Java and not a recipe file</h2>
 *
 * There is one recipe here and it has hundreds of results: a charm plus any
 * potion gives a charm of that potion. Writing it as files would mean one file
 * per potion, regenerated whenever a mod adds an effect — and a modded potion
 * would simply not work.
 *
 * <h2>Refilling is the same recipe</h2>
 *
 * A charm that already holds the same potion comes back with its durability
 * restored, which is deliberately the same shape rather than a second recipe: a
 * player who works out how to make one has worked out how to keep it.
 */
public class PotionCharmRecipe extends CustomRecipe {

    public PotionCharmRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return result(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack made = result(input);
        return made == null ? ItemStack.EMPTY : made;
    }

    /**
     * The charm this grid would make, or null.
     *
     * Exactly one charm and exactly one potion, and nothing else in the grid:
     * a recipe that ignored a third item would quietly eat it.
     */
    private static ItemStack result(CraftingInput input) {
        ItemStack charm = ItemStack.EMPTY;
        ItemStack potion = ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof PotionCharmItem) {
                if (!charm.isEmpty()) {
                    return null;
                }
                charm = stack;
            } else if (stack.is(Items.POTION)) {
                if (!potion.isEmpty()) {
                    return null;
                }
                potion = stack;
            } else {
                return null;
            }
        }
        if (charm.isEmpty() || potion.isEmpty()) {
            return null;
        }
        PotionContents contents = potion.getOrDefault(DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY);
        if (!PotionCharmItem.allowed(contents)) {
            // Instant and harmful potions are refused here rather than made
            // into a charm that the handler would then ignore: a player should
            // find out at the bench, not by wearing something inert.
            return null;
        }
        PotionContents already = PotionCharmItem.contentsOf(charm);
        if (!already.equals(PotionContents.EMPTY) && !already.equals(contents)) {
            // A charm already holding something different is not refilled by a
            // different potion: that would be a free swap and would lose
            // whatever was in it.
            return null;
        }
        ItemStack made = charm.copy();
        made.setCount(1);
        made.set(DataComponents.POTION_CONTENTS, contents);
        made.setDamageValue(0);
        return made;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(Items.POTION)) {
                // The bottle comes back, as it does from brewing and from
                // every vanilla recipe that drinks one.
                left.set(slot, new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return left;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ElysiumTrinkets.POTION_CHARM_RECIPE.get();
    }
}
