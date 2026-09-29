package com.elysium.armory.craft;

import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryGearLogic;
import com.elysium.armory.item.ArmoryGearParts;
import com.elysium.armory.property.ArmorySalvageRules;
import com.elysium.armory.registry.ArmoryComponents;
import com.elysium.armory.registry.ArmoryItems;
import com.elysium.lib.affix.ElysiumAffixEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Taking a piece of gear back apart.
 *
 * <p>The rules are in {@link ArmorySalvageRules}; this is what they act on. A
 * whole piece yields its parts back, each on a roll, plus one essence for each
 * level of each affix it was carrying. Any affixed piece can go on the bench,
 * found or made: a piece of the base game's gear has no parts, and gives only
 * its essence.
 */
public final class ArmorySalvage {

    private ArmorySalvage() {
    }

    /** Whether there is anything to get out of this stack: parts, essence, or both. */
    public static boolean canSalvage(ItemStack stack) {
        boolean modular = stack.getItem() instanceof ArmoryGear && !ArmoryGearLogic.partsOf(stack).isEmpty();
        return !stack.isEmpty() && ArmorySalvageRules.salvageable(modular, affixLevels(stack));
    }

    /**
     * What comes out. The stack itself is not consumed here — the caller does
     * that, once it has somewhere to put the results.
     */
    public static List<ItemStack> salvage(ItemStack gear, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        if (!canSalvage(gear)) {
            return out;
        }
        double wear = gear.getMaxDamage() <= 0 ? 0.0D
                : (double) gear.getDamageValue() / gear.getMaxDamage();
        double efficiency = ArmoryGearLogic.statsOf(gear)
                .get(com.elysium.armory.property.ArmoryProperty.REPAIR_EFFICIENCY.id(), 1.0D);
        double chance = ArmorySalvageRules.chance(wear, efficiency);
        for (ArmoryGearParts.Entry entry : ArmoryGearLogic.partsOf(gear).entries()) {
            if (random.nextDouble() >= chance) {
                continue;                   // that one did not survive the bench
            }
            ItemStack part = ArmoryBuilder.part(entry.part(), entry.materials());
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        int essence = ArmorySalvageRules.essence(affixLevels(gear), paidFor(gear));
        while (essence > 0) {
            int here = Math.min(essence, 64);
            out.add(new ItemStack(ArmoryItems.AFFIX_ESSENCE.get(), here));
            essence -= here;
        }
        return out;
    }

    /** What a reforge cost for the affixes on a stack; 0 when they were found rather than bought. */
    public static int paidFor(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.REFORGED.get(), 0);
    }

    /** Every level of every affix on a stack, added up. */
    public static int affixLevels(ItemStack stack) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : ElysiumAffixEffects.of(stack).levels().entrySet()) {
            total += Math.max(0, entry.getValue());
        }
        return total;
    }
}
