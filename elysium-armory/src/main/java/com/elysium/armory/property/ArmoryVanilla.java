package com.elysium.armory.property;

import java.util.Locale;

/**
 * What a computed property means to the game (Silent Gear's SG-6): the
 * attributes it becomes, the durability an armour piece gets, how a bow draws.
 * One table, executed by a harness, so the item classes in P3 can be thin.
 *
 * <p>Elysium's rulings show up in two places. Magic armour's reduction was a
 * piecewise curve that flattened at 60% and then crept; here it is
 * {@code stat / (stat + b)} like every other percentage, uncapped and never
 * reaching all of it. Nothing else here is a ceiling: the draw and charge
 * clamps are kept, because they are the tick loop's own limits rather than a
 * balance decision, and a bow that draws in zero ticks does not draw at all.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public final class ArmoryVanilla {

    private ArmoryVanilla() {
    }

    /** Vanilla's own attack speed with an empty hand; a weapon states the difference from it. */
    public static final double BASE_ATTACK_SPEED = 4.0D;

    /** A broken tool swings faster and hits for nothing. */
    public static final double BROKEN_ATTACK_SPEED_BONUS = 0.7D;
    public static final double BROKEN_ATTACK_DAMAGE = 1.0D;
    public static final double BROKEN_HARVEST_SPEED = 0.25D;

    /** Durability an armour piece gets per point of armor_durability, by slot. */
    public static double armourDurabilityMultiplier(String slot) {
        return switch (slot.toLowerCase(Locale.ROOT)) {
            case "helmet" -> 11.0D;
            case "chestplate" -> 16.0D;
            case "leggings" -> 15.0D;
            case "boots" -> 13.0D;
            case "elytra" -> 25.0D;
            case "shield" -> 22.466667D;
            default -> 0.0D;
        };
    }

    public static int armourDurability(double armourDurability, String slot) {
        return (int) (Math.max(0.0D, armourDurability) * armourDurabilityMultiplier(slot));
    }

    /** The attack damage attribute a weapon adds (the player's own 1 is on top of it). */
    public static double attackDamage(double attackDamage, boolean broken) {
        return broken ? BROKEN_ATTACK_DAMAGE : Math.max(0.0D, attackDamage);
    }

    /** The attack speed attribute: the property is the speed, the attribute the difference from 4. */
    public static double attackSpeed(double attackSpeed, boolean broken) {
        return (broken ? attackSpeed + BROKEN_ATTACK_SPEED_BONUS : attackSpeed) - BASE_ATTACK_SPEED;
    }

    /** Mining speed against a block the tool is right for; 1 when it is not, a quarter when broken. */
    public static double harvestSpeed(double harvestSpeed, boolean correctTool, boolean broken) {
        if (broken) {
            return BROKEN_HARVEST_SPEED;
        }
        return correctTool ? Math.max(0.0D, harvestSpeed) : 1.0D;
    }

    public static double toughnessAttribute(double armourToughness) {
        return armourToughness / 4.0D;
    }

    public static double knockbackResistanceAttribute(double knockbackResistance) {
        return knockbackResistance > 0.0D ? knockbackResistance / 10.0D : 0.0D;
    }

    /**
     * Magic armour's share of a magic hit. Silent Gear gave 3% a point to
     * twenty points and 1.5% after, flattening at 60%; Elysium's ruling makes
     * it a share of an uncapped stat, at the same early slope.
     */
    public static final double MAGIC_ARMOUR_POINTS_FOR_ALL = 33.3D;

    public static double magicArmourShare(double magicArmour) {
        double m = Math.max(0.0D, magicArmour);
        return m <= 0.0D ? 0.0D : m / (m + MAGIC_ARMOUR_POINTS_FOR_ALL);
    }

    public static int enchantmentValue(double enchantmentValue, boolean enchantingAllowed) {
        return enchantingAllowed ? (int) Math.max(0.0D, enchantmentValue) : 0;
    }

    /** COMMON, UNCOMMON, RARE or EPIC, from the rarity property (enchanting adds 20). */
    public static String rarity(double rarity, boolean enchanted) {
        int r = (int) rarity + (enchanted ? 20 : 0);
        if (r < 40) {
            return "COMMON";
        }
        if (r < 80) {
            return "UNCOMMON";
        }
        return r < 120 ? "RARE" : "EPIC";
    }

    // ------------------------------------------------------------------
    // Ranged weapons
    // ------------------------------------------------------------------

    public static final int MIN_DRAW_TICKS = 10;
    public static final int MAX_DRAW_TICKS = 100;

    /** Ticks a bow takes to draw fully. */
    public static int drawDelay(double drawSpeed) {
        double speed = drawSpeed > 0.0D ? drawSpeed : 1.0D;
        return (int) Math.max(MIN_DRAW_TICKS, Math.min(MAX_DRAW_TICKS, 20.0D / speed));
    }

    /** How fast the arrow leaves, from how long the bow was held; one is a full draw. */
    public static double arrowVelocity(int useTicks, int drawDelay) {
        double t = drawDelay <= 0 ? 1.0D : (double) useTicks / drawDelay;
        double v = (t * t + 2.0D * t) / 3.0D;
        return Math.min(1.0D, Math.max(0.0D, v));
    }

    /** Ticks a crossbow takes to charge. */
    public static int crossbowCharge(double drawSpeed) {
        double speed = drawSpeed > 0.0D ? drawSpeed : 1.0D;
        return (int) Math.round(Math.max(5.0D, Math.min(50.0D, 25.0D / speed)));
    }

    /** A dagger leaves its target vulnerable again sooner. */
    public static final double DAGGER_INVULNERABILITY = 0.67D;

    // ------------------------------------------------------------------
    // Damage factor (SG-7): properties are recomputed in steps as gear wears
    // ------------------------------------------------------------------

    public static final int DAMAGE_FACTOR_LEVELS = 10;

    /**
     * Which step of wear a piece is on. Traits that read "as it is damaged"
     * change in this many steps rather than every point of durability, so a
     * sword does not recompute its properties on every hit.
     */
    public static int damageFactor(int damage, int maxDamage) {
        int per = Math.max(1, maxDamage / DAMAGE_FACTOR_LEVELS);
        return Math.max(0, damage) / per;
    }

    /** Whether a piece's properties have to be computed again after taking damage. */
    public static boolean needsRecompute(int damageBefore, int damageAfter, int maxDamage) {
        return damageFactor(damageBefore, maxDamage) != damageFactor(damageAfter, maxDamage);
    }
}
