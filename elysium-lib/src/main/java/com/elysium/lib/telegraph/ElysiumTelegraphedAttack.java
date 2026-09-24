package com.elysium.lib.telegraph;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * A melee attack that announces itself before it lands.
 *
 * <h2>Why a goal and not a mixin or a tick handler</h2>
 *
 * The moment a mob decides to swing is inside its attack goal, and nowhere
 * else. A tick handler watching from outside would have to guess — near the
 * player, facing them, cooldown probably up — and it would guess wrong every
 * time a mob was interrupted, which is the moment a false warning is most
 * confusing. Extending the goal means the warning is raised by the same code
 * that will land the blow, so the two cannot disagree.
 *
 * <h2>What it actually changes</h2>
 *
 * Only the timing. The mob still walks, still paths, still swings on the same
 * interval; it simply raises the warning some ticks before the blow rather than
 * at the moment of it. That is deliberate — a telegraph that also nerfed the
 * attack would be two changes wearing one name, and the balance argument would
 * get made about the wrong one.
 *
 * <h2>Why the wind-up is read from the mob's own attack damage</h2>
 *
 * So that a mob which is dangerous gets a long warning and one which is not
 * gets none, without anybody maintaining a table. A mob scaled up by
 * {@code ElysiumScaling} earns a longer warning the moment it is scaled,
 * because the warning is a function of what it will actually hit for.
 */
public class ElysiumTelegraphedAttack extends MeleeAttackGoal {

    public ElysiumTelegraphedAttack(PathfinderMob mob, double speed,
                                    boolean followEvenIfNotSeen) {
        super(mob, speed, followEvenIfNotSeen);
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        // How long before the swing this mob should be shouting. Recomputed
        // each tick rather than cached: a mob's attack damage changes when it
        // is scaled, equipped or buffed, and a warning sized on the wrong
        // number is the exact failure this whole system exists to prevent.
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int windup = ElysiumTelegraph.windupFor(damage);
        if (windup > 0 && getTicksUntilNextAttack() == windup
                && canPerformAttack(target)) {
            // Exactly once, on the tick the wind-up begins. Announcing on every
            // tick of the approach would turn the warning into a continuous
            // noise, which a player stops hearing — the opposite of readable.
            ElysiumWarnings.begin(mob, damage);
        }
        super.checkAndPerformAttack(target);
    }
}
