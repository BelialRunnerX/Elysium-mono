package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryComponents;
import com.elysium.armory.registry.ArmoryItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Putting parts together: what a recipe, a command or a creative tab calls to
 * make a finished part or a finished piece of gear.
 *
 * <p>Everything a built stack needs is decided here and written onto it — what
 * it is made of, what that computes to, what colour it is and what it is
 * called — so that nothing downstream has to know how gear is assembled.
 */
public final class ArmoryBuilder {

    private ArmoryBuilder() {
    }

    /** A finished part of these materials. Returns an empty stack if the part has no item. */
    public static ItemStack part(String partId, List<String> materials) {
        String bare = partId.contains(":") ? partId.substring(partId.indexOf(':') + 1) : partId;
        ArmoryPartItem item = ArmoryItems.partItem(bare);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = item.of(materials);
        // A mixture is named after what it is, when it is something: a blade of
        // iron, copper and tin is a Metal Alloy Blade rather than an Iron Blade
        // that happens to have two other metals in it.
        ArmoryMaterial primary = compound(materials);
        stack.set(ArmoryComponents.TINT.get(), new ArmoryTint(colour(primary), colour(primary)));
        if (primary != null && primary.name() != null) {
            stack.set(DataComponents.ITEM_NAME,
                    Component.literal(primary.name() + " ").append(item.getDescription()));
        }
        return stack;
    }

    /**
     * A finished piece of gear from parts that have already been made.
     * Materials are read off each part stack, so a sword is exactly the blade
     * and rod that went into it.
     */
    public static ItemStack gear(String gearType, List<ItemStack> parts) {
        ArmoryGear item = ArmoryItems.gearItem(gearType);
        if (!(item instanceof net.minecraft.world.item.Item asItem)) {
            return ItemStack.EMPTY;
        }
        ArmoryGearParts build = ArmoryGearParts.EMPTY;
        ArmoryMaterial main = null;
        ArmoryMaterial rod = null;
        for (ItemStack stack : parts) {
            if (!(stack.getItem() instanceof ArmoryPartItem part)) {
                continue;
            }
            List<String> materials = new ArrayList<>(part.materials(stack));
            build = build.with(part.partId(), materials);
            ArmoryPart definition = ArmoryParts.get().byId(part.partId());
            if (definition == null) {
                continue;
            }
            if (definition.partType().equals("main") && main == null) {
                main = first(materials);
            } else if (definition.partType().equals("rod") && rod == null) {
                rod = first(materials);
            }
        }
        if (build.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(asItem);
        stack.set(ArmoryComponents.PARTS.get(), build);
        // The marks the parts carried come with them: a piece built from a
        // graded blade and a charged rod is both, averaged, because it is one
        // piece and the numbers it is worth are one set of numbers.
        ArmoryMarks marks = combine(parts);
        if (!marks.isPlain()) {
            stack.set(ArmoryComponents.MARKS.get(), marks);
        }
        item.recompute(stack);
        stack.set(ArmoryComponents.TINT.get(), new ArmoryTint(colour(main), colour(rod == null ? main : rod)));
        if (main != null && main.name() != null) {
            stack.set(DataComponents.ITEM_NAME,
                    Component.literal(main.name() + " ").append(asItem.getDescription()));
        }
        // Worn armour is tinted the same way its sprite is: the layer texture is
        // dyeable, and this is the dye.
        if (item.armourSlot() != null) {
            stack.set(DataComponents.DYED_COLOR,
                    new net.minecraft.world.item.component.DyedItemColor(colour(main), false));
        }
        return stack;
    }

    /**
     * What a set of parts comes to, as one set of marks.
     *
     * The grade and the synergy are averaged, because every part contributes
     * to the whole and a good blade should not be undone by a plain handle.
     * The charge is not: a piece is starcharged if anything in it is, since
     * that is a thing that happened rather than a degree of quality.
     */
    public static ArmoryMarks combine(List<ItemStack> parts) {
        int graded = 0;
        int gradeTotal = 0;
        double synergyTotal = 0.0D;
        int counted = 0;
        boolean charged = false;
        for (ItemStack stack : parts) {
            if (!(stack.getItem() instanceof ArmoryPartItem)) {
                continue;
            }
            ArmoryMarks marks = stack.getOrDefault(ArmoryComponents.MARKS.get(), ArmoryMarks.PLAIN);
            gradeTotal += marks.grade();
            synergyTotal += marks.synergy();
            charged |= marks.starcharged();
            counted++;
            graded++;
        }
        if (counted == 0) {
            return ArmoryMarks.PLAIN;
        }
        int grade = (int) Math.round((double) gradeTotal / graded);
        return new ArmoryMarks(grade, charged, synergyTotal / counted);
    }

    /**
     * What a part is named and coloured after: the compound its materials make,
     * or the first one laid down when they make none.
     */
    private static ArmoryMaterial compound(List<String> materials) {
        List<ArmoryMaterial> resolved = new ArrayList<>();
        for (String id : materials) {
            ArmoryMaterial material = ArmoryMaterials.get().byId(id);
            if (material != null) {
                resolved.add(material);
            }
        }
        ArmoryMaterial named = com.elysium.armory.material.ArmoryCompounds.named(resolved);
        return named != null ? named : first(materials);
    }

    private static ArmoryMaterial first(List<String> materials) {
        return materials.isEmpty() ? null : ArmoryMaterials.get().byId(materials.get(0));
    }

    private static int colour(ArmoryMaterial material) {
        return material == null ? 0xFFFFFF : material.colour();
    }
}
