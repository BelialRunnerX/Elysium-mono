package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.KinEntity;
import com.elysium.bestiary.rules.KindredRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one place a creature's one difference from its kin happens.
 *
 * <h2>One branch per trait, and no creature named</h2>
 *
 * The fourth file in this module built that way, and the argument has not
 * changed: a branch per <em>thing done</em> rather than per creature. Eleven
 * branches is a thing somebody reads in one sitting and a validator can check
 * the shape of; twenty-seven classes is twenty-seven places for the same bug,
 * and a twenty-eighth creature is a twenty-eighth file.
 *
 * <p>Here the rule earns its keep more than anywhere else in the module,
 * because this roster is the one that grows by variant. The moment a branch
 * says {@code "rimebarb"}, the next one says {@code "cinderbarb"},
 * and what was eleven fights becomes twenty-seven near-copies of four. No
 * creature is named anywhere below, and {@code validate.py} reads this file and
 * fails if it finds one.
 *
 * <h2>Why none of these is simply a bigger hit</h2>
 *
 * Because a bigger hit is what {@code tier} is for, and the beasts already do
 * it better. Every branch below changes what the player has to <em>do</em> —
 * where they stand, what they look at, what they bring, whether backing off
 * works — and a trait that changed only a number would leave the whole file
 * proving nothing.
 */
public final class KinTraits {

    private KinTraits() {
    }

    /** How long between one of these and the next, in ticks. */
    public static final int BETWEEN = 80;

    /** How far a trait that does not need contact reaches. */
    public static final double REACHES = 10.0D;

    /** How long an effect one of these leaves lasts, in ticks. */
    public static final int LINGERS = 120;

    /** How many of a kindred have to be near before a howl is worth anything. */
    public static final int A_PACK = 2;

    /** How far up and along a leap carries. */
    public static final double LEAP_ALONG = 0.9D;

    /** And how far up. */
    public static final double LEAP_UP = 0.42D;

    /**
     * The trait that happens on its own clock.
     *
     * <p>Handed the row rather than reading it, so a control can hand one built
     * to be caught.
     */
    public static void happen(KinEntity self, KindredRules.Kin row) {
        Level level = self.level();
        switch (row.trait()) {
            case KindredRules.SUMMONS -> {
                // Killing it first is the difference between one fight and
                // four, which is a decision the player only gets to make if
                // the calling is something they can see happen.
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            self.getX(), self.getEyeY(), self.getZ(),
                            3, 0.3D, 0.3D, 0.3D, 0.0D);
                }
                self.callOne();
            }
            case KindredRules.HOWLS -> {
                // What it does to the others near it is worse than what it
                // does to you. It is worth nothing on its own, which is the
                // point: the answer is to not let it have a pack.
                List<KinEntity> pack = kin(self, row);
                if (pack.size() >= A_PACK) {
                    for (KinEntity other : pack) {
                        other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                                LINGERS, 0, true, false));
                        other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                                LINGERS, 0, true, false));
                    }
                }
            }
            case KindredRules.BURROWS -> {
                // It goes under, so line of sight is not a thing you have.
                if (level instanceof ServerLevel server && !self.hasVisiblePlayer()) {
                    server.sendParticles(ParticleTypes.CRIT,
                            self.getX(), self.getY(), self.getZ(),
                            6, 0.4D, 0.1D, 0.4D, 0.0D);
                    self.goUnder();
                }
            }
            case KindredRules.THROWS -> {
                // It hits from where you cannot, so cover matters more than
                // armour does.
                for (Player near : nearby(self)) {
                    if (self.hasLineOfSight(near) && self.distanceTo(near) > 4.0D) {
                        self.throwSomething(near);
                    }
                }
            }
            case KindredRules.LEAPS -> {
                // Backing off does not open the distance, because it closes
                // it in one.
                LivingEntity after = self.getTarget();
                if (after != null && self.onGround()
                        && self.distanceTo(after) > 4.0D) {
                    Vec3 toward = new Vec3(after.getX() - self.getX(), 0.0D,
                            after.getZ() - self.getZ()).normalize();
                    self.setDeltaMovement(toward.x * LEAP_ALONG, LEAP_UP,
                            toward.z * LEAP_ALONG);
                }
            }
            case KindredRules.DISGUISES -> {
                // It is not what it looks like, so looking is the skill. It
                // stops pretending the moment it is noticed, because a
                // disguise that survives being seen through is not a puzzle.
                self.setDisguised(!self.hasVisiblePlayer());
            }
            case KindredRules.CARRIES -> {
                // The answer to it is to get on it, so it has to still be
                // there when the player decides that.
                if (level instanceof ServerLevel server && self.getPassengers().isEmpty()) {
                    server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            self.getX(), self.getY() + 1.0D, self.getZ(),
                            1, 0.3D, 0.3D, 0.3D, 0.0D);
                }
            }
            case KindredRules.POISONS, KindredRules.FREEZES,
                 KindredRules.IGNITES, KindredRules.DRAINS -> {
                // These four land on contact rather than on a clock, and a
                // branch that pretended otherwise would be a branch that hit
                // the player from across the room for no reason they could see.
            }
            default -> {
            }
        }
    }

    /**
     * And the traits that land when it hits you.
     *
     * <p>Split from {@link #happen} rather than folded into it because "what it
     * does when it reaches you" and "what it does while it is coming" are
     * different questions, and a creature whose danger only happens on a timer
     * is one the player learns to walk away from.
     */
    public static void onContact(KinEntity self, LivingEntity hurt,
                                 KindredRules.Kin row) {
        switch (row.trait()) {
            case KindredRules.POISONS -> hurt.addEffect(new MobEffectInstance(
                    MobEffects.POISON, LINGERS, 0));
            case KindredRules.FREEZES -> {
                hurt.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        LINGERS, 2));
                hurt.setTicksFrozen(hurt.getTicksFrozen() + LINGERS);
            }
            case KindredRules.IGNITES -> hurt.igniteForSeconds(4);
            case KindredRules.DRAINS -> {
                // It takes what you were going to use to heal, which armour
                // does not protect and a bigger weapon does not answer.
                if (hurt instanceof Player fed) {
                    fed.getFoodData().setFoodLevel(
                            Math.max(0, fed.getFoodData().getFoodLevel() - 2));
                }
                hurt.addEffect(new MobEffectInstance(MobEffects.HUNGER, LINGERS, 1));
            }
            case KindredRules.SUMMONS, KindredRules.HOWLS, KindredRules.BURROWS,
                 KindredRules.THROWS, KindredRules.LEAPS, KindredRules.DISGUISES,
                 KindredRules.CARRIES -> {
                // These seven happen on the clock above, where the player can
                // see them coming and do something about them.
            }
            default -> {
            }
        }
    }

    /** Everybody near enough for this to be about them. */
    private static List<Player> nearby(KinEntity self) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(Player.class, around,
                player -> !player.isSpectator());
    }

    /** The others of its own kindred within reach, itself included. */
    private static List<KinEntity> kin(KinEntity self, KindredRules.Kin row) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(KinEntity.class, around,
                other -> other.isAlive() && other.creature().kin().equals(row.kin()));
    }

    /** Whether a mob of this module's own is the same creature as another. */
    public static boolean sameCreature(Entity one, Entity other) {
        return EntityType.getKey(one.getType()).equals(EntityType.getKey(other.getType()));
    }

    /** Whether something is a mob at all, for the branches that need one. */
    public static boolean isMob(Entity what) {
        return what instanceof Mob;
    }
}
