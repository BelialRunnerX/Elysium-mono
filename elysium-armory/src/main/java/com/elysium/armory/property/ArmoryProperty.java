package com.elysium.armory.property;

import java.util.List;

/**
 * A number a piece of gear has: durability, attack damage, armour.
 *
 * <h2>Floors, and no ceilings</h2>
 *
 * The originals give every property a range. Elysium keeps the bottom of the
 * range where going below it would <em>invert</em> the property — a negative
 * durability, an attack speed that never swings — and drops every top: the
 * owner's ruling, "uncap everything", and "we want the player to scale really
 * hard". Properties that are percentages of something are marked, so that
 * when they reach a vanilla attribute they go through stat / (stat + b)
 * rather than being added as a raw fraction.
 *
 * @param id           the property's name in data ("attack_damage")
 * @param base         what a piece starts from before any material
 * @param defaultValue what a piece with no modifiers at all has
 * @param floor        the least it can be; {@link ArmoryCompute#NO_FLOOR} for none
 * @param group        which kinds of gear care about it
 * @param integer      shown and stored as a whole number
 * @param share        a percentage: applied through stat / (stat + b)
 *
 * <p>Imports nothing Minecraft, and must not: it is executed by a harness.
 */
public record ArmoryProperty(String id, double base, double defaultValue, double floor,
                             Group group, boolean integer, boolean share) {

    /** Which kinds of gear a property applies to. A sword ignores ARMOR; a helmet ignores ATTACK. */
    public enum Group { GENERAL, HARVEST, ATTACK, PROJECTILE, ARMOR }

    private static ArmoryProperty of(String id, double defaultValue, double floor, Group group,
                                     boolean integer) {
        return new ArmoryProperty(id, 0.0D, defaultValue, floor, group, integer, false);
    }

    public static final ArmoryProperty DURABILITY = of("durability", 0, 0, Group.GENERAL, true);
    public static final ArmoryProperty ARMOR_DURABILITY = of("armor_durability", 0, 0, Group.GENERAL, true);
    public static final ArmoryProperty REPAIR_EFFICIENCY = of("repair_efficiency", 1, 0, Group.GENERAL, false);
    public static final ArmoryProperty REPAIR_VALUE = of("repair_value", 1, 0, Group.GENERAL, false);
    public static final ArmoryProperty ENCHANTMENT_VALUE = of("enchantment_value", 0, 0, Group.GENERAL, true);
    public static final ArmoryProperty CHARGING_VALUE = of("charging_value", 0, 0, Group.GENERAL, false);
    public static final ArmoryProperty RARITY = of("rarity", 0, 0, Group.GENERAL, true);
    public static final ArmoryProperty HARVEST_SPEED = of("harvest_speed", 0, 0, Group.HARVEST, false);
    /** Reach has no floor worth naming: less reach is still reach. */
    public static final ArmoryProperty BLOCK_REACH = of("block_reach", 0, ArmoryCompute.NO_FLOOR, Group.HARVEST, false);
    public static final ArmoryProperty ATTACK_DAMAGE = of("attack_damage", 0, 0, Group.ATTACK, false);
    /** Vanilla's base attack speed is 4; below -3.9 the weapon would never finish a swing. */
    public static final ArmoryProperty ATTACK_SPEED = of("attack_speed", 0, -3.9D, Group.ATTACK, false);
    public static final ArmoryProperty ATTACK_REACH = of("attack_reach", 0, ArmoryCompute.NO_FLOOR, Group.ATTACK, false);
    public static final ArmoryProperty MAGIC_DAMAGE = of("magic_damage", 0, 0, Group.ATTACK, false);
    public static final ArmoryProperty RANGED_DAMAGE = of("ranged_damage", 0, 0, Group.PROJECTILE, false);
    /** Below -10 a bow would take longer than forever to draw. */
    public static final ArmoryProperty DRAW_SPEED = of("draw_speed", 0, -10.0D, Group.PROJECTILE, false);
    public static final ArmoryProperty PROJECTILE_SPEED = of("projectile_speed", 1, 0, Group.PROJECTILE, false);
    public static final ArmoryProperty PROJECTILE_ACCURACY = of("projectile_accuracy", 1, 0, Group.PROJECTILE, false);
    public static final ArmoryProperty ARMOR = of("armor", 0, 0, Group.ARMOR, false);
    public static final ArmoryProperty ARMOR_TOUGHNESS = of("armor_toughness", 0, 0, Group.ARMOR, false);
    /** Vanilla's knockback resistance is a fraction of knockback: a percentage. */
    public static final ArmoryProperty KNOCKBACK_RESISTANCE = new ArmoryProperty(
            "knockback_resistance", 0, 0, 0, Group.ARMOR, false, true);
    public static final ArmoryProperty MAGIC_ARMOR = of("magic_armor", 0, 0, Group.ARMOR, false);
    /**
     * Not a number gear carries: a material marked additive (redstone,
     * glowstone, a crushed shulker shell) may only go into a part alongside
     * something else. Above zero is "additive".
     */
    public static final ArmoryProperty ADDITIVE = of("additive", 0, 0, Group.GENERAL, false);

    /** Every property, in the order a tooltip lists them. */
    public static final List<ArmoryProperty> ALL = List.of(
            DURABILITY, ARMOR_DURABILITY, REPAIR_EFFICIENCY, REPAIR_VALUE, ENCHANTMENT_VALUE,
            CHARGING_VALUE, RARITY, HARVEST_SPEED, BLOCK_REACH, ATTACK_DAMAGE, ATTACK_SPEED,
            ATTACK_REACH, MAGIC_DAMAGE, RANGED_DAMAGE, DRAW_SPEED, PROJECTILE_SPEED,
            PROJECTILE_ACCURACY, ARMOR, ARMOR_TOUGHNESS, KNOCKBACK_RESISTANCE, MAGIC_ARMOR, ADDITIVE);

    /** The property with this id, or null. */
    public static ArmoryProperty byId(String id) {
        for (ArmoryProperty p : ALL) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** This property's value from these modifiers: the pipeline, floored. */
    public double compute(List<ArmoryModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return Math.max(defaultValue, floor);
        }
        return ArmoryCompute.compute(base, modifiers, floor);
    }
}
