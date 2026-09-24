package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributeValues;
import com.elysium.lib.attribute.ElysiumDamageTypes;
import com.elysium.lib.combat.ElysiumCombatRules;
import com.elysium.lib.combat.ElysiumEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What the combat attributes do outside the damage pipeline in
 * {@link ElysiumCombatHandler}: the auxiliary melee hits (AA-4), the ranged
 * attributes (AA-7) and the utility ones (AA-8).
 *
 * <p>Two things are worth knowing about the auxiliary hits. They are dealt
 * <em>after</em> the blow that caused them, as separate damage with its own
 * type, so armour, resistances and the elemental matrix all read them as what
 * they are — a burn, a chill, a wound that answers to how much the target has
 * left — rather than as more sword. And they bypass the invulnerability window
 * the first hit opened, because that window exists so one swing lands once, and
 * these are that same swing.
 *
 * <p>A player's swing strength is gone by the time damage is dealt (vanilla
 * resets the ticker before it hurts anything), so it is captured on
 * {@link AttackEntityEvent}, which fires first, and read back here.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumAttributeCombat {

    private ElysiumAttributeCombat() {
    }

    /** A swing's strength, captured before vanilla resets it. Weak keys: a logged-out player is forgotten. */
    private static final Map<UUID, Float> SWINGS = new WeakHashMap<>();

    /** Guards the auxiliary hits from causing more auxiliary hits. */
    private static final ThreadLocal<Boolean> AUXILIARY = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            SWINGS.put(player.getUUID(), player.getAttackStrengthScale(0.5F));
        }
    }

    /** How full the swing was: a player's ticker, or a full swing for anything that has no ticker. */
    private static double swingOf(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            Float strength = SWINGS.remove(player.getUUID());
            return strength == null ? 1.0D : strength;
        }
        return 1.0D;
    }

    /**
     * The auxiliary hits, after the blow they ride on. Physical melee only: a
     * bow does not set anything on fire, and neither does a burn.
     */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide() || AUXILIARY.get() || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.is(ElysiumDamageTypes.NON_PHYSICAL)) {
            return;
        }
        boolean any = ElysiumAttributeValues.has(attacker, "current_hp_damage")
                || ElysiumAttributeValues.has(attacker, "fire_damage")
                || ElysiumAttributeValues.has(attacker, "cold_damage");
        if (!any) {
            return;
        }
        double swing = swingOf(attacker);
        float current = ElysiumCombatRules.auxiliary(
                ElysiumCombatRules.currentHpDamage(target.getHealth(),
                        ElysiumAttributeValues.chance(attacker, "current_hp_damage")), swing);
        float fire = ElysiumCombatRules.auxiliary(ElysiumAttributeValues.flat(attacker, "fire_damage"), swing);
        float cold = ElysiumCombatRules.auxiliary(ElysiumAttributeValues.flat(attacker, "cold_damage"), swing);
        if (current <= 0.0F && fire <= 0.0F && cold <= 0.0F) {
            return;
        }
        AUXILIARY.set(Boolean.TRUE);
        try {
            if (current > 0.0F) {
                hurt(target, attacker, ElysiumDamageTypes.CURRENT_HP, current);
            }
            if (fire > 0.0F) {
                float dealt = hurt(target, attacker, ElysiumDamageTypes.FIRE, fire);
                target.setRemainingFireTicks(target.getRemainingFireTicks() + ElysiumCombatRules.fireTicks(dealt));
            }
            if (cold > 0.0F) {
                float dealt = hurt(target, attacker, ElysiumDamageTypes.COLD, cold);
                int ticks = ElysiumCombatRules.coldSlowTicks(dealt);
                if (ticks > 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                            ElysiumCombatRules.coldAmplifier(dealt)), attacker);
                }
            }
        } finally {
            AUXILIARY.set(Boolean.FALSE);
        }
    }

    /** One auxiliary hit, through the invulnerability window the first hit opened. Returns what landed. */
    private static float hurt(LivingEntity target, LivingEntity attacker, ResourceKey<DamageType> type, float amount) {
        float before = target.getHealth() + target.getAbsorptionAmount();
        int window = target.invulnerableTime;
        target.invulnerableTime = 0;
        target.hurt(ElysiumDamageTypes.source(target.level(), type, attacker), amount);
        target.invulnerableTime = window;
        return Math.max(0.0F, before - (target.getHealth() + target.getAbsorptionAmount()));
    }

    /**
     * Projectile damage (AA-7): a projectile hits for its owner's stat. Read
     * off the owner rather than the projectile so that a stat gained after the
     * arrow was loosed still counts, as Apotheosis has it.
     */
    @SubscribeEvent
    public static void onProjectileDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getSource().getDirectEntity() instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof LivingEntity owner)
                || !ElysiumAttributeValues.has(owner, "projectile_damage")) {
            return;
        }
        double multiplier = ElysiumAttributeValues.multiplier(owner, "projectile_damage");
        if (multiplier != 1.0D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** The marker that keeps an arrow from being sped up again every time its chunk reloads. */
    private static final String BOOSTED = "elysiumlib:boosted";

    /** Arrow damage and velocity (AA-7), applied once, when the arrow first enters the world. */
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof LivingEntity owner)
                || arrow.getPersistentData().getBoolean(BOOSTED)) {
            return;
        }
        arrow.getPersistentData().putBoolean(BOOSTED, true);
        if (ElysiumAttributeValues.has(owner, "arrow_damage")) {
            arrow.setBaseDamage(arrow.getBaseDamage() * ElysiumAttributeValues.multiplier(owner, "arrow_damage"));
        }
        if (ElysiumAttributeValues.has(owner, "arrow_velocity")) {
            double velocity = ElysiumAttributeValues.multiplier(owner, "arrow_velocity");
            if (velocity != 1.0D) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(velocity));
            }
        }
    }

    /** Draw speed (AA-7): extra use ticks for anything that is drawn or thrown. */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        LivingEntity user = event.getEntity();
        ItemStack stack = event.getItem();
        UseAnim anim = stack.getUseAnimation();
        if (user.level().isClientSide() || !ElysiumAttributeValues.has(user, "draw_speed")
                || (anim != UseAnim.BOW && anim != UseAnim.CROSSBOW && anim != UseAnim.SPEAR)) {
            return;
        }
        int extra = ElysiumCombatRules.drawExtraTicks(
                ElysiumAttributeValues.multiplier(user, "draw_speed"), event.getDuration());
        if (extra > 0) {
            event.setDuration(Math.max(1, event.getDuration() - extra));
        }
    }

    /**
     * Sundering (AA-9): a sundered target takes more of everything. Read here
     * rather than as an attribute, because what it changes is the blow, and the
     * blow is in hand.
     */
    @SubscribeEvent
    public static void onSundered(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance sundering = entity.getEffect(ElysiumEffects.SUNDERING);
        if (sundering != null && !entity.level().isClientSide()) {
            event.setAmount((float) (event.getAmount()
                    * (1.0D + ElysiumEffects.SUNDERING_PER_LEVEL * (sundering.getAmplifier() + 1))));
        }
    }

    /** Healing received (AA-8), and Grievous Wounds (AA-9), which is a share of the same heal. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance grievous = entity.getEffect(ElysiumEffects.GRIEVOUS);
        double wounds = grievous == null ? 0.0D
                : ElysiumCombatRules.levelShare(grievous.getAmplifier() + 1, ElysiumEffects.GRIEVOUS_POINTS_FOR_ALL);
        if (!ElysiumAttributeValues.has(entity, "healing_received") && wounds <= 0.0D) {
            return;
        }
        double multiplier = ElysiumAttributeValues.has(entity, "healing_received")
                ? ElysiumAttributeValues.multiplier(entity, "healing_received") : 1.0D;
        multiplier *= (1.0D - wounds);
        if (multiplier == 1.0D) {
            return;
        }
        float healed = (float) (event.getAmount() * multiplier);
        if (healed <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(healed);
        }
    }

    /** Experience gained (AA-8), from a kill. */
    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null || !ElysiumAttributeValues.has(player, "experience_gained")) {
            return;
        }
        event.setDroppedExperience((int) Math.round(event.getDroppedExperience()
                * ElysiumAttributeValues.multiplier(player, "experience_gained")));
    }

    /** Experience gained (AA-8), from a block. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || !ElysiumAttributeValues.has(player, "experience_gained")) {
            return;
        }
        event.setDroppedExperience((int) Math.round(event.getDroppedExperience()
                * ElysiumAttributeValues.multiplier(player, "experience_gained")));
    }

    /**
     * Elytra flight (AA-1's toggle): the wearer glides.
     *
     * <p>Vanilla only lets a player glide while a chest item says they may, and
     * it clears the flag every tick that item does not — so this re-asserts it
     * each tick rather than starting it once. A player with the stat glides
     * whenever they are falling clear of the ground, which is the closest thing
     * to "as if wearing an elytra" that needs no elytra: landing, water and
     * creative flight all end it, and a firework boosts it as usual.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !ElysiumAttributeValues.has(player, "elytra_flight")
                || !ElysiumAttributeValues.on(player, "elytra_flight")) {
            return;
        }
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger()
                || player.getAbilities().flying) {
            return;
        }
        if (player.isFallFlying() || player.getDeltaMovement().y < -0.1D) {
            player.startFallFlying();
        }
    }

    /** Vanilla's jump crit uses the crit_damage stat instead of its own 1.5 (AA-3). */
    @SubscribeEvent
    public static void onVanillaCrit(net.neoforged.neoforge.event.entity.player.CriticalHitEvent event) {
        Player player = event.getEntity();
        if (event.isVanillaCritical() && ElysiumAttributeValues.has(player, "crit_damage")) {
            event.setDamageMultiplier((float) ElysiumAttributeValues.multiplier(player, "crit_damage"));
        }
    }

}
