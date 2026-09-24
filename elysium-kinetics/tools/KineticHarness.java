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
        theTree();
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
