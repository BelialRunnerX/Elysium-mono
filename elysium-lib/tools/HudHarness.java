package com.elysium.lib.client;

/**
 * The health bar's geometry, executed without a screen.
 *
 * <h2>What is worth running here</h2>
 *
 * Not the drawing — that needs a window, a font and a GuiGraphics, and mocking
 * those would be testing the mock. What can be checked is the reason the bars
 * exist at all: they replaced rows of icons because an icon is a fixed two
 * points, and this mod's numbers outgrow twenty — maximum health into the
 * hundreds, Fortitude filling vanilla's armour ceiling and spilling into
 * toughness. So the claim under test is that the split holds at <em>every</em>
 * pair of capacities, including the ones vanilla never produces and the
 * degenerate ones a modded effect can, and that the three bars sit where the
 * icons they replaced used to be without overlapping each other.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/hud elysium-lib/tools/HudHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/hud" \
 *         com.elysium.lib.client.HudHarness
 */
public final class HudHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Maximum healths a character in this mod actually reaches. */
    private static final float[] HEALTHS = {
        1.0F, 20.0F, 40.0F, 100.0F, 270.0F, 1000.0F, 20000.0F,
    };

    /** Shield capacities, including none at all. */
    private static final float[] SHIELDS = {
        0.0F, 1.0F, 8.0F, 40.0F, 400.0F, 20000.0F,
    };

    /**
     * The two regions always fill the bar, and neither ever vanishes.
     *
     * A region of zero width is the failure that matters here: it is not a
     * crash and not a visual glitch, it is a bar that silently stops reporting
     * one of the two things standing between the player and death.
     */
    private static void theSplitAlwaysHolds() {
        for (float max : HEALTHS) {
            for (float shield : SHIELDS) {
                int health = ElysiumHealthBar.healthWidth(
                        ElysiumHealthBar.WIDTH, max, shield);
                int rest = ElysiumHealthBar.WIDTH - health;

                check(health >= 1,
                        "at " + max + " max health and a " + shield + " shield the "
                                + "health region is " + health + " pixels wide, so "
                                + "the player's actual health is not shown at all");
                check(health <= ElysiumHealthBar.WIDTH,
                        "the health region is wider than the bar at " + max + "/" + shield);
                check(health + rest == ElysiumHealthBar.WIDTH,
                        "the two regions do not add up to the bar at " + max + "/" + shield);

                if (shield > 0.0F) {
                    check(rest >= 1,
                            "a shield of " + shield + " against " + max + " health "
                                    + "gets no width at all, so a player cannot see "
                                    + "their shield recharge");
                } else {
                    check(health == ElysiumHealthBar.WIDTH,
                            "with no shield the health bar is only " + health
                                    + " of " + ElysiumHealthBar.WIDTH + " pixels, "
                                    + "leaving a permanently empty stub");
                }
            }
        }

        // The whole point: a bar is a fraction, so the denominator is free.
        check(ElysiumHealthBar.healthWidth(ElysiumHealthBar.WIDTH, 20.0F, 0.0F)
                        == ElysiumHealthBar.healthWidth(
                                ElysiumHealthBar.WIDTH, 20000.0F, 0.0F),
                "the bar's geometry changes with maximum health, so it has a "
                        + "ceiling of its own and hearts would have done");

        // A larger shield must never take width *away* from a larger health.
        int small = ElysiumHealthBar.healthWidth(ElysiumHealthBar.WIDTH, 20.0F, 40.0F);
        int large = ElysiumHealthBar.healthWidth(ElysiumHealthBar.WIDTH, 200.0F, 40.0F);
        check(large > small,
                "raising maximum health from 20 to 200 against the same shield "
                        + "did not widen the health region (" + small + " to "
                        + large + "), so the split is not reading the capacities");

        System.out.println("  the split: " + ElysiumHealthBar.WIDTH
                + " pixels divided at every one of " + (HEALTHS.length * SHIELDS.length)
                + " health-and-shield combinations, from 1 health to 20000");
    }

    /** A fraction is a fraction, including where it would divide by zero. */
    private static void fractionsAreSafe() {
        check(ElysiumHealthBar.fraction(0.0F, 0.0F) == 0.0F,
                "an empty capacity produced a fraction instead of nothing");
        check(ElysiumHealthBar.fraction(5.0F, 0.0F) == 0.0F,
                "health against no capacity divided by zero");
        check(ElysiumHealthBar.fraction(-5.0F, 20.0F) == 0.0F,
                "negative health produced a negative bar");
        check(ElysiumHealthBar.fraction(10.0F, 20.0F) == 0.5F,
                "half of a capacity is not half a bar");
        check(ElysiumHealthBar.fraction(4000.0F, 20.0F) == 1.0F,
                "more than the capacity overflowed the bar instead of filling it");
        check(ElysiumHealthBar.fraction(Float.NaN, 20.0F) == 0.0F,
                "a NaN health produced a NaN bar width");

        // Monotonic, at a maximum health nothing in vanilla reaches.
        float previous = -1.0F;
        for (int health = 0; health <= 20000; health += 137) {
            float value = ElysiumHealthBar.fraction(health, 20000.0F);
            check(value >= previous, "the bar fell while health rose at " + health);
            previous = value;
        }
        System.out.println("  the fill: clamped at both ends, monotonic to 20000, "
                + "and safe against a zero capacity and a NaN");
    }

    /** The bar has to sit where the hearts did, and clear of the hotbar. */
    private static void itSitsWhereTheHeartsDid() {
        check(ElysiumHealthBar.WIDTH <= ElysiumHealthBar.LEFT_OF_CENTRE,
                "the bar is wider than the space vanilla's hearts occupied, so it "
                        + "runs under the food bar on the other side of centre");
        // Vanilla's hotbar is 22 pixels tall and sits on the bottom edge.
        check(ElysiumHealthBar.BOTTOM_INSET >= 22,
                "the bar's bottom is " + ElysiumHealthBar.BOTTOM_INSET
                        + " pixels up, which is inside the hotbar");
        check(ElysiumHealthBar.BOTTOM_INSET + ElysiumHealthBar.HEIGHT <= 39,
                "the bar's top is above where vanilla drew hearts, so it collides "
                        + "with the armour row rather than replacing the hearts");
        check(ElysiumHealthBar.LOW > 0.0F && ElysiumHealthBar.LOW < 0.5F,
                "the low-health threshold is " + ElysiumHealthBar.LOW
                        + ", so the bar is either always alarming or never");
        System.out.println("  where it sits: " + ElysiumHealthBar.WIDTH + " pixels "
                + "in the hearts' own column, clear of the hotbar and the armour row");
    }

    /**
     * Food and armour are the same arithmetic, so they get the same checks.
     *
     * Armour is the one that needed the bar: vanilla's ten icons stop at 20
     * points, its ARMOR attribute stops paying at 30, and Fortitude now fills
     * both and keeps going into toughness. Two ceilings on one bar is exactly
     * the health-and-shield problem with different numbers.
     */
    private static void theOtherTwoUseTheSameRule() {
        check(ElysiumStatusBars.FOOD_CEILING == 20.0F,
                "food is not twenty points, which is the one number in this "
                        + "layer that vanilla fixes and will not change");

        int armourOnly = com.elysium.lib.client.ElysiumMeter.split(
                ElysiumHealthBar.WIDTH, ElysiumStatusBars.ARMOUR_CEILING, 0.0F);
        check(armourOnly == ElysiumHealthBar.WIDTH,
                "with no toughness the armour bar is only " + armourOnly
                        + " of " + ElysiumHealthBar.WIDTH + " pixels, leaving a "
                        + "permanently empty stub on a player in plain iron");

        int withTough = com.elysium.lib.client.ElysiumMeter.split(
                ElysiumHealthBar.WIDTH, ElysiumStatusBars.ARMOUR_CEILING,
                ElysiumStatusBars.TOUGHNESS_CEILING);
        check(withTough >= 1 && withTough <= ElysiumHealthBar.WIDTH - 1,
                "the armour and toughness regions do not both exist at "
                        + ElysiumStatusBars.ARMOUR_CEILING + "/"
                        + ElysiumStatusBars.TOUGHNESS_CEILING);
        check(withTough > ElysiumHealthBar.WIDTH / 2,
                "toughness takes more of the bar than armour does, though it is "
                        + "the smaller ceiling and the rarer stat");

        check(ElysiumStatusBars.HUNGRY > 0.0F && ElysiumStatusBars.HUNGRY < 0.5F,
                "the hunger threshold is " + ElysiumStatusBars.HUNGRY
                        + ", so the food bar is either always alarming or never");

        System.out.println("  food and armour: 20 points of food, "
                + (int) ElysiumStatusBars.ARMOUR_CEILING + " of armour then "
                + (int) ElysiumStatusBars.TOUGHNESS_CEILING + " of toughness, "
                + "split " + withTough + "/"
                + (ElysiumHealthBar.WIDTH - withTough));
    }

    /**
     * Three bars, one hotbar's width, and nothing landing on anything else.
     *
     * The arithmetic is trivial and the failure is not: two bars an inch apart
     * on a mockup are two bars on top of each other at a GUI scale nobody
     * tested, and the symptom is a screen that looks broken rather than one
     * that reports a wrong number.
     */
    private static void theThreeBarsDoNotCollide() {
        int lineHeight = 9;         // vanilla's font, which is what ElysiumUI uses

        // Health and food share a row, mirrored about the centre.
        int healthLeft = -ElysiumHealthBar.LEFT_OF_CENTRE;
        int healthRight = healthLeft + ElysiumHealthBar.WIDTH;
        int foodLeft = ElysiumStatusBars.RIGHT_OF_CENTRE;
        check(healthRight <= foodLeft,
                "the health bar ends " + healthRight + " from the centre and the "
                        + "food bar starts at " + foodLeft + " — they overlap "
                        + "across the middle of the screen");
        check(foodLeft + ElysiumHealthBar.WIDTH <= ElysiumHealthBar.LEFT_OF_CENTRE,
                "the food bar runs past the right end of the hotbar");

        // Armour sits above health's text row, and clear of it.
        int healthBar = -ElysiumHealthBar.BOTTOM_INSET - ElysiumHealthBar.HEIGHT;
        int healthText = healthBar - lineHeight - 1;
        int armourBar = healthText - ElysiumStatusBars.ARMOUR_LIFT
                - ElysiumStatusBars.ARMOUR_HEIGHT;
        check(armourBar + ElysiumStatusBars.ARMOUR_HEIGHT <= healthText,
                "the armour bar overlaps the health readout's own text");
        check(armourBar >= -60,
                "the armour bar is " + (-armourBar) + " pixels up the screen, "
                        + "far above where vanilla's armour row was and into "
                        + "whatever else is drawn there");
        check(ElysiumStatusBars.ARMOUR_HEIGHT < ElysiumHealthBar.HEIGHT,
                "the armour bar is as tall as the health bar, so the two read "
                        + "as equally important and the eye has no anchor");

        System.out.println("  the layout: health and food mirrored about the "
                + "centre on one row, armour " + (-armourBar) + " up, health "
                + (-healthBar) + " up, hotbar at 22");
    }

    public static void main(String[] args) {
        theSplitAlwaysHolds();
        fractionsAreSafe();
        itSitsWhereTheHeartsDid();
        theOtherTwoUseTheSameRule();
        theThreeBarsDoNotCollide();

        System.out.println("hud: the split, the fill, and where all three bars "
                + "sit — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("a bar does not care how large its denominator is, "
                + "which is the whole reason it replaced the icons");
    }
}
