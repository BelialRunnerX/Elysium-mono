package com.elysium.lib.nihil;

import java.util.Random;

/**
 * Executes ElysiumNullRules against the JDK alone. Run by validate.py; exits
 * non-zero on the first table that does not hold.
 *
 * <ol>
 *   <li>The thresholds are in order and the top is below one.</li>
 *   <li>A sealed player never accrues; peaceful never accrues; harder is
 *       faster and protection slows it; nothing past hard accrues more.</li>
 *   <li>Damage is nothing below half, one point at half, one multiplier more
 *       per step, quicker once the field speaks, and never falls as the gauge
 *       rises.</li>
 *   <li>The gauge is held in range whatever its capacity, and an unprotected
 *       player lasts between a minute and twenty in a full field.</li>
 *   <li>The ward blocks a fifth at level one, more at every level, never all;
 *       drains the gauge below level three and feeds it from there.</li>
 * </ol>
 */
public final class NullHarness {
    private static int failures;
    private static int checks;

    private NullHarness() {
    }

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theGauge();
        theWard();
        theSweep();
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: the field cannot touch a sealed player, hurts only past half, "
                + "collapses nobody in under a minute, and the ward drains the gauge below level "
                + ElysiumNullRules.wardCrossover() + " and feeds it from there");
    }

    private static void theGauge() {
        check(ElysiumNullRules.DAMAGING < ElysiumNullRules.SPEAKING
                && ElysiumNullRules.SPEAKING < ElysiumNullRules.COLLAPSE, "the thresholds are out of order");
        check(ElysiumNullRules.COLLAPSE < 1.0F, "collapse sits at the very top, so a gauge that rounds never gets there");
        check(ElysiumNullRules.accrual(1.0F, 3) == 0.0F, "a sealed player accrues on hard");
        check(ElysiumNullRules.accrual(0.0F, 0) == 0.0F, "peaceful accrues");
        check(ElysiumNullRules.accrual(Float.NaN, 3) > 0.0F, "a NaN protection stops the field");
        check(ElysiumNullRules.accrual(0.0F, 1) < ElysiumNullRules.accrual(0.0F, 2)
                && ElysiumNullRules.accrual(0.0F, 2) < ElysiumNullRules.accrual(0.0F, 3), "harder is not faster");
        check(ElysiumNullRules.accrual(0.5F, 3) < ElysiumNullRules.accrual(0.0F, 3), "protection does not slow the field");
        check(ElysiumNullRules.accrual(0.0F, 9) == ElysiumNullRules.accrual(0.0F, 3), "a difficulty past hard accrues more");
        check(ElysiumNullRules.damage(0.49F, 1.0F) == 0.0F, "the field hurts below half");
        check(ElysiumNullRules.damage(0.50F, 1.0F) == 1.0F, "the field's first blow is not one point");
        check(ElysiumNullRules.damage(0.60F, 1.0F) == 2.0F, "one step past half is not one multiplier more");
        check(ElysiumNullRules.damage(0.95F, 2.0F) == 9.0F, "the top blow at multiplier two is not nine");
        check(ElysiumNullRules.damageInterval(0.74F) == 40 && ElysiumNullRules.damageInterval(0.75F) == 20,
                "the field does not quicken when it speaks");
        check(!ElysiumNullRules.speaks(0.74F) && ElysiumNullRules.speaks(0.75F), "the field speaks at the wrong share");
        check(!ElysiumNullRules.collapses(0.949F) && ElysiumNullRules.collapses(0.95F), "collapse is at the wrong share");
        check(ElysiumNullRules.after(99.0F, 5.0F, 100.0F) == 100.0F, "the gauge overflows");
        check(ElysiumNullRules.after(0.5F, -5.0F, 100.0F) == 0.0F, "the gauge goes negative");
        check(ElysiumNullRules.after(150.0F, 0.0F, 120.0F) == 120.0F, "a gauge above a shrunk capacity is not brought down");
        check(ElysiumNullRules.ratio(50.0F, 0.0F) == 1.0F, "a capacity of nothing is not read as full");
        check(ElysiumNullRules.ratio(50.0F, 200.0F) == 0.25F, "the ratio is not saturation over capacity");
        check(ElysiumNullRules.resilienceTicks(5.0D) == 100, "five seconds is not a hundred ticks");
        check(ElysiumNullRules.resilienceTicks(0.0D) == 20, "a resilience of nothing ticks faster than once a second");
        check(ElysiumNullRules.resilienceTicks(Double.NaN) == 100, "a NaN resilience is not the default");
        check(ElysiumNullRules.decay(1) == 2.0F * ElysiumNullRules.decay(2), "easy does not forget twice as fast");
        check(ElysiumNullRules.secondsToCollapse(1.0F, 3, 100.0F) == Integer.MAX_VALUE, "a sealed player collapses eventually");
        int hard = ElysiumNullRules.secondsToCollapse(0.0F, 3, ElysiumNullRules.DEFAULT_CAPACITY);
        check(hard >= 60, "an unprotected player collapses in " + hard + " s, under a minute");
        check(hard <= 20 * 60, "an unprotected player lasts " + hard + " s on hard, which is not a threat");
        check(ElysiumNullRules.secondsToCollapse(0.0F, 1, 100.0F) > hard, "easy collapses no slower than hard");
        check(ElysiumNullRules.secondsToCollapse(0.0F, 3, 200.0F) > hard, "a bigger capacity does not last longer");
        float gauge = ElysiumNullRules.DEFAULT_CAPACITY;
        int seconds = 0;
        while (gauge > 0.0F && seconds < 24 * 3600) {
            gauge = ElysiumNullRules.after(gauge, -ElysiumNullRules.decay(2), ElysiumNullRules.DEFAULT_CAPACITY);
            seconds += ElysiumNullRules.DECAY_SECONDS;
        }
        check(gauge == 0.0F, "a full gauge never empties");
        check(seconds <= 15 * 60, "a full gauge takes " + seconds + " s to empty, which outlasts the visit");
    }

    private static void theWard() {
        check(Math.abs(ElysiumNullRules.wardBlock(1) - 0.2F) < 1.0E-6F,
                "a level-one ward blocks " + ElysiumNullRules.wardBlock(1) + ", not a fifth");
        float previous = 0.0F;
        for (int level = 1; level <= 200; level++) {
            float block = ElysiumNullRules.wardBlock(level);
            check(block > previous, "level " + level + " blocks no more than " + (level - 1));
            check(block < 1.0F, "level " + level + " blocks everything");
            previous = block;
        }
        check(ElysiumNullRules.wardLeak(10.0F, 8.0F) < 0.0F, "a level-one ward feeds the gauge");
        check(ElysiumNullRules.wardLeak(10.0F, 0.0F) == 10.0F, "a shield that stopped nothing leaks the whole blow");
        int crossover = ElysiumNullRules.wardCrossover();
        check(crossover == 3, "the ward starts leaking at level " + crossover + ", not three as Valoria's did");
        check(ElysiumNullRules.wardLeak(1.0F, 1.0F - ElysiumNullRules.wardBlock(2)) < 0.0F, "level two leaks");
        check(ElysiumNullRules.wardLeak(1.0F, 1.0F - ElysiumNullRules.wardBlock(4)) > 0.0F, "level four drains");
    }

    private static void theSweep() {
        Random random = new Random(20260924L);
        for (int i = 0; i < 100_000; i++) {
            float protection = random.nextFloat();
            int difficulty = random.nextInt(4);
            float gained = ElysiumNullRules.accrual(protection, difficulty);
            check(gained >= 0.0F && gained <= ElysiumNullRules.ACCRUAL_CEILING,
                    "accrual " + gained + " at protection " + protection + ", difficulty " + difficulty);
            float ratio = random.nextFloat();
            float blow = ElysiumNullRules.damage(ratio, 1.0F + random.nextFloat() * 3.0F);
            check(blow >= 0.0F, "negative damage at " + ratio);
            check((ratio < ElysiumNullRules.DAMAGING) == (blow == 0.0F), "damage and threshold disagree at " + ratio);
            float capacity = 1.0F + random.nextFloat() * 1000.0F;
            float held = ElysiumNullRules.after(random.nextFloat() * 2000.0F - 500.0F,
                    random.nextFloat() * 200.0F - 100.0F, capacity);
            check(held >= 0.0F && held <= capacity, "the gauge escaped its range at capacity " + capacity);
            if (failures > 0) {
                return;
            }
        }
        float previous = 0.0F;
        for (int step = 0; step <= 100; step++) {
            float blow = ElysiumNullRules.damage(step / 100.0F, 1.0F);
            check(blow >= previous, "damage falls between " + ((step - 1) / 100.0F) + " and " + (step / 100.0F));
            previous = blow;
        }
    }
}
