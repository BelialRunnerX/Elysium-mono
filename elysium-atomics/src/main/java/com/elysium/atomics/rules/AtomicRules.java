package com.elysium.atomics.rules;

import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.rules.KineticRules;

import java.util.List;

/**
 * The third age, in numbers. The JDK and the two ages below it, nothing else.
 *
 * <h2>What Age III adds, in one sentence each</h2>
 *
 * Age I is <b>rigidly coupled</b>: hang another machine on a line and the whole
 * hall slows down together. Age II is <b>divided</b>: ask a grid for more than
 * it has and every machine gets the same share. Age III is <b>buffered</b>: a
 * grid that has more than it needs keeps the difference, and a grid that has
 * less spends what it kept.
 *
 * <p>That is a bigger change than it reads as. Every grid until now has been
 * instantaneous — what is made this tick is what is spent this tick, and
 * building more generation than you need is waste. A cell makes surplus worth
 * having, makes a factory survive the night, and makes "how much can I store"
 * a question a player can build an answer to. It is the first time in the
 * spine that time is a resource.
 *
 * <h2>And the second thing: heat</h2>
 *
 * A reactor is not a bigger engine. An engine that runs out of fuel stops; a
 * reactor that runs out of <em>cooling</em> destroys itself and a good deal of
 * the room. The whole of the danger is one number that integrates over time
 * towards a value you can work out in advance, so a player who does the
 * arithmetic is safe and a player who guesses is usually fine and occasionally
 * is not.
 *
 * <p>Crucially it settles rather than running away on its own: a reactor at a
 * given throttle with a given amount of coolant reaches a temperature and
 * stays there. Runaway is something you cause by turning it up, not something
 * that happens while you are away — because a meltdown that happens while a
 * player is in the Nether is a meltdown they learn nothing from.
 *
 * <h2>What it does not add</h2>
 *
 * Its own model of electricity. The grid, the voltage tiers, the wire that
 * burns and the alternator are all Age II's, and this age reaches down for
 * them rather than declaring a second set that can disagree with the first.
 */
public final class AtomicRules {

    private AtomicRules() {
    }

    // ==================================================================
    // Storage
    // ==================================================================

    /**
     * Something that keeps power.
     *
     * @param capacity how much it holds, in FE
     * @param mostIn   the most it will take in one tick
     * @param mostOut  the most it will give in one tick
     */
    public record Cell(int capacity, float mostIn, float mostOut) {

        public Cell {
            if (capacity <= 0) {
                throw new IllegalArgumentException("a cell that holds nothing is a wire");
            }
            if (mostIn <= 0.0F || mostOut <= 0.0F) {
                throw new IllegalArgumentException("a cell that cannot be filled or "
                        + "emptied is a decoration");
            }
            if (mostIn > IndustryRules.MOST_FE || mostOut > IndustryRules.MOST_FE) {
                throw new IllegalArgumentException("a cell that moves more than the best "
                        + "wire carries can only ever be used by burning the wire");
            }
        }

        /** How long it takes to fill from empty, in ticks, at its own limit. */
        public int fillsIn() {
            return Math.round(capacity / mostIn);
        }

        /**
         * The thinnest wire that will carry what this cell moves.
         *
         * Worked out rather than declared, so a cell whose throughput is
         * raised cannot keep a wire rating that would now burn — which is a
         * mistake that shows up as a mysteriously destroyed grid rather than
         * as anything a player could attribute to the cell they just placed.
         */
        public IndustryRules.Voltage wire() {
            float moves = Math.max(mostIn, mostOut);
            for (IndustryRules.Voltage voltage : IndustryRules.Voltage.values()) {
                if (!IndustryRules.burns(voltage, moves)) {
                    return voltage;
                }
            }
            return IndustryRules.Voltage.EXTREME;
        }
    }

    /** How much bigger each tier of cell is than the one below. */
    public static final int CELL_STEP_CAPACITY = 16;

    /** How much faster each tier moves power than the one below. */
    public static final int CELL_STEP_THROUGHPUT = 8;

    /** A shed battery. The first thing that makes a surplus worth having. */
    public static final Cell BASIC_CELL = new Cell(1_048_576, 256.0F, 256.0F);

    /** What a factory runs on. */
    public static final Cell ADVANCED_CELL = new Cell(
            BASIC_CELL.capacity() * CELL_STEP_CAPACITY,
            BASIC_CELL.mostIn() * CELL_STEP_THROUGHPUT,
            BASIC_CELL.mostOut() * CELL_STEP_THROUGHPUT);

    /**
     * The top of the age, and deliberately exactly a wire.
     *
     * Its throughput is {@link IndustryRules#MOST_FE} — the capacity of the
     * best wire there is — so the last cell a player builds can saturate the
     * best connection they can make and not one unit more. A cell that could
     * push more than a wire carries would be a cell whose only use is burning
     * the wire it is attached to.
     */
    public static final Cell QUANTUM_CELL = new Cell(
            ADVANCED_CELL.capacity() * CELL_STEP_CAPACITY,
            IndustryRules.MOST_FE, IndustryRules.MOST_FE);

    /**
     * What a buffered grid did over one tick.
     *
     * @param supply    FE per tick arriving from generators
     * @param demand    FE per tick every machine on it would like
     * @param fromCells what the cells gave to make up a shortfall
     * @param toCells   what the cells took of a surplus
     * @param burned    whether a wire on it was carrying more than it could
     */
    public record Buffered(float supply, float demand, float fromCells, float toCells,
                           boolean burned) {

        /** What actually reached the machines this tick. */
        public float delivered() {
            return burned ? 0.0F : Math.min(demand, supply + fromCells);
        }

        /**
         * The fraction of full speed everything on the grid runs at.
         *
         * The same meaning as Age II's, so a machine does not have to know
         * whether there is a cell on the grid behind it.
         */
        public float share() {
            if (burned || demand <= 0.0F) {
                return burned ? 0.0F : 1.0F;
            }
            return Math.min(1.0F, (supply + fromCells) / demand);
        }

        /** Whether the cells are filling rather than emptying. */
        public boolean charging() {
            return toCells > 0.0F;
        }

        /** Whether the grid is living off what it stored. */
        public boolean draining() {
            return fromCells > 0.0F;
        }
    }

    /**
     * Where a grid with cells on it settles over one tick.
     *
     * <h2>One direction at a time</h2>
     *
     * A grid is either short or long, never both, so the cells either give or
     * take and never do both in a tick. That is not a simplification: a cell
     * that could take at {@code mostIn} and give at {@code mostOut} in the
     * same tick on the same grid would be a loop, and a loop is the one thing
     * this whole file exists to make impossible.
     *
     * <h2>What a wire carries with a cell on it</h2>
     *
     * Whatever flows, which is now generation plus discharge, or generation
     * of which some is going into storage. Either way it is the larger of what
     * leaves the generators and what the machines and cells between them
     * actually move — a cell does not make a wire safer.
     *
     * @param supply    what the generators make, already through their own wire
     * @param demand    what the machines want
     * @param cells     every cell on the grid
     * @param stored    what each of those cells currently holds, in order
     * @param wire      the weakest wire anywhere on the grid
     */
    public static Buffered settle(float supply, float demand, List<Cell> cells,
                                  List<Integer> stored, IndustryRules.Voltage wire) {
        IndustryRules.Flow flow = IndustryRules.settle(
                List.of(new IndustryRules.Generator(Math.max(0.0F, supply), wire)),
                List.of(new IndustryRules.Machine(Math.max(0.0F, demand), wire)),
                wire, reserveOf(cells, stored));
        return new Buffered(supply, demand, flow.fromReserve(), flow.toReserve(),
                flow.grid().burned());
    }

    /**
     * This bank, described in the only two numbers the grid below understands.
     *
     * <p>Which is the whole of the joint. Age II knows that something on its
     * grid can lend and borrow, and nothing at all about what a cell is, how
     * much it holds, or what it is made of; this age answers those, and never
     * has to run a second grid to do it.
     */
    public static IndustryRules.Reserve reserveOf(List<Cell> cells, List<Integer> stored) {
        return new IndustryRules.Reserve(couldGive(cells, stored),
                couldTake(cells, stored));
    }

    /** The most this bank could hand over in one tick, given what it holds. */
    public static float couldGive(List<Cell> cells, List<Integer> stored) {
        float available = 0.0F;
        for (int index = 0; index < cells.size(); index++) {
            available += Math.min(cells.get(index).mostOut(), held(stored, index));
        }
        return available;
    }

    /** The most this bank could accept in one tick, given what it holds. */
    public static float couldTake(List<Cell> cells, List<Integer> stored) {
        float room = 0.0F;
        for (int index = 0; index < cells.size(); index++) {
            Cell cell = cells.get(index);
            room += Math.min(cell.mostIn(), cell.capacity() - held(stored, index));
        }
        return room;
    }

    private static int held(List<Integer> stored, int index) {
        return index < stored.size() ? Math.max(0, stored.get(index)) : 0;
    }

    /** The same answer, for a bank the grid was told about as one reserve. */
    public static int after(Cell cell, int was, IndustryRules.Flow flow,
                            IndustryRules.Reserve bank) {
        Buffered went = new Buffered(0.0F, 0.0F, flow.fromReserve(), flow.toReserve(),
                flow.grid().burned());
        return after(cell, was, went, bank.couldGive(), bank.couldTake());
    }

    /**
     * What one cell holds after a tick that went like that.
     *
     * <h2>Whole units, and which way the rounding goes</h2>
     *
     * Forge Energy is an integer and a cell's contents are kept as one, which
     * is not tidiness: a bank kept in floating point gains and loses a unit or
     * two per tick to rounding at the magnitudes the top cell holds, and over
     * a night that is thousands of units of energy appearing out of nothing.
     * There is no amount of tolerance that makes "energy is conserved" true of
     * a float; there is an exact answer available, so this takes it.
     *
     * <p>The rounding is therefore deliberate and asymmetric. Charging rounds
     * <b>down</b> and discharging rounds <b>up</b>, so a bank takes at most
     * what the grid offered it and gives up at least what the grid drew. What
     * is lost that way is a unit per cell per tick at worst, which is a real
     * battery being slightly imperfect, and it can only ever fall on the side
     * of the player having less. A rounding that went the other way would be a
     * free-energy bug with a very long fuse.
     *
     * <h2>And why it is shared out in proportion</h2>
     *
     * By what each cell could have contributed, so a bank of mixed cells
     * drains evenly rather than emptying whichever one happens to be first in
     * a list. Build order is not something a player can see.
     */
    public static int after(Cell cell, int was, Buffered grid, float couldHaveGiven,
                            float couldHaveTaken) {
        if (grid.draining() && couldHaveGiven > 0.0F) {
            float mine = Math.min(cell.mostOut(), was);
            int given = (int) Math.ceil(grid.fromCells() * mine / couldHaveGiven);
            return clamp(cell, was - given);
        }
        if (grid.charging() && couldHaveTaken > 0.0F) {
            float mine = Math.min(cell.mostIn(), cell.capacity() - was);
            int taken = (int) Math.floor(grid.toCells() * mine / couldHaveTaken);
            return clamp(cell, was + taken);
        }
        return clamp(cell, was);
    }

    private static int clamp(Cell cell, int amount) {
        return Math.max(0, Math.min(cell.capacity(), amount));
    }

    // ==================================================================
    // Heat
    // ==================================================================

    /** The temperature everything sits at with nothing running. */
    public static final float AMBIENT = 20.0F;

    /** Above this a reactor is wearing itself out. */
    public static final float SAFE = 800.0F;

    /** Above this it is wearing out fast, and saying so. */
    public static final float CRITICAL = 1200.0F;

    /** At this it is over. */
    public static final float MELTDOWN = 1600.0F;

    /**
     * How much heat a unit of output makes.
     *
     * This and {@link #BARE_LOSS} together are the reason a reactor is not a
     * generator you can simply place: at full throttle with nothing plumbed to
     * it, the temperature this pair settles at is several times
     * {@link #MELTDOWN}.
     */
    public static final float HEAT_PER_FE = 0.02F;

    /** How much heat the core itself soaks up before its temperature moves. */
    public static final float THERMAL_MASS = 1.0F;

    /** The fraction of its excess temperature a bare core sheds each tick. */
    public static final float BARE_LOSS = 0.02F;

    /** What one coolant channel adds to that fraction. */
    public static final float COOLING_PER_CHANNEL = 0.05F;

    /** How many channels a core will take. */
    public static final int MOST_CHANNELS = 4;

    /** The cooling a core with this many channels plumbed into it has. */
    public static float coolingWith(int channels) {
        int used = Math.max(0, Math.min(MOST_CHANNELS, channels));
        return BARE_LOSS + COOLING_PER_CHANNEL * used;
    }

    /**
     * The temperature a core reaches one tick later.
     *
     * <p>Newton, and nothing cleverer: heat in raises it, and what it sheds is
     * a fraction of how far above ambient it already is. That is what makes it
     * <em>settle</em> — the hotter it gets the faster it loses — and settling
     * is the whole safety design. A reactor that ran away on its own would
     * melt down while the player was in the Nether, and a player learns
     * nothing from that.
     */
    public static float nextTemperature(float temperature, float heatIn, float cooling) {
        float shed = (temperature - AMBIENT) * Math.max(0.0F, cooling);
        return Math.max(AMBIENT, temperature + heatIn / THERMAL_MASS - shed);
    }

    /**
     * Where that ends up, in closed form.
     *
     * The fixed point of {@link #nextTemperature}, which is what a readout
     * shows a player <em>before</em> they turn the throttle up. The harness
     * runs the one against the other: a prediction that disagrees with the
     * reactor is worse than no prediction.
     */
    public static float settlesAt(float heatIn, float cooling) {
        if (cooling <= 0.0F) {
            return Float.POSITIVE_INFINITY;
        }
        return AMBIENT + heatIn / (THERMAL_MASS * cooling);
    }

    /** The heat a core throttled this far makes each tick. */
    public static float heatOf(Core core, float throttle) {
        return core.outputAt(throttle) * HEAT_PER_FE;
    }

    /** Where a core throttled this far, cooled this much, ends up. */
    public static float settlesAt(Core core, float throttle, int channels) {
        return settlesAt(heatOf(core, throttle), coolingWith(channels));
    }

    /**
     * The most a core can be throttled to and still settle below a temperature.
     *
     * What the readout offers a player who has just built one and has not
     * plumbed it yet: not "do not do that", but "this far is fine".
     */
    public static float throttleUnder(Core core, int channels, float temperature) {
        float room = temperature - AMBIENT;
        if (room <= 0.0F || core.mostOutput() <= 0.0F) {
            return 0.0F;
        }
        float allowed = room * THERMAL_MASS * coolingWith(channels) / HEAT_PER_FE;
        return Math.max(0.0F, Math.min(1.0F, allowed / core.mostOutput()));
    }

    /** How fast a core at this temperature is wearing itself out, per tick. */
    public static float wearAt(float temperature) {
        if (temperature <= SAFE) {
            return 0.0F;
        }
        float through = (Math.min(temperature, MELTDOWN) - SAFE) / (MELTDOWN - SAFE);
        return through * through * WEAR_AT_MELTDOWN;
    }

    /**
     * The wear a core takes in one tick sitting exactly at meltdown.
     *
     * Squared rather than straight so that the last two hundred degrees cost
     * far more than the first two hundred: a player who overshoots slightly
     * has time to notice, and one who ignores it does not.
     */
    public static final float WEAR_AT_MELTDOWN = 0.002F;

    /** Whether a core at this temperature has gone. */
    public static boolean meltingDown(float temperature) {
        return temperature >= MELTDOWN;
    }

    /**
     * How many notches a core's throttle has, not counting off.
     *
     * A dial rather than a slider, because the whole design of this age is
     * that a player can work out where a setting will land <em>before</em>
     * choosing it. Four notches is four numbers to know; a continuous control
     * is a number nobody reads.
     */
    public static final int THROTTLE_STEPS = 4;

    /** The throttle a given notch means. */
    public static float throttleOf(int step) {
        return Math.max(0, Math.min(THROTTLE_STEPS, step)) / (float) THROTTLE_STEPS;
    }

    /** How long one piece of fuel lasts at full throttle, in ticks. */
    public static final int FUEL_TICKS = 6_000;

    /**
     * A piece of fuel, counted in notch-ticks.
     *
     * Kept as whole units of "one notch for one tick" so that burning at a
     * quarter throttle lasts exactly four times as long, rather than four
     * times as long give or take whatever the rounding did. A fuel that did
     * not divide evenly would be a reason to run reactors at odd settings.
     */
    public static final int FUEL_BUDGET = FUEL_TICKS * THROTTLE_STEPS;

    /** How much of a core's wear one steel plate puts right. */
    public static final float REPAIR_PER_PLATE = 0.25F;

    /** The wear at which a core stops working until somebody sees to it. */
    public static final float SPENT = 1.0F;

    /**
     * How far a meltdown reaches.
     *
     * Large enough that it is not something a player builds next to their
     * storage, and finite, because an unrecoverable save is not a lesson.
     */
    public static final float MELTDOWN_RADIUS = 8.0F;

    /** Something that makes power out of heat it has to be rid of. */
    public record Core(float mostOutput) {

        public Core {
            if (mostOutput <= 0.0F) {
                throw new IllegalArgumentException("a core that makes nothing is a rock");
            }
        }

        /** What it makes at this throttle. */
        public float outputAt(float throttle) {
            return mostOutput * Math.max(0.0F, Math.min(1.0F, throttle));
        }
    }

    /**
     * The reactor, and the one number in the age that is deliberately awkward.
     *
     * <p>Half of what the best wire carries, so a second one needs the best
     * wire there is and a third burns it. A player's first reactor is a
     * triumph, their second is a wiring problem, and their third is a lesson
     * about transformers — which is the same shape of lesson Age II taught
     * with the arc furnace, one tier up.
     */
    public static final Core REACTOR = new Core(IndustryRules.MOST_FE / 2.0F);

    // ==================================================================
    // Where this age meets the ones below
    // ==================================================================

    /**
     * What Age III's machinery is made of, as an Age I line reckons strength.
     *
     * <p>The top of the table, because this age drives lines at the fastest
     * speed in the pack and anything on such a line that answered something
     * weaker would cap it below the speed it is being turned at — a line that
     * shears itself, which is a bug with no symptom until it has cost somebody
     * a machine hall. Age II learned this against timber; the same mistake is
     * one missing override away at every point the ages touch.
     */
    public static final KineticRules.Shaft MADE_OF = KineticRules.Shaft.ASCENDANT;

    /** What a servo draws from the grid. */
    public static final float SERVO_RATED_FE = 4096.0F;

    /**
     * How fast a servo will drive a line with nothing on it.
     *
     * {@link KineticRules#MAX_RPM} exactly — the fastest anything in the pack
     * turns. That is the point of the servo: it drives Age I machinery at
     * speeds no Age I source can reach and no timber shaft survives, so a
     * player who reaches Age III and goes back to their first machine hall has
     * to rebuild it in something stronger before they can use what they have
     * learned.
     */
    public static final float SERVO_FREE_RPM = KineticRules.MAX_RPM;

    /**
     * What it pushes with at a standstill.
     *
     * <p>Not chosen for feel. It is the largest value that keeps
     * {@link #servoRoundTrip()} at exactly the same return as Age II's motor,
     * and the reason it is pinned there rather than higher is that a servo and
     * an alternator are a loop like any other: Age II proved its motor cannot
     * pay, and that proof says nothing whatever about this one.
     */
    public static final float SERVO_STALL_TORQUE = 384.0F;

    /** A servo described as an Age I source, so a line can be driven by one. */
    public static KineticRules.Source servoAsSource(float fedWith) {
        float scale = Math.max(0.0F, Math.min(1.0F, fedWith / SERVO_RATED_FE));
        return new KineticRules.Source(SERVO_FREE_RPM, SERVO_STALL_TORQUE * scale + 0.001F);
    }

    /**
     * What a servo driving alternators returns, at best, of what it drew.
     *
     * Age II's proof, with this age's numbers put through it. The formula is
     * Age II's because the alternator is Age II's and does not care which age
     * is turning it; the answer is this age's problem.
     */
    public static float servoRoundTrip() {
        return IndustryRules.roundTripBest(SERVO_FREE_RPM, SERVO_STALL_TORQUE,
                SERVO_RATED_FE);
    }

    /**
     * How fast an Age III machine runs when it is driven by a shaft.
     *
     * <p>Worse than Age II's, and on purpose. The shaft route exists so that a
     * player whose power has failed is slowed rather than stopped, and the
     * further up the spine a machine is the less a rope and a water wheel
     * should be able to do for it. By Age IV it will be gone.
     */
    public static final float ON_A_SHAFT = IndustryRules.ON_A_SHAFT / 2.0F;

    /**
     * The rotation an Age III machine wants before a shaft will drive it at all.
     *
     * <p>Above brass's shear rating, exactly as Age II's is above timber's, so
     * each age's fallback route needs the shaft material of the age above the
     * one that introduced it. Twice Age II's number would have been 192, which
     * is brass's rating <em>precisely</em> — the same off-by-one that the
     * harness caught in Age II, where a shaft at its own limit could have
     * driven an arc furnace. It is written out rather than derived for that
     * reason, and checked against the material table below.
     */
    public static final float SHAFT_LEAST_RPM = 256.0F;

    public static float shaftShare(float rpm) {
        return Math.abs(rpm) < SHAFT_LEAST_RPM ? 0.0F : ON_A_SHAFT;
    }

    /** An Age III machine described as an Age I load, so a shaft can drive it. */
    public static KineticRules.Load asLoad(IndustryRules.Machine machine) {
        return new KineticRules.Load(machine.draw() / IndustryRules.SHAFT_FE_PER_TORQUE,
                SHAFT_LEAST_RPM);
    }

    // ==================================================================
    // What Age III is made of
    // ==================================================================

    /**
     * How much metal Age III gets out of an ore.
     *
     * The next rung of the spine's material ladder, written as the rung below
     * plus one rather than as a number, so the ladder is 1, 2, 3 by
     * construction and cannot be re-tuned into 1, 2, 4 in one file.
     */
    public static final int ENRICHER_MULTIPLIER = IndustryRules.SEPARATOR_MULTIPLIER + 1;

    /** Dissolving ore in acid to get the third unit out. Hungry, and slow. */
    public static final IndustryRules.Machine ENRICHER =
            new IndustryRules.Machine(2048.0F, IndustryRules.Voltage.HIGH);

    /** Chemistry: the machine that makes the acid, and everything else besides. */
    public static final IndustryRules.Machine REACTION_CHAMBER =
            new IndustryRules.Machine(1024.0F, IndustryRules.Voltage.HIGH);

    /** Pulling one element out of a mixture of them. What fuel is made in. */
    public static final IndustryRules.Machine CENTRIFUGE =
            new IndustryRules.Machine(3072.0F, IndustryRules.Voltage.HIGH);

    /** Electricity into rotation, at a speed nothing below can reach. */
    public static final IndustryRules.Machine SERVO =
            new IndustryRules.Machine(SERVO_RATED_FE, IndustryRules.Voltage.HIGH);

    /**
     * The sun, which is free and useless on its own.
     *
     * <p>Deliberately tiny — a fortieth of a steam turbine — because a solar
     * panel is not a generator in this pack, it is the thing that makes a
     * <em>cell</em> worth building. One panel and one cell is a night light.
     * A roof of them and a bank of cells is a factory that runs after dark,
     * and the second of those is a build rather than a purchase.
     */
    public static final IndustryRules.Generator SOLAR_PANEL =
            new IndustryRules.Generator(3.0F, IndustryRules.Voltage.LOW);

    /** How much less a panel makes when it cannot see the sky properly. */
    public static final float SOLAR_SHADED = 0.0F;

    /** What a reactor is, as something the grid can be told about. */
    public static IndustryRules.Generator reactorAt(float throttle) {
        return new IndustryRules.Generator(REACTOR.outputAt(throttle),
                IndustryRules.Voltage.EXTREME);
    }
}
