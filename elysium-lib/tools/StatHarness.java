package com.elysium.lib.stats;

import com.elysium.lib.character.ElysiumCharacter;

/**
 * The stat curves and the twelve effects, executed outside a world.
 *
 * <h2>Why this exists now and did not before</h2>
 *
 * At two free points a level, the multipliers below were small numbers applied
 * to small totals and the worst a typo could do was make a stat dull. At ten a
 * level a character at level fifty has assigned five hundred points by hand,
 * and the same multipliers are the difference between a character who is strong
 * and one who cannot be hurt. A misplaced decimal is no longer a balance
 * problem; it is the end of the game's difficulty, and it would be invisible
 * until somebody played for a week.
 *
 * So the claims the design makes about scaling are written here as arithmetic:
 *
 * <ul>
 *   <li>every proportional stat rises forever and arrives nowhere — no amount
 *       of investment reaches its ceiling, and no ceiling is 1.0;</li>
 *   <li>the halfway point means what it says;</li>
 *   <li>Fortitude keeps paying after vanilla's armour ceiling, and stops when
 *       vanilla's toughness ceiling is reached;</li>
 *   <li>a level is worth ten points, and five hundred points is a large
 *       character rather than an invulnerable one.</li>
 * </ul>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/sh elysium-lib/tools/StatHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/sh" \
 *         com.elysium.lib.stats.StatHarness
 */
public final class StatHarness {

    private static int failures = 0;
    private static int checked = 0;

    /**
     * The point total a character reaches by the time the game is long over.
     *
     * Level fifty at ten a level, plus the growth race and class hand out. Used
     * as the yardstick everywhere below, because "is this stat balanced" is
     * meaningless without a number of points to ask it at.
     */
    private static final int LATE_GAME = 50 * ElysiumCharacter.POINTS_PER_LEVEL;

    /** And the number somebody with nothing better to do will reach. */
    private static final int ABSURD = 100_000;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static String pct(float share) {
        return String.format("%.2f%%", share * 100.0F);
    }

    /** A level has to be worth noticing, and points are never capped. */
    private static void aLevelIsAnEvent() {
        check(ElysiumCharacter.POINTS_PER_LEVEL >= 5,
                "a level grants " + ElysiumCharacter.POINTS_PER_LEVEL
                        + " point(s), which is small enough that the level-up "
                        + "message is the only part of it a player notices");
        check(ElysiumCharacter.POINTS_PER_LEVEL <= 25,
                "a level grants " + ElysiumCharacter.POINTS_PER_LEVEL
                        + " points, enough that a single level rewrites a "
                        + "character and choosing where they go stops mattering");
        System.out.println("  levels: " + ElysiumCharacter.POINTS_PER_LEVEL
                + " points each, " + LATE_GAME + " assigned by hand at level 50");
    }

    /**
     * Levels arrive steadily.
     *
     * The owner asked for a curve closer to linear. "Linear" is a claim about
     * two numbers — how much the cost of a level grows over a whole game, and
     * how much a whole game costs — and both are measurable here without any
     * of the game around them.
     */
    private static void levelsArriveSteadily() {
        int first = com.elysium.lib.character.ElysiumLevelling.xpToNext(1);
        int fiftieth = com.elysium.lib.character.ElysiumLevelling.xpToNext(50);
        check(first > 0, "the first level costs nothing");

        int previous = 0;
        long cumulative = 0;
        for (int level = 1; level <= 50; level++) {
            int cost = com.elysium.lib.character.ElysiumLevelling.xpToNext(level);
            check(cost >= previous, "level " + level + " costs less than the one before");
            previous = cost;
            cumulative += cost;
        }

        float growth = (float) fiftieth / (float) first;
        check(growth <= 8.0F,
                "level 50 costs " + growth + "x level 1, which is the quadratic "
                        + "climb the owner asked to flatten — a level in the "
                        + "late game should be a little dearer, not an order of "
                        + "magnitude");
        check(growth >= 1.5F,
                "level 50 costs " + growth + "x level 1, so the last levels "
                        + "arrive as fast as the first and levelling stops being "
                        + "a climb at all");

        check(cumulative >= 5_000L,
                "level 50 is reached for " + cumulative + " experience in total, "
                        + "which a single evening of fighting pays for");
        check(cumulative <= 60_000L,
                "level 50 costs " + cumulative + " experience in total, which is "
                        + "the curve this replaced");

        System.out.println("  levelling: " + first + " for the first level, "
                + fiftieth + " for the fiftieth (" + String.format("%.1f", growth)
                + "x), " + cumulative + " in all");
    }

    /**
     * The curve rises forever and arrives nowhere.
     *
     * This is the property that makes an uncapped point total safe. If it ever
     * stopped holding, "no limit to skill points" would become "no limit to
     * dodge chance", and the answer would have to be a cap — which is the thing
     * the curve exists so that nobody has to write.
     */
    private static void curvesNeverArrive() {
        for (ElysiumStat stat : ElysiumStat.REGISTRY.all()) {
            if (stat.getShape() != ElysiumStat.Shape.CURVE) {
                continue;
            }
            float ceiling = stat.getCeiling();

            // The owner's ruling: "uncap everything, but give percentile
            // effects stat/stat+b scaling". So every Elysium curve stat has no
            // ceiling below 1 — the stat's share is value / (value + halfway)
            // and nothing more. What keeps it from arriving is the formula,
            // checked just below at every point total up to Integer.MAX_VALUE.
            if (stat.getId() != null && stat.getId().getNamespace().equals("elysiumlib")) {
                check(ceiling == ElysiumStats.UNCAPPED,
                        stat + " has a ceiling of " + pct(ceiling) + ". Elysium's "
                                + "percentage stats are not capped; the owner ruled "
                                + "stat / (stat + b) instead");
            }

            // Monotonic, and never at the ceiling however absurd the total.
            float previous = -1.0F;
            for (int points : new int[] {0, 1, 10, 50, 100, LATE_GAME, 10_000, ABSURD,
                    Integer.MAX_VALUE}) {
                float value = stat.proportionOf(points);
                check(value >= previous,
                        stat + " fell from " + pct(previous) + " to " + pct(value)
                                + " at " + points + " points");
                check(value < ceiling,
                        stat + " reached its ceiling of " + pct(ceiling) + " at "
                                + points + " points");
                previous = value;
            }

            // The halfway point has to be the halfway point. Checked against
            // the curve rather than against a point total: since the square
            // term arrived, the value reaching the curve is not the number that
            // was passed in, and proportionOf(halfway) has not meant "half the
            // ceiling" since.
            float atHalfway = stat.curveOf(stat.getHalfway());
            check(Math.abs(atHalfway - ceiling / 2.0F) < 0.01F,
                    stat + " reaches " + pct(atHalfway) + " at its stated "
                            + "halfway point of " + stat.getHalfway()
                            + ", not half of " + pct(ceiling));

            // The soft cap has to be high — that is the whole design — but a
            // ceiling nobody approaches is a stat with no top half. These two
            // bracket it: worth building by level 50, and not nearly finished
            // until far beyond it.
            float late = stat.proportionOf(LATE_GAME) / ceiling;
            check(late >= 0.20F,
                    stat + " reaches only " + pct(late) + " of its own ceiling "
                            + "with every point a level-50 character has, so the "
                            + "stat is not worth building toward");
            check(late < 0.95F,
                    stat + " is already at " + pct(late) + " of its ceiling by "
                            + "level 50, which leaves nothing to climb after it. The "
                            + "owner wants hard scaling, so a level-50 build may be "
                            + "high — but not finished");

            float far = stat.proportionOf(LATE_GAME * 10) / ceiling;
            check(far >= 0.80F,
                    stat + " is still at " + pct(far) + " of its ceiling at ten "
                            + "times a level-50 investment, so the ceiling is a "
                            + "number nothing in the game ever approaches");
        }

        StringBuilder line = new StringBuilder("  at " + LATE_GAME + " points: ");
        for (ElysiumStat stat : ElysiumStat.REGISTRY.all()) {
            if (stat.getShape() == ElysiumStat.Shape.CURVE) {
                line.append(stat.getId().getPath()).append(' ')
                    .append(pct(stat.proportionOf(LATE_GAME))).append("  ");
            }
        }
        System.out.println(line.toString().trim());
    }

    /**
     * The two-term scaling: linear, plus a square that takes over late.
     *
     * <h3>What is actually being claimed</h3>
     *
     * That a point is worth at least what it was worth before (never a
     * penalty), that the curve accelerates rather than merely rising, and that
     * the acceleration arrives <em>late</em> — the divisor is the handover
     * point, and if it drifted downward the early game would quietly turn into
     * the late one.
     */
    private static void scalingIsLinearThenSquare() {
        check(ElysiumStat.effective(0) == 0.0F, "no points produced a value");
        check(ElysiumStat.effective(-40) == 0.0F,
                "a negative stat produced a negative value instead of nothing");

        float previous = -1.0F;
        for (int points = 0; points <= 5000; points += 7) {
            float value = ElysiumStat.effective(points);
            check(value >= previous, "the scaling fell at " + points + " points");
            check(value >= points,
                    "a stat of " + points + " is worth " + value + " — less than "
                            + "itself, so the scaling is a penalty");
            previous = value;
        }

        // Superlinear: doubling the points is worth more than doubling.
        for (int points : new int[] {10, 100, LATE_GAME, 5000}) {
            check(ElysiumStat.effective(points * 2)
                            > ElysiumStat.effective(points) * 2.0F,
                    "doubling " + points + " points is worth exactly double, so "
                            + "the square term is doing nothing and levelling has "
                            + "the same slope it had in the first hour");
        }

        // The divisor is the handover: at exactly that many points, the square
        // term equals the linear one. This is what "raise it to delay the
        // takeover" means, and it is only true while the formula is that shape.
        int divisor = Math.round(ElysiumStat.SCALING_DIVISOR);
        float atDivisor = ElysiumStat.effective(divisor);
        check(Math.abs(atDivisor - divisor * 2.0F) < divisor * 0.01F,
                "at the divisor (" + divisor + " points) the value is " + atDivisor
                        + " rather than double — the divisor is no longer the "
                        + "point where the square term takes over, so tuning it "
                        + "does not move the takeover");

        // ...and the takeover must be beyond where an ordinary game ends, or
        // "delayed" is not what it is.
        check(ElysiumStat.SCALING_DIVISOR > LATE_GAME * 0.5F,
                "the square term overtakes the linear one at "
                        + (int) ElysiumStat.SCALING_DIVISOR + " points, well "
                        + "inside a level-50 character's budget — the exponential "
                        + "is the whole game rather than its far end");

        System.out.println("  scaling: " + LATE_GAME + " points are worth "
                + Math.round(ElysiumStat.effective(LATE_GAME)) + ", 1000 are worth "
                + Math.round(ElysiumStat.effective(1000)) + ", 2000 are worth "
                + Math.round(ElysiumStat.effective(2000))
                + "; the square overtakes the linear at " + divisor);
    }

    /**
     * Resilience is the one that has to be held below immunity by hand.
     *
     * Every other curve reduces something a player can survive without. This
     * one reduces damage, so its ceiling is the game's floor on how hard
     * anything can ever hit a fully invested character.
     */
    private static void damageStaysPossible() {
        // Resilience has no ceiling now (the owner's ruling). Damage stays
        // possible because stat / (stat + b) never reaches 1 — asked of every
        // point total up to the largest an int can hold, where a float would
        // otherwise round to exactly 100% and a hit would do nothing.
        float ceiling = ElysiumStats.RESILIENCE.getCeiling();
        check(ceiling == ElysiumStats.UNCAPPED,
                "Resilience is capped at " + pct(ceiling) + "; the owner ruled no caps");
        for (int points : new int[] {1, 1_000, 1_000_000, Integer.MAX_VALUE}) {
            float removed = ElysiumStats.RESILIENCE.proportionOf(points);
            check(removed < 1.0F,
                    "Resilience at " + points + " points removes all damage — a "
                            + "finite stat arrived at 100%, which the formula "
                            + "never does");
        }

        // The owner's ruling, "we want the player to scale really hard", held
        // as a number: a level-50 character all in on a percentage stat is
        // well past halfway. At the old pace this was 41%.
        check(ElysiumStats.RESILIENCE.proportionOf(LATE_GAME) >= 0.70F,
                "a level-50 character all in on Resilience removes only "
                        + pct(ElysiumStats.RESILIENCE.proportionOf(LATE_GAME))
                        + "; the owner asked for hard scaling (at least 70%)");

        // Pacing, not a cap: a level-50 character with every point in
        // Resilience still takes a real share of every blow.
        float late = ElysiumStats.RESILIENCE.proportionOf(LATE_GAME);
        check(late < 0.85F,
                "a character at level 50 with every point in Resilience removes "
                        + pct(late) + " of all damage");
        System.out.println("  resilience: " + pct(late) + " at " + LATE_GAME
                + " points, climbing without a ceiling and never reaching 100%");
    }

    /** Fortitude must keep paying after vanilla stops counting armour. */
    private static void fortitudeOutlivesVanillasCeiling() {
        check(ElysiumStats.armourFor(ElysiumStats.ARMOUR_FULL)
                        >= ElysiumStats.VANILLA_ARMOUR_CEILING,
                "ARMOUR_FULL is " + ElysiumStats.ARMOUR_FULL + " Fortitude, which "
                        + "grants " + ElysiumStats.armourFor(ElysiumStats.ARMOUR_FULL)
                        + " armour — short of vanilla's ceiling, so the handover "
                        + "to toughness happens while armour is still paying");

        // Checked below the handover as well as at it. At it alone, the
        // subtraction zeroes itself — deleting the guard entirely still gave
        // exactly 0 there, so a deliberate break walked straight through the
        // assertion. The interesting failure is a point before the handover.
        for (int points : new int[] {1, 10, ElysiumStats.ARMOUR_FULL / 2,
                                     ElysiumStats.ARMOUR_FULL - 1,
                                     ElysiumStats.ARMOUR_FULL}) {
            check(ElysiumStats.toughnessFor(points) == 0.0D,
                    "a Fortitude of " + points + " grants "
                            + ElysiumStats.toughnessFor(points) + " toughness, "
                            + "before armour has reached vanilla's ceiling — so "
                            + "a low-Fortitude character is handed a stat they "
                            + "have not earned, or a negative one");
        }
        check(ElysiumStats.toughnessFor(ElysiumStats.ARMOUR_FULL + 1) > 0.0D,
                "toughness never starts, so every point of Fortitude past "
                        + ElysiumStats.ARMOUR_FULL + " buys nothing at all");
        // Uncapped: Fortitude keeps buying toughness past vanilla's 20.
        check(ElysiumStats.toughnessFor(ABSURD) > ElysiumStats.VANILLA_TOUGHNESS_CEILING,
                "toughness stops at vanilla's " + ElysiumStats.VANILLA_TOUGHNESS_CEILING
                        + "; the owner ruled no ceilings");

        check(ElysiumStats.toughnessFor(0) == 0.0D, "no Fortitude grants toughness");
        check(ElysiumStats.armourFor(0) == 0.0D, "no Fortitude grants armour");
        check(ElysiumStats.armourFor(-50) == 0.0D,
                "a negative Fortitude grants negative armour");

        // Searched for rather than divided out. The arithmetic version of this
        // line printed 126 and the real answer was 118: it divided the ceiling
        // by the per-point rate, which stopped being how toughness accumulates
        // the moment the square term arrived. A reported number that is
        // computed a second way is a number that will eventually disagree with
        // the code it claims to describe.
        int toughnessFull = ElysiumStats.ARMOUR_FULL;
        while (toughnessFull < 100_000
                && ElysiumStats.toughnessFor(toughnessFull)
                        < ElysiumStats.VANILLA_TOUGHNESS_CEILING) {
            toughnessFull++;
        }
        check(ElysiumStats.toughnessFor(toughnessFull)
                        >= ElysiumStats.VANILLA_TOUGHNESS_CEILING,
                "toughness never fills, so its ceiling is unreachable");

        System.out.println("  fortitude: armour to vanilla's "
                + (int) ElysiumStats.VANILLA_ARMOUR_CEILING + " by "
                + ElysiumStats.ARMOUR_FULL + " points, then toughness to "
                + (int) ElysiumStats.VANILLA_TOUGHNESS_CEILING + " by "
                + toughnessFull);
    }

    /**
     * The flat effects, at a total somebody will actually reach.
     *
     * Not a balance opinion — a decimal-point check. Every bound here is an
     * order of magnitude away from the intended value, so it fails on a typo
     * and passes on a deliberate change.
     */
    private static void flatEffectsStayInTheirOrderOfMagnitude() {
        double health = ElysiumStats.healthFor(LATE_GAME);
        check(health >= 20.0D,
                "every point in Vitality at level 50 adds " + health
                        + " maximum health, which is less than the player starts "
                        + "with — the stat is not worth a build");
        check(health <= 2000.0D,
                "every point in Vitality at level 50 adds " + health
                        + " maximum health; the health bar stops being a thing "
                        + "anything can empty");

        float regen = ElysiumStats.regenFor(LATE_GAME);
        check(regen > ElysiumStats.REGEN_BASE,
                "Vitality does not raise regeneration at all");
        check(regen * 1.5F < health,
                "a Vitality character regenerates " + regen + " per tick against "
                        + health + " bonus health — they refill from empty in "
                        + "under a second and damage stops mattering");

        float damage = ElysiumStats.damageFor(LATE_GAME);
        check(damage >= 20.0F,
                "every point in Strength at level 50 is worth " + damage
                        + " base damage, less than a diamond sword");
        check(damage <= 2000.0F,
                "every point in Strength at level 50 is worth " + damage
                        + " base damage, which is every mob in the game in one "
                        + "swing including the ones built to take several");

        float shield = ElysiumStats.shieldFor(LATE_GAME);
        check(shield > 0.0F, "Willpower grants no shield");
        check(shield <= health * 4.0D,
                "Willpower's shield at level 50 is " + shield + " against "
                        + health + " health from the same investment, so the "
                        + "shield is the health bar and the health bar is "
                        + "decoration");

        System.out.println("  at " + LATE_GAME + " points: +" + (int) health
                + " health, " + String.format("%.1f", regen)
                + " regen/tick, +" + (int) damage + " damage, "
                + (int) shield + " shield");
    }

    /**
     * The one rule for every percentage: stat / (stat + b). Asked directly,
     * because every percentage in the mod now goes through it and a mistake
     * here is a mistake everywhere at once.
     */
    private static void sharesRule() {
        float[] samples = {0.01F, 0.05F, 0.2F, 0.25F, 0.5F, 0.6F, 0.9F, 0.99F};
        for (float p : samples) {
            check(Math.abs(ElysiumShares.add(p) - p) < 1e-6,
                    "one source of " + p + " alone became " + ElysiumShares.add(p)
                            + "; a single source must be unchanged");
            check(Math.abs(ElysiumShares.scaled(p, 1.0D) - p) < 1e-6,
                    "one times the stat behind " + p + " is not " + p);
            for (int n = 2; n <= 64; n++) {
                double wanted = ElysiumShares.odds(p) * n;
                double got = ElysiumShares.odds(ElysiumShares.scaled(p, n));
                check(Math.abs(got - wanted) <= 1e-3 * wanted,
                        n + " times the stat behind " + p + " is " + got
                                + ", not " + wanted);
                check(ElysiumShares.scaled(p, n) < 1.0F,
                        n + " times the stat behind " + p + " reached 100%");
                check(ElysiumShares.scaled(p, n) >= ElysiumShares.scaled(p, n - 1),
                        "more stat behind " + p + " gave a smaller share");
            }
        }
        // Adding shares is adding the stats behind them.
        check(Math.abs(ElysiumShares.add(0.2F, 0.5F)
                        - ElysiumShares.fromOdds(0.25D + 1.0D)) < 1e-6,
                "20% and 50% did not add as stats (0.25 + 1 = 1.25 -> 55.6%)");
        check(Math.abs(ElysiumShares.add(0.1F, 0.6F, 0.3F)
                        - ElysiumShares.add(0.3F, 0.1F, 0.6F)) < 1e-6,
                "the order of sources changed the total");
        // Finite never arrives — including where a float would round to it.
        for (double stat : new double[] {1.0, 1e6, 1e9, 1e15, 1e30, Double.MAX_VALUE}) {
            check(ElysiumShares.share(stat, 1.0D) < 1.0F,
                    "a finite stat of " + stat + " reached 100%");
        }
        check(ElysiumShares.share(Double.POSITIVE_INFINITY, 1.0D) == 1.0F,
                "an infinite stat is not certain");
        check(ElysiumShares.share(Double.NaN, 1.0D) == 0.0F
                        && ElysiumShares.share(-5.0D, 1.0D) == 0.0F
                        && ElysiumShares.add(Float.NaN, -1.0F) == 0.0F,
                "nonsense counted as something");
        check(ElysiumShares.sane(3.0F) == 1.0F && ElysiumShares.sane(-1.0F) == 0.0F,
                "sane() is not a type check to [0, 1]");
        System.out.println("  shares: 20% + 20% + 20% + 20% = "
                + pct(ElysiumShares.add(0.2F, 0.2F, 0.2F, 0.2F))
                + "; a tier-10 5% rune is " + pct(ElysiumShares.scaled(0.05F, 10))
                + "; nothing finite reaches 100%");
    }

    /** Armour and Protection: vanilla's mechanics, none of its ceilings. */
    private static void armourRules() {
        // What vanilla gives: min(effective, 20) / 25 — the 80% ceiling.
        double lastShare = -1.0D;
        for (double armour = 0.0D; armour <= 2000.0D; armour += 0.5D) {
            double eff = ElysiumArmourRules.effectiveArmour(armour, 0.0D, 1.0D);
            float share = ElysiumArmourRules.armourShare(eff);
            check(share < 1.0F, armour + " armour removed all damage");
            check(share >= lastShare, "more armour removed less at " + armour);
            lastShare = share;
            // Keyed on the armour, not on the effective value under test: if
            // something capped effective armour, a check gated on it would
            // simply stop running — which is how a re-added cap of 20 once
            // went unnoticed here. Against a 1-damage hit, 30 armour or more
            // must beat vanilla's ceiling of 80%.
            if (armour >= 30.0D) {
                check(share > 0.8F, armour + " armour removes " + pct(share)
                        + ", no better than vanilla's capped 80%");
            }
        }
        check(ElysiumArmourRules.armourShare(
                        ElysiumArmourRules.effectiveArmour(200.0D, 20.0D, 10.0D)) > 0.95F,
                "200 armour removes no more than 95% — something caps armour");
        // The mechanics kept: the one-fifth floor, and toughness blunting big hits.
        check(Math.abs(ElysiumArmourRules.effectiveArmour(10.0D, 0.0D, 1.0E9D) - 2.0D) < 1e-9,
                "a huge blow did not leave armour at its one-fifth floor");
        check(ElysiumArmourRules.effectiveArmour(20.0D, 12.0D, 30.0D)
                        > ElysiumArmourRules.effectiveArmour(20.0D, 0.0D, 30.0D),
                "toughness did not help against a big hit");
        check(ElysiumArmourRules.effectiveArmour(0.0D, 50.0D, 5.0D) == 0.0D
                        && ElysiumArmourRules.effectiveArmour(Double.NaN, 0.0D, 5.0D) == 0.0D,
                "no armour counted as some");
        // Protection: vanilla stops at 20 points, 80%.
        check(ElysiumArmourRules.protectionShare(40.0D) > 0.8F
                        && ElysiumArmourRules.protectionShare(1.0E9D) < 1.0F,
                "Protection is capped at vanilla's 80%, or reaches 100%");
        // The uncapped attribute read must equal vanilla's formula below the ceiling.
        check(Math.abs(ElysiumArmourRules.attributeValue(10.0D, new double[] {2.0D, 3.0D},
                        new double[] {0.5D}, new double[] {0.1D, 0.2D}) - (15.0D * 1.5D * 1.1D * 1.2D)) < 1e-9,
                "the uncapped attribute read does not match vanilla's formula");
        check(ElysiumArmourRules.attributeValue(30.0D, new double[] {50.0D},
                        new double[0], new double[0]) == 80.0D,
                "the uncapped attribute read stops at a ceiling");
        System.out.println("  armour: 20 -> " + pct(ElysiumArmourRules.armourShare(20.0D))
                + ", 30 -> " + pct(ElysiumArmourRules.armourShare(30.0D))
                + ", 100 -> " + pct(ElysiumArmourRules.armourShare(100.0D))
                + " (vanilla stops at 80%)");
    }

    /** Stacking proportional sources still cannot reach immunity. */
    private static void stackingStillArrivesNowhere() {
        float total = 0.0F;
        for (int source = 0; source < 40; source++) {
            float next = ElysiumStats.combine(total, 0.5F);
            check(next >= total, "combining a source lowered the total");
            check(next < 1.0F, source + " sources of a half reached 1.0");
            total = next;
        }
        // A source that already says 100% is taken at its word now — nothing
        // clamps. What must hold is that sources short of it never combine
        // into it, however close they each are.
        check(ElysiumStats.combine(0.999F, 0.999F) < 1.0F,
                "two sources short of total combined into total immunity");
        System.out.println("  stacking: 40 sources of a half reach "
                + pct(total) + ", and never 100%");
    }

    public static void main(String[] args) {
        ElysiumStats.bootstrap();
        aLevelIsAnEvent();
        levelsArriveSteadily();
        scalingIsLinearThenSquare();
        curvesNeverArrive();
        damageStaysPossible();
        fortitudeOutlivesVanillasCeiling();
        flatEffectsStayInTheirOrderOfMagnitude();
        stackingStillArrivesNowhere();
        sharesRule();
        armourRules();

        System.out.println("stats: the level grant, every curve's ceiling and "
                + "halfway, Fortitude's handover and the flat effects at "
                + LATE_GAME + " points — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("points are uncapped because nothing they buy "
                + "can arrive anywhere");
    }
}
