package com.elysium.spawners.rules;

/**
 * What a spawner is, and what tuning one costs.
 *
 * <h2>The thing vanilla's spawner is missing</h2>
 *
 * A vanilla spawner is a fixed object. A player finds one, lights the room, and
 * from then on it produces the same four mobs every twenty-five seconds for the
 * rest of the world's life. There is nothing to do with it and nothing to spend
 * on it, so the only interesting decision it ever poses is whether to break it.
 *
 * <p>Everything here exists to make it a thing a player invests in: it can be
 * picked up, moved, tuned along six axes and given behaviours, and each step
 * costs more than the last.
 *
 * <h2>Amounts climb and shares curve</h2>
 *
 * The rule the whole mod uses. How many mobs a spawner sends is an amount, so
 * it climbs by one a step and never stops. How long it waits is a share of the
 * time it used to wait, so it goes through {@code x/(x+b)} and approaches zero
 * without ever arriving: a spawner tuned forever gets faster forever and never
 * becomes a solid wall of mobs on one tick.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class SpawnerRules {

    private SpawnerRules() {
    }

    // -- what an untouched spawner is, which is vanilla's own numbers --------

    public static final int BASE_MIN_DELAY = 200;
    public static final int BASE_MAX_DELAY = 800;
    public static final int BASE_SPAWN_COUNT = 4;
    public static final int BASE_MAX_NEARBY = 6;
    public static final int BASE_PLAYER_RANGE = 16;
    public static final int BASE_SPAWN_RANGE = 4;

    /** How fast the waiting shrinks: at this many steps it is halved. */
    public static final double DELAY_PACE = 4.0D;

    /** The fastest a spawner may ever be told to go, so a tick is never a wall. */
    public static final int DELAY_FLOOR = 10;

    /** Amounts, per step. */
    public static final int COUNT_PER_STEP = 1;
    public static final int NEARBY_PER_STEP = 2;
    public static final int PLAYER_RANGE_PER_STEP = 4;
    public static final int SPAWN_RANGE_PER_STEP = 1;

    /**
     * The range a spawner is given when it has been told to stop caring
     * whether anybody is watching.
     *
     * Not a flag: {@link net.minecraft.world.level.BaseSpawner} has a required
     * player range and no field for "always", so the answer is a range larger
     * than a spawner can act over anyway. Sixteen chunks: past that the chunk
     * is not loaded and nothing ticks.
     */
    public static final int ALWAYS_AWAKE = 256;

    /**
     * How hard an unstable spawner goes off when it is broken.
     *
     * Three, which is a creeper and a half: enough to hurt somebody standing
     * on top of it and not enough to take the room down. An unstable spawner
     * is a warning a player walked past, not a trap.
     */
    public static final float UNSTABLE_POWER = 3.0F;

    /** How fast capturing approaches its ceiling. */
    public static final double CAPTURE_PACE = 3.0D;

    /** And the ceiling it never reaches, because a certain egg ends the question. */
    public static final float CAPTURE_CEILING = 0.5F;

    /**
     * The chance a kill leaves a spawn egg behind.
     *
     * A share of a ceiling, so more levels always help and no number of them
     * ever makes it certain.
     */
    public static float captureChance(int level) {
        double at = Math.max(0, level);
        return (float) (CAPTURE_CEILING * (at / (at + CAPTURE_PACE)));
    }

    /** How many of the modifier item one more step takes. */
    public static final int FIRST_STEP_COST = 1;

    /**
     * The share a number of steps is worth, through the same {@code x/(x+b)}
     * every percentile thing in this mod goes through.
     */
    public static double share(int steps) {
        double at = Math.max(0, steps);
        return at / (at + DELAY_PACE);
    }

    /** How long a spawner waits, at least, after this many steps. */
    public static int minDelay(int steps) {
        return delay(BASE_MIN_DELAY, steps);
    }

    /** And at most. */
    public static int maxDelay(int steps) {
        return Math.max(minDelay(steps), delay(BASE_MAX_DELAY, steps));
    }

    private static int delay(int base, int steps) {
        double left = base * (1.0D - share(steps));
        return (int) Math.max(DELAY_FLOOR, Math.round(left));
    }

    /** How many it sends at once. An amount, so it climbs and does not stop. */
    public static int spawnCount(int steps) {
        return BASE_SPAWN_COUNT + COUNT_PER_STEP * Math.max(0, steps);
    }

    /** How many of its own it tolerates nearby before it stops. */
    public static int maxNearby(int steps) {
        return BASE_MAX_NEARBY + NEARBY_PER_STEP * Math.max(0, steps);
    }

    /** How far away a player may stand and still keep it awake. */
    public static int playerRange(int steps) {
        return BASE_PLAYER_RANGE + PLAYER_RANGE_PER_STEP * Math.max(0, steps);
    }

    /** How far from itself it puts them. */
    public static int spawnRange(int steps) {
        return BASE_SPAWN_RANGE + SPAWN_RANGE_PER_STEP * Math.max(0, steps);
    }

    /**
     * How many of the modifier item the next step takes.
     *
     * One more each time. Not doubling: a spawner is a thing a player keeps
     * working on for a whole world, and a price that doubles means the sixth
     * step is the last one anybody ever takes.
     */
    public static int stepCost(int stepsAlready) {
        return FIRST_STEP_COST + Math.max(0, stepsAlready);
    }

    /** What all the steps so far have cost, which is what a tooltip should say. */
    public static int spentOn(int steps) {
        int total = 0;
        for (int step = 0; step < Math.max(0, steps); step++) {
            total += stepCost(step);
        }
        return total;
    }
}
