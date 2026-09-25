package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * The first age, executed.
 *
 * <p>The claims worth pinning are the ones the design rests on: that a line
 * slows before it stops, that gearing costs something, that a water wheel
 * cannot drive a saw until the player learns gearing, and that steam forces
 * the brass shaft. Every one of those is an emergent property of four
 * constants and one line of algebra, which is exactly the kind of thing that
 * is true when it is written and quietly false three edits later.
 */
public final class KineticHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    /**
     * A list of faults, reported one at a time.
     *
     * <p>A single check handed a whole list concatenates it into one
     * complaint, and {@link #trimmed} then cuts everything past the first
     * fault away — so a section with three things wrong shows one of them and
     * a reader fixes it three times. Each fault gets its own line and its own
     * character budget instead.
     */
    private static void none(java.util.List<String> faults, String what) {
        check(faults.isEmpty(),
                what + ": " + (faults.isEmpty() ? "" : faults.get(0)));
        for (int at = 1; at < faults.size(); at++) {
            checks++;
            failures++;
            System.out.println("  FAIL: " + trimmed(what + ": "
                    + faults.get(at)));
        }
    }

    private static void near(float got, float want, float slack, String what) {
        check(Math.abs(got - want) <= slack,
                what + ": expected about " + want + ", got " + got);
    }

    public static void main(String[] args) {
        theCurve();
        theLine();
        theWarning();
        gearing();
        shafts();
        theAge();
        pace();
        work();
        theDynamo();
        theSeam();
        theTree();
        theBooks();
        theEngines();
        theProcessing();
        theAnchors();
        edges();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a source is strong when slow, a line finds "
                + "its own speed and warns before it stops, gearing costs what it carries, "
                + "and the age is a ladder - a crank turns one millstone, a wheel turns six, "
                + "no wheel turns a saw until you gear it, and steam shears timber; a "
                + "line with cogs in it is a tree of speeds that jams when two paths "
                + "disagree");
    }

    private static List<KineticRules.Load> loads(KineticRules.Load one, int count) {
        List<KineticRules.Load> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(one);
        }
        return list;
    }

    private static List<KineticRules.Source> sources(KineticRules.Source one, int count) {
        List<KineticRules.Source> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(one);
        }
        return list;
    }

    // ------------------------------------------------------------------

    private static void theCurve() {
        KineticRules.Source wheel = KineticRules.WATER_WHEEL;
        near(wheel.torqueAt(0.0F), wheel.stallTorque(), 0.001F,
                "a source at a standstill makes its stall torque");
        near(wheel.torqueAt(wheel.freeSpeed()), 0.0F, 0.001F,
                "a source at its free speed pushes with nothing left");
        check(wheel.torqueAt(4.0F) > wheel.torqueAt(12.0F),
                "the curve does not fall as the source speeds up, so a source is as strong "
                        + "at full speed as at rest and nothing ever has to be geared down");
        check(wheel.torqueAt(wheel.freeSpeed() * 2.0F) == 0.0F,
                "a source driven past its free speed pushes backwards instead of freewheeling");

        // The two Age I extremes are opposites, which is the whole reason both
        // exist: one for force, one for speed.
        check(KineticRules.WATER_WHEEL.stallTorque() > KineticRules.STEAM_ENGINE.stallTorque(),
                "the steam engine is stronger than the water wheel, so the water wheel has "
                        + "no reason to exist once you can burn something");
        check(KineticRules.STEAM_ENGINE.freeSpeed() > KineticRules.WATER_WHEEL.freeSpeed(),
                "the water wheel is faster than steam, so steam has no reason to exist");
    }

    private static void theLine() {
        KineticRules.Shaft timber = KineticRules.Shaft.TIMBER;
        List<KineticRules.Source> one = sources(KineticRules.WATER_WHEEL, 1);

        KineticRules.Reading idle = KineticRules.settle(one, List.of(), timber);
        near(idle.rpm(), KineticRules.WATER_WHEEL.freeSpeed(), 0.01F,
                "a line with nothing on it does not run at its source's free speed");
        check(idle.running(), "an unloaded line is not running");

        float previous = idle.rpm();
        for (int count = 1; count <= 10; count++) {
            KineticRules.Reading reading =
                    KineticRules.settle(one, loads(KineticRules.MILLSTONE, count), timber);
            check(reading.rpm() < previous,
                    "millstone " + count + " did not slow the line, so machines are free "
                            + "until the moment everything stops at once");
            previous = reading.rpm();
        }

        // Stalling is exactly where the loads outweigh every source at rest.
        int stallsAt = (int) Math.ceil(KineticRules.WATER_WHEEL.stallTorque()
                / KineticRules.MILLSTONE.draw());
        KineticRules.Reading just = KineticRules.settle(one,
                loads(KineticRules.MILLSTONE, stallsAt - 1), timber);
        KineticRules.Reading over = KineticRules.settle(one,
                loads(KineticRules.MILLSTONE, stallsAt), timber);
        check(!just.stalled(), "the line stalls one machine early");
        check(over.stalled(), "the line does not stall when the loads outweigh the source");
        check(over.rpm() == 0.0F, "a stalled line is still turning");
        check(!over.running(), "a stalled line reports itself as running");

        // More sources, more room. Same loads, faster line.
        KineticRules.Reading single = KineticRules.settle(one,
                loads(KineticRules.MILLSTONE, 4), timber);
        KineticRules.Reading doubled = KineticRules.settle(
                sources(KineticRules.WATER_WHEEL, 2), loads(KineticRules.MILLSTONE, 4), timber);
        check(doubled.rpm() > single.rpm(),
                "a second water wheel does not speed the line up, so sources do not add");
        check(doubled.rpm() <= KineticRules.WATER_WHEEL.freeSpeed() + 0.01F,
                "two wheels drive the line past the free speed of either, which is a "
                        + "perpetual motion machine");
    }

    /**
     * The claim this whole model exists to make: a player is warned.
     *
     * Create's network is a light switch. Ours has to climb visibly, and a
     * machine has to stop for a reason that names itself, before anything
     * stalls.
     */
    private static void theWarning() {
        KineticRules.Shaft timber = KineticRules.Shaft.TIMBER;
        List<KineticRules.Source> one = sources(KineticRules.WATER_WHEEL, 1);

        float effort = 0.0F;
        boolean sawAWarning = false;
        for (int count = 0; count <= 11; count++) {
            KineticRules.Reading reading =
                    KineticRules.settle(one, loads(KineticRules.MILLSTONE, count), timber);
            check(reading.effort() >= effort - 0.0001F,
                    "effort fell when a machine was added at " + count);
            effort = reading.effort();
            if (!reading.stalled() && effort > 0.6F && effort < 1.0F) {
                sawAWarning = true;
            }
        }
        check(sawAWarning,
                "the line never reports itself as working hard while it is still running, "
                        + "so the gauge is a light switch after all");
        near(KineticRules.settle(one, List.of(), timber).effort(), 0.0F, 0.0001F,
                "an idle line reports effort");

        // The named failure: dragged slow, the fussiest machine stops first
        // and the line is still turning, which is what makes it diagnosable.
        KineticRules.Reading dragged =
                KineticRules.settle(one, loads(KineticRules.MILLSTONE, 9), timber);
        check(!dragged.stalled() && dragged.rpm() > KineticRules.STOPPED,
                "a heavily loaded line has already stalled, leaving nothing to diagnose");
        check(!KineticRules.MILLSTONE.working(dragged.rpm()),
                "a millstone on a line dragged below its least speed still works, so "
                        + "leastRpm does nothing");

        // And the line has to be able to say what it is waiting for, because
        // the effort gauge reads comfortable at exactly the moment everything
        // has quietly stopped.
        List<KineticRules.Load> nine = loads(KineticRules.MILLSTONE, 9);
        near(KineticRules.waitingFor(nine, dragged.rpm()), KineticRules.MILLSTONE.leastRpm(),
                0.0001F, "a line full of stopped machines cannot say what speed it needs");
        check(dragged.effort() < 0.9F,
                "the effort gauge already reads nearly stalled here, which would make "
                        + "waitingFor redundant and the diagnosis obvious anyway");

        KineticRules.Reading easy = KineticRules.settle(one,
                loads(KineticRules.MILLSTONE, 2), timber);
        check(KineticRules.waitingFor(loads(KineticRules.MILLSTONE, 2), easy.rpm()) == 0.0F,
                "a line where everything is working still reports something waiting");

        // The fussiest machine is the one named, not the first one found.
        List<KineticRules.Load> mixed = List.of(KineticRules.MILLSTONE, KineticRules.MIXER);
        near(KineticRules.waitingFor(mixed, 10.0F), KineticRules.MIXER.leastRpm(), 0.0001F,
                "a line reports the least demanding machine that stopped, so the number "
                        + "shown is not enough to fix it");
    }

    private static void gearing() {
        near(KineticRules.kept(0), 1.0F, 0.0001F, "a line with no gears loses power");
        near(KineticRules.kept(1), 1.0F - KineticRules.GEAR_LOSS, 0.0001F,
                "one gear stage does not cost one gear stage");
        check(KineticRules.kept(4) < KineticRules.kept(2),
                "four stages cost no more than two");
        check(KineticRules.kept(KineticRules.MOST_STAGES * 4) > 0.0F,
                "an absurd gear train underflows to nothing, which divides by zero "
                        + "somewhere downstream");

        float up = KineticRules.ratio(KineticRules.LARGE_TEETH, KineticRules.SMALL_TEETH);
        float down = KineticRules.ratio(KineticRules.SMALL_TEETH, KineticRules.LARGE_TEETH);
        near(Math.abs(up), 3.0F, 0.001F, "a large cog driving a small one is not 3:1");
        near(Math.abs(down), 1.0F / 3.0F, 0.001F, "a small cog driving a large one is not 1:3");
        check(up < 0.0F && down < 0.0F, "a pair of gears turns the same way, not opposite ways");
        near(KineticRules.geared(10.0F, up, up), 90.0F, 0.01F,
                "two step-ups in a row do not multiply");
        near(KineticRules.geared(10.0F, up, down), 10.0F, 0.01F,
                "up then down does not come back to where it started");

        // Power crossing teeth is conserved but for the loss, in both
        // directions. This is the property that makes gearing a trade rather
        // than a cheat.
        KineticRules.Load saw = KineticRules.SAW;
        KineticRules.Load reflected = saw.through(up, 1);
        near(reflected.draw(), saw.draw() * 3.0F / KineticRules.kept(1), 0.01F,
                "a load behind a step-up is not proportionally heavier on the main line");
        check(reflected.leastRpm() < saw.leastRpm(),
                "a machine geared up still demands its own speed of the main line");

        KineticRules.Source geared = KineticRules.WATER_WHEEL.through(up, 1);
        check(geared.freeSpeed() < KineticRules.WATER_WHEEL.freeSpeed(),
                "a source behind a step-up is not slower as the main line sees it");
        check(geared.stallTorque() > KineticRules.WATER_WHEEL.stallTorque(),
                "a source behind a step-up is not stronger as the main line sees it");
        float before = KineticRules.WATER_WHEEL.stallTorque() * KineticRules.WATER_WHEEL.freeSpeed();
        float after = geared.stallTorque() * geared.freeSpeed();
        check(after < before, "gearing a source costs nothing, so a gear train is free power");
        near(after, before * KineticRules.kept(1), 0.01F,
                "gearing a source costs something other than the stated loss");
    }

    private static void shafts() {
        check(!KineticRules.shears(KineticRules.Shaft.TIMBER, 64.0F),
                "timber shears at exactly its rating, so the rating is not the rating");
        check(KineticRules.shears(KineticRules.Shaft.TIMBER, 65.0F),
                "timber survives above its rating");
        check(!KineticRules.shears(KineticRules.Shaft.BRASS, 65.0F),
                "brass is no better than timber");
        for (KineticRules.Shaft shaft : KineticRules.Shaft.values()) {
            check(shaft.rating() <= KineticRules.MAX_RPM,
                    shaft + " is rated past the ceiling every age shares");
        }
        check(KineticRules.Shaft.ASCENDANT.rating() == KineticRules.MAX_RPM,
                "nothing in any age reaches the ceiling, so the ceiling is decoration");
        check(KineticRules.weakest(List.of(KineticRules.Shaft.STEEL, KineticRules.Shaft.TIMBER,
                        KineticRules.Shaft.BRASS)) == KineticRules.Shaft.TIMBER,
                "a line is as strong as its strongest shaft, which is not how a chain works");
    }

    /**
     * The age as a ladder.
     *
     * Each rung here is a thing a player cannot do until they have built the
     * thing before it, and every one of them falls out of the constants rather
     * than being gated by a research tree.
     */
    private static void theAge() {
        KineticRules.Shaft timber = KineticRules.Shaft.TIMBER;
        List<KineticRules.Source> crank = sources(KineticRules.HAND_CRANK, 1);
        List<KineticRules.Source> wheel = sources(KineticRules.WATER_WHEEL, 1);

        KineticRules.Reading oneStone =
                KineticRules.settle(crank, loads(KineticRules.MILLSTONE, 1), timber);
        check(oneStone.running() && KineticRules.MILLSTONE.working(oneStone.rpm()),
                "a hand crank cannot turn a single millstone, so the age has no first step");
        check(KineticRules.settle(crank, loads(KineticRules.MILLSTONE, 2), timber).stalled(),
                "a hand crank turns two millstones, so nobody ever builds a water wheel");

        int turning = 0;
        for (int count = 1; count <= 12; count++) {
            KineticRules.Reading reading =
                    KineticRules.settle(wheel, loads(KineticRules.MILLSTONE, count), timber);
            if (reading.running() && KineticRules.MILLSTONE.working(reading.rpm())) {
                turning = count;
            }
        }
        check(turning >= 4 && turning <= 8,
                "a water wheel usefully turns " + turning + " millstones; the design says "
                        + "about six, and a number far from that is a different game");

        // The saw is the lesson. No number of water wheels reaches 24 rpm,
        // because no water wheel goes above 16 however strong the line is.
        boolean everDirect = false;
        for (int count = 1; count <= 16; count++) {
            KineticRules.Reading reading = KineticRules.settle(
                    sources(KineticRules.WATER_WHEEL, count), List.of(KineticRules.SAW), timber);
            if (reading.running() && KineticRules.SAW.working(reading.rpm())) {
                everDirect = true;
            }
        }
        check(!everDirect,
                "a saw runs straight off water wheels, so gearing is optional and the "
                        + "second half of the age never has to be learned");

        float up = KineticRules.ratio(KineticRules.LARGE_TEETH, KineticRules.SMALL_TEETH);
        KineticRules.Reading gearedSaw = KineticRules.settle(
                wheel, List.of(KineticRules.SAW.through(up, 1)), timber);
        float atTheSaw = Math.abs(KineticRules.geared(gearedSaw.rpm(), up));
        check(gearedSaw.running() && KineticRules.SAW.working(atTheSaw),
                "one water wheel and one step-up still will not turn a saw, so the lesson "
                        + "has no answer");

        // Steam is the door out, and it takes the timber with it.
        KineticRules.Reading steam = KineticRules.settle(
                sources(KineticRules.STEAM_ENGINE, 1), List.of(KineticRules.MIXER), timber);
        check(KineticRules.MIXER.working(steam.rpm()),
                "steam will not turn a mixer, so the end of the age reaches nothing new");
        check(steam.sheared(),
                "steam runs happily on timber shafts, so brass is a cosmetic upgrade");
        KineticRules.Reading onBrass = KineticRules.settle(
                sources(KineticRules.STEAM_ENGINE, 1), List.of(KineticRules.MIXER),
                KineticRules.Shaft.BRASS);
        check(onBrass.running(), "steam on brass shafts still shears, so there is no fix");

        check(KineticRules.MOST_SAILS > 0, "a windmill has no sails");
        KineticRules.Reading windmill = KineticRules.settle(
                sources(KineticRules.SAIL, KineticRules.MOST_SAILS), List.of(), timber);
        check(windmill.rpm() <= KineticRules.SAIL.freeSpeed() + 0.01F,
                "sails add speed as well as force, so a big enough windmill is a steam engine");
        KineticRules.Reading full = KineticRules.settle(
                sources(KineticRules.SAIL, KineticRules.MOST_SAILS),
                loads(KineticRules.MILLSTONE, 12), timber);
        check(full.running(),
                "a full windmill carries less than a water wheel, so nobody would build one");
    }

    private static void pace() {
        check(KineticRules.pace(0.0F) == KineticRules.Pace.STOPPED, "a stopped line has a pace");
        check(KineticRules.pace(KineticRules.SLOW_AT) == KineticRules.Pace.SLOW,
                "the slow threshold is not slow");
        check(KineticRules.pace(KineticRules.STEADY_AT) == KineticRules.Pace.STEADY,
                "the steady threshold is not steady");
        check(KineticRules.pace(KineticRules.FAST_AT) == KineticRules.Pace.FAST,
                "the fast threshold is not fast");
        check(KineticRules.pace(KineticRules.RACING_AT) == KineticRules.Pace.RACING,
                "the racing threshold is not racing");
        check(KineticRules.pace(-40.0F) == KineticRules.pace(40.0F),
                "a line turning the other way has a different pace");

        float[] thresholds = {KineticRules.SLOW_AT, KineticRules.STEADY_AT,
                KineticRules.FAST_AT, KineticRules.RACING_AT};
        for (int i = 1; i < thresholds.length; i++) {
            check(thresholds[i] > thresholds[i - 1],
                    "the pace thresholds are not in order, so one band is empty");
        }
        check(KineticRules.FAST_AT == KineticRules.Shaft.TIMBER.rating(),
                "the pace a timber shaft shears at is not where the bands change, so the "
                        + "recipe book and the shaft disagree about what fast means");
        check(KineticRules.enough(100.0F, KineticRules.Pace.STEADY),
                "a fast line is not enough for a recipe that asked for steady");
        check(!KineticRules.enough(10.0F, KineticRules.Pace.STEADY),
                "a slow line satisfies a recipe that asked for steady");
    }

    private static void work() {
        int base = 100;
        check(KineticRules.ticksFor(KineticRules.REFERENCE_RPM, base) == base,
                "the reference speed does not take the base time, so the base time means "
                        + "nothing");
        check(KineticRules.ticksFor(64.0F, base) < KineticRules.ticksFor(32.0F, base),
                "a faster line does not work faster");
        check(KineticRules.ticksFor(0.0F, base) == KineticRules.SLOWEST_TICKS,
                "a stopped line still finishes things");
        check(KineticRules.ticksFor(KineticRules.MAX_RPM, base) >= KineticRules.QUICKEST_TICKS,
                "a very fast line does an operation in less than the floor, which turns a "
                        + "machine into a multiplier on its own output");
        check(KineticRules.ticksFor(0.01F, base) <= KineticRules.SLOWEST_TICKS,
                "a barely turning line takes longer than the ceiling");
    }

    private static void theDynamo() {
        check(KineticRules.dynamoOutput(0.0F) == 0.0F, "a stopped dynamo makes power");
        check(KineticRules.dynamoOutput(KineticRules.DYNAMO.leastRpm() - 1.0F) == 0.0F,
                "a dynamo below its least speed still makes power");
        check(KineticRules.dynamoOutput(64.0F) > KineticRules.dynamoOutput(32.0F),
                "a faster dynamo makes no more power");

        // The whole point: Age I electricity is a curiosity, not a power plant.
        // A water wheel cannot even reach the dynamo's least speed, so it takes
        // gearing and it still pays almost nothing.
        KineticRules.Reading direct = KineticRules.settle(
                sources(KineticRules.WATER_WHEEL, 1), List.of(KineticRules.DYNAMO),
                KineticRules.Shaft.TIMBER);
        check(KineticRules.dynamoOutput(direct.rpm()) == 0.0F,
                "a water wheel drives a dynamo with no gearing at all, so Age I electricity "
                        + "is free");
        KineticRules.Reading steam = KineticRules.settle(
                sources(KineticRules.STEAM_ENGINE, 1), List.of(KineticRules.DYNAMO),
                KineticRules.Shaft.BRASS);
        float fe = KineticRules.dynamoOutput(steam.rpm());
        check(fe > 0.0F, "a steam engine cannot drive a dynamo at all, so the item is dead");
        check(fe < 40.0F,
                "a single Age I dynamo makes " + fe + " FE/t, which is a power plant; Age II "
                        + "has nothing left to be about");
    }

    /**
     * The seam: rotation into Forge Energy, and the two things that crossing
     * has to be.
     *
     * <p>It has to lose — or the loop out through a dynamo and back through
     * Age II's motor is a generator — and the closed form for the best a
     * dynamo can be geared to has to actually be the best, which is the kind
     * of algebra that is written once and then trusted forever with a sign
     * in it the wrong way round.
     */
    private static void theSeam() {
        none(SeamRules.faults(), "the seam");

        // The crossing loses, and loses enough to be the bad bargain the spec
        // promises.
        check(SeamRules.crossingReturns() < 1.0F,
                "rotation into FE and back into rotation comes home with "
                        + SeamRules.crossingReturns() + " of what it left "
                        + "with, which is a perpetual motion machine");
        check(SeamRules.crossingReturns() <= SeamRules.WORST_WORTH_HAVING,
                "the seam comes home with " + SeamRules.crossingReturns()
                        + " and Age I's electricity is meant to be a "
                        + "curiosity");
        check(SeamRules.crossingReturns() > 0.0F,
                "the seam returns nothing at all, so a dynamo is a load with "
                        + "no product");

        // The solved gearing against a sweep. The closed form says a dynamo
        // does best when the source is held at half its free speed; this
        // takes every source there is, tries five hundred ratios at every
        // stage count, and checks that none of them beats it.
        int swept = 0;
        for (KineticRules.Source source : SeamRules.everySource()) {
            for (int stages = 0; stages <= 4; stages++) {
                float solved = SeamRules.bestRpm(source, stages);
                float kept = KineticRules.kept(stages);
                float best = 0.0F;
                float bestAt = 0.0F;
                for (int step = 1; step <= 500; step++) {
                    float ratio = SeamRules.bestRatio(source, stages)
                            * step / 250.0F;
                    KineticRules.Load reflected =
                            KineticRules.DYNAMO.through(ratio, stages);
                    KineticRules.Reading settled = KineticRules.settle(
                            List.of(source), List.of(reflected),
                            KineticRules.Shaft.ASCENDANT);
                    if (!settled.running()) {
                        continue;
                    }
                    float turning = settled.rpm() * ratio;
                    swept++;
                    if (turning > best) {
                        best = turning;
                        bestAt = ratio;
                    }
                    check(turning <= solved + Math.max(1.0E-3F, solved * 1.0E-3F),
                            "a dynamo geared by " + ratio + " through "
                                    + stages + " stage(s) turns at " + turning
                                    + " and the solved best is " + solved
                                    + ", so the closed form for the best "
                                    + "gearing is not the best");
                }
                check(Math.abs(best - solved) <= solved * 0.01F,
                        "the best of five hundred ratios is " + best
                                + " and the solved best is " + solved
                                + ", so the closed form is a ceiling nothing "
                                + "reaches rather than a peak");
                near(bestAt, SeamRules.bestRatio(source, stages),
                        SeamRules.bestRatio(source, stages) * 0.02F,
                        "the ratio the sweep found best and the ratio the "
                                + "algebra solves for disagree");
                // And the source really is being held at half its free speed
                // there, which is the sentence the algebra collapses to.
                near(solved / bestAt, source.freeSpeed() / 2.0F,
                        source.freeSpeed() * 0.01F,
                        "at its best gearing the line is not at half the "
                                + "source's free speed");
                check(kept > 0.0F, "a gearing that keeps nothing");
            }
        }
        check(swept > 5_000, "only " + swept + " gearing(s) were swept");

        // No line anywhere makes more FE than the rotation put into it is
        // worth. This is the conservation statement, and it is about solved
        // lines rather than about the formula: the teeth are paid, the line
        // settles where it settles, and the dynamo is asked what it made.
        int lines = 0;
        for (KineticRules.Source source : SeamRules.everySource()) {
            for (int count = 1; count <= 3; count++) {
                for (int stages = 0; stages <= 4; stages++) {
                    for (int step = 1; step <= 60; step++) {
                        float ratio = step / 8.0F;
                        KineticRules.Load reflected =
                                KineticRules.DYNAMO.through(ratio, stages);
                        KineticRules.Reading settled = KineticRules.settle(
                                sources(source, count), List.of(reflected),
                                KineticRules.Shaft.ASCENDANT);
                        if (!settled.running()) {
                            continue;
                        }
                        lines++;
                        float turning = settled.rpm() * ratio;
                        float made = SeamRules.output(turning);
                        float taken = SeamRules.taking(turning);
                        check(made <= taken * SeamRules.WORTH_OF_A_TORQUE_RPM
                                        + 1.0E-3F,
                                "a machine hall that makes energy: a dynamo "
                                        + "geared by " + ratio + " made "
                                        + made + " FE from " + taken
                                        + " torque-rpm, and a torque-rpm is "
                                        + "worth "
                                        + SeamRules.WORTH_OF_A_TORQUE_RPM);
                        // And what the source delivered is at least what the
                        // dynamo consumed: the teeth cannot be a profit.
                        float delivered = source.torqueAt(settled.rpm())
                                * settled.rpm() * count;
                        check(delivered >= taken - 1.0E-2F,
                                "a dynamo took " + taken + " torque-rpm out "
                                        + "of a line delivering " + delivered
                                        + ", so the teeth gave power back");
                    }
                }
            }
        }
        check(lines > 2_000, "only " + lines + " line(s) with a dynamo on them "
                + "were built");

        // Controls, built to be caught, because over the numbers this module
        // ships every rule above can only ever answer no.
        // Controls, built to be caught, because over the numbers this module
        // ships every rule above can only ever answer no. Each one is pointed
        // at its own rule: a control aimed at the whole crossing is answered
        // by whichever of the three rules happens to fire first, and two of
        // them could then be deleted with every check still green.
        float worth = SeamRules.WORTH_OF_A_TORQUE_RPM;
        check(!SeamRules.faultsInLoop(worth, worth).isEmpty(),
                "a dynamo paying exactly what a motor charges is not "
                        + "reported, so the rule that the seam loses cannot "
                        + "say no");
        check(!SeamRules.faultsInLoop(worth * 2.0F, worth).isEmpty(),
                "a dynamo paying twice what a motor charges is not reported");
        check(SeamRules.faultsInLoop(worth * 0.1F, worth).isEmpty(),
                "a crossing that loses nine tenths is reported as a loop");
        check(!SeamRules.faultsInBargain(worth * 0.9F, worth).isEmpty(),
                "a crossing that comes home with nine tenths is not "
                        + "reported, so Age I could quietly become a power "
                        + "plant while still losing");
        check(SeamRules.faultsInBargain(worth * 0.4F, worth).isEmpty(),
                "a crossing that comes home with two fifths is reported as "
                        + "too good");
        check(!SeamRules.faultsInRates(0.0F, worth).isEmpty(),
                "a dynamo that makes nothing is not reported");
        check(!SeamRules.faultsInRates(KineticRules.DYNAMO_FE_PER_RPM, 0.0F)
                        .isEmpty(),
                "a torque-rpm worth nothing is not reported");
        check(SeamRules.faultsInRates(KineticRules.DYNAMO_FE_PER_RPM, worth)
                        .isEmpty(),
                "the two rates this module ships are reported as faulty");
        check(SeamRules.faultsInCrossing(KineticRules.DYNAMO_FE_PER_RPM,
                        KineticRules.DYNAMO.draw(), worth).isEmpty(),
                "the crossing this module ships is reported as faulty");

        // And the gearing, handed a pair that does not agree — which is the
        // only way this rule can ever be shown able to say yes, because a
        // solved ratio and a solved speed always agree with each other.
        KineticRules.Source control = KineticRules.WATER_WHEEL;
        check(!SeamRules.faultsInAgreement(control,
                        SeamRules.bestRatio(control, 0) * 2.0F,
                        SeamRules.bestRpm(control, 0)).isEmpty(),
                "a gearing twice what the solved speed needs is not reported, "
                        + "so the ratio and the speed could drift apart");
        check(!SeamRules.faultsInAgreement(control,
                        SeamRules.bestRatio(control, 0),
                        SeamRules.bestRpm(control, 0) * 0.5F).isEmpty(),
                "a speed half what the solved gearing gives is not reported");
        check(!SeamRules.faultsInAgreement(control, -1.0F,
                        SeamRules.bestRpm(control, 0)).isEmpty(),
                "a gearing that is not a gearing is not reported");
        check(!SeamRules.faultsInAgreement(control,
                        SeamRules.bestRatio(control, 0), 0.0F).isEmpty(),
                "a dynamo that does not turn is not reported");
        check(SeamRules.faultsInAgreement(control,
                        SeamRules.bestRatio(control, 0),
                        SeamRules.bestRpm(control, 0)).isEmpty(),
                "the solved pair for a water wheel is reported as disagreeing");
        check(SeamRules.faultsInGearing(SeamRules.everySource()).isEmpty(),
                "the sources this module ships are reported as ungearable");

        // The buffer is solved off the roster and is big enough to hold what
        // the best dynamo anywhere makes while nothing is taking it.
        check(SeamRules.buffer() > 0, "a dynamo with no tank at all");
        float bestAnywhere = 0.0F;
        for (KineticRules.Source source : SeamRules.everySource()) {
            bestAnywhere = Math.max(bestAnywhere, SeamRules.bestOutput(source, 0));
        }
        check(SeamRules.buffer() >= Math.round(bestAnywhere),
                "a dynamo's tank holds " + SeamRules.buffer()
                        + " FE and the best dynamo anywhere makes "
                        + bestAnywhere + " a tick, so it throws away what it "
                        + "made whenever nothing is there to take it");

        System.out.println("  the seam: " + swept + " gearing(s) swept against "
                + "the closed form and " + lines + " line(s) with a dynamo on "
                + "them, none of them making more FE than the rotation was "
                + "worth, and a round trip through Age II coming home with "
                + String.format("%.2f", SeamRules.crossingReturns()));
    }

    /**
     * A line with cogs in it: one root, many speeds, and the ways it fails.
     */
    private static void theTree() {
        float up = KineticRules.ratio(KineticRules.LARGE_TEETH, KineticRules.SMALL_TEETH);
        float down = KineticRules.ratio(KineticRules.SMALL_TEETH, KineticRules.LARGE_TEETH);

        check(!LineRules.conflict(3.0F, 3.0F), "a shaft disagrees with itself");
        check(!LineRules.conflict(3.0F, 3.0F * (1.0F + LineRules.SAME_RATIO / 2.0F)),
                "float drift through a gear train jams a line that is perfectly sound");
        check(LineRules.conflict(3.0F, 1.0F),
                "one shaft asked to turn at one speed and at three times it does not jam");
        check(LineRules.conflict(3.0F, -3.0F),
                "one shaft asked to turn both ways at once does not jam");
        check(!LineRules.conflict(0.0F, 0.0F), "two stopped shafts conflict");

        // Round trip: up then down is where you started, however deep.
        float there = LineRules.compose(LineRules.compose(1.0F, up), down);
        check(!LineRules.conflict(there, 1.0F),
                "a step-up followed by a step-down does not come back to the root's speed");
        near(LineRules.speedAt(10.0F, up), -30.0F, 0.001F,
                "a shaft one step-up from the root does not turn three times as fast, "
                        + "the other way");

        // The whole point of the tree: one settle, many speeds read back.
        LineRules.At root = LineRules.At.ROOT;
        LineRules.At geared = new LineRules.At(up, 1);
        LineRules.Assembly wheelAndSaw = LineRules.assemble(
                List.of(new LineRules.Driven(KineticRules.WATER_WHEEL, root)),
                List.of(new LineRules.Drawn(KineticRules.SAW, geared)),
                KineticRules.Shaft.TIMBER, false);
        check(wheelAndSaw.running(), "a water wheel and a geared saw will not run");
        float atSaw = Math.abs(wheelAndSaw.speedOf(geared));
        check(KineticRules.SAW.working(atSaw),
                "the saw on the geared line is turning at " + atSaw + ", too slow to cut");
        check(atSaw > Math.abs(wheelAndSaw.speedOf(root)),
                "the geared half of the line is not turning faster than the root");

        LineRules.Assembly jam = LineRules.assemble(
                List.of(new LineRules.Driven(KineticRules.WATER_WHEEL, root)),
                List.of(new LineRules.Drawn(KineticRules.MILLSTONE, root)),
                KineticRules.Shaft.TIMBER, true);
        check(jam.jammed() && !jam.running(), "a jammed line still runs");
        check(jam.speedOf(root) == 0.0F, "a jammed line is still turning");

        // Shearing is decided at the fastest shaft, not at the root. A root
        // turning at a safe 30 with a 3:1 out to 90 shears timber, and a check
        // written against the root would call that line healthy.
        // Four engines, because one is dragged to a crawl by the reflected
        // weight of the far millstone — a load two step-ups out is nine times
        // heavier at the root, which is the trade the model exists to make.
        LineRules.At far = new LineRules.At(up * up, 2);
        List<LineRules.Driven> bank = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            bank.add(new LineRules.Driven(KineticRules.STEAM_ENGINE, root));
        }
        LineRules.Assembly overdriven = LineRules.assemble(bank,
                List.of(new LineRules.Drawn(KineticRules.MILLSTONE, far)),
                KineticRules.Shaft.BRASS, false);
        check(overdriven.root().rpm() < KineticRules.Shaft.BRASS.rating(),
                "the root of this line is already over the rating, so the check below "
                        + "would pass for the wrong reason");
        check(overdriven.root().sheared(),
                "a line whose far end is spun past the shaft rating reports itself intact, "
                        + "because shearing was decided at the root");

        // waitingFor names the speed written on the machine, not that speed
        // divided by whatever cogs happen to sit between it and the root.
        List<LineRules.Drawn> fussy = List.of(new LineRules.Drawn(KineticRules.MIXER, geared));
        LineRules.Assembly tooSlow = LineRules.assemble(
                List.of(new LineRules.Driven(KineticRules.HAND_CRANK, root)),
                fussy, KineticRules.Shaft.TIMBER, false);
        float asked = LineRules.waitingFor(fussy, tooSlow);
        check(asked == 0.0F || Math.abs(asked - KineticRules.MIXER.leastRpm()) < 0.001F,
                "a geared line reports a speed the machine's own label does not say");

        LineRules.Assembly fine = LineRules.assemble(
                List.of(new LineRules.Driven(KineticRules.WATER_WHEEL, root)),
                List.of(new LineRules.Drawn(KineticRules.MILLSTONE, root)),
                KineticRules.Shaft.TIMBER, false);
        check(LineRules.waitingFor(
                        List.of(new LineRules.Drawn(KineticRules.MILLSTONE, root)), fine) == 0.0F,
                "a line where everything works still says it is waiting for something");

        check(!LineRules.tooBig(LineRules.MOST_MEMBERS),
                "a line of exactly the limit is refused, so the limit is not the limit");
        check(LineRules.tooBig(LineRules.MOST_MEMBERS + 1),
                "a line past the limit is walked anyway");

        boolean refused = false;
        try {
            new LineRules.At(0.0F, 0);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a member geared to a standstill is accepted onto the line");
    }

// ==================================================================

    /**
     * The one thing anything kinetic has to prove.
     *
     * <p>Torque is conserved. At the speed a line settles at, what the
     * sources push equals what the loads draw plus what is idle — on every
     * network, including ones with loops in them. A hall that gains torque
     * going round a loop is a perpetual motion machine, and a player finds
     * that long before anybody finds it by reading.
     *
     * <p>Randomly built rather than hand-written, because the interesting
     * cases are combinations: four kinds of source, five kinds of load,
     * gearing in both directions to six stages deep, and a loop that agrees
     * with itself. Nobody writes the thirty-thousandth of those by hand.
     */
    private static void theBooks() {
        java.util.List<KineticRules.Source> kinds = java.util.List.of(
                KineticRules.HAND_CRANK, KineticRules.WATER_WHEEL,
                KineticRules.SAIL, KineticRules.STEAM_ENGINE);
        java.util.List<KineticRules.Load> drawn = java.util.List.of(
                KineticRules.MILLSTONE, KineticRules.PRESS, KineticRules.SAW,
                KineticRules.MIXER, KineticRules.DYNAMO);
        float up = KineticRules.ratio(KineticRules.LARGE_TEETH,
                KineticRules.SMALL_TEETH);
        float down = KineticRules.ratio(KineticRules.SMALL_TEETH,
                KineticRules.LARGE_TEETH);

        java.util.Random random = new java.util.Random(6_170_425L);
        int built = 0;
        float worst = 0.0F;
        for (int hall = 0; hall < 20_000; hall++) {
            java.util.List<LineRules.Driven> sources = new java.util.ArrayList<>();
            java.util.List<LineRules.Drawn> loads = new java.util.ArrayList<>();
            int howManySources = 1 + random.nextInt(4);
            for (int at = 0; at < howManySources; at++) {
                sources.add(new LineRules.Driven(
                        kinds.get(random.nextInt(kinds.size())),
                        somewhere(random, up, down)));
            }
            int howManyLoads = random.nextInt(5);
            for (int at = 0; at < howManyLoads; at++) {
                loads.add(new LineRules.Drawn(
                        drawn.get(random.nextInt(drawn.size())),
                        somewhere(random, up, down)));
            }
            KineticRules.Shaft shaft = KineticRules.Shaft.values()[
                    random.nextInt(KineticRules.Shaft.values().length)];
            LineRules.Assembly hallAssembly =
                    LineRules.assemble(sources, loads, shaft, false);
            built++;
            LineRules.Balance books = LineRules.balance(sources, loads, hallAssembly);
            worst = Math.max(worst, books.apart());
            check(books.apart() < 1.0E-3F,
                    "a hall of " + howManySources + " source(s) and " + howManyLoads
                            + " load(s) pushes " + books.pushed() + " and draws "
                            + books.drawn() + " with " + books.idle()
                            + " idle, which does not add up");
            check(books.idle() >= -1.0E-4F,
                    "a hall is drawing more than its sources push, which is "
                            + "torque out of nowhere");
            check(hallAssembly.root().rpm() >= 0.0F,
                    "a line settled at a negative speed, and the root's speed is "
                            + "a magnitude");
            check(hallAssembly.root().rpm() <= KineticRules.MAX_RPM,
                    "a line settled at " + hallAssembly.root().rpm()
                            + " and nothing in any age turns faster than "
                            + KineticRules.MAX_RPM);
        }
        check(built == 20_000, "only " + built + " hall(s) were built");

        // A loop that agrees with itself. Out through a ratio and back
        // through its inverse has to give the source back, less the teeth.
        for (KineticRules.Source one : kinds) {
            for (float ratio : new float[] {up, down, up * up, down * down,
                    1.0F, -up, -down}) {
                for (int stages = 0; stages <= 4; stages++) {
                    float gained = LineRules.goingRound(one, ratio, stages);
                    check(gained <= 1.0E-4F,
                            "a source sent out through " + ratio + " and back "
                                    + "comes home " + gained + " stronger, which "
                                    + "is a machine hall that makes torque out "
                                    + "of cogs");
                    check(gained >= -one.stallTorque() - 1.0E-4F,
                            "a source sent out and back comes home weaker than "
                                    + "nothing at all");
                }
            }
        }

        // And the teeth cost something, or the loss is a comment.
        float lossy = LineRules.goingRound(KineticRules.WATER_WHEEL, up, 2);
        check(lossy < 0.0F, "a round trip through two gear stages costs nothing, "
                + "so GEAR_LOSS is a number nothing reads");

        // The teeth are paid both ways. A trip out through some stages and
        // home through the same stages has to cost exactly what going
        // straight through twice as many costs — which is the statement that
        // a ratio round-trips exactly and that the losses compose. A trip
        // that pays the teeth only once is still lossy, so the check above
        // would not notice it, and it would hide a source gaining a little on
        // every stage behind a loss that is charged half as often as it
        // should be.
        for (KineticRules.Source one : kinds) {
            for (float ratio : new float[] {up, down, up * up, -up}) {
                for (int stages = 1; stages <= 4; stages++) {
                    float round = LineRules.goingRound(one, ratio, stages);
                    float straight = one.through(1.0F, 2 * stages).stallTorque()
                            - one.stallTorque();
                    check(Math.abs(round - straight)
                                    <= Math.abs(straight) * 1.0E-3F + 1.0E-4F,
                            "a source sent out through " + ratio + " and back "
                                    + "comes home " + round + " different and "
                                    + "going straight through " + (2 * stages)
                                    + " stages costs " + straight + ", so the "
                                    + "round trip pays the teeth only once");
                }
            }
        }

        // A control: a balance built to be caught, because over twenty
        // thousand healthy halls this rule can only ever answer yes.
        LineRules.Balance crooked = new LineRules.Balance(10.0F, 40.0F, 0.0F);
        check(crooked.apart() > 1.0E-3F,
                "books showing ten pushed against forty drawn are reported as "
                        + "balanced, so the rule that torque is conserved cannot "
                        + "say no");
        LineRules.Balance straight = new LineRules.Balance(10.0F, 4.0F, 6.0F);
        check(straight.apart() < 1.0E-3F,
                "books that add up exactly are reported as out of balance");
        check(new LineRules.Balance(0.0F, 0.0F, 0.0F).apart() == 0.0F,
                "a hall with nothing in it is reported as out of balance");

        System.out.println("  the books: " + built + " halls built at random, "
                + "every one of them pushing exactly what it draws and what is "
                + "idle (worst " + String.format("%.2e", worst)
                + "), and no loop anywhere giving a source back stronger than "
                + "it went out");
    }

    /**
     * Somewhere on a line, through up to six stages of gearing.
     *
     * <p>Both directions and both signs, because a sign error in the
     * reflection is exactly the bug the books above exist to find.
     */
    private static LineRules.At somewhere(java.util.Random random, float up,
                                          float down) {
        float ratio = 1.0F;
        int stages = random.nextInt(7);
        for (int at = 0; at < stages; at++) {
            ratio = LineRules.compose(ratio, random.nextBoolean() ? up : down);
        }
        return new LineRules.At(ratio, stages);
    }

    // ==================================================================

    /**
     * Engines, and the sum they may not get wrong.
     *
     * <p>Age I had four sources and every one of them was free once it was
     * built. A fuel is the thing that makes a machine hall a decision rather
     * than a shape, so: engines that burn something.
     *
     * <p>And an engine may not put more energy into a line than the fuel it
     * burned contains. That is not a balance decision, it is arithmetic, and
     * getting it wrong is a machine hall that makes energy — the same failure
     * as a loop that gains torque, arrived at from the other end.
     */
    private static void theEngines() {
        none(EngineRules.faults(), "the engines");
        none(EngineRules.makingEnergy(EngineRules.ENGINES, EngineRules.FUELS),
                "an engine getting more out of a fuel than the fuel contains");
        none(EngineRules.unburnable(), "a fuel nothing will burn");
        none(EngineRules.faultsInCeiling(EngineRules.EFFICIENCY),
                "the ceiling on an engine is not a ceiling");

        // Every engine on every fuel it takes, counted.
        int pairs = 0;
        for (EngineRules.Engine engine : EngineRules.ENGINES) {
            for (EngineRules.Fuel one : EngineRules.FUELS) {
                if (!engine.accepts(one)) {
                    continue;
                }
                pairs++;
                float got = engine.outOf(one);
                check(got > 0.0F, engine.id() + " gets nothing out of "
                        + one.id());
                check(got <= one.allowed() + 1.0E-3F, engine.id()
                        + " gets " + got + " out of " + one.id()
                        + " and may have " + one.allowed());
                float share = EngineRules.efficiencyOf(engine, one);
                check(share > 0.0F && share < 1.0F, engine.id() + " runs at "
                        + share + " of the energy in " + one.id()
                        + ", and a perfect engine is the shape of a bug rather "
                        + "than of a reward");
                check(share <= EngineRules.EFFICIENCY + 1.0E-4F,
                        engine.id() + " runs at " + share + " and the ceiling "
                                + "is " + EngineRules.EFFICIENCY);
            }
        }
        check(pairs >= 10, "only " + pairs + " engine-and-fuel pair(s) were "
                + "checked, and there are " + EngineRules.ENGINES.size()
                + " engine(s) and " + EngineRules.FUELS.size() + " fuel(s)");

        // An engine drives a line like any other source, and a line with one
        // on it balances its books like any other line.
        for (EngineRules.Engine engine : EngineRules.ENGINES) {
            java.util.List<LineRules.Driven> driving = java.util.List.of(
                    new LineRules.Driven(engine.source(), LineRules.At.ROOT));
            java.util.List<LineRules.Drawn> drawing = java.util.List.of(
                    new LineRules.Drawn(EngineRules.REFINERY_LOAD,
                            LineRules.At.ROOT));
            KineticRules.Shaft shaft = EngineRules.shaftFor(engine);
            check(shaft != null, engine.id() + " turns faster than any shaft in "
                    + "any age survives, so it is a block nobody can use");
            LineRules.Assembly hall = LineRules.assemble(driving, drawing,
                    shaft == null ? KineticRules.Shaft.ASCENDANT : shaft, false);
            check(hall.running(), engine.id() + " will not turn a refinery on "
                    + shaft + ", which is the one thing every engine above the "
                    + "first is for");
            check(!LineRules.assemble(driving, drawing,
                            KineticRules.Shaft.TIMBER, false).running()
                            || shaft == KineticRules.Shaft.TIMBER,
                    engine.id() + " runs on timber and wants " + shaft
                            + ", so the shaft it needs is not the shaft it "
                            + "needs");
            LineRules.Balance books = LineRules.balance(driving, drawing, hall);
            check(books.apart() < 1.0E-3F, engine.id()
                    + "'s line does not balance its books");
        }

        // Each engine names the shaft it needs, and the ladder of engines
        // climbs the ladder of materials with it.
        KineticRules.Shaft wanted = null;
        for (EngineRules.Engine engine : EngineRules.ENGINES) {
            KineticRules.Shaft shaft = EngineRules.shaftFor(engine);
            check(shaft != null, engine.id() + " needs a shaft no age has");
            if (shaft != null && wanted != null) {
                check(shaft.ordinal() >= wanted.ordinal(), engine.id()
                        + " runs on a weaker shaft than the engine below it, so "
                        + "the ladder of engines and the ladder of materials "
                        + "pull against each other");
            }
            wanted = shaft;
        }

        // The ladder: stronger going up, and thirstier with it.
        for (int at = 1; at < EngineRules.ENGINES.size(); at++) {
            EngineRules.Engine one = EngineRules.ENGINES.get(at);
            EngineRules.Engine under = EngineRules.ENGINES.get(at - 1);
            check(one.source().stallTorque() > under.source().stallTorque(),
                    one.id() + " pushes no harder than " + under.id());
            check(one.burning() > under.burning(), one.id()
                    + " is no thirstier than " + under.id()
                    + ", and an engine that is stronger for the same fuel is "
                    + "the whole ladder in one block");
            check(one.takes() > under.takes(), one.id() + " takes no deeper a "
                    + "fuel than " + under.id());
        }
        for (int at = 1; at < EngineRules.FUELS.size(); at++) {
            check(EngineRules.FUELS.get(at).burn()
                            >= EngineRules.FUELS.get(at - 1).burn(),
                    "the fuel ladder runs backwards at "
                            + EngineRules.FUELS.get(at).id());
        }
        none(EngineRules.notWorthRefining(EngineRules.ENGINES),
                "an engine further up the ladder is merely thirstier");

        // Controls, built to be caught.
        EngineRules.Engine greedy = new EngineRules.Engine("control",
                new KineticRules.Source(1024.0F, 1024.0F), 1, 1);
        check(!EngineRules.makingEnergy(java.util.List.of(greedy),
                        EngineRules.FUELS).isEmpty(),
                "an engine that gets a thousand times a lump of coal out of a "
                        + "lump of coal is not reported, so the rule that a "
                        + "machine may not make energy cannot say no");
        check(EngineRules.makingEnergy(
                        java.util.List.of(EngineRules.ENGINES.get(0)),
                        EngineRules.FUELS).isEmpty(),
                "an ordinary engine is reported as making energy");
        check(!EngineRules.faultsInCeiling(1.0F).isEmpty(),
                "an engine allowed every last joule in a fuel is not reported, "
                        + "so the ceiling every other check here measures "
                        + "against could be raised until nothing is asked");
        check(!EngineRules.faultsInCeiling(1.4F).isEmpty(),
                "an engine allowed more energy out than there is in the fuel "
                        + "is not reported");
        check(!EngineRules.faultsInCeiling(0.0F).isEmpty(),
                "an engine allowed nothing at all is not reported");
        check(EngineRules.faultsInCeiling(0.6F).isEmpty(),
                "an ordinary ceiling is reported as faulty");
        check(!EngineRules.faultsInFuels(java.util.List.of(
                        new EngineRules.Fuel("control", 1_600, ""),
                        new EngineRules.Fuel("control_two", 400, "work")))
                        .isEmpty(),
                "a fuel worth less than the one below it is not reported, so "
                        + "the ladder could run backwards and nobody would "
                        + "refine anything");
        check(EngineRules.faultsInFuels(java.util.List.of(
                        new EngineRules.Fuel("control", 400, ""),
                        new EngineRules.Fuel("control_two", 1_600, "work")))
                        .isEmpty(),
                "an ordinary pair of fuels is reported as faulty");
        check(!EngineRules.faultsInEngines(java.util.List.of(
                        new EngineRules.Engine("control",
                                new KineticRules.Source(64.0F, 24.0F), 2, 32),
                        new EngineRules.Engine("control_two",
                                new KineticRules.Source(64.0F, 24.0F), 3, 32)))
                        .isEmpty(),
                "an engine no stronger than the one below it is not reported");
        check(!EngineRules.notWorthRefining(java.util.List.of(
                        EngineRules.ENGINES.get(0), EngineRules.ENGINES.get(0)))
                        .isEmpty(),
                "two engines that get exactly the same out of a fuel are not "
                        + "reported, so the top of the ladder could be merely "
                        + "thirstier");

        java.util.List<String> allowed = new java.util.ArrayList<>();
        try {
            new EngineRules.Fuel("control", 0, "");
            allowed.add("a fuel that does not burn");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new EngineRules.Engine("control",
                    new KineticRules.Source(64.0F, 24.0F), 1, 0);
            allowed.add("an engine that runs on nothing");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the engines allow what they say they forbid: "
                + allowed);

        System.out.println("  the engines: " + EngineRules.ENGINES.size()
                + " engine(s) on a ladder of " + EngineRules.FUELS.size()
                + " fuel(s), " + pairs + " pairing(s), not one of them getting "
                + "more out of a fuel than the fuel contains");
    }

        /**
     * Anchors, and the one thing gearing may never do to one.
     *
     * <p>A machine with a speed floor invites exactly one exploit: gear up
     * into it, so it meets its floor while the hall turns slowly. It does not
     * work, and the reason is the whole model in a sentence — gearing moves
     * power about and does not make any. Swept rather than asserted.
     */
    private static void theAnchors() {
        none(AnchorRules.faults(), "the anchors");

        // Every radius costs the same per chunk, which is what stops one
        // width being the only one worth building.
        float perChunk = AnchorRules.powerPerChunk(0);
        for (int radius = 0; radius <= AnchorRules.WIDEST; radius++) {
            near(AnchorRules.powerPerChunk(radius), perChunk, perChunk * 1.0E-3F,
                    "a chunk at radius " + radius + " costs something other "
                            + "than a chunk at radius 0");
            check(AnchorRules.chunksAt(radius)
                            == AnchorRules.acrossAt(radius) * AnchorRules.acrossAt(radius),
                    "a square of radius " + radius + " is not its width times "
                            + "its width");
            check(AnchorRules.loadAt(radius).draw() > 0.0F
                            && AnchorRules.loadAt(radius).leastRpm() > 0.0F,
                    "an anchor at radius " + radius + " is free");
        }
        check(AnchorRules.chunksAt(0) == 1, "a radius of nothing is not one chunk");
        check(AnchorRules.chunksAt(1) == 9, "a radius of one is not nine chunks");
        check(AnchorRules.chunksAt(2) == 25, "a radius of two is not twenty-five");

        // Gearing may never make an anchor cheaper. Every radius, every
        // ratio either way, every stage count.
        int swept = 0;
        for (int radius = 0; radius <= AnchorRules.WIDEST; radius++) {
            float ungeared = AnchorRules.powerAt(radius);
            for (int step = 1; step <= 160; step++) {
                float ratio = step / 8.0F;
                for (int stages = 0; stages <= 4; stages++) {
                    swept++;
                    float cost = AnchorRules.costAtRoot(radius, ratio, stages);
                    check(cost >= ungeared - ungeared * 1.0E-3F,
                            "an anchor at radius " + radius + " geared by "
                                    + ratio + " through " + stages
                                    + " stage(s) costs " + cost + " and costs "
                                    + ungeared + " bolted straight on, so "
                                    + "gearing has made power rather than "
                                    + "moved it about");
                    if (stages > 0) {
                        check(cost > ungeared,
                                "an anchor " + stages + " gear stage(s) out "
                                        + "costs no more than one bolted "
                                        + "straight on, so the teeth are free");
                    } else {
                        near(cost, ungeared, ungeared * 1.0E-3F,
                                "an anchor geared with no teeth at all costs "
                                        + "something other than what it costs "
                                        + "bolted straight on");
                    }
                }
            }
        }
        check(swept > 1_000, "only " + swept + " gearing(s) were swept");

        // The comparator says something, and says more the more it holds.
        check(AnchorRules.comparator(false, AnchorRules.WIDEST) == 0,
                "an anchor holding nothing reads as holding something");
        int last = 0;
        for (int radius = 0; radius <= AnchorRules.WIDEST; radius++) {
            int here = AnchorRules.comparator(true, radius);
            check(here >= 1 && here <= 15, "an anchor at radius " + radius
                    + " reads " + here + " on a comparator");
            check(here >= last, "a wider anchor reads lower than a narrower one");
            last = here;
        }
        check(AnchorRules.GRACE_TICKS > 0,
                "an anchor lets go the instant the wind turns, so a windmill "
                        + "hall unloads its own farm several times a minute");
        check(AnchorRules.GRACE_TICKS <= 1_200,
                "an anchor holds on for " + AnchorRules.GRACE_TICKS
                        + " ticks after it stops, which is a chunk loader with "
                        + "a long memory rather than a machine");

        // Controls, built to be caught.
        check(!AnchorRules.faultsInAnchors(java.util.List.of(
                        new AnchorRules.Anchor("control", 1,
                                KineticRules.Shaft.STEEL),
                        new AnchorRules.Anchor("control", 2,
                                KineticRules.Shaft.STEEL))).isEmpty(),
                "two anchors under one name are not reported");
        check(!AnchorRules.faultsInAnchors(java.util.List.of(
                        new AnchorRules.Anchor("control", 1,
                                KineticRules.Shaft.STEEL),
                        new AnchorRules.Anchor("control_two", 2,
                                KineticRules.Shaft.TIMBER))).isEmpty(),
                "an anchor made of a metal that shears at the speed it needs "
                        + "is not reported, so it could be a block that breaks "
                        + "itself the first time it works");
        check(!AnchorRules.faultsInAnchors(java.util.List.of(
                        new AnchorRules.Anchor("control", 1,
                                KineticRules.Shaft.STEEL),
                        new AnchorRules.Anchor("control_two", 1,
                                KineticRules.Shaft.STEEL))).isEmpty(),
                "an anchor holding no more than the one below it is not "
                        + "reported");
        check(AnchorRules.faultsInAnchors(AnchorRules.ANCHORS).isEmpty(),
                "the anchors this module ships are reported as faulty");
        check(!AnchorRules.faultsInCost(java.util.List.of(
                        AnchorRules.loadAt(0))).isEmpty(),
                "a roster with one width in it is not reported, so the panel "
                        + "could be a screen with one button");
        check(!AnchorRules.faultsInCost(java.util.List.of(
                        new KineticRules.Load(6.0F, 48.0F),
                        new KineticRules.Load(54.0F, 432.0F),
                        new KineticRules.Load(150.0F, 1200.0F))).isEmpty(),
                "widths priced by their area on both numbers are not "
                        + "reported, so one width could be the only one worth "
                        + "building and the other two decoration");
        check(!AnchorRules.faultsInCost(java.util.List.of(
                        new KineticRules.Load(0.0F, 48.0F),
                        new KineticRules.Load(0.0F, 144.0F))).isEmpty(),
                "an anchor that costs nothing at all is not reported");
        check(AnchorRules.faultsInCost(AnchorRules.everyWidth()).isEmpty(),
                "the widths this module ships are reported as faulty");
        check(!AnchorRules.faultsInReach(java.util.List.of(
                        new AnchorRules.Anchor("control", 2,
                                KineticRules.Shaft.STEEL),
                        new AnchorRules.Anchor("control_two", 2,
                                KineticRules.Shaft.STEEL))).isEmpty(),
                "a first anchor nothing in the age can drive is not reported");
        check(!AnchorRules.faultsInReach(java.util.List.of(
                        new AnchorRules.Anchor("control", 0,
                                KineticRules.Shaft.BRASS),
                        new AnchorRules.Anchor("control_two", 0,
                                KineticRules.Shaft.BRASS))).isEmpty(),
                "a widest anchor one of Age I's own sources turns with no "
                        + "gearing is not reported, so the biggest anchor in "
                        + "the pack could be a block you place and forget");
        check(AnchorRules.drivenBySomething(AnchorRules.loadAt(0)),
                "nothing in this age can drive the cheapest anchor");
        check(!AnchorRules.drivenBySomething(AnchorRules.loadAt(AnchorRules.WIDEST)),
                "one of this age's sources turns the widest anchor on its own");

        java.util.List<String> allowed = new java.util.ArrayList<>();
        try {
            new AnchorRules.Anchor("control", AnchorRules.WIDEST + 1,
                    KineticRules.Shaft.STEEL);
            allowed.add("an anchor wider than this age has");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the anchors allow what they say they forbid: "
                + allowed);

        System.out.println("  the anchors: " + AnchorRules.ANCHORS.size()
                + " of them over " + (AnchorRules.WIDEST + 1) + " width(s), "
                + swept + " gearing(s) swept and not one of them making an "
                + "anchor cheaper than bolting it straight on, at "
                + String.format("%.0f", perChunk) + " torque-rpm a chunk "
                + "whatever width it is set to");
    }

    /**
     * What the machines do, and the one thing none of it may add up to.
     *
     * <p>The roster is a graph and the graph may not have a loop that
     * multiplies. That is checked over the roster as it ships, and then
     * proved able to say yes by handing it graphs built to be caught —
     * because a rule about loops, over a roster with no loop in it that
     * multiplies, is a rule that has only ever answered no.
     */
    private static void theProcessing() {
        none(ProcessRules.faults(), "the processing");
        none(ProcessRules.faultsInEachStep(ProcessRules.EVERYTHING),
                "the recipe roster");
        none(ProcessRules.faultsInCoverage(ProcessRules.EVERYTHING),
                "a machine with nothing to read");
        none(ProcessRules.faultsInLoops(ProcessRules.EVERYTHING,
                        ProcessRules.THE_GAMES_ROUTES, ProcessRules.IN_TAG),
                "a loop that comes back with more than it started with");

        check(ProcessRules.EVERYTHING.size()
                        == ProcessRules.MILLING.size() + ProcessRules.PRESSING.size()
                        + ProcessRules.SAWING.size() + ProcessRules.MIXING.size(),
                "the roster walked is not the four rosters added up");
        int walked = 0;
        for (String machine : ProcessRules.MACHINES) {
            int here = ProcessRules.on(machine).size();
            check(here > 0, machine + " has no recipes");
            walked += here;
        }
        check(walked == ProcessRules.EVERYTHING.size(),
                "walking the machines found " + walked + " recipe(s) and the "
                        + "roster has " + ProcessRules.EVERYTHING.size());

        // Every recipe runs at a speed some source in this age can reach, or
        // it is a recipe that exists and cannot be made.
        for (ProcessRules.Step one : ProcessRules.EVERYTHING) {
            boolean reachable = false;
            for (KineticRules.Source source : SeamRules.everySource()) {
                reachable |= KineticRules.pace(source.freeSpeed()).ordinal()
                        >= one.pace().ordinal();
            }
            check(reachable, one.name() + " wants a " + one.pace()
                    + " line and nothing in this age turns that fast");
            check(one.ticks() > 0 && one.ticks() <= 1_200, one.name()
                    + " takes " + one.ticks() + " ticks at the reference speed");
        }

        // The loop rule, proved able to say yes. Three shapes: a two-step
        // loop that doubles, a three-step loop that doubles round the houses,
        // and a route of the game's own that closes one.
        ProcessRules.Step there = new ProcessRules.Step("milling", "control_a",
                "control:one", "control:two", 2, 100, KineticRules.Pace.SLOW);
        ProcessRules.Step back = new ProcessRules.Step("pressing", "control_b",
                "control:two", "control:one", 1, 100, KineticRules.Pace.SLOW);
        check(!ProcessRules.faultsInLoops(java.util.List.of(there, back),
                        java.util.List.of(), java.util.List.of()).isEmpty(),
                "a two-step loop that comes back with twice what went in is "
                        + "not reported, so the rule that nothing multiplies "
                        + "cannot say no");
        ProcessRules.Step evens = new ProcessRules.Step("pressing", "control_c",
                "control:two", "control:one", 1, 100, KineticRules.Pace.SLOW);
        check(ProcessRules.faultsInLoops(java.util.List.of(
                        new ProcessRules.Step("milling", "control_a",
                                "control:one", "control:two", 1, 100,
                                KineticRules.Pace.SLOW), evens),
                        java.util.List.of(), java.util.List.of()).isEmpty(),
                "a loop that comes back with exactly what went in is reported "
                        + "as multiplying, so the rule is off by the one case "
                        + "three of the game's own routes sit on");
        check(!ProcessRules.faultsInLoops(java.util.List.of(
                        new ProcessRules.Step("milling", "a", "control:one",
                                "control:two", 3, 100, KineticRules.Pace.SLOW),
                        new ProcessRules.Step("sawing", "b", "control:two",
                                "control:three", 1, 100, KineticRules.Pace.SLOW),
                        new ProcessRules.Step("pressing", "c", "control:three",
                                "control:one", 1, 100, KineticRules.Pace.SLOW)),
                        java.util.List.of(), java.util.List.of()).isEmpty(),
                "a three-step loop that trebles is not reported");
        check(!ProcessRules.faultsInLoops(java.util.List.of(
                        new ProcessRules.Step("milling", "a", "control:one",
                                "control:two", 2, 100, KineticRules.Pace.SLOW)),
                        java.util.List.of(new ProcessRules.Route(
                                "control:back", "control:two", 1,
                                "control:one", 1)),
                        java.util.List.of()).isEmpty(),
                "a loop closed by one of the game's own recipes is not "
                        + "reported, so the game's half of the graph could be "
                        + "dropped with every check still green");
        check(!ProcessRules.faultsInLoops(java.util.List.of(
                        new ProcessRules.Step("sawing", "b", "#control:tag",
                                "control:two", 2, 100, KineticRules.Pace.SLOW)),
                        java.util.List.of(),
                        java.util.List.of(new ProcessRules.Membership(
                                "control:two", "#control:tag"))).isEmpty(),
                "a loop closed through a tag membership is not reported, so "
                        + "the tag half of the graph could be dropped with "
                        + "every check still green");
        check(ProcessRules.faultsInLoops(java.util.List.of(
                        new ProcessRules.Step("sawing", "b", "#control:tag",
                                "control:two", 1, 100, KineticRules.Pace.SLOW)),
                        java.util.List.of(),
                        java.util.List.of(new ProcessRules.Membership(
                                "control:two", "#control:tag"))).isEmpty(),
                "a tag a recipe gives exactly one member of is reported as a "
                        + "loop that multiplies");
        check(ProcessRules.faultsInLoops(java.util.List.of(there),
                        java.util.List.of(), java.util.List.of()).isEmpty(),
                "a roster with no loop in it at all is reported as looping");

        // And the roster rule, likewise.
        check(!ProcessRules.faultsInEachStep(java.util.List.of(
                        new ProcessRules.Step("milling", "control", "a", "b",
                                1, 100, KineticRules.Pace.SLOW),
                        new ProcessRules.Step("milling", "control", "c", "d",
                                1, 100, KineticRules.Pace.SLOW))).isEmpty(),
                "two recipes on one machine under one name are not reported");
        check(!ProcessRules.faultsInEachStep(java.util.List.of(
                        new ProcessRules.Step("grinding", "control", "a", "b",
                                1, 100, KineticRules.Pace.SLOW))).isEmpty(),
                "a recipe read by a machine that does not exist is not "
                        + "reported");
        check(!ProcessRules.faultsInEachStep(java.util.List.of(
                        new ProcessRules.Step("milling", "control", "a", "a",
                                1, 100, KineticRules.Pace.SLOW))).isEmpty(),
                "a recipe that turns a thing into itself is not reported");
        check(!ProcessRules.faultsInEachStep(java.util.List.of(
                        new ProcessRules.Step("milling", "control", "a", "b",
                                1, 100, KineticRules.Pace.STOPPED))).isEmpty(),
                "a recipe that runs on a stopped line is not reported");
        check(ProcessRules.faultsInEachStep(ProcessRules.EVERYTHING).isEmpty(),
                "the roster this module ships is reported as faulty");
        check(ProcessRules.faultsInCoverage(ProcessRules.EVERYTHING).isEmpty(),
                "the roster this module ships leaves a machine with nothing "
                        + "to read");
        check(!ProcessRules.faultsInCoverage(java.util.List.of(
                        new ProcessRules.Step("milling", "control", "a", "b",
                                1, 100, KineticRules.Pace.SLOW))).isEmpty(),
                "a roster that gives three of the four machines nothing to "
                        + "read is not reported");

        java.util.List<String> allowed = new java.util.ArrayList<>();
        try {
            new ProcessRules.Step("milling", "control", "a", "b", 0, 100,
                    KineticRules.Pace.SLOW);
            allowed.add("a recipe that makes nothing");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ProcessRules.Step("milling", "control", "a", "b", 1, 0,
                    KineticRules.Pace.SLOW);
            allowed.add("a recipe that takes no time");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the roster allows what it says it forbids: "
                + allowed);

        System.out.println("  the processing: " + ProcessRules.EVERYTHING.size()
                + " recipe(s) across " + ProcessRules.MACHINES.size()
                + " machine(s), joined to " + ProcessRules.THE_GAMES_ROUTES.size()
                + " of the game's own recipes, and not one loop anywhere in "
                + "the graph that comes back with more than it started with");
    }

    private static void edges() {
        KineticRules.Shaft timber = KineticRules.Shaft.TIMBER;
        KineticRules.Reading nothing = KineticRules.settle(List.of(), List.of(), timber);
        check(nothing.rpm() == 0.0F && !nothing.stalled(),
                "an empty line is either turning or stalled; it is neither");

        KineticRules.Reading loadsOnly =
                KineticRules.settle(List.of(), loads(KineticRules.MILLSTONE, 3), timber);
        check(loadsOnly.stalled(), "machines with nothing driving them are not stalled");

        boolean refused = false;
        try {
            new KineticRules.Source(0.0F, 10.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a source with no free speed is accepted, and divides by zero");

        refused = false;
        try {
            new KineticRules.Load(-1.0F, 0.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a load with negative draw is accepted, and is free power");

        refused = false;
        try {
            KineticRules.ratio(0, 8);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a gear with no teeth is accepted");

        // The preview a wrench shows before a machine is placed must agree
        // with what actually happens when it is.
        List<KineticRules.Source> wheel = sources(KineticRules.WATER_WHEEL, 1);
        List<KineticRules.Load> two = loads(KineticRules.MILLSTONE, 2);
        KineticRules.Reading predicted =
                KineticRules.with(wheel, two, timber, KineticRules.MILLSTONE);
        KineticRules.Reading actual =
                KineticRules.settle(wheel, loads(KineticRules.MILLSTONE, 3), timber);
        near(predicted.rpm(), actual.rpm(), 0.0001F,
                "the preview disagrees with what placing the machine does");
        check(two.size() == 2, "the preview modified the line it was asked about");
    }
}
