package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryBlueprintItem;
import com.elysium.armory.item.ArmoryPlanBookItem;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Teaching the plan book: the book and any number of blueprints.
 *
 * <p>The blueprints are consumed and the book keeps what they said. A plan it
 * already knows is still consumed — a duplicate blueprint is worth nothing to
 * a book that has read it, and refusing the craft would only make a player
 * check the tooltip first.
 *
 * <p>A <b>template</b> is refused. A template is the single-use plan; a book
 * that could eat one would turn something you were given once into something
 * you know forever, which is the whole distinction between the two items.
 */
public class ArmoryPlanBookRecipe extends CustomRecipe {

    public ArmoryPlanBookRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The book and the plans in one grid, or null when this is not that. */
    private record Found(ItemStack book, List<String> plans) {
    }

    private static Found read(CraftingInput input) {
        ItemStack book = ItemStack.EMPTY;
        List<String> plans = new ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArmoryPlanBookItem) {
                if (!book.isEmpty()) {
                    return null;                    // two books
                }
                book = stack;
                continue;
            }
            if (stack.getItem() instanceof ArmoryBlueprintItem plan) {
                if (!plan.reusable()) {
                    return null;                    // a template is not knowledge
                }
                String part = ArmoryBlueprintItem.partOf(stack);
                if (part == null) {
                    return null;                    // a blank plan says nothing
                }
                plans.add(part);
                continue;
            }
            return null;                            // something else in the grid
        }
        return book.isEmpty() || plans.isEmpty() ? null : new Found(book, plans);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return read(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = read(input);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = found.book().copy();
        out.setCount(1);
        for (String plan : found.plans()) {
            out = ArmoryPlanBookItem.taught(out, plan);
        }
        return out;
    }

    /**
     * What comes back out of the grid: nothing.
     *
     * The book is the result, so it must not also be returned as a remainder —
     * a recipe that gave back its own input would make a second book every
     * time somebody read a blueprint.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ArmoryRecipes.PLAN_BOOK.get();
    }
}
