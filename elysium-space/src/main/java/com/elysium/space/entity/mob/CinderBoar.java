package com.elysium.space.entity.mob;

import com.elysium.space.registry.SpaceEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.Level;
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Cinderveil's boar (Stellaris's mogler): a hoglin that is at home on its
 * world, so it never zombifies outside the Nether, breeds true, and does not
 * despawn. Plays Stellaris's charge animation when it strikes (the hoglin's
 * own attack event, 4).
 */
public class CinderBoar extends Hoglin implements Animated {

    private final AnimationState attack = new AnimationState();

    public CinderBoar(EntityType<? extends Hoglin> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }

    /**
     * Never a zoglin. The constructor said so, and the hoglin's own load
     * read the saved flag back over it - false, for a boar summoned or loaded
     * from a chunk - so a cinder boar turned within a Nether day's ticks of
     * standing in the overworld (the mechanics survey, 27 September 2026).
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

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        CinderBoar child = SpaceEntities.CINDER_BOAR.get().create(level);
        if (child != null) {
            child.setPersistenceRequired();
        }
        return child;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            attack.start(tickCount);
        }
        super.handleEntityEvent(id);
    }

    @Override
    public AnimationState animation(String state) {
        return "attack".equals(state) ? attack : null;
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return "cinder_boar";
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
