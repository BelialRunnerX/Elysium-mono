package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.item.ArmoryPartItem;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parts make gear: a main part and the parts that hold it (Silent Gear's
 * SG-31 construction).
 *
 * <p>The main part decides what is being built — a sword blade makes a sword —
 * and every other part in the grid has to be one that fits that gear type and
 * not a second of the same kind. Optional parts are optional: a blade and a rod
 * are a sword, and a tip, grip and binding are the same sword, better.
 */
public class ArmoryGearAssemblyRecipe extends CustomRecipe {

    public ArmoryGearAssemblyRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Found(String gearType, List<ItemStack> parts) {
    }

    private static Found read(CraftingInput input) {
        List<ItemStack> parts = new ArrayList<>();
        Set<String> kinds = new HashSet<>();
        String gearType = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof ArmoryPartItem item)) {
                return null;
            }
            ArmoryPart part = ArmoryParts.get().byId(item.partId());
            if (part == null || item.materials(stack).isEmpty() || !kinds.add(part.partType())) {
                return null;                        // unknown, unbuilt, or two of the same kind
            }
            if (part.partType().equals("main")) {
                gearType = part.gearType();
            }
            parts.add(stack);
        }
        if (gearType == null) {
            return null;
        }
        for (ItemStack stack : parts) {
            ArmoryPart part = ArmoryParts.get().byId(((ArmoryPartItem) stack.getItem()).partId());
            if (part == null || !part.fits(gearType)) {
                return null;
            }
            boolean mainOrShared = part.partType().equals("main")
                    || part.gearType().equals("all") || part.gearType().equals(gearType)
                    || fitsUnder(part.gearType(), gearType);
            if (!mainOrShared) {
                return null;
            }
        }
        return new Found(gearType, parts);
    }

    /** Whether a part made for a family (tool, armor, projectile) fits this gear type. */
    private static boolean fitsUnder(String family, String gearType) {
        com.elysium.armory.property.ArmoryGearType type =
                com.elysium.armory.property.ArmoryGearType.named(gearType);
        if (type == null) {
            return false;
        }
        for (com.elysium.armory.property.ArmoryGearType parent : type.lineage()) {
            if (parent.name().equals(family)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Found found = read(input);
        return found != null && !ArmoryBuilder.gear(found.gearType(), found.parts()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = read(input);
        return found == null ? ItemStack.EMPTY : ArmoryBuilder.gear(found.gearType(), found.parts());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArmoryRecipes.GEAR_ASSEMBLY.get();
    }
}
