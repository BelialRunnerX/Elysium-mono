package com.elysium.lib.ability.act;

import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.ElysiumAbilityShapes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one place an ability actually happens.
 *
 * <h2>Six branches, and a rule that there may not be a seventh</h2>
 *
 * What goes wrong with ability systems is that they grow one bespoke branch
 * per ability, until there are thirty of them, nothing can be said about the
 * set, and adding the thirty-first means reading all thirty. So there are six
 * branches here — one per shape — and every number they use comes out of
 * {@link ElysiumAbilityShapes}, which derives it from what the ability is
 * already worth.
 *
 * <p>The consequence worth stating: <b>this class has no table in it.</b>
 * Nothing here knows that a stormcall is a stormcall. It knows that some
 * abilities are thrown, and how far and how hard this one is, and both of
 * those are answered by arithmetic a harness runs.
 */
public final class ElysiumAbilityUse {

    private ElysiumAbilityUse() {
    }

    /**
     * Do whatever this ability does.
     *
     * @param ability what to do
     * @param user    who is doing it
     * @return whether anything happened, which is what decides if it goes on
     *         cooldown and whether the cost is taken
     */
    public static boolean perform(ElysiumAbilityRules.Ability ability, Player user) {
        if (ability == null || user == null || user.level().isClientSide) {
            return false;
        }
        String shape = ElysiumAbilityShapes.shapeOf(ability.id());
        return switch (shape) {
            case ElysiumAbilityShapes.SELF -> onSelf(ability, user);
            case ElysiumAbilityShapes.TOUCH -> onTouch(ability, user);
            case ElysiumAbilityShapes.AREA -> around(ability, user);
            case ElysiumAbilityShapes.BOLT -> thrown(ability, user);
            case ElysiumAbilityShapes.SUMMON -> calledUp(ability, user);
            case ElysiumAbilityShapes.STEP -> stepped(ability, user);
            default -> false;
        };
    }

    /** An effect on the user, for as long as the ability lasts. */
    private static boolean onSelf(ElysiumAbilityRules.Ability ability, Player user) {
        int lasts = Math.max(1, ElysiumAbilityShapes.lastsOf(ability));
        user.addEffect(new MobEffectInstance(effectFor(ability), lasts,
                strengthOf(ability)));
        sound(user);
        return true;
    }

    /** An effect on whatever the user is looking at within reach. */
    private static boolean onTouch(ElysiumAbilityRules.Ability ability, Player user) {
        LivingEntity target = nearest(user, ElysiumAbilityShapes.reachOf(ability));
        if (target == null) {
            return false;
        }
        return hit(ability, user, target);
    }

    /** Everything within reach, the user excepted. */
    private static boolean around(ElysiumAbilityRules.Ability ability, Player user) {
        float reach = ElysiumAbilityShapes.reachOf(ability);
        List<LivingEntity> found = user.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(user.blockPosition()).inflate(reach),
                other -> other != user && other.isAlive());
        boolean any = false;
        for (LivingEntity target : found) {
            any |= hit(ability, user, target);
        }
        if (any) {
            sound(user);
        }
        return any;
    }

    /** As many bolts as the ability is worth, thrown where the user is looking. */
    private static boolean thrown(ElysiumAbilityRules.Ability ability, Player user) {
        EntityType<?> type = ElysiumAbilityEntities.bolt();
        if (type == null || !(user.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 look = user.getLookAngle();
        int count = ElysiumAbilityShapes.countOf(ability);
        for (int at = 0; at < count; at++) {
            Entity bolt = type.create(level);
            if (bolt == null) {
                return false;
            }
            bolt.setPos(user.getEyePosition().add(look));
            bolt.setDeltaMovement(look.scale(1.0D + at * 0.1D));
            level.addFreshEntity(bolt);
        }
        sound(user);
        return count > 0;
    }

    /** As many shades as the ability is worth, stood beside the user. */
    private static boolean calledUp(ElysiumAbilityRules.Ability ability, Player user) {
        EntityType<?> type = ElysiumAbilityEntities.summon();
        if (type == null || !(user.level() instanceof ServerLevel level)) {
            return false;
        }
        int count = ElysiumAbilityShapes.countOf(ability);
        for (int at = 0; at < count; at++) {
            Entity shade = type.create(level);
            if (shade == null) {
                return false;
            }
            shade.setPos(user.getX() + at - count / 2.0D, user.getY(), user.getZ());
            level.addFreshEntity(shade);
        }
        sound(user);
        return count > 0;
    }

    /** The user, as far along their own gaze as the ability reaches. */
    private static boolean stepped(ElysiumAbilityRules.Ability ability, Player user) {
        float reach = ElysiumAbilityShapes.reachOf(ability);
        Vec3 to = user.position().add(user.getLookAngle().scale(reach));
        if (!user.level().getBlockState(
                net.minecraft.core.BlockPos.containing(to)).isAir()) {
            return false;
        }
        user.teleportTo(to.x, to.y, to.z);
        sound(user);
        return true;
    }

    // ------------------------------------------------------------------

    /** One target, hurt or slowed according to what the ability is about. */
    private static boolean hit(ElysiumAbilityRules.Ability ability, Player user,
                               LivingEntity target) {
        float hurt = ElysiumAbilityShapes.hurtOf(ability);
        if (hurt > 0.0F) {
            target.hurt(user.damageSources().playerAttack(user), hurt);
            return true;
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                Math.max(1, ElysiumAbilityShapes.lastsOf(ability)),
                strengthOf(ability)));
        return true;
    }

    /**
     * Which effect a self ability gives, from its domain.
     *
     * <p>From the domain rather than from a table of ability names, so a
     * thirty-first ability in an existing domain needs nothing added here.
     */
    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>
            effectFor(ElysiumAbilityRules.Ability ability) {
        return switch (ability.domain()) {
            case "mobility" -> MobEffects.MOVEMENT_SPEED;
            case "defence", "poise" -> MobEffects.DAMAGE_RESISTANCE;
            case "offence" -> MobEffects.DAMAGE_BOOST;
            case "breath" -> MobEffects.WATER_BREATHING;
            case "vision" -> MobEffects.NIGHT_VISION;
            case "stealth" -> MobEffects.INVISIBILITY;
            case "healing", "rescue" -> MobEffects.REGENERATION;
            case "handling", "gathering" -> MobEffects.DIG_SPEED;
            default -> MobEffects.LUCK;
        };
    }

    /** How strong an effect is, from where the ability sits on the ladder. */
    private static int strengthOf(ElysiumAbilityRules.Ability ability) {
        return Math.round(ElysiumAbilityShapes.fraction(ability) * 2.0F);
    }

    private static LivingEntity nearest(Player user, float reach) {
        List<LivingEntity> found = user.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(user.blockPosition()).inflate(reach),
                other -> other != user && other.isAlive());
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity one : found) {
            double away = one.distanceToSqr(user);
            if (away < closest) {
                closest = away;
                best = one;
            }
        }
        return best;
    }

    private static void sound(Player user) {
        Level level = user.level();
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.6F, 1.0F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT, user.getX(),
                    user.getY() + 1.0D, user.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.02D);
        }
    }
}
