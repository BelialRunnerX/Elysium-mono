package com.elysium.lib.combat;

import com.elysium.lib.attribute.ElysiumAttributeSpec;
import com.elysium.lib.stats.ElysiumArmourRules;
import com.elysium.lib.stats.ElysiumShares;

/**
 * The combat attributes' arithmetic (P1), executed outside a world.
 *
 * What the source cannot show and this does: that pierce and shred take armour
 * apart in the right order and that toughness blunts them without ever turning
 * them off; that a blow with armour driven below zero grows rather than heals;
 * that crits repeat, fall off, and terminate however large the stat is; that
 * cold's slow has lost its ceiling but not its shape; that overheal fills
 * toward a share of max health and stops; and that draw speed hands out exactly
 * the ticks it promises over a second.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/sh elysium-lib/tools/CombatHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/sh" \
 *         com.elysium.lib.combat.CombatHarness
 */
public final class CombatHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Armour comes apart shred-first, and toughness never makes a target immune to piercing. */
    private static void pierceAndShred() {
        double armour = 20.0D;
        check(ElysiumCombatRules.piercedArmour(armour, 0.0D, 0.0D, 0.0D) == armour,
                "an attacker with no pierce or shred changes the target's armour");
        double shredded = ElysiumCombatRules.piercedArmour(armour, 0.5D, 0.0D, 0.0D);
        check(Math.abs(shredded - 10.0D) < 1e-9, "half shred took " + shredded + " instead of ten");
        double both = ElysiumCombatRules.piercedArmour(armour, 0.5D, 5.0D, 0.0D);
        check(Math.abs(both - 5.0D) < 1e-9, "shred then pierce gave " + both + " instead of five");
        check(ElysiumCombatRules.piercedArmour(armour, 0.5D, 0.0D, 10.0D) > shredded,
                "toughness did not blunt shred at all");
        for (double toughness : new double[] {0, 1, 10, 100, 10_000, 1e9}) {
            float bypass = ElysiumCombatRules.bypassResistance(toughness);
            check(bypass >= 0.0F && bypass < 1.0F,
                    "toughness " + toughness + " turns aside " + bypass + " of pierce, which is not a share");
            check(ElysiumCombatRules.piercedArmour(armour, 0.0D, 5.0D, toughness) < armour,
                    "toughness " + toughness + " made pierce do nothing at all");
        }
        check(ElysiumCombatRules.bypassResistance(1e9) > ElysiumCombatRules.bypassResistance(100.0D),
                "toughness stopped paying");
    }

    /** Armour past zero makes the blow bigger, and only past zero. */
    private static void negativeArmour() {
        check(ElysiumCombatRules.negativeArmourFactor(5.0D) == 1.0F, "armour that is left over grew the blow");
        check(ElysiumCombatRules.negativeArmourFactor(0.0D) == 1.0F, "armour at exactly zero grew the blow");
        float at20 = ElysiumCombatRules.negativeArmourFactor(-20.0D);
        check(Math.abs(at20 - 1.3F) < 1e-5F, "twenty points below zero gave " + at20 + " instead of 1.3");
        check(ElysiumCombatRules.negativeArmourFactor(-1000.0D) > at20, "more piercing stopped paying");
    }

    /** Vanilla's own share still applies to anything that is not a player. */
    private static void vanillaShareForMobs() {
        check(Math.abs(ElysiumCombatRules.vanillaArmourShare(20.0D) - 0.8F) < 1e-6F,
                "vanilla's twenty points no longer remove 80%");
        check(ElysiumCombatRules.vanillaArmourShare(200.0D) == ElysiumCombatRules.vanillaArmourShare(20.0D),
                "vanilla's ceiling has moved; mobs are supposed to stay as vanilla made them");
        check(ElysiumCombatRules.vanillaArmourShare(-5.0D) == 0.0F, "negative armour removed damage");
        check(ElysiumArmourRules.armourShare(40.0D) > ElysiumCombatRules.vanillaArmourShare(40.0D),
                "past vanilla's ceiling a player's armour is no better than a zombie's, "
                        + "which is the whole point of the replacement");
    }

    /** Crits repeat, weaken, and always stop. */
    private static void crits() {
        double odds = ElysiumAttributeSpec.odds(0.05D);
        check(ElysiumCombatRules.critMultiplier(odds, 1.5D, () -> 0.999D) == 1.0F,
                "a roll that fails still crit");
        check(ElysiumCombatRules.critMultiplier(odds, 1.0D, () -> 0.0D) == 1.0F,
                "a crit multiplier of one still added damage");
        float always = ElysiumCombatRules.critMultiplier(odds, 1.5D, () -> 0.0D);
        check(always > 1.0F, "a roll that always succeeds did not crit");
        double expectedCap = 1.0D + 0.5D / (1.0D - ElysiumCombatRules.CRIT_FALLOFF);
        check(always <= expectedCap + 1e-6D,
                "sixty-four crits gave " + always + ", past the falloff's own sum " + expectedCap);
        float single = ElysiumCombatRules.critMultiplier(odds, 1.5D, new java.util.function.DoubleSupplier() {
            private int call;

            @Override
            public double getAsDouble() {
                return call++ == 0 ? 0.0D : 0.999D;
            }
        });
        check(Math.abs(single - 1.5F) < 1e-6F, "one crit gave " + single + " instead of the crit multiplier");
        // Bigger stats buy more crits: the same rolls, a larger chance.
        java.util.Random random = new java.util.Random(7L);
        double small = 0.0D;
        double large = 0.0D;
        for (int i = 0; i < 20_000; i++) {
            small += ElysiumCombatRules.critMultiplier(ElysiumAttributeSpec.odds(0.2D), 1.5D, random::nextDouble);
            large += ElysiumCombatRules.critMultiplier(ElysiumAttributeSpec.odds(0.9D), 1.5D, random::nextDouble);
        }
        check(large > small * 1.2D, "a much larger crit stat barely hits harder: " + small + " vs " + large);
    }

    /** The auxiliary hits, and what they leave behind. */
    private static void auxiliary() {
        check(ElysiumCombatRules.auxiliary(5.0D, 1.0D) == 5.0F, "a full swing did not deal its auxiliary hit");
        check(ElysiumCombatRules.auxiliary(5.0D, 0.5D) == 0.0F, "half a swing still dealt an auxiliary hit");
        check(ElysiumCombatRules.auxiliary(5.0D, 0.8D) < 5.0F, "a partial swing dealt a full auxiliary hit");
        check(ElysiumCombatRules.auxiliary(0.0D, 1.0D) == 0.0F, "no stat still dealt a hit");
        check(ElysiumCombatRules.currentHpDamage(100.0D, 0.1D) == 10.0F, "current-HP damage is not a share of health");
        check(ElysiumCombatRules.currentHpDamage(0.0D, 0.1D) == 0.0F, "a dead target still bled");
        check(ElysiumCombatRules.fireTicks(4.0D) == 40, "fire ticks are not ten a point");
        check(ElysiumCombatRules.coldSlowTicks(4.0D) == 60, "cold's slow is not fifteen ticks a point");
        check(ElysiumCombatRules.coldSlowTicks(100.0D) == 1500,
                "cold's slow is capped again; the ceiling was removed on purpose");
        check(ElysiumCombatRules.coldAmplifier(5.0D) == 0, "five cold damage is already Slowness II");
        check(ElysiumCombatRules.coldAmplifier(10.0D) == 1, "ten cold damage did not reach Slowness II");
        check(ElysiumCombatRules.coldAmplifier(40.0D) == 3, "forty cold damage gave the wrong level");
        check(ElysiumCombatRules.coldAmplifier(0.0D) == 0, "no cold damage still slowed");
    }

    /** Lifesteal pays out, and overheal fills toward its share and stops. */
    private static void stealAndOverheal() {
        check(ElysiumCombatRules.lifeSteal(10.0D, 0.25D) == 2.5F, "lifesteal is not a share of what landed");
        check(ElysiumCombatRules.lifeSteal(-3.0D, 0.25D) == 0.0F, "a blow that did nothing still healed");
        double share = ElysiumShares.fromOdds(ElysiumAttributeSpec.odds(0.3D));
        float first = ElysiumCombatRules.overhealAbsorption(0.0D, 10.0D, 20.0D, share);
        check(first > 0.0F, "overheal granted no absorption");
        float full = first;
        for (int i = 0; i < 100; i++) {
            full = ElysiumCombatRules.overhealAbsorption(full, 10.0D, 20.0D, share);
        }
        check(full <= 20.0D * share + 1e-4D,
                "overheal filled past its share of max health: " + full);
        check(full > 20.0D * share - 1e-3D, "overheal never reached its ceiling");
        check(ElysiumCombatRules.overhealAbsorption(4.0D, 10.0D, 20.0D, 0.0D) == 4.0F,
                "no overheal stat still changed absorption");
    }

    /** Draw speed hands out the ticks it promises, and cooldowns shrink without vanishing. */
    private static void drawAndCooldown() {
        check(ElysiumCombatRules.drawExtraTicks(1.0D, 0) == 0, "an unmodified draw is faster than vanilla");
        check(ElysiumCombatRules.drawExtraTicks(0.5D, 0) == 0, "a slow draw handed out extra ticks");
        int over20 = 0;
        for (int tick = 0; tick < 20; tick++) {
            over20 += ElysiumCombatRules.drawExtraTicks(1.5D, tick);
        }
        check(over20 == 10, "draw speed 1.5 gave " + over20 + " extra ticks in twenty instead of ten");
        over20 = 0;
        for (int tick = 0; tick < 20; tick++) {
            over20 += ElysiumCombatRules.drawExtraTicks(3.0D, tick);
        }
        check(over20 == 40, "draw speed 3 gave " + over20 + " extra ticks in twenty instead of forty");
        check(ElysiumCombatRules.cooldownTicks(100, 0.0D) == 100, "a cooldown shrank with no reduction");
        check(ElysiumCombatRules.cooldownTicks(100, 0.5D) == 50, "half reduction did not halve the cooldown");
        check(ElysiumCombatRules.cooldownTicks(100, 1.0D) == 1, "a cooldown reached zero ticks");
        check(ElysiumCombatRules.cooldownTicks(0, 0.5D) == 0, "something with no cooldown was given one");
    }

    /** An effect's level is a stat like any other: it climbs and arrives nowhere. */
    private static void levels() {
        float one = ElysiumCombatRules.levelShare(1, 10.0D);
        float three = ElysiumCombatRules.levelShare(3, 10.0D);
        check(one > 0.0F && one < 1.0F && three > one && three < 1.0F,
                "an effect level does not behave like a share: " + one + ", " + three);
        check(ElysiumCombatRules.levelShare(1_000_000, 10.0D) < 1.0F, "an effect level reached certainty");
        check(ElysiumCombatRules.levelShare(0, 10.0D) == 0.0F, "no effect still did something");
    }

    public static void main(String[] args) {
        pierceAndShred();
        negativeArmour();
        vanillaShareForMobs();
        crits();
        auxiliary();
        stealAndOverheal();
        drawAndCooldown();
        levels();

        System.out.println("combat: pierce, shred, negative armour, repeating crits, the auxiliary hits, "
                + "lifesteal and overheal, draw speed and cooldowns — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("every percentage here is a share of an uncapped stat");
    }
}
