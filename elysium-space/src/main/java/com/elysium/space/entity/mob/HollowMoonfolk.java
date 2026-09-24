package com.elysium.space.entity.mob;

import com.elysium.space.world.MobRules;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Moonfolk the moon took back (Stellaris's alien zombie). Hunts players and
 * moonfolk, closing to strike or keeping its distance to spit rime; Stellaris's
 * goals and priorities, unchanged.
 */
public class HollowMoonfolk extends Monster implements RangedAttackMob {

    public HollowMoonfolk(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, MobRules.SPIT_INTERVAL, MobRules.SPIT_RANGE));
        goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Player.class, false, false));
        targetSelector.addGoal(6, new NearestAttackableTargetGoal<>(this, Moonfolk.class, false, false));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        RimeSpit.shoot(this, target);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }
}
