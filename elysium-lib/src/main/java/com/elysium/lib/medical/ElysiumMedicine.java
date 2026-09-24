package com.elysium.lib.medical;

/**
 * How wounds are taken and how they mend.
 *
 * <h2>Measured on what landed</h2>
 *
 * The opposite choice to scarring, and for the mirror of its reason. The wall
 * behind a player has no armour, so terrain is scarred by the blow as swung.
 * The player does have armour, and armour that did not protect against lasting
 * injury would be armour doing half its job — so a wound is rolled on the
 * damage that actually got through.
 *
 * <h2>Lasting, not permanent</h2>
 *
 * Every wound heals on its own, in twenty to thirty game minutes. The Med bay
 * is not the only cure; it is the <em>fast</em> one — four times the pace, and
 * eight asleep in one. That keeps the room a thing worth building rather than
 * a thing a player is locked out of the game until they have built.
 *
 * <p>Imports nothing, and must not: it is in {@code EXECUTED_PURE}.
 */
public final class ElysiumMedicine {

    private ElysiumMedicine() {
    }

    /** No single blow is more likely than not to leave a wound. */
    public static final float MAX_CHANCE = 0.5F;

    /** Chance added per point of landed damage at or past a kind's threshold. */
    public static final float CHANCE_PER_POINT = 0.08F;

    /**
     * The chance a blow of this size leaves a wound of this kind.
     *
     * Zero below the threshold. At the threshold one step of chance, then a
     * step a point, to the cap. A NaN is no blow at all and an infinity is the
     * biggest blow there is — the telegraph's overflow taught that the huge
     * case is the one to get right, because it is the one nobody tries.
     */
    public static float chanceFor(ElysiumWoundKind kind, float landed) {
        if (kind == null || !(landed >= kind.threshold())) {   // NaN fails this too
            return 0.0F;
        }
        double over = (double) landed - kind.threshold() + 1.0D;
        double chance = over * CHANCE_PER_POINT;
        if (!(chance < MAX_CHANCE)) {                          // and infinity this
            return MAX_CHANCE;
        }
        return (float) chance;
    }

    // ------------------------------------------------------------------
    // Mending
    // ------------------------------------------------------------------

    /** Ticks between recovery steps. Five seconds: a wound is minutes, not ticks. */
    public static final int TICK_INTERVAL = 100;

    public static final long TICKS_PER_MINUTE = 20L * 60L;

    /** Recovery anywhere at all. */
    public static final int RATE_NATURAL = 1;

    /** A Med bay's multiple. */
    public static final int RATE_MED_BAY = 4;

    /** Sleeping's multiple. Stacks with the Med bay: rest in the right room. */
    public static final int RATE_RESTING = 2;

    /** The pace a wound mends at, for where the patient is and what they are doing. */
    public static int rate(boolean inMedBay, boolean sleeping) {
        int rate = RATE_NATURAL;
        if (inMedBay) {
            rate *= RATE_MED_BAY;
        }
        if (sleeping) {
            rate *= RATE_RESTING;
        }
        return rate;
    }

    /** A fresh wound's time to heal, in ticks of natural recovery. */
    public static int fullTicks(ElysiumWoundKind kind) {
        return (int) Math.min(Integer.MAX_VALUE, kind.healMinutes() * TICKS_PER_MINUTE);
    }

    /**
     * What is left of a wound after {@code elapsed} ticks at {@code rate}.
     *
     * Saturating, and a negative elapsed time or rate is no progress rather
     * than a wound reopening.
     */
    public static int healed(int remaining, long elapsed, int rate) {
        if (remaining <= 0) {
            return 0;
        }
        if (elapsed <= 0 || rate <= 0) {
            return remaining;
        }
        if (elapsed > Long.MAX_VALUE / rate) {
            return 0;
        }
        long progress = elapsed * rate;
        return progress >= remaining ? 0 : (int) (remaining - progress);
    }

    /** What this many wounds of a kind cost its attribute, counting only up to the cap. */
    public static double penaltyFor(ElysiumWoundKind kind, int count) {
        int counted = Math.max(0, Math.min(count, kind.stack()));
        return kind.penalty() * counted;
    }
}
