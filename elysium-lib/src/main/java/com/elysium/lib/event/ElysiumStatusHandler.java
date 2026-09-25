package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributeValues;
import com.elysium.lib.combat.ElysiumCombatRules;
import com.elysium.lib.combat.ElysiumEffects;
import com.elysium.lib.entity.ElysiumSummons;
import com.elysium.lib.nihil.ElysiumNull;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Valoria's status effects and combat attributes, read where they matter.
 *
 * <ul>
 *   <li><b>Stunned</b> stops a creature attacking, using an item or working a
 *       block; its slow is on the effect itself.</li>
 *   <li><b>Exhaustion</b> and <b>Renewal</b> scale healing, through the
 *       tested rules rather than a second copy of the arithmetic.</li>
 *   <li><b>miss_chance</b> is the attacker's own roll, the mirror of dodge:
 *       a blow that never lands, announced to a player who swung it.</li>
 *   <li><b>Elemental damage</b>: an attacker's {@code <element>_damage} is
 *       added to the blow, less the share the target's
 *       {@code <element>_resistance} and {@code element_resistance} stop.
 *       {@code null_damage} is the same, resisted by {@code null_resistance}
 *       alone, and every point of it that lands also fills the target's
 *       gauge.</li>
 *   <li><b>summon_damage</b> scales what an owned creature deals by its
 *       owner's multiplier.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumStatusHandler {

    private ElysiumStatusHandler() {
    }

    /** Each element's damage row and the resistance row that answers it. */
    private static final String[][] ELEMENTAL = {
            {"void_damage", "void_resistance"},
            {"plasma_damage", "plasma_resistance"},
            {"neural_damage", "neural_resistance"},
            {"dimensional_damage", "dimensional_resistance"},
            {"kinetic_damage", "kinetic_resistance"},
    };

    // --- stunned ----------------------------------------------------------

    private static boolean stunned(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(ElysiumEffects.STUNNED);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // --- healing ----------------------------------------------------------

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        float amount = event.getAmount();
        MobEffectInstance exhaustion = entity.getEffect(ElysiumEffects.EXHAUSTION);
        if (exhaustion != null) {
            amount *= ElysiumCombatRules.exhaustionScale(exhaustion.getAmplifier() + 1);
        }
        MobEffectInstance renewal = entity.getEffect(ElysiumEffects.RENEWAL);
        if (renewal != null) {
            amount *= ElysiumCombatRules.renewalScale(renewal.getAmplifier() + 1);
        }
        event.setAmount(amount);
    }

    // --- the blow ---------------------------------------------------------

    /**
     * A stunned attacker's blow never lands, and neither does a missed one.
     * Highest priority so nothing downstream spends a crit or a lifesteal on
     * a hit that did not happen.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlowThrown(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (stunned(attacker)) {
            event.setCanceled(true);
            return;
        }
        if (ElysiumAttributeValues.has(attacker, "miss_chance")) {
            float miss = (float) ElysiumAttributeValues.chance(attacker, "miss_chance");
            if (miss > 0.0F && attacker.getRandom().nextFloat() < miss) {
                event.setCanceled(true);
                if (attacker instanceof Player player) {
                    player.displayClientMessage(Component.translatable("elysium.combat.missed"), true);
                }
            }
        }
    }

    /** Elemental bonuses and the summoner's share, added to a blow that is landing. */
    @SubscribeEvent
    public static void onBlowLanding(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        float amount = event.getAmount();
        if (attacker instanceof OwnableEntity owned && owned.getOwner() instanceof LivingEntity owner
                && ElysiumAttributeValues.has(owner, "summon_damage")) {
            amount *= (float) ElysiumSummons.damageMultiplier(owner);
        }
        // Player against player is a duel, not a matchup: the elemental
        // bonuses stay out of it, as Valoria kept them out.
        if (!(attacker instanceof Player && victim instanceof Player)) {
            float all = (float) ElysiumAttributeValues.chance(victim, "element_resistance");
            for (String[] element : ELEMENTAL) {
                if (!ElysiumAttributeValues.has(attacker, element[0])) {
                    continue;
                }
                float flat = (float) ElysiumAttributeValues.flat(attacker, element[0]);
                float resisted = (float) ElysiumAttributeValues.chance(victim, element[1]);
                amount += ElysiumCombatRules.elementalBonus(flat, resisted, all);
            }
            if (ElysiumAttributeValues.has(attacker, "null_damage")) {
                float flat = (float) ElysiumAttributeValues.flat(attacker, "null_damage");
                float resisted = (float) ElysiumAttributeValues.chance(victim, "null_resistance");
                float landed = ElysiumCombatRules.elementalBonus(flat, resisted, 0.0F);
                amount += landed;
                if (victim instanceof Player player && !player.isCreative()) {
                    ElysiumNull.add(player, landed);
                }
            }
        }
        event.setAmount(amount);
    }
}
