package com.elysium.core.item;

import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Ascension: combine two identical pieces of gear to push one of them a tier
 * higher.
 *
 * <h2>What changed, and why it was wrong before</h2>
 *
 * This was {@code ElysiumArmorAscension}, and it meant it: the first thing it
 * did was reject anything that was not an {@code ElysiumArmorItem}. So a
 * Neutronium Hammer, a Singularity Lance and every one of the 104 material
 * tools could take runes, could be reforged in the same block's other slot,
 * carried an element and a tier and a socket count — and could not be ascended
 * at all. Nothing said so. The table simply did nothing, which reads as a bug
 * in the table rather than a rule about the item.
 *
 * There was never a reason for the restriction. Everything ascension touches —
 * the tier on the stack, the rarity component, the socket count derived from
 * tier — lives on {@link ElysiumSocketable}, which armour, weapons and tools
 * have all implemented since the rune system was unified. The gate was left
 * over from when armour was the only thing that had any of it.
 *
 * So the rule is now the interface: <b>if it is Elysium gear, it ascends.</b>
 * That includes gear this mod has not written yet — a trinket implementing
 * {@link ElysiumSocketable} is ascendable the day it is registered, with no
 * change here, which is the property that was worth having.
 *
 * <h2>The rules that remain</h2>
 *
 * <ul>
 *   <li>both stacks must be the same item;</li>
 *   <li>both must already be at the same tier, so reaching tier <i>n</i> costs
 *       2^<i>n</i> base pieces rather than <i>n</i> of them — this is the only
 *       thing limiting an uncapped system, and feeding a fresh spare into a
 *       Sovereign piece would quietly bypass it;</li>
 *   <li>the result keeps the first stack's runes and reforge rolls, and comes
 *       out fully repaired.</li>
 * </ul>
 */
public final class ElysiumGearAscension {

    private ElysiumGearAscension() {
    }

    public static boolean canAscend(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (first.getItem() != second.getItem()) {
            return false;
        }
        if (!(first.getItem() instanceof ElysiumSocketable gear)) {
            return false;
        }
        // Same tier both sides: the doubling price, and the whole of what keeps
        // an uncapped ladder from being climbed with spares.
        if (gear.getEffectiveTier(first) != gear.getEffectiveTier(second)) {
            return false;
        }
        return gear.canAscend(first);
    }

    /**
     * @return the ascended stack, or {@link ItemStack#EMPTY} when the inputs
     *         cannot be ascended
     */
    public static ItemStack ascend(ItemStack first, ItemStack second) {
        if (!canAscend(first, second)) {
            return ItemStack.EMPTY;
        }

        ElysiumSocketable gear = (ElysiumSocketable) first.getItem();
        int nextTier = gear.getNextTier(first);

        // copy() carries every component across, so runes and reforge rolls
        // survive the upgrade. The old code rebuilt a bare stack and re-attached
        // a tag, which silently dropped anything it forgot.
        ItemStack result = first.copy();
        result.setCount(1);
        result.setDamageValue(0);

        ElysiumSockets.setGearData(result,
                ElysiumSockets.gearData(result).withAscendedTier(nextTier));

        // Keep the displayed rarity in step with the new tier.
        result.set(DataComponents.RARITY, ElysiumRarities.getRarityFromTier(nextTier));

        return result;
    }
}
