package com.elysium.agronomy.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.HashSet;
import java.util.Set;

/**
 * Things that grow, executed.
 *
 * <p>One claim here matters more than everything else: an acre of seeds is
 * worse than a mine. That is the property whose failure has ruined more
 * modpacks than any other single number, it is invisible until a player has
 * already stopped mining, and it is the reason this file exists.
 */
public final class AgronomyHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    private static void near(float got, float want, float slack, String what) {
        check(Math.abs(got - want) <= slack,
                what + ": expected about " + want + ", got " + got);
    }

    public static void main(String[] args) {
        theLadder();
        theMargin();
        thePot();
        theWork();
        theHive();
        theRock();
        theDiet();
        theFrontier();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a farm is worse than a mine at every tier "
                + "and every size worth building, a pot is faster for the trouble of it, "
                + "a crowded hive slows rather than stopping, and a varied diet climbs, "
                + "curves and stops");
    }

    // ==================================================================

    private static void theLadder() {
        check(AgronomyRules.TIERS >= 3,
                "there are fewer than three tiers of essence, so there is no ladder to "
                        + "climb");
        int slower = 0;
        for (int tier = 2; tier <= AgronomyRules.TIERS; tier++) {
            if (AgronomyRules.ticksAt(tier) > AgronomyRules.ticksAt(tier - 1)) {
                slower++;
            }
        }
        check(slower == AgronomyRules.TIERS - 1,
                "some tier of crop grows no slower than the one below it, so the ladder "
                        + "has a rung that costs nothing");
        near(AgronomyRules.ticksAt(2),
                AgronomyRules.ticksAt(1) * AgronomyRules.TIER_SLOWER, 1.0F,
                "the second tier is not the stated multiple of the first");
        check(AgronomyRules.ticksAt(AgronomyRules.TIERS + 5)
                        == AgronomyRules.ticksAt(AgronomyRules.TIERS),
                "a tier past the top of the ladder keeps getting slower, so a data file "
                        + "can invent a sixth tier by accident");

        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            AgronomyRules.Crop crop = AgronomyRules.cropAt(tier);
            check(crop.yield() == AgronomyRules.FIRST_CROP.yield(),
                    "a higher tier yields more per harvest as well as growing slower, so "
                            + "the ladder is made of two things and neither can be tuned "
                            + "on its own");
            check(crop.perHour() > 0.0F, "a crop yields nothing in an hour");
        }

        boolean refused = false;
        try {
            new AgronomyRules.Crop(0, 100, 1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a crop below the first tier can be declared");
        refused = false;
        try {
            new AgronomyRules.Crop(1, 0, 1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a crop that grows instantly can be declared");
    }

    // ==================================================================

    /**
     * The one that matters.
     *
     * <p>Checked at every tier, and at sizes a player would actually build:
     * a window box, a room, a floor, and a farm bigger than anybody sane would
     * make. The margin is allowed to be crossed by the last of those — the
     * point is that it takes a deliberate industrial farm to beat a mine, not
     * a corner of a basement.
     */
    private static void theMargin() {
        check(AgronomyRules.BEST_MULTIPLIER == 3,
                "the best ore processing in the pack is not three, so this module is "
                        + "comparing a farm against a mine that does not exist");
        check(AgronomyRules.BEST_MULTIPLIER > IndustryRules.SEPARATOR_MULTIPLIER,
                "the ceiling this module checks against is no better than Age II's, so "
                        + "Age III's tripling is not in the comparison at all");
        check(AgronomyRules.FARM_AT_MOST > 0.0F && AgronomyRules.FARM_AT_MOST < 1.0F,
                "a farm is allowed to match or beat a mine, which is the failure this "
                        + "whole file exists to prevent");

        float mined = AgronomyRules.minedPerHour();
        check(mined > 0.0F, "an hour of mining yields nothing");

        // A window box and a room. Not a warehouse: the point is not that a
        // farm may never beat a mine, it is that beating one has to be a
        // deliberate industrial project rather than a corner of a basement.
        for (int plots : new int[] {9, 64}) {
            for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
                float farmed = AgronomyRules.farmedPerHour(tier, plots);
                check(farmed <= mined * AgronomyRules.FARM_AT_MOST,
                        "a tier " + tier + " farm of " + plots + " plots yields " + farmed
                                + " an hour against a mine's " + mined + ", which is past "
                                + "the margin — three ages of ore ladder stop mattering "
                                + "at that point");
            }
        }

        // And the number itself has to be a number, not infinity.
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            int room = AgronomyRules.plotsBefore(tier);
            check(room > 0 && room < Integer.MAX_VALUE,
                    "there is no size at which a tier " + tier + " farm beats a mine, so "
                            + "either the crop yields nothing or the comparison is not "
                            + "being made");
            check(AgronomyRules.farmedPerHour(tier, room)
                            <= mined * AgronomyRules.FARM_AT_MOST,
                    "the largest farm the rules call acceptable is already past the "
                            + "margin at tier " + tier);
            check(AgronomyRules.farmedPerHour(tier, room + 1)
                            > mined * AgronomyRules.FARM_AT_MOST
                            || room > 100_000,
                    "the largest acceptable tier " + tier + " farm is smaller than it "
                            + "needs to be, so the margin is stricter than it says");
            check(room >= 128,
                    "a tier " + tier + " farm beats a mine at " + room + " plots, which "
                            + "is small enough to build in a basement without meaning to "
                            + "— overtaking a mine is supposed to be a project");
        }

        // A farm of a higher tier must be worse, not better.
        for (int tier = 2; tier <= AgronomyRules.TIERS; tier++) {
            check(AgronomyRules.farmedPerHour(tier, 64)
                            < AgronomyRules.farmedPerHour(tier - 1, 64),
                    "a tier " + tier + " farm out-yields a tier " + (tier - 1) + " one of "
                            + "the same size, so the expensive seeds are the efficient "
                            + "ones and the ladder runs backwards");
        }

        check(AgronomyRules.ESSENCE_PER_INGOT > AgronomyRules.ESSENCE_PER_STEP,
                "making the material outright costs no more than climbing a rung, so "
                        + "nobody ever climbs");
    }

    // ==================================================================

    private static void thePot() {
        near(AgronomyRules.potSpeed(AgronomyRules.LEAST_LIGHT, false, 0.0F),
                AgronomyRules.POT_FASTER, 0.001F,
                "a pot in enough light is not faster than the ground, so there is no "
                        + "reason to build one");
        near(AgronomyRules.potSpeed(AgronomyRules.LEAST_LIGHT - 1, true, 1.0F), 0.0F,
                0.001F, "a pot in the dark grows things, so light is not a requirement "
                        + "and a farm needs no windows");
        check(AgronomyRules.potSpeed(AgronomyRules.LEAST_LIGHT, true, 1.0F)
                        > AgronomyRules.potSpeed(AgronomyRules.LEAST_LIGHT, false, 1.0F),
                "an accelerator makes no difference, so the machine is decoration");
        near(AgronomyRules.potSpeed(15, true, 0.0F),
                AgronomyRules.potSpeed(15, false, 0.0F), 0.001F,
                "an accelerator on a dead grid still works, so it costs nothing to run");

        // And the share is a share: half the power, half the extra speed.
        float bare = AgronomyRules.potSpeed(15, false, 0.0F);
        float full = AgronomyRules.potSpeed(15, true, 1.0F);
        float half = AgronomyRules.potSpeed(15, true, 0.5F);
        near(half, bare + (full - bare) / 2.0F, 0.001F,
                "a half-fed accelerator does not give half its extra speed, so the grid "
                        + "share means something different here from everywhere else");
        near(AgronomyRules.potSpeed(15, true, 4.0F), full, 0.001F,
                "a grid with more than the accelerator needs makes it faster than full");

        check(AgronomyRules.ACCELERATOR.draw() > 0.0F,
                "an accelerator draws nothing, so it is free speed");
        check(AgronomyRules.ACCELERATOR.draw() < IndustryRules.STEAM_TURBINE.output(),
                "one accelerator needs more than a whole steam turbine, so the first one "
                        + "a player builds takes their entire grid");
    }

    /**
     * Work, and the countdown that claims to describe it.
     *
     * Written separately from the pot that counts it, so the only thing
     * keeping them honest is running one against the other.
     */
    private static void theWork() {
        check(AgronomyRules.workPerTick(1.0F) == AgronomyRules.WORK_FULL,
                "a pot at speed one is not doing a tick's work in a tick");
        check(AgronomyRules.workPerTick(0.0F) == 0,
                "a pot in the dark makes progress");
        check(AgronomyRules.workPerTick(0.0001F) >= 1,
                "a pot growing very slowly makes no progress at all, so 'slow' and "
                        + "'stopped' are the same state");

        int[] jobs = {1, 60, 2_400, 12_000, 192_000};
        float[] speeds = {1.0F, AgronomyRules.POT_FASTER, 6.0F, 0.5F};
        for (float speed : speeds) {
            for (int ticks : jobs) {
                int counted = 0;
                int work = AgronomyRules.workFor(ticks);
                int per = AgronomyRules.workPerTick(speed);
                int done = 0;
                while (done < work) {
                    done += per;
                    counted++;
                }
                check(counted == AgronomyRules.ticksAtSpeed(ticks, speed),
                        "a " + ticks + "-tick growth at speed " + speed + " actually "
                                + "takes " + counted + " ticks, but the readout says "
                                + AgronomyRules.ticksAtSpeed(ticks, speed));
            }
            check(AgronomyRules.ticksAtSpeed(12_000, speed) > 0,
                    "a growth at speed " + speed + " finishes instantly");
        }
        check(AgronomyRules.ticksAtSpeed(12_000, 1.0F) == 12_000,
                "a growth at speed one does not take the ticks it says, so the number in "
                        + "a crop's definition means nothing by itself");
        check(AgronomyRules.ticksAtSpeed(12_000, AgronomyRules.POT_FASTER)
                        < AgronomyRules.ticksAtSpeed(12_000, 1.0F),
                "a pot is no quicker than the ground after all");
        check(AgronomyRules.ticksAtSpeed(12_000, 0.0F) == Integer.MAX_VALUE,
                "a growth in the dark finishes");
    }

    private static void theHive() {
        near(AgronomyRules.combsPerHour(0, 100), 0.0F, 0.001F, "an empty hive produces");
        check(AgronomyRules.combsPerHour(4, 100) > 0.0F, "a well fed hive produces nothing");
        check(AgronomyRules.combsPerHour(AgronomyRules.MOST_BEES + 6, 1000)
                        == AgronomyRules.combsPerHour(AgronomyRules.MOST_BEES, 1000),
                "a hive works more bees than it says it holds");

        // Crowding is a fraction, not a stop.
        float roomy = AgronomyRules.combsPerHour(8, 8 * AgronomyRules.FLOWERS_PER_BEE);
        float crowded = AgronomyRules.combsPerHour(8, 2);
        check(crowded > 0.0F,
                "a hive with too few flowers stops entirely, so a player who adds one bee "
                        + "too many finds it switched off with no explanation");
        check(crowded < roomy, "flowers make no difference to a hive");
        near(AgronomyRules.combsPerHour(8, 8 * AgronomyRules.FLOWERS_PER_BEE * 4), roomy,
                0.001F, "more flowers than the bees want makes a hive faster than full");

        for (int bees = 1; bees <= AgronomyRules.MOST_BEES; bees++) {
            float fed = AgronomyRules.combsPerHour(bees,
                    bees * AgronomyRules.FLOWERS_PER_BEE);
            float fewer = AgronomyRules.combsPerHour(bees - 1,
                    (bees - 1) * AgronomyRules.FLOWERS_PER_BEE);
            check(fed > fewer,
                    "the " + bees + "th bee in a hive with flowers enough adds nothing");
        }
    }

    private static void theRock() {
        for (int tier = 1; tier < AgronomyRules.ROCK_PER_HOUR.length; tier++) {
            check(AgronomyRules.ROCK_PER_HOUR[tier] > AgronomyRules.ROCK_PER_HOUR[tier - 1],
                    "a tier of rock generator makes no more than the one below it");
            check(AgronomyRules.rockDraw(tier).draw()
                            > AgronomyRules.rockDraw(tier - 1).draw(),
                    "a faster rock generator costs no more to run");
        }
        near(AgronomyRules.rockDraw(0).draw(), 0.0F, 0.001F,
                "the first rock generator needs power, so the oldest trick in the game "
                        + "is behind the second age");
        near(AgronomyRules.rockPerHour(0, 0.0F), AgronomyRules.ROCK_PER_HOUR[0], 0.001F,
                "the first rock generator stops without power, which it does not need");
        near(AgronomyRules.rockPerHour(2, 0.5F), AgronomyRules.ROCK_PER_HOUR[2] / 2.0F,
                0.001F, "a half-fed rock generator does not make half the rock");
        near(AgronomyRules.rockPerHour(2, 5.0F), AgronomyRules.ROCK_PER_HOUR[2], 0.001F,
                "a rock generator on a surplus grid makes more than its rating");
        check(AgronomyRules.rockPerHour(9, 1.0F)
                        == AgronomyRules.rockPerHour(AgronomyRules.ROCK_PER_HOUR.length - 1,
                        1.0F),
                "a tier past the top of the table keeps going, so a data file can invent "
                        + "a fourth generator");
    }

    private static void theDiet() {
        near(AgronomyRules.extraHearts(0), 0.0F, 0.001F,
                "a player who has eaten nothing already has the bonus");
        near(AgronomyRules.extraHearts(AgronomyRules.FOODS_FOR_FULL),
                AgronomyRules.MOST_EXTRA_HEARTS, 0.001F,
                "eating everything does not give the whole bonus");
        near(AgronomyRules.extraHearts(AgronomyRules.FOODS_FOR_FULL * 4),
                AgronomyRules.MOST_EXTRA_HEARTS, 0.001F,
                "the bonus keeps climbing past the cap, so a pack that adds foods adds "
                        + "health without meaning to");
        near(AgronomyRules.extraHearts(-5), 0.0F, 0.001F,
                "a negative number of foods gives a bonus");

        float last = -1.0F;
        for (int foods = 0; foods <= AgronomyRules.FOODS_FOR_FULL * 2; foods++) {
            float now = AgronomyRules.extraHearts(foods);
            check(now >= last,
                    "the bonus went down between " + (foods - 1) + " foods and " + foods
                            + ", so a player lost hearts for eating something new");
            check(now <= AgronomyRules.MOST_EXTRA_HEARTS + 0.001F,
                    "the bonus passed its cap at " + foods + " foods");
            last = now;
        }

        // It has to curve, or the last food is worth as much as the first.
        float firstTen = AgronomyRules.extraHearts(10) - AgronomyRules.extraHearts(0);
        float lastTen = AgronomyRules.extraHearts(AgronomyRules.FOODS_FOR_FULL)
                - AgronomyRules.extraHearts(AgronomyRules.FOODS_FOR_FULL - 10);
        check(firstTen > lastTen * 1.5F,
                "the first ten new foods are worth about what the last ten are, so trying "
                        + "things is a checklist rather than a discovery");
        check(AgronomyRules.extraHearts(10) > AgronomyRules.MOST_EXTRA_HEARTS / 2.0F,
                "a player who has tried ten things has less than half the bonus, so most "
                        + "of it is reserved for completionists");

        near(AgronomyRules.extraHealth(AgronomyRules.FOODS_FOR_FULL),
                AgronomyRules.MOST_EXTRA_HEARTS * 2.0F, 0.001F,
                "hearts and half-hearts disagree, so the attribute and the readout are "
                        + "different numbers");
    }

    private static void theFrontier() {
        check(!AgronomyRules.FRONTIERS.isEmpty(),
                "there are no frontier crops, which is the one thing this module was "
                        + "asked for by name");
        Set<String> worlds = new HashSet<>();
        boolean anywhere = false;
        for (AgronomyRules.Frontier frontier : AgronomyRules.FRONTIERS) {
            check(worlds.add(frontier.world()),
                    frontier.world() + " is named twice, so one of its crops is "
                            + "unreachable");
            if (frontier.growsAnywhere()) {
                anywhere = true;
            }
            check(frontier.leastLight() <= frontier.mostLight(),
                    frontier.world() + "'s crop wants more light than it will tolerate, "
                            + "so it grows nowhere at all");
            check(frontier.happy((frontier.leastLight() + frontier.mostLight()) / 2, true),
                    frontier.world() + "'s crop refuses the conditions it asks for");
            check(!frontier.happy(frontier.leastLight() - 1, true)
                            || frontier.leastLight() == 0,
                    frontier.world() + "'s crop grows below the light it says it needs");
            check(!frontier.happy(frontier.mostLight() + 1, true)
                            || frontier.mostLight() >= 15,
                    frontier.world() + "'s crop grows above the light it says it will "
                            + "tolerate");
        }
        check(!anywhere,
                "a frontier crop grows in the dark with air, which is everywhere — so it "
                        + "is not a frontier crop, it is a crop");
        check(worlds.contains("luna"),
                "the moon grows nothing, and it is the first world a player reaches");
        for (AgronomyRules.Frontier frontier : AgronomyRules.FRONTIERS) {
            if (frontier.needsVacuum()) {
                check(!frontier.happy(10, false),
                        frontier.world() + "'s crop grows in air as well as vacuum, so "
                                + "the one thing that makes it a vacuum crop does "
                                + "nothing");
            }
        }
    }
}
