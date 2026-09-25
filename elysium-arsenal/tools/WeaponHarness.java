package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link WeaponRules}, executed.
 *
 * <p>Five weapon mods in this pack add about a hundred swords between them.
 * What a player ends up with is three. The other ninety-seven are not badly
 * made — they are simply never compared to each other, because comparing a
 * hundred weapons against each other is not something anybody does by reading.
 *
 * <p>So: no weapon of a tier may be at least as good as another on every axis
 * and better on one, every weapon must be the best at something, the tiers
 * must climb, and the game's own sword must still be worth carrying. All four
 * are properties of the whole set.
 */
public final class WeaponHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theCatalogue();
        theDomination();
        theCrowns();
        theRungs();
        theLadder();
        theGame();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no weapon of a tier beaten on every axis "
                + "by another, every one of them the best at something, a ladder that "
                + "climbs, a side-grade that is actually to one side, and the game's "
                + "own sword still worth carrying");
    }

    // ==================================================================

    private static void theCatalogue() {
        Set<String> names = new HashSet<>();
        for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
            check(names.add(kind.name()), kind.name() + " is declared twice");
            check(kind.damage() > 0.0F && kind.speed() > 0.0F,
                    kind.name() + " does nothing");
        }
        check(WeaponRules.catalogue().size()
                        == WeaponRules.KINDS.size() * WeaponRules.TIERS.size(),
                "the catalogue is not every kind at every tier");

        // Armour that a weapon goes straight through is armour that is a
        // cosmetic, so the record refuses it outright.
        boolean refused = false;
        try {
            new WeaponRules.Weapon("cheat", 5.0F, 1.0F, 0.0F, 1, 1.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a weapon that ignores all armour is accepted");

        boolean tierRefused = false;
        try {
            WeaponRules.at(WeaponRules.KINDS.get(0), "mithril");
        } catch (IllegalArgumentException expected) {
            tierRefused = true;
        }
        check(tierRefused, "a tier nobody has numbers for is accepted, and every weapon "
                + "at it would be whatever the last one was");
        System.out.println("  the catalogue: " + WeaponRules.KINDS.size() + " kinds at "
                + WeaponRules.TIERS.size() + " tiers = "
                + WeaponRules.catalogue().size() + " weapons");
    }

    private static void theDomination() {
        check(WeaponRules.dominated().isEmpty(),
                "weapons nobody would ever make: " + WeaponRules.dominated());

        int pairs = 0;
        for (String tier : WeaponRules.TIERS) {
            for (WeaponRules.Weapon one : WeaponRules.KINDS) {
                for (WeaponRules.Weapon other : WeaponRules.KINDS) {
                    if (one == other) {
                        continue;
                    }
                    pairs++;
                    WeaponRules.Weapon a = WeaponRules.at(one, tier);
                    WeaponRules.Weapon b = WeaponRules.at(other, tier);
                    check(!a.dominates(b), a.name() + " beats " + b.name()
                            + " on every axis at " + tier);
                    check(!(a.dominates(b) && b.dominates(a)),
                            "two weapons dominate each other, which cannot happen and "
                                    + "means the comparison is not a comparison");
                }
            }
        }

        // And the checker itself, swept: a weapon built to be worse than
        // another on every axis must always be reported, and one built to be
        // better on something must never be. A domination check that quietly
        // stops reporting is a catalogue that quietly fills with dead content.
        Random random = new Random(11_235_813L);
        for (int round = 0; round < 200_000; round++) {
            float damage = 1.0F + random.nextFloat() * 20.0F;
            float speed = 0.2F + random.nextFloat() * 3.0F;
            float reach = random.nextFloat() * 3.0F;
            int sweep = 1 + random.nextInt(4);
            float pierce = random.nextFloat() * 0.9F;
            WeaponRules.Weapon good = new WeaponRules.Weapon("good", damage, speed,
                    reach, sweep, pierce);
            WeaponRules.Weapon worse = new WeaponRules.Weapon("worse",
                    damage * 0.5F, speed * 0.5F, reach * 0.5F, sweep, pierce * 0.5F);
            check(good.dominates(worse) || !good.betterSomewhereThan(worse),
                    "a weapon worse on every axis was not reported as dominated");
            check(!worse.dominates(good), "the worse weapon dominated the better one");
            check(!good.dominates(good), "a weapon dominates itself, so every weapon in "
                    + "the module is dead content");

            WeaponRules.Weapon sideways = new WeaponRules.Weapon("sideways",
                    damage * 0.5F, speed * 2.0F, reach, sweep, pierce);
            check(!good.dominates(sideways),
                    "a weapon that trades damage for speed was reported as dominated, "
                            + "which would forbid the only interesting thing a weapon "
                            + "set can do");
        }
        check(pairs > 100, "only " + pairs + " ordered pairs, which is too few for the "
                + "check to mean anything");
        System.out.println("  domination: " + pairs + " ordered pairs of weapons at a "
                + "tier, and 200,000 built pairs where the checker had to be right");
    }

    private static void theCrowns() {
        check(WeaponRules.bestAtNothing().isEmpty(),
                "weapons that are the answer to no question: "
                        + WeaponRules.bestAtNothing());

        // There are seven crowns and six kinds. A seventh kind would have to
        // share one, which is the hundred-swords problem starting.
        check(WeaponRules.KINDS.size() <= 7,
                "there are " + WeaponRules.KINDS.size() + " kinds and seven things a "
                        + "weapon can be best at, so at least one of them is there to "
                        + "fill out a loot table");
        for (String tier : WeaponRules.TIERS) {
            float spread = WeaponRules.spread(tier);
            check(spread <= WeaponRules.WIDEST_SPREAD,
                    "at " + tier + " the best weapon against one thing does " + spread
                            + " times the worst, so the choice is not a choice");
            check(spread > 1.0F,
                    "at " + tier + " every weapon does exactly the same damage a "
                            + "second, so the six are one weapon with six names");
        }
        System.out.println("  the crowns: every kind the best at something, and the "
                + "best against one thing never more than "
                + Math.round(WeaponRules.WIDEST_SPREAD * 100) / 100.0
                + " times the worst");
    }

    private static void theRungs() {
        java.util.Set<String> names = new HashSet<>();
        for (WeaponRules.Rung rung : WeaponRules.RUNGS) {
            check(names.add(rung.name()), rung.name() + " is two rungs");
            check(WeaponRules.TIER_SHARE.containsKey(rung.name())
                            && WeaponRules.TIER_HASTE.containsKey(rung.name()),
                    rung.name() + " is a rung with no numbers, so every weapon at it is whatever "
                            + "the last one was");
            if (rung.cordon()) {
                check(rung.uses() > 0 && rung.speed() > 0.0F && rung.enchant() > 0 && rung.level() >= 2,
                        rung.name() + "'s tier has a number that is not a number");
                check(rung.material().startsWith("c:"),
                        rung.name() + " is made of " + rung.material() + ", which is not a common tag, "
                                + "so its weapons would need one exact item");
                check(rung.colour().matches("#[0-9a-f]{6}"), rung.name() + " has no hue to draw in");
            }
        }
        check(WeaponRules.TIERS.size() == WeaponRules.RUNGS.size(), "a rung is not a tier");
        check(WeaponRules.CORDON.size() == 10, "the Cordon brought " + WeaponRules.CORDON.size()
                + " tiers, not the ten sets that had tools");
        for (WeaponRules.Cordon cordon : WeaponRules.CORDON) {
            check(WeaponRules.rung(cordon.name()) != null, cordon.name() + " was never placed");
        }
        // The ladder climbs, at every step, by more than reforging can be worth.
        for (int rung = 1; rung < WeaponRules.LADDER.size(); rung++) {
            float below = WeaponRules.TIER_SHARE.get(WeaponRules.LADDER.get(rung - 1));
            float above = WeaponRules.TIER_SHARE.get(WeaponRules.LADDER.get(rung));
            check(above > below, WeaponRules.LADDER.get(rung) + " is no better than the rung below it");
            check(above / below >= 1.10F, WeaponRules.LADDER.get(rung) + " is only " + (above / below)
                    + " of the rung below, a step a reforge could be worth");
        }
        check(Math.abs(WeaponRules.smallestStep() - 1.12F) < 0.01F,
                "the smallest step is " + WeaponRules.smallestStep() + ", so the Cordon's rungs moved "
                        + "reforging's ceiling");
        // Every side-grade trades: less damage than the rung above it, and quicker.
        float top = WeaponRules.TIER_SHARE.get(WeaponRules.LADDER.get(WeaponRules.LADDER.size() - 1));
        for (WeaponRules.Rung rung : WeaponRules.RUNGS) {
            if (rung.onLadder()) {
                continue;
            }
            check(rung.haste() > 1.0F, rung.name() + " sits beside the ladder and is not quicker, so "
                    + "it is simply a worse rung and nobody makes one");
            check(rung.share() < top, rung.name() + " beside the ladder out-damages the top of it");
            boolean beside = false;
            for (String on : WeaponRules.LADDER) {
                beside |= WeaponRules.TIER_SHARE.get(on) == rung.share();
            }
            check(beside, rung.name() + " has a share no rung has, so it is a rung in all but name");
        }
        // Gold is what every side-grade copies: iron's speed and a rung less damage.
        check(WeaponRules.TIER_SHARE.get("golden") < WeaponRules.TIER_SHARE.get("iron")
                        && WeaponRules.TIER_HASTE.get("golden") > 1.0F,
                "gold is not quick and weak, which is what it is in the game this is in");
        // The placing is by damage, and a tie is a side-grade of the rung it ties.
        check(!WeaponRules.rung("lunite").onLadder() && WeaponRules.rung("lunite").share()
                        == WeaponRules.TIER_SHARE.get("iron"),
                "lunite, at the game's diamond damage, is not diamond's side-grade with iron's share");
        check(WeaponRules.rung("relicsteel").onLadder() && !WeaponRules.rung("phasium").onLadder(),
                "two Cordon tiers at one damage did not become a rung and its side-grade");
        check(WeaponRules.rung("nullsteel").share() > WeaponRules.rung("hemalite").share()
                        && WeaponRules.rung("hemalite").share() > WeaponRules.rung("jade").share(),
                "the Cordon's ladder is not in Valoria's order");
        System.out.println("  the rungs: " + WeaponRules.RUNGS.size() + " tiers, "
                + WeaponRules.LADDER.size() + " on the ladder and "
                + (WeaponRules.RUNGS.size() - WeaponRules.LADDER.size()) + " beside it, the smallest "
                + "step still diamond to netherite");
    }

    private static void theLadder() {
        check(WeaponRules.flatLadders().isEmpty(),
                "ladders that do not climb: " + WeaponRules.flatLadders());
        for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
            for (int rung = 1; rung < WeaponRules.LADDER.size(); rung++) {
                WeaponRules.Weapon below =
                        WeaponRules.at(kind, WeaponRules.LADDER.get(rung - 1));
                WeaponRules.Weapon above =
                        WeaponRules.at(kind, WeaponRules.LADDER.get(rung));
                check(above.damage() > below.damage(),
                        kind.name() + " does no more damage one rung up");
                check(above.reach() == below.reach(),
                        kind.name() + " reaches further for being made of something "
                                + "else, and a netherite spear is not a longer spear");
                check(above.sweep() == below.sweep(),
                        kind.name() + " hits more things for being made of something "
                                + "else");
            }
        }
        check(!WeaponRules.LADDER.contains("golden"),
                "gold is on the ladder, and a ladder with gold in registry order dips "
                        + "in the middle — which is what this check was written for");
        System.out.println("  the ladder: " + WeaponRules.LADDER.size()
                + " rungs that climb, with gold beside them rather than on them");
    }

    private static void theGame() {
        check(WeaponRules.outclassesTheGame().isEmpty(),
                "the game's own sword against ours: "
                        + WeaponRules.outclassesTheGame());

        WeaponRules.Weapon sword = new WeaponRules.Weapon("sword",
                WeaponRules.SWORD_DAMAGE, WeaponRules.SWORD_SPEED,
                WeaponRules.SWORD_REACH, 1, 0.0F);
        for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
            WeaponRules.Weapon ours = WeaponRules.at(kind, "diamond");
            check(!ours.dominates(sword),
                    "a diamond " + kind.name() + " beats a diamond sword on every axis");
        }
        // And the other way: if the sword beat all six, the module would be
        // six ways of being worse at fighting.
        int beaten = 0;
        for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
            if (sword.dominates(WeaponRules.at(kind, "diamond"))) {
                beaten++;
            }
        }
        check(beaten == 0, beaten + " of our weapons are beaten outright by the game's "
                + "own sword, so they are ways of being worse at fighting");
        System.out.println("  the game: a diamond sword beats none of ours outright and "
                + "is beaten outright by none of ours");
    }
}
