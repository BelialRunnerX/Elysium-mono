package com.elysium.armory.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;

/**
 * The ranged gear. Each is the vanilla class, so the game draws, charges and
 * throws it exactly as it always has; what changes is where the numbers come
 * from.
 *
 * <p>Draw speed and arrow damage are not handled here at all. They are written
 * onto the stack as the library's own {@code draw_speed} and {@code
 * arrow_damage} attributes, which P1 already reads for every bow in the game —
 * so a modular bow is fast for the same reason an enchanted one is, and the two
 * stack without either knowing about the other. What is left for these classes
 * is the flight of the shot: how fast it leaves and how true.
 */
public final class ArmoryRangedItems {

    private ArmoryRangedItems() {
    }

    /** Speed and accuracy, from the gear's properties, applied to a shot as it is loosed. */
    static void shape(LivingEntity shooter, Projectile projectile, ItemStack weapon, float[] velocityAndSpread) {
        ArmoryGearStats stats = ArmoryGearLogic.statsOf(weapon);
        double speed = stats.get("projectile_speed", 1.0D);
        double accuracy = stats.get("projectile_accuracy", 1.0D);
        if (speed > 0.0D) {
            velocityAndSpread[0] *= (float) speed;
        }
        if (accuracy > 0.0D) {
            velocityAndSpread[1] /= (float) accuracy;
        }
        if (projectile instanceof AbstractArrow arrow) {
            double ranged = stats.get("ranged_damage", 0.0D);
            if (ranged > 0.0D) {
                arrow.setBaseDamage(arrow.getBaseDamage() + ranged);
            }
        }
    }

    public static class Bow extends BowItem implements ArmoryGear {

        private final String gearType;

        public Bow(String gearType, Properties properties) {
            super(properties.stacksTo(1));
            this.gearType = gearType;
        }

        @Override
        public String gearType() {
            return gearType;
        }

        @Override
        protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity,
                                       float inaccuracy, float angle, LivingEntity target) {
            float[] shot = {velocity, inaccuracy};
            shape(shooter, projectile, shooter.getUseItem(), shot);
            super.shootProjectile(shooter, projectile, index, shot[0], shot[1], angle, target);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            ArmoryGearLogic.tick(this, stack, level);
        }
    }

    public static class Crossbow extends CrossbowItem implements ArmoryGear {

        public Crossbow(Properties properties) {
            super(properties.stacksTo(1));
        }

        @Override
        public String gearType() {
            return "crossbow";
        }

        @Override
        protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity,
                                       float inaccuracy, float angle, LivingEntity target) {
            float[] shot = {velocity, inaccuracy};
            shape(shooter, projectile, shooter.getUseItem(), shot);
            super.shootProjectile(shooter, projectile, index, shot[0], shot[1], angle, target);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            ArmoryGearLogic.tick(this, stack, level);
        }
    }

    public static class Trident extends TridentItem implements ArmoryGear {

        public Trident(Properties properties) {
            super(properties.stacksTo(1));
        }

        @Override
        public String gearType() {
            return "trident";
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            ArmoryGearLogic.tick(this, stack, level);
        }
    }

    public static class Rod extends FishingRodItem implements ArmoryGear {

        public Rod(Properties properties) {
            super(properties.stacksTo(1));
        }

        @Override
        public String gearType() {
            return "fishing_rod";
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            ArmoryGearLogic.tick(this, stack, level);
        }
    }

    /** A modular arrow: its head and fletching decide what it hits for and how straight it flies. */
    public static class Arrow extends ArrowItem implements ArmoryGear {

        public Arrow(Properties properties) {
            super(properties);
        }

        @Override
        public String gearType() {
            return "arrow";
        }

        @Override
        public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter, ItemStack weapon) {
            AbstractArrow arrow = super.createArrow(level, stack, shooter, weapon);
            ArmoryGearStats stats = ArmoryGearLogic.statsOf(stack);
            double ranged = stats.get("ranged_damage", 0.0D);
            if (ranged > 0.0D) {
                arrow.setBaseDamage(arrow.getBaseDamage() + ranged);
            }
            return arrow;
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            ArmoryGearLogic.tick(this, stack, level);
        }
    }
}
