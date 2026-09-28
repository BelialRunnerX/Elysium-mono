package com.elysium.space.entity.mob;

import com.elysium.space.registry.SpaceEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * The Pale Moon's villagers (Stellaris's alien). A villager in every way that
 * matters, professions, trades, breeding, gossip, with three differences: they
 * never summon iron golems, their children are moonfolk, and they shake their
 * heads (Stellaris's keyframe animation) when they refuse a trade.
 */
public class Moonfolk extends Villager implements Animated {

    private final AnimationState shake = new AnimationState();
    private int lastUnhappy;

    public Moonfolk(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }

    @Override
    public void spawnGolemIfNeeded(ServerLevel level, long gameTime, int minVillagers) {
        // No iron golems on the moon: the settlements guard themselves.
    }

    @Override
    public Villager getBreedOffspring(ServerLevel level, AgeableMob other) {
        Moonfolk child = SpaceEntities.MOONFOLK.get().create(level);
        if (child != null) {
            child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.BREEDING, null);
        }
        return child;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            int unhappy = getUnhappyCounter();
            if (unhappy > 0 && lastUnhappy == 0) {
                shake.start(tickCount);
            } else if (unhappy == 0) {
                shake.stop();
            }
            lastUnhappy = unhappy;
        }
    }

    @Override
    public AnimationState animation(String state) {
        return "shake".equals(state) ? shake : null;
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return "moonfolk";
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
