package com.elysium.flora.rules;

/**
 * The growing rules, executed.
 *
 * The failures worth pinning are the ones that would turn a slow, always-
 * available road into a faster one that replaces what it was meant to sit
 * beside: a crop that outpays an ore, a fibre that is a string on its own, and
 * a fuel good enough that nobody mines coal again.
 */
public final class FloraHarness {

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
        growing();
        harvesting();
        fortune();
        fibre();
        burning();
        wild();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: string and charcoal you can farm, on a "
                + "slower road than the ones they sit beside");
    }

    private static void growing() {
        check(FloraRules.STAGES == 8,
                "a crop with a different number of stages from every other crop in the "
                        + "game, which a player would have to learn separately");
        check(FloraRules.MATURE == FloraRules.STAGES - 1,
                "the ripe stage is not the last one");
        check(!FloraRules.ripe(0), "a seed was ripe");
        check(!FloraRules.ripe(FloraRules.MATURE - 1), "a crop was ripe one stage early");
        check(FloraRules.ripe(FloraRules.MATURE), "a fully grown crop was not ripe");
        check(FloraRules.ripe(99), "an over-aged crop stopped being ripe");
        check(FloraRules.clampAge(-3) == 0, "a negative age came out negative");
        check(FloraRules.clampAge(99) == FloraRules.MATURE,
                "an age past the last stage was not clamped");
        for (int age = 0; age <= FloraRules.MATURE; age++) {
            check(FloraRules.clampAge(age) == age, "a valid age was changed, at " + age);
        }
    }

    private static void harvesting() {
        check(FloraRules.BASE_YIELD >= 1, "a ripe plant gave nothing");
        check(FloraRules.bonus(0) == 0, "the first tier already paid a bonus");
        check(FloraRules.bonus(-9) == 0, "a negative tier paid a bonus");
        check(FloraRules.bonus(FloraRules.TIERS_PER_EXTRA) == 1,
                "the first extra did not arrive at the tier promised for it");

        // Slower than the ground. A field is replanted; a vein is not.
        check(FloraRules.TIERS_PER_EXTRA > 3,
                "a crop pays out as fast as an ore does, so farming replaces mining");

        int last = -1;
        for (int tier = 0; tier <= 400; tier++) {
            int now = FloraRules.bonus(tier);
            check(now >= last, "the bonus fell as the tier rose, at " + tier);
            last = now;
        }
        check(FloraRules.bonus(600) > FloraRules.bonus(60),
                "the bonus stopped climbing, so a deep world stops paying");

        check(FloraRules.bonusChance(0) == 0.0F, "tier zero was already sometimes lucky");
        float previous = -1.0F;
        for (int tier = 0; tier <= 1000; tier++) {
            float share = FloraRules.bonusChance(tier);
            check(share >= 0.0F && share < 1.0F, "harvest became certain at tier " + tier);
            check(share >= previous, "the chance fell as the tier rose, at " + tier);
            previous = share;
        }
        check(FloraRules.bonusChance((int) FloraRules.BONUS_PACE) == 0.5F,
                "the pace does not mean what it says");

        for (int tier = 0; tier <= 60; tier++) {
            check(FloraRules.harvest(tier, 0.0D) >= FloraRules.BASE_YIELD,
                    "a ripe plant gave less than its base, at " + tier);
            check(FloraRules.harvest(tier, 0.999999D) == FloraRules.BASE_YIELD,
                    "the unluckiest roll still paid a bonus, at " + tier);
            check(FloraRules.harvest(tier, 0.0D)
                            == FloraRules.BASE_YIELD + FloraRules.bonus(tier),
                    "the luckiest roll did not pay the whole bonus, at " + tier);
        }
    }

    private static void fortune() {
        check(FloraRules.fortuned(1, 0, 0.99D) == 1, "no fortune changed the yield");
        check(FloraRules.fortuned(0, 5, 0.0D) == 0, "fortune conjured fibre from nothing");
        check(FloraRules.fortuned(-2, 5, 0.0D) == 0, "a negative yield came out negative");
        for (int fortune = 1; fortune <= 10; fortune++) {
            check(FloraRules.fortuned(3, fortune, 0.0D) == 3,
                    "fortune " + fortune + " made the worst roll worse than none");
            for (double roll = 0.0D; roll < 1.0D; roll += 0.01D) {
                int got = FloraRules.fortuned(3, fortune, roll);
                check(got >= 3, "fortune " + fortune + " fell below the unenchanted yield");
                check(got <= 3 * (fortune + 1),
                        "fortune " + fortune + " paid past its own ceiling");
            }
        }
    }

    private static void fibre() {
        check(FloraRules.FIBRE_PER_STRING > 1,
                "one fibre is a string, so a single plant replaces a spider");
        check(FloraRules.TUFT_PER_STRING > FloraRules.FIBRE_PER_STRING,
                "the softer fibre is not the more generous one, so one of the two crops "
                        + "has no reason to exist");
        check(FloraRules.fibreFor(0, FloraRules.FIBRE_PER_STRING) == 0,
                "no string cost something");
        check(FloraRules.fibreFor(1, FloraRules.FIBRE_PER_STRING)
                        == FloraRules.FIBRE_PER_STRING,
                "one string cost the wrong number of fibres");
        check(FloraRules.fibreFor(-4, FloraRules.FIBRE_PER_STRING) == 0,
                "a negative order of string cost something");
        check(FloraRules.fibreFor(7, 0) == 7,
                "a zero rate divided by nothing rather than being floored at one");
    }

    private static void burning() {
        check(FloraRules.CHARCOAL_TICKS > FloraRules.COAL_TICKS,
                "a fuel that took a trip to the Nether and a sapling home is no better "
                        + "than the coal under the player's feet");
        check(FloraRules.CHARCOAL_TICKS < FloraRules.COAL_TICKS * 2,
                "one farmed fuel is worth two coal, so nobody mines coal again");
        check(FloraRules.blockTicks() == FloraRules.CHARCOAL_TICKS * 9,
                "a block of charcoal is not worth the nine that made it, so packing it "
                        + "either loses or conjures fuel");
        check(FloraRules.PLANK_TICKS < FloraRules.CHARCOAL_TICKS,
                "raw planks burn as long as the charcoal made from them");
        check(FloraRules.PLANK_TICKS == 300,
                "these planks burn for a different time from every other plank in the "
                        + "game");
    }

    private static void wild() {
        check(FloraRules.WILD_TRIES >= 1, "no wild patch ever generates, so the crop "
                + "cannot be started at all");
        check(FloraRules.WILD_TRIES <= 4,
                "wild patches are common enough to harvest, which removes the reason to "
                        + "plant anything");
        check(FloraRules.WILD_PATCH > 1, "a wild patch is a single plant");
        check(FloraRules.WILD_SEEDS >= 2,
                "one wild plant gives one seed, so a player has to find a second patch "
                        + "before they can plant anything");
    }
}
