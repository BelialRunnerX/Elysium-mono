package com.elysium.arsenal.rules;

import com.elysium.lib.ability.ElysiumAbilityRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every number in {@link RangedRules} and {@link UniqueRules}, executed.
 *
 * <p>The bows have the same problem the swords had and one more besides. Six
 * kinds is six chances for one of them to be a worse version of another; and a
 * bow, unlike a sword, is also competing with the bow the game already ships,
 * which is free, craftable on day one and perfectly good. A set of six that
 * does not beat it is six items nobody makes, and a set of six that beats it
 * on damage, draw and range at once has not added six bows, it has deleted
 * one.
 *
 * <p>The uniques have a third problem on top of both: they are ordinary
 * weapons that also do something, and the something is priced by a system
 * that lives in another module. A unique that carries more than its tier's
 * band is not a stronger unique, it is a reason not to climb the tier above.
 */
public final class RangedHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    /**
     * One complaint, short enough that twenty of them are still readable.
     *
     * <p>A failure that prints a list of four hundred things is a failure
     * nobody reads to the end of, and the thing that went wrong is usually in
     * the first one.
     */
    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theCatalogue();
        theDomination();
        theCorners();
        theLadder();
        theGamesOwnBow();
        theAmmunition();
        theUniques();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: six bows each owning exactly one corner "
                + "of six, a ladder that climbs, the game's own bow neither deleted "
                + "nor left better than all of ours, twelve shots none of which is "
                + "another under a different name, and twenty-four uniques none of "
                + "which carries more than its tier's band");
    }

    // ==================================================================

    private static void theCatalogue() {
        Set<String> names = new HashSet<>();
        for (RangedRules.Bow kind : RangedRules.KINDS) {
            check(names.add(kind.name()), kind.name() + " is declared twice");
            check(kind.drawTicks() >= 1, kind.name() + " draws in no time at all");
        }
        check(RangedRules.catalogue().size()
                        == RangedRules.KINDS.size() * RangedRules.TIERS.size(),
                "the catalogue is not every kind at every tier");
        check(RangedRules.collidingWithMelee().isEmpty(),
                "bows that are also melee weapons: "
                        + RangedRules.collidingWithMelee());

        // The record's own refusals. A bow that never finishes drawing is a
        // bow that never fires, and it should be impossible to write one.
        int refused = 0;
        String[] whys = {"a bow that never finishes drawing",
                "a bow that does no damage", "a bow that carries nowhere",
                "a bow that looses nothing", "a bow with no accuracy",
                "a bow with more than perfect accuracy",
                "a bow that leaves you standing still",
                "a bow that leaves you faster than not holding it"};
        float[][] broken = {
                {0.0F, 4.0F, 1.0F, 1, 0.7F, 1.0F},
                {2.0F, 0.0F, 1.0F, 1, 0.7F, 1.0F},
                {2.0F, 4.0F, 0.0F, 1, 0.7F, 1.0F},
                {2.0F, 4.0F, 1.0F, 0, 0.7F, 1.0F},
                {2.0F, 4.0F, 1.0F, 1, 0.0F, 1.0F},
                {2.0F, 4.0F, 1.0F, 1, 1.4F, 1.0F},
                {2.0F, 4.0F, 1.0F, 1, 0.7F, 0.0F},
                {2.0F, 4.0F, 1.0F, 1, 0.7F, 1.4F}};
        for (int one = 0; one < broken.length; one++) {
            float[] numbers = broken[one];
            boolean threw = false;
            try {
                new RangedRules.Bow("broken", numbers[0], numbers[1], numbers[2],
                        (int) numbers[3], numbers[4], numbers[5]);
            } catch (IllegalArgumentException | NullPointerException expected) {
                threw = true;
            }
            check(threw, whys[one] + " is accepted");
            if (threw) {
                refused++;
            }
        }

        boolean tierRefused = false;
        try {
            RangedRules.at(RangedRules.KINDS.get(0), "mithril");
        } catch (IllegalArgumentException expected) {
            tierRefused = true;
        }
        check(tierRefused, "a tier nobody has numbers for is accepted, and every bow "
                + "at it would be whatever the last one was");

        System.out.println("  the catalogue: " + RangedRules.KINDS.size()
                + " kinds at " + RangedRules.TIERS.size() + " tiers = "
                + RangedRules.catalogue().size() + " bows, and " + refused
                + " of " + broken.length + " impossible ones refused");
    }

    private static void theDomination() {
        check(RangedRules.dominated().isEmpty(),
                "bows nobody would ever make: " + RangedRules.dominated());
        check(RangedRules.bestAtNothing().isEmpty(),
                "bows that are the answer to no question: "
                        + RangedRules.bestAtNothing());

        int pairs = 0;
        for (String tier : RangedRules.TIERS) {
            for (RangedRules.Bow one : RangedRules.KINDS) {
                for (RangedRules.Bow other : RangedRules.KINDS) {
                    if (one == other) {
                        continue;
                    }
                    pairs++;
                    RangedRules.Bow a = RangedRules.at(one, tier);
                    RangedRules.Bow b = RangedRules.at(other, tier);
                    check(!a.dominates(b), a.name() + " beats " + b.name()
                            + " on every axis at " + tier);
                }
            }
        }
        check(pairs > 100, "only " + pairs + " ordered pairs, which is too few for "
                + "the check to mean anything");
        System.out.println("  domination: " + pairs + " ordered pairs of bows at a "
                + "tier, none of them one-sided");
    }

    private static void theCorners() {
        check(RangedRules.cornersUnclaimed().isEmpty(),
                "corners nobody owns: " + RangedRules.cornersUnclaimed());
        check(RangedRules.KINDS.size() == RangedRules.AXES,
                "there are " + RangedRules.KINDS.size() + " kinds and "
                        + RangedRules.AXES + " axes, so either a corner is shared or "
                        + "one kind is there to fill out a loot table");

        // And each kind owns exactly one, which is a stronger claim than
        // "every corner is owned": one kind could own two while another owns
        // none, and cornersUnclaimed() would still be empty if the sharing
        // check alone were doing the work.
        TreeMap<String, Integer> owned = new TreeMap<>();
        for (RangedRules.Bow kind : RangedRules.KINDS) {
            owned.put(kind.name(), 0);
        }
        for (int axis = 0; axis < RangedRules.AXES; axis++) {
            String best = null;
            float most = -1.0F;
            for (RangedRules.Bow kind : RangedRules.KINDS) {
                float mine = axisOf(kind, axis);
                if (mine > most) {
                    most = mine;
                    best = kind.name();
                }
            }
            owned.merge(best, 1, Integer::sum);
        }
        for (String name : owned.keySet()) {
            check(owned.get(name) == 1, name + " owns " + owned.get(name)
                    + " corners, and with six kinds and six axes that leaves another "
                    + "kind with none");
        }
        System.out.println("  the corners: " + owned);
    }

    private static float axisOf(RangedRules.Bow bow, int axis) {
        return switch (axis) {
            case 0 -> bow.draw();
            case 1 -> bow.damage();
            case 2 -> bow.range();
            case 3 -> bow.shots();
            case 4 -> bow.accuracy();
            default -> bow.handling();
        };
    }

    private static void theLadder() {
        check(RangedRules.ladderDips().isEmpty(),
                "rungs that buy nothing: " + RangedRules.ladderDips());
        for (RangedRules.Bow kind : RangedRules.KINDS) {
            for (int rung = 1; rung < RangedRules.LADDER.size(); rung++) {
                RangedRules.Bow below =
                        RangedRules.at(kind, RangedRules.LADDER.get(rung - 1));
                RangedRules.Bow above =
                        RangedRules.at(kind, RangedRules.LADDER.get(rung));
                check(above.dps() > below.dps(),
                        kind.name() + " does no more a second one rung up");
                check(above.shots() == below.shots(),
                        kind.name() + " looses more arrows for being made of "
                                + "something else, and a netherite repeater is not a "
                                + "wider repeater");
            }
        }
        check(!RangedRules.LADDER.contains("golden"),
                "gold is on the ladder, and a ladder with gold in registry order dips "
                        + "in the middle");
        System.out.println("  the ladder: " + RangedRules.LADDER.size()
                + " rungs that climb, at every one of " + RangedRules.KINDS.size()
                + " kinds");
    }

    private static void theGamesOwnBow() {
        check(RangedRules.replacingTheGamesBow().isEmpty(),
                "bows that delete the game's own: "
                        + RangedRules.replacingTheGamesBow());

        // And the other way. If the game's bow beat every one of ours on
        // damage at a tier the player can reach, the module would be six ways
        // of being worse at shooting.
        int beaten = 0;
        for (RangedRules.Bow kind : RangedRules.KINDS) {
            if (RangedRules.at(kind, "diamond").damage()
                    < RangedRules.VANILLA_BOW_DAMAGE * 0.25F) {
                beaten++;
            }
        }
        check(beaten < RangedRules.KINDS.size(),
                "every bow here does less than a quarter of what the game's own bow "
                        + "does, so the module is six ways of being worse at shooting");
        System.out.println("  the game's bow: neither deleted by ours nor better than "
                + "all of them");
    }

    private static void theAmmunition() {
        check(RangedRules.unfireable().isEmpty(),
                "ammunition nothing can fire: " + RangedRules.unfireable());
        check(RangedRules.repeatedShots().isEmpty(),
                "ammunition that is another under a different name: "
                        + RangedRules.repeatedShots());

        int arrows = RangedRules.ammunition(false).size();
        int bolts = RangedRules.ammunition(true).size();
        check(arrows > 0, "nothing at all is an arrow");
        check(bolts > 0, "nothing at all is a bolt, so the crossbow cannot fire");
        check(arrows + bolts == RangedRules.SHOTS.size(),
                "some shot is neither an arrow nor a bolt");

        // A crossbow takes bolts and nothing else does. Written out rather
        // than read off takesBolts(), because a check that asks the function
        // it is checking agrees with itself by construction.
        for (RangedRules.Bow kind : RangedRules.KINDS) {
            boolean bolt = RangedRules.takesBolts(kind);
            check(bolt == kind.name().equals("crossbow"),
                    kind.name() + " takes " + (bolt ? "bolts" : "arrows")
                            + ", which is not what its name says it takes");
        }

        // Nothing may be a strictly better shot of its own sort: a barbed
        // arrow that did everything a flint arrow does and bled as well would
        // make flint arrows a crafting ingredient.
        for (RangedRules.Shot one : RangedRules.SHOTS) {
            for (RangedRules.Shot other : RangedRules.SHOTS) {
                if (one == other || one.forBolt() != other.forBolt()) {
                    continue;
                }
                boolean sameEffect = one.effect().equals(other.effect());
                check(!(sameEffect && one.damage() > other.damage()
                                && !other.effect().isEmpty()),
                        one.name() + " does what " + other.name()
                                + " does and hits harder");
            }
        }
        System.out.println("  the ammunition: " + arrows + " arrow(s) and " + bolts
                + " bolt(s), none of them another under a different name");
    }

    private static void theUniques() {
        check(UniqueRules.shapeless().isEmpty(),
                "uniques on shapes we do not make: " + UniqueRules.shapeless());
        check(UniqueRules.illegal().isEmpty(),
                "uniques carrying abilities no item may carry: "
                        + UniqueRules.illegal());
        check(UniqueRules.shapesWithout().isEmpty(),
                "shapes with nothing unique on them: "
                        + UniqueRules.shapesWithout());
        check(UniqueRules.indistinguishable().isEmpty(),
                "uniques that are one unique under two names: "
                        + UniqueRules.indistinguishable());
        check(UniqueRules.rarityBackwards().isEmpty(),
                "rarity that runs backwards: " + UniqueRules.rarityBackwards());
        check(UniqueRules.strongerThanTheirShape().isEmpty(),
                "uniques that are better weapons rather than different ones: "
                        + UniqueRules.strongerThanTheirShape());
        check(UniqueRules.repeated().isEmpty(),
                "uniques declared twice: " + UniqueRules.repeated());
        check(UniqueRules.collidingWithTheCatalogue().isEmpty(),
                "uniques that collide with an ordinary weapon: "
                        + UniqueRules.collidingWithTheCatalogue());

        // Every ability a unique names has to exist. A misspelling here is a
        // unique that does nothing and says nothing about it.
        Set<String> known = new TreeSet<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            known.add(ability.id());
        }
        for (UniqueRules.Unique one : UniqueRules.UNIQUES) {
            for (String named : one.abilities()) {
                check(known.contains(named), one.id() + " names " + named
                        + ", which is not an ability the library has");
            }
            check(one.worth() > 0.0F, one.id() + " is worth nothing, so it is a skin");
        }

        // Every unique must be smithable from a weapon no other unique is
        // smithed from, or two of them are one recipe and the loser cannot be
        // obtained at all. This is the roster's side of the check the
        // generator makes about the recipe files.
        TreeMap<String, String> seated = new TreeMap<>();
        for (UniqueRules.Unique one : UniqueRules.UNIQUES) {
            String base = one.tier() + "_" + one.shape();
            String had = seated.put(base, one.id());
            check(had == null, one.id() + " and " + had + " are both smithed from a "
                    + base + ", so one of them cannot be made");
        }

        // And the bands hold with room in them: a tier whose band is already
        // full at the commonest rarity has nothing to give the rarest.
        for (String tier : UniqueRules.TIERS) {
            Float band = UniqueRules.WORTH_AT_TIER.get(tier);
            check(band != null && band > 0.0F,
                    tier + " has no band, so a unique at it may carry anything");
        }
        for (int rung = 1; rung < UniqueRules.TIERS.size(); rung++) {
            Float below = UniqueRules.WORTH_AT_TIER.get(UniqueRules.TIERS.get(rung - 1));
            Float above = UniqueRules.WORTH_AT_TIER.get(UniqueRules.TIERS.get(rung));
            check(below != null && above != null && above > below,
                    "the band at " + UniqueRules.TIERS.get(rung) + " is no wider than "
                            + "the one below it, so climbing buys nothing");
        }
        System.out.println("  the uniques: " + UniqueRules.UNIQUES.size()
                + " across " + UniqueRules.shapes().size()
                + " shapes, each on a base no other one is smithed from");
    }

    /**
     * The checkers themselves, against things built to be caught.
     *
     * <p>Every predicate above answers "no" when the roster is healthy, and a
     * predicate that has stopped being able to answer "yes" answers "no" just
     * as convincingly. This module has already shipped one such: a check that
     * compared a weapon's damage to the same weapon's damage, which could not
     * report anything and sat in the file looking like a check.
     */
    private static void theControls() {
        RangedRules.Bow better = new RangedRules.Bow("better", 2.0F, 8.0F, 1.5F, 2,
                0.9F, 0.9F);
        RangedRules.Bow worse = new RangedRules.Bow("worse", 1.0F, 4.0F, 0.75F, 1,
                0.45F, 0.45F);
        check(better.dominates(worse),
                "a bow worse on every axis is not reported as dominated, so the "
                        + "domination check cannot report anything");
        check(!worse.dominates(better), "the worse bow dominates the better one");
        check(!better.dominates(better), "a bow dominates itself, which would make "
                + "every bow in the module dead content");

        RangedRules.Bow sideways = new RangedRules.Bow("sideways", 4.0F, 4.0F, 0.75F,
                1, 0.45F, 0.45F);
        check(!better.dominates(sideways),
                "a bow that trades damage for draw was reported as dominated, which "
                        + "would forbid the only interesting thing a bow set can do");

        // The same for the uniques' band, which is the check that replaced the
        // one that compared a number to itself.
        UniqueRules.Unique over = new UniqueRules.Unique("control_over", "sabre",
                "iron", 1, List.of("shockwave", "cleave", "sunder"));
        boolean caught = false;
        Float band = UniqueRules.WORTH_AT_TIER.get("iron");
        if (band != null && over.worth() > band) {
            caught = true;
        }
        check(caught, "a unique carrying three of the heaviest abilities in the "
                + "library at iron is inside the iron band, so the band is not a band");

        UniqueRules.Unique under = new UniqueRules.Unique("control_under", "sabre",
                "netherite", 1, List.of("swiftfoot"));
        check(under.worth() <= UniqueRules.WORTH_AT_TIER.get("netherite"),
                "a unique carrying one light ability at netherite is outside the "
                        + "netherite band, so the band forbids the roster it exists to "
                        + "allow");

        // And a sweep, so the predicate is not merely right about two cases.
        Random random = new Random(2_718_281L);
        List<String> roster = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            roster.add(ability.id());
        }
        int reported = 0;
        int rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            String one = roster.get(random.nextInt(roster.size()));
            String two = roster.get(random.nextInt(roster.size()));
            if (one.equals(two)) {
                continue;
            }
            if (!ElysiumAbilityRules.cannotShareAnItem(List.of(one, two)).isEmpty()) {
                continue;
            }
            rounds++;
            UniqueRules.Unique built = new UniqueRules.Unique("control_" + round,
                    "sabre", "iron", 1, List.of(one, two));
            if (built.worth() > UniqueRules.WORTH_AT_TIER.get("iron")) {
                reported++;
            }
        }
        check(rounds > 1_000, "only " + rounds + " pairs could be built, which is too "
                + "few for the sweep to mean anything");
        check(reported > 0, "not one pair of abilities in the whole library is over "
                + "the iron band, so the iron band cannot refuse anything");
        check(reported < rounds, "every pair of abilities in the whole library is over "
                + "the iron band, so no iron unique could carry two of anything");
        System.out.println("  the controls: bows built to be dominated are, bows built "
                + "to be side-grades are not, and " + reported + " of " + rounds
                + " ability pairs are over the iron band");
    }
}
