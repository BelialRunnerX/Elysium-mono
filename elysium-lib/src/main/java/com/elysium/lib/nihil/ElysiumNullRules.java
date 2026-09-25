package com.elysium.lib.nihil;

/**
 * Null saturation: Valoria's Nihility, kept as arithmetic and reread as a
 * fiction. The Null field is a hazard on the Void element; the library's
 * protection arithmetic decides how much of it a player stops; this class
 * decides what the part that reaches them does over time.
 *
 * <p>It is a gauge, not damage. Exposure accrues while unblocked, decays away
 * from the field, hurts past half, speaks past three quarters and collapses the
 * player at the top. The numbers are Valoria's own — accrual every five
 * seconds, decay every six, thresholds at 50/75/95 percent — because they were
 * tuned by play and nothing here has been played yet.
 *
 * <p>The ward (Valoria's Nihility Protection) is the one place a cap was
 * removed: it blocked {@code (level+1) * 10%} of a blow up to 90%. Here the
 * blocked share is a share of the uncapped level, {@code stat / (stat + b)},
 * with {@code b} chosen so level one still blocks a fifth. Its second half is
 * kept exactly: what the shield turns aside leaks into the gauge at 1.5 times
 * the rate, so a low ward drains saturation on every hit and a high one feeds
 * it — the crossover sits at level three, where it was.
 *
 * <p>This class is the JDK alone. {@code NullHarness} executes every rule here
 * without a game, and the validator refuses any import that would stop it.
 */
public final class ElysiumNullRules {

    private ElysiumNullRules() {
    }

    /** The gauge's default top, before the null_capacity attribute is read. */
    public static final float DEFAULT_CAPACITY = 100.0F;
    /** Seconds between accrual ticks while exposed, before the null_resilience attribute is read. */
    public static final int DEFAULT_RESILIENCE_SECONDS = 5;
    /** Seconds between decay ticks away from the field. */
    public static final int DECAY_SECONDS = 6;
    /** Points lost per decay tick. */
    public static final float DECAY = 1.0F;
    /** What one difficulty step is worth per accrual tick, before protection. */
    public static final float ACCRUAL_PER_DIFFICULTY = 0.5F;
    /** Nothing accrues faster than the field itself at full difficulty. */
    public static final float ACCRUAL_CEILING = 1.5F;

    /** Past this share of the gauge the field hurts. */
    public static final float DAMAGING = 0.50F;
    /** Past this share the field speaks, and the player is warned aloud. */
    public static final float SPEAKING = 0.75F;
    /** At this share the player collapses. */
    public static final float COLLAPSE = 0.95F;
    /** Each step of this share past DAMAGING adds one multiplier of damage. */
    public static final float DAMAGE_STEP = 0.10F;
    /** Ticks between field damage below SPEAKING, and above it. */
    public static final int DAMAGE_INTERVAL = 40;
    public static final int DAMAGE_INTERVAL_SPEAKING = 20;

    /** The share of a blow a ward of level one turns aside, and the b behind it. */
    public static final double WARD_PACE = 4.0D;
    public static final double WARD_POINTS_FOR_ALL = 32.0D;
    /** What the shield turns aside comes back into the gauge at this rate. */
    public static final float WARD_LEAK_FACTOR = 1.5F;

    /** What happens at the top: the field reclaims the player, or the Nullspire failsafe extracts them. */
    public enum Failsafe { COLLAPSE, EXTRACT }

    /**
     * Points gained in one accrual tick.
     *
     * @param protection the share of the field the player's gear, wards, rooms,
     *                   claim and null_resistance stop, 0..1, as the library's
     *                   resistance arithmetic combines them
     * @param difficulty 0 peaceful, 1 easy, 2 normal, 3 hard
     * @return zero on peaceful or at full protection; otherwise the unblocked
     *         share of the difficulty's worth
     */
    public static float accrual(float protection, int difficulty) {
        if (difficulty <= 0) {
            return 0.0F;
        }
        float unblocked = 1.0F - clampShare(protection);
        if (unblocked <= 0.0F) {
            return 0.0F;
        }
        return Math.min(ACCRUAL_CEILING, unblocked * Math.min(3, difficulty) * ACCRUAL_PER_DIFFICULTY);
    }

    /** Points lost in one decay tick away from the field; easy forgets twice as fast. */
    public static float decay(int difficulty) {
        return difficulty == 1 ? DECAY * 2.0F : DECAY;
    }

    /**
     * What the field does per damage tick at a share of the gauge.
     *
     * @param ratio      saturation over capacity, 0..1
     * @param multiplier the server's damage multiplier
     * @return nothing below DAMAGING; from there one point plus one multiplier
     *         per DAMAGE_STEP past it
     */
    public static float damage(float ratio, float multiplier) {
        if (ratio < DAMAGING) {
            return 0.0F;
        }
        int steps = (int) Math.floor((ratio - DAMAGING) / DAMAGE_STEP + 1.0E-6F);
        return 1.0F + steps * Math.max(0.0F, multiplier);
    }

    /** How often the field hurts, in ticks, at a share of the gauge. */
    public static int damageInterval(float ratio) {
        return ratio >= SPEAKING ? DAMAGE_INTERVAL_SPEAKING : DAMAGE_INTERVAL;
    }

    /** Whether the field speaks at a share of the gauge. */
    public static boolean speaks(float ratio) {
        return ratio >= SPEAKING;
    }

    /** Whether a share of the gauge is the top. */
    public static boolean collapses(float ratio) {
        return ratio >= COLLAPSE;
    }

    /** The saturation as a share of a capacity, 0..1; a capacity of nothing reads as full. */
    public static float ratio(float saturation, float capacity) {
        if (capacity <= 0.0F) {
            return 1.0F;
        }
        return clampShare(saturation / capacity);
    }

    /** A gauge after a change, held inside zero and its capacity. */
    public static float after(float saturation, float delta, float capacity) {
        return Math.max(0.0F, Math.min(Math.max(0.0F, capacity), saturation + delta));
    }

    /** Ticks between accrual ticks for a resilience in seconds; never under a second. */
    public static int resilienceTicks(double resilienceSeconds) {
        double seconds = Double.isNaN(resilienceSeconds) ? DEFAULT_RESILIENCE_SECONDS : resilienceSeconds;
        return Math.max(20, (int) Math.round(Math.max(1.0D, seconds) * 20.0D));
    }

    /** A share held inside 0..1; NaN reads as nothing stopped. */
    public static float clampShare(float share) {
        if (Float.isNaN(share)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, share));
    }

    /**
     * The share of a blow a ward of a level turns aside: a share of the
     * uncapped level, so the tenth level is better than the ninth and none
     * reaches all of it.
     */
    public static float wardBlock(int level) {
        double stat = Math.max(0, level + 1) * WARD_PACE;
        return (float) (stat / (stat + WARD_POINTS_FOR_ALL));
    }

    /**
     * What a warded blow does to the gauge: the whole blow comes in, and what
     * the shield turned aside goes out again at LEAK_FACTOR. Below the
     * crossover the ward drains saturation; above it the ward feeds it.
     */
    public static float wardLeak(float incoming, float reduced) {
        return Math.max(0.0F, incoming) - Math.max(0.0F, reduced) * WARD_LEAK_FACTOR;
    }

    /** The first level at which a warded blow feeds the gauge rather than draining it. */
    public static int wardCrossover() {
        for (int level = 1; level < 1000; level++) {
            float block = wardBlock(level);
            if (wardLeak(1.0F, 1.0F - block) > 0.0F) {
                return level;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Seconds an unprotected player lasts in a full-strength field at a
     * difficulty before collapsing, starting empty. The harness bounds this:
     * long enough to be a visit, short enough to be a threat.
     */
    public static int secondsToCollapse(float protection, int difficulty, float capacity) {
        float perTick = accrual(protection, difficulty);
        if (perTick <= 0.0F) {
            return Integer.MAX_VALUE;
        }
        float needed = capacity * COLLAPSE;
        return (int) Math.ceil(needed / perTick) * DEFAULT_RESILIENCE_SECONDS;
    }
}
