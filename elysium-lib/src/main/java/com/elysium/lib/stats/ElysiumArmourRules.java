package com.elysium.lib.stats;

/**
 * Armour, toughness and Protection, without vanilla's ceilings.
 *
 * <h2>What vanilla does, and where it stops</h2>
 *
 * {@code CombatRules}: effective armour is {@code armour - damage / (2 +
 * toughness / 4)}, never below a fifth of the armour; it is then capped at 20
 * and divided by 25 — so no amount of armour removes more than 80% of a blow.
 * Protection is the same shape: 4% a point, capped at 20 points, 80%. And the
 * armour attribute itself stops at 30, toughness at 20.
 *
 * <h2>What Elysium does</h2>
 *
 * The owner's rulings: "uncap everything, but give percentile effects
 * stat/stat+b scaling", and "we want the player to scale really hard". So the
 * parts of vanilla that are mechanics stay — toughness blunting armour against
 * big hits, the one-fifth floor, Breach and its kin piercing the result — and
 * the parts that are ceilings go. Effective armour is a stat, uncapped; what it
 * removes is stat / (stat + b), with vanilla's own 4%-a-point as the b, at the
 * mod's pace (ElysiumShares.PACE). Protection the same.
 *
 * Applied to players only. The ruling is about how hard the <em>player</em>
 * scales; a zombie in iron armour stays exactly as tough as vanilla made it.
 *
 * <p>Imports nothing Minecraft, and must not: it is in {@code EXECUTED_PURE}.
 */
public final class ElysiumArmourRules {

    private ElysiumArmourRules() {
    }

    /** Vanilla's divisor: 25 points of anything is "all of it" at 4% a point. The b, before pace. */
    public static final double VANILLA_POINTS_FOR_ALL = 25.0D;

    /** Vanilla's floor: armour never counts for less than a fifth of itself, however big the hit. */
    public static final double MIN_ARMOUR_RATIO = 0.2D;

    /** Vanilla's base in the toughness term: damage / (2 + toughness / 4). */
    public static final double BASE_TOUGHNESS = 2.0D;

    /**
     * Armour as it stands against a blow of this size: vanilla's toughness
     * mechanic and its floor, with no ceiling.
     */
    public static double effectiveArmour(double armour, double toughness, double damage) {
        if (!(armour > 0.0D)) {
            return 0.0D;
        }
        double t = toughness > 0.0D ? toughness : 0.0D;
        double d = damage > 0.0D ? damage : 0.0D;
        double against = armour - d / (BASE_TOUGHNESS + t / 4.0D);
        return Math.max(armour * MIN_ARMOUR_RATIO, against);
    }

    /** What effective armour removes. stat / (stat + b), never 100%. */
    public static float armourShare(double effectiveArmour) {
        return ElysiumShares.share(effectiveArmour * ElysiumShares.PACE, VANILLA_POINTS_FOR_ALL);
    }

    /** What Protection points (vanilla's "EPF") remove. The same rule. */
    public static float protectionShare(double protectionPoints) {
        return ElysiumShares.share(protectionPoints * ElysiumShares.PACE, VANILLA_POINTS_FOR_ALL);
    }

    /**
     * An attribute's value from its parts, the way vanilla's AttributeInstance
     * computes it, but without the final clamp to the attribute's range:
     *
     * <pre>
     *   v  = base + sum(ADD_VALUE)
     *   v' = v + v * sum(ADD_MULTIPLIED_BASE)
     *   v' = v' * product(1 + ADD_MULTIPLIED_TOTAL)
     * </pre>
     *
     * So armour and toughness count past 30 and 20 even where the attribute's
     * ceiling could not be raised.
     */
    public static double attributeValue(double base, double[] add, double[] multipliedBase,
                                        double[] multipliedTotal) {
        double value = base;
        for (double a : add) {
            value += a;
        }
        double result = value;
        for (double m : multipliedBase) {
            result += value * m;
        }
        for (double m : multipliedTotal) {
            result *= 1.0D + m;
        }
        return result;
    }
}
