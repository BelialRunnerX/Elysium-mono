package com.elysium.lib.telegraph;

/**
 * The readability promise, executed.
 *
 * <h2>The promise</h2>
 *
 * Nothing hits a player for more than {@link ElysiumTelegraph#DANGEROUS}
 * without having announced itself first. Written as one sentence, it is the
 * kind of claim that is obviously true of the code in front of you and false
 * for some damage value nobody tried. So every damage value the game can
 * plausibly produce is tried, against every length of warning, which is the
 * only way to be sure the sentence has no exceptions.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/th elysium-lib/tools/TelegraphHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/th" \
 *         com.elysium.lib.telegraph.TelegraphHarness
 */
public final class TelegraphHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Quarter-point steps from nothing to well past anything in the mod. */
    private static float[] everyDamage() {
        float[] all = new float[4 * 200 + 1];
        for (int i = 0; i < all.length; i++) {
            all[i] = i / 4.0F;
        }
        return all;
    }

    /** The promise itself, at every damage and every warning length. */
    private static void nothingLandsUnannounced() {
        for (float damage : everyDamage()) {
            int needed = ElysiumTelegraph.windupFor(damage);
            for (int announced = 0; announced <= ElysiumTelegraph.MAXIMUM_WINDUP + 5;
                 announced++) {
                float allowed = ElysiumTelegraph.allowed(damage, announced);
                check(allowed <= damage,
                        "a blow of " + damage + " was allowed to do " + allowed);
                if (announced < needed) {
                    check(allowed <= ElysiumTelegraph.DANGEROUS,
                            "a blow of " + damage + " announced for only "
                                    + announced + " of " + needed
                                    + " ticks landed for " + allowed);
                } else {
                    check(allowed == damage,
                            "a blow of " + damage + " announced for " + announced
                                    + " ticks was still reduced to " + allowed);
                }
            }
        }
    }

    /** A jab needs no warning, and a killing blow needs the longest there is. */
    private static void theThresholdIsWhereItSays() {
        for (float damage : everyDamage()) {
            boolean dangerous = ElysiumTelegraph.isDangerous(damage);
            check(dangerous == (damage > ElysiumTelegraph.DANGEROUS),
                    damage + " reads as " + (dangerous ? "dangerous" : "safe"));
            if (!dangerous) {
                check(ElysiumTelegraph.allowed(damage, 0) == damage,
                        "a harmless blow of " + damage + " was capped anyway, "
                                + "which would make every skeleton in the world "
                                + "pause before swinging");
            }
        }
        check(ElysiumTelegraph.windupFor(Float.NaN) == 0,
                "a NaN damage is treated as dangerous, which would put a mob "
                        + "into a wind-up it can never finish");
        check(ElysiumTelegraph.windupFor(Float.POSITIVE_INFINITY)
                        == ElysiumTelegraph.MAXIMUM_WINDUP,
                "an unbounded blow does not earn the longest warning there is");
        check(ElysiumTelegraph.windupFor(-5.0F) == 0,
                "a negative damage earns a warning");
    }

    /** The warning grows with the blow, and stops growing. */
    private static void theWindupIsBounded() {
        int previous = 0;
        for (float damage : everyDamage()) {
            int windup = ElysiumTelegraph.windupFor(damage);
            check(windup >= previous,
                    "a bigger blow of " + damage + " earned a shorter warning: "
                            + windup + " after " + previous);
            check(windup == 0 || windup >= ElysiumTelegraph.MINIMUM_WINDUP,
                    "a dangerous blow of " + damage + " earned only " + windup
                            + " ticks, below the floor of "
                            + ElysiumTelegraph.MINIMUM_WINDUP);
            check(windup <= ElysiumTelegraph.MAXIMUM_WINDUP,
                    "a blow of " + damage + " earned " + windup + " ticks, past "
                            + "the ceiling — a monster standing still that long "
                            + "is one the player simply walks away from");
            previous = windup;
        }
        System.out.println("  windup: " + ElysiumTelegraph.MINIMUM_WINDUP
                + " ticks at " + (ElysiumTelegraph.DANGEROUS + 0.25F)
                + " damage, reaching the ceiling of "
                + ElysiumTelegraph.MAXIMUM_WINDUP + " at "
                + ceilingReachedAt() + " damage");
    }

    private static float ceilingReachedAt() {
        for (float damage : everyDamage()) {
            if (ElysiumTelegraph.windupFor(damage)
                    >= ElysiumTelegraph.MAXIMUM_WINDUP) {
                return damage;
            }
        }
        return -1.0F;
    }

    /**
     * The window is worth having.
     *
     * A warning shorter than a player's reaction time is a warning in name
     * only. Two hundred milliseconds is about as fast as anybody gets, and the
     * floor has to clear it with something to spare — at twenty ticks a second,
     * that is four ticks, and ten gives a player half a second.
     */
    private static void theWindowIsUsable() {
        check(ElysiumTelegraph.MINIMUM_WINDUP >= 8,
                "the shortest warning is " + ElysiumTelegraph.MINIMUM_WINDUP
                        + " ticks, which is under half a second and is not a "
                        + "window anybody can use");
        check(ElysiumTelegraph.MAXIMUM_WINDUP
                        <= ElysiumTelegraph.MINIMUM_WINDUP * 4,
                "the longest warning is more than four times the shortest, so a "
                        + "big monster is easier to fight than a small one");
        check(ElysiumTelegraph.DANGEROUS > 0.0F
                        && ElysiumTelegraph.DANGEROUS < 10.0F,
                "the unannounced ceiling is " + ElysiumTelegraph.DANGEROUS
                        + "; it has to be survivable at full health and still "
                        + "worth avoiding");
    }

    /** A capped blow is still a blow. */
    private static void cappingIsNotCancelling() {
        for (float damage : everyDamage()) {
            if (damage <= 0.0F) {
                continue;
            }
            check(ElysiumTelegraph.allowed(damage, 0) > 0.0F,
                    "an unannounced blow of " + damage + " was reduced to "
                            + "nothing. A player who learns that some attacks "
                            + "do nothing has learned something worse than any "
                            + "amount of damage");
        }
    }

    public static void main(String[] args) {
        nothingLandsUnannounced();
        theThresholdIsWhereItSays();
        theWindupIsBounded();
        theWindowIsUsable();
        cappingIsNotCancelling();

        System.out.println("telegraph: nothing above "
                + ElysiumTelegraph.DANGEROUS + " lands unannounced, across "
                + everyDamage().length + " damage values and "
                + (ElysiumTelegraph.MAXIMUM_WINDUP + 6) + " warning lengths — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("you always see it coming");
    }
}
