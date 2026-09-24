package com.elysium.arsenal.item;

import com.elysium.arsenal.rules.WeaponRules;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * One weapon out of {@link WeaponRules}, with its numbers on it.
 *
 * <h2>Why the numbers are not typed here</h2>
 *
 * Because the one thing this module has to guarantee is a property of all
 * thirty-six weapons together — that no two of them are the same weapon and no
 * one of them is beaten on every axis by another — and a number typed into an
 * item is a number the arithmetic has never seen. Everything below is read out
 * of the rules the harness sweeps.
 *
 * <h2>The two things a sword cannot do</h2>
 *
 * <p><b>Sweep</b> is not vanilla's sweeping attack, which is an enchantment
 * and hits everything for a fraction. It is a count: a greatsword takes three,
 * and the other two are hit for the same as the first.
 *
 * <p><b>Pierce</b> is the estoc's whole reason to exist, and it is split at
 * the source rather than added on top. The attribute carries only the share
 * that armour may stop; the rest arrives as a separate hit that armour does
 * not. Against a bare target the two halves come to exactly the damage the
 * rules declare, so a piercing weapon is not simply a stronger one.
 */
public class WeaponItem extends SwordItem {

    /** Our own armour-bypassing hit, so a death message says what happened. */
    public static final ResourceKey<DamageType> PIERCING = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("elysiumarsenal", "piercing"));

    private final WeaponRules.Weapon weapon;

    public WeaponItem(Tier tier, String tierName, WeaponRules.Weapon kind,
                      Properties properties) {
        super(tier, properties.attributes(attributes(kind, tierName)));
        this.weapon = WeaponRules.at(kind, tierName);
    }

    /** What this one is, in the rules' own terms. */
    public WeaponRules.Weapon weapon() {
        return weapon;
    }

    /**
     * Damage, speed and reach, straight off the rules.
     *
     * <p>The game states attack damage as a bonus over one and attack speed as
     * a penalty from four, which is a convention rather than a fact, so the
     * conversion happens here and only here.
     */
    private static ItemAttributeModifiers attributes(WeaponRules.Weapon kind,
                                                     String tierName) {
        WeaponRules.Weapon made = WeaponRules.at(kind, tierName);
        float stoppable = made.damage() * (1.0F - made.pierce());
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                stoppable - 1.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID,
                                made.speed() - 4.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (made.reach() > 0.0F) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath("elysiumarsenal",
                                    "weapon_reach"),
                            made.reach(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    /** How far to one side a sweep finds its extra targets. */
    public static final double SWEEP_AROUND = 1.0D;

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity holder) {
        boolean hit = super.hurtEnemy(stack, target, holder);
        if (!(holder.level() instanceof ServerLevel level)) {
            return hit;
        }
        pierce(level, target, holder);
        if (weapon.sweep() > 1) {
            sweep(level, target, holder);
        }
        return hit;
    }

    /**
     * The half of the damage armour does not stop.
     *
     * <p>Nothing at all when the weapon does not pierce, which is five of the
     * six: a check that only ever runs for one weapon is a check nobody
     * notices has broken.
     */
    private void pierce(ServerLevel level, LivingEntity target, LivingEntity holder) {
        if (weapon.pierce() <= 0.0F) {
            return;
        }
        Holder<DamageType> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PIERCING);
        target.hurt(new DamageSource(type, holder),
                weapon.damage() * weapon.pierce());
    }

    /**
     * The extra targets, if this is a weapon that takes more than one.
     *
     * <p>Counted rather than area-swept: the rules say how many, so the code
     * takes that many and stops. An area that happens to contain four things
     * would make a greatsword's number a suggestion.
     */
    private void sweep(ServerLevel level, LivingEntity target, LivingEntity holder) {
        AABB around = target.getBoundingBox().inflate(SWEEP_AROUND);
        List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, around,
                other -> other != target && other != holder && other.isAlive()
                        && !other.isAlliedTo(holder));
        int taken = 0;
        for (LivingEntity other : others) {
            if (taken >= weapon.sweep() - 1) {
                break;
            }
            other.hurt(level.damageSources().mobAttack(holder),
                    weapon.damage() * (1.0F - weapon.pierce()));
            taken++;
        }
    }
}
