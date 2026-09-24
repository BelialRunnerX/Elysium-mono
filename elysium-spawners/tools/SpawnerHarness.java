package com.elysium.spawners.rules;

/**
 * A spawner's arithmetic, executed.
 *
 * The two failures worth pinning hardest are the ones that would ruin the
 * feature rather than merely change it: waiting that reaches zero, which is
 * every mob in the game arriving on one tick, and a capture chance that
 * reaches one, which ends the mob-farm question forever.
 */
public final class SpawnerHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        untouched();
        waiting();
        amounts();
        prices();
        capturing();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: waiting that halves and never reaches zero, "
                + "amounts that climb and do not stop, and a capture that never becomes "
                + "certain");
    }

    /** An untouched spawner is vanilla's spawner, exactly. */
    private static void untouched() {
        // Pinned as literals rather than only compared to themselves: every
        // spawner already in every player's world runs on these six numbers,
        // and a mod that quietly changes one has changed all of them.
        check(SpawnerRules.BASE_MIN_DELAY == 200 && SpawnerRules.BASE_MAX_DELAY == 800,
                "an untouched spawner does not wait what vanilla's waits");
        check(SpawnerRules.BASE_SPAWN_COUNT == 4 && SpawnerRules.BASE_MAX_NEARBY == 6,
                "an untouched spawner does not send or tolerate what vanilla's does");
        check(SpawnerRules.BASE_PLAYER_RANGE == 16 && SpawnerRules.BASE_SPAWN_RANGE == 4,
                "an untouched spawner does not watch or scatter as vanilla's does");
        check(SpawnerRules.minDelay(0) == SpawnerRules.BASE_MIN_DELAY,
                "an untouched spawner does not wait what vanilla's waits");
        check(SpawnerRules.maxDelay(0) == SpawnerRules.BASE_MAX_DELAY,
                "an untouched spawner's longest wait is not vanilla's");
        check(SpawnerRules.spawnCount(0) == SpawnerRules.BASE_SPAWN_COUNT,
                "an untouched spawner does not send what vanilla's sends");
        check(SpawnerRules.maxNearby(0) == SpawnerRules.BASE_MAX_NEARBY,
                "an untouched spawner tolerates a different crowd than vanilla's");
        check(SpawnerRules.playerRange(0) == SpawnerRules.BASE_PLAYER_RANGE,
                "an untouched spawner watches a different distance than vanilla's");
        check(SpawnerRules.spawnRange(0) == SpawnerRules.BASE_SPAWN_RANGE,
                "an untouched spawner scatters differently than vanilla's");
    }

    private static void waiting() {
        check(SpawnerRules.share(0) == 0.0D, "a spawner nobody tuned was faster anyway");
        double previous = -1.0D;
        for (int steps = 0; steps < 2000; steps++) {
            double share = SpawnerRules.share(steps);
            check(share < 1.0D, "the waiting was cut away entirely at " + steps
                    + " steps, which is every mob in the game on one tick");
            check(share > previous || steps == 0, "tuning stopped helping at " + steps);
            previous = share;
        }
        check(Math.abs(SpawnerRules.share((int) SpawnerRules.DELAY_PACE) - 0.5D) < 1e-9,
                "the pace no longer means what it says: at DELAY_PACE steps the waiting "
                        + "should be halved");
        for (int steps = 0; steps < 500; steps++) {
            check(SpawnerRules.minDelay(steps) >= SpawnerRules.DELAY_FLOOR,
                    "a spawner was told to go faster than the floor at " + steps);
            check(SpawnerRules.maxDelay(steps) >= SpawnerRules.minDelay(steps),
                    "a spawner's longest wait fell below its shortest at " + steps);
        }
        check(SpawnerRules.minDelay(4) < SpawnerRules.minDelay(0),
                "four steps of tuning did not make a spawner faster");
        check(SpawnerRules.minDelay(-5) == SpawnerRules.minDelay(0),
                "a negative number of steps made a spawner slower than untouched");
    }

    private static void amounts() {
        check(SpawnerRules.spawnCount(10) == SpawnerRules.BASE_SPAWN_COUNT + 10,
                "how many a spawner sends is not an amount that climbs by one a step");
        check(SpawnerRules.spawnCount(1000) > SpawnerRules.spawnCount(999),
                "how many a spawner sends stopped climbing, which is a cap");
        check(SpawnerRules.maxNearby(5) > SpawnerRules.maxNearby(4)
                        && SpawnerRules.playerRange(5) > SpawnerRules.playerRange(4)
                        && SpawnerRules.spawnRange(5) > SpawnerRules.spawnRange(4),
                "an axis stopped answering to being tuned");
        check(SpawnerRules.ALWAYS_AWAKE > SpawnerRules.playerRange(20),
                "a spawner told to ignore players watches less far than one that was "
                        + "simply tuned to watch, so the rune is a downgrade");
    }

    private static void prices() {
        check(SpawnerRules.stepCost(0) == SpawnerRules.FIRST_STEP_COST,
                "the first step is not the first price");
        check(SpawnerRules.stepCost(5) > SpawnerRules.stepCost(4),
                "the sixth step cost no more than the fifth, so tuning is free after a "
                        + "while");
        check(SpawnerRules.stepCost(20) < SpawnerRules.stepCost(0) * 1000,
                "the price runs away rather than climbing, so the sixth step is the last "
                        + "one anybody takes");
        int sum = 0;
        for (int step = 0; step < 7; step++) {
            sum += SpawnerRules.stepCost(step);
        }
        check(SpawnerRules.spentOn(7) == sum,
                "what a spawner has cost is not the sum of what its steps cost");
        check(SpawnerRules.spentOn(0) == 0, "an untouched spawner had already cost something");
    }

    private static void capturing() {
        check(SpawnerRules.captureChance(0) == 0.0F,
                "a weapon with no Capturing on it captured things");
        float previous = -1.0F;
        for (int level = 0; level < 5000; level++) {
            float chance = SpawnerRules.captureChance(level);
            check(chance < 1.0F, "capturing became certain at level " + level);
            check(chance <= SpawnerRules.CAPTURE_CEILING,
                    "capturing passed its own ceiling at level " + level);
            check(chance > previous || level == 0, "capturing stopped rising at " + level);
            previous = chance;
        }
        check(SpawnerRules.captureChance(3) > SpawnerRules.captureChance(1),
                "a better Capturing was not better");
        check(SpawnerRules.CAPTURE_CEILING < 1.0F,
                "capturing's ceiling is certainty, so the first Capturing sword ends the "
                        + "mob-farm question forever");
    }
}
