package com.elysium.armory.craft;

import com.elysium.armory.property.ArmorySalvageRules;
import com.elysium.lib.affix.ElysiumAffixRoll;
import com.elysium.lib.item.ElysiumLootCategory;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.tier.ElysiumLootRarity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling a piece's affixes again, for a price.
 *
 * <h2>Three results, not one</h2>
 *
 * A reroll that hands back one result is a coin flip: the player spends, looks,
 * and spends again. Three results shown at once makes it a decision — and it
 * is the same three until the inputs change, so the table cannot be rerolled
 * by closing and reopening it.
 *
 * <h2>What the price buys</h2>
 *
 * The rarity, directly. Essence is the currency and the count is the target:
 * five essence rerolls to Legendary. There is no chance in the price — the
 * chance is in which affixes come up, which is the part worth being uncertain
 * about.
 *
 * <p>The world tier still gates the pools, so paying for a Legendary reroll at
 * the Frontier buys five ordinary affixes rather than five that do not exist
 * there yet.
 */
public final class ArmoryReforge {

    private ArmoryReforge() {
    }

    /** Whether this stack is something a reforge could act on. */
    public static boolean canReforge(ItemStack stack) {
        return !stack.isEmpty() && ElysiumLootCategory.of(stack) != null;
    }

    /** The target rarity a pile of essence pays for. */
    public static int targetRarity(int essence) {
        return Math.max(0, essence);
    }

    /** What that target costs, which is the same number — kept as a call so it reads as a price. */
    public static int cost(int targetRarity) {
        return ArmorySalvageRules.rerollCost(targetRarity);
    }

    /**
     * The choices a bench offers for one set of inputs.
     *
     * @param seed the same inputs must give the same three, so the seed comes
     *             from the bench rather than from a fresh random
     */
    public static List<ItemStack> choices(ItemStack gear, int targetRarity, int worldTier, long seed) {
        List<ItemStack> out = new ArrayList<>();
        if (!canReforge(gear) || targetRarity <= 0) {
            return out;
        }
        for (int i = 0; i < ArmorySalvageRules.CHOICES; i++) {
            RandomSource random = RandomSource.create(seed + i * 0x9E3779B97F4A7C15L);
            ItemStack copy = gear.copy();
            ElysiumAffixRoll.clear(copy);
            ElysiumAffixRoll.roll(copy, targetRarity, worldTier, random);
            copy.set(DataComponents.RARITY, ElysiumRarities.getRarityFromTier(targetRarity));
            out.add(copy);
        }
        return out;
    }

    /**
     * The highest rarity worth offering at a world tier, for a bench that wants
     * to say so rather than let a player spend essence on nothing.
     */
    public static int ceilingFor(int worldTier, double luck) {
        return Math.max(1, ElysiumLootRarity.expected(worldTier, luck) + 1);
    }
}
