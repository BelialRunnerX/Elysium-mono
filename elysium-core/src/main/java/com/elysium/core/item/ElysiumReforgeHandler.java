package com.elysium.core.item;

import com.elysium.lib.event.ElysiumPassives;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.elysium.lib.item.ElysiumGearData;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import com.elysium.lib.item.ElysiumRarities;

/**
 * Rerolls the bonus stats on a piece of Elysium gear.
 *
 * Armour, weapons and tools alike. It was armour-only, for no reason anything
 * here needed: the budget is a function of the effective tier and the rolls are
 * written to the gear data component, and both of those belong to
 * {@link ElysiumSocketable}, which every kind of Elysium gear implements.
 *
 * The three rolls are still called armour, health and speed, because that is
 * what {@code ElysiumGearStats} reads them back as - Fortitude, Vitality and
 * Agility on the character. They were never attributes on the piece, so a
 * reforged blade making its wielder harder to kill is the system working as
 * written rather than armour stats leaking onto a sword.
 *
 * Behaviour:
 * <ul>
 *   <li>the roll budget scales with the piece's effective tier;</li>
 *   <li>Silent Gear material grades multiply that budget when present;</li>
 *   <li>the piece keeps its runes and ascension - only the reforge rolls
 *       change.</li>
 * </ul>
 */
public final class ElysiumReforgeHandler {

    private ElysiumReforgeHandler() {
    }

    /**
     * @param gearStack the piece to reforge - armour, weapon or tool
     * @param material the catalyst consumed by the reforge
     * @param random   the level's random source
     * @return a new stack carrying fresh rolls, or {@link ItemStack#EMPTY} when
     *         the inputs are not reforgeable
     */
    public static ItemStack reforge(ItemStack gearStack, ItemStack material,
                                    RandomSource random, Player player) {
        if (gearStack.isEmpty() || material.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Any Elysium gear, not armour alone. Everything below reads the
        // effective tier and the gear data component, both of which live on
        // ElysiumSocketable - the armour-only check was the sole reason a
        // weapon or tool could not be reforged.
        if (!(gearStack.getItem() instanceof ElysiumSocketable gear)) {
            return ItemStack.EMPTY;
        }

        // The equipment archive gives every piece a finite reforging potential
        // — "up to 3 times". Without a cap a player just rerolls until the
        // stats are perfect and the whole system stops being a decision.
        if (!ElysiumSockets.gearData(gearStack).canReforge()) {
            return ItemStack.EMPTY;
        }

        int tier = gear.getEffectiveTier(gearStack);
        int tierMultiplier = ElysiumRarities.getTierMultiplier(tier);
        float gradeMultiplier = getGradeMultiplier(material);

        int basePoints = getBaseStatPoints(tier);
        // Presence and an Artificer's training were both documented as
        // affecting reforge quality and neither was ever consulted. They are
        // the same multiplier, so they are read in one place.
        float character = ElysiumPassives.reforgeScale(player);
        int finalPoints = Math.max(2,
                Math.round(basePoints * tierMultiplier * gradeMultiplier * character));

        ItemStack result = gearStack.copy();

        // Math.max guards here are not cosmetic: nextInt(0) throws, and the old
        // code could reach that with a low tier and a sub-1.0 grade multiplier.
        int armorBonus = random.nextInt(Math.max(1, finalPoints / 2)) + 1;
        int healthBonus = random.nextInt(Math.max(1, finalPoints / 2)) + 1;
        int speedBonus = random.nextInt(Math.max(1, finalPoints / 3)) + 1;

        ElysiumGearData data = ElysiumSockets.gearData(result)
                .withReforgedStats(armorBonus, healthBonus, speedBonus);
        ElysiumSockets.setGearData(result, data);

        return result;
    }

    /**
     * Silent Gear stores a material grade on the stack. We cannot read its
     * component without a hard dependency, so the catalyst's own item decides
     * the multiplier and the Silent Gear path stays open through
     * {@link ElysiumReforgeItem#getGradeMultiplier(String)}.
     */
    private static float getGradeMultiplier(ItemStack material) {
        if (material.getItem() instanceof ElysiumReforgeItem) {
            return ElysiumReforgeItem.getGradeMultiplier("rare");
        }
        return ElysiumReforgeItem.getGradeMultiplier("common");
    }

    /**
     * The roll budget for a tier.
     *
     * The named tiers keep their hand-tuned figures. Above them the curve
     * continues at the same ratio rather than falling through to a default,
     * which is what the table used to do: ascending a Unique piece dropped its
     * budget from 25 to 3 and cut its rolls by 86% — the exact opposite of what
     * ascension is for, and it would not have recovered until roughly tier 49.
     */
    private static int getBaseStatPoints(int tier) {
        if (tier > ElysiumRarities.MAX_NAMED_TIER) {
            // Unique is 25; every tier past it is worth about 40% more again.
            double points = 25.0D * Math.pow(1.4D, tier - ElysiumRarities.MAX_NAMED_TIER);
            // Bounded so an absurdly ascended piece cannot overflow the roll.
            return (int) Math.min(100_000.0D, points);
        }
        return switch (tier) {
            case 0 -> 3;   // Common
            case 1 -> 5;   // Uncommon
            case 2 -> 8;   // Rare
            case 3 -> 12;  // Epic
            case 4 -> 18;  // Legendary
            case 5 -> 25;  // Unique
            default -> 3;
        };
    }
}
