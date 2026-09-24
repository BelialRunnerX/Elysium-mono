package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
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
import java.util.Map;

/**
 * A vanilla tool, made modular (Silent Gear's SG-42).
 *
 * <p>Put an iron pickaxe in the grid and take back a modular one of iron, with
 * an oak rod — the same tool, now made of parts that can be improved. Read from
 * the item's own name ({@code minecraft:iron_pickaxe} is iron, and a pickaxe)
 * rather than from a table of sixty recipes, so a vanilla tool this mod has
 * never heard of converts the moment its material exists.
 */
public class ArmoryConversionRecipe extends CustomRecipe {

    public ArmoryConversionRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** Vanilla's tool materials under the names the material files use. */
    private static final Map<String, String> MATERIALS = Map.of(
            "wooden", "wood", "stone", "stone", "iron", "iron", "golden", "gold",
            "diamond", "diamond", "netherite", "netherite", "leather", "leather", "chainmail", "iron");

    /** Vanilla's shapes under the gear types they become. */
    private static final Map<String, String> SHAPES = Map.ofEntries(
            Map.entry("sword", "sword"), Map.entry("pickaxe", "pickaxe"), Map.entry("axe", "axe"),
            Map.entry("shovel", "shovel"), Map.entry("hoe", "hoe"), Map.entry("helmet", "helmet"),
            Map.entry("chestplate", "chestplate"), Map.entry("leggings", "leggings"),
            Map.entry("boots", "boots"), Map.entry("shears", "shears"), Map.entry("bow", "bow"),
            Map.entry("crossbow", "crossbow"), Map.entry("trident", "trident"), Map.entry("shield", "shield"),
            Map.entry("fishing_rod", "fishing_rod"), Map.entry("elytra", "elytra"));

    private record Conversion(String gearType, String material) {
    }

    /** What this vanilla item would become, or null if it would become nothing. */
    static Conversion of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals("minecraft")) {
            return null;
        }
        String path = key.getPath();
        for (Map.Entry<String, String> shape : SHAPES.entrySet()) {
            if (!path.endsWith(shape.getKey())) {
                continue;
            }
            String prefix = path.substring(0, Math.max(0, path.length() - shape.getKey().length()));
            prefix = prefix.endsWith("_") ? prefix.substring(0, prefix.length() - 1) : prefix;
            String material = prefix.isEmpty() ? defaultMaterial(shape.getValue()) : MATERIALS.get(prefix);
            if (material == null) {
                return null;
            }
            return new Conversion(shape.getValue(), material);
        }
        return null;
    }

    /** What a shape with no material in its name (a bow, a shield) is taken to be made of. */
    private static String defaultMaterial(String gearType) {
        return switch (gearType) {
            case "bow", "crossbow", "fishing_rod", "shield" -> "wood";
            case "trident" -> "prismarine";
            case "elytra" -> "phantom_membrane";
            default -> null;
        };
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return single(input) != null;
    }

    private static Conversion single(CraftingInput input) {
        Conversion found = null;
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
        Conversion conversion = single(input);
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
