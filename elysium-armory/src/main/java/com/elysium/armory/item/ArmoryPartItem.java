package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A finished part, waiting to be built into gear: a sword blade of iron, a rod
 * of oak, a netherite tip.
 *
 * <p>Which part it is, is the item; what it is made of rides on the stack, in
 * the same component a whole piece of gear uses — one entry, this part's.
 */
public class ArmoryPartItem extends Item {

    private final String partId;

    public ArmoryPartItem(String partId, Properties properties) {
        super(properties);
        this.partId = partId;
    }

    /** The part definition's id, as the data names it. */
    public String partId() {
        return "elysiumarmory:" + partId;
    }

    public ArmoryPart definition() {
        return ArmoryParts.get().byId(partId());
    }

    /** The materials this part was made from, in order; empty for an unbuilt one. */
    public List<String> materials(ItemStack stack) {
        ArmoryGearParts parts = stack.getOrDefault(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY);
        ArmoryGearParts.Entry entry = parts.find(partId());
        return entry == null ? List.of() : entry.materials();
    }

    /** The loaded materials, skipping any a pack has since removed. */
    public List<ArmoryMaterial> resolved(ItemStack stack) {
        List<ArmoryMaterial> out = new ArrayList<>();
        for (String id : materials(stack)) {
            ArmoryMaterial material = ArmoryMaterials.get().byId(id);
            if (material != null) {
                out.add(material);
            }
        }
        return out;
    }

    /** A stack of this part made of these materials. */
    public ItemStack of(List<String> materials) {
        ItemStack stack = new ItemStack(this);
        stack.set(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY.with(partId(), materials));
        return stack;
    }

    /** The material a part is named and coloured after: the first one. */
    public ArmoryMaterial primary(ItemStack stack) {
        List<ArmoryMaterial> materials = resolved(stack);
        return materials.isEmpty() ? null : materials.get(0);
    }
}
