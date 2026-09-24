package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.registry.ArmoryItems;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One finished example of each kind of gear, for the creative tab (Silent
 * Gear's SG-109).
 *
 * <h2>Why the tab needed this</h2>
 *
 * Modular gear registered as an item is an <em>empty</em> item: a sword with
 * no blade, no rod and no material, which has no damage, no durability and no
 * name worth reading. A creative player taking one out of the tab gets
 * something that looks broken, and the natural conclusion is that the mod is.
 *
 * <p>So the tab also offers one of each, built. Built through
 * {@link ArmoryBuilder} — the same call the assembly recipe makes — rather
 * than hand-assembled here, because a sample that was put together a second
 * way would be a second way of putting gear together, and the first person to
 * find the difference would be a player wondering why the creative sword is
 * not the crafted one.
 *
 * <h2>When there is nothing to show</h2>
 *
 * Parts and materials are datapack data. Before a world is loaded there are
 * none, so there is nothing to build and this returns an empty list — the tab
 * then holds exactly what it held before, rather than a row of missing-texture
 * squares or a crash in the main menu.
 */
public final class ArmorySamples {

    private ArmorySamples() {
    }

    /**
     * What a sample is made of.
     *
     * Iron: the material every player already has a feel for, so a sample
     * reads as "this is what one of these is" rather than as a prize.
     */
    public static final String MATERIAL = "iron";

    /** One built piece per gear type that has a main part, in registration order. */
    public static List<ItemStack> all() {
        List<ItemStack> samples = new ArrayList<>();
        if (ArmoryMaterials.get().byId(MATERIAL) == null) {
            return samples;
        }
        for (String gearType : ArmoryItems.gear().keySet()) {
            ItemStack sample = of(gearType);
            if (!sample.isEmpty()) {
                samples.add(sample);
            }
        }
        return samples;
    }

    /** One built piece, or an empty stack when the data has nothing to build it from. */
    public static ItemStack of(String gearType) {
        ArmoryPart main = mainPartFor(gearType);
        if (main == null || ArmoryMaterials.get().byId(MATERIAL) == null) {
            return ItemStack.EMPTY;
        }
        ItemStack part = ArmoryBuilder.part(main.id(), List.of(MATERIAL));
        if (part.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ArmoryBuilder.gear(gearType, List.of(part));
    }

    /**
     * The part that decides what a piece is.
     *
     * A main part names its gear type, which is how the assembly recipe works
     * out what is being built — so finding one is the same question asked
     * backwards.
     */
    private static ArmoryPart mainPartFor(String gearType) {
        for (ArmoryPart part : ArmoryParts.get().all().values()) {
            if (part.partType().equals("main") && part.gearType().equals(gearType)) {
                return part;
            }
        }
        return null;
    }
}
