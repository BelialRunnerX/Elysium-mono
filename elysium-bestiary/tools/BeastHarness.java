package com.elysium.bestiary.rules;

import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.ElysiumAbilityShapes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link BeastRules}, executed.
 *
 * Four claims: fighting a harder thing is always worth more than fighting an
 * easier one, no one thing is so much the best to farm that the rest of the
 * bestiary is scenery, the band the bestiary sits in contains the game's own
 * zombie, and the first of those is true of {@link BeastRules#derive} for
 * <em>any</em> bestiary rather than only for the one we happened to write.
 * All four are ratios over the whole set, which is not a thing anybody checks
 * by reading a list of mobs.
 */
public final class BeastHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    /** Whether building something refused to happen. */
    private static boolean threwOn(Runnable building) {
        try {
            building.run();
            return false;
        } catch (RuntimeException wanted) {
            return true;
        }
    }

    private static BeastRules.Reward earns(BeastRules.Beast beast) {
        return BeastRules.earned(beast);
    }

    private static double worth(BeastRules.Beast beast) {
        return earns(beast).worth();
    }

    private static double rate(BeastRules.Beast beast) {
        return BeastRules.rate(beast, earns(beast));
    }

    public static void main(String[] args) {
        theLadders();
        theDerivation();
        theReward();
        theFarm();
        theScale();
        theBaseGame();
        theDark();
        theHaunts();
        theFauna();
        theBehaviours();
        theSummons();
        theMyths();
        theCompanions();
        theKindred();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        // The table, printed so that anything else working the same numbers
        // out — the generator that writes the loot tables, for one — can be
        // compared against it instead of believed.
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            BeastRules.Reward reward = earns(beast);
            System.out.println("EARNED " + beast.id() + " " + reward.experience() + " "
                    + reward.drops());
        }
        System.out.println(checks + " checks: every family a ladder, every harder thing "
                + "worth more than every easier one in this bestiary and in a hundred "
                + "thousand others, no one thing worth farming more than half again as "
                + "much as any other, the game's own zombie inside that band, and "
                + "nothing in the dark that is not on the game's own scale");
    }

    // ==================================================================

    private static void theLadders() {
        check(BeastRules.FAMILIES.size() == 3,
                "there are " + BeastRules.FAMILIES.size() + " families, and three is as "
                        + "many as a player holds");
        Set<String> ids = new HashSet<>();
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            check(ids.add(beast.id()), beast.id() + " is declared twice");
            check(BeastRules.FAMILIES.contains(beast.family()),
                    beast.id() + " is a " + beast.family() + ", which is not a family");
            check(beast.width() > 0.0F && beast.height() > 0.0F,
                    beast.id() + " has no size");
            check(beast.height() < 4.0F,
                    beast.id() + " is " + beast.height() + " blocks tall and will not "
                            + "fit through anything a player builds");
            check(BeastRules.beast(beast.id()) == beast,
                    beast.id() + " cannot be looked up by its own id");
        }
        for (String family : BeastRules.FAMILIES) {
            List<BeastRules.Beast> ladder = BeastRules.family(family);
            check(ladder.size() >= 3,
                    family + " has " + ladder.size() + " rung(s), which is not a ladder");
            for (int rung = 1; rung < ladder.size(); rung++) {
                BeastRules.Beast below = ladder.get(rung - 1);
                BeastRules.Beast above = ladder.get(rung);
                check(above.tier() == below.tier() + 1,
                        family + " skips from tier " + below.tier() + " to "
                                + above.tier());
                check(above.health() > below.health(),
                        above.id() + " has no more health than " + below.id());
                check(above.damage() > below.damage(),
                        above.id() + " hits no harder than " + below.id());
                check(above.threat() > below.threat(),
                        above.id() + " is no more dangerous than " + below.id()
                                + ", so its tier means nothing");
                check(above.weight() < below.weight(),
                        above.id() + " is met as often as " + below.id()
                                + ", so the rare ones are not rare");
            }
        }
        // The families have to be different from one another, or there is one
        // family wearing three names.
        List<BeastRules.Beast> prowlers = BeastRules.family(BeastRules.PROWLER);
        List<BeastRules.Beast> brutes = BeastRules.family(BeastRules.BRUTE);
        for (int rung = 0; rung < Math.min(prowlers.size(), brutes.size()); rung++) {
            check(prowlers.get(rung).speed() > brutes.get(rung).speed(),
                    "a prowler is no faster than a brute of the same tier, so the two "
                            + "families are one family");
            check(brutes.get(rung).health() > prowlers.get(rung).health(),
                    "a brute is no tougher than a prowler of the same tier");
        }
        System.out.println("  the ladders: " + BeastRules.FAMILIES.size() + " families of "
                + prowlers.size() + ", each rung harder and rarer than the one below");
    }

    /**
     * The claim that matters, made about the rule rather than about the table.
     *
     * <p>{@link BeastRules#notWorthIt()} being empty for our nine beasts is
     * worth very little on its own: it would go on being empty right up until
     * somebody added a tenth. So this draws a hundred thousand bestiaries of
     * random stats, runs each one through {@link BeastRules#derive}, and
     * checks that the rewards that come out rise strictly with danger every
     * single time. Anything that gets added later inherits that.
     */
    private static void theDerivation() {
        Random random = new Random(1_207_331L);
        int bestiaries = 0;
        int rungs = 0;
        for (int round = 0; round < 100_000; round++) {
            int many = 2 + random.nextInt(11);
            List<BeastRules.Beast> made = new ArrayList<>(many);
            for (int at = 0; at < many; at++) {
                made.add(new BeastRules.Beast("b" + at, BeastRules.BRUTE,
                        1 + random.nextInt(3),
                        1.0D + random.nextInt(400),
                        random.nextInt(60),
                        0.05D + random.nextDouble(),
                        random.nextInt(20),
                        1 + random.nextInt(100),
                        1.0F, 1.0F));
            }
            Map<String, BeastRules.Reward> rewards = BeastRules.derive(made);
            bestiaries++;
            check(rewards.size() == many,
                    "derive handed back " + rewards.size() + " rewards for " + many
                            + " beasts");
            List<BeastRules.Beast> order = new ArrayList<>(made);
            order.sort((first, second) -> Double.compare(first.threat(), second.threat()));
            for (int at = 1; at < order.size(); at++) {
                BeastRules.Beast below = order.get(at - 1);
                BeastRules.Beast above = order.get(at);
                if (above.threat() <= below.threat()) {
                    continue;
                }
                rungs++;
                double under = rewards.get(below.id()).worth();
                double over = rewards.get(above.id()).worth();
                check(over > under,
                        "derive gave " + above.id() + " (threat " + above.threat()
                                + ") a worth of " + over + " and " + below.id()
                                + " (threat " + below.threat() + ") a worth of " + under);
            }
            for (BeastRules.Reward reward : rewards.values()) {
                check(reward.experience() >= 1, "derive made a beast worth no orbs");
                check(reward.drops() >= BeastRules.LEAST_DROPS,
                        "derive made a beast that leaves nothing behind");
            }
        }
        // And the table we ship is the table the rule makes, with nothing
        // written down a second time to drift away from it.
        Map<String, BeastRules.Reward> again = BeastRules.derive(BeastRules.BEASTS);
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            check(again.get(beast.id()).equals(earns(beast)),
                    beast.id() + " earns " + earns(beast) + " but deriving the bestiary "
                            + "again gives " + again.get(beast.id()));
        }
        System.out.println("  the derivation: " + bestiaries + " random bestiaries, "
                + rungs + " rungs, and in every one of them the harder thing came out "
                + "worth more");
    }

    private static void theReward() {
        check(BeastRules.notWorthIt().isEmpty(),
                "things that are harder and worth less: " + BeastRules.notWorthIt());

        // And the same thing said the other way round, over every pair, so the
        // check above cannot be passing for the wrong reason.
        int pairs = 0;
        for (BeastRules.Beast first : BeastRules.BEASTS) {
            for (BeastRules.Beast second : BeastRules.BEASTS) {
                if (first.threat() > second.threat()) {
                    pairs++;
                    check(worth(first) > worth(second),
                            first.id() + " is more dangerous than " + second.id()
                                    + " and worth " + worth(first) + " against "
                                    + worth(second));
                }
            }
        }
        check(pairs > 30,
                "only " + pairs + " ordered pairs of beasts, so the bestiary is too "
                        + "small for the check to mean anything");
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            check(worth(beast) > 0.0D, beast.id() + " is worth nothing to kill");
            check(beast.threat() > 0.0D, beast.id() + " is no danger at all");
            check(earns(beast).drops() > 0,
                    beast.id() + " drops nothing, so killing it is experience and a "
                            + "walk back");
            check(earns(beast).experience() >= 1,
                    beast.id() + " gives no experience at all");
            check(Math.abs(worth(beast) - (earns(beast).experience()
                            + earns(beast).drops() * (double) BeastRules.WORTH_OF_A_DROP))
                            < 1.0e-9,
                    beast.id() + " is worth something other than its orbs and its items");
        }
        System.out.println("  the reward: " + pairs + " ordered pairs, and in every one "
                + "of them the harder thing is worth more");
    }

    private static void theFarm() {
        double spread = BeastRules.spread();
        check(spread <= BeastRules.WIDEST_SPREAD,
                "the best thing to farm is " + spread + " times the worst, so every "
                        + "grinder in the pack becomes a grinder for one mob and the "
                        + "rest of the bestiary is scenery");
        check(spread > 1.0D,
                "every beast is worth exactly as much to farm as every other, so which "
                        + "one you build a grinder for means nothing");
        check(BeastRules.noReasonToClimb().isEmpty(),
                "climbing the ladders: " + BeastRules.noReasonToClimb());
        check(BeastRules.averageRate(3) > BeastRules.averageRate(1),
                "the top of the ladders pays " + BeastRules.averageRate(3) + " per unit "
                        + "of danger and the bottom pays " + BeastRules.averageRate(1));

        // And the arithmetic behind it, swept: the rate has to fall when a
        // thing gets harder for the same reward, and rise when it is worth
        // more for the same danger.
        Random random = new Random(66_613L);
        for (int round = 0; round < 200_000; round++) {
            double health = 1.0D + random.nextInt(200);
            double damage = random.nextInt(40);
            double speed = 0.05D + random.nextDouble();
            BeastRules.Reward paid = new BeastRules.Reward(1 + random.nextInt(100), 1);
            BeastRules.Reward twice = new BeastRules.Reward(
                    paid.experience() * 2 + BeastRules.WORTH_OF_A_DROP, 1);
            BeastRules.Beast one = new BeastRules.Beast("a", BeastRules.BRUTE, 1, health,
                    damage, speed, 0.0D, 1, 1.0F, 1.0F);
            BeastRules.Beast harder = new BeastRules.Beast("b", BeastRules.BRUTE, 1,
                    health * 2.0D, damage, speed, 0.0D, 1, 1.0F, 1.0F);
            check(harder.threat() > one.threat(), "twice the health was no harder");
            check(BeastRules.rate(harder, paid) < BeastRules.rate(one, paid),
                    "twice the health for the same reward was worth more to farm");
            check(twice.worth() > paid.worth(), "twice the reward was worth no more");
            check(BeastRules.rate(one, twice) > BeastRules.rate(one, paid),
                    "twice the reward for the same danger was worth no more to farm");
        }
        System.out.println("  the farm: best to worst is " + Math.round(spread * 100)
                / 100.0 + " times, inside the band, the top of the ladders paying "
                + Math.round(BeastRules.averageRate(3) / BeastRules.averageRate(1) * 100)
                + "% of what the bottom pays per unit of danger, and 200,000 swept "
                + "pairs where the rate moves the way it must");
    }

    private static void theScale() {
        check(BeastRules.offTheScale().isEmpty(),
                "things off the game's own scale: " + BeastRules.offTheScale());
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            double zombies = beast.threat() / BeastRules.ZOMBIE.threat();
            check(zombies >= 1.0D,
                    beast.id() + " is " + zombies + " zombies, so it is a thing in the "
                            + "dark nobody has to think about");
            check(zombies <= BeastRules.MOST_TIMES_A_ZOMBIE,
                    beast.id() + " is " + zombies + " zombies, which is an event rather "
                            + "than a mob");
        }
        System.out.println("  the scale: every beast between one zombie and "
                + Math.round(BeastRules.MOST_TIMES_A_ZOMBIE) + ", measured against the "
                + "game's own");
    }

    /**
     * The check nobody writes, because writing it means measuring your own
     * work against somebody else's game.
     */
    private static void theBaseGame() {
        check(BeastRules.outfarmed().isEmpty(),
                "the base game against the bestiary: " + BeastRules.outfarmed());
        double zombie = BeastRules.rate(BeastRules.ZOMBIE, BeastRules.ZOMBIE_EARNS);
        double least = Double.MAX_VALUE;
        double most = 0.0D;
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            least = Math.min(least, rate(beast));
            most = Math.max(most, rate(beast));
        }
        check(zombie >= least && zombie <= most,
                "a zombie pays " + zombie + " and the bestiary pays between " + least
                        + " and " + most + ", so one of the two is a waste of a farm");
        // A zombie has to be the easy thing, or the bottom of our ladder is
        // not the bottom of anything.
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            check(worth(beast) > BeastRules.ZOMBIE_EARNS.worth(),
                    beast.id() + " is worth no more than a zombie");
        }
        System.out.println("  the base game: a zombie pays "
                + Math.round(zombie * 10_000) / 10_000.0 + " and the bestiary pays "
                + Math.round(least * 10_000) / 10_000.0 + " to "
                + Math.round(most * 10_000) / 10_000.0 + ", so it is inside the band");
    }

    private static void theDark() {
        check(BeastRules.lopsided().isEmpty(),
                "beasts that are too much or too little of what you meet: "
                        + BeastRules.lopsided());
        Map<String, Double> shares = BeastRules.shares();
        double total = 0.0D;
        for (double share : shares.values()) {
            total += share;
        }
        check(Math.abs(total - 1.0D) < 1.0e-9,
                "the shares of what you meet come to " + total + " rather than one");
        check(shares.size() == BeastRules.BEASTS.size(),
                "only " + shares.size() + " of " + BeastRules.BEASTS.size()
                        + " beasts have a share of the dark");

        // A night, simulated: draw two million spawns by weight and check the
        // shares that come out are the shares that were declared.
        Random random = new Random(20_260_924L);
        int total_weight = 0;
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            total_weight += beast.weight();
        }
        int[] seen = new int[BeastRules.BEASTS.size()];
        int draws = 2_000_000;
        for (int draw = 0; draw < draws; draw++) {
            int roll = random.nextInt(total_weight);
            for (int at = 0; at < BeastRules.BEASTS.size(); at++) {
                roll -= BeastRules.BEASTS.get(at).weight();
                if (roll < 0) {
                    seen[at]++;
                    break;
                }
            }
        }
        for (int at = 0; at < BeastRules.BEASTS.size(); at++) {
            BeastRules.Beast beast = BeastRules.BEASTS.get(at);
            double got = (double) seen[at] / draws;
            double wanted = shares.get(beast.id());
            check(Math.abs(got - wanted) < 0.005D,
                    beast.id() + " was " + got + " of two million spawns and its share "
                            + "says " + wanted);
            check(seen[at] > 0, beast.id() + " never spawned in two million tries");
        }
        System.out.println("  the dark: two million spawns drawn, every share within "
                + "half a per cent of what was declared");
    }

    /**
     * Where each beast is, and whether it can be there at all.
     *
     * <p>A mob with a spawn that cannot happen is the quietest bug a pack
     * has: registered, rendered, loot table right, harness passing, and in
     * four hundred hours nobody has seen it. Every band here is checked
     * against a world that really exists, and every group against the floor
     * it has to stand on.
     */
    private static void theHaunts() {
        java.util.List<String> faults = HauntRules.faults();
        check(faults.isEmpty(), "something wrong with the haunts: " + faults);

        int placed = 0;
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            HauntRules.Haunt haunt = HauntRules.haunt(beast.id());
            check(haunt != null, beast.id() + " is nowhere at all");
            if (haunt == null) {
                continue;
            }
            placed++;
            // Every band is a band a world has, and every band is a band with
            // something in it. Both, because an empty band and a band off the
            // end of the world look identical from outside: nothing spawns.
            check(haunt.lightBand() > 0 && haunt.heightBand() > 0,
                    haunt.beast() + " has a band with nothing in it");
            check(haunt.darkest() >= HauntRules.PITCH_DARK
                            && haunt.brightest() <= HauntRules.FULL_LIGHT,
                    haunt.beast() + " wants a light level the game does not have");
            check(haunt.lowest() >= HauntRules.BEDROCK
                            && haunt.highest() <= HauntRules.SKY,
                    haunt.beast() + " wants a height the world does not have");
            check(haunt.where().startsWith("#"),
                    haunt.beast() + " is tied to one biome rather than a tag");
            check(haunt.least() >= 1 && haunt.most() >= haunt.least(),
                    haunt.beast() + " comes in a group that is not a group");
            // And the group fits on the floor it is asked to stand on.
            double room = haunt.most() * beast.width() * beast.width();
            check(room <= HauntRules.ROOM_FOR_A_PACK,
                    haunt.beast() + " wants " + room + " square blocks for a "
                            + "full group and there are "
                            + HauntRules.ROOM_FOR_A_PACK);
        }
        check(placed == BeastRules.BEASTS.size(),
                "only " + placed + " of " + BeastRules.BEASTS.size()
                        + " beast(s) are anywhere");

        // Nothing is a weaker version of a fight already had.
        check(HauntRules.faultsInDominance(BeastRules.BEASTS).isEmpty(),
                "one beast is a strictly worse version of another: "
                        + HauntRules.faultsInDominance(BeastRules.BEASTS));

        // Controls, built to be caught, because over this roster every rule
        // above can only ever answer no.
        BeastRules.Beast first = BeastRules.BEASTS.get(0);
        check(!HauntRules.faultsInCover(HauntRules.theseBeasts(BeastRules.BEASTS),
                        java.util.List.of()).isEmpty(),
                "a roster where nothing is anywhere is not reported, so every "
                        + "beast in the pack could be one nobody ever meets");
        check(!HauntRules.faultsInCover(HauntRules.theseBeasts(java.util.List.of(first)),
                        java.util.List.of(
                                new HauntRules.Haunt(first.id(),
                                        "#minecraft:is_overworld", 0, 7, 0, 64, 1, 2),
                                new HauntRules.Haunt(first.id(),
                                        "#minecraft:is_forest", 0, 7, 0, 64, 1, 2)))
                        .isEmpty(),
                "one beast in two places is not reported");
        check(!HauntRules.faultsInCover(HauntRules.theseBeasts(java.util.List.of(first)),
                        java.util.List.of(
                                new HauntRules.Haunt(first.id(),
                                        "#minecraft:is_overworld", 0, 7, 0, 64, 1, 2),
                                new HauntRules.Haunt("not_a_beast",
                                        "#minecraft:is_forest", 0, 7, 0, 64, 1, 2)))
                        .isEmpty(),
                "somewhere that is not a beast's is not reported");
        check(HauntRules.faultsInCover(HauntRules.theseBeasts(BeastRules.BEASTS), HauntRules.HAUNTS)
                        .isEmpty(),
                "the haunts this module ships are reported as faulty");

        check(!HauntRules.faultsInBands(java.util.List.of(
                        new HauntRules.Haunt("control", "#minecraft:is_overworld",
                                7, 0, 0, 64, 1, 2))).isEmpty(),
                "a light band with nothing in it is not reported, which is "
                        + "the quietest bug a pack can have");
        check(!HauntRules.faultsInBands(java.util.List.of(
                        new HauntRules.Haunt("control", "#minecraft:is_overworld",
                                0, 7, 400, 500, 1, 2))).isEmpty(),
                "a height band above the sky is not reported");
        // Separately from the one above, because a band with nothing in it and
        // a band off the end of the world are two different mistakes and one
        // guard answering for both is a guard that can be half deleted.
        check(!HauntRules.faultsInBands(java.util.List.of(
                        new HauntRules.Haunt("control", "#minecraft:is_overworld",
                                0, 7, 64, 0, 1, 2))).isEmpty(),
                "a height band with nothing in it — a floor above its own "
                        + "ceiling, both inside the world — is not reported");
        check(!HauntRules.faultsInBands(java.util.List.of(
                        new HauntRules.Haunt("control", "#minecraft:is_overworld",
                                0, 99, 0, 64, 1, 2))).isEmpty(),
                "a light level the game does not have is not reported");
        check(!HauntRules.faultsInBands(java.util.List.of(
                        new HauntRules.Haunt("control", "minecraft:plains",
                                0, 7, 0, 64, 1, 2))).isEmpty(),
                "a beast tied to one biome rather than a tag is not reported");
        check(HauntRules.faultsInBands(HauntRules.HAUNTS).isEmpty(),
                "the bands this module ships are reported as faulty");

        check(!HauntRules.faultsInRoom(HauntRules.theseBeasts(BeastRules.BEASTS), java.util.List.of(
                        new HauntRules.Haunt("siege_beast", "#minecraft:is_nether",
                                0, 7, 0, 64, 1, 60))).isEmpty(),
                "sixty siege beasts at a time are not reported, so a weight "
                        + "could promise a rate the game cannot place");
        check(HauntRules.faultsInRoom(HauntRules.theseBeasts(BeastRules.BEASTS), HauntRules.HAUNTS)
                        .isEmpty(),
                "the groups this module ships are reported as too big");

        check(!HauntRules.faultsInDominance(java.util.List.of(
                        new BeastRules.Beast("control", BeastRules.PROWLER, 1,
                                10.0D, 2.0D, 0.3D, 0.0D, 10, 0.6F, 1.0F),
                        new BeastRules.Beast("control_two", BeastRules.PROWLER, 1,
                                20.0D, 4.0D, 0.4D, 2.0D, 10, 0.6F, 1.0F)))
                        .isEmpty(),
                "a beast beaten on every axis by another of its own family and "
                        + "tier is not reported");
        check(HauntRules.faultsInDominance(java.util.List.of(
                        new BeastRules.Beast("control", BeastRules.PROWLER, 1,
                                10.0D, 6.0D, 0.3D, 0.0D, 10, 0.6F, 1.0F),
                        new BeastRules.Beast("control_two", BeastRules.PROWLER, 1,
                                20.0D, 4.0D, 0.4D, 2.0D, 10, 0.6F, 1.0F)))
                        .isEmpty(),
                "two beasts that each beat the other somewhere are reported as "
                        + "one dominating the other");
        check(HauntRules.faultsInDominance(java.util.List.of(
                        new BeastRules.Beast("control", BeastRules.PROWLER, 1,
                                10.0D, 2.0D, 0.3D, 0.0D, 10, 0.6F, 1.0F),
                        new BeastRules.Beast("control_two", BeastRules.BRUTE, 1,
                                20.0D, 4.0D, 0.4D, 2.0D, 10, 0.6F, 1.0F)))
                        .isEmpty(),
                "a prowler and a brute are reported as two strengths of one "
                        + "idea, which is the rule that reports nine times and "
                        + "gets switched off");

        // And a haunt that is not a haunt cannot be built at all. These are
        // the only three rules in the file that are enforced by refusing to
        // exist rather than by being added to a list, so they are the only
        // three that need a control that expects a throw.
        check(threwOn(() -> new HauntRules.Haunt(null, "#minecraft:is_overworld",
                        0, 7, 0, 64, 1, 2)),
                "a haunt of nothing is accepted, so a row could name no beast "
                        + "and every check downstream would look it up and "
                        + "find nothing wrong");
        check(threwOn(() -> new HauntRules.Haunt("control", "",
                        0, 7, 0, 64, 1, 2)),
                "a haunt nowhere is accepted, so a beast could be written as "
                        + "living in the empty string and read as placed");
        check(threwOn(() -> new HauntRules.Haunt("control", "#minecraft:is_overworld",
                        0, 7, 0, 64, 0, 0)),
                "a group of none is accepted as a group, so the game would "
                        + "roll the beast and put nothing there");

        System.out.println("  the haunts: " + placed + " beast(s), each in a "
                + "place that exists, in a light band with something in it, at "
                + "a height the world has, and in a group that fits on the "
                + "floor it is asked to stand on");
    }

    // ==================================================================

    /**
     * The half of the world that does not attack you.
     *
     * <p>The claim that matters is not that these twelve animals are balanced
     * — that would go on being true right up until somebody added a
     * thirteenth. It is that the <em>derivation</em> cannot produce an
     * unbalanced one: a bigger yield always costs a longer wait, so what an
     * hour of pen is worth depends on the kind of animal and on nothing else.
     * Anything added tomorrow inherits that.
     */
    private static void theFauna() {
        java.util.List<String> faults = FaunaRules.faults();
        check(faults.isEmpty(), "something wrong with the fauna: " + faults);

        // The derivation, over far more rosters than this one.
        Random random = new Random(8_812_403L);
        int swept = 0;
        for (int round = 0; round < 200_000; round++) {
            String kind = FaunaRules.KINDS.get(random.nextInt(FaunaRules.KINDS.size()));
            int yield = 1 + random.nextInt(40);
            FaunaRules.Animal one = new FaunaRules.Animal("a" + round, kind,
                    1.0D + random.nextInt(40), 0.1D + random.nextDouble(),
                    0.2F + random.nextFloat(), 0.2F + random.nextFloat(),
                    "minecraft:wheat", "minecraft:stick", yield, 1 + random.nextInt(99));
            FaunaRules.Animal more = new FaunaRules.Animal("b" + round, kind,
                    one.health(), one.speed(), one.width(), one.height(),
                    one.eats(), one.gives(), yield + 1, one.weight());
            swept++;
            check(FaunaRules.patienceOf(more) > FaunaRules.patienceOf(one),
                    "an animal that leaves more does not wait longer, so leaving "
                            + "more is free and one row of the roster is the "
                            + "answer to every pen");
            double worth = FaunaRules.worthAnHour(one);
            double meant = FaunaRules.rateOf(kind);
            check(Math.abs(worth - meant) <= meant * 0.01D,
                    "an hour of " + kind + " is worth " + worth + " and the rate "
                            + "for that kind is " + meant);
        }

        // Every kind is worth keeping, whatever is in it.
        double best = 0.0D;
        double worst = Double.MAX_VALUE;
        for (String kind : FaunaRules.KINDS) {
            best = Math.max(best, FaunaRules.rateOf(kind));
            worst = Math.min(worst, FaunaRules.rateOf(kind));
        }
        check(best <= worst * FaunaRules.MOST_TIMES_ANOTHER,
                "the best kind of animal is worth " + (best / worst) + " times the "
                        + "worst, and " + FaunaRules.MOST_TIMES_ANOTHER + " is the "
                        + "line past which every pen in the pack holds one thing");

        // Controls, built to be caught, because over this roster every rule
        // above can only ever answer no.
        FaunaRules.Animal big = new FaunaRules.Animal("control", FaunaRules.GRAZER,
                10.0D, 0.3D, 1.0F, 1.0F, "minecraft:wheat", "minecraft:mutton", 4, 10);
        FaunaRules.Animal small = new FaunaRules.Animal("control_two",
                FaunaRules.GRAZER, 10.0D, 0.3D, 0.5F, 1.0F, "minecraft:wheat",
                "minecraft:beef", 5, 10);

        check(!FaunaRules.faultsInKinds(java.util.List.of(
                        new FaunaRules.Animal("control", "wibble", 10.0D, 0.3D,
                                1.0F, 1.0F, "minecraft:wheat", "minecraft:mutton",
                                2, 10))).isEmpty(),
                "an animal that is not any kind of animal is not reported, so "
                        + "nothing knows what an hour of keeping one is worth");
        check(!FaunaRules.faultsInKinds(java.util.List.of(big, big)).isEmpty(),
                "one animal written twice is not reported");
        check(!FaunaRules.faultsInGroups(java.util.List.of(big)).isEmpty(),
                "a kind with one animal in it is not reported, so a kind could be "
                        + "one animal with a label on it");
        check(FaunaRules.faultsInKinds(FaunaRules.ANIMALS).isEmpty(),
                "the kinds this module ships are reported as faulty");
        check(FaunaRules.faultsInGroups(FaunaRules.ANIMALS).isEmpty(),
                "the groups this module ships are reported as too small");

        check(!FaunaRules.faultsInYields(java.util.List.of(big, small)).isEmpty(),
                "an animal that gives more per trip and more per square block "
                        + "than another of its own kind is not reported, so one "
                        + "row could be the answer to every pen");
        check(FaunaRules.faultsInYields(FaunaRules.ANIMALS).isEmpty(),
                "the roster this module ships has an animal nobody would keep");

        check(!FaunaRules.faultsInPatience(java.util.List.of(
                        new FaunaRules.Animal("control", FaunaRules.GRAZER, 10.0D,
                                0.3D, 1.0F, 1.0F, "minecraft:wheat",
                                "minecraft:mutton", 400, 10)),
                        FaunaRules.LEAST_PATIENCE, FaunaRules.MOST_PATIENCE).isEmpty(),
                "a wait nobody would sit through is not reported");
        check(!FaunaRules.faultsInPatience(FaunaRules.ANIMALS,
                        FaunaRules.MOST_PATIENCE, FaunaRules.MOST_PATIENCE).isEmpty(),
                "a pen that is ready again before a player can walk to it is not "
                        + "reported, so half of what that rule claims to check "
                        + "would never once be asked");
        check(FaunaRules.faultsInPatience(FaunaRules.ANIMALS,
                        FaunaRules.LEAST_PATIENCE, FaunaRules.MOST_PATIENCE).isEmpty(),
                "the waits this module ships are reported as unreasonable");

        check(!FaunaRules.faultsInSpread(java.util.Map.of(
                        FaunaRules.GRAZER, 3.0D, FaunaRules.FORAGER, 1.0D,
                        FaunaRules.FOWL, 1.0D, FaunaRules.SWIMMER, 1.0D)).isEmpty(),
                "one kind of animal worth three times another is not reported, "
                        + "so every pen in the pack could hold one thing");
        check(!FaunaRules.faultsInSpread(java.util.Map.of(
                        FaunaRules.GRAZER, 1.0D, FaunaRules.FORAGER, 1.0D,
                        FaunaRules.FOWL, 1.0D)).isEmpty(),
                "a kind with no tilt at all is not reported, so keeping one "
                        + "would throw rather than be worth something");
        check(!FaunaRules.faultsInSpread(java.util.Map.of(
                        FaunaRules.GRAZER, 1.0D, FaunaRules.FORAGER, 1.0D,
                        FaunaRules.FOWL, 1.0D, FaunaRules.SWIMMER, 0.0D)).isEmpty(),
                "a kind worth nothing an hour is not reported");
        check(FaunaRules.faultsInSpread(FaunaRules.TILT).isEmpty(),
                "the spread this module ships is reported as too wide");

        check(!FaunaRules.faultsInLoop(java.util.List.of(
                        new FaunaRules.Animal("control", FaunaRules.GRAZER, 10.0D,
                                0.3D, 1.0F, 1.0F, "minecraft:wheat",
                                "minecraft:wheat", 4, 10))).isEmpty(),
                "a pen that makes what it is fed is not reported, so two animals "
                        + "and a fence are a machine that ends hunger");
        check(FaunaRules.faultsInLoop(FaunaRules.ANIMALS).isEmpty(),
                "the roster this module ships is reported as feeding itself");

        check(!FaunaRules.faultsInOverlap(java.util.List.of(
                        new FaunaRules.Animal("control", FaunaRules.GRAZER, 10.0D,
                                0.3D, 1.0F, 1.0F, "minecraft:wheat",
                                BeastRules.LEFT_BY.get(BeastRules.PROWLER).staple(),
                                4, 10)), BeastRules.BEASTS).isEmpty(),
                "an animal you can keep in a pen leaving what something in the "
                        + "dark leaves is not reported, so the ladder the "
                        + "bestiary is built on is one nobody has to climb");
        check(FaunaRules.faultsInOverlap(FaunaRules.ANIMALS, BeastRules.BEASTS)
                        .isEmpty(),
                "the fauna this module ships is reported as making the bestiary "
                        + "pointless");

        check(!FaunaRules.faultsInDaylight(FaunaRules.ANIMALS, java.util.List.of(
                        new HauntRules.Haunt("moorstag", "#minecraft:is_forest",
                                0, 15, 0, 64, 1, 2))).isEmpty(),
                "a land animal that spawns down to pitch dark is not reported, so "
                        + "the half of the roster that does not attack you is in "
                        + "the caves with the half that does");
        check(!FaunaRules.faultsInDaylight(FaunaRules.ANIMALS, java.util.List.of(
                        new HauntRules.Haunt("moorstag", "#minecraft:is_forest",
                                9, 10, 0, 64, 1, 2))).isEmpty(),
                "a land animal that never spawns in full daylight is not "
                        + "reported, so a thing meant to be standing in a field "
                        + "is a thing you find in a cave");
        check(FaunaRules.faultsInDaylight(FaunaRules.ANIMALS, HauntRules.THE_FAUNA)
                        .isEmpty(),
                "the daylight this module ships its animals in is reported as "
                        + "wrong");

        check(!FaunaRules.faultsInReach(FaunaRules.ANIMALS, java.util.List.of())
                        .isEmpty(),
                "a roster no kind of which is anywhere a player certainly goes "
                        + "is not reported");
        check(FaunaRules.faultsInReach(FaunaRules.ANIMALS, HauntRules.THE_FAUNA)
                        .isEmpty(),
                "the places this module puts its animals are reported as out of "
                        + "the way");

        // And a haunt for every one of them, on the bestiary's own rules.
        check(HauntRules.faultsInCover(
                        HauntRules.theseAnimals(FaunaRules.ANIMALS),
                        HauntRules.THE_FAUNA).isEmpty(),
                "an animal is somewhere nothing says, or somewhere that is not "
                        + "an animal");
        check(HauntRules.faultsInTwoPlaces(HauntRules.everyHaunt()).isEmpty(),
                "something is placed by both tables at once");
        check(!HauntRules.faultsInTwoPlaces(java.util.List.of(
                        HauntRules.HAUNTS.get(0), HauntRules.HAUNTS.get(0)))
                        .isEmpty(),
                "one name in both tables is not reported, so one of two spawn "
                        + "rules would quietly win and nothing would say which");

        System.out.println("  the fauna: " + FaunaRules.ANIMALS.size()
                + " animal(s) over " + FaunaRules.KINDS.size() + " kind(s), "
                + swept + " derivations swept, every bigger yield a longer wait, "
                + "no pen that feeds itself and nothing in one that something in "
                + "the dark already drops");
    }

    // ==================================================================

    /**
     * A mechanic apiece, and every one of its magnitudes worked out.
     *
     * <p>The claim that matters is not that these nine verbs are spread
     * correctly over these nine beasts — that would go on being true until
     * somebody added a tenth. It is that the derivation cannot produce a
     * beast whose mechanic is weaker than an easier beast's: a harder thing
     * always lands harder and always lands more often, whatever roster it is
     * put in. Anything added tomorrow inherits that.
     */
    private static void theBehaviours() {
        java.util.List<String> faults = BehaviourRules.faults();
        check(faults.isEmpty(), "something wrong with the behaviours: " + faults);

        // The derivation, over rosters nobody wrote.
        Random random = new Random(4_471_909L);
        int swept = 0;
        for (int round = 0; round < 100_000; round++) {
            int many = 2 + random.nextInt(6);
            List<BeastRules.Beast> made = new ArrayList<>(many);
            for (int at = 0; at < many; at++) {
                made.add(new BeastRules.Beast("b" + at, BeastRules.BRUTE,
                        1 + random.nextInt(3), 1.0D + random.nextInt(400),
                        random.nextInt(60), 0.05D + random.nextDouble(),
                        random.nextInt(20), 1 + random.nextInt(100), 1.0F, 1.0F));
            }
            List<BeastRules.Beast> order = new ArrayList<>(made);
            order.sort((first, second) -> Double.compare(first.threat(), second.threat()));
            swept++;
            for (int at = 1; at < order.size(); at++) {
                BeastRules.Beast below = order.get(at - 1);
                BeastRules.Beast above = order.get(at);
                if (above.threat() <= below.threat()) {
                    continue;
                }
                // Not strictly harder: two threats a hair apart round onto
                // one float, and a rule that demanded otherwise would be a
                // rule about floating point rather than about the bestiary.
                // Strictness is the ladder's job, over a roster somebody
                // actually wrote.
                check(BehaviourRules.strengthOf(above, made)
                                >= BehaviourRules.strengthOf(below, made),
                        "a more dangerous beast's mechanic lands softer than a "
                                + "less dangerous one's, so the mechanic is a "
                                + "second set of dials that disagrees with the "
                                + "first");
                check(BehaviourRules.waitsOf(above, made)
                                <= BehaviourRules.waitsOf(below, made),
                        "a more dangerous beast does what it does less often than "
                                + "a less dangerous one");
            }
            for (BeastRules.Beast one : made) {
                check(BehaviourRules.strengthOf(one, made) >= BehaviourRules.LEAST_STRENGTH
                                && BehaviourRules.strengthOf(one, made)
                                        <= BehaviourRules.MOST_STRENGTH,
                        "a strength outside its own band came out of the derivation");
                check(BehaviourRules.waitsOf(one, made) >= BehaviourRules.SOONEST
                                && BehaviourRules.waitsOf(one, made)
                                        <= BehaviourRules.LATEST,
                        "a wait outside its own band came out of the derivation");
            }
        }

        // Controls, built to be caught.
        BeastRules.Beast first = BeastRules.BEASTS.get(0);
        BeastRules.Beast second = BeastRules.BEASTS.get(1);

        check(!BehaviourRules.faultsInSilence(BeastRules.BEASTS,
                        java.util.Map.of()).isEmpty(),
                "a roster where nothing does anything in particular is not "
                        + "reported, so every beast in the pack could be the "
                        + "same fight with a different model on it");
        check(BehaviourRules.faultsInSilence(BeastRules.BEASTS,
                        BehaviourRules.DOES).isEmpty(),
                "the beasts this module ships are reported as doing nothing");
        check(!BehaviourRules.faultsInCoverage(java.util.List.of(first),
                        java.util.Map.of(first.id(), "wibble")).isEmpty(),
                "a beast that does something nothing can perform is not reported");
        check(!BehaviourRules.faultsInCoverage(java.util.List.of(first, second),
                        java.util.Map.of(first.id(), BehaviourRules.LEAP,
                                second.id(), BehaviourRules.LEAP)).isEmpty(),
                "two beasts that do the same thing are not reported, so two of "
                        + "the nine are one fight wearing two skins");
        check(BehaviourRules.faultsInCoverage(BeastRules.BEASTS,
                        BehaviourRules.DOES).isEmpty(),
                "the behaviours this module ships are reported as faulty");

        check(!BehaviourRules.faultsInSpares(BehaviourRules.VERBS,
                        java.util.Map.of(first.id(), BehaviourRules.LEAP)).isEmpty(),
                "a verb nothing does is not reported, so a mechanic could be "
                        + "written, branched and never once performed");
        check(!BehaviourRules.faultsInSpares(java.util.List.of(),
                        java.util.Map.of("not_a_beast", BehaviourRules.LEAP)).isEmpty(),
                "something that is not a beast being given something to do is "
                        + "not reported");
        check(BehaviourRules.faultsInSpares(BehaviourRules.VERBS,
                        BehaviourRules.DOES).isEmpty(),
                "the verbs this module ships are reported as spare");

        check(!BehaviourRules.faultsInTimings(BeastRules.BEASTS,
                        BehaviourRules.LATEST, BehaviourRules.LATEST).isEmpty(),
                "a mechanic that happens oftener than a player can react to is "
                        + "not reported");
        check(!BehaviourRules.faultsInTimings(BeastRules.BEASTS,
                        BehaviourRules.SOONEST, BehaviourRules.SOONEST).isEmpty(),
                "a mechanic most fights end before is not reported");
        check(BehaviourRules.faultsInTimings(BeastRules.BEASTS,
                        BehaviourRules.SOONEST, BehaviourRules.LATEST).isEmpty(),
                "the waits this module ships are reported as outside their band");

        check(!BehaviourRules.faultsInStrengths(BeastRules.BEASTS,
                        BehaviourRules.MOST_STRENGTH,
                        BehaviourRules.MOST_STRENGTH).isEmpty(),
                "a mechanic that lands under its own floor is not reported");
        check(!BehaviourRules.faultsInStrengths(BeastRules.BEASTS,
                        BehaviourRules.LEAST_STRENGTH,
                        BehaviourRules.LEAST_STRENGTH).isEmpty(),
                "a mechanic that lands over its own ceiling is not reported");
        check(BehaviourRules.faultsInStrengths(BeastRules.BEASTS,
                        BehaviourRules.LEAST_STRENGTH,
                        BehaviourRules.MOST_STRENGTH).isEmpty(),
                "the strengths this module ships are reported as outside their band");

        check(!BehaviourRules.faultsInReaches(BeastRules.BEASTS,
                        BehaviourRules.MOST_REACH, BehaviourRules.MOST_REACH).isEmpty(),
                "a reach under its own floor is not reported");
        check(!BehaviourRules.faultsInReaches(java.util.List.of(
                        new BeastRules.Beast("control", BeastRules.BRUTE, 1, 10.0D,
                                2.0D, 0.2D, 0.0D, 10, 40.0F, 2.0F)),
                        BehaviourRules.LEAST_REACH, BehaviourRules.MOST_REACH).isEmpty(),
                "a beast wider than anything its mechanic can touch is not "
                        + "reported, so it would stand against you and miss");
        check(BehaviourRules.faultsInReaches(BeastRules.BEASTS,
                        BehaviourRules.LEAST_REACH, BehaviourRules.MOST_REACH).isEmpty(),
                "the reaches this module ships are reported as outside their band");

        List<BeastRules.Beast> upsideDown = java.util.List.of(
                new BeastRules.Beast("control", BeastRules.BRUTE, 1, 400.0D,
                        50.0D, 0.9D, 19.0D, 10, 1.0F, 1.0F),
                new BeastRules.Beast("control_two", BeastRules.BRUTE, 2, 10.0D,
                        1.0D, 0.1D, 0.0D, 9, 1.0F, 1.0F));
        check(!BehaviourRules.faultsInLadder(upsideDown).isEmpty(),
                "a tier-two beast whose mechanic is weaker than its tier-one "
                        + "family-mate's is not reported, so climbing a ladder "
                        + "could buy a bigger health bar and nothing else");
        check(!BehaviourRules.faultsInPace(upsideDown).isEmpty(),
                "a tier-two beast whose mechanic happens less often than its "
                        + "tier-one family-mate's is not reported");
        check(BehaviourRules.faultsInLadder(BeastRules.BEASTS).isEmpty(),
                "the ladder this module ships is reported as flat");
        check(BehaviourRules.faultsInPace(BeastRules.BEASTS).isEmpty(),
                "the pace this module ships is reported as flat");

        System.out.println("  the behaviours: " + BehaviourRules.VERBS.size()
                + " verb(s) over " + BeastRules.BEASTS.size() + " beast(s), one "
                + "each and none spare, " + swept + " rosters swept, and in every "
                + "one of them the more dangerous thing's mechanic never landed "
                + "softer and never landed less often");
    }

    /**
     * The two things an ability puts in the world.
     *
     * <p>The property worth proving is one sentence: nothing an ability calls
     * up outlives the cooldown that is supposed to limit it. Everything else
     * here is about the same failure in smaller ways — a bolt that arrives
     * weightless, a shade that out-fights what it was called to help with, a
     * shape the library expects and nothing answers.
     */
    private static void theSummons() {
        List<String> faults = SummonRules.faults();
        check(faults.isEmpty(), "something wrong with the summons: " + faults);

        SummonRules.Summon bolt = SummonRules.summon(SummonRules.BOLT);
        SummonRules.Summon shade = SummonRules.summon(SummonRules.SHADE);
        check(bolt != null, "there is no bolt, so two abilities throw nothing");
        check(shade != null, "there is no shade, so one ability calls up nothing");

        // Controls first, and the sweep after.
        check(!SummonRules.faultsInNames(List.of(bolt, bolt)).isEmpty(),
                "two summons written under one name are not reported");
        check(SummonRules.faultsInNames(SummonRules.SUMMONED).isEmpty(),
                "the summons this module ships are reported as doubled");

        check(!SummonRules.faultsInShapes(List.of(new SummonRules.Summon(
                        "control", "wibble", 20, 1.0D, 1.0D, 0.5F, 0.5F, false)),
                        ElysiumAbilityShapes.NEED_AN_ENTITY).isEmpty(),
                "a summon made by a shape the library has never heard of is not "
                        + "reported");
        check(SummonRules.faultsInShapes(SummonRules.SUMMONED,
                        ElysiumAbilityShapes.NEED_AN_ENTITY).isEmpty(),
                "the shapes this module answers are reported as unknown");

        check(!SummonRules.faultsInGaps(List.of(),
                        ElysiumAbilityShapes.NEED_AN_ENTITY).isEmpty(),
                "a shape the library expects an entity for, with nothing behind "
                        + "it, is not reported — and every ability of that shape "
                        + "pays its cost and does nothing");
        check(SummonRules.faultsInGaps(SummonRules.SUMMONED,
                        ElysiumAbilityShapes.NEED_AN_ENTITY).isEmpty(),
                "the shapes this module ships leave one unanswered");

        // The rule the file exists for, in both directions.
        check(!SummonRules.faultsInLifetimes(
                        List.of(new SummonRules.Summon(SummonRules.SHADE,
                                ElysiumAbilityShapes.SUMMON, 100_000, 20.0D,
                                3.0D, 0.6F, 1.9F, true)),
                        SummonRules.abilitiesThatSummon()).isEmpty(),
                "a summon that stands far longer than its own cooldown is not "
                        + "reported, so a player could have two at once and the "
                        + "cost would stop being a limit");
        check(SummonRules.faultsInLifetimes(SummonRules.SUMMONED,
                        SummonRules.abilitiesThatSummon()).isEmpty(),
                "the summons this module ships are reported as outliving their "
                        + "own cooldowns");

        check(!SummonRules.faultsInFloor(0.0D).isEmpty(),
                "a floor of nought on contact, which makes every thrown thing "
                        + "arrive weightless, is not reported");
        check(SummonRules.faultsInFloor(SummonRules.IMPACT).isEmpty(),
                "the floor this module ships is reported as nought");
        check(!SummonRules.faultsInHurt(SummonRules.abilitiesThatSummon(), 0.0D)
                        .isEmpty(),
                "a thrown thing that arrives weightless is not reported");
        check(SummonRules.faultsInHurt(SummonRules.abilitiesThatSummon(),
                        SummonRules.IMPACT).isEmpty(),
                "the bolts this module throws are reported as weightless");

        check(!SummonRules.faultsInPricing(SummonRules.abilitiesThatSummon(),
                        1_000.0D).isEmpty(),
                "an ability priced to hurt whose bolt hits no harder than one "
                        + "priced to mark is not reported, so what it was priced "
                        + "for bought nothing");
        check(SummonRules.faultsInPricing(SummonRules.abilitiesThatSummon(),
                        SummonRules.IMPACT).isEmpty(),
                "the pricing this module ships is reported as buying nothing");

        check(!SummonRules.faultsInStanding(new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 100, 40.0D, 99.0D, 0.6F,
                        1.9F, true), BeastRules.BEASTS).isEmpty(),
                "a summon that out-fights every beast in the world is not "
                        + "reported, so the player would stand behind it");
        check(!SummonRules.faultsInStanding(new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 100, 1.0D, 1.0D, 0.6F,
                        1.9F, true), BeastRules.BEASTS).isEmpty(),
                "a summon that dies on contact is not reported, so nobody would "
                        + "call one twice");
        check(!SummonRules.faultsInShadeExists(null).isEmpty(),
                "no shade at all is not reported");
        check(SummonRules.faultsInShadeExists(shade).isEmpty(),
                "the shade this module ships is reported as absent");
        check(!SummonRules.faultsInMeasure(List.of()).isEmpty(),
                "a shade measured against nothing at all is not reported");
        check(SummonRules.faultsInMeasure(BeastRules.BEASTS).isEmpty(),
                "the beasts this module ships are reported as nothing to "
                        + "measure against");
        check(SummonRules.faultsInStanding(shade, BeastRules.BEASTS).isEmpty(),
                "the shade this module ships is reported as out of its band");

        check(!SummonRules.faultsInFighting(List.of(new SummonRules.Summon(
                        "control", ElysiumAbilityShapes.BOLT, 20, 0.0D, 1.0D,
                        0.5F, 0.5F, true))).isEmpty(),
                "a thrown thing that picks its own targets is not reported, and "
                        + "a homing bolt is a different ability from the one "
                        + "that was priced");
        check(!SummonRules.faultsInFighting(List.of(new SummonRules.Summon(
                        "control", ElysiumAbilityShapes.SUMMON, 20, 1.0D, 1.0D,
                        0.5F, 0.5F, false))).isEmpty(),
                "a summon that picks no targets is not reported, so it stands "
                        + "where it was put");
        check(SummonRules.faultsInFighting(SummonRules.SUMMONED).isEmpty(),
                "the summons this module ships are reported as fighting wrongly");

        check(!SummonRules.faultsInSizes(List.of(new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 20, 1.0D, 1.0D, 9.0F, 9.0F,
                        true))).isEmpty(),
                "a summon too big to follow a player through a door is not "
                        + "reported");
        check(SummonRules.faultsInSizes(SummonRules.SUMMONED).isEmpty(),
                "the summons this module ships are reported as too big");

        check(threwOn(() -> new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 0, 1.0D, 1.0D, 0.5F, 0.5F,
                        true)),
                "a summon that lasts no time at all is accepted");
        check(threwOn(() -> new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 20, -1.0D, 1.0D, 0.5F, 0.5F,
                        true)),
                "a summon with less than no health is accepted");
        check(threwOn(() -> new SummonRules.Summon("control",
                        ElysiumAbilityShapes.SUMMON, 20, 1.0D, 1.0D, 0.0F, 0.5F,
                        true)),
                "a summon of no width at all, which nothing could draw, is "
                        + "accepted");

        // And the sweep: every lifetime against every cooldown the library has.
        Random random = new Random(5_501_773L);
        int swept = 0;
        for (ElysiumAbilityRules.Ability ability : SummonRules.abilitiesThatSummon()) {
            String shape = ElysiumAbilityShapes.shapeOf(ability.id());
            SummonRules.Summon made = SummonRules.forShape(shape);
            check(made != null, ability.id() + " is of the " + shape + " shape "
                    + "and nothing is made for it");
            if (made != null) {
                check(made.lasts() < ability.cooldown(),
                        made.id() + " stands " + made.lasts() + " against "
                                + ability.id() + "'s cooldown of "
                                + ability.cooldown());
            }
            swept++;
        }
        for (int round = 0; round < 200_000; round++) {
            int lasts = 1 + random.nextInt(4_000);
            int cooldown = 1 + random.nextInt(4_000);
            SummonRules.Summon made = new SummonRules.Summon("s" + round,
                    ElysiumAbilityShapes.SUMMON, lasts, 20.0D, 3.0D, 0.6F, 1.9F,
                    true);
            ElysiumAbilityRules.Ability priced =
                    ElysiumAbilityRules.ability("gravewake");
            boolean overstays = lasts >= priced.cooldown();
            check(overstays == !SummonRules.faultsInLifetimes(List.of(made),
                            List.of(priced)).isEmpty(),
                    "a summon standing " + lasts + " against a cooldown of "
                            + priced.cooldown() + " is reported wrongly");
            swept++;
        }

        System.out.println("  the summons: " + SummonRules.SUMMONED.size()
                + " thing(s) over " + ElysiumAbilityShapes.NEED_AN_ENTITY.size()
                + " shape(s) the library cannot do without, " + swept
                + " lifetimes swept against the cooldowns meant to limit them, "
                + "and not one of this module's own outlasting its own");
    }

    /**
     * Eight creatures dangerous in a way better armour does not answer.
     *
     * <p>The property worth proving is that each is dangerous in its <em>own</em>
     * way and that none of them is also simply the hardest thing on its rung.
     * A myth that beat the beast beside it on the plain numbers as well would
     * make the ladder pointless, and a player who geared for that tier would
     * find their gear answered nothing at all.
     */
    private static void theMyths() {
        List<String> faults = MythRules.faults();
        check(faults.isEmpty(), "something wrong with the myths: " + faults);

        MythRules.Myth first = MythRules.MYTHS.get(0);

        check(!MythRules.faultsInNames(List.of(first, first)).isEmpty(),
                "two myths written under one name are not reported");
        check(MythRules.faultsInNames(MythRules.MYTHS).isEmpty(),
                "the myths this module ships are reported as doubled");

        check(!MythRules.faultsInBanes(List.of(new MythRules.Myth("control",
                        "wibble", "minecraft:stick", 1, 10.0D, 1.0D, 0.2D, 20,
                        false, BeastRules.PROWLER)), MythRules.BANES).isEmpty(),
                "a way past armour nothing here has heard of is not reported");
        check(MythRules.faultsInBanes(MythRules.MYTHS, MythRules.BANES).isEmpty(),
                "the banes this module ships are reported as unknown");

        check(!MythRules.faultsInBaneNames(List.of(first,
                        new MythRules.Myth("control", first.bane(),
                                "minecraft:stick", 1, 10.0D, 1.0D, 0.2D, 20,
                                false, BeastRules.PROWLER))).isEmpty(),
                "two myths dangerous in the same way are not reported, and the "
                        + "second of them is scenery from the moment a player "
                        + "learns the first");
        check(MythRules.faultsInBaneNames(MythRules.MYTHS).isEmpty(),
                "the myths this module ships are reported as sharing a danger");

        check(!MythRules.faultsInGaps(List.of(), MythRules.BANES).isEmpty(),
                "a way past armour nothing uses is not reported, so it is "
                        + "written down and never happens");
        check(MythRules.faultsInGaps(MythRules.MYTHS, MythRules.BANES).isEmpty(),
                "the myths this module ships leave a bane unused");

        check(!MythRules.faultsInAnswers(List.of(first,
                        new MythRules.Myth("control", MythRules.SPORES,
                                first.answer(), 1, 10.0D, 1.0D, 0.2D, 20, false,
                                BeastRules.PROWLER))).isEmpty(),
                "one thing carried in a pocket answering two of these is not "
                        + "reported, so the player stops thinking about either");
        check(MythRules.faultsInAnswers(MythRules.MYTHS).isEmpty(),
                "the answers this module ships are reported as doubled");

        check(!MythRules.faultsInRestraint(List.of(new MythRules.Myth("control",
                        MythRules.PETRIFY, "minecraft:stick", 1, 900.0D, 90.0D,
                        0.9D, 20, false, BeastRules.PROWLER)),
                        BeastRules.BEASTS).isEmpty(),
                "a myth that is also the hardest thing on its rung is not "
                        + "reported, so gearing up would answer nothing");
        check(MythRules.faultsInRestraint(MythRules.MYTHS, BeastRules.BEASTS)
                        .isEmpty(),
                "the myths this module ships are reported as out-fighting the "
                        + "beasts beside them");

        check(!MythRules.faultsInStanding(List.of(new MythRules.Myth("control",
                        MythRules.PETRIFY, "minecraft:stick", 99, 10.0D, 1.0D,
                        0.2D, 20, false, BeastRules.PROWLER)),
                        BeastRules.BEASTS).isEmpty(),
                "a myth standing beside a rung with no beast on it is not "
                        + "reported, so nothing holds its numbers down");
        check(MythRules.faultsInStanding(MythRules.MYTHS, BeastRules.BEASTS)
                        .isEmpty(),
                "the myths this module ships are reported as standing beside "
                        + "nothing");

        check(!MythRules.faultsInFleeing(List.of(
                        new MythRules.Myth("runs", MythRules.PETRIFY,
                                "minecraft:stick", 1, 90.0D, 9.0D, 0.9D, 20,
                                true, BeastRules.PROWLER),
                        new MythRules.Myth("stands", MythRules.DRAIN,
                                "minecraft:bread", 1, 10.0D, 1.0D, 0.2D, 20,
                                false, BeastRules.PROWLER))).isEmpty(),
                "something that runs being worth more than something that "
                        + "stands is not reported, so the safest thing on the "
                        + "rung would also be the worst to meet");
        check(MythRules.faultsInFleeing(MythRules.MYTHS).isEmpty(),
                "the myths this module ships are reported as running and still "
                        + "being the worst of it");

        check(!MythRules.faultsInRarity(MythRules.MYTHS, 1_000).isEmpty(),
                "a creature nobody ever meets is not reported");
        check(MythRules.faultsInRarity(MythRules.MYTHS, MythRules.LEAST_WEIGHT)
                        .isEmpty(),
                "the myths this module ships are reported as too rare to meet");

        check(!MythRules.faultsInLeavings(List.of(new MythRules.Myth("control",
                        MythRules.PETRIFY, "minecraft:stick", 1, 10.0D, 1.0D,
                        0.2D, 20, false, BeastRules.PROWLER)),
                        List.of()).isEmpty(),
                "a myth that leaves nothing at all is not reported, so killing "
                        + "one would be the only fight in the pack that pays "
                        + "nothing");
        check(!MythRules.faultsInLeavings(List.of(new MythRules.Myth("control",
                        MythRules.PETRIFY, "minecraft:stick", 1, 10.0D, 1.0D,
                        0.2D, 20, false, BeastRules.PROWLER)),
                        List.of("not_a_name")).isEmpty(),
                "a leaving that is not a name anything can look up is not "
                        + "reported");
        check(MythRules.faultsInLeavings(MythRules.MYTHS,
                        MythRules.LEAVINGS).isEmpty(),
                "the myths this module ships are reported as leaving nothing");

        // And that danger is danger. The sweep below compares two threats, so
        // it agrees with itself however threat() is worked out; these two say
        // what the number has to be made of.
        MythRules.Myth mild = new MythRules.Myth("mild", MythRules.PETRIFY,
                "minecraft:stick", 1, 20.0D, 2.0D, 0.3D, 20, false,
                BeastRules.PROWLER);
        MythRules.Myth fierce = new MythRules.Myth("fierce", MythRules.PETRIFY,
                "minecraft:stick", 1, 20.0D, 9.0D, 0.3D, 20, false,
                BeastRules.PROWLER);
        MythRules.Myth quick = new MythRules.Myth("quick", MythRules.PETRIFY,
                "minecraft:stick", 1, 20.0D, 2.0D, 0.9D, 20, false,
                BeastRules.PROWLER);
        check(fierce.threat() > mild.threat(),
                "something that hits harder is not worth more, so what a myth "
                        + "does to you is not part of how dangerous it is");
        check(quick.threat() > mild.threat(),
                "something that closes faster is not worth more, so how hard it "
                        + "is to walk away from is not part of how dangerous "
                        + "it is");

        check(!MythRules.faultsInForms(List.of(new MythRules.Myth("control",
                        MythRules.PETRIFY, "minecraft:stick", 1, 10.0D, 1.0D,
                        0.2D, 20, false, "wibble")), MythRules.forms()).isEmpty(),
                "a myth drawn as a shape this module has not got is not "
                        + "reported, so it renders as a black-and-magenta cube");
        check(MythRules.faultsInForms(MythRules.MYTHS, MythRules.forms()).isEmpty(),
                "the myths this module ships are reported as undrawable");

        check(threwOn(() -> new MythRules.Myth("control", MythRules.PETRIFY, "",
                        1, 10.0D, 1.0D, 0.2D, 20, false, BeastRules.PROWLER)),
                "a myth nothing answers is accepted, which is a wall rather "
                        + "than a fight");
        check(threwOn(() -> new MythRules.Myth("control", MythRules.PETRIFY,
                        "minecraft:stick", 1, 10.0D, 1.0D, 0.2D, 0, false,
                        BeastRules.PROWLER)),
                "a myth nothing ever spawns is accepted");
        check(threwOn(() -> new MythRules.Myth("control", MythRules.PETRIFY,
                        "minecraft:stick", 1, 0.0D, 1.0D, 0.2D, 20, false,
                        BeastRules.PROWLER)),
                "a myth with no health is accepted");
        check(threwOn(() -> new MythRules.Myth("control", MythRules.PETRIFY,
                        "minecraft:stick", 1, 10.0D, 1.0D, 0.2D, 20, false, "")),
                "a myth with no shape, which nothing could draw, is accepted");

        // And the sweep: every myth against every beast on its own rung, and
        // a hundred thousand rosters nobody wrote.
        Random random = new Random(7_733_921L);
        int swept = 0;
        for (MythRules.Myth myth : MythRules.MYTHS) {
            BeastRules.Beast beside = MythRules.beside(myth, BeastRules.BEASTS);
            check(beside != null, myth.id() + " stands beside tier "
                    + myth.tier() + " and there is no beast there");
            if (beside != null) {
                check(myth.threat() < beside.threat(), myth.id() + " is worth "
                        + Math.round(myth.threat()) + " against " + beside.id()
                        + "'s " + Math.round(beside.threat()));
            }
            swept++;
        }
        for (int round = 0; round < 100_000; round++) {
            double health = 1.0D + random.nextInt(200);
            double damage = random.nextInt(30);
            double speed = 0.05D + random.nextDouble();
            int tier = 1 + random.nextInt(3);
            MythRules.Myth made = new MythRules.Myth("m" + round,
                    MythRules.PETRIFY, "minecraft:stick", tier, health, damage,
                    speed, 20, false, BeastRules.PROWLER);
            BeastRules.Beast beside = MythRules.beside(made, BeastRules.BEASTS);
            boolean overshoots = beside != null
                    && made.threat() >= beside.threat();
            check(overshoots == !MythRules.faultsInRestraint(List.of(made),
                            BeastRules.BEASTS).isEmpty(),
                    "a myth worth " + Math.round(made.threat()) + " on tier "
                            + tier + " is reported wrongly");
            swept++;
        }

        System.out.println("  the myths: " + MythRules.MYTHS.size()
                + " creature(s) over " + MythRules.BANES.size() + " way(s) past "
                + "armour, one each and none spare, each answered by something "
                + "nothing else answers, " + swept + " rosters swept, and not "
                + "one of them also the hardest thing on its own rung");
    }

    /**
     * Eleven creatures that do a block's job.
     *
     * <p>The property worth proving is that none of them does it for free. A
     * block sits there, and that is the whole of what a block has over
     * something alive; a companion that is neither tamed nor fed nor kept close
     * is strictly better than the thing it replaces, and the thing it replaces
     * stops being built — quietly, one category at a time, with nothing in the
     * pack saying when.
     */
    private static void theCompanions() {
        List<String> faults = CompanionRules.faults();
        check(faults.isEmpty(), "something wrong with the companions: " + faults);

        CompanionRules.Companion first = CompanionRules.COMPANIONS.get(0);

        check(!CompanionRules.faultsInNames(List.of(first, first)).isEmpty(),
                "two companions written under one name are not reported");
        check(CompanionRules.faultsInNames(CompanionRules.COMPANIONS).isEmpty(),
                "the companions this module ships are reported as doubled");

        check(!CompanionRules.faultsInGifts(List.of(new CompanionRules.Companion(
                        "control", "wibble", "minecraft:stone", false, 0, 4.0D,
                        0, 20)), CompanionRules.GIFTS).isEmpty(),
                "a companion doing something nothing here has heard of is not "
                        + "reported");
        check(CompanionRules.faultsInGifts(CompanionRules.COMPANIONS,
                        CompanionRules.GIFTS).isEmpty(),
                "the gifts this module ships are reported as unknown");

        check(!CompanionRules.faultsInGiftNames(List.of(first,
                        new CompanionRules.Companion("control", first.gift(),
                                "minecraft:stone", false, 0, 4.0D, 0, 20)))
                        .isEmpty(),
                "two companions doing the same job are not reported, and "
                        + "whichever is easier to keep is the one everybody "
                        + "keeps");
        check(CompanionRules.faultsInGiftNames(CompanionRules.COMPANIONS).isEmpty(),
                "the companions this module ships are reported as sharing a job");

        check(!CompanionRules.faultsInGaps(List.of(), CompanionRules.GIFTS)
                        .isEmpty(),
                "a job nothing does is not reported, so a thing this module "
                        + "knows how to do is written down and never happens");
        check(CompanionRules.faultsInGaps(CompanionRules.COMPANIONS,
                        CompanionRules.GIFTS).isEmpty(),
                "the companions this module ships leave a job undone");

        // The rule the file exists for.
        check(!CompanionRules.faultsInCost(List.of(new CompanionRules.Companion(
                        "control", CompanionRules.REACH, "minecraft:scaffolding",
                        false, 0, 400.0D, 0, 20)), CompanionRules.MOST_REACH)
                        .isEmpty(),
                "a companion that does a block's job for free — neither tamed "
                        + "nor fed nor kept close — is not reported, so nobody "
                        + "would build one of those again");
        check(CompanionRules.faultsInCost(CompanionRules.COMPANIONS,
                        CompanionRules.MOST_REACH).isEmpty(),
                "the companions this module ships are reported as free");

        check(!CompanionRules.faultsInReach(List.of(new CompanionRules.Companion(
                        "control", CompanionRules.REACH, "minecraft:scaffolding",
                        false, 0, 0.5D, 0, 20)), CompanionRules.LEAST_REACH,
                        CompanionRules.MOST_REACH).isEmpty(),
                "a companion you have to stand on is not reported");
        check(!CompanionRules.faultsInReach(List.of(new CompanionRules.Companion(
                        "control", CompanionRules.REACH, "minecraft:scaffolding",
                        false, 0, 900.0D, 0, 20)), CompanionRules.LEAST_REACH,
                        CompanionRules.MOST_REACH).isEmpty(),
                "a companion that works from anywhere is not reported, and that "
                        + "is a permanent effect with a mob somewhere behind it");
        check(CompanionRules.faultsInReach(CompanionRules.COMPANIONS,
                        CompanionRules.LEAST_REACH, CompanionRules.MOST_REACH)
                        .isEmpty(),
                "the reaches this module ships are reported as out of band");

        check(!CompanionRules.faultsInFading(List.of(
                        new CompanionRules.Companion("control",
                                CompanionRules.REACH, "minecraft:scaffolding",
                                false, 0, 4.0D, 100_000, 20)),
                        CompanionRules.LONGEST_FADE).isEmpty(),
                "a gift that outlasts the creature is not reported, and a stat "
                        + "a player keeps is a thing they will kill it for");
        check(CompanionRules.faultsInFading(CompanionRules.COMPANIONS,
                        CompanionRules.LONGEST_FADE).isEmpty(),
                "the gifts this module ships are reported as permanent");

        check(!CompanionRules.faultsInRarity(CompanionRules.COMPANIONS, 1_000)
                        .isEmpty(),
                "a creature nobody ever meets is not reported");
        check(CompanionRules.faultsInRarity(CompanionRules.COMPANIONS,
                        CompanionRules.LEAST_WEIGHT).isEmpty(),
                "the companions this module ships are reported as too rare");

        check(!CompanionRules.faultsInUpkeep(List.of(
                        new CompanionRules.Companion("control",
                                CompanionRules.REACH, "minecraft:scaffolding",
                                false, 3, 20.0D, 0, 20))).isEmpty(),
                "something fed but neither tamed nor kept underfoot is not "
                        + "reported, so a player feeds what is free to wander "
                        + "off with it");
        check(CompanionRules.faultsInUpkeep(CompanionRules.COMPANIONS).isEmpty(),
                "the upkeep this module ships is reported as paid to nothing");

        check(threwOn(() -> new CompanionRules.Companion("control",
                        CompanionRules.REACH, "minecraft:stone", false, 0, 0.0D,
                        0, 20)),
                "a companion that works from nowhere is accepted");
        check(threwOn(() -> new CompanionRules.Companion("control",
                        CompanionRules.REACH, "minecraft:stone", false, -1, 4.0D,
                        0, 20)),
                "a companion that costs less than nothing to keep is accepted");
        check(threwOn(() -> new CompanionRules.Companion("control",
                        CompanionRules.REACH, "minecraft:stone", false, 0, 4.0D,
                        0, 0)),
                "a companion nothing ever spawns is accepted");
        check(threwOn(() -> new CompanionRules.Companion("control",
                        CompanionRules.REACH, "", false, 0, 4.0D, 0, 20)),
                "a companion that competes with nothing is accepted, so nothing "
                        + "would hold it under anything");

        // And the sweep: every combination of what a companion might cost.
        int swept = 0;
        for (boolean tamed : new boolean[]{false, true}) {
            for (int upkeep : new int[]{0, 1, 5}) {
                for (double reach : new double[]{4.0D, 20.0D, 24.0D, 25.0D,
                        100.0D}) {
                    CompanionRules.Companion made = new CompanionRules.Companion(
                            "c" + swept, CompanionRules.REACH,
                            "minecraft:scaffolding", tamed, upkeep, reach, 0, 20);
                    boolean free = !tamed && upkeep <= 0
                            && reach > CompanionRules.MOST_REACH;
                    check(free == !CompanionRules.faultsInCost(List.of(made),
                                    CompanionRules.MOST_REACH).isEmpty(),
                            "a companion " + (tamed ? "tamed" : "wild")
                                    + ", fed " + upkeep + ", working from "
                                    + reach + " is reported wrongly");
                    swept++;
                }
            }
        }

        System.out.println("  the companions: " + CompanionRules.COMPANIONS.size()
                + " creature(s) over " + CompanionRules.GIFTS.size() + " job(s) "
                + "a block would otherwise do, one each and none spare, " + swept
                + " ways of costing nothing swept, and not one of this module's "
                + "own doing its job for free");
    }

    /**
     * Twenty-seven creatures in thirteen kindreds.
     *
     * <p>The claim under test is the one a roster breaks silently: that a
     * variant is a different fight rather than the same fight in a different
     * colour. Every control below is a kindred built to break exactly one half
     * of that — same thing done, same place lived in, a rung of its own, or a
     * spread wide enough that the weak ones stop being fights anybody takes.
     *
     * <p>Every rule is handed its roster and its numbers rather than reading
     * them, so a control can hand one built to be caught. A rule that reads the
     * constant it judges re-solves itself against the broken value and reports
     * nothing, which is the failure this module has now hit six times.
     */
    private static void theKindred() {
        List<String> faults = KindredRules.faults();
        check(faults.isEmpty(), "something wrong with the kindred: " + faults);

        KindredRules.Kin first = KindredRules.KINDRED.get(0);

        check(!KindredRules.faultsInNames(List.of(first, first)).isEmpty(),
                "two creatures written under one name are not reported");
        check(KindredRules.faultsInNames(KindredRules.KINDRED).isEmpty(),
                "the creatures this module ships are reported as doubled");

        // A kindred with nobody at the head of it, and one with two.
        KindredRules.Kin orphan = new KindredRules.Kin("control", "control", false,
                KindredRules.POISONS, KindredRules.OPEN, 1, 10.0D, 1.0D, 0.3D, 20, false);
        check(!KindredRules.faultsInBases(List.of(orphan)).isEmpty(),
                "a kindred with no base is not reported, so every one of its "
                        + "members is a variant of nothing");
        KindredRules.Kin twoHeaded = new KindredRules.Kin("control2", "control", true,
                KindredRules.FREEZES, KindredRules.COLD, 1, 10.0D, 1.0D, 0.3D, 20, false);
        KindredRules.Kin alsoHeaded = new KindredRules.Kin("control3", "control", true,
                KindredRules.IGNITES, KindredRules.HELL, 1, 10.0D, 1.0D, 0.3D, 20, false);
        check(!KindredRules.faultsInBases(List.of(twoHeaded, alsoHeaded)).isEmpty(),
                "a kindred with two bases is not reported");
        check(KindredRules.faultsInBases(KindredRules.KINDRED).isEmpty(),
                "the kindreds this module ships are reported as headless or two-headed");

        // A thing nothing here makes happen.
        check(!KindredRules.faultsInTraits(List.of(new KindredRules.Kin("control",
                        "control", true, "whistles", KindredRules.OPEN, 1,
                        10.0D, 1.0D, 0.3D, 20, false)),
                KindredRules.TRAITS).isEmpty(),
                "a creature doing something nothing here has heard of is not reported");
        check(KindredRules.faultsInTraits(KindredRules.KINDRED,
                        KindredRules.TRAITS).isEmpty(),
                "the creatures this module ships do things it does not make happen");

        // The first half of the claim: two of a kindred doing one thing.
        check(!KindredRules.faultsInTraitNames(List.of(first,
                        new KindredRules.Kin("control", first.kin(), false,
                                first.trait(), KindredRules.RIVERSIDE, first.tier(),
                                first.health(), first.damage(), first.speed(),
                                first.weight(), false))).isEmpty(),
                "two of one kindred doing the same thing are not reported, and that "
                        + "is the whole failure this file exists to stop");
        check(KindredRules.faultsInTraitNames(KindredRules.KINDRED).isEmpty(),
                "the kindreds this module ships are reported as repeating themselves");

        // The second half: two of a kindred in one place.
        check(!KindredRules.faultsInHomeNames(List.of(first,
                        new KindredRules.Kin("control", first.kin(), false,
                                KindredRules.CARRIES, first.home(), first.tier(),
                                first.health(), first.damage(), first.speed(),
                                first.weight(), false))).isEmpty(),
                "two of one kindred living in one place are not reported");
        check(KindredRules.faultsInHomeNames(KindredRules.KINDRED).isEmpty(),
                "the kindreds this module ships are reported as crowding one biome");

        check(!KindredRules.faultsInHomes(List.of(new KindredRules.Kin("control",
                        "control", true, KindredRules.POISONS, "#c:is_cold", 1,
                        10.0D, 1.0D, 0.3D, 20, false)),
                KindredRules.HOMES).isEmpty(),
                "a creature in a tag the game does not have is not reported, and a "
                        + "tag nothing fills looks exactly like a creature that is rare");
        check(KindredRules.faultsInHomes(KindredRules.KINDRED,
                        KindredRules.HOMES).isEmpty(),
                "the creatures this module ships live somewhere it does not know");

        // Nothing spare at either end.
        check(!KindredRules.faultsInTraitGaps(List.of(first),
                        KindredRules.TRAITS).isEmpty(),
                "a trait nothing carries is not reported");
        check(KindredRules.faultsInTraitGaps(KindredRules.KINDRED,
                        KindredRules.TRAITS).isEmpty(),
                "the roster this module ships leaves a trait uncarried");
        check(!KindredRules.faultsInHomeGaps(List.of(first),
                        KindredRules.HOMES).isEmpty(),
                "a place nothing lives in is not reported");
        check(KindredRules.faultsInHomeGaps(KindredRules.KINDRED,
                        KindredRules.HOMES).isEmpty(),
                "the roster this module ships leaves a place empty");

        // A version of something that is really a rung of its own.
        check(!KindredRules.faultsInTiers(List.of(first,
                        new KindredRules.Kin("control", first.kin(), false,
                                KindredRules.CARRIES, KindredRules.RIVERSIDE,
                                first.tier() + 1, first.health(), first.damage(),
                                first.speed(), first.weight(), false))).isEmpty(),
                "a member of a kindred standing on a different rung is not reported");
        check(KindredRules.faultsInTiers(KindredRules.KINDRED).isEmpty(),
                "the kindreds this module ships straddle rungs");

        check(!KindredRules.faultsInSpread(List.of(first,
                        new KindredRules.Kin("control", first.kin(), false,
                                KindredRules.CARRIES, KindredRules.RIVERSIDE,
                                first.tier(), first.health() * 10.0D, first.damage(),
                                first.speed(), first.weight(), false)),
                KindredRules.WIDEST_KINDRED).isEmpty(),
                "a kindred ten times wider than it is allowed to be is not reported");
        check(KindredRules.faultsInSpread(KindredRules.KINDRED,
                        KindredRules.WIDEST_KINDRED).isEmpty(),
                "the kindreds this module ships are wider than it allows");

        check(!KindredRules.faultsInStanding(List.of(new KindredRules.Kin("control",
                        "control", true, KindredRules.POISONS, KindredRules.OPEN, 1,
                        900.0D, 90.0D, 0.9D, 20, false)),
                BeastRules.BEASTS).isEmpty(),
                "a creature out-fighting the beast of its own rung is not reported");
        check(!KindredRules.faultsInStanding(List.of(new KindredRules.Kin("control",
                        "control", true, KindredRules.POISONS, KindredRules.OPEN, 9,
                        10.0D, 1.0D, 0.3D, 20, false)),
                BeastRules.BEASTS).isEmpty(),
                "a creature on a rung with no beast on it is not reported, so "
                        + "nothing holds it to anything");
        check(KindredRules.faultsInStanding(KindredRules.KINDRED,
                        BeastRules.BEASTS).isEmpty(),
                "the creatures this module ships stand over the beasts' ladder");

        check(!KindredRules.faultsInYoung(List.of(new KindredRules.Kin("control",
                        "control2", false, KindredRules.POISONS, KindredRules.OPEN, 1,
                        10.0D, 1.0D, 0.3D, 20, true))).isEmpty(),
                "young belonging to a costume rather than to the family are not reported");
        check(KindredRules.faultsInYoung(KindredRules.KINDRED).isEmpty(),
                "the young this module ships belong to something that is not a base");

        check(!KindredRules.faultsInRarity(KindredRules.KINDRED, 1_000).isEmpty(),
                "a roster nobody could meet is not reported");
        check(KindredRules.faultsInRarity(KindredRules.KINDRED,
                        KindredRules.LEAST_WEIGHT).isEmpty(),
                "the creatures this module ships are reported as too rare");

        check(KindredRules.faultsInLeavings(KindredRules.KINDRED).isEmpty(),
                "something this module ships leaves nothing behind");

        // What the compact constructor will not build at all.
        check(threwOn(() -> new KindredRules.Kin("control", "", true,
                        KindredRules.POISONS, KindredRules.OPEN, 1,
                        10.0D, 1.0D, 0.3D, 20, false)),
                "a creature belonging to no kindred is accepted, so nothing holds "
                        + "it to being different from anything");
        check(threwOn(() -> new KindredRules.Kin("control", "control", true,
                        "", KindredRules.OPEN, 1, 10.0D, 1.0D, 0.3D, 20, false)),
                "a creature that does nothing its kin do not is accepted");
        check(threwOn(() -> new KindredRules.Kin("control", "control", true,
                        KindredRules.POISONS, "", 1, 10.0D, 1.0D, 0.3D, 20, false)),
                "a creature that lives nowhere is accepted");
        check(threwOn(() -> new KindredRules.Kin("control", "control", true,
                        KindredRules.POISONS, KindredRules.OPEN, 1,
                        10.0D, 1.0D, 0.3D, 0, false)),
                "a creature nothing ever spawns is accepted");

        // And the sweep: the spread rule is arithmetic, so it is swept rather
        // than sampled. Every pair of threats against every cap, and the rule
        // has to agree with the division every time.
        int swept = 0;
        java.util.Random random = new java.util.Random(27_1321L);
        for (int round = 0; round < 200_000; round++) {
            double health = 1.0D + random.nextInt(200);
            double other = 1.0D + random.nextInt(200);
            double cap = 1.0D + random.nextInt(5);
            KindredRules.Kin a = new KindredRules.Kin("a" + round, "control", true,
                    KindredRules.POISONS, KindredRules.OPEN, 1, health, 3.0D, 0.3D,
                    20, false);
            KindredRules.Kin b = new KindredRules.Kin("b" + round, "control", false,
                    KindredRules.FREEZES, KindredRules.COLD, 1, other, 3.0D, 0.3D,
                    20, false);
            double least = Math.min(a.threat(), b.threat());
            double most = Math.max(a.threat(), b.threat());
            boolean wide = most > least * cap;
            check(wide == !KindredRules.faultsInSpread(List.of(a, b), cap).isEmpty(),
                    "a kindred of " + Math.round(most) + " over " + Math.round(least)
                            + " against a cap of " + cap + " is reported wrongly");
            swept++;
        }

        System.out.println("  the kindred: " + KindredRules.KINDRED.size()
                + " creature(s) in "
                + KindredRules.kindreds(KindredRules.KINDRED).size() + " kindred(s) "
                + "over " + KindredRules.TRAITS.size() + " thing(s) a player has to "
                + "do differently and " + KindredRules.HOMES.size() + " place(s) the "
                + "game really has, no two of one kindred doing one thing or standing "
                + "in one place, " + swept + " widths swept, and not one version of "
                + "something that is really a rung of its own");
    }
}
