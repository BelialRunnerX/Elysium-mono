package com.elysium.lib.stats;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.item.ElysiumAscension;
import com.elysium.lib.item.ElysiumGearData;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import net.minecraft.world.item.ItemStack;

/**
 * What a piece of gear adds to the character wearing it, and what it demands
 * in return.
 *
 * <h2>The grant</h2>
 *
 * Two sources, both of which climb without limit:
 *
 * <ul>
 *   <li><b>Tier.</b> Every piece gives {@code 1 + tier} points in each of the
 *       two stats its element governs, plus a smaller amount of Fortitude for
 *       being armour at all. Ascension raises tier forever, so this term does
 *       too.</li>
 *   <li><b>Reforge rolls.</b> The armour, health and speed a reforge produced
 *       are read as Fortitude, Vitality and Agility. Reforging was previously
 *       three attribute numbers on one item; now it is three stat numbers on
 *       the character, and the difference matters because ascension refills
 *       the charges.</li>
 * </ul>
 *
 * <h2>The demand</h2>
 *
 * Five character levels per tier. A piece a player cannot meet still equips —
 * refusing to equip is a fight with the inventory that nobody wins — but it
 * grants no stats and no rune affixes until they can. The tooltip says so in
 * red rather than leaving them to wonder why nothing happened.
 */
public final class ElysiumGearStats {

    private ElysiumGearStats() {
    }

    /**
     * The stats a stack grants, before any level check.
     *
     * @return {@link ElysiumStatBlock#EMPTY} for anything that is not Elysium
     *         gear
     */
    public static ElysiumStatBlock of(ItemStack stack) {
        if (!(stack.getItem() instanceof ElysiumSocketable gear)) {
            return ElysiumStatBlock.EMPTY;
        }

        int tier = gear.getEffectiveTier(stack);
        // Was 1 + tier. Linear against a price that doubles every tier meant
        // the twentieth ascension bought a twentieth of what the first one did;
        // ElysiumAscension is the one curve everything tier-shaped now reads.
        int weight = ElysiumAscension.statWeight(tier);
        ElysiumStatBlock block = forElement(gear.getElement(), weight);

        // Armour is armour whatever it resonates with. Half the weight rather
        // than half the tier, so this term keeps pace with the curve instead of
        // flattening out beside it.
        if (gear.isArmour()) {
            block = block.with(ElysiumStats.FORTITUDE, Math.max(1, weight / 2));
        }

        // Reforge rolls, read as character stats rather than as three
        // attribute modifiers bolted to one item.
        ElysiumGearData data = ElysiumSockets.gearData(stack);
        if (data.armorBonus() > 0) {
            block = block.with(ElysiumStats.FORTITUDE, data.armorBonus());
        }
        if (data.healthBonus() > 0) {
            block = block.with(ElysiumStats.VITALITY, data.healthBonus());
        }
        if (data.speedBonus() > 0) {
            block = block.with(ElysiumStats.AGILITY, data.speedBonus());
        }

        return block;
    }

    /**
     * The stats an element governs, at the given weight.
     *
     * An element declares its own — see {@code ElysiumElement#getGrantedStats}.
     * This was a switch over the five canonical elements, which silently gave
     * nothing to a sixth: the default branch fired and no error was raised.
     */
    private static ElysiumStatBlock forElement(ElysiumElement element, int weight) {
        java.util.List<ElysiumStat> granted = element.getGrantedStats();
        if (granted.isEmpty()) {
            // Inert gear pays in the two things any well-made plate gives you.
            return ElysiumStatBlock.of(ElysiumStats.FORTITUDE, weight,
                    ElysiumStats.VITALITY, weight);
        }
        ElysiumStatBlock block = ElysiumStatBlock.EMPTY;
        for (ElysiumStat stat : granted) {
            block = block.with(stat, weight);
        }
        return block;
    }

    /** Character level needed before a stack grants anything. */
    public static int requiredLevel(ItemStack stack) {
        if (!(stack.getItem() instanceof ElysiumSocketable gear)) {
            return 0;
        }
        return ElysiumRarities.getRequiredLevel(gear.getEffectiveTier(stack));
    }

    public static boolean meetsRequirement(int characterLevel, ItemStack stack) {
        return characterLevel >= requiredLevel(stack);
    }
}
