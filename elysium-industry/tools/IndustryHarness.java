package com.elysium.industry.rules;

import com.elysium.kinetics.rules.KineticRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The second age, executed.
 *
 * <p>The claims worth pinning are the ones that make Age II a different game
 * from Age I rather than a bigger one: that a short grid shares rather than
 * stops, that a wire is a real limit and the reason voltage exists, that
 * distance costs, and that the two ages join in both directions without either
 * making the other pointless.
 */
public final class IndustryHarness {

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
        voltage();
        wires();
        theGrid();
        theShare();
        theBridge();
        theShaft();
        theAge();
        theWork();
        theReserve();
        theLoop();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a grid shares out what it has instead of "
                + "stopping, a wire is a real limit and the reason voltage exists, distance "
                + "costs, and the two ages join both ways without either making the other "
                + "pointless");
    }

    private static List<IndustryRules.Generator> generators(IndustryRules.Generator one, int count) {
        List<IndustryRules.Generator> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(one);
        }
        return list;
    }

    private static List<IndustryRules.Machine> machines(IndustryRules.Machine one, int count) {
        List<IndustryRules.Machine> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(one);
        }
        return list;
    }

    // ------------------------------------------------------------------

    private static void voltage() {
        IndustryRules.Voltage[] tiers = IndustryRules.Voltage.values();
        for (int i = 1; i < tiers.length; i++) {
            check(tiers[i].capacity() > tiers[i - 1].capacity(),
                    tiers[i] + " carries no more than " + tiers[i - 1]);
            check(tiers[i].lossPerBlock() < tiers[i - 1].lossPerBlock(),
                    tiers[i] + " loses as much as " + tiers[i - 1] + " per block, so "
                            + "stepping up buys only headroom and never distance");
            near(tiers[i].capacity(), tiers[i - 1].capacity() * 4.0F, 1.0F,
                    "each tier is four times the one below");
        }
        check(tiers[tiers.length - 1].stepUp() == tiers[tiers.length - 1],
                "the top tier steps up to something that does not exist");
        check(IndustryRules.Voltage.LOW.stepUp() == IndustryRules.Voltage.MEDIUM,
                "stepping up from low does not reach medium");
        check(IndustryRules.MOST_FE == tiers[tiers.length - 1].capacity(),
                "the ceiling is not the top tier's capacity, so there are two ceilings");
    }

    private static void wires() {
        IndustryRules.Voltage low = IndustryRules.Voltage.LOW;
        check(!IndustryRules.burns(low, low.capacity()),
                "a wire burns at exactly its rating, so the rating is not the rating");
        check(IndustryRules.burns(low, low.capacity() + 1),
                "a wire survives above its rating");
        check(!IndustryRules.burns(low, 0.0F), "an idle wire burns");

        near(IndustryRules.delivered(low, 100.0F, 0), 100.0F, 0.001F,
                "a wire of no length loses something");
        check(IndustryRules.delivered(low, 100.0F, 10) < 100.0F,
                "distance costs nothing, so a generator may as well be a mile away");
        check(IndustryRules.delivered(low, 100.0F, 20)
                        < IndustryRules.delivered(low, 100.0F, 10),
                "twice the distance costs no more than half of it");
        check(IndustryRules.delivered(low, 100.0F, 100000) == 0.0F,
                "a long enough wire delivers negative power");
        check(IndustryRules.delivered(IndustryRules.Voltage.HIGH, 100.0F, 50)
                        > IndustryRules.delivered(low, 100.0F, 50),
                "a higher tier does not carry further, so there is no reason to build one "
                        + "except headroom");

        for (IndustryRules.Voltage tier : IndustryRules.Voltage.values()) {
            int halfway = IndustryRules.halfwayAt(tier);
            near(IndustryRules.delivered(tier, 100.0F, halfway), 50.0F, 1.0F,
                    tier + "'s stated halfway distance does not halve what it carries");
        }

        check(!IndustryRules.destroys(low, IndustryRules.Voltage.HIGH),
                "a low wire destroys a machine built for more, so under-voltage is fatal");
        check(IndustryRules.destroys(IndustryRules.Voltage.HIGH, low),
                "a high wire does not destroy a machine built for less, so a transformer "
                        + "has no purpose");
        check(!IndustryRules.destroys(low, low), "a wire destroys a machine of its own tier");
    }

    private static void theGrid() {
        IndustryRules.Voltage low = IndustryRules.Voltage.LOW;
        IndustryRules.Reading idle = IndustryRules.settle(
                generators(IndustryRules.STEAM_TURBINE, 1), List.of(), low);
        check(idle.running(), "a grid with nothing drawing on it is not running");
        near(idle.share(), 1.0F, 0.001F, "an unloaded grid does not run at full speed");
        near(idle.effort(), 0.0F, 0.001F, "an unloaded grid reports effort");

        IndustryRules.Reading nothing = IndustryRules.settle(
                List.of(), machines(IndustryRules.CRUSHER, 1), low);
        check(!nothing.running(), "a machine with nothing feeding it is running");
        near(nothing.share(), 0.0F, 0.001F, "a grid with no supply gives its machines a share");

        // A starved grid does not burn its wire: what flows is the smaller of
        // what is asked and what there is.
        IndustryRules.Reading starved = IndustryRules.settle(
                generators(IndustryRules.STEAM_TURBINE, 1),
                machines(IndustryRules.ARC_FURNACE, 4), low);
        check(!starved.burned(),
                "a grid that wants far more than it has burns its wire, so asking for too "
                        + "much destroys the thing that was coping");
        check(starved.running(), "a starved grid stops instead of crawling");

        IndustryRules.Reading overloaded = IndustryRules.settle(
                generators(IndustryRules.DIESEL_ENGINE, 1),
                machines(IndustryRules.ARC_FURNACE, 1), low);
        check(overloaded.burned(),
                "a diesel engine's output goes down a low wire without burning it, so the "
                        + "voltage tiers are decoration");
        check(!overloaded.running(), "a burned grid is still running");
        near(overloaded.share(), 0.0F, 0.001F, "a burned grid still feeds its machines");
    }

    /**
     * The claim Age II is built on: short of power, everything crawls.
     */
    private static void theShare() {
        IndustryRules.Voltage medium = IndustryRules.Voltage.MEDIUM;
        List<IndustryRules.Generator> one = generators(IndustryRules.DIESEL_ENGINE, 1);

        float share = 1.0F;
        boolean sawAWarning = false;
        for (int count = 0; count <= 8; count++) {
            IndustryRules.Reading reading =
                    IndustryRules.settle(one, machines(IndustryRules.SEPARATOR, count), medium);
            check(reading.share() <= share + 0.0001F,
                    "adding a machine raised everyone's share at " + count);
            share = reading.share();
            if (!reading.burned() && share > 0.0F && share < 1.0F) {
                sawAWarning = true;
            }
            if (count > 0 && !reading.burned()) {
                check(reading.running(),
                        "with " + count + " machines nothing runs at all, so a grid is a "
                                + "light switch after all");
            }
        }
        check(sawAWarning,
                "a grid is never partly fed: it either has enough or nothing works, which "
                        + "is exactly the behaviour this model exists to avoid");

        // Two machines and half the power is half speed each, not one machine
        // at full speed and one stopped.
        IndustryRules.Reading half = IndustryRules.settle(
                List.of(new IndustryRules.Generator(128.0F, medium)),
                machines(IndustryRules.SEPARATOR, 1), medium);
        near(half.share(), 0.5F, 0.001F,
                "a machine given half its draw does not run at half speed");
    }

    private static void theBridge() {
        near(IndustryRules.alternatorOutput(IndustryRules.ALTERNATOR_LEAST_RPM - 1.0F), 0.0F,
                0.001F, "an alternator below its least speed still makes power");
        check(IndustryRules.alternatorOutput(128.0F) > 0.0F,
                "an alternator at a proper speed makes nothing");
        near(IndustryRules.alternatorOutput(128.0F),
                KineticRules.dynamoOutput(128.0F) * IndustryRules.ALTERNATOR_OVER_DYNAMO,
                0.01F, "an alternator is not the stated multiple of a dynamo");
        check(IndustryRules.ALTERNATOR_OVER_DYNAMO > 1.0F,
                "an alternator is no better than Age I's dynamo, so Age II's own generator "
                        + "is a sidegrade");
        check(IndustryRules.ALTERNATOR_LEAST_RPM > KineticRules.DYNAMO.leastRpm(),
                "an alternator is no harder to spin than a dynamo, so the dynamo is "
                        + "obsolete rather than early");

        check(IndustryRules.motorDraw(0.0F, 10.0F) == 0.0F, "a stopped motor draws power");
        check(IndustryRules.motorDraw(100.0F, 10.0F) > IndustryRules.motorDraw(50.0F, 10.0F),
                "a motor turning faster draws no more");
        check(IndustryRules.motorDraw(100.0F, 20.0F) > IndustryRules.motorDraw(100.0F, 10.0F),
                "a motor under more load draws no more");
        check(IndustryRules.MOTOR_EFFICIENCY < 1.0F,
                "a motor loses nothing, so rotation to power and back is free and the two "
                        + "ages can be looped for infinite energy");

        KineticRules.Source fed = IndustryRules.motorAsSource(IndustryRules.MOTOR_RATED_FE);
        KineticRules.Source starved = IndustryRules.motorAsSource(IndustryRules.MOTOR_RATED_FE / 4);
        check(fed.stallTorque() > starved.stallTorque(),
                "a motor given less power is no weaker");
        near(fed.freeSpeed(), starved.freeSpeed(), 0.001F,
                "a motor given less power runs slower rather than weaker, which is a shaft "
                        + "and not a motor");
        check(fed.stallTorque() > KineticRules.WATER_WHEEL.stallTorque(),
                "a motor is weaker than a water wheel, so electrifying a mill is a "
                        + "downgrade");

        // The loop has to lose. A motor driving an alternator must not pay for
        // itself, or the whole energy economy is optional.
        float spent = IndustryRules.motorDraw(IndustryRules.MOTOR_FREE_RPM / 2,
                IndustryRules.MOTOR_STALL_TORQUE / 2);
        float made = IndustryRules.alternatorOutput(IndustryRules.MOTOR_FREE_RPM / 2);
        check(made < spent,
                "a motor turning an alternator makes " + made + " from " + spent
                        + ", which is a perpetual motion machine");
    }

    private static void theShaft() {
        check(IndustryRules.shaftShare(IndustryRules.SHAFT_LEAST_RPM - 1.0F) == 0.0F,
                "a shaft too slow for an Age II machine drives it anyway");
        near(IndustryRules.shaftShare(IndustryRules.SHAFT_LEAST_RPM),
                IndustryRules.ON_A_SHAFT, 0.001F,
                "a shaft at the stated speed does not drive the machine");
        check(IndustryRules.ON_A_SHAFT > 0.0F && IndustryRules.ON_A_SHAFT < 1.0F,
                "an Age II machine on a shaft runs at " + IndustryRules.ON_A_SHAFT
                        + " of full speed; it has to be a penalty and not a refusal, and "
                        + "not free either");
        check(IndustryRules.SHAFT_LEAST_RPM > KineticRules.Shaft.TIMBER.rating(),
                "a timber shaft can drive an Age II machine, so the age below has no "
                        + "reason to upgrade its shafts");
    }

    /**
     * The age as a ladder, the same way Age I is one.
     */
    private static void theAge() {
        IndustryRules.Voltage low = IndustryRules.Voltage.LOW;
        IndustryRules.Voltage medium = IndustryRules.Voltage.MEDIUM;

        // A turbine runs the first two machines and nothing more.
        IndustryRules.Reading first = IndustryRules.settle(
                generators(IndustryRules.STEAM_TURBINE, 1),
                List.of(IndustryRules.CRUSHER), low);
        near(first.share(), 1.0F, 0.001F, "one turbine cannot run one crusher");
        IndustryRules.Reading both = IndustryRules.settle(
                generators(IndustryRules.STEAM_TURBINE, 1),
                List.of(IndustryRules.CRUSHER, IndustryRules.ROLLING_MILL), low);
        check(both.share() < 1.0F,
                "one turbine runs a crusher and a rolling mill at full speed, so the first "
                        + "thing a player builds is already enough");

        // The arc furnace is the lesson: it cannot be fed down a low wire at
        // all, however many turbines are stacked behind it.
        boolean everOnLow = false;
        for (int count = 1; count <= 12; count++) {
            IndustryRules.Reading reading = IndustryRules.settle(
                    generators(IndustryRules.STEAM_TURBINE, count),
                    List.of(IndustryRules.ARC_FURNACE), low);
            if (reading.running() && reading.share() >= 1.0F) {
                everOnLow = true;
            }
        }
        check(!everOnLow,
                "an arc furnace can be run off a low wire given enough turbines, so the "
                        + "step up to medium is optional and nobody builds a transformer");

        IndustryRules.Reading onMedium = IndustryRules.settle(
                generators(IndustryRules.DIESEL_ENGINE, 2),
                List.of(IndustryRules.ARC_FURNACE), medium);
        check(onMedium.running() && onMedium.share() >= 1.0F,
                "two diesel engines on a medium wire will not run one arc furnace, so the "
                        + "lesson has no answer");

        check(IndustryRules.SEPARATOR_MULTIPLIER == 2,
                "Age II does not double ore, which is the spine's whole reason for the age");
        check(IndustryRules.DIESEL_ENGINE.output() > IndustryRules.STEAM_TURBINE.output(),
                "a diesel engine is no better than a steam turbine");
        check(IndustryRules.DIESEL_ENGINE.voltage().ordinal()
                        > IndustryRules.STEAM_TURBINE.voltage().ordinal(),
                "a diesel engine runs on the same wire as a turbine, so its output has "
                        + "nowhere it must go");
        check(IndustryRules.ARC_FURNACE.draw() > IndustryRules.CRUSHER.draw() * 4,
                "the arc furnace is not the expensive machine, so nothing in Age II "
                        + "creates the pressure to build a real grid");
    }

    // ==================================================================
    // How long a job takes
    // ==================================================================

    /**
     * Work, and the countdown that claims to describe it.
     *
     * A machine counts work and a gauge counts seconds, and the two are
     * written separately, so the only thing keeping them honest is running one
     * against the other.
     */
    private static void theWork() {
        check(IndustryRules.workPerTick(1.0F) == IndustryRules.WORK_FULL,
                "a machine with everything it wants is not working at full speed");
        check(IndustryRules.workPerTick(0.0F) == 0,
                "a machine on a dead grid still makes progress");
        check(IndustryRules.workPerTick(2.0F) == IndustryRules.WORK_FULL,
                "a grid with more than it needs runs its machines faster than full, so "
                        + "over-building is a speed upgrade nobody documented");
        check(IndustryRules.workPerTick(0.0001F) >= 1,
                "a machine on a nearly dead grid makes no progress at all, so a "
                        + "brown-out looks like a breakage");

        // The countdown a player is shown has to be the one the machine keeps.
        float[] shares = {1.0F, 0.75F, 0.5F, 0.33F, 0.1F, 0.02F};
        int[] jobs = {1, 7, 60, 120, 200, 400};
        for (float share : shares) {
            for (int ticks : jobs) {
                int counted = 0;
                int work = IndustryRules.workFor(ticks);
                int per = IndustryRules.workPerTick(share);
                int done = 0;
                while (done < work) {
                    done += per;
                    counted++;
                }
                check(counted == IndustryRules.ticksAtShare(ticks, share),
                        "a " + ticks + "-tick job at share " + share + " actually takes "
                                + counted + " ticks, but the readout says "
                                + IndustryRules.ticksAtShare(ticks, share));
            }
        }
        // The number in a recipe file has to mean something on its own, or a
        // recipe author is tuning against a scale nobody wrote down.
        for (int ticks : jobs) {
            check(IndustryRules.ticksAtShare(ticks, 1.0F) == ticks,
                    "a " + ticks + "-tick job at full share takes "
                            + IndustryRules.ticksAtShare(ticks, 1.0F) + " ticks, so the "
                            + "number in a recipe file means nothing by itself");
        }
        check(IndustryRules.ticksAtShare(100, 0.5F) > IndustryRules.ticksAtShare(100, 1.0F),
                "half a share is not slower than a whole one, so a starved grid costs "
                        + "a player nothing");
        check(IndustryRules.ticksAtShare(100, 0.0F) == Integer.MAX_VALUE,
                "a job on a dead grid finishes");
    }

    // ==================================================================
    // Lending and borrowing
    // ==================================================================

    /**
     * The shape Age II knows about and ships nothing that does.
     *
     * <p>Age III's cells join this grid rather than running a second one, so
     * the arithmetic of who lends, how much, and whether the wire survives it
     * is here and is executed here. What a cell is, how much it holds and what
     * it is made of are not, and this file has no word for any of them.
     */
    private static void theReserve() {
        IndustryRules.Voltage big = IndustryRules.Voltage.EXTREME;
        List<IndustryRules.Generator> none = List.of();
        List<IndustryRules.Machine> wanting = List.of(new IndustryRules.Machine(300.0F,
                big));
        List<IndustryRules.Generator> plenty = List.of(new IndustryRules.Generator(500.0F,
                big));
        List<IndustryRules.Machine> little = List.of(new IndustryRules.Machine(100.0F,
                big));

        // A grid with no reserve on it settles exactly as it always did.
        for (float supply : new float[] {0.0F, 100.0F, 300.0F, 900.0F}) {
            List<IndustryRules.Generator> some =
                    List.of(new IndustryRules.Generator(supply, big));
            IndustryRules.Flow flow = IndustryRules.settle(some, wanting, big,
                    IndustryRules.Reserve.NOTHING);
            IndustryRules.Reading plain = IndustryRules.settle(some, wanting, big);
            near(flow.grid().share(), plain.share(), 0.0001F,
                    "a grid with nothing on it that stores power settles differently "
                            + "from the way it did before storage existed");
            check(!flow.charging() && !flow.draining(),
                    "a grid with no reserve on it moved power into or out of one");
        }

        IndustryRules.Reserve bank = new IndustryRules.Reserve(256.0F, 256.0F);

        // Short: it borrows, and only what it is short by.
        IndustryRules.Flow borrowing = IndustryRules.settle(none, wanting, big, bank);
        near(borrowing.fromReserve(), 256.0F, 0.001F,
                "a grid short by three hundred with two hundred and fifty-six to "
                        + "borrow did not borrow all of it");
        check(!borrowing.charging(), "a grid that is borrowing is also lending");
        check(borrowing.grid().share() < 1.0F,
                "a reserve that cannot cover the shortfall still runs the grid at full "
                        + "speed");

        IndustryRules.Flow nearlyCovered = IndustryRules.settle(
                List.of(new IndustryRules.Generator(200.0F, big)), wanting, big, bank);
        near(nearlyCovered.fromReserve(), 100.0F, 0.001F,
                "a grid borrows more than it is short by, which is how a battery fills "
                        + "itself");
        near(nearlyCovered.grid().share(), 1.0F, 0.001F,
                "a grid whose reserve covered the shortfall does not run at full speed");

        // Long: it lends, and only what is spare.
        IndustryRules.Flow lending = IndustryRules.settle(plenty, little, big, bank);
        near(lending.toReserve(), 256.0F, 0.001F,
                "a grid with four hundred spare and room for two hundred and fifty-six "
                        + "stored something else");
        check(!lending.draining(), "a grid that is lending is also borrowing");

        // Level: nothing moves.
        IndustryRules.Flow level = IndustryRules.settle(
                List.of(new IndustryRules.Generator(100.0F, big)), little, big, bank);
        check(!level.charging() && !level.draining(),
                "an exactly fed grid moves power in and out of storage anyway");

        // Never both, over a sweep.
        boolean bothWays = false;
        boolean borrowedTooMuch = false;
        boolean lentTooMuch = false;
        for (int supplyStep = 0; supplyStep <= 40; supplyStep++) {
            for (int demandStep = 0; demandStep <= 40; demandStep++) {
                float supply = supplyStep * 25.0F;
                float demand = demandStep * 25.0F;
                IndustryRules.Flow flow = IndustryRules.settle(
                        List.of(new IndustryRules.Generator(supply, big)),
                        List.of(new IndustryRules.Machine(demand, big)), big, bank);
                if (flow.charging() && flow.draining()) {
                    bothWays = true;
                }
                if (flow.fromReserve() > Math.max(0.0F, demand - supply) + 0.001F
                        || flow.fromReserve() > bank.couldGive() + 0.001F) {
                    borrowedTooMuch = true;
                }
                if (flow.toReserve() > Math.max(0.0F, supply - demand) + 0.001F
                        || flow.toReserve() > bank.couldTake() + 0.001F) {
                    lentTooMuch = true;
                }
            }
        }
        check(!bothWays, "a grid lent and borrowed in the same tick, which is a loop");
        check(!borrowedTooMuch, "a grid borrowed more than it was short by, or more than "
                + "the reserve could give");
        check(!lentTooMuch, "a grid stored more than it had spare, or more than the "
                + "reserve could take");

        // A battery does not make a wire safe.
        IndustryRules.Voltage thin = IndustryRules.Voltage.LOW;
        int over = thin.capacity() * 2;
        IndustryRules.Reserve wide = new IndustryRules.Reserve(over, over);
        IndustryRules.Flow stuffing = IndustryRules.settle(
                List.of(new IndustryRules.Generator(over, thin)), List.of(), thin, wide);
        check(stuffing.grid().burned(),
                "a surplus far past what the wire carries does not burn it as long as it "
                        + "is on its way into storage");
        IndustryRules.Flow emptying = IndustryRules.settle(List.of(),
                List.of(new IndustryRules.Machine(over, thin)), thin, wide);
        check(emptying.grid().burned(),
                "a reserve discharging far past what the wire carries does not burn it");
        IndustryRules.Flow gentle = IndustryRules.settle(
                List.of(new IndustryRules.Generator(100.0F, thin)),
                List.of(new IndustryRules.Machine(100.0F, thin)), thin, wide);
        check(!gentle.grid().burned(),
                "a wire well within its rating burns because there is a reserve on the "
                        + "grid");

        boolean refused = false;
        try {
            new IndustryRules.Reserve(-1.0F, 0.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a reserve that lends a negative amount can be declared");
    }

    // ==================================================================
    // The loop
    // ==================================================================

    /**
     * A motor turning an alternator turning a motor.
     *
     * The one bug in a system like this that ruins everything downstream of
     * it, because every recipe cost in four ages is priced against energy
     * being scarce. It is closed in arithmetic rather than by a rule, so the
     * arithmetic is what gets run: the closed form, and then a sweep over
     * every arrangement a player could actually build, to confirm that none of
     * them beats it.
     */
    private static void theLoop() {
        float best = IndustryRules.roundTripBest();
        check(best < 1.0F,
                "a motor driving alternators returns " + best + " of what it drew, so "
                        + "the two ages are a free energy loop");
        check(best > 0.0F,
                "the loop returns nothing at all, so the motor and the alternator cannot "
                        + "both be worth building");

        float highest = 0.0F;
        String at = "nothing";
        for (int motors = 1; motors <= 8; motors++) {
            for (int alternators = 1; alternators <= 8; alternators++) {
                for (int step = 1; step <= 160; step++) {
                    float ratio = step / 20.0F;
                    float got = IndustryRules.roundTrip(motors, alternators, ratio);
                    if (got > highest) {
                        highest = got;
                        at = motors + " motor(s), " + alternators + " alternator(s) at "
                                + ratio;
                    }
                }
            }
        }
        check(highest < 1.0F,
                "an arrangement of " + at + " returns " + highest + " of what it drew");
        check(highest <= best + 0.0005F,
                "an arrangement of " + at + " returns " + highest + ", which beats the "
                        + "closed form's " + best + " — the formula is wrong, not the game");
        check(highest > best - 0.01F,
                "the closed form claims " + best + " is reachable but the best real "
                        + "arrangement only manages " + highest);

        // Age II machinery on an Age I line: a penalty, priced off the same
        // number the wire uses, so retuning one half cannot drift from the other.
        check(IndustryRules.asLoad(IndustryRules.ARC_FURNACE).draw()
                        > IndustryRules.asLoad(IndustryRules.CRUSHER).draw(),
                "an arc furnace is no heavier on a shaft than a crusher, so the ladder "
                        + "inside the age stops existing the moment you use a shaft");
        for (IndustryRules.Machine each : List.of(IndustryRules.CRUSHER,
                IndustryRules.ROLLING_MILL, IndustryRules.ARC_FURNACE,
                IndustryRules.SEPARATOR)) {
            near(IndustryRules.asLoad(each).draw(),
                    each.draw() / IndustryRules.SHAFT_FE_PER_TORQUE, 0.001F,
                    "a machine's draw on a shaft is not its draw on a wire at the "
                            + "agreed exchange rate");
            check(IndustryRules.asLoad(each).leastRpm() == IndustryRules.SHAFT_LEAST_RPM,
                    "a machine wants its own speed off a shaft, so the fallback route "
                            + "has four things to remember rather than one");
        }

        check(IndustryRules.DIESEL_ENGINE.output()
                        == IndustryRules.STEAM_TURBINE.output() * IndustryRules.DIESEL_HASTE,
                "a diesel engine is not exactly " + IndustryRules.DIESEL_HASTE
                        + " turbines, so the fuel it burns at that rate buys more or less "
                        + "power than four turbines would and the rung is not a rung");
        check(IndustryRules.DIESEL_HASTE > 1,
                "a diesel engine is no hastier than a turbine");
    }
}
