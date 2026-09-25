package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Where rotation meets Forge Energy, and the sum that crossing may not get
 * wrong.
 *
 * <h2>Why Age I makes electricity at all</h2>
 *
 * It should not want to. Forge Energy is Age II's subject, and a dynamo here
 * is deliberately a poor bargain — a whole water wheel spent on one lights a
 * lamp. The reason it exists at the bottom of the tree rather than the top of
 * it is that a player should meet the unit before the age that is about it,
 * so that "FE" is a familiar word by the time it starts to matter and not a
 * new mechanic arriving at the same moment as the machines that use it.
 *
 * <h2>The sum</h2>
 *
 * A dynamo takes rotation out of a line and puts FE into the world. Two
 * quantities in two units, and the moment there is an exchange rate between
 * them there is a loop to close: rotation into FE at a dynamo, FE back into
 * rotation at Age II's motor, and round again. If that loop comes home with
 * as much as it left with, the pack has a perpetual motion machine that a
 * player will find in an afternoon.
 *
 * <p>So this states what a torque-rpm is worth in FE — {@link
 * #WORTH_OF_A_TORQUE_RPM} — once, here, at the bottom of the tree, and every
 * later age reads it rather than choosing its own. {@link #crossingReturns()}
 * is then the fraction of the mechanical power that survives the round trip,
 * and it is under one by arithmetic rather than by anybody remembering.
 *
 * <h2>And the gearing</h2>
 *
 * The exploit this model invites is not the loop; it is the gearbox. A dynamo
 * on a line geared up turns faster and makes more FE, and it is not obvious
 * where that stops. It does stop: the reflected load grows with the ratio too,
 * so the line settles slower, and the product has a maximum. {@link
 * #bestRatio} solves for it, and the answer is worth stating in words as well
 * as in algebra — <b>the best a dynamo can ever do on a source is to hold that
 * source at its peak power</b>, which is half its free speed. Gear past that
 * and the line slows more than the dynamo speeds up.
 */
public final class SeamRules {

    private SeamRules() {
    }

    // ==================================================================
    // The exchange rate
    // ==================================================================

    /**
     * What one torque-rpm of mechanical power is worth in FE per tick.
     *
     * <p>Stated here, at the bottom of the tech tree, because both directions
     * across the seam have to agree on it. Age II's motor charges exactly
     * this to turn FE back into rotation; a dynamo here pays a good deal less
     * than this to go the other way, and the difference is the loss that
     * makes the loop a loop rather than a generator.
     */
    public static final float WORTH_OF_A_TORQUE_RPM = 0.05F;

    /**
     * What a dynamo actually pays, in FE per tick per torque-rpm taken.
     *
     * <p>Not a constant of its own: it is what {@link
     * KineticRules#DYNAMO_FE_PER_RPM} and {@link KineticRules#DYNAMO}'s draw
     * already say between them, read back rather than typed again. A second
     * number here is a second number to drift.
     */
    public static float dynamoRate() {
        return rate(KineticRules.DYNAMO_FE_PER_RPM, KineticRules.DYNAMO.draw());
    }

    /**
     * FE per torque-rpm for a dynamo described by these two numbers.
     *
     * @param fePerRpm what it makes per rpm it turns at
     * @param draw     what it takes out of the line, in torque
     */
    public static float rate(float fePerRpm, float draw) {
        return draw <= 0.0F ? Float.POSITIVE_INFINITY : fePerRpm / draw;
    }

    /**
     * How much of the mechanical power comes home from a round trip across
     * the seam and back: rotation to FE at a dynamo, FE to rotation at a
     * motor.
     *
     * <p>Under one, or the pack has a perpetual motion machine.
     */
    public static float crossingReturns() {
        return returns(dynamoRate(), WORTH_OF_A_TORQUE_RPM);
    }

    /** The same, for a pair of rates a control can choose. */
    public static float returns(float dynamo, float worth) {
        return worth <= 0.0F ? Float.POSITIVE_INFINITY : dynamo / worth;
    }

    /**
     * The most of the mechanical power a crossing may ever return.
     *
     * <p>Half. Not because half is special, but because the spec promises
     * that Age I's electricity is <em>bad</em>, and a crossing that returned
     * nine tenths would be a dynamo nobody notices is a bad bargain.
     */
    public static final float WORST_WORTH_HAVING = 0.5F;

    // ==================================================================
    // What a dynamo can be made to do
    // ==================================================================

    /**
     * The gear ratio that gets the most FE out of a source, solved.
     *
     * <p>A dynamo geared up by {@code r} through {@code stages} of teeth
     * reflects onto the line as a load of {@code D·r/k}, so the line settles
     * at {@code f·(1 − D·r/(k·s))} and the dynamo itself turns {@code r}
     * times that. That product is a downward parabola in {@code r} and its
     * peak is where its derivative is zero:
     *
     * <pre>
     *     g(r)  = r·f·(1 − D·r/(k·s))
     *     g'(r) = f − 2·f·D·r/(k·s) = 0
     *     r*    = k·s / (2·D)
     * </pre>
     *
     * @param source what is driving the line
     * @param stages how many gear stages the dynamo is from it
     */
    public static float bestRatio(KineticRules.Source source, int stages) {
        return KineticRules.kept(stages) * source.stallTorque()
                / (2.0F * KineticRules.DYNAMO.draw());
    }

    /**
     * How fast a dynamo turns at that ratio.
     *
     * <p>Substituting {@code r*} back in collapses to {@code k·s·f/(4·D)},
     * and {@code s·f/4} is exactly {@link KineticRules.Source#powerAt} at
     * half the free speed — which is the sentence worth remembering: the best
     * a dynamo can do is to hold the source at its peak power, and what it
     * turns at is that power divided by what it draws, less the teeth.
     */
    public static float bestRpm(KineticRules.Source source, int stages) {
        return KineticRules.kept(stages)
                * source.powerAt(source.freeSpeed() / 2.0F)
                / KineticRules.DYNAMO.draw();
    }

    /** The FE per tick a dynamo makes there, before any ceiling is applied. */
    public static float bestOutput(KineticRules.Source source, int stages) {
        return KineticRules.dynamoOutput(bestRpm(source, stages));
    }

    /**
     * What a dynamo on a line actually makes, given how fast it is turning.
     *
     * <p>A thin wrapper so that the block, the harness and the validator all
     * ask the same question of the same method rather than three of them
     * multiplying by the same constant.
     */
    public static float output(float dynamoRpm) {
        return KineticRules.dynamoOutput(Math.min(Math.abs(dynamoRpm),
                KineticRules.MAX_RPM));
    }

    /** What a dynamo takes out of the line, in torque-rpm, at that speed. */
    public static float taking(float dynamoRpm) {
        if (!KineticRules.DYNAMO.working(dynamoRpm)) {
            return 0.0F;
        }
        return KineticRules.DYNAMO.draw() * Math.abs(dynamoRpm);
    }

    /**
     * Every source a dynamo could be hung off: the age's own four, and the
     * engines, which are sources too and live in their own file.
     *
     * <p>Walked rather than listed, so that an engine added to the ladder is
     * an engine the seam has been asked about.
     */
    public static List<KineticRules.Source> everySource() {
        List<KineticRules.Source> all =
                new ArrayList<>(KineticRules.EVERY_SOURCE);
        for (EngineRules.Engine engine : EngineRules.ENGINES) {
            all.add(engine.source());
        }
        return List.copyOf(all);
    }

    /** How much FE a tank holds: a second of the best dynamo anywhere. */
    public static final int BUFFER_TICKS = 20;

    /**
     * The buffer a dynamo block keeps, in FE.
     *
     * <p>Solved off the roster rather than chosen, so that an engine added to
     * the ladder gives the dynamo the tank it now needs without anybody
     * noticing that it needed one. A buffer smaller than a tick's production
     * is a dynamo that throws away what it made whenever nothing is there to
     * take it, which reads to a player as a dynamo that does not work.
     */
    public static int buffer() {
        float best = 0.0F;
        for (KineticRules.Source source : everySource()) {
            best = Math.max(best, bestOutput(source, 0));
        }
        return Math.max(1, Math.round(best * BUFFER_TICKS));
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the seam. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInCrossing(KineticRules.DYNAMO_FE_PER_RPM,
                KineticRules.DYNAMO.draw(), WORTH_OF_A_TORQUE_RPM));
        bad.addAll(faultsInGearing(everySource()));
        return List.copyOf(bad);
    }

    /**
     * Whether the crossing loses, and loses enough to be worth noticing.
     *
     * <p>Three separate rules rather than one, and that is not tidiness. A
     * single method holding all three answers every control with whichever
     * rule happens to fire first: switch off the rule that says a crossing
     * may not return everything, and the rule that says it may not return
     * more than half reports instead, the control passes, and a rule that no
     * longer works has never been seen not to work. This project has made
     * that mistake in four places and found it four times by negative test.
     *
     * @param fePerRpm what a dynamo makes per rpm
     * @param draw     what a dynamo takes, in torque
     * @param worth    what a torque-rpm is worth in FE going the other way
     */
    public static List<String> faultsInCrossing(float fePerRpm, float draw,
                                                float worth) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInRates(fePerRpm, worth));
        bad.addAll(faultsInLoop(rate(fePerRpm, draw), worth));
        bad.addAll(faultsInBargain(rate(fePerRpm, draw), worth));
        return bad;
    }

    /** Whether either side of the crossing is a rate at all. */
    public static List<String> faultsInRates(float fePerRpm, float worth) {
        List<String> bad = new ArrayList<>();
        if (fePerRpm <= 0.0F) {
            bad.add("a dynamo makes " + fePerRpm + " FE per rpm, so it is a "
                    + "load with no product and the seam does not exist");
        }
        if (worth <= 0.0F) {
            bad.add("a torque-rpm is worth " + worth + " FE, so every later "
                    + "age can have rotation for nothing");
        }
        return bad;
    }

    /**
     * Whether a round trip across the seam comes home with everything.
     *
     * @param dynamo what a dynamo pays, in FE per torque-rpm
     * @param worth  what a motor charges for the same
     */
    public static List<String> faultsInLoop(float dynamo, float worth) {
        List<String> bad = new ArrayList<>();
        float home = returns(dynamo, worth);
        if (!(home < 1.0F)) {
            bad.add("a perpetual motion machine, arrived at through the seam "
                    + "rather than through a gearbox: rotation into FE and "
                    + "back into rotation comes home with " + home
                    + " of what it left with, and anything at one or above is "
                    + "a loop a player closes once and never opens");
        }
        return bad;
    }

    /** Whether the crossing is the bad bargain the spec promises. */
    public static List<String> faultsInBargain(float dynamo, float worth) {
        List<String> bad = new ArrayList<>();
        float home = returns(dynamo, worth);
        if (home > WORST_WORTH_HAVING) {
            bad.add("the seam is too good to be the bad bargain the spec "
                    + "promises: a round trip comes home with " + home
                    + " and Age I's electricity is meant to be a curiosity, "
                    + "not a power plant with an extra step");
        }
        return bad;
    }

    /**
     * Whether the solved best gearing really is the best.
     *
     * <p>The closed form above is algebra, and algebra written down once is
     * algebra with a sign in it nobody has ever checked. This does not check
     * that it is the maximum — the harness does, by sweeping ratios against
     * it — but it does check that the ratio and the speed it solves for agree
     * with each other, which is the one thing that can be checked without
     * sweeping anything.
     */
    public static List<String> faultsInGearing(List<KineticRules.Source> sources) {
        List<String> bad = new ArrayList<>();
        for (KineticRules.Source source : sources) {
            bad.addAll(faultsInAgreement(source, bestRatio(source, 0),
                    bestRpm(source, 0)));
        }
        return bad;
    }

    /**
     * Whether a gearing and a dynamo speed are the same answer.
     *
     * <p>Handed both numbers rather than working them out, because working
     * them out is exactly what makes this rule unable to fail: break the
     * ratio and the speed re-solves from the same broken constant, the two
     * agree again, and the rule reports nothing. A control passes a pair that
     * does not agree.
     *
     * <p>What they have to agree about is the sentence the algebra collapses
     * to: at its best gearing a source is held at half its free speed, so the
     * line is at {@code f/2} and the dynamo is at {@code f/2 · r}.
     *
     * @param source what is driving the line
     * @param ratio  the gearing between the line and the dynamo
     * @param rpm    how fast the dynamo is meant to turn there
     */
    public static List<String> faultsInAgreement(KineticRules.Source source,
                                                 float ratio, float rpm) {
        List<String> bad = new ArrayList<>();
        if (!(ratio > 0.0F) || Float.isInfinite(ratio)) {
            bad.add("the best gearing for a source of " + source
                    + " works out at " + ratio + ", which is not a gearing");
        }
        if (!(rpm > 0.0F)) {
            bad.add("a source of " + source + " geared for the most FE turns "
                    + "a dynamo at " + rpm + ", which is a dynamo that is not "
                    + "turning");
        }
        float line = source.freeSpeed() / 2.0F;
        if (Math.abs(line * ratio - rpm) > Math.max(1.0E-3F, rpm * 1.0E-3F)) {
            bad.add("the gearing and the speed disagree for " + source
                    + ": a line at " + line + " geared by " + ratio
                    + " turns a dynamo at " + (line * ratio)
                    + " and the speed solved for is " + rpm + ", so one of "
                    + "the two has been changed and the other has not");
        }
        return bad;
    }
}
