package com.elysium.lib.craft;

/**
 * The quality ladder, executed.
 *
 * <h2>What is worth running here</h2>
 *
 * Not the stamping — that needs an item, a registry and a player. What decides
 * whether craft authorship is a feature or a label is the <em>shape of the
 * ladder</em>: whether a first attempt costs something, whether the top is
 * reachable by a person rather than by a machine, and whether the durability
 * curve is worth pursuing without being worth automating. All three are
 * decided by fifteen numbers in one file and none of them mentions the others.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/crh elysium-lib/tools/CraftHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/crh" \
 *         com.elysium.lib.craft.CraftHarness
 */
public final class CraftHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Practice counts from nothing to well past the top. */
    private static void theLadderIsMonotonic() {
        ElysiumQuality previous = ElysiumQuality.CRUDE;
        for (int made = 0; made <= ElysiumQuality.MASTERWORK.practice() * 3; made++) {
            ElysiumQuality grade = ElysiumQuality.forPractice(made);
            check(grade.ordinal() >= previous.ordinal(),
                    "making one more dropped the grade at " + made + ": "
                            + previous + " to " + grade);
            check(grade.durabilityMultiplier() >= previous.durabilityMultiplier(),
                    "durability fell at " + made + " practice");
            previous = grade;
        }
        check(ElysiumQuality.forPractice(0) == ElysiumQuality.CRUDE,
                "a first attempt is not Crude, so the mark is a label rather "
                        + "than a record of the skill at the time");
        check(ElysiumQuality.forPractice(Integer.MAX_VALUE)
                        == ElysiumQuality.MASTERWORK,
                "the top grade is not the top of the ladder");
        check(ElysiumQuality.forPractice(-3) == ElysiumQuality.CRUDE,
                "a negative practice count produced " 
                        + ElysiumQuality.forPractice(-3));
    }

    /** The grades are ordered, distinct, and start at nothing. */
    private static void theGradesAreDistinct() {
        ElysiumQuality[] all = ElysiumQuality.values();
        check(all[0].practice() == 0,
                "the first grade needs " + all[0].practice() + " practice, so a "
                        + "player's first item has no grade at all");
        for (int i = 1; i < all.length; i++) {
            check(all[i].practice() > all[i - 1].practice(),
                    all[i] + " needs " + all[i].practice() + ", not more than "
                            + all[i - 1] + " at " + all[i - 1].practice());
            check(all[i].durabilityMultiplier() > all[i - 1].durabilityMultiplier(),
                    all[i] + " is worth no more than " + all[i - 1]
                            + "; two grades that do the same thing are one grade "
                            + "with two names");
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ElysiumQuality grade : all) {
            check(ids.add(grade.id()), "two grades share the id " + grade.id());
            check(ElysiumQuality.byId(grade.id()) == grade,
                    grade + " does not come back from its own id");
        }
        check(ElysiumQuality.byId("a_removed_mods_grade") == null,
                "an unknown grade id throws rather than reading as no grade");
    }

    /** A first attempt costs something, and mastery is worth having. */
    private static void thePriceAndThePrize() {
        check(ElysiumQuality.CRUDE.durabilityMultiplier() < 1.0F,
                "a first attempt is no worse than an ordinary one, so there is "
                        + "no bottom rung and nothing to climb from");
        check(ElysiumQuality.MASTERWORK.durabilityMultiplier() > 1.0F,
                "mastery is worth nothing");
        float spread = ElysiumQuality.MASTERWORK.durabilityMultiplier()
                / ElysiumQuality.CRUDE.durabilityMultiplier();
        System.out.println("  ladder: crude "
                + ElysiumQuality.CRUDE.durabilityMultiplier() + "x to masterwork "
                + ElysiumQuality.MASTERWORK.durabilityMultiplier() + "x, a spread of "
                + String.format(java.util.Locale.ROOT, "%.2f", spread) + "x over "
                + ElysiumQuality.MASTERWORK.practice() + " pieces");
        check(spread <= 2.5F,
                "a masterwork lasts " + spread + " times as long as a first "
                        + "attempt. Past about double, everything a player owns "
                        + "has to be re-forged by a specialist and the mark "
                        + "stops being flavour");
        check(spread >= 1.4F,
                "the whole ladder is worth " + spread + "x, which nobody will "
                        + "climb a hundred and twenty-eight pieces for");
    }

    /** Durability never rounds a real item away, and never invents one. */
    private static void durabilityIsSafe() {
        for (ElysiumQuality grade : ElysiumQuality.values()) {
            check(grade.durabilityFor(0) == 0,
                    grade + " gave a durability to something that had none; an "
                            + "ingot with a maximum damage is a broken tool");
            check(grade.durabilityFor(-5) == -5,
                    grade + " changed a negative maximum damage");
            for (int base = 1; base <= 4096; base++) {
                int result = grade.durabilityFor(base);
                check(result >= 1,
                        grade + " reduced a " + base + "-use item to " + result
                                + ", which breaks on the tick it is made");
                check(result <= Math.round(base * 2.0F),
                        grade + " turned " + base + " uses into " + result);
            }
        }
    }

    /** The log stops counting where the ladder stops. */
    private static void theLogIsCapped() {
        check(ElysiumQuality.COUNT_CEILING == ElysiumQuality.MASTERWORK.practice() + 1,
                "the counting ceiling is " + ElysiumQuality.COUNT_CEILING
                        + " and the top grade needs "
                        + ElysiumQuality.MASTERWORK.practice()
                        + "; they are one number written twice");
        check(ElysiumQuality.COUNT_CEILING > ElysiumQuality.MASTERWORK.practice(),
                "the craft log stops counting at " + ElysiumQuality.COUNT_CEILING
                        + " and the top grade needs "
                        + ElysiumQuality.MASTERWORK.practice()
                        + ", so mastery is unreachable");
        check(ElysiumQuality.COUNT_CEILING
                        <= ElysiumQuality.MASTERWORK.practice() * 2,
                "the craft log counts to " + ElysiumQuality.COUNT_CEILING
                        + ", far past anything that buys a grade — a number "
                        + "that grows and means nothing");
        check(ElysiumQuality.forPractice(ElysiumQuality.COUNT_CEILING)
                        == ElysiumQuality.MASTERWORK,
                "a player who has hit the counting ceiling is not a master");
    }

    /**
     * Quality is craftsmanship, and craftsmanship is not power.
     *
     * The one thing this feature must not become. Durability is the whole of
     * what a grade does; the moment it has a second effect, it belongs in the
     * damage pipeline and has to be argued about against everything already
     * there — and a player would have to forge to keep up.
     */
    private static void qualityIsOnlyDurability() {
        for (java.lang.reflect.Method method : ElysiumQuality.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            check(!name.contains("damage") || name.equals("durabilityfor"),
                    "ElysiumQuality has grown " + method.getName()
                            + "; quality is craftsmanship and changes how long "
                            + "a thing lasts, not what it does");
            check(!name.contains("attack") && !name.contains("speed")
                            && !name.contains("armour") && !name.contains("armor"),
                    "ElysiumQuality has grown " + method.getName()
                            + ", which is power rather than craftsmanship");
        }
    }

    public static void main(String[] args) {
        theLadderIsMonotonic();
        theGradesAreDistinct();
        thePriceAndThePrize();
        durabilityIsSafe();
        theLogIsCapped();
        qualityIsOnlyDurability();

        System.out.println("craft: " + ElysiumQuality.values().length
                + " grades from a first attempt to "
                + ElysiumQuality.MASTERWORK.practice() + " pieces — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("the mark records the skill at the time");
    }
}
