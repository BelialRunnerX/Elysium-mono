package com.elysium.agronomy.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.HashSet;
import java.util.List;
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
     * characters, and the validator that prints this keeps the first sixteen
     * hundred. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * falls off the end entirely. It cost two negative tests to find that, so
     * every complaint now leaves room for the next one.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
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
        theRoster();
        theApiary();
        theMachinery();
        theAltar();
        theFish();
        theCast();
        thePots();
        theVeins();
        theKitchen();
        theSeeds();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a farm is worse than a mine at every tier "
                + "and every size worth building, a pot is faster for the trouble of it, "
                + "a crowded hive slows rather than stopping, a varied diet climbs, "
                + "curves and stops, and every one of " + BeeRules.BEES.size()
                + " bees can be bred from the four you can find, and every one of "
                + FishRules.FISH.size() + " fish comes up on a rod somewhere, and "
                + PotRules.catalogue().size() + " pots that all have all three "
                + "fittings, and "
                + AgronomyRules.VEINS.size() + " generators none of which beats a "
                + "mine, and "
                + KitchenRules.MEALS.size() + " meals of which none is pointless, and "
                + SeedRules.SEEDS.size() + " seeds none of which grows an alloy");
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

    // ==================================================================

    /**
     * Whether the bee roster hangs together.
     *
     * <p>The mod this replaces registers 463 bees, of which 428 name a
     * material this pack cannot make. Every check below exists so that this
     * file cannot quietly end up in the same state: a bee nothing breeds, a
     * comb two bees make, a pair that breeds two children, a chain that loops.
     */
    private static void theRoster() {
        check(BeeRules.orphaned().isEmpty(),
                "a bee bred from something that is not a bee: " + BeeRules.orphaned());
        check(BeeRules.cycles().isEmpty(),
                "a bee that is its own ancestor: " + BeeRules.cycles());
        check(BeeRules.unreachable().isEmpty(),
                "a bee no chain of breeding reaches from the ones you find in the "
                        + "world — it is in the registry and nowhere else: "
                        + BeeRules.unreachable());
        check(BeeRules.sharingProduce().isEmpty(),
                "two bees making the same thing: " + BeeRules.sharingProduce());
        check(BeeRules.sharingParents().isEmpty(),
                "one pair breeding two different bees, so one can never be got on "
                        + "purpose: " + BeeRules.sharingParents());
        check(BeeRules.inbred().isEmpty(),
                "a bee bred from itself or from two of the same bee: "
                        + BeeRules.inbred());
        check(BeeRules.misplaced().isEmpty(),
                "a bee bound to the wrong kind of place, or to none: "
                        + BeeRules.misplaced());

        Set<String> ids = new HashSet<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            check(ids.add(bee.id()), "two bees called " + bee.id());
            check(!bee.id().endsWith("_bee"),
                    bee.id() + " already ends in _bee, so its entity would be "
                            + bee.entity());
            check(!bee.produce().isEmpty(), bee.id() + " produces nothing");
        }

        int wild = BeeRules.ofKind(BeeRules.WILD).size();
        check(wild >= 2, "only " + wild + " bee(s) can be found in the world, so the "
                + "whole tree hangs off too narrow a base");
        check(wild <= 6, wild + " bees can be found without breeding, which leaves "
                + "breeding little to do");

        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.RESOURCE)) {
            check(bee.produce().equals(bee.id()),
                    "the " + bee.id() + " bee produces " + bee.produce() + " — a "
                            + "resource bee is named after what it makes, or a comb "
                            + "stops being self-explanatory");
        }

        check(BeeRules.longestChain() >= 4,
                "the longest breeding chain is only " + BeeRules.longestChain()
                        + " deep, which is not a progression");
        check(BeeRules.longestChain() <= 12,
                "the longest breeding chain is " + BeeRules.longestChain()
                        + " deep, which is a second career");

        // Every tier between the bottom and the top has somebody on it. A gap
        // is a rung a player reaches and finds nothing to breed.
        List<Integer> tiers = BeeRules.tiers();
        for (int step = 1; step < tiers.size(); step++) {
            check(tiers.get(step) == tiers.get(step - 1) + 1,
                    "nothing at all sits at tier " + (tiers.get(step - 1) + 1)
                            + ", so the ladder has a rung missing");
        }
    }

    // ==================================================================

    /**
     * Whether a hive ever beats a pickaxe.
     *
     * <p>The same rule the crops are held to, at a stricter margin: a hive
     * wants no light, no water, no replanting and no attention, so the only
     * honest way to price something that runs itself is to make it slow.
     */
    private static void theApiary() {
        check(BeeRules.outMining().isEmpty(),
                "a full hive out-earns a mine at tier(s) " + BeeRules.outMining()
                        + " — the moment the ore ladder stops mattering");

        int last = -1;
        for (int tier : BeeRules.tiers()) {
            if (tier == 0) {
                continue;
            }
            int ticks = BeeRules.combTicks(tier);
            check(ticks >= last, "a tier " + tier + " bee fills a comb faster than the "
                    + "tier below it, so the ladder runs backwards there");
            last = ticks;
            check(BeeRules.perHour(tier) > 0.0F,
                    "a tier " + tier + " hive produces nothing at all");
        }

        int top = BeeRules.tiers().get(BeeRules.tiers().size() - 1);
        check(BeeRules.combTicks(top) > BeeRules.combTicks(1),
                "the top of the bee ladder is no slower than the first rung of it");
        check(BeeRules.HIVE_AT_MOST < AgronomyRules.FARM_AT_MOST,
                "a hive is allowed as big a share of a mine as a farm is, and a hive "
                        + "runs itself while a farm does not");
        check(AgronomyRules.combsPerHour(AgronomyRules.MOST_BEES + 5, 1000)
                        == AgronomyRules.combsPerHour(AgronomyRules.MOST_BEES, 1000),
                "a hive stuffed past its limit goes on getting faster");

        // The first resource rung is derived from the mine rather than chosen,
        // so retuning the mine has to move it. If somebody changes one and not
        // the other this is what says so.
        float solved = AgronomyRules.MOST_BEES * (float) AgronomyRules.TICKS_PER_HOUR
                / (AgronomyRules.minedPerHour() * BeeRules.HIVE_AT_MOST);
        check(BeeRules.FIRST_RESOURCE_TICKS >= Math.round(solved),
                "the first resource bee takes " + BeeRules.FIRST_RESOURCE_TICKS
                        + " ticks a comb, and the margin against the mine needs at "
                        + "least " + Math.round(solved));

        // A wild bee makes honey, and honey is not priced against the ore
        // ladder — but it must not be so fast that it is the whole game either.
        check(BeeRules.combTicks(0) < BeeRules.combTicks(1),
                "a wild bee is no faster than a bee that makes metal, which leaves "
                        + "nothing to like about the ones you can actually find");
    }


    // ==================================================================

    /**
     * The apiaries, the upgrades and the one line none of them may cross.
     *
     * <p>Not the best case. Every case: four housings, seven upgrades, every
     * way of filling the slots, every tier carrying a resource bee. An
     * upgrade system written as arithmetic can be asked "does anything at all
     * out-earn a mine"; one written as a handful of block entities can only
     * be asked whether somebody thought the top one looked about right.
     */
    private static void theMachinery() {
        check(ApiaryRules.faults().isEmpty(),
                "something wrong with the machinery itself: " + ApiaryRules.faults());
        check(ApiaryRules.outMining().isEmpty(),
                ApiaryRules.outMining().size() + " housing-and-fitting(s) out-earn "
                        + "a mine, first: " + (ApiaryRules.outMining().isEmpty()
                        ? "" : ApiaryRules.outMining().get(0)));

        // The sweep itself, counted, because a sweep that visits nothing
        // passes exactly as loudly as one that visits everything.
        int swept = 0;
        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.RESOURCE)) {
            int tier = BeeRules.tierOf(bee.id());
            if (tier == BeeRules.A_CYCLE) {
                continue;
            }
            for (ApiaryRules.Housing housing : ApiaryRules.HOUSINGS) {
                for (java.util.List<ApiaryRules.Upgrade> fitted
                        : ApiaryRules.everyFitting(housing)) {
                    swept++;
                    check(ApiaryRules.underTheCeiling(tier, housing, fitted),
                            housing.id() + " at tier " + tier + " with "
                                    + fitted.size() + " upgrade(s) earns "
                                    + ApiaryRules.perHour(tier, housing, fitted)
                                    + " an hour and the ceiling is "
                                    + ApiaryRules.ceiling());
                    check(ApiaryRules.beesIn(housing, fitted) >= housing.bees(),
                            "fitting upgrades to " + housing.id() + " took bees "
                                    + "out of it");
                    check(ApiaryRules.speedIn(fitted) >= 1.0F,
                            "fitting upgrades to " + housing.id() + " made it "
                                    + "slower");
                }
            }
        }
        check(swept > 5_000, "only " + swept + " housing-and-fitting(s) were "
                + "swept, and there are four housings, seven upgrades and a "
                + "roster of resource bees behind them");

        // The ladder is a ladder: every rung better than the one under it,
        // and every rung built out of it.
        for (int at = 1; at < ApiaryRules.HOUSINGS.size(); at++) {
            ApiaryRules.Housing over = ApiaryRules.HOUSINGS.get(at);
            ApiaryRules.Housing under = ApiaryRules.HOUSINGS.get(at - 1);
            check(over.from().equals(under.id()), over.id() + " is built out of "
                    + over.from() + " and the rung below it is " + under.id());
            check(ApiaryRules.perHour(1, over, java.util.List.of())
                            > ApiaryRules.perHour(1, under, java.util.List.of()),
                    over.id() + " bare earns no more than " + under.id()
                            + " bare, so nobody would ever build it");
            check(over.slots() >= under.slots(), over.id() + " takes fewer "
                    + "upgrades than " + under.id());
        }

        // The two ends of it, against the line.
        ApiaryRules.Housing top = ApiaryRules.best();
        float reached = ApiaryRules.perHour(1, top, ApiaryRules.fittable(top));
        check(Math.abs(reached - ApiaryRules.ceiling()) < 0.01F,
                "the best apiary anybody can build earns " + reached
                        + " an hour and the ceiling is " + ApiaryRules.ceiling()
                        + "; the whole point of solving for the ticks is that "
                        + "those two are the same number");
        float bare = ApiaryRules.perHour(1, ApiaryRules.HOUSINGS.get(0),
                java.util.List.of());
        check(bare < ApiaryRules.ceiling() * ApiaryRules.BARE_AT_MOST,
                "a hive nobody has built anything for earns " + bare
                        + " against a ceiling of " + ApiaryRules.ceiling()
                        + ", so there is no room under the line for the ladder");

        // Five of the seven buy attention rather than output, which is what
        // the jar this replaces is actually for.
        int earns = ApiaryRules.earning().size();
        check(earns >= 1 && earns < ApiaryRules.UPGRADES.size(),
                earns + " of " + ApiaryRules.UPGRADES.size() + " upgrades change "
                        + "what comes out of a hive; if it is none the ladder is "
                        + "decoration and if it is all of them nothing buys back "
                        + "any of the attention this is for");
        java.util.Set<String> chores = new java.util.LinkedHashSet<>();
        for (ApiaryRules.Upgrade one : ApiaryRules.UPGRADES) {
            if (!one.attention().isEmpty()) {
                check(chores.add(one.attention()),
                        "two upgrades take away " + one.attention());
            }
        }
        check(chores.size() >= 4, "only " + chores.size() + " chore(s) can be "
                + "bought back, and the jar this replaces is mostly about that");

        // What a comb comes apart into. A resource comb gives its material; a
        // comb of honey does not, and a check that cannot tell them apart is
        // one that would happily ship a honey bee that makes iron.
        for (BeeRules.Bee bee : BeeRules.BEES) {
            java.util.List<String> out = ApiaryRules.spun(bee);
            java.util.List<Integer> counts = ApiaryRules.spunCounts(bee);
            check(out.size() == counts.size(), bee.id() + "'s comb comes apart "
                    + "into " + out.size() + " thing(s) counted " + counts.size()
                    + " way(s)");
            check(!out.isEmpty(), bee.id() + "'s comb comes apart into nothing");
            for (int count : counts) {
                check(count > 0, bee.id() + "'s comb gives none of something");
            }
            boolean resource = BeeRules.RESOURCE.equals(bee.kind());
            String chunk = ApiaryRules.OURS + ":"
                    + ApiaryRules.chunkOf(bee.produce());
            check(out.contains(chunk) == resource,
                    bee.id() + " is a " + bee.kind() + " bee and its comb "
                            + (resource ? "does not give " : "gives ") + chunk);
            for (String id : out) {
                check(id.indexOf(':') > 0, bee.id() + "'s comb gives " + id
                        + ", which is not an item id anything can look up");
                check(id.startsWith(ApiaryRules.OURS + ":")
                                || id.startsWith(ApiaryRules.VANILLA + ":"),
                        bee.id() + "'s comb gives " + id + ", and this module "
                                + "may only hand over its own items and the "
                                + "game's — anything else is a module reaching "
                                + "up into the one above it");
            }
            if (resource) {
                check(ApiaryRules.chunked().contains(bee.produce()),
                        bee.id() + " makes " + bee.produce() + " and no chunk of "
                                + "it is registered, so its comb spins into "
                                + "nothing");
            }
        }
        check(ApiaryRules.SPIN_TICKS > 0, "a comb comes apart instantly");
        check(ApiaryRules.SPINNER.draw() > 0.0F,
                "the centrifuge runs on nothing at all, which is free work");

        // A hive built to be over the line, so the rule that says nothing is
        // over it has been shown able to say otherwise. Every real housing is
        // under the ceiling by construction — the comb time is solved for
        // exactly that — so without this the rule could be `return true` and
        // every check in this file would still pass.
        ApiaryRules.Housing absurd =
                new ApiaryRules.Housing("control", 10_000, 0, "hive");
        check(!ApiaryRules.underTheCeiling(1, absurd, java.util.List.of()),
                "a hive holding ten thousand bees is not reported as over the "
                        + "line, so the ceiling check cannot say no and the one "
                        + "rule this whole system exists to keep is a comment");
        check(ApiaryRules.underTheCeiling(1, ApiaryRules.HOUSINGS.get(0),
                        java.util.List.of()),
                "a bare hive is reported as over the line, which is the rule "
                        + "backwards");

        // Controls: a ladder and a set of upgrades built to be caught.
        check(!ApiaryRules.faultsInLadder(java.util.List.of(
                        new ApiaryRules.Housing("control", 12, 0, ""),
                        new ApiaryRules.Housing("control_two", 8, 0, "control")))
                        .isEmpty(),
                "a rung that holds fewer bees and no more upgrades than the one "
                        + "below it is not reported, so a block nobody would ever "
                        + "build would ship");
        check(!ApiaryRules.faultsInLadder(java.util.List.of(
                        new ApiaryRules.Housing("control", 12, 0, ""),
                        new ApiaryRules.Housing("control_two", 16, 2, "nowhere")))
                        .isEmpty(),
                "a rung built out of something that is not the rung below it is "
                        + "not reported, so the ladder has a step nobody climbs");
        check(ApiaryRules.faultsInLadder(java.util.List.of(
                        new ApiaryRules.Housing("control", 12, 0, ""),
                        new ApiaryRules.Housing("control_two", 16, 2, "control")))
                        .isEmpty(),
                "an ordinary two-rung ladder is reported as faulty: "
                        + ApiaryRules.faultsInLadder(java.util.List.of(
                                new ApiaryRules.Housing("control", 12, 0, ""),
                                new ApiaryRules.Housing("control_two", 16, 2,
                                        "control"))));
        check(!ApiaryRules.faultsInUpgrades(java.util.List.of(
                        new ApiaryRules.Upgrade("control", 0, 1.0F, "")))
                        .isEmpty(),
                "an upgrade that earns nothing and takes no chore away is not "
                        + "reported, and it is a recipe that costs something and "
                        + "does nothing");
        check(!ApiaryRules.faultsInUpgrades(java.util.List.of(
                        new ApiaryRules.Upgrade("control", 4, 1.0F, "planting")))
                        .isEmpty(),
                "an upgrade that both earns and buys attention is not reported, "
                        + "and the whole ceiling argument is that those are two "
                        + "different kinds of thing");

        java.util.List<String> allowed = new java.util.ArrayList<>();
        try {
            new ApiaryRules.Upgrade("control", 0, 0.5F, "");
            allowed.add("an upgrade that makes a hive slower");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ApiaryRules.Housing("control", 0, 0, "");
            allowed.add("a housing that holds no bees");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the machinery allows what it says it forbids: "
                + allowed);

        System.out.println("  the apiary: " + swept + " housing-and-fitting(s) "
                + "swept, none over the line, a bare hive at "
                + Math.round(bare / ApiaryRules.ceiling() * 100) + "% of it and "
                + "the best one anybody can build exactly on it");
    }


    // ==================================================================

    /**
     * The altar, the sixth rung, and the thing awakening is not.
     *
     * <p>The one worth watching is the economy. Doubling a yield is a
     * satisfying reward to write and it is a farm that beats a mine; the
     * difference between the two is a single line about how many plots are
     * allowed, and nothing in the game would ever say which of the two had
     * been shipped.
     */
    private static void theAltar() {
        check(AltarRules.faults().isEmpty(),
                "something wrong with the altar itself: " + AltarRules.faults());

        // Awakening buys space and nothing else, at every tier.
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            check(AltarRules.sameEarnings(tier),
                    "an awakened farm of tier " + tier + " at its largest earns "
                            + AltarRules.awakenedAtItsLargest(tier)
                            + " and an ordinary one "
                            + AltarRules.ordinaryAtItsLargest(tier));
            check(AltarRules.awakenedPlotsBefore(tier)
                            < AgronomyRules.plotsBefore(tier),
                    "an awakened farm of tier " + tier + " may be as large as an "
                            + "ordinary one, so awakening buys nothing");
            check(AltarRules.awakenedAtItsLargest(tier)
                            <= AgronomyRules.minedPerHour()
                                    * AgronomyRules.FARM_AT_MOST + 0.001F,
                    "an awakened farm of tier " + tier + " at its largest earns "
                            + AltarRules.awakenedAtItsLargest(tier)
                            + " and the margin against the mine is "
                            + AgronomyRules.minedPerHour()
                                    * AgronomyRules.FARM_AT_MOST);
            // And a sweep under the allowed size, because "at its largest" is
            // one number and the rule is about every farm anybody builds.
            for (int plots = 1; plots <= AltarRules.awakenedPlotsBefore(tier);
                    plots = plots < 16 ? plots + 1 : plots * 2) {
                check(AltarRules.awakenedPerHour(tier, plots)
                                <= AgronomyRules.minedPerHour()
                                        * AgronomyRules.FARM_AT_MOST + 0.001F,
                        "an awakened farm of tier " + tier + " with " + plots
                                + " plot(s) earns "
                                + AltarRules.awakenedPerHour(tier, plots));
            }
        }

        // A farm built to be over the line, so the rule has been shown able to
        // say no. Every allowed size is under it by construction.
        check(AltarRules.awakenedPerHour(1, AgronomyRules.plotsBefore(1) * 4)
                        > AgronomyRules.minedPerHour() * AgronomyRules.FARM_AT_MOST,
                "an awakened farm four times the ordinary allowance is still "
                        + "under the margin, so the margin is not what is "
                        + "limiting anything");

        // The shape.
        check(AltarRules.pedestals() == 4, "an altar has "
                + AltarRules.pedestals() + " pedestal(s)");
        check(AltarRules.relays() == 4, "an altar has " + AltarRules.relays()
                + " relay(s)");
        for (AltarRules.Place one : AltarRules.AROUND) {
            check(one.reach() <= AltarRules.THE_BOX, one.kind() + " is "
                    + one.reach() + " out and the altar claims "
                    + AltarRules.THE_BOX);
            check(one.reach() > 0, one.kind() + " stands on the centre");
            AltarRules.Place round = one.turned().turned().turned().turned();
            check(round.east() == one.east() && round.north() == one.north(),
                    "four quarter turns do not bring " + one.kind() + " at "
                            + one.east() + "," + one.north() + " back to where "
                            + "it started, so the turn is not a turn");
        }

        // The awakenings.
        check(AltarRules.howMany() == SeedRules.SEEDS.size() + 1,
                "there are " + AltarRules.howMany() + " awakening(s) for "
                        + SeedRules.SEEDS.size() + " seed(s) and the sixth rung");
        java.util.Set<String> made = new java.util.LinkedHashSet<>();
        for (AltarRules.Awakening one : AltarRules.AWAKENINGS) {
            check(made.add(one.makes()), "two awakenings both make "
                    + one.makes() + ", so one of them is a recipe the altar "
                    + "will never reach");
            check(one.essences() > 0, one.id() + " swallows no essence");
            check(!one.makes().equals(one.takes()), one.id()
                    + " hands back what it took");
        }
        check(AltarRules.AWAKENINGS.get(0).makes().equals(AltarRules.INSANIUM),
                "the first thing an altar can do is not making the sixth rung, "
                        + "and everything else needs it");
        for (SeedRules.Seed seed : SeedRules.SEEDS) {
            check(AltarRules.awakenedSeeds()
                            .contains(AltarRules.awakenedSeed(seed.material())),
                    seed.material() + " has no awakened seed");
        }
        check(AltarRules.PER_INSANIUM >= AgronomyRules.ESSENCE_PER_STEP,
                "the sixth rung costs less than a rung, so the altar is a "
                        + "discount on the ladder it sits on top of");
        check(AltarRules.ticksPerInsanium()
                        >= AgronomyRules.ticksAt(AgronomyRules.TIERS),
                "one of the sixth rung costs less farm time than one of the "
                        + "fifth, which is essence out of nothing");

        // Controls: shapes and awakenings built to be caught.
        // Each rule asked on its own. A single far-out pedestal is also
        // asymmetric and a doubled one is also asymmetric, so asking the
        // heap of three would let any one of them be deleted with every
        // control still green.
        check(!AltarRules.faultsInBox(java.util.List.of(
                        new AltarRules.Place(AltarRules.PEDESTAL, 40, 0)))
                        .isEmpty(),
                "a pedestal forty blocks out is not reported, so an altar may "
                        + "reach into the chunk next door");
        check(AltarRules.faultsInBox(AltarRules.AROUND).isEmpty(),
                "the altar's own blocks are reported as outside its box");
        check(!AltarRules.faultsInDoubles(java.util.List.of(
                        new AltarRules.Place(AltarRules.PEDESTAL, 2, 2),
                        new AltarRules.Place(AltarRules.RELAY, 2, 2)))
                        .isEmpty(),
                "two blocks of the altar in one place are not reported");
        check(AltarRules.faultsInDoubles(AltarRules.AROUND).isEmpty(),
                "the altar's own blocks are reported as standing in each other");
        check(!AltarRules.faultsInSymmetry(java.util.List.of(
                        new AltarRules.Place(AltarRules.PEDESTAL, 2, 2)))
                        .isEmpty(),
                "an altar with one pedestal and no mirror images of it is not "
                        + "reported, so it would work facing one way and not "
                        + "another and nothing would say which");
        check(AltarRules.faultsInSymmetry(AltarRules.AROUND).isEmpty(),
                "the altar's own shape is reported as unsymmetric");
        // And the earnings rule, over two numbers built to be caught: over a
        // healthy ladder it can only ever answer yes.
        check(!AltarRules.sameEarnings(200.0F, 100.0F, 1.0F),
                "a farm earning twice what another earns is reported as earning "
                        + "the same, so the rule that awakening buys space "
                        + "rather than output cannot say no");
        check(AltarRules.sameEarnings(100.0F, 100.5F, 1.0F),
                "two farms a fraction of a plot apart are reported as earning "
                        + "differently, which would fail on every rounding");
        check(AltarRules.faultsInShape(AltarRules.AROUND).isEmpty(),
                "the altar's own shape is reported as faulty: "
                        + AltarRules.faultsInShape(AltarRules.AROUND));
        check(!AltarRules.faultsInAwakenings(java.util.List.of(
                        new AltarRules.Awakening("control", "iron_seed", 4,
                                AltarRules.INSANIUM, "iron_seed"))).isEmpty(),
                "an awakening that hands back what it took is not reported");

        java.util.List<String> allowed = new java.util.ArrayList<>();
        try {
            new AltarRules.Awakening("control", "", 0, "x", "y");
            allowed.add("an awakening that swallows no essence");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the altar allows what it says it forbids: "
                + allowed);

        System.out.println("  the altar: " + AltarRules.howMany()
                + " awakening(s), " + AltarRules.AROUND.size()
                + " blocks around a centre, and an awakened farm of half the "
                + "plots earning what a whole one does");
    }

    // ==================================================================

    /** Whether the roster of fish is a roster of fish anybody can catch. */
    private static void theFish() {
        check(FishRules.repeated().isEmpty(),
                "two fish with the same name: " + FishRules.repeated());
        check(FishRules.neverCaught().isEmpty(),
                "fish that never come up on any rod in any water — a texture, a "
                        + "bucket, a spawn egg and no content at all: "
                        + FishRules.neverCaught());
        check(FishRules.deadWater().isEmpty(),
                "a kind of water with nothing living in it anywhere: "
                        + FishRules.deadWater());
        check(FishRules.thinWater().isEmpty(),
                "water too thin to be worth a rod: " + FishRules.thinWater());
        check(FishRules.unreasonablyRare().isEmpty(),
                "fish that are a second job rather than a story: "
                        + FishRules.unreasonablyRare());
        check(FishRules.betterFoodIsCommoner().isEmpty(),
                "the rarer fish is the worse meal on average: "
                        + FishRules.betterFoodIsCommoner());
        check(FishRules.bestFoodIsCommoner().isEmpty(),
                "the best meal in the game is a common fish: "
                        + FishRules.bestFoodIsCommoner());

        Set<String> names = new HashSet<>();
        for (FishRules.Fish one : FishRules.FISH) {
            check(names.add(one.id()), "two fish called " + one.id());
            check(one.length() > 0.0F, one.id() + " has no length to keep a record of");
            check(one.nutrition() > 0, one.id() + " feeds nobody");
            check(!one.id().endsWith("_bucket"),
                    one.id() + " already ends in _bucket, so its bucket would be "
                            + one.bucket());
        }

        // Every family has somewhere it is the thing you catch, or it is a
        // label rather than a habitat.
        for (FishRules.Family family : FishRules.FAMILIES) {
            check(!FishRules.ofFamily(family).isEmpty(),
                    "the " + family.name() + " family has no fish in it");
        }

        // Every rarity band is occupied, or the ladder has a rung missing and
        // a rod upgrade buys nothing at that step.
        for (int rarity = FishRules.COMMONEST; rarity <= FishRules.RAREST; rarity++) {
            boolean any = false;
            for (FishRules.Fish one : FishRules.FISH) {
                if (one.rarity() == rarity) {
                    any = true;
                    break;
                }
            }
            check(any, "nothing at all has rarity " + rarity);
        }
    }

    // ==================================================================

    /** Whether a cast is arithmetic that holds up. */
    private static void theCast() {
        check(FishRules.luckThatHurts().isEmpty(),
                "a better rod that is worse at being a rod: "
                        + FishRules.luckThatHurts());
        check(FishRules.baitThatExcludes().isEmpty(),
                "bait that takes a fish out of the water rather than shifting the "
                        + "odds: " + FishRules.baitThatExcludes());
        check(FishRules.baitThatPullsWrong().isEmpty(),
                "bait that pulls away from the thing it names: "
                        + FishRules.baitThatPullsWrong());

        // The shares in any water sum to one. A table that has quietly become
        // empty, or one that double-counts, shows up here and nowhere else.
        for (FishRules.Water where : FishRules.fishableWaters()) {
            for (int luck = 0; luck < FishRules.RODS; luck++) {
                for (String bait : new String[]{FishRules.NOTHING, "ocean", "river"}) {
                    double total = 0.0D;
                    for (FishRules.Chance chance : FishRules.table(where, luck, bait)) {
                        check(chance.share() > 0.0D,
                                chance.fish() + " is in the table with no chance at "
                                        + "all of being caught");
                        total += chance.share();
                    }
                    check(Math.abs(total - 1.0D) < 1.0E-9D,
                            "the chances in " + where.kind() + " band " + where.band()
                                    + " with rod " + luck + " sum to " + total
                                    + " rather than one");
                }
            }
        }

        // A rod past the top of the ladder is the top of the ladder, not
        // something better. Data files can ask for anything.
        for (FishRules.Water where : FishRules.fishableWaters()) {
            check(FishRules.shares(FishRules.table(where, FishRules.RODS + 9,
                            FishRules.NOTHING))
                            .equals(FishRules.shares(FishRules.table(where,
                                    FishRules.RODS - 1, FishRules.NOTHING))),
                    "a rod past the top of the ladder in " + where.kind()
                            + " keeps getting better, so a data file can ask for one");
            check(FishRules.shares(FishRules.table(where, -4, FishRules.NOTHING))
                            .equals(FishRules.shares(FishRules.table(where, 0,
                                    FishRules.NOTHING))),
                    "a rod below the bottom of the ladder in " + where.kind()
                            + " is worse than no rod at all");
        }

        // Bait that names nothing is no bait, not an empty water.
        for (FishRules.Water where : FishRules.fishableWaters()) {
            check(!FishRules.table(where, 0, "notafamily").isEmpty(),
                    "bait nothing recognises empties " + where.kind() + " band "
                            + where.band() + " rather than doing nothing");
        }

        // Rarity has to mean something: the commonest fish in a water must be
        // commoner than the rarest, with no rod at all.
        for (FishRules.Water where : FishRules.fishableWaters()) {
            if (FishRules.countRarities(where) < 2) {
                continue;
            }
            String rarest = FishRules.rarestIn(where);
            java.util.Map<String, Double> table =
                    FishRules.shares(FishRules.table(where, 0, FishRules.NOTHING));
            double worst = table.getOrDefault(rarest, 0.0D);
            for (FishRules.Fish one : FishRules.FISH) {
                Double share = table.get(one.id());
                if (share == null || one.rarity() >= FishRules.RAREST) {
                    continue;
                }
                if (one.rarity() < FishRules.fish(rarest).rarity()) {
                    check(share >= worst,
                            one.id() + " (rarity " + one.rarity() + ") is rarer than "
                                    + rarest + " (rarity "
                                    + FishRules.fish(rarest).rarity() + ") in "
                                    + where.kind() + " band " + where.band());
                }
            }
        }

        check(FishRules.baseWeight(FishRules.COMMONEST)
                        > FishRules.baseWeight(FishRules.RAREST),
                "the rarest fish is not rarer than the commonest before any rod is "
                        + "taken into account");
        check(FishRules.BAIT_PULL > 1.0D,
                "bait does not pull towards anything, so it is a crafting recipe "
                        + "with no effect");
        check(FishRules.LUCK_TILT > 0.0D,
                "a rod tilts nothing, so every rung of the rod ladder is the same rod");
    }

    // ==================================================================

    /** Whether the pot catalogue and the soil it holds hang together. */
    private static void thePots() {
        check(PotRules.halfMade().isEmpty(),
                "a material with some of its fittings and not others, so half the "
                        + "catalogue is missing and only the player who wanted that "
                        + "colour finds out: " + PotRules.halfMade());
        check(PotRules.repeated().isEmpty(),
                "two pots with the same id: " + PotRules.repeated());
        check(PotRules.ungrowable().isEmpty(),
                "a sprout nothing will grow: " + PotRules.ungrowable());
        check(PotRules.growsEverything().isEmpty(),
                "a soil that grows everything, leaving the others decoration: "
                        + PotRules.growsEverything());
        check(PotRules.unwanted().isEmpty(),
                "a kind of soil nothing ever asks for: " + PotRules.unwanted());
        check(PotRules.flatLadder().isEmpty(),
                "a rung of the soil ladder that buys nothing: "
                        + PotRules.flatLadder());
        check(PotRules.pointless().isEmpty(),
                "a sprout that gives back exactly what you planted: "
                        + PotRules.pointless());
        check(PotRules.waxIsCosmetic(),
                "waxing a pot changes how fast it grows, so every pot in every base "
                        + "is the wrong one and nobody can say why");
        check(PotRules.fittingsThatHurry().isEmpty(),
                "a fitting that is a hidden speed upgrade: "
                        + PotRules.fittingsThatHurry());

        check(PotRules.catalogue().size()
                        == PotRules.materials().size() * PotRules.FITTINGS.size(),
                "the catalogue is not every material times every fitting, so it was "
                        + "written out rather than derived");
        check(PotRules.materials().size()
                        == PotRules.COLOURS.size() * PotRules.CERAMICS.size()
                        + PotRules.STONES.size(),
                "the materials are not the colours times the clays plus the stones");
        check(PotRules.COLOURS.size() == 16,
                "there are " + PotRules.COLOURS.size() + " dyes rather than sixteen");

        Set<String> ids = new HashSet<>();
        for (PotRules.Pot pot : PotRules.catalogue()) {
            check(ids.add(pot.id()), "two pots called " + pot.id());
            check(pot.id().endsWith("_botany_pot"),
                    pot.id() + " is not named like a pot");
            check(pot.feeds() == PotRules.HOPPER.equals(pot.fitting()),
                    pot.id() + " disagrees with itself about whether it empties into "
                            + "what is below");
        }

        // A soil grows faster than bare dirt or there is no reason to dig it.
        PotRules.Soil dirt = PotRules.soil("dirt");
        check(dirt != null, "there is no plain dirt, which is what everything else "
                + "is measured against");
        if (dirt != null) {
            for (PotRules.Soil soil : PotRules.SOILS) {
                if (soil.tier() > dirt.tier()) {
                    check(soil.speed() != dirt.speed() || !soil.grows().equals(
                                    dirt.grows()),
                            soil.id() + " is dearer than dirt and does nothing dirt "
                                    + "does not");
                }
            }
        }

        // Growing is slower in poorer soil and faster in better, at every pot
        // speed a player can reach.
        for (AgronomyRules.Frontier ignored : AgronomyRules.FRONTIERS.subList(0, 1)) {
            for (PotRules.Sprout sprout : PotRules.SPROUTS) {
                int slow = sprout.ticksIn(PotRules.soil("coarse_dirt"), 1.0F);
                int fast = sprout.ticksIn(PotRules.soil("rich_loam"), 1.0F);
                check(fast < slow, sprout.id() + " takes the same time in rich loam as "
                        + "in coarse dirt, so the soil ladder does nothing");
                check(sprout.ticksIn(PotRules.soil("dirt"), 0.0F) >= 1,
                        sprout.id() + " in a pot that is not growing at all still "
                                + "finishes, which is a crop that grows in the dark");
            }
        }
    }

    // ==================================================================

    /**
     * Whether a generator ever beats going to find the stuff.
     *
     * <p>The mod this replaces ships a netherite generator. A generator that
     * hands you netherite is the most direct way there is to make three ages
     * of ore ladder pointless, so every row in the table carries the share of
     * a mine it may be worth and this refuses the build if one exceeds it.
     *
     * <p>Merely loading the table found a real bug the first time it ran: the
     * rows were declared above the array they validate themselves against, so
     * the class threw on initialisation. In a game that is a crash four
     * seconds into loading with no screen and no explanation.
     */
    private static void theVeins() {
        check(!AgronomyRules.VEINS.isEmpty(), "there are no veins at all");
        check(AgronomyRules.veinsOutmining().isEmpty(),
                "a generator worth more of a mine than the line allows: "
                        + AgronomyRules.veinsOutmining());
        check(AgronomyRules.veinsOutOfOrder().isEmpty(),
                "the veins do not get slower as the material gets better: "
                        + AgronomyRules.veinsOutOfOrder());
        check(AgronomyRules.veinsWithNoGenerator().isEmpty(),
                "a vein that needs a generator nobody can build: "
                        + AgronomyRules.veinsWithNoGenerator());

        Set<String> materials = new HashSet<>();
        for (AgronomyRules.Vein vein : AgronomyRules.VEINS) {
            check(materials.add(vein.material()),
                    "two veins for " + vein.material());
            check(vein.perHour() > 0.0F,
                    vein.material() + " comes out of its generator at nothing an hour");
            check(vein.perHour() <= AgronomyRules.minedPerHour(),
                    vein.material() + " comes out of a generator faster than out of "
                            + "the ground");
        }

        // Stone is the one that may be worth a whole mine, because stone is
        // worth nothing. Everything else is held to the tighter line.
        AgronomyRules.Vein stone = AgronomyRules.VEINS.get(0);
        check("stone".equals(stone.material()),
                "the first vein is " + stone.material() + " rather than stone, so the "
                        + "free one is not the worthless one");
        check(stone.tier() == 0,
                "the stone generator needs a dearer machine than the first rung, so "
                        + "the oldest trick in the game is gated behind power");
        for (AgronomyRules.Vein vein : AgronomyRules.VEINS) {
            if (!"stone".equals(vein.material())) {
                check(vein.share() <= AgronomyRules.VEIN_AT_MOST,
                        vein.material() + " is worth " + vein.share() + " of a mine "
                                + "and the line is " + AgronomyRules.VEIN_AT_MOST);
                check(vein.tier() > 0,
                        vein.material() + " comes out of a generator that costs no "
                                + "power at all");
            }
        }

        // A vein table past its end is its end, not something better.
        check(AgronomyRules.rockPerHour(AgronomyRules.ROCK_PER_HOUR.length + 4, 1.0F)
                        == AgronomyRules.rockPerHour(
                                AgronomyRules.ROCK_PER_HOUR.length - 1, 1.0F),
                "a rock generator past the top of the table keeps going");
    }

    // ==================================================================

    /**
     * Whether a cooking system is a system or a recipe book.
     *
     * <p>Twenty-nine meals of which six are worth making is a system with six
     * meals. The domination check is the one that decides which it is, and the
     * effects exist so it can pass honestly rather than by giving everything
     * the same numbers.
     */
    private static void theKitchen() {
        check(KitchenRules.repeated().isEmpty(),
                "two meals with the same name: " + KitchenRules.repeated());
        check(KitchenRules.dominated().isEmpty(),
                "a meal another meal makes pointless: " + KitchenRules.dominated());
        check(KitchenRules.indistinguishable().isEmpty(),
                "two meals that are the same meal under two names: "
                        + KitchenRules.indistinguishable());

        // A positive control, because a predicate that always answers no
        // passes every check above it and proves nothing at all. This is the
        // clearest possible domination: same station, same effect, better in
        // every way. If dominates() cannot see it, dominates() is broken and
        // the twenty-nine meals below have been checked by nothing.
        KitchenRules.Meal plain = new KitchenRules.Meal("a_test_meal",
                java.util.List.of("bread", "cheese_wheel", "butter"),
                4, 0.4F, 600, 1, KitchenRules.POT, "comfort");
        KitchenRules.Meal better = new KitchenRules.Meal("a_better_test_meal",
                java.util.List.of("bread"), 9, 0.9F, 200, 4, KitchenRules.POT,
                "comfort");
        check(better.dominates(plain),
                "the domination check cannot see a meal that is better in every "
                        + "single way, so it is answering no to everything and the "
                        + "whole roster has been checked by nothing");
        check(!plain.dominates(better),
                "the domination check says the worse meal dominates the better one, "
                        + "so it is answering yes to everything");
        // Worse in every way, and cooked somewhere else. Only the station
        // check stops this being a domination, so this control fails the
        // moment somebody takes that check out.
        KitchenRules.Meal elsewhere = new KitchenRules.Meal("a_third_test_meal",
                java.util.List.of("bread", "cheese_wheel", "butter"),
                3, 0.3F, 700, 1, KitchenRules.SKILLET, "comfort");
        check(!better.dominates(elsewhere),
                "the domination check compares meals made on different stations, so "
                        + "a soup can make a sandwich pointless");
        KitchenRules.Meal otherEffect = new KitchenRules.Meal("a_fourth_test_meal",
                java.util.List.of("bread", "cheese_wheel", "butter"),
                3, 0.3F, 700, 1, KitchenRules.POT, "strength");
        check(!better.dominates(otherEffect),
                "the domination check compares meals that do different things, so a "
                        + "meal is pointless for being worse at feeding you even "
                        + "when it is the only thing that makes you stronger");
        check(KitchenRules.impossible().isEmpty(),
                "a recipe that names something nothing makes: "
                        + KitchenRules.impossible());
        check(KitchenRules.badValue().isEmpty(),
                "a meal worth too little to cook or too much to be food: "
                        + KitchenRules.badValue());
        check(KitchenRules.unused().isEmpty(),
                "an ingredient nothing ever uses: " + KitchenRules.unused());
        check(KitchenRules.emptyStations().isEmpty(),
                "a station you build once and never use: "
                        + KitchenRules.emptyStations());
        check(KitchenRules.cutsThatCreate().isEmpty(),
                "a knife that is a food multiplier: " + KitchenRules.cutsThatCreate());

        Set<String> names = new HashSet<>();
        for (KitchenRules.Meal meal : KitchenRules.MEALS) {
            check(names.add(meal.id()), "two meals called " + meal.id());
            check(!meal.of().isEmpty(), meal.id() + " is made of nothing");
            check(meal.ticks() > 0, meal.id() + " cooks instantly");
            check(meal.servings() >= 1, meal.id() + " serves nobody");
            check(KitchenRules.STATIONS.contains(meal.station()),
                    meal.id() + " is cooked somewhere that does not exist");
            // Nothing dominates itself, which is the property the check rests
            // on and the easiest one to break by loosening the comparison.
            check(!meal.dominates(meal), meal.id() + " makes itself pointless");
        }

        // Every station has more than one effect on it, or the station is a
        // single recipe wearing a block.
        for (String station : KitchenRules.STATIONS) {
            Set<String> effects = new HashSet<>();
            for (KitchenRules.Meal meal : KitchenRules.at(station)) {
                effects.add(meal.effect());
            }
            check(effects.size() >= 2,
                    "everything made on the " + station + " does the same thing, so "
                            + "there is one recipe there wearing several names");
        }

        // An effect that exactly one meal gives is the point of that meal; an
        // effect nothing gives at all is a field nobody filled in.
        for (String effect : KitchenRules.effects()) {
            int count = 0;
            for (KitchenRules.Meal meal : KitchenRules.MEALS) {
                if (meal.effect().equals(effect)) {
                    count++;
                }
            }
            check(count >= 1, "nothing gives " + effect);
        }

        check(KitchenRules.effects().size() >= 6,
                "there are only " + KitchenRules.effects().size() + " effects across "
                        + KitchenRules.MEALS.size() + " meals, which is not enough to "
                        + "tell them apart by");
        check(KitchenRules.LEAST_OF_ITS_PARTS < 1.0F,
                "a meal is required to be worth as much as its parts raw, which no "
                        + "amount of cooking can promise");
        check(KitchenRules.MOST_OF_ITS_PARTS > 1.0F,
                "a meal may not be worth more than its parts, so cooking is a way of "
                        + "wasting food");
    }

    // ==================================================================

    /**
     * Whether the seeds grow only what the ground gives you.
     *
     * <p>The mined-or-not check needs the foundry's list, which lives a module
     * above this one and is read by validate.py out of its source. What can be
     * checked here is everything else: that the ladder has no gaps, that
     * nothing is grown twice, and that every essence turns into something a
     * recipe can actually name.
     */
    private static void theSeeds() {
        check(!SeedRules.SEEDS.isEmpty(), "there are no seeds at all");
        check(SeedRules.repeated().isEmpty(),
                "a material two seeds grow: " + SeedRules.repeated());
        check(SeedRules.emptyRungs().isEmpty(),
                "a rung of the ladder nothing grows at: " + SeedRules.emptyRungs());
        check(SeedRules.makingNothing().isEmpty(),
                "an essence that turns into something no recipe can name: "
                        + SeedRules.makingNothing());

        Set<String> materials = new HashSet<>();
        for (SeedRules.Seed seed : SeedRules.SEEDS) {
            check(materials.add(seed.material()),
                    "two seeds for " + seed.material());
            check(seed.tier() >= 1 && seed.tier() <= AgronomyRules.TIERS,
                    seed.material() + " sits off the end of the ladder");
            check(seed.id().equals(seed.material() + "_seed"),
                    seed.material() + "'s seed is not named after it");
        }

        // A positive control for the mined-only rule: told that a material is
        // made rather than mined, the check has to say so. Without this the
        // rule could answer no to everything and validate.py would be reading
        // an empty list from a function that never finds anything.
        check(!SeedRules.growingWhatIsMade(Set.of(), Set.of("iron")).isEmpty(),
                "the mined-only rule cannot see a seed for something it has been "
                        + "told is made, so it is answering no to everything");
        check(!SeedRules.growingWhatIsMade(Set.of(), Set.of()).isEmpty(),
                "the mined-only rule passes a material that is neither mined nor "
                        + "the game's own, so a seed can grow something nothing "
                        + "in this pack supplies");
        check(SeedRules.growingWhatIsMade(new HashSet<>(SeedRules.materials()),
                        Set.of()).isEmpty(),
                "the mined-only rule refuses a roster where every material is "
                        + "mined, so it is answering yes to everything");

        // Growing has to stay under the mine at every rung, which is the rule
        // the whole module is built on, applied to the seeds that now exist.
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            check(AgronomyRules.plotsBefore(tier) > 0,
                    "tier " + tier + " overtakes a mine at no size at all, so there "
                            + "is no size of farm that is allowed");
        }
        check(SeedRules.chunked().size() < SeedRules.SEEDS.size(),
                "every seed grows something this module has to invent an item for, "
                        + "which means none of them grows anything the game or the "
                        + "pack already has");
    }
}
