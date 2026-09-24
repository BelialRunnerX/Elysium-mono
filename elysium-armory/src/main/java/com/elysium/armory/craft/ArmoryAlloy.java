package com.elysium.armory.craft;

import com.elysium.armory.item.ArmoryMarks;
import com.elysium.armory.item.ArmoryPartItem;
import com.elysium.armory.material.ArmoryCategory;
import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.property.ArmoryMaterialScience;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Alloy Forge does to a part (Silent Gear's SG-80).
 *
 * <h2>What it adds, and what it does not take away</h2>
 *
 * A part made of several materials has always averaged them, and that works in
 * a crafting grid. The forge adds the thing a grid cannot: synergy, the bonus
 * materials earn for having something in common.
 *
 * <p>Nothing is removed from the grid. A player who never builds a forge makes
 * exactly the parts they always did; a player who does gets more out of the
 * same metals for mixing them on purpose.
 */
public final class ArmoryAlloy {

    private ArmoryAlloy() {
    }

    /** Whether there is any synergy to be had from this part. */
    public static boolean canAlloy(ItemStack part) {
        return synergyOf(part) > 1.0D
                && marks(part).synergy() <= 1.0D;
    }

    /** The synergy this part's materials earn. */
    public static double synergyOf(ItemStack part) {
        if (!(part.getItem() instanceof ArmoryPartItem item)) {
            return 1.0D;
        }
        List<Set<String>> categories = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : item.materials(part)) {
            if (!seen.add(id)) {
                continue;                   // the same metal twice is one metal
            }
            ArmoryMaterial material = ArmoryMaterials.get().byId(id);
            if (material == null) {
                continue;
            }
            Set<String> names = new LinkedHashSet<>();
            for (ArmoryCategory category : material.categories()) {
                names.add(category.id());
            }
            categories.add(names);
        }
        return ArmoryMaterialScience.synergy(categories);
    }

    /** The part with its synergy written onto it. Returns an empty stack if there is none. */
    public static ItemStack alloy(ItemStack part) {
        if (!canAlloy(part)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = part.copy();
        out.setCount(1);
        out.set(ArmoryComponents.MARKS.get(), marks(part).withSynergy(synergyOf(part)));
        
        return out;
    }

    private static ArmoryMarks marks(ItemStack part) {
        return part.getOrDefault(ArmoryComponents.MARKS.get(), ArmoryMarks.PLAIN);
    }
}
