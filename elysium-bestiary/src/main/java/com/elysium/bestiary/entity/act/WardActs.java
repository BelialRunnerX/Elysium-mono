package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.WardEntity;
import com.elysium.bestiary.rules.WardRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The one place a ward is looked for, and the one place the two numbers are
 * chosen between.
 *
 * <h2>One branch per ward, and no creature named</h2>
 *
 * The seventh file in this module built that way. Six branches for thirteen
 * creatures, because a ward is a question about the <em>player</em> — what are
 * they carrying — and thirteen versions of that question would be thirteen
 * places to ask it slightly differently.
 *
 * <p>No creature is named anywhere below, and {@code validate.py} reads this
 * file and fails if it finds one.
 *
 * <h2>Why this file has no numbers in it</h2>
 *
 * Because the two numbers are the entire claim. {@link WardRules} proves that
 * every creature leaves between a third and four fifths of itself to somebody
 * carrying the right thing — and a number typed here would leave that proof
 * passing while the game hit for something else, which is the failure this
 * module has now written a validator against four times. What this file decides
 * is only <em>which</em> of the row's two numbers applies.
 */
public final class WardActs {

    private WardActs() {
    }

    /** Whether the one being hit is carrying what answers this creature. */
    public static boolean warded(LivingEntity who, WardRules.Warded row) {
        return switch (row.ward()) {
            case WardRules.FLAME -> who.hasEffect(MobEffects.FIRE_RESISTANCE)
                    || holding(who, Items.WATER_BUCKET);
            case WardRules.SHOT -> who.isBlocking() || holding(who, Items.SHIELD);
            case WardRules.BLAST -> wearing(who, Items.IRON_CHESTPLATE)
                    || wearing(who, Items.DIAMOND_CHESTPLATE)
                    || wearing(who, Items.NETHERITE_CHESTPLATE);
            case WardRules.ROT -> who.hasEffect(MobEffects.ABSORPTION)
                    || holding(who, Items.MILK_BUCKET);
            case WardRules.FROST -> wearing(who, Items.LEATHER_BOOTS)
                    || who.hasEffect(MobEffects.FIRE_RESISTANCE);
            case WardRules.CURSE -> holding(who, Items.TOTEM_OF_UNDYING)
                    || who.hasEffect(MobEffects.REGENERATION);
            default -> false;
        };
    }

    /**
     * What this creature takes off that one, which is the row's own number.
     *
     * <p>Two numbers on the row and nothing else. Every ward above answers the
     * same question, and whichever way it answers, the amount comes from the
     * table that was proved about.
     */
    public static float bites(WardEntity self, LivingEntity who,
                              WardRules.Warded row) {
        return (float) (warded(who, row) ? row.blunted() : row.bite());
    }

    /** Whether it is in a hand. */
    private static boolean holding(LivingEntity who, net.minecraft.world.item.Item what) {
        return who.getMainHandItem().is(what) || who.getOffhandItem().is(what);
    }

    /** And whether it is worn. */
    private static boolean wearing(LivingEntity who, net.minecraft.world.item.Item what) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = who.getItemBySlot(slot);
            if (!worn.isEmpty() && worn.is(what)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a player is the one being hit, for the branches that care. */
    public static boolean isPlayer(LivingEntity who) {
        return who instanceof Player;
    }
}
