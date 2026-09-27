package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.registry.BeastEntities;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.BehaviourRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one place a beast's mechanic happens.
 *
 * <h2>Why there is one switch and not nine methods on nine classes</h2>
 *
 * The same argument the ability system settled: a branch per <em>verb</em>
 * rather than per creature. Nine branches is a thing somebody can read in one
 * sitting and a validator can check the shape of; nine classes is nine places
 * for the same bug and a tenth beast is a tenth file.
 *
 * <p>No beast is named anywhere below. If one ever is, the module has started
 * growing a branch per creature and the whole roster will follow it there —
 * which is why {@code validate.py} reads this file and fails if it finds one.
 */
public final class BeastActs {

    private BeastActs() {
    }

    /** How many of its own family one beast may have called up and alive. */
    public static final int MOST_CALLED = 2;

    /** How far ahead of you a drifting beast walks. */
    public static final double LOOKS_AHEAD = 8.0D;

    /** How many blocks a sundering beast takes out at once. */
    public static final int SUNDERS = 1;

    /**
     * Do whatever this beast does, if anything is in reach to do it to.
     *
     * @return whether it happened, so the caller can start the wait again
     */
    public static boolean perform(BeastEntity beast, LivingEntity at) {
        BeastRules.Beast row = beast.beast();
        String verb = BehaviourRules.verbOf(row.id());
        if (verb.isEmpty() || !(beast.level() instanceof ServerLevel level)) {
            return false;
        }
        List<BeastRules.Beast> all = BeastRules.BEASTS;
        float strength = BehaviourRules.strengthOf(row, all);
        float reach = BehaviourRules.reachOf(row);
        return switch (verb) {
            case BehaviourRules.SPLIT -> comesApart(beast, level, strength);
            case BehaviourRules.LEAP -> jumps(beast, at, reach);
            case BehaviourRules.STALK -> holdsStill(beast, at);
            case BehaviourRules.ANCHOR -> shoves(beast, at, reach, strength);
            case BehaviourRules.SUNDER -> goesThrough(beast, level);
            case BehaviourRules.CHARGE -> crosses(beast, at, reach, strength);
            case BehaviourRules.DRIFT -> walksAhead(beast, at);
            case BehaviourRules.CHILL -> cools(beast, at, reach, strength);
            case BehaviourRules.CALL -> callsKin(beast, level);
            default -> false;
        };
    }

    // ==================================================================

    /** It comes apart when you hit it and the pieces keep coming. */
    private static boolean comesApart(BeastEntity beast, ServerLevel level,
                                      float strength) {
        if (beast.isAlive() || beast.piece()) {
            return false;
        }
        int many = Math.max(1, Math.round(strength * 2.0F));
        for (int at = 0; at < many; at++) {
            BeastEntity piece = (BeastEntity) beast.getType().create(level);
            if (piece == null) {
                continue;
            }
            piece.moveTo(beast.getX(), beast.getY(), beast.getZ(),
                    level.random.nextFloat() * 360.0F, 0.0F);
            piece.becomeAPiece();
            level.addFreshEntity(piece);
        }
        return true;
    }

    /** Fast, and it has worked out that you are slower going uphill. */
    private static boolean jumps(BeastEntity beast, LivingEntity at, float reach) {
        if (at == null || !beast.onGround() || beast.distanceTo(at) > reach * 2.0F) {
            return false;
        }
        Vec3 toward = at.position().subtract(beast.position());
        if (toward.lengthSqr() < 1.0E-4D) {
            return false;
        }
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z).normalize().scale(0.55D);
        beast.setDeltaMovement(flat.x, 0.52D + Math.max(0.0D, toward.y) * 0.08D, flat.z);
        beast.hasImpulse = true;
        return true;
    }

    /** You will hear it stop following you. That is the bad part. */
    private static boolean holdsStill(BeastEntity beast, LivingEntity at) {
        if (at == null || !watchedBy(beast, at)) {
            return false;
        }
        beast.getNavigation().stop();
        beast.setDeltaMovement(0.0D, beast.getDeltaMovement().y, 0.0D);
        return true;
    }

    /** Whether the thing it is looking at is looking back. */
    private static boolean watchedBy(Mob beast, LivingEntity at) {
        Vec3 look = at.getViewVector(1.0F).normalize();
        Vec3 toward = beast.position().subtract(at.getEyePosition()).normalize();
        return look.dot(toward) > 0.85D && at.hasLineOfSight(beast);
    }

    /** Too heavy to knock back and too slow to run from. Pick one. */
    private static boolean shoves(BeastEntity beast, LivingEntity at, float reach,
                                  float strength) {
        if (at == null || beast.distanceTo(at) > reach) {
            return false;
        }
        Vec3 away = at.position().subtract(beast.position());
        if (away.lengthSqr() < 1.0E-4D) {
            return false;
        }
        away = new Vec3(away.x, 0.0D, away.z).normalize().scale(strength * 0.6D);
        at.push(away.x, 0.35D, away.z);
        at.hurtMarked = true;
        return true;
    }

    /** It goes through the wall rather than round it, every time. */
    private static boolean goesThrough(BeastEntity beast, ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        Direction facing = beast.getDirection();
        BlockPos ahead = beast.blockPosition().relative(facing);
        int broken = 0;
        for (int up = 0; up <= 1 && broken < SUNDERS; up++) {
            BlockPos where = ahead.above(up);
            if (level.getBlockState(where).isAir()
                    || level.getBlockState(where).getDestroySpeed(level, where) < 0.0F
                    || level.getBlockEntity(where) != null) {
                continue;
            }
            level.destroyBlock(where, true, beast);
            broken++;
        }
        return broken > 0;
    }

    /** Three metres of it. Bring the door down and leave. */
    private static boolean crosses(BeastEntity beast, LivingEntity at, float reach,
                                   float strength) {
        if (at == null || beast.distanceTo(at) > reach * 2.5F
                || beast.distanceTo(at) < reach * 0.5F) {
            return false;
        }
        Vec3 toward = at.position().subtract(beast.position());
        if (toward.lengthSqr() < 1.0E-4D) {
            return false;
        }
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z).normalize()
                .scale(0.35D + strength * 0.16D);
        beast.setDeltaMovement(flat.x, 0.15D, flat.z);
        beast.hasImpulse = true;
        return true;
    }

    /** It is not chasing you. It is going the same way you are. */
    private static boolean walksAhead(BeastEntity beast, LivingEntity at) {
        if (at == null) {
            return false;
        }
        Vec3 ahead = at.position().add(at.getDeltaMovement().scale(LOOKS_AHEAD));
        return beast.getNavigation().moveTo(ahead.x, ahead.y, ahead.z, 1.0D);
    }

    /** The noise arrives first and the cold arrives with it. */
    private static boolean cools(BeastEntity beast, LivingEntity at, float reach,
                                 float strength) {
        if (at == null || beast.distanceTo(at) > reach) {
            return false;
        }
        int ticks = Math.round(strength * 40.0F);
        at.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                Math.max(0, Math.round(strength) - 1)));
        return true;
    }

    /** It waited. Whatever it was waiting for, it was not you. */
    private static boolean callsKin(BeastEntity beast, ServerLevel level) {
        BeastRules.Beast row = beast.beast();
        List<BeastRules.Beast> family = BeastRules.family(row.family());
        if (family.isEmpty() || beast.called() >= MOST_CALLED) {
            return false;
        }
        BeastRules.Beast kin = family.get(0);
        for (BeastRules.Beast one : family) {
            if (one.tier() < row.tier() && one.tier() >= kin.tier()) {
                kin = one;
            }
        }
        EntityType<?> type = BeastEntities.ALL.get(kin.id()).get();
        BlockPos where = beast.blockPosition().relative(beast.getDirection(), 2);
        if (type.spawn(level, where, MobSpawnType.MOB_SUMMONED) == null) {
            return false;
        }
        beast.calledOneMore();
        return true;
    }
}
