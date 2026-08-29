package com.elysium.core.entity;

import com.elysium.core.Elysium;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An Imperial Enforcer — what the Empire sends when your Suspicion gets high
 * enough to be worth a visit.
 *
 * Built on {@link Zombie} deliberately. The Sleeping Empire does not raise new
 * soldiers; it wakes the ones it already has. Practically, it also means the
 * enforcer renders through vanilla's zombie renderer, which already draws worn
 * armour — so an enforcer in a full Elysium set reads as an enforcer, not as a
 * zombie, without the mod shipping a single line of rendering code.
 *
 * They do not burn in daylight and do not drown into anything else. Whatever
 * these were before, the Empire is not finished with them.
 */
public class ImperialEnforcer extends Zombie {

    public ImperialEnforcer(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createEnforcerAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    /**
     * Kits the enforcer out according to how badly the Empire wants you.
     *
     * The drop chances are low on purpose: an enforcer is a threat that
     * occasionally pays, not a vending machine for endgame armour. Killing them
     * also raises Suspicion, so farming them escalates the problem rather than
     * solving it.
     */
    public void equipForBand(int band) {
        setPersistenceRequired();

        Item weapon = switch (band) {
            case 3 -> Elysium.SINGULARITY_LANCE.get();
            case 2 -> Elysium.NEURAL_CASCADE_RIFLE.get();
            default -> Elysium.VOIDCUT_BLADE.get();
        };
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        setDropChance(EquipmentSlot.MAINHAND, band >= 3 ? 0.08F : 0.04F);

        // Higher bands arrive in more of the set, so an enforcer's silhouette
        // tells you how much trouble you are in before it reaches you.
        equipPiece(EquipmentSlot.HEAD, Elysium.NEUTRONIUM_HELMET.get(), band >= 1);
        equipPiece(EquipmentSlot.CHEST,
                band >= 3 ? Elysium.VOIDWEAVE_AEGIS.get() : Elysium.NEUTRONIUM_CHESTPLATE.get(),
                band >= 1);
        equipPiece(EquipmentSlot.LEGS, Elysium.NEUTRONIUM_LEGGINGS.get(), band >= 2);
        equipPiece(EquipmentSlot.FEET, Elysium.NEUTRONIUM_BOOTS.get(), band >= 2);
    }

    private void equipPiece(EquipmentSlot slot, Item item, boolean include) {
        if (!include) {
            return;
        }
        setItemSlot(slot, new ItemStack(item));
        setDropChance(slot, 0.03F);
    }
}
