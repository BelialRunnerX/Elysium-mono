package com.elysium.lib.weather;

/**
 * The weather schedule, executed without a game.
 *
 * <ol>
 *   <li>A storm arrives and leaves rather than appearing: strength is zero at
 *       both ends of a watch and one in the middle.</li>
 *   <li>A world's storminess means what it says: over many days, the share of
 *       watches with weather is the share the world declared.</li>
 *   <li>No world storms all the time, and a world that declared nothing never
 *       storms at all.</li>
 *   <li>Two worlds a day apart do not get each other's weather a day apart.</li>
 *   <li>A machine in a storm makes less and never nothing.</li>
 * </ol>
 */
public final class WeatherHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        // --- the shape of a spell ---------------------------------------
        check(ElysiumWeatherRules.strength(0) == 0.0F,
                "a storm is at full strength the instant it starts");
        check(ElysiumWeatherRules.strength(ElysiumWeatherRules.WATCH_TICKS - 1) < 0.2F,
                "a storm is still blowing at the end of its watch");
        check(ElysiumWeatherRules.strength(ElysiumWeatherRules.WATCH_TICKS / 2) == 1.0F,
                "a storm never reaches full strength");
        check(ElysiumWeatherRules.strength(-5) == 0.0F, "a storm before it began");
        check(ElysiumWeatherRules.strength(ElysiumWeatherRules.WATCH_TICKS * 3) == 0.0F,
                "a storm after its watch is over");
        float last = -1.0F;
        int rising = 0;
        for (int into = 0; into < ElysiumWeatherRules.WATCH_TICKS / 4; into++) {
            float now = ElysiumWeatherRules.strength(into);
            check(now >= last, "a storm weakened while it was still arriving, at " + into);
            if (now > last) {
                rising++;
            }
            last = now;
        }
        check(rising > 10, "a storm arrives in one step, so there is no warning in it");
        for (int into = 0; into < ElysiumWeatherRules.WATCH_TICKS; into++) {
            float now = ElysiumWeatherRules.strength(into);
            check(now >= 0.0F && now <= 1.0F, "strength out of range at " + into);
        }

        // --- how often it storms ----------------------------------------
        for (int storminess = 0; storminess <= ElysiumWeatherRules.MOST_STORMY;
                storminess++) {
            int stormy = 0;
            int watches = 0;
            for (long day = 0; day < 2000; day++) {
                for (int watch = 0; watch < ElysiumWeatherRules.WATCHES; watch++) {
                    watches++;
                    if (ElysiumWeatherRules.kindAt(12345, day, watch, 2, storminess) >= 0) {
                        stormy++;
                    }
                }
            }
            double share = stormy / (double) watches;
            double wanted = storminess / 8.0D;
            check(Math.abs(share - wanted) < 0.03D,
                    "a world that declared " + storminess + " in eight storms "
                            + Math.round(share * 800) / 100.0 + " in eight");
        }
        check(ElysiumWeatherRules.kindAt(1, 1, 1, 0, 4) < 0,
                "a world with no kinds of weather had some anyway");
        // Over a year rather than on one day: a world that storms one watch in
        // eight passes a single sample seven times out of eight, and a check
        // that passes seven times out of eight is not a check.
        int calmBroken = 0;
        for (long day = 0; day < 400; day++) {
            for (int watch = 0; watch < ElysiumWeatherRules.WATCHES; watch++) {
                if (ElysiumWeatherRules.kindAt(1, day, watch, 2, 0) >= 0) {
                    calmBroken++;
                }
            }
        }
        check(calmBroken == 0, "a world declared calm had weather on " + calmBroken
                + " of " + (400 * ElysiumWeatherRules.WATCHES) + " watches");
        check(ElysiumWeatherRules.kindAt(1, 1, 1, 2, 99) >= -1,
                "an impossible storminess broke the schedule");
        for (long day = 0; day < 500; day++) {
            for (int watch = 0; watch < ElysiumWeatherRules.WATCHES; watch++) {
                int kind = ElysiumWeatherRules.kindAt(7, day, watch, 3, 4);
                check(kind >= -1 && kind < 3, "a kind outside the world's list");
                check(kind == ElysiumWeatherRules.kindAt(7, day, watch, 3, 4),
                        "the same watch gave two answers, so the schedule is not "
                                + "something a player can learn");
            }
        }

        // No world is always storming, whatever it declares.
        int worst = 0;
        for (long day = 0; day < 1000; day++) {
            for (int watch = 0; watch < ElysiumWeatherRules.WATCHES; watch++) {
                worst = ElysiumWeatherRules.kindAt(3, day, watch, 2,
                        ElysiumWeatherRules.MOST_STORMY + 5) >= 0 ? worst + 1 : worst;
            }
        }
        check(worst < 1000 * ElysiumWeatherRules.WATCHES,
                "a world storms in every watch there is, which is a climate rather "
                        + "than weather");

        // --- two worlds are not each other, shifted ----------------------
        int same = 0;
        for (long day = 0; day < 400; day++) {
            for (int watch = 0; watch < ElysiumWeatherRules.WATCHES; watch++) {
                if (ElysiumWeatherRules.kindAt(11, day, watch, 2, 4)
                        == ElysiumWeatherRules.kindAt(12, day - 1, watch, 2, 4)) {
                    same++;
                }
            }
        }
        check(same < 400 * ElysiumWeatherRules.WATCHES,
                "one world's weather is another's, a day later");

        // --- what a machine gets -----------------------------------------
        for (int peak = 0; peak <= 100; peak++) {
            for (int strength = 0; strength <= 100; strength++) {
                float share = ElysiumWeatherRules.machineShare(peak / 100.0F,
                        strength / 100.0F);
                check(share >= ElysiumWeatherRules.LEAST_OUTPUT,
                        "a storm switched a machine off, at " + peak + "/" + strength);
                check(share <= 1.0F,
                        "a storm made a machine better, at " + peak + "/" + strength);
            }
        }
        check(ElysiumWeatherRules.machineShare(0.5F, 0.0F) == 1.0F,
                "a storm that has not arrived is already costing output");
        check(ElysiumWeatherRules.machineShare(0.5F, 1.0F)
                        < ElysiumWeatherRules.machineShare(0.5F, 0.5F),
                "a storm at full strength costs no more than half a storm");
        check(ElysiumWeatherRules.LEAST_OUTPUT > 0.0F,
                "a storm can stop a machine dead, so a base needs a second power "
                        + "plant for the weather rather than a bigger one");

        // --- what a hazard gets ------------------------------------------
        for (int peak = 0; peak <= 100; peak++) {
            for (int strength = 0; strength <= 100; strength++) {
                float added = ElysiumWeatherRules.hazardFrom(peak / 100.0F,
                        strength / 100.0F);
                check(added >= 0.0F && added <= 1.0F,
                        "a hazard outside the scale, at " + peak + "/" + strength);
            }
        }
        check(ElysiumWeatherRules.hazardFrom(1.0F, 0.0F) == 0.0F,
                "a storm that has not arrived is already hurting");
        check(ElysiumWeatherRules.hazardFrom(0.5F, 1.0F) > 0.0F,
                "a storm at full strength adds nothing");
        check(ElysiumWeatherRules.hazardFrom(Float.NaN, 1.0F) == 0.0F,
                "a broken number became a hazard");

        // --- reading the clock -------------------------------------------
        for (long time = 0; time < 48000; time += 137) {
            int watch = ElysiumWeatherRules.watchOf(time);
            check(watch >= 0 && watch < ElysiumWeatherRules.WATCHES,
                    "a watch outside the day, at " + time);
            int into = ElysiumWeatherRules.intoWatch(time);
            check(into >= 0 && into < ElysiumWeatherRules.WATCH_TICKS,
                    "a position outside its watch, at " + time);
            check(ElysiumWeatherRules.remaining(time) >= 0,
                    "a storm with negative time left, at " + time);
        }

        if (failures > 0) {
            System.out.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println(checks + " checks: storms arrive and leave, a world storms "
                + "as often as it says it does, no sky is always angry, and a machine "
                + "under one makes less and never nothing");
    }
}
