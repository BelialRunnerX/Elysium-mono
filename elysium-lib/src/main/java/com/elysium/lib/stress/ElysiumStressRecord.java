package com.elysium.lib.stress;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The one thing about stress that is remembered: how bad it recently got.
 *
 * <h2>Why anything is saved at all</h2>
 *
 * Because Recovery is a direction rather than a level, and a direction needs
 * two points. Everything else about stress is derived from the world every time
 * it is asked for — see {@link ElysiumStressor} — and this is the deliberate
 * exception, kept as small as it can be: a band and a timestamp.
 *
 * <h2>Why it is not a stress score in disguise</h2>
 *
 * It only ever moves one way. A worse band overwrites it; a better one does
 * not, and nothing decays it. What ends a recovery is the window in
 * {@link ElysiumStress#read} expiring, which is a question asked at read time
 * rather than a tick doing bookkeeping. That means a villager in an unloaded
 * chunk recovers on schedule without anything having to run.
 */
public final class ElysiumStressRecord {

    private ElysiumStressRecord() {
    }

    /**
     * @param band the worst band reached lately
     * @param when the game time it was reached
     */
    public record Peak(String band, long when) {

        public static final Codec<Peak> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.STRING.fieldOf("band").forGetter(Peak::band),
                        Codec.LONG.fieldOf("when").forGetter(Peak::when)
                ).apply(instance, Peak::new));

        /** None yet. A villager who has never had a bad moment. */
        public static final Peak NONE = new Peak("", 0L);

        /** The band, or null for one this build no longer has. */
        public ElysiumStressBand resolve() {
            for (ElysiumStressBand candidate : ElysiumStressBand.values()) {
                if (candidate.id().equals(band)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<Peak>> PEAK =
            ATTACHMENTS.register("stress_peak", () ->
                    AttachmentType.builder(() -> Peak.NONE)
                            .serialize(Peak.CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /**
     * Read somebody's stress, and update the remembered peak if this is one.
     *
     * The two happen together because they cannot be allowed to drift: a peak
     * recorded by a different code path from the one that reads it is a peak
     * that will eventually describe a moment the readout never showed.
     */
    public static ElysiumStress.Reading readingFor(LivingEntity subject) {
        if (!(subject instanceof Villager villager)
                || subject.level().isClientSide()) {
            return new ElysiumStress.Reading(0, ElysiumStressBand.STABLE,
                    java.util.List.of());
        }
        Set<ElysiumStressor> reasons = ElysiumStressField.reasonsFor(villager);
        Peak peak = villager.getData(PEAK.get());
        long now = villager.level().getGameTime();
        ElysiumStressBand remembered = peak.resolve();

        ElysiumStress.Reading reading = ElysiumStress.read(reasons, remembered,
                remembered == null ? Long.MAX_VALUE : now - peak.when());

        if (ElysiumStress.isNewPeak(reading.band(), remembered)) {
            villager.setData(PEAK.get(), new Peak(reading.band().id(), now));
        }
        return reading;
    }
}
