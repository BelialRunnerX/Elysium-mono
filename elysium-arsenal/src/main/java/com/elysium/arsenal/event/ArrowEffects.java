package com.elysium.arsenal.event;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.WeaponItem;
import com.elysium.arsenal.rules.RangedRules;
import com.elysium.lib.combat.ElysiumEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.List;

/**
 * What an arrow does beyond arriving.
 *
 * <h2>Why this is an event and not an entity</h2>
 *
 * The obvious shape is twelve projectile entities, one per kind of shot, each
 * overriding the hook the game provides. It is also wrong twice over. It needs
 * twelve entity types, twelve renderers and twelve spawn registrations to do
 * what one lookup does; and, worse, it only works for arrows <em>we</em> fire.
 * A skeleton that picks up a rime arrow, a dispenser loaded with ember arrows,
 * an arrow fired from the game's own bow — none of those build our entity, and
 * all of them carry our item.
 *
 * <p>So the question this asks is not "which entity is this" but "what was
 * this arrow made of", which is on the arrow whoever fired it:
 * {@code getPickupItemStackOrigin()} is the stack the shot came out of.
 *
 * <h2>The one thing that is not an effect</h2>
 *
 * A piercing shot is split at the source exactly the way the estoc's is: the
 * arrow's own damage is not increased, and the share that armour would stop
 * arrives instead as a second, armour-bypassing hit. An arrow that simply hit
 * harder would be a better arrow rather than a different one, and the
 * arithmetic that proves no shot dominates another would be proving it about
 * numbers the game never used.
 */
@EventBusSubscriber(modid = ElysiumArsenal.MODID)
public final class ArrowEffects {

    private ArrowEffects() {
    }

    /** How long a shot's effect lasts, in ticks. */
    public static final int EFFECT_TICKS = 100;

    /** How far a thunder arrow reaches for its second target. */
    public static final double CHAIN_REACH = 5.0D;

    /** What the second target of a chain takes, as a share of the first. */
    public static final float CHAIN_SHARE = 0.5F;

    /** The share of a piercing shot that armour is not allowed to stop. */
    public static final float PIERCE_SHARE = 0.5F;

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        RangedRules.Shot shot = shotBehind(arrow.getPickupItemStackOrigin());
        if (shot == null || shot.effect().isEmpty()) {
            return;
        }
        apply(level, shot, event.getEntity(), arrow, source, event.getNewDamage());
    }

    /**
     * Which of the twelve this arrow was, or null for one of the game's own.
     *
     * <p>By registered name rather than by identity, because the item the
     * arrow carries has been through a network packet and a save file and is a
     * copy of a copy.
     */
    public static RangedRules.Shot shotBehind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !ElysiumArsenal.MODID.equals(id.getNamespace())) {
            return null;
        }
        for (RangedRules.Shot shot : RangedRules.SHOTS) {
            if (shot.name().equals(id.getPath())) {
                return shot;
            }
        }
        return null;
    }

    private static void apply(ServerLevel level, RangedRules.Shot shot,
                              LivingEntity hit, AbstractArrow arrow,
                              DamageSource source, float dealt) {
        switch (shot.effect()) {
            case "bleed" -> hit.addEffect(new MobEffectInstance(
                    ElysiumEffects.BLEEDING, EFFECT_TICKS, 0));
            case "sundering" -> hit.addEffect(new MobEffectInstance(
                    ElysiumEffects.SUNDERING, EFFECT_TICKS, 0));
            case "slowing" -> hit.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 1));
            case "marking" -> hit.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, EFFECT_TICKS, 0));
            case "burning" -> hit.igniteForSeconds(EFFECT_TICKS / 20.0F);
            case "piercing" -> pierce(level, hit, arrow, dealt);
            case "chaining" -> chain(level, hit, arrow, dealt);
            default -> { }
        }
    }

    /**
     * The half armour does not stop.
     *
     * <p>Worked out from what the hit actually took rather than from the
     * arrow's stated damage, because the stated damage is before armour,
     * resistance, shields and every other mod in the pack — and a share of a
     * number nothing used is a number nobody can reason about.
     */
    private static void pierce(ServerLevel level, LivingEntity hit,
                               AbstractArrow arrow, float dealt) {
        if (dealt <= 0.0F) {
            return;
        }
        Holder<net.minecraft.world.damagesource.DamageType> type =
                level.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(WeaponItem.PIERCING);
        Entity shooter = arrow.getOwner();
        hit.hurt(new DamageSource(type, arrow, shooter), dealt * PIERCE_SHARE);
    }

    /**
     * One arc, to one other thing, once.
     *
     * <p>One rather than "everything nearby": a shot that hits a herd for half
     * each is a shot that clears a room, and the arithmetic that says no shot
     * dominates another has only ever compared single targets. The arc also
     * refuses to come back to what it just left, which is the difference
     * between an effect and a loop.
     */
    private static void chain(ServerLevel level, LivingEntity hit,
                              AbstractArrow arrow, float dealt) {
        if (dealt <= 0.0F) {
            return;
        }
        Entity shooter = arrow.getOwner();
        AABB reach = hit.getBoundingBox().inflate(CHAIN_REACH);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, reach,
                other -> other != hit && other != shooter && other.isAlive()
                        && other.attackable());
        if (near.isEmpty()) {
            return;
        }
        LivingEntity nearest = near.get(0);
        double best = nearest.distanceToSqr(hit);
        for (LivingEntity other : near) {
            double gap = other.distanceToSqr(hit);
            if (gap < best) {
                best = gap;
                nearest = other;
            }
        }
        nearest.hurt(level.damageSources().indirectMagic(arrow, shooter),
                dealt * CHAIN_SHARE);
    }
}
