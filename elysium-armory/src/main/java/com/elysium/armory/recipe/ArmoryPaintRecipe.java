package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryTint;
import com.elysium.armory.registry.ArmoryComponents;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Paint (Silent Gear's SG-74): gear in whatever colour the player wants.
 *
 * <h2>Two colours, because a piece has two layers</h2>
 *
 * A modular sprite is drawn in two greys — the working metal and the thing you
 * hold it by — and tinted at render time by the materials. Painting overrides
 * that: one dye paints the metal, two paint the metal and the handle. It is
 * the same two numbers the material would have set, so nothing else in the mod
 * has to know a piece was painted.
 *
 * <h2>Undoing it</h2>
 *
 * A sponge. Paint is the one decision in this mod that costs nothing to make
 * and should cost nothing to unmake, and a player who has painted a piece
 * black and cannot find it in a chest has been punished for decorating.
 */
public class ArmoryPaintRecipe extends CustomRecipe {

    public ArmoryPaintRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Paint(ItemStack gear, List<DyeColor> dyes, boolean strip) {
    }

    private static Paint read(CraftingInput input) {
        ItemStack gear = ItemStack.EMPTY;
        List<DyeColor> dyes = new ArrayList<>();
        boolean strip = false;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArmoryGear) {
                if (!gear.isEmpty()) {
                    return null;
                }
                gear = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyes.add(dye.getDyeColor());
            } else if (stack.is(Items.SPONGE)) {
                strip = true;
            } else {
                return null;
            }
        }
        if (gear.isEmpty() || (dyes.isEmpty() && !strip) || (strip && !dyes.isEmpty())) {
            return null;
        }
        return new Paint(gear, dyes, strip);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return read(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Paint paint = read(input);
        if (paint == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = paint.gear().copy();
        if (paint.strip()) {
            // Back to the materials' own colours, which is what the absence of
            // a tint means everywhere else.
            out.remove(ArmoryComponents.TINT.get());
            return out;
        }
        int first = paint.dyes().get(0).getTextureDiffuseColor() & 0xFFFFFF;
        int second = paint.dyes().size() > 1
                ? paint.dyes().get(1).getTextureDiffuseColor() & 0xFFFFFF
                : first;
        out.set(ArmoryComponents.TINT.get(), new ArmoryTint(first, second));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ArmoryRecipes.PAINT.get();
    }
}
