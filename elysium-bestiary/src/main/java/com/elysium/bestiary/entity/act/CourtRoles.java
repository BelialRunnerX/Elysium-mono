package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.CourtEntity;
import com.elysium.bestiary.registry.CourtEntities;
import com.elysium.bestiary.rules.CourtRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The one place a creature's place in its court happens.
 *
 * <h2>One branch per role, and no creature named</h2>
 *
 * The sixth file in this module built that way, and on the biggest roster yet.
 * Seven branches for fifty-eight creatures, because a court is seven jobs done
 * by however many bodies the court has — and a branch per creature here would
 * be fifty-eight places for one bug, on a roster where the same job is done by
 * nineteen different things.
 *
 * <p>No creature is named anywhere below, and {@code validate.py} reads this
 * file and fails if it finds one.
 *
 * <h2>Why the herald asks for room</h2>
 *
 * Because the rule that holds a court under its head counts what can be
 * <em>standing at once</em>, and a herald that called without a cap would make
 * that number unbounded — at which point the proof is about a table and the
 * game is about something else. The cap is the row's {@code most}, asked for
 * rather than typed.
 */
public final class CourtRoles {

    private CourtRoles() {
    }

    /** How long between one of these and the next, in ticks. */
    public static final int BETWEEN = 60;

    /** How far a role that does not need contact reaches. */
    public static final double REACHES = 12.0D;

    /** How long an effect one of these leaves lasts, in ticks. */
    public static final int LINGERS = 100;

    /** How much a healer puts back each time it acts. */
    public static final float MENDS = 2.0F;

    /** How much of a hit a shield takes instead of its head. */
    public static final float SHIELDS = 0.5F;

    /**
     * The role that happens on its own clock.
     *
     * <p>Handed the row rather than reading it, so a control can hand one built
     * to be caught.
     */
    public static void happen(CourtEntity self, CourtRules.Member row) {
        Level level = self.level();
        switch (row.role()) {
            case CourtRules.HERALD -> {
                // The cap is the row's own. Without it the court's budget is
                // unbounded and the rule that holds it under the head is a rule
                // about a table rather than about the game.
                if (self.roomFor() > 0 && level instanceof ServerLevel server) {
                    callOne(self, row, server);
                }
            }
            case CourtRules.HEALER -> {
                for (CourtEntity kin : court(self, row)) {
                    if (kin.member().head() && kin.getHealth() < kin.getMaxHealth()) {
                        kin.heal(MENDS);
                        if (level instanceof ServerLevel server) {
                            server.sendParticles(ParticleTypes.HEART, kin.getX(),
                                    kin.getEyeY(), kin.getZ(), 1,
                                    0.3D, 0.3D, 0.3D, 0.0D);
                        }
                    }
                }
            }
            case CourtRules.ARTILLERY -> {
                for (Player near : nearby(self)) {
                    if (self.hasLineOfSight(near) && self.distanceTo(near) > 5.0D) {
                        throwAt(self, near);
                    }
                }
            }
            case CourtRules.SHIELD -> {
                // It stands between you and the head, which is a position
                // rather than an effect: it walks toward whatever is nearest
                // its head rather than toward the player.
                for (CourtEntity kin : court(self, row)) {
                    if (kin.member().head()) {
                        self.getNavigation().moveTo(kin.getX(), kin.getY(),
                                kin.getZ(), 1.0D);
                    }
                }
            }
            case CourtRules.MOUNT -> {
                for (CourtEntity kin : court(self, row)) {
                    if (kin.member().head() && self.getPassengers().isEmpty()
                            && self.distanceTo(kin) < 4.0D) {
                        kin.startRiding(self);
                    }
                }
            }
            case CourtRules.HEAD -> {
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.ANGRY_VILLAGER, self.getX(),
                            self.getEyeY() + 0.4D, self.getZ(), 2,
                            0.4D, 0.2D, 0.4D, 0.0D);
                }
            }
            case CourtRules.SWARM -> {
                // Nothing on a clock. A swarm's whole character is that there
                // are several and none of them is the problem, and a branch
                // that gave each one something to do would make every one of
                // them the problem.
            }
            default -> {
            }
        }
    }

    /** And what lands when it reaches you. */
    public static void onContact(CourtEntity self, LivingEntity hurt,
                                 CourtRules.Member row) {
        switch (row.role()) {
            case CourtRules.SWARM -> hurt.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, LINGERS, 0));
            case CourtRules.HEAD -> hurt.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, LINGERS, 0));
            case CourtRules.SHIELD -> hurt.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, LINGERS, 0));
            case CourtRules.HERALD, CourtRules.HEALER, CourtRules.ARTILLERY,
                 CourtRules.MOUNT -> {
                // These four do their job away from the player, and a fifth
                // thing that also hit hard would make the whole court melee.
            }
            default -> {
            }
        }
    }

    /**
     * What happens the once, when a two-phased head turns.
     *
     * <p>Separate from {@link #happen} because it is not a role, it is a moment
     * — and folding it into the clock would mean a head that turned every time
     * it was asked.
     */
    public static void turns(CourtEntity self, CourtRules.Member row) {
        Level level = self.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(ParticleTypes.SOUL, self.getX(), self.getEyeY(),
                self.getZ(), 24, 0.6D, 0.6D, 0.6D, 0.02D);
        for (Player near : nearby(self)) {
            near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, LINGERS, 0));
        }
    }

    /** One more of the court, which is one of its own kind. */
    private static void callOne(CourtEntity self, CourtRules.Member row,
                                ServerLevel server) {
        EntityType<?> mine = self.getType();
        Entity made = mine.create(server);
        if (made instanceof CourtEntity fresh) {
            fresh.moveTo(self.getX() + (self.getRandom().nextDouble() - 0.5D) * 4.0D,
                    self.getY(),
                    self.getZ() + (self.getRandom().nextDouble() - 0.5D) * 4.0D,
                    self.getYRot(), 0.0F);
            fresh.calledOut();
            server.addFreshEntity(fresh);
            self.counted();
        }
    }

    /** Something thrown, for the ones that fight from where the head is not. */
    private static void throwAt(CourtEntity self, LivingEntity at) {
        Level level = self.level();
        if (level.isClientSide()) {
            return;
        }
        Snowball thrown = new Snowball(level, self);
        double up = at.getEyeY() - 1.1D - thrown.getY();
        double along = at.getX() - self.getX();
        double across = at.getZ() - self.getZ();
        double flat = Math.sqrt(along * along + across * across);
        thrown.shoot(along, up + flat * 0.2D, across, 1.3F, 8.0F);
        level.addFreshEntity(thrown);
    }

    /** Everybody near enough for this to be about them. */
    private static List<Player> nearby(CourtEntity self) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(Player.class, around,
                player -> !player.isSpectator());
    }

    /** The others of its own court within reach. */
    private static List<CourtEntity> court(CourtEntity self, CourtRules.Member row) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(CourtEntity.class, around,
                other -> other.isAlive() && other != self
                        && other.member().court().equals(row.court()));
    }

    /** How many of a court are registered at all, so nothing here counts by hand. */
    public static int registered() {
        return CourtEntities.ALL.size();
    }
}
