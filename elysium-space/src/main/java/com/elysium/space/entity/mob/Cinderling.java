package com.elysium.space.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Cinderveil's natives (Stellaris's pygro): piglins at home on their world.
 * They barter, hunt and ally as piglins do, and never zombify outside the
 * Nether. Kept when far away, since they live in their settlements.
 */
public class Cinderling extends Piglin {

    public Cinderling(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }

    /**
     * Never zombified. The constructor says so, and the piglin's own load reads
     * the saved flag back over it - false for one summoned or put down by a
     * structure - so without these a cinderling summoned off Cinderveil turned
     * as a piglin does (the cinder boar's fault, 27 September 2026; found again
     * here on 17 October 2026).
     */
    @Override
    public boolean isConverting() {
        return false;
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setImmuneToZombification(true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return "cinderling";
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
