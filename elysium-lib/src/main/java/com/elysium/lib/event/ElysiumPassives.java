package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * Where the engine asks a character's race and class what they do.
 *
 * This was a switch statement over the six races and nine classes the mod
 * happened to ship, which meant a race added by another mod was inert. It is
 * now a set of questions put to whatever passives a character has, so a race
 * nobody here has heard of works exactly as well as the built-in ones.
 *
 * Every method is a pure function of the player and the situation, called from
 * the combat and tick handlers rather than subscribing separately, so the order
 * of effects stays visible in one place instead of depending on which listener
 * registered first. Fall damage is the exception: it has its own event and
 * nothing to interleave with.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumPassives {

    private ElysiumPassives() {
    }

    // ------------------------------------------------------------------
    // Offence
    // ------------------------------------------------------------------

    /** Multiplier on outgoing melee damage — every passive's answer multiplied. */
    public static float attackScale(Player attacker, LivingEntity victim) {
        return ElysiumCharacter.passiveProduct(attacker,
                passive -> passive.attackScale(attacker, victim));
    }

    /**
     * What a critical hit is worth.
     *
     * The best answer wins rather than the product: two passives that each make
     * criticals hurt should give the better of the two, not their multiple.
     */
    public static float critMultiplier(Player attacker) {
        return ElysiumCharacter.passiveMax(attacker,
                passive -> passive.critMultiplier(attacker), 1.5F);
    }

    // ------------------------------------------------------------------
    // Defence
    // ------------------------------------------------------------------

    /** Multiplier on incoming damage, applied after Resilience. */
    public static float defenceScale(Player defender, DamageSource source) {
        return ElysiumCharacter.passiveProduct(defender,
                passive -> passive.defenceScale(defender, source));
    }

    /**
     * The total share of a blow a defender sends back.
     *
     * The Retribution stat, then every passive's own share, combined
     * proportionally rather than added — so the total approaches 1.0 and cannot
     * pass it, and no clamp is needed at the call site. A passive may also
     * multiply the result, which is how "double whatever you already reflect"
     * is expressed without being able to exceed the whole.
     */
    public static float totalReflectShare(Player defender) {
        float share = ElysiumStats.reflectShare(defender);
        share = ElysiumStats.combine(share, ElysiumCharacter.passiveShare(defender,
                passive -> passive.reflectShare(defender)));
        float multiplier = ElysiumCharacter.passiveProduct(defender,
                passive -> passive.reflectMultiplier(defender));
        return ElysiumStats.clampShare(share * multiplier);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        float ignored = 0.0F;
        for (ElysiumPassive passive : ElysiumCharacter.passives(player)) {
            ignored = Math.max(ignored, passive.fallDistanceIgnored(player));
        }
        if (ignored > 0.0F) {
            event.setDistance(Math.max(0.0F, event.getDistance() - ignored));
        }
        float scale = ElysiumCharacter.passiveProduct(player,
                passive -> passive.fallDamageScale(player));
        if (scale != 1.0F) {
            event.setDamageMultiplier(event.getDamageMultiplier() * scale);
        }
    }

    // ------------------------------------------------------------------
    // Body, standing, work
    // ------------------------------------------------------------------

    public static float regenScale(Player player) {
        return ElysiumCharacter.passiveProduct(player, passive -> passive.regenScale(player));
    }

    public static float shieldScale(Player player) {
        return ElysiumCharacter.passiveProduct(player, passive -> passive.shieldScale(player));
    }

    public static void serverTick(Player player) {
        for (ElysiumPassive passive : ElysiumCharacter.passives(player)) {
            passive.onServerTick(player);
        }
    }

    /** Presence, then whatever the race and class think of it. */
    public static float favorScale(Player player) {
        return ElysiumStats.presenceScale(player) * ElysiumCharacter.passiveProduct(
                player, passive -> passive.favorScale(player));
    }

    public static float suspicionScale(Player player) {
        return ElysiumStats.presenceScale(player) * ElysiumCharacter.passiveProduct(
                player, passive -> passive.suspicionScale(player));
    }

    /** How fast standing bleeds off. One point per tick unless something says otherwise. */
    public static int decayRate(Player player) {
        return Math.max(1, ElysiumCharacter.passiveSum(
                player, passive -> passive.decayRate(player), 1));
    }

    /** Luck, then any passive that adds to it — combined, never summed. */
    public static float extraDropChance(Player player) {
        return ElysiumStats.combine(
                ElysiumStats.luckChance(player),
                ElysiumCharacter.passiveShare(player, passive -> passive.extraDropChance(player)));
    }

    public static float reforgeScale(Player player) {
        return ElysiumStats.presenceScale(player) * ElysiumCharacter.passiveProduct(
                player, passive -> passive.reforgeScale(player));
    }

    public static boolean savesDurability(Player player) {
        return ElysiumCharacter.passiveAny(player, passive -> passive.savesDurability(player));
    }

    public static boolean doublesOre(Player player) {
        return ElysiumCharacter.passiveAny(player, passive -> passive.doublesOre(player));
    }

    // ------------------------------------------------------------------
    // Moments
    // ------------------------------------------------------------------

    /**
     * Tells every passive that this player killed something.
     *
     * Its own listener rather than a call from the standing handler, which also
     * watches deaths. The two want different things: standing decides a kill
     * does not count in some situations, and a trinket whose whole text is "on
     * kill, do X" should not quietly stop working because of a rule about
     * Favor. Independent listeners, independent reasons.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)
                || killer.level().isClientSide()) {
            return;
        }
        for (ElysiumPassive passive : ElysiumCharacter.passives(killer)) {
            passive.onKill(killer, event.getEntity());
        }
    }

    /** Tells every passive what just landed on this player. */
    public static void damaged(Player defender, DamageSource source, float amount) {
        for (ElysiumPassive passive : ElysiumCharacter.passives(defender)) {
            passive.onDamaged(defender, source, amount);
        }
    }

    /**
     * The share of this blow avoided outright.
     *
     * Proportional, so three sources of 20% come to 49% rather than 60%, and no
     * stack of trinkets reaches immunity.
     */
    public static float dodgeChance(Player defender, DamageSource source) {
        return ElysiumCharacter.passiveShare(defender,
                passive -> passive.dodgeChance(defender, source));
    }

    /** A share of damage dealt returned to the attacker as health. */
    public static float lifestealShare(Player attacker, LivingEntity victim) {
        return ElysiumCharacter.passiveShare(attacker,
                passive -> passive.lifestealShare(attacker, victim));
    }

    /** A multiplier on Elysium experience gained. */
    public static float xpScale(Player player) {
        return ElysiumCharacter.passiveProduct(player, passive -> passive.xpScale(player));
    }
}
