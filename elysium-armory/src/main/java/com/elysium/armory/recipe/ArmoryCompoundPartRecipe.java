package com.elysium.armory.recipe;

import com.elysium.armory.item.ArmoryBlueprintItem;
import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.material.ArmoryCategory;
import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Blueprint plus materials makes the part the blueprint plans (Silent Gear's
 * SG-22).
 *
 * <p>One recipe for all forty-eight parts, because the blueprint says which
 * part and the part says how many materials it takes and what they may be. A
 * template is used up; a blueprint comes back.
 *
 * <p>Mixed materials are allowed on purpose — it is what makes two of the same
 * shape different — and the first one laid down is the one the part is named
 * and coloured after, so the order in the grid is a choice rather than noise.
 */
public class ArmoryCompoundPartRecipe extends CustomRecipe {

    public ArmoryCompoundPartRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Found(ArmoryPart part, boolean reusable, List<String> materials) {
    }

    private static Found read(CraftingInput input) {
        ArmoryPart part = null;
        boolean reusable = false;
        List<String> materials = new ArrayList<>();
        boolean blueprint = false;
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArmoryBlueprintItem plan) {
                if (blueprint) {
                    return null;                    // two plans in one grid
                }
                blueprint = true;
                reusable = plan.reusable();
                String id = ArmoryBlueprintItem.partOf(stack);
                part = id == null ? null : ArmoryParts.get().byId(id);
                if (part == null) {
                    return null;
                }
                continue;
            }
            // A plan book stands in for any plan it has learned. Always
            // reusable, because knowing something is not spent by using it,
            // and still only one plan per grid: a book and a blueprint
            // together is two plans and is refused.
            if (stack.getItem() instanceof com.elysium.armory.item.ArmoryPlanBookItem) {
                if (blueprint) {
                    return null;
                }
                blueprint = true;
                reusable = true;
                book = stack;
                continue;
            }
            ArmoryMaterial material = ArmoryMaterials.get().byItem(stack);
            if (material == null) {
                return null;                        // something in the grid is not a material
            }
            materials.add(material.id());
        }
        if (!book.isEmpty()) {
            part = onlyKnown(book, materials);
        }
        return part == null ? null : new Found(part, reusable, materials);
    }

    /**
     * The one part a book could be planning, given what else is in the grid.
     *
     * A book that knows forty plans cannot say which one you meant, so the
     * materials decide: exactly one of the plans it knows must be makeable
     * from what is on the bench. Two that both fit is ambiguous and refused,
     * which is the honest answer — the player puts the blueprint in instead
     * and gets exactly what they asked for.
     */
    private static ArmoryPart onlyKnown(ItemStack book, List<String> materials) {
        ArmoryPart only = null;
        for (String id : com.elysium.armory.item.ArmoryPlanBookItem.plansOf(book)
                .plans()) {
            ArmoryPart known = ArmoryParts.get().byId(id);
            if (known == null || !fits(known, materials)) {
                continue;
            }
            if (only != null) {
                return null;               // two plans fit; say so by refusing
            }
            only = known;
        }
        return only;
    }

    /** Whether these materials make that part: the recipe's own test, shared. */
    private static boolean fits(ArmoryPart part, List<String> materials) {
        if (part.isUpgrade() || part.crafting().materials() <= 0
                || materials.size() != part.crafting().materials()) {
            return false;
        }
        for (String id : materials) {
            ArmoryMaterial material = ArmoryMaterials.get().byId(id);
            if (material == null || !material.supports(part.partType())) {
                return false;
            }
            if (!part.crafting().categories().isEmpty()) {
                boolean any = false;
                for (ArmoryCategory category : part.crafting().categories()) {
                    any |= material.categories().contains(category);
                }
                if (!any) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Found found = read(input);
        return found != null && fits(found.part(), found.materials());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = read(input);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ArmoryBuilder.part(found.part().id(), found.materials());
        out.setCount(Math.max(1, found.part().crafting().count()));
        return out;
    }

    /** A blueprint survives the crafting; a template does not. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof ArmoryBlueprintItem plan && plan.reusable()) {
                remaining.set(i, stack.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArmoryRecipes.COMPOUND_PART.get();
    }

    /** Vanilla still asks for this shape in a few places; the grid is read through CraftingInput. */
    public static boolean isMaterial(CraftingContainer container, int slot) {
        return ArmoryMaterials.get().byItem(container.getItem(slot)) != null;
    }
}
