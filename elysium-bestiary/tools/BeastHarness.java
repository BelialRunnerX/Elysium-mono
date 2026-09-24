package com.elysium.bestiary.rules;

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
}
