package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reforging, executed.
 *
 * <p>Everything else in this module is checked by asking whether any item is
 * pointless. That question is the wrong one here and asking it would delete
 * the mechanic: a grade is not something a player picks, it is something that
 * happens to them, and a strictly worse grade is the risk rather than dead
 * content.
 *
 * <p>So what is swept instead is the shape of the gamble — that losing is
 * possible and common, that winning is possible and rare, that the table pays
 * a little and not much, that the best roll is never worth a tier of the
 * ladder, and that every good roll makes the next one dearer. And the roll
 * itself, two million times, because a weighted table that quietly never
 * returns its last entry is a grade that does not exist and nothing about the
 * numbers would say so.
 */
public final class ForgeHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theTable();
        theHammers();
        theGamble();
        theCeiling();
        thePrice();
        theRoll();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: five kinds of gear each rolled by "
                + "exactly one hammer, a table likelier to spoil than to improve, a "
                + "best roll worth less than the smallest step up the ladder, a price "
                + "that climbs with what you are risking, and a weighted roll that "
                + "returns every grade in the proportion it says it will");
    }

    // ==================================================================

    private static void theTable() {
        Set<String> names = new HashSet<>();
        for (ForgeRules.Grade one : ForgeRules.GRADES) {
            check(names.add(one.id()), one.id() + " is declared twice");
        }
        check(ForgeRules.repeated().isEmpty(),
                "grades that collide with gear: " + ForgeRules.repeated());
        check(ForgeRules.indistinguishable().isEmpty(),
                "grades that are one grade under two names: "
                        + ForgeRules.indistinguishable());

        // Every attribute a grade names has to have a stated worth, or the
        // arithmetic below is adding percentages of incomparable things --
        // which is exactly what the first draft of this file did.
        for (ForgeRules.Grade one : ForgeRules.GRADES) {
            for (ForgeRules.Change change : one.changes()) {
                boolean stated = true;
                try {
                    ForgeRules.matters(change.attribute());
                } catch (IllegalArgumentException expected) {
                    stated = false;
                }
                check(stated, one.id() + " changes " + change.attribute()
                        + ", which has no stated worth, so what the grade comes to "
                        + "is a number nobody chose");
            }
        }

        // The record's own refusals.
        int refused = 0;
        try {
            new ForgeRules.Change("attack_damage", true, 0.0F);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ForgeRules.Change("attack_damage", true, -1.5F);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ForgeRules.Grade("nowhere", "spaceship", 1, 1, 1, List.of());
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ForgeRules.Grade("never", "melee", 0, 1, 1, List.of());
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 4, refused + " of 4 impossible grades were refused");

        System.out.println("  the table: " + ForgeRules.GRADES.size()
                + " grades across " + ForgeRules.KINDS.size() + " kinds of gear");
    }

    private static void theHammers() {
        check(ForgeRules.unhammered().isEmpty(),
                "gear nothing rolls, or gear two things roll: "
                        + ForgeRules.unhammered());
        for (String kind : ForgeRules.KINDS) {
            check(ForgeRules.hammerFor(kind) != null,
                    kind + " has no hammer");
            check(!ForgeRules.of(kind).isEmpty(), kind + " has no grades");
        }
        int covered = 0;
        for (List<String> kinds : ForgeRules.HAMMERS.values()) {
            covered += kinds.size();
        }
        check(covered == ForgeRules.KINDS.size(), "the hammers between them roll "
                + covered + " kinds and there are " + ForgeRules.KINDS.size()
                + ", so something is rolled twice or not at all");
        System.out.println("  the hammers: " + ForgeRules.HAMMERS.size()
                + ", covering " + covered + " kinds of gear between them");
    }

    private static void theGamble() {
        check(ForgeRules.noGamble().isEmpty(),
                "kinds with nothing at stake: " + ForgeRules.noGamble());
        check(ForgeRules.badOdds().isEmpty(),
                "tables that pay too well or not at all: " + ForgeRules.badOdds());
        check(ForgeRules.cruelJackpots().isEmpty(),
                "losses rarer than wins: " + ForgeRules.cruelJackpots());

        for (String kind : ForgeRules.KINDS) {
            double loss = ForgeRules.chanceOfLoss(kind);
            double win = ForgeRules.chanceOfWin(kind);
            check(loss + win <= 1.0D + 1e-9D,
                    kind + " loses and wins with a total chance above one");
            check(loss > win, kind + " is likelier to improve than to spoil");
            check(loss + win < 1.0D,
                    kind + " has no outcome that leaves the gear as it was, so there "
                            + "is no floor under a run of bad luck");
        }
        System.out.println("  the gamble: spoil "
                + Math.round(ForgeRules.chanceOfLoss("melee") * 1000) / 10.0
                + "%, improve "
                + Math.round(ForgeRules.chanceOfWin("melee") * 1000) / 10.0
                + "%, worth "
                + Math.round(ForgeRules.expected("melee") * 10_000) / 100.0
                + "% a roll");
    }

    private static void theCeiling() {
        check(ForgeRules.outclassingTheLadder().isEmpty(),
                "grades worth a tier of the ladder: "
                        + ForgeRules.outclassingTheLadder());
        float step = ForgeRules.smallestLadderStep();
        check(step > 0.0F, "the weapon ladder has no step at all, so the ceiling is "
                + "zero and every grade in the game is over it — which would be this "
                + "check reporting a problem in a file it does not own");
        for (String kind : ForgeRules.KINDS) {
            ForgeRules.Grade best = ForgeRules.bestOf(kind);
            check(best != null && best.worth() > 0.0F,
                    kind + "'s best grade is not an improvement");
            check(best != null && best.worth() < step,
                    kind + "'s best grade is worth as much as climbing a tier");
        }
        System.out.println("  the ceiling: the smallest step up the weapon ladder is "
                + Math.round(step * 1000) / 10.0 + "%, and no grade reaches it");
    }

    private static void thePrice() {
        check(ForgeRules.costsThatDoNotClimb().isEmpty(),
                "good grades cheaper to gamble away than bad ones: "
                        + ForgeRules.costsThatDoNotClimb());
        for (String kind : ForgeRules.KINDS) {
            ForgeRules.Grade best = ForgeRules.bestOf(kind);
            ForgeRules.Grade worst = ForgeRules.worstOf(kind);
            check(best.experience() > worst.experience(),
                    kind + "'s best grade costs no more to roll away from than its "
                            + "worst, so the right play is to hold the hammer down");
            check(best.durability() > worst.durability(),
                    kind + "'s best grade wears the hammer no more than its worst");
            double rolls = ForgeRules.rollsToBest(kind);
            check(rolls >= 20.0D, kind + "'s best grade takes " + rolls + " rolls to "
                    + "see on average, which is an afternoon rather than a chase");
            check(Double.isFinite(rolls), kind + "'s best grade can never come up");
            check(ForgeRules.experienceToBest(kind) >= 50.0D,
                    kind + "'s best grade costs "
                            + Math.round(ForgeRules.experienceToBest(kind))
                            + " levels to chase, which is nothing");
        }
        System.out.println("  the price: about "
                + Math.round(ForgeRules.rollsToBest("melee")) + " rolls and "
                + Math.round(ForgeRules.experienceToBest("melee"))
                + " levels to see the best grade a blade can have");
    }

    /**
     * The weighted roll itself, swept.
     *
     * <p>Not arithmetic about the table but the function that reads it. A
     * weighted roll that silently never returns its last entry is a grade
     * that does not exist in the game, and nothing about the weights would
     * say so — every number in this file would still be right.
     */
    private static void theRoll() {
        Random random = new Random(1_618_033L);
        for (String kind : ForgeRules.KINDS) {
            TreeMap<String, Integer> seen = new TreeMap<>();
            for (ForgeRules.Grade one : ForgeRules.of(kind)) {
                seen.put(one.id(), 0);
            }
            int rounds = 400_000;
            for (int round = 0; round < rounds; round++) {
                ForgeRules.Grade rolled = ForgeRules.roll(kind, random.nextInt());
                seen.merge(rolled.id(), 1, Integer::sum);
            }
            for (ForgeRules.Grade one : ForgeRules.of(kind)) {
                int got = seen.get(one.id());
                check(got > 0, one.id() + " never came up in " + rounds + " rolls, so "
                        + "it is a grade that does not exist");
                double want = ForgeRules.chanceOf(one) * rounds;
                // Generous: this is looking for a table read wrongly, not for
                // a random number generator with a slight bias.
                check(Math.abs(got - want) < Math.max(200.0D, want * 0.20D),
                        one.id() + " came up " + got + " times and should have come "
                                + "up about " + Math.round(want));
            }
            // Negative and huge picks are what a random int actually is.
            // An implementation that took the modulus without flooring would
            // return the first grade for every negative pick, which is half
            // of them.
            for (int pick : new int[]{Integer.MIN_VALUE, -1, -97,
                    Integer.MAX_VALUE, 0}) {
                ForgeRules.Grade rolled = ForgeRules.roll(kind, pick);
                check(rolled != null && rolled.kind().equals(kind),
                        "a pick of " + pick + " rolled " + rolled + " for " + kind);
            }
        }
        System.out.println("  the roll: " + (400_000 * ForgeRules.KINDS.size())
                + " rolls, every grade coming up in the proportion it claims, and "
                + "negative picks landing inside the table");
    }

    /**
     * The checkers, against tables built to be caught.
     *
     * <p>Every predicate above answers "no" when the table is healthy, and one
     * that has lost the ability to answer "yes" answers "no" just as
     * convincingly. That failure has already happened twice in this module.
     */
    private static void theControls() {
        // A grade worth a whole tier of the ladder must be reported.
        float step = ForgeRules.smallestLadderStep();
        ForgeRules.Grade huge = new ForgeRules.Grade("control_huge", "melee", 1, 1, 1,
                List.of(new ForgeRules.Change("attack_damage", true, step * 2.0F)));
        check(huge.worth() >= step,
                "a grade worth twice the ladder's step comes out under the ceiling, "
                        + "so the ceiling check cannot report anything");

        // A side-grade must come out worth nothing much, not worth a lot.
        ForgeRules.Grade sideways = new ForgeRules.Grade("control_side", "melee", 1,
                1, 1, List.of(new ForgeRules.Change("attack_damage", true, 0.10F),
                new ForgeRules.Change("attack_speed", true, -0.125F)));
        check(Math.abs(sideways.worth()) < 0.02F,
                "a grade that trades ten per cent damage for the same in speed comes "
                        + "out worth " + sideways.worth() + ", so the weighting that "
                        + "makes two attributes comparable is not doing it");

        // And the weighting itself: knockback is not damage.
        ForgeRules.Grade knock = new ForgeRules.Grade("control_knock", "melee", 1, 1,
                1, List.of(new ForgeRules.Change("attack_knockback", true, 0.25F)));
        ForgeRules.Grade hit = new ForgeRules.Grade("control_hit", "melee", 1, 1, 1,
                List.of(new ForgeRules.Change("attack_damage", true, 0.25F)));
        check(knock.worth() < hit.worth(),
                "a quarter more knockback is worth as much as a quarter more damage, "
                        + "which is how a grade that gives a little damage and a lot "
                        + "of knockback came out looking like a tier of the ladder");

        // A loss and a win, built to be exactly that.
        ForgeRules.Grade loss = new ForgeRules.Grade("control_loss", "melee", 1, 1, 1,
                List.of(new ForgeRules.Change("armor", true, -0.10F)));
        check(loss.aLoss() && !loss.aWin(), "a grade that takes armour away is not a "
                + "loss, so the gamble check cannot see a spoiled roll");
        check(hit.aWin() && !hit.aLoss(), "a grade that adds damage is not a win");

        ForgeRules.Grade nothing = new ForgeRules.Grade("control_plain", "melee", 1,
                1, 1, List.of());
        check(!nothing.aWin() && !nothing.aLoss(),
                "a grade that changes nothing counts as a win or a loss, which would "
                        + "make the plain grade part of the odds it is the floor "
                        + "under");

        // A sweep, so none of the above is merely right about one case.
        Random random = new Random(577_215L);
        int wins = 0, losses = 0, rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            float amount = (random.nextFloat() - 0.5F) * 0.4F;
            if (Math.abs(amount) < 1e-4F) {
                continue;
            }
            rounds++;
            ForgeRules.Grade built = new ForgeRules.Grade("control_" + round, "melee",
                    1, 1, 1, List.of(new ForgeRules.Change("attack_damage", true,
                    amount)));
            if (built.aWin()) {
                wins++;
            }
            if (built.aLoss()) {
                losses++;
            }
            check(built.aWin() != built.aLoss(),
                    "a grade is both a win and a loss, or neither, at " + amount);
        }
        check(wins > 1_000 && losses > 1_000,
                "of " + rounds + " built grades only " + wins + " were wins and "
                        + losses + " losses, so one of the two answers is nearly "
                        + "unreachable");
        System.out.println("  the controls: " + rounds + " built grades sorted into "
                + wins + " wins and " + losses + " losses, a side-grade worth "
                + Math.round(sideways.worth() * 10_000) / 10_000.0
                + ", and knockback worth less than damage");
    }
}
