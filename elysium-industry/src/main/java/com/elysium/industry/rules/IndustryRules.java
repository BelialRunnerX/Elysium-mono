package com.elysium.industry.rules;

import com.elysium.kinetics.rules.KineticRules;

import java.util.List;

/**
 * The second age, as arithmetic.
 *
 * <h2>How Age II differs from Age I, in one sentence each</h2>
 *
 * <b>Age I: everything slows together.</b> A line is rigidly coupled, so
 * hanging another millstone on it drags the whole hall down and the fussiest
 * machine stops first.
 *
 * <p><b>Age II: everything gets a share.</b> A grid is not coupled, it is
 * divided. Ask it for more than it has and every machine on it runs at the
 * same fraction of full speed — none of them stops, all of them crawl. That is
 * the difference between a shaft and a wire, and it is the whole reason the
 * second age feels like a different game rather than a bigger one.
 *
 * <p>Both of those are the same design decision made twice: degrade, do not
 * switch off. Create's network flips to OVERSTRESSED; Immersive Engineering's
 * machines simply stop. Ours get slower, visibly, with a number you can read
 * before anything has gone wrong.
 *
 * <h2>What a wire is</h2>
 *
 * A wire has a <b>capacity</b> and a <b>loss</b>. Push more through it than it
 * carries and it burns out — the Age II answer to a timber shaft shearing, and
 * the reason voltage tiers exist rather than being a number on a tooltip. Push
 * anything through it at all and some of it is lost per block, so where the
 * generator sits is a decision, exactly as where a machine sits was in Age I.
 *
 * <h2>Where the two ages meet</h2>
 *
 * Two machines, and they are inverses. An <b>alternator</b> turns rotation
 * into Forge Energy properly, where Age I's dynamo did it badly. A <b>motor</b>
 * turns Forge Energy back into rotation, which is what finally lets an Age I
 * machine hall be driven by something other than water. And every Age II
 * machine will accept a shaft instead of a wire at a penalty, so a player who
 * has electricity and a broken generator is slowed down rather than stopped.
 *
 * @see <a href="../../../../../../docs/SPEC_TECH.md">docs/SPEC_TECH.md</a>
 */
public final class IndustryRules {

    private IndustryRules() {
    }

    // ==================================================================
    // Voltage
    // ==================================================================

    /**
     * What a wire carries and what it costs to carry it.
     *
     * <p>Three tiers in Age II and a fourth left for Age III, because the
     * ceiling has to be somewhere the age above can still move into. Each step
     * is four times the capacity and half the loss, which is steep enough that
     * stepping up is obviously worth doing and cheap enough to describe.
     */
    public enum Voltage {

        /** Copper on poles. The first wire anybody strings. */
        LOW(256, 0.0040F),

        /** Gold, which conducts better than copper and costs like it. */
        MEDIUM(1024, 0.0020F),

        /** A steel core, which is the first thing the age has to make for itself. */
        HIGH(4096, 0.0010F),

        /** Age III's, declared here so the ceiling is one number in one place. */
        EXTREME(16384, 0.0005F);

        private final int capacity;
        private final float lossPerBlock;

        Voltage(int capacity, float lossPerBlock) {
            this.capacity = capacity;
            this.lossPerBlock = lossPerBlock;
        }

        /** The most this wire carries, in FE per tick, before it burns. */
        public int capacity() {
            return capacity;
        }

        /** What fraction of the load is lost for every block of wire. */
        public float lossPerBlock() {
            return lossPerBlock;
        }

        /** The next tier up, or this one if there is none. */
        public Voltage stepUp() {
            Voltage[] all = values();
            return this == all[all.length - 1] ? this : all[ordinal() + 1];
        }
    }

    /** The hard ceiling on any single connection, in FE per tick. */
    public static final int MOST_FE = Voltage.EXTREME.capacity();

    /**
     * Whether pushing this much through this wire destroys it.
     *
     * At exactly its capacity a wire is fine — the rating is the rating, the
     * same as a shaft's, so a player who reads the tooltip and builds to it is
     * not punished for believing it.
     */
    public static boolean burns(Voltage voltage, float carried) {
        return carried > voltage.capacity();
    }

    /**
     * What arrives at the far end of a run of wire.
     *
     * Straight-line loss per block rather than anything cleverer: a player has
     * to be able to look at a wire and know roughly what it costs, and
     * "one part in two hundred and fifty, per block" is a thing you can hold.
     * Floored at zero, because a long enough run should deliver nothing rather
     * than start delivering negative power.
     */
    public static float delivered(Voltage voltage, float sent, int blocks) {
        float kept = 1.0F - voltage.lossPerBlock() * Math.max(0, blocks);
        return Math.max(0.0F, sent * kept);
    }

    /** How far this wire can run before it delivers less than half of what it carries. */
    public static int halfwayAt(Voltage voltage) {
        return Math.round(0.5F / voltage.lossPerBlock());
    }

    /**
     * What happens when a wire meets a machine built for a different tier.
     *
     * <p>Under-voltage is harmless and slow: a low wire feeding a high machine
     * simply cannot supply it, which the share already expresses. Over-voltage
     * destroys the machine, which is the one place Age II is allowed to be
     * unkind, because a transformer is cheap and the mistake is legible.
     */
    public static boolean destroys(Voltage supplied, Voltage rated) {
        return supplied.ordinal() > rated.ordinal();
    }

    // ==================================================================
    // The grid
    // ==================================================================

    /**
     * What a grid is doing.
     *
     * @param supply  FE per tick arriving, after the wires have taken their cut
     * @param demand  FE per tick every machine on it would like
     * @param burned  true when some wire on it was carrying more than it could
     */
    public record Reading(float supply, float demand, boolean burned) {

        /**
         * What fraction of full speed everything on this grid runs at.
         *
         * One, when there is enough. Otherwise the share, which every machine
         * gets equally — a grid does not have a queue, and a model where the
         * first machine placed gets fed first would make build order matter in
         * a way nobody could see.
         */
        public float share() {
            if (burned || supply <= 0.0F) {
                return 0.0F;
            }
            if (demand <= 0.0F) {
                return 1.0F;
            }
            return Math.min(1.0F, supply / demand);
        }

        /** Whether anything on this grid is working at all. */
        public boolean running() {
            return !burned && share() > 0.0F;
        }

        /** How hard the grid is working, for the gauge. Same meaning as a line's. */
        public float effort() {
            if (supply <= 0.0F) {
                return demand > 0.0F ? 1.0F : 0.0F;
            }
            return Math.min(1.0F, demand / supply);
        }
    }

    /** Something that puts power into a grid. */
    public record Generator(float output, Voltage voltage) {

        public Generator {
            if (output < 0.0F) {
                throw new IllegalArgumentException("a generator that consumes power is a machine");
            }
        }
    }

    /** Something that takes power out of one. */
    public record Machine(float draw, Voltage rated) {

        public Machine {
            if (draw < 0.0F) {
                throw new IllegalArgumentException("a machine that supplies power is a generator");
            }
        }
    }

    /**
     * Where a grid settles.
     *
     * @param generators everything feeding it, already through its own wire
     * @param machines   everything drawing from it
     * @param wire       the weakest wire anywhere on the grid
     */
    /**
     * What everything on a grid that can lend or borrow power could do this tick.
     *
     * <h2>Why Age II declares this and ships nothing that does it</h2>
     *
     * Storage is Age III's, and no block in Age II holds a single unit of
     * energy. But there can only be <b>one</b> model of a grid in the pack: a
     * second age with its own walk, its own wire rules and its own idea of
     * what a share means is two models that will disagree, and the disagreement
     * will be found by a player rather than by a harness.
     *
     * <p>So the grid knows the <em>shape</em> "some things on me can lend and
     * borrow", in two floats, and knows nothing whatever about what a cell is,
     * how much it holds, what it is made of, or what it costs. Age III answers
     * those and Age II never asks.
     *
     * <p>Both numbers depend only on what the lender already holds, never on
     * what the grid is doing — which is the rule that keeps this from being a
     * loop with no fixed point, the same rule that stops a machine's draw
     * depending on its share.
     *
     * @param couldGive the most everything on the grid could hand over this tick
     * @param couldTake the most it could all accept
     */
    public record Reserve(float couldGive, float couldTake) {

        /** A grid with nothing on it that stores anything. Age II's whole world. */
        public static final Reserve NOTHING = new Reserve(0.0F, 0.0F);

        public Reserve {
            if (couldGive < 0.0F || couldTake < 0.0F) {
                throw new IllegalArgumentException("a reserve that lends or borrows a "
                        + "negative amount is an accounting error, not a battery");
            }
        }
    }

    /**
     * What a grid did this tick, including what moved in and out of storage.
     *
     * <p>{@code grid} already counts a loan as supply, so everything that
     * reads a {@link Reading} — every machine, every readout — sees a grid that
     * is simply better supplied than its generators alone would make it, and
     * nothing below Age III has to learn what a battery is.
     *
     * @param grid        what the machines see
     * @param fromReserve what storage lent to make up a shortfall
     * @param toReserve   what storage took of a surplus
     */
    public record Flow(Reading grid, float fromReserve, float toReserve) {

        public boolean draining() {
            return fromReserve > 0.0F;
        }

        public boolean charging() {
            return toReserve > 0.0F;
        }
    }

    /**
     * Where a grid settles when something on it can lend or borrow.
     *
     * <h2>One direction a tick</h2>
     *
     * A grid is either short or long and never both, so the reserve either
     * lends or borrows and never does both in a tick. That is not a
     * simplification for tidiness: something that could take at its input
     * limit and give at its output limit in the same tick on the same grid
     * would be a loop, and a loop is the one thing this file exists to make
     * impossible.
     *
     * <h2>And a battery does not make a wire safe</h2>
     *
     * What burns a wire is what flows through it, which is now generation plus
     * whatever is being lent, or generation of which some is on its way into
     * storage. Either way the wire is carrying it.
     */
    public static Flow settle(List<Generator> generators, List<Machine> machines,
                              Voltage wire, Reserve reserve) {
        float supply = 0.0F;
        for (Generator generator : generators) {
            supply += generator.output();
        }
        float demand = 0.0F;
        for (Machine machine : machines) {
            demand += machine.draw();
        }
        float lent = 0.0F;
        float kept = 0.0F;
        if (demand > supply) {
            lent = Math.min(demand - supply, reserve.couldGive());
        } else if (supply > demand) {
            kept = Math.min(supply - demand, reserve.couldTake());
        }
        float carried = Math.max(supply, Math.min(demand, supply + lent) + kept);
        return new Flow(new Reading(supply + lent, demand, burns(wire, carried)),
                lent, kept);
    }

    public static Reading settle(List<Generator> generators, List<Machine> machines,
                                 Voltage wire) {
        float supply = 0.0F;
        for (Generator generator : generators) {
            supply += generator.output();
        }
        float demand = 0.0F;
        for (Machine machine : machines) {
            demand += machine.draw();
        }
        // A wire burns on what actually flows through it, which is whichever of
        // supply and demand is smaller: a grid that wants ten thousand and has
        // two hundred is carrying two hundred.
        float carried = Math.min(supply, demand);
        return new Reading(supply, demand, burns(wire, carried));
    }

    // ==================================================================
    // Where the ages meet
    // ==================================================================

    /**
     * FE a proper alternator makes from rotation.
     *
     * <p>Measured against Age I's dynamo on purpose. The dynamo exists so a
     * player meets Forge Energy before the age that is about it; the
     * alternator is the first time electricity is worth having, and the
     * multiplier below is the size of that step.
     */
    public static final float ALTERNATOR_OVER_DYNAMO = 6.0F;

    /** The speed an alternator needs before it makes anything at all. */
    public static final float ALTERNATOR_LEAST_RPM = 48.0F;

    public static float alternatorOutput(float rpm) {
        if (Math.abs(rpm) < ALTERNATOR_LEAST_RPM) {
            return 0.0F;
        }
        return KineticRules.dynamoOutput(rpm) * ALTERNATOR_OVER_DYNAMO;
    }

    /** FE per rpm an alternator makes, once it is turning fast enough. */
    public static final float ALTERNATOR_FE_PER_RPM =
            KineticRules.DYNAMO_FE_PER_RPM * ALTERNATOR_OVER_DYNAMO;

    /**
     * The torque an alternator takes out of the line driving it.
     *
     * Six times the dynamo's, because it makes six times the electricity. A
     * generator that gave more for the same torque would make the dynamo a
     * mistake to have built rather than the rung below.
     */
    public static final float ALTERNATOR_DRAW =
            KineticRules.DYNAMO.draw() * ALTERNATOR_OVER_DYNAMO;

    /** The alternator as an Age I load, so a line can carry one. */
    public static final KineticRules.Load ALTERNATOR_LOAD =
            new KineticRules.Load(ALTERNATOR_DRAW, ALTERNATOR_LEAST_RPM);

    /** What a motor draws to hold a line at a given speed against a given load. */
    public static final float MOTOR_FE_PER_TORQUE_RPM = 0.05F;

    /** How much of what a motor draws comes out as rotation. Never all of it. */
    public static final float MOTOR_EFFICIENCY = 0.80F;

    public static float motorDraw(float rpm, float torque) {
        return Math.abs(rpm) * Math.max(0.0F, torque) * MOTOR_FE_PER_TORQUE_RPM
                / MOTOR_EFFICIENCY;
    }

    /** A motor described as an Age I source, so a line can be driven by one. */
    public static KineticRules.Source motorAsSource(float fedWith) {
        float scale = Math.max(0.0F, Math.min(1.0F, fedWith / MOTOR_RATED_FE));
        return new KineticRules.Source(MOTOR_FREE_RPM, MOTOR_STALL_TORQUE * scale + 0.001F);
    }

    /** What a motor wants, and what it gives when it gets it. */
    public static final float MOTOR_RATED_FE = 256.0F;
    public static final float MOTOR_FREE_RPM = 256.0F;
    public static final float MOTOR_STALL_TORQUE = 96.0F;

    /**
     * The best a motor driving alternators can ever give back, as a fraction
     * of what it drew.
     *
     * <h2>Why this exists at all</h2>
     *
     * A motor turns electricity into rotation and an alternator turns rotation
     * back into electricity, so the two of them bolted together are a loop,
     * and a loop that returned more than it took would be free power — the one
     * bug in a system like this that ruins everything downstream of it,
     * because every recipe cost in four ages is priced against energy being
     * scarce.
     *
     * <p>It cannot be settled by testing one arrangement. A player will gear
     * the alternator up, hang several off one motor, or run several motors
     * into one line, and each of those changes the answer. So it is settled
     * in closed form instead, over every ratio and every count at once.
     *
     * <h2>The arithmetic</h2>
     *
     * With {@code n} motors and {@code m} alternators geared at ratio
     * {@code r}, the line settles at {@code f(ns − mdr) / ns}, and the
     * alternators make {@code p} FE per rpm each, so what comes back over what
     * went in is {@code p·f·x·(s − x·d) / (R·s)} with {@code x = mr/n} — which
     * depends on the <em>product</em> of gearing and count and on nothing
     * else. It is a parabola in {@code x}, largest at {@code x = s/2d}, and
     * that largest value is what this returns.
     *
     * <p>The harness checks it is below one, and sweeps counts and ratios to
     * confirm no arrangement beats the closed form. If a later age raises a
     * motor's torque or an alternator's yield, this is the number that has to
     * stay under one, and the build stops if it does not.
     */
    public static float roundTripBest() {
        return roundTripBest(MOTOR_FREE_RPM, MOTOR_STALL_TORQUE, MOTOR_RATED_FE);
    }

    /**
     * The same proof, for any later age's motor.
     *
     * <p>Age III and Age IV will each add something that turns electricity
     * into rotation, and each of them reopens this question: the alternator
     * does not care which age is driving it. So the formula lives here, where
     * the alternator is, and every age above passes its own numbers through it
     * and has its own harness prove the answer is under one.
     *
     * <p>The alternator's two constants are not arguments, deliberately. If a
     * later age adds a <em>better</em> way of turning rotation into
     * electricity, this proof does not cover it and a new one is owed — which
     * is a thing worth having to notice rather than a parameter worth passing.
     *
     * @param freeRpm     the motor's free speed
     * @param stallTorque the torque it pushes with at a standstill
     * @param ratedFe     what it draws from the grid
     */
    public static float roundTripBest(float freeRpm, float stallTorque, float ratedFe) {
        if (ratedFe <= 0.0F) {
            return Float.POSITIVE_INFINITY;    // free rotation is free energy
        }
        return ALTERNATOR_FE_PER_RPM * freeRpm * stallTorque
                / (4.0F * ALTERNATOR_DRAW * ratedFe);
    }

    /**
     * What a loop of {@code motors} motors and {@code alternators} alternators
     * geared at {@code ratio} actually returns, as a fraction of what it drew.
     *
     * The long way round, for the harness to check {@link #roundTripBest}
     * against. Nothing in the game calls it.
     */
    public static float roundTrip(int motors, int alternators, float ratio) {
        return roundTrip(MOTOR_FREE_RPM, MOTOR_STALL_TORQUE, MOTOR_RATED_FE,
                motors, alternators, ratio);
    }

    /** The long way round, for any motor. */
    public static float roundTrip(float freeRpm, float stallTorque, float ratedFe,
                                  int motors, int alternators, float ratio) {
        if (motors <= 0 || alternators <= 0 || ratio <= 0.0F) {
            return 0.0F;
        }
        float stall = stallTorque * motors;
        float drawn = ALTERNATOR_DRAW * alternators * ratio;
        if (drawn >= stall) {
            return 0.0F;                       // it cannot even turn
        }
        float rpm = freeRpm * (stall - drawn) / stall;
        float made = ALTERNATOR_FE_PER_RPM * rpm * ratio * alternators;
        return made / (ratedFe * motors);
    }

    /**
     * How fast an Age II machine runs when it is driven by a shaft rather than
     * a wire.
     *
     * <p>The spine's rule, and the reason it is a fraction rather than a
     * refusal: a player whose generator has broken should be slowed down, not
     * stopped, and a player who has not built a generator yet should be able
     * to see what the machine does before committing to the age.
     */
    public static final float ON_A_SHAFT = 0.45F;

    /**
     * The rotation an Age II machine wants before a shaft will drive it at all.
     *
     * <p>Deliberately above what a timber shaft survives. Driving Age II
     * machinery by hand means brass at the very least, which makes the brass
     * shaft a rung on the ladder rather than a tidier-looking timber one. The
     * harness caught this as equal to timber's rating, where a shaft at
     * exactly its own limit could drive an arc furnace.
     */
    public static final float SHAFT_LEAST_RPM = 96.0F;

    public static float shaftShare(float rpm) {
        return Math.abs(rpm) < SHAFT_LEAST_RPM ? 0.0F : ON_A_SHAFT;
    }

    /**
     * How much electricity one unit of Age I torque stands in for.
     *
     * <p>The exchange rate between the ages, and the reason no Age II block
     * states a torque of its own. A machine's draw on a shaft is its draw on a
     * wire divided by this, so a machine that is four times as hungry on the
     * grid is four times as hungry on a line, and the two halves of the spine
     * cannot drift apart when somebody retunes one of them.
     */
    public static final float SHAFT_FE_PER_TORQUE = 8.0F;

    /**
     * An Age II machine described as an Age I load, so a shaft can drive it.
     *
     * The floor is {@link #SHAFT_LEAST_RPM} for every machine and not a number
     * per machine: the shaft route is a fallback, and a fallback with four
     * different speed requirements is four things to remember about a route
     * nobody is supposed to be taking for long.
     */
    public static KineticRules.Load asLoad(Machine machine) {
        return new KineticRules.Load(machine.draw() / SHAFT_FE_PER_TORQUE, SHAFT_LEAST_RPM);
    }

    // ==================================================================
    // What Age II is made of
    // ==================================================================

    /** A firebox and a boiler. The first generator, and it eats fuel forever. */
    public static final Generator STEAM_TURBINE = new Generator(120.0F, Voltage.LOW);

    /**
     * How much louder and hungrier a diesel engine is than a turbine.
     *
     * One number, used twice: it is what the engine's output is a multiple of,
     * and the rate at which it gets through the same fuel. So the engine is
     * not a better turbine — it is four turbines in one block, on a wire a
     * player has to earn, and it costs exactly what four turbines cost to run.
     */
    public static final int DIESEL_HASTE = 4;

    /** Burning oil. Louder, hungrier, and four times the turbine. */
    public static final Generator DIESEL_ENGINE =
            new Generator(STEAM_TURBINE.output() * DIESEL_HASTE, Voltage.MEDIUM);

    /** Rotation in, electricity out. What connects the age below to this one. */
    public static final Generator ALTERNATOR = new Generator(0.0F, Voltage.LOW);

    /** Crushing, at industrial scale: what the millstone becomes. */
    public static final Machine CRUSHER = new Machine(64.0F, Voltage.LOW);

    /** The press, electrified, and the machine that makes plates. */
    public static final Machine ROLLING_MILL = new Machine(96.0F, Voltage.LOW);

    /** Heat without a furnace. The first thing that needs a real grid. */
    public static final Machine ARC_FURNACE = new Machine(512.0F, Voltage.MEDIUM);

    /** Ore in, twice the metal out. The reason Age II exists. */
    public static final Machine SEPARATOR = new Machine(256.0F, Voltage.MEDIUM);

    // ==================================================================
    // How long a job takes on a grid
    // ==================================================================

    /**
     * Work done in one tick by a machine that has everything it wants.
     *
     * <p>A job is counted in work rather than in ticks so that a share which
     * changes halfway through does not throw away or invent progress. A
     * machine that did half a job at full speed and then lost half its supply
     * finishes the other half at half speed, which is what a player watching a
     * gauge expects and what a "ticks remaining" countdown cannot express.
     */
    public static final int WORK_FULL = 100;

    /** The whole of a job that takes this many ticks at full share. */
    public static int workFor(int ticks) {
        return Math.max(1, ticks) * WORK_FULL;
    }

    /**
     * What a machine running at this share gets through in one tick.
     *
     * Never zero above no share at all: a machine on a grid with one percent
     * of what it wants still finishes eventually, which is what stops a
     * browned-out factory from looking broken rather than slow.
     */
    public static int workPerTick(float share) {
        if (share <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.round(Math.min(1.0F, share) * WORK_FULL));
    }

    /**
     * The same thing said as a countdown, for anything that has to show one.
     *
     * The closed form of running {@link #workPerTick} against
     * {@link #workFor} until it is done, and the harness checks that the two
     * agree — an estimate that disagrees with the machine is worse than none.
     */
    public static int ticksAtShare(int ticks, float share) {
        int perTick = workPerTick(share);
        if (perTick <= 0) {
            return Integer.MAX_VALUE;
        }
        int work = workFor(ticks);
        return (work + perTick - 1) / perTick;
    }

    /**
     * How much more metal Age II gets out of an ore than Age I does.
     *
     * The spine's material ladder: Age I gives you raw metal, Age II doubles
     * it, Age III triples and separates it. Written here so the number cannot
     * be decided twice.
     */
    public static final int SEPARATOR_MULTIPLIER = 2;
}
