package com.elysium.lib.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * What a creature, a machine or a station says, asked by name.
 *
 * <p>An entity overrides its three sound methods and answers with
 * {@link #ambient}, {@link #hurt} and {@link #death} for its voice; a voice
 * the table does not have falls back to the game's own default for that
 * class, so a misspelt name is a quiet creature rather than a crash — and the
 * validator refuses the misspelling. A machine's ticker calls {@link #hum}
 * every tick while it runs and the hum is replayed on the table's cadence; a
 * station calls {@link #click} when it opens.
 */
public final class ElysiumVoices {

    private ElysiumVoices() {
    }

    public static SoundEvent ambient(String voice, SoundEvent otherwise) {
        SoundEvent event = ElysiumSounds.ambient(voice);
        return event != null ? event : otherwise;
    }

    public static SoundEvent hurt(String voice, SoundEvent otherwise) {
        SoundEvent event = ElysiumSounds.hurt(voice);
        return event != null ? event : otherwise;
    }

    public static SoundEvent death(String voice, SoundEvent otherwise) {
        SoundEvent event = ElysiumSounds.death(voice);
        return event != null ? event : otherwise;
    }

    /**
     * The pitch a creature's calls are played at: a young one higher, and a
     * touch of variation so a pen of six is six animals rather than one.
     */
    public static float pitchOf(Mob mob) {
        float young = mob.isBaby() ? 1.5F : 1.0F;
        return young * (0.94F + 0.12F * mob.getRandom().nextFloat());
    }

    /**
     * Keep a machine humming: call every server tick it runs. The clip is a
     * loop a hair longer than {@link ElysiumSoundRules#HUM_EVERY} ticks, so
     * replaying it on that cadence is one continuous hum, and a machine that
     * stops simply stops calling.
     */
    public static void hum(Level level, BlockPos pos, String hum) {
        if (level.isClientSide() || (level.getGameTime() + pos.hashCode()) % ElysiumSoundRules.HUM_EVERY != 0) {
            return;
        }
        SoundEvent event = ElysiumSounds.hum(hum);
        if (event != null) {
            level.playSound(null, pos, event, SoundSource.BLOCKS, ElysiumSoundRules.HUM_VOLUME, 1.0F);
        }
    }

    /** A station opening under a player's hand. */
    public static void click(Level level, BlockPos pos, String click) {
        if (level.isClientSide()) {
            return;
        }
        SoundEvent event = ElysiumSounds.click(click);
        if (event != null) {
            level.playSound(null, pos, event, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
    }
}
