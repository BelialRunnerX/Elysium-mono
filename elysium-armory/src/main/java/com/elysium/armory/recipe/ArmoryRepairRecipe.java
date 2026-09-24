package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryGearLogic;
import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Repair (Silent Gear's SG-71): a damaged piece and the metal to mend it.
 *
 * <h2>Why not the anvil</h2>
 *
 * Vanilla's anvil repairs by consuming levels and forgetting what the item is
 * made of. A modular piece knows exactly what it is made of, and its materials
 * say how well they mend — a material's {@code repair_value} is how much one
 * unit is worth, and the piece's {@code repair_efficiency} is how much of that
 * survives the join. So repair happens in the grid, costs materials rather
 * than experience, and a well-chosen material is worth choosing twice.
 *
 * <h2>Broken is not gone</h2>
 *
 * Gear in this mod stops one point short of breaking and stays there. This is
 * how it comes back: a broken piece repairs exactly as a damaged one does, and
 * the player never loses the metal they put in.
 */
public class ArmoryRepairRecipe extends CustomRecipe {

    /**
     * How much durability one unit of an ordinary material mends.
     *
     * A quarter of the piece, so four ingots fully mend anything and a single
     * one is always worth doing.
     */
    public static final double SHARE_PER_UNIT = 0.25D;

    public ArmoryRepairRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Repair(ItemStack gear, int units, double value) {
    }

    private static Repair read(CraftingInput input) {
        ItemStack gear = ItemStack.EMPTY;
        int units = 0;
        double value = 0.0D;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArmoryGear) {
                if (!gear.isEmpty()) {
                    return null;                    // one piece at a time
                }
                gear = stack;
                continue;
            }
            ArmoryMaterial material = ArmoryMaterials.get().byItem(stack);
            if (material == null) {
                return null;                        // something that is not metal
            }
            units++;
            value += repairValue(material);
        }
        if (gear.isEmpty() || units == 0 || gear.getDamageValue() <= 0) {
            return null;
        }
        return new Repair(gear, units, value / units);
    }

    /**
     * What one unit of this material is worth as a mend.
     *
     * Read off the material's own main-part properties, so a material that was
     * never meant to make a main part — a dye, a dust — mends at the ordinary
     * rate rather than not at all. The properties are modifiers rather than a
     * number, which is why this walks them through the same compute the rest
     * of the mod uses instead of reading a field.
     */
    private static double repairValue(ArmoryMaterial material) {
        var properties = material.forPart("main");
        for (var entry : properties.modifiers().entrySet()) {
            if (entry.getKey().property().equals(ArmoryProperty.REPAIR_VALUE.id())) {
                return com.elysium.armory.property.ArmoryCompute.compute(
                        1.0D, entry.getValue(), 0.0D);
            }
        }
        return 1.0D;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return read(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Repair repair = read(input);
        if (repair == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = repair.gear().copy();
        double efficiency = ArmoryGearLogic.statsOf(out)
                .get(ArmoryProperty.REPAIR_EFFICIENCY.id(), 1.0D);
        double share = SHARE_PER_UNIT * repair.units() * repair.value() * Math.max(0.0D, efficiency);
        int mended = (int) Math.ceil(out.getMaxDamage() * share);
        out.setDamageValue(Math.max(0, out.getDamageValue() - Math.max(1, mended)));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ArmoryRecipes.REPAIR.get();
    }
}
