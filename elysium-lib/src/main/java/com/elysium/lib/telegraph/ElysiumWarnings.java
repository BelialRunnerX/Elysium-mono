package com.elysium.lib.telegraph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is currently winding up, and since when.
 *
 * <h2>Why this is not saved</h2>
 *
 * A warning lives for at most a second and a quarter. Writing it to disk would
 * mean a field on every hostile mob in the world, a codec, and a migration, to
 * preserve something that is meaningless the moment the chunk reloads — a mob
 * that unloads mid-swing has not swung, and should start again.
 *
 * <h2>Why the record is the mob and not the blow</h2>
 *
 * A mob winds up once and then hits. Keyed by the attacker, a second wind-up
 * replaces the first, which is right: a monster that starts a new swing has
 * abandoned the old one. Keying by the pair would let a mob accumulate
 * warnings, one per player in the room, and then spend them all on whoever it
 * reached first.
 *
 * <h2>Server thread only</h2>
 *
 * Bounded and in access order, like the heat, room and exposure caches, for the
 * same reason: one caller, and a LinkedHashMap in access order is not safe for
 * two.
 */
public final class ElysiumWarnings {

    private ElysiumWarnings() {
    }

    /**
     * How many attackers are tracked at once.
     *
     * A warning that falls out of the map reads as no warning, which caps the
     * blow — so the failure mode of running out of room is a monster that hits
     * softly, not one that hits unfairly. That is the right way round, and it
     * is why the capacity can be modest.
     */
    public static final int CAPACITY = 256;

    private record Warning(long startedAt, float damage) {
    }

    private static final Map<UUID, Warning> WARNINGS =
            new LinkedHashMap<>(CAPACITY, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Warning> eldest) {
                    return size() > CAPACITY;
                }
            };

    /**
     * Announce that this attacker is about to do something worth avoiding.
     *
     * Plays a sound and throws up particles at the mob, both of which are the
     * actual readability — the record is only how the rule is enforced
     * afterwards. A warning nobody can see or hear would satisfy every check in
     * this file and none of the point.
     *
     * @return the ticks of warning this blow has earned, or 0 if it needed none
     */
    public static int begin(LivingEntity attacker, float damage) {
        if (attacker == null || attacker.level().isClientSide()) {
            return 0;
        }
        int windup = ElysiumTelegraph.windupFor(damage);
        if (windup <= 0) {
            return 0;
        }
        WARNINGS.put(attacker.getUUID(),
                new Warning(attacker.level().getGameTime(), damage));

        attacker.level().playSound(null, attacker.getX(), attacker.getY(),
                attacker.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.HOSTILE, 0.7F,
                // Higher for a smaller monster, so the pitch says roughly how
                // much is coming before the player has worked out which mob it
                // was. A single pitch for everything would be a noise rather
                // than information.
                1.5F - Math.min(0.7F, windup / (float) ElysiumTelegraph.MAXIMUM_WINDUP));

        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    attacker.getX(), attacker.getEyeY() + 0.4D, attacker.getZ(),
                    1 + windup / 8, 0.25D, 0.15D, 0.25D, 0.0D);
        }
        return windup;
    }

    /**
     * How long this attacker's current warning has been showing.
     *
     * Zero for an attacker with no warning, and zero for one whose warning is
     * older than the longest wind-up there is — a mob that announced a blow and
     * then wandered off for ten seconds has not announced the blow it is
     * landing now.
     */
    public static int showingFor(LivingEntity attacker) {
        if (attacker == null) {
            return 0;
        }
        Warning warning = WARNINGS.get(attacker.getUUID());
        if (warning == null) {
            return 0;
        }
        long age = attacker.level().getGameTime() - warning.startedAt();
        if (age < 0 || age > ElysiumTelegraph.MAXIMUM_WINDUP) {
            return 0;
        }
        return (int) age;
    }

    /** Forget an attacker's warning. Called when the blow lands. */
    public static void spend(LivingEntity attacker) {
        if (attacker != null) {
            WARNINGS.remove(attacker.getUUID());
        }
    }

    /** Forget everything. For a world unload, and for the tests. */
    public static void clear() {
        WARNINGS.clear();
    }

    /** How many warnings are being held. Reported, and checked. */
    public static int size() {
        return WARNINGS.size();
    }
}
