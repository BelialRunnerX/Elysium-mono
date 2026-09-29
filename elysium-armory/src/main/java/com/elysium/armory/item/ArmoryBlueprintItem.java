package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.property.ArmoryPlanRules;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A blueprint: the plan for one part. Lay it in a crafting grid with the
 * materials the part calls for and you get that part.
 *
 * <p>Which part it plans rides on the stack, so one item covers all forty-eight
 * — and a pack that adds a part gets blueprints for it without adding an item.
 * A <b>template</b> is the same thing, used up in the crafting; a blueprint
 * comes back out of the grid. Blueprints are drawn at the Gear Smithing Table;
 * templates are found ({@link ArmoryPlanRules#TEMPLATES}).
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

    /**
     * Every part a plan can be drawn for, by id and in order: what the Gear
     * Smithing Table offers, and what a found template can turn out to be.
     * One list, so the two can never disagree.
     */
    public static List<String> plannable() {
        List<String> out = new ArrayList<>();
        for (ArmoryPart part : ArmoryParts.get().all().values()) {
            if (ArmoryPlanRules.plannable(part.isUpgrade(), part.crafting().materials())) {
                out.add(part.id());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    public ItemStack of(String partId) {
        return plan(new ItemStack(this), partId);
    }

    /** Draws a plan onto a stack of this item: which part, and the name that says so. */
    private ItemStack plan(ItemStack stack, String partId) {
        stack.set(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY.with(partId, List.of()));
        ArmoryPart part = ArmoryParts.get().byId(partId);
        if (part != null) {
            stack.set(DataComponents.ITEM_NAME, Component.translatable(
                    "item.elysiumarmory." + partId.substring(partId.indexOf(':') + 1))
                    .append(" ").append(getDescription()));
        }
        return stack;
    }

    /**
     * A found template is read the first time a player carries it: it becomes
     * one of the plans the table offers, drawn evenly. It is found blank and
     * read here, on the server, so that it can be any plan the loaded data
     * holds, a pack's own parts included, rather than one a loot table listed.
     * A stack of blank templates is read as one plan.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (reusable || level.isClientSide() || partOf(stack) != null) {
            return;
        }
        List<String> plans = plannable();
        int drawn = ArmoryPlanRules.drawn(plans.size(), level.getRandom().nextDouble());
        if (drawn >= 0) {
            plan(stack, plans.get(drawn));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String partId = partOf(stack);
        ArmoryPart part = partId == null ? null : ArmoryParts.get().byId(partId);
        if (part == null) {
            tooltip.add(Component.translatable(partId == null && !reusable
                    ? "elysiumarmory.template.unread" : "elysiumarmory.blueprint.blank"));
            return;
        }
        tooltip.add(Component.translatable("elysiumarmory.blueprint.needs", part.crafting().materials()));
        // What else the grid will take, one each: the recipe charges it.
        for (String extra : part.crafting().extra()) {
            tooltip.add(Component.translatable("elysiumarmory.blueprint.also", nameOf(extra)));
        }
    }

    /** What an extra is called: the item's own name, or the first item in the tag. */
    private static Component nameOf(String extra) {
        boolean tag = extra.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? extra.substring(1) : extra);
        if (id != null && tag) {
            var members = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
            if (members.isPresent() && members.get().size() > 0) {
                return members.get().get(0).value().getDescription();
            }
        } else if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDescription();
        }
        return Component.literal(extra);
    }
}
