package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A blueprint: the plan for one part. Lay it in a crafting grid with the
 * materials the part calls for and you get that part.
 *
 * <p>Which part it plans rides on the stack, so one item covers all forty-eight
 * — and a pack that adds a part gets blueprints for it without adding an item.
 * A <b>template</b> is the same thing, used up in the crafting; a blueprint
 * comes back out of the grid.
 */
public class ArmoryBlueprintItem extends Item {

    private final boolean reusable;

    public ArmoryBlueprintItem(boolean reusable, Properties properties) {
        super(reusable ? properties.stacksTo(1) : properties);
        this.reusable = reusable;
    }

    public boolean reusable() {
        return reusable;
    }

    /** The part this plans, as the data names it; null for a blank one. */
    public static String partOf(ItemStack stack) {
        ArmoryGearParts parts = stack.getOrDefault(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY);
        return parts.isEmpty() ? null : parts.entries().get(0).part();
    }

    public ItemStack of(String partId) {
        ItemStack stack = new ItemStack(this);
        stack.set(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY.with(partId, List.of()));
        ArmoryPart part = ArmoryParts.get().byId(partId);
        if (part != null) {
            stack.set(DataComponents.ITEM_NAME, Component.translatable(
                    "item.elysiumarmory." + partId.substring(partId.indexOf(':') + 1))
                    .append(" ").append(getDescription()));
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String partId = partOf(stack);
        ArmoryPart part = partId == null ? null : ArmoryParts.get().byId(partId);
        if (part == null) {
            tooltip.add(Component.translatable("elysiumarmory.blueprint.blank"));
            return;
        }
        tooltip.add(Component.translatable("elysiumarmory.blueprint.needs", part.crafting().materials()));
    }
}
