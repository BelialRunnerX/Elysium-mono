package com.elysium.lib.weather;

/**
 * When a storm happens, how hard it blows, and for how long.
 *
 * <h2>Why the schedule is arithmetic rather than a roll</h2>
 *
 * Because a stored random walk is a thing a player can only learn by standing
 * there. Read off the day instead, the way the frontier market's prices are, a
 * world's weather has a history before anybody visits it and a shape somebody
 * can work out — so "the ion storms on the Pale Moon come in threes" is a
 * thing to know rather than a thing to endure. Knowing the frontier ought to
 * be worth something, and this is the third system in this project that says
 * so on purpose.
 *
 * <p>It also means nothing has to be saved. A weather system with a file is a
 * weather system that can disagree with itself after a restart.
 *
 * <h2>The shape of a spell</h2>
 *
 * A day is four watches. Each watch either has weather or does not, decided by
 * a hash of the world, the day and the watch against that world's own
 * storminess. Within a watch, the weather builds, holds and fades, so a storm
 * arrives rather than appearing — and the tail end of one is a warning that
 * the next is not far off.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumWeatherRules {

    private ElysiumWeatherRules() {
    }

    /** How many watches a day is divided into. Four: six hours each. */
    public static final int WATCHES = 4;

    /** How long a watch is, in ticks. */
    public static final int WATCH_TICKS = 24000 / WATCHES;

    /**
     * The share of a watch a storm spends arriving, and the same leaving.
     *
     * A fifth each way. Long enough that a player caught outside has time to
     * notice and get in, short enough that most of a storm is the storm.
     */
    public static final float RAMP = 0.2F;

    /**
     * How stormy a world may be, at most, as watches in eight.
     *
     * Six of eight rather than eight of eight: a world where it is always
     * storming has no weather, it has a climate, and the whole value of a
     * storm is that it ends.
     */
    public static final int MOST_STORMY = 6;

    /**
     * Whether this world has weather in this watch, and which of its kinds.
     *
     * @param world    a number that differs per world
     * @param day      the world's own day count
     * @param watch    which watch of that day
     * @param kinds    how many kinds of weather this world has
     * @param storminess how many watches in eight this world storms in
     * @return the index of the kind, or -1 for clear
     */
    public static int kindAt(int world, long day, int watch, int kinds,
                             int storminess) {
        if (kinds <= 0 || storminess <= 0) {
            return -1;
        }
        int rolled = hash(world, day, watch);
        if (Math.floorMod(rolled, 8) >= Math.min(MOST_STORMY, storminess)) {
            return -1;
        }
        return Math.floorMod(rolled >> 3, kinds);
    }

    /**
     * How hard it is blowing, from 0 to 1, this far into a watch.
     *
     * Trapezoidal: up, along, down. Not a sine, because a sine spends most of
     * its time near neither end and a storm should spend most of its time
     * being a storm.
     */
    public static float strength(int intoWatch) {
        if (intoWatch < 0 || intoWatch >= WATCH_TICKS) {
            return 0.0F;
        }
        float ramp = Math.max(1.0F, RAMP * WATCH_TICKS);
        if (intoWatch < ramp) {
            return intoWatch / ramp;
        }
        float from = WATCH_TICKS - ramp;
        if (intoWatch > from) {
            return Math.max(0.0F, (WATCH_TICKS - intoWatch) / ramp);
        }
        return 1.0F;
    }

    /** Which watch a time of day falls in. */
    public static int watchOf(long timeOfDay) {
        return (int) Math.floorMod(timeOfDay, 24000L) / WATCH_TICKS;
    }

    /** How far into its watch a time of day is. */
    public static int intoWatch(long timeOfDay) {
        return (int) Math.floorMod(timeOfDay, (long) WATCH_TICKS);
    }

    /**
     * How much of a hazard a storm of this kind adds, at this strength.
     *
     * Multiplied by the strength rather than switched on, so the ramp means
     * something, and bounded at one because every hazard in this engine is a
     * share of the worst a place can be.
     */
    public static float hazardFrom(float peak, float strength) {
        return clamp(clamp(peak) * clamp(strength));
    }

    /**
     * What a machine gets out of a storm, as a share of what it would get.
     *
     * One when it is clear, and never zero: a solar panel under an ash cloud
     * is a bad solar panel, not a broken one, and a system that switched
     * machines off would have players building a second power plant for the
     * weather rather than a bigger one.
     */
    public static final float LEAST_OUTPUT = 0.15F;

    public static float machineShare(float worst, float strength) {
        float lost = clamp(worst) * clamp(strength);
        return Math.max(LEAST_OUTPUT, 1.0F - lost);
    }

    /**
     * How long is left of this spell, in seconds, for something a player reads.
     */
    public static int remaining(long timeOfDay) {
        return (WATCH_TICKS - intoWatch(timeOfDay)) / 20;
    }

    /**
     * A number from a world, a day and a watch.
     *
     * Mixed rather than added, so two worlds a day apart do not get the same
     * weather a day apart, which is what a plain sum would give.
     */
    public static int hash(int world, long day, int watch) {
        long mixed = world * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L
                + watch * 0x94D049BB133111EBL;
        mixed ^= (mixed >>> 31);
        mixed *= 0xD6E8FEB86659FD93L;
        mixed ^= (mixed >>> 32);
        return (int) (mixed & 0x7FFFFFFF);
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }
}
