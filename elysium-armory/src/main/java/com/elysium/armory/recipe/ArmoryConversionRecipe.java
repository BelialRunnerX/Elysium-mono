package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.property.ArmoryConversionRules;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A vanilla tool, made modular (Silent Gear's SG-42).
 *
 * <p>Put an iron pickaxe in the grid and take back a modular one of iron, with
 * an oak rod — the same tool, now made of parts that can be improved. What a
 * piece becomes is read from its own name by {@link ArmoryConversionRules}
 * ({@code minecraft:iron_pickaxe} is iron, and a pickaxe) rather than from a
 * table of sixty recipes, so a vanilla tool this mod has never heard of
 * converts the moment its material exists.
 */
public class ArmoryConversionRecipe extends CustomRecipe {

    public ArmoryConversionRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** What this vanilla item would become, or null if it would become nothing. */
    static ArmoryConversionRules.Conversion of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals("minecraft")) {
            return null;
        }
        return ArmoryConversionRules.of(key.getPath());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return single(input) != null;
    }

    private static ArmoryConversionRules.Conversion single(CraftingInput input) {
        ArmoryConversionRules.Conversion found = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (found != null) {
                return null;                        // one tool at a time
            }
            found = of(stack);
            if (found == null) {
                return null;
            }
        }
        return found;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ArmoryConversionRules.Conversion conversion = single(input);
        if (conversion == null) {
            return ItemStack.EMPTY;
        }
        String materialId = "elysiumarmory:" + conversion.material();
        if (ArmoryMaterials.get().byId(materialId) == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> parts = new ArrayList<>();
        for (ArmoryPart part : ArmoryParts.get().forGear(conversion.gearType())) {
            if (!part.partType().equals("main")) {
                continue;
            }
            int count = Math.max(1, part.crafting().materials());
            List<String> materials = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                materials.add(materialId);
            }
            parts.add(ArmoryBuilder.part(part.id(), materials));
        }
        // Everything held in the hand needs something to hold it by.
        ArmoryPart rod = ArmoryParts.get().byId("elysiumarmory:rod");
        if (rod != null && ArmoryMaterials.get().byId("elysiumarmory:wood") != null
                && !conversion.gearType().equals("helmet") && !conversion.gearType().equals("chestplate")
                && !conversion.gearType().equals("leggings") && !conversion.gearType().equals("boots")
                && !conversion.gearType().equals("elytra")) {
            parts.add(ArmoryBuilder.part(rod.id(), List.of("elysiumarmory:wood")));
        }
        return ArmoryBuilder.gear(conversion.gearType(), parts);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArmoryRecipes.CONVERSION.get();
    }
}
