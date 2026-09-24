package com.elysium.lib.combat;

import com.elysium.lib.stats.ElysiumShares;

import java.util.function.DoubleSupplier;

/**
 * The combat attributes' arithmetic (Apothic Attributes' combat rules, AA-2 to
 * AA-8), with Elysium's rulings applied: no ceilings, and every percentage
 * through {@code stat / (stat + b)}.
 *
 * <p>What each original did and what changed:
 *
 * <ul>
 *   <li><b>Pierce and shred</b> (AA-2) stay as they were: shred is a share of
 *       the target's armour, pierce a flat amount, shred first, both blunted by
 *       the target's toughness. Toughness's blunting was capped at 60%; here it
 *       is a share of an uncapped stat.</li>
 *   <li><b>Negative armour</b> stays: armour driven below zero makes the blow
 *       bigger, 1.5% a point.</li>
 *   <li><b>Crits</b> (AA-3) still repeat — Apotheosis subtracted 100% from the
 *       chance for each extra crit, which needs a chance above 100%, and no
 *       Elysium chance ever reaches it. Instead the n-th crit rolls the same
 *       odds divided by n, so a bigger crit stat still buys more crits, each
 *       rarer than the last, and each 15% weaker as it was there.</li>
 *   <li><b>Cold's slow</b> (AA-4) is no longer capped at 150 ticks.</li>
 *   <li><b>Overheal</b> (AA-5) filled absorption to half of max health; here
 *       the ceiling is the overheal share of max health, which climbs with the
 *       stat and never reaches all of it.</li>
 * </ul>
 *
 * <p>Imports nothing Minecraft: it is in the validator's EXECUTED_PURE list and
 * runs in tools/CombatHarness.java without a game.
 */
public final class ElysiumCombatRules {

    private ElysiumCombatRules() {
    }

    // ------------------------------------------------------------------
    // Armour: pierce, shred and negative armour (AA-2)
    // ------------------------------------------------------------------

    /** Apotheosis's 2% a point of toughness, as an uncapped share at the mod's pace. */
    public static final double TOUGHNESS_POINTS_FOR_ALL = 50.0D;

    /** Apotheosis's Negative Armor Factor: each point below zero adds 1.5% to the blow. */
    public static final double NEGATIVE_ARMOUR_PER_POINT = 0.015D;

    /** How much of an attacker's pierce and shred the target's toughness turns aside. */
    public static float bypassResistance(double toughness) {
        return ElysiumShares.share(toughness * ElysiumShares.PACE, TOUGHNESS_POINTS_FOR_ALL);
    }

    /**
     * Armour after the attacker's shred (a share) and pierce (flat), each
     * blunted by the target's toughness. Shred first, as in Apotheosis. May
     * return a negative number, which {@link #negativeArmourFactor} then reads.
     */
    public static double piercedArmour(double armour, double shred, double pierce, double toughness) {
        double bypass = 1.0D - bypassResistance(toughness);
        double shredded = shred > 0.001D ? armour * (1.0D - shred * bypass) : armour;
        double pierced = pierce > 0.001D ? shredded - pierce * bypass : shredded;
        return pierced;
    }

    /** Protection points after prot shred then prot pierce; never below zero. */
    public static double piercedProtection(double points, double shred, double pierce) {
        double shredded = shred > 0.001D ? points * (1.0D - shred) : points;
        return Math.max(0.0D, shredded - Math.max(0.0D, pierce));
    }

    /** What a blow is multiplied by when armour has been driven below zero. */
    public static float negativeArmourFactor(double armour) {
        return armour >= 0.0D ? 1.0F : (float) (1.0D + Math.abs(armour) * NEGATIVE_ARMOUR_PER_POINT);
    }

    /**
     * Vanilla's own armour share, for defenders the mod leaves alone (anything
     * but a player). Vanilla: {@code clamp(effective, 0, 20) / 25} removed.
     */
    public static float vanillaArmourShare(double effectiveArmour) {
        double points = Math.max(0.0D, Math.min(20.0D, effectiveArmour));
        return (float) (points / 25.0D);
    }

    // ------------------------------------------------------------------
    // Crits (AA-3)
    // ------------------------------------------------------------------

    /** Each crit after the first hits 15% softer, as in Apotheosis. */
    public static final double CRIT_FALLOFF = 0.85D;

    /** No blow rolls more crits than this; a guard on the arithmetic, not a balance cap. */
    public static final int MAX_CRITS = 64;

    /**
     * The multiplier a blow gets from critting, rolling as many crits as the
     * stat buys. The n-th roll is against {@code odds / n}, so the chance of
     * another crit always falls but never reaches zero, and the extra damage of
     * each is 15% less than the last.
     *
     * @param odds       the crit chance as odds (what the attribute stores)
     * @param critDamage the crit multiplier, 1.5 by default; 1 or less never crits
     * @param rolls      a source of numbers in [0, 1)
     */
    public static float critMultiplier(double odds, double critDamage, DoubleSupplier rolls) {
        if (!(odds > 0.0D) || !(critDamage > 1.0D)) {
            return 1.0F;
        }
        double extra = 0.0D;
        double bonus = critDamage - 1.0D;
        for (int n = 1; n <= MAX_CRITS; n++) {
            float chance = ElysiumShares.fromOdds(odds / n);
            if (rolls.getAsDouble() >= chance) {
                break;
            }
            extra += bonus * Math.pow(CRIT_FALLOFF, n - 1);
        }
        return (float) (1.0D + extra);
    }

    // ------------------------------------------------------------------
    // Auxiliary melee damage (AA-4)
    // ------------------------------------------------------------------

    /** Below this much of a full swing, the auxiliary hits do not land at all. */
    public static final double SWING_THRESHOLD = 0.55D;

    /** An auxiliary hit smaller than this is not dealt. */
    public static final double MIN_AUX_DAMAGE = 0.001D;

    /** An auxiliary hit, scaled by how full the swing was; zero if the swing was too weak. */
    public static float auxiliary(double amount, double swing) {
        if (!(amount > 0.0D) || swing < SWING_THRESHOLD) {
            return 0.0F;
        }
        double scaled = amount * swing;
        return scaled < MIN_AUX_DAMAGE ? 0.0F : (float) scaled;
    }

    /** Current-HP damage: a share of what the target has left. */
    public static float currentHpDamage(double health, double share) {
        return share > 0.0D && health > 0.0D ? (float) (health * share) : 0.0F;
    }

    /** Fire ticks a fire hit leaves: ten a point, as in Apotheosis. */
    public static int fireTicks(double dealt) {
        return dealt > 0.0D ? (int) Math.round(dealt * 10.0D) : 0;
    }

    /** Ticks of Slowness a cold hit leaves. Apotheosis capped this at 150; the cap is gone. */
    public static int coldSlowTicks(double dealt) {
        return dealt > 0.0D ? (int) Math.round(dealt * 15.0D) : 0;
    }

    /** The Slowness level a cold hit leaves: log2 of the damage in fives. */
    public static int coldAmplifier(double dealt) {
        long fives = Math.round(dealt / 5.0D);
        if (fives <= 1L) {
            return 0;
        }
        return Math.max(0, (int) (Math.log(fives) / Math.log(2.0D)));
    }

    // ------------------------------------------------------------------
    // Life steal and overheal (AA-5)
    // ------------------------------------------------------------------

    /** What a hit heals the attacker for: a share of what landed. */
    public static float lifeSteal(double landed, double share) {
        return landed > 0.0D && share > 0.0D ? (float) (landed * share) : 0.0F;
    }

    /**
     * Absorption after overheal. Apotheosis filled to half of max health; here
     * the ceiling is the overheal share of max health, so a bigger stat holds
     * more and nothing ever reaches a full second health bar.
     */
    public static float overhealAbsorption(double absorption, double landed, double maxHealth, double share) {
        if (!(share > 0.0D) || !(landed > 0.0D)) {
            return (float) Math.max(0.0D, absorption);
        }
        double ceiling = maxHealth * share;
        return (float) Math.min(ceiling, Math.max(0.0D, absorption) + landed * share);
    }

    // ------------------------------------------------------------------
    // Ranged and utility (AA-7, AA-8)
    // ------------------------------------------------------------------

    /**
     * Extra use ticks a draw-speed stat adds this tick: whole points always,
     * and the fraction on one tick in {@code round(1 / fraction)}.
     */
    public static int drawExtraTicks(double drawSpeed, int tick) {
        if (!(drawSpeed > 1.0D)) {
            return 0;
        }
        int whole = (int) Math.floor(drawSpeed - 1.0D);
        double fraction = drawSpeed - 1.0D - whole;
        if (fraction > 0.0D) {
            int every = Math.max(1, (int) Math.round(1.0D / fraction));
            if (tick % every == 0) {
                whole++;
            }
        }
        return whole;
    }

    /** A cooldown after reduction; never below one tick while the cooldown exists. */
    public static int cooldownTicks(int base, double reduction) {
        if (base <= 0) {
            return 0;
        }
        double left = base * (1.0D - Math.max(0.0D, Math.min(0.999D, reduction)));
        return Math.max(1, (int) Math.round(left));
    }

    /** What an effect level removes or adds as a share: the level is the stat. */
    public static float levelShare(int level, double pointsForAll) {
        return ElysiumShares.share(level * ElysiumShares.PACE, pointsForAll);
    }
}
