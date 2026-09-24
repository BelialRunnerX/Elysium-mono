package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributeValues;
import com.elysium.lib.combat.ElysiumCombatRules;
import com.elysium.lib.stats.ElysiumArmourRules;
import com.elysium.lib.stats.ElysiumAttributeLimits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Replaces vanilla's armour and Protection reductions for players with
 * {@link ElysiumArmourRules}: the same mechanics, no ceilings. Whoever is being
 * hit, the attacker's armour pierce and shred (and their Protection twins) take
 * the defence apart first, blunted by the defender's toughness — Apotheosis's
 * combat rules, through {@link ElysiumCombatRules}. A defender that is not a
 * player keeps vanilla's own curve, so a zombie in iron armour is exactly as
 * tough as vanilla made it until somebody pierces it.
 *
 * Through NeoForge's reduction modifiers, which receive vanilla's reduction and
 * return the one to use. The damage a modifier sees ({@code getNewDamage}) is
 * the damage before that reduction — confirmed from DamageContainer's bytecode,
 * which runs the modifiers and only then subtracts.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumArmourHandler {

    private ElysiumArmourHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) {
            return;
        }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        boolean piercing = attacker != null && (pierce(attacker, "armor_pierce") > 0.0D
                || shred(attacker, "armor_shred") > 0.0D || pierce(attacker, "prot_pierce") > 0.0D
                || shred(attacker, "prot_shred") > 0.0D);
        if (!(defender instanceof Player) && !piercing) {
            return;                         // nothing to change: vanilla's own numbers, untouched
        }
        event.addReductionModifier(DamageContainer.Reduction.ARMOR,
                (container, vanilla) -> armour(defender, attacker, container, vanilla));
        event.addReductionModifier(DamageContainer.Reduction.ENCHANTMENTS,
                (container, vanilla) -> protection(defender, attacker, container, vanilla));
    }

    private static double pierce(LivingEntity attacker, String name) {
        return ElysiumAttributeValues.has(attacker, name) ? ElysiumAttributeValues.flat(attacker, name) : 0.0D;
    }

    private static double shred(LivingEntity attacker, String name) {
        return ElysiumAttributeValues.has(attacker, name) ? ElysiumAttributeValues.chance(attacker, name) : 0.0D;
    }

    private static float armour(LivingEntity defender, LivingEntity attacker,
                                DamageContainer container, float vanilla) {
        DamageSource source = container.getSource();
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return vanilla;                 // vanilla gave nothing, and nor do we
        }
        float damage = container.getNewDamage();
        double armour = ElysiumAttributeLimits.uncapped(defender, Attributes.ARMOR);
        double toughness = ElysiumAttributeLimits.uncapped(defender, Attributes.ARMOR_TOUGHNESS);
        if (attacker != null) {
            armour = ElysiumCombatRules.piercedArmour(armour, shred(attacker, "armor_shred"),
                    pierce(attacker, "armor_pierce"), toughness);
        }
        // Armour driven below zero is not defence at all: the blow lands harder.
        if (armour < 0.0D) {
            return -(damage * (ElysiumCombatRules.negativeArmourFactor(armour) - 1.0F));
        }
        float share = defender instanceof Player
                ? ElysiumArmourRules.armourShare(ElysiumArmourRules.effectiveArmour(armour, toughness, damage))
                : ElysiumCombatRules.vanillaArmourShare(
                        ElysiumArmourRules.effectiveArmour(armour, toughness, damage));
        // Breach and its kin act on the fraction, as they do in vanilla.
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && defender.level() instanceof ServerLevel level) {
            share = Math.max(0.0F, Math.min(1.0F,
                    EnchantmentHelper.modifyArmorEffectiveness(level, weapon, defender, source, share)));
        }
        return damage * share;
    }

    private static float protection(LivingEntity defender, LivingEntity attacker,
                                    DamageContainer container, float vanilla) {
        DamageSource source = container.getSource();
        if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || !(defender.level() instanceof ServerLevel level)) {
            return vanilla;
        }
        double points = EnchantmentHelper.getDamageProtection(level, defender, source);
        if (attacker != null) {
            points = ElysiumCombatRules.piercedProtection(points, shred(attacker, "prot_shred"),
                    pierce(attacker, "prot_pierce"));
        }
        float share = defender instanceof Player ? ElysiumArmourRules.protectionShare(points)
                : (float) Math.min(0.8D, points * 0.04D);      // vanilla's own 4% a point, for anything else
        return container.getNewDamage() * share;
    }
}
