package com.elysium.space.entity.mob;

import com.elysium.space.world.MobRules;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * The moon caves' four-armed crawler (Stellaris's star crawler): slow, heavy
 * hitting, and plated against arrows, so it has to be met up close.
 */
public class VoidCrawler extends Monster implements Animated {

    private final AnimationState attack = new AnimationState();

    public VoidCrawler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.8D, false));
        goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity direct = source.getDirectEntity();
        if (direct != null && MobRules.crawlerDeflects(direct.getClass().getSimpleName())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        level().broadcastEntityEvent(this, (byte) 4);
        return super.doHurtTarget(target);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            attack.start(tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public AnimationState animation(String state) {
        return "attack".equals(state) ? attack : null;
    }



    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return "void_crawler";
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ElysiumVoices.ambient(voice(), super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ElysiumVoices.hurt(voice(), super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ElysiumVoices.death(voice(), super.getDeathSound());
    }

    @Override
    public float getVoicePitch() {
        return ElysiumVoices.pitchOf(this);
    }
}
