package com.elysium.kinetics.rules;

import java.util.List;

/**
 * The first age of machinery, as arithmetic.
 *
 * <h2>What this is</h2>
 *
 * Everything in {@code elysium-kinetics} that is a number lives here, and
 * nothing here imports Minecraft. {@code tools/KineticHarness.java} executes
 * it against hand-built lines, which is the only way to walk a model whose
 * interesting cases are combinations — four sources and eleven loads behave in
 * ways no amount of reading the method establishes.
 *
 * <h2>The model, in one paragraph</h2>
 *
 * A <b>line</b> is one rigidly coupled set of shafts; everything on it turns
 * at one speed. A <b>source</b> is described by a free speed and a stall
 * torque, and pushes along the line between them, so it is strong when slow
 * and gives up as it approaches its free speed. A <b>load</b> draws a constant
 * torque. The line settles at the speed where what the sources push equals
 * what the loads take. Add a machine and the line slows; add a source and it
 * speeds back up; take more than the sources can push against from a
 * standstill and it stalls.
 *
 * <h2>Why not Create's model</h2>
 *
 * Create gives a source a fixed capacity and a fixed speed, and flips the
 * whole network to OVERSTRESSED the moment demand crosses supply. It is one
 * comparison to implement and it reads as a light switch: everything works,
 * then nothing does, with no warning in between and no reading that tells you
 * how close you were.
 *
 * <p>A linear speed-torque curve is the same amount of code and behaves like a
 * machine. The line gets audibly slower as you hang things off it. The first
 * thing that stops is whatever needed the most speed, which is a sentence a
 * player can act on. And the difference between a water wheel and a steam
 * engine stops being a tooltip and becomes a fact about what they can drive.
 *
 * @see <a href="../../../../../../docs/SPEC_TECH.md">docs/SPEC_TECH.md</a>
 */
public final class KineticRules {

    private KineticRules() {
    }

    // ==================================================================
    // Limits
    // ==================================================================

    /**
     * The hard ceiling, in rpm, for every age.
     *
     * Nothing turns faster than this, ever, including Age IV. It exists so
     * that "how fast can this possibly go" has an answer that a later module
     * cannot quietly raise: a number only the top age uses is a number the top
     * age's author feels free to change.
     */
    public static final float MAX_RPM = 1024.0F;

    /** Below this a line is treated as stopped; floating point never lands on zero. */
    public static final float STOPPED = 0.01F;

    /** What one gear stage costs, as a fraction of the power crossing it. */
    public static final float GEAR_LOSS = 0.03F;

    /**
     * The most gear stages between a source and a load that is worth modelling.
     *
     * Not a rule the game enforces — it is a floor on the loss curve, so that a
     * player who builds a forty-stage train gets a line that is useless rather
     * than a float that has underflowed to zero and a division that is not a
     * number. 0.97^64 is about 14%, which is already useless.
     */
    public static final int MOST_STAGES = 64;

    // ==================================================================
    // What a shaft survives
    // ==================================================================

    /** A shaft material: how fast it can turn before it comes apart. */
    public enum Shaft {

        /** Age I. Cheap, and the reason gearing up is a decision. */
        TIMBER(64.0F),

        /** Age I, late. Brass is the salvager's metal. */
        BRASS(192.0F),

        /** Age II. Steel is the Reconstruction's answer to everything. */
        STEEL(512.0F),

        /** Age III. The only thing that reaches the ceiling. */
        ASCENDANT(1024.0F);

        private final float rating;

        Shaft(float rating) {
            this.rating = rating;
        }

        /** The fastest this material turns without shearing, in rpm. */
        public float rating() {
            return rating;
        }
    }

    /** Whether a line of this material survives being driven at this speed. */
    public static boolean shears(Shaft shaft, float rpm) {
        return Math.abs(rpm) > shaft.rating() + STOPPED;
    }

    /** The weakest material on a line decides what the whole line survives. */
    public static Shaft weakest(List<Shaft> shafts) {
        Shaft found = Shaft.ASCENDANT;
        for (Shaft shaft : shafts) {
            if (shaft.rating() < found.rating()) {
                found = shaft;
            }
        }
        return found;
    }

    // ==================================================================
    // Sources and loads
    // ==================================================================

    /**
     * Something that turns a line.
     *
     * @param freeSpeed  rpm with nothing attached; always above zero
     * @param stallTorque the torque it makes at a standstill
     */
    public record Source(float freeSpeed, float stallTorque) {

        public Source {
            if (freeSpeed <= 0.0F) {
                throw new IllegalArgumentException("a source with no free speed cannot turn");
            }
            if (stallTorque < 0.0F) {
                throw new IllegalArgumentException("a source that pulls backwards is a load");
            }
        }

        /** What this source pushes with at a given speed. Never negative. */
        public float torqueAt(float rpm) {
            return Math.max(0.0F, stallTorque * (1.0F - Math.abs(rpm) / freeSpeed));
        }

        /** Power at a given speed, which is what gearing conserves. */
        public float powerAt(float rpm) {
            return torqueAt(rpm) * Math.abs(rpm);
        }

        /**
         * This source as the main line sees it through gearing.
         *
         * A source on a sub-line turning {@code ratio} times as fast as the
         * main line appears slower and stronger by exactly that factor, less
         * what the teeth cost.
         */
        public Source through(float ratio, int stages) {
            float size = Math.abs(ratio);
            float kept = kept(stages);
            return new Source(freeSpeed / size, stallTorque * size * kept);
        }
    }

    /**
     * Something that takes torque out of a line.
     *
     * @param draw    the torque it needs, the same at any speed
     * @param leastRpm the speed below which it does not work at all
     */
    public record Load(float draw, float leastRpm) {

        public Load {
            if (draw < 0.0F) {
                throw new IllegalArgumentException("a load that gives torque back is a source");
            }
        }

        /** Whether this load is doing anything on a line at this speed. */
        public boolean working(float rpm) {
            return Math.abs(rpm) >= leastRpm && Math.abs(rpm) > STOPPED;
        }

        /**
         * This load as the main line sees it through gearing.
         *
         * Heavier by the ratio, because the power it takes is the same on both
         * sides of the teeth, and heavier again by what the teeth waste.
         */
        public Load through(float ratio, int stages) {
            float size = Math.abs(ratio);
            return new Load(draw * size / kept(stages), leastRpm / size);
        }
    }

    /** What fraction of the power crossing this many gear stages comes out. */
    public static float kept(int stages) {
        int count = Math.max(0, Math.min(MOST_STAGES, stages));
        return (float) Math.pow(1.0F - GEAR_LOSS, count);
    }

    // ==================================================================
    // The line
    // ==================================================================

    /**
     * What a line is doing.
     *
     * <p>There is deliberately no "supply" field. At the speed a line settles
     * at, supply equals demand by definition — that is what settling means —
     * so a field holding it would read as information and carry none, and a
     * gauge drawn from it would sit at 100% on a line with a hand crank and
     * nothing attached. The honest headroom is against {@link #capacity},
     * which is the most the sources could ever push.
     *
     * @param rpm      the speed it settled at, always zero or more
     * @param demand   the torque the loads want
     * @param capacity the torque every source together makes at a standstill
     * @param stalled  true when the loads outweigh every source from a standstill
     * @param sheared  true when it settled above what its weakest shaft survives
     */
    public record Reading(float rpm, float demand, float capacity,
                          boolean stalled, boolean sheared) {

        /** A line that is turning and holding together. */
        public boolean running() {
            return !stalled && !sheared && rpm > STOPPED;
        }

        /**
         * How hard the line is working, as a fraction.
         *
         * Zero is freewheeling and one is the moment it stalls. This is the
         * number the gauge shows, and it is the whole reason a player gets a
         * warning instead of a light switch: it climbs visibly as machines go
         * on, long before anything stops.
         */
        public float effort() {
            if (capacity <= 0.0F) {
                return demand > 0.0F ? 1.0F : 0.0F;
            }
            return Math.min(1.0F, demand / capacity);
        }
    }

    /**
     * Where a line settles.
     *
     * <p>Every source pushes along {@code s(1 - rpm/f)} and every load takes a
     * constant {@code d}, so the speed where the two balance falls out of one
     * line of algebra:
     *
     * <pre>
     *     Σs − rpm·Σ(s/f) = D
     *     rpm = (Σs − D) / Σ(s/f)
     * </pre>
     *
     * with three edges worth naming. No sources is a line nothing is turning.
     * Demand at or above {@code Σs} is a line the sources cannot move at all,
     * which is the stall. And no demand at all settles at the weighted free
     * speed, which is what {@code Σs / Σ(s/f)} already is.
     *
     * @param sources every source already reflected onto this line
     * @param loads   every load already reflected onto this line
     * @param shaft   the weakest material anywhere on the line
     */
    public static Reading settle(List<Source> sources, List<Load> loads, Shaft shaft) {
        float stall = 0.0F;
        float slope = 0.0F;
        for (Source source : sources) {
            stall += source.stallTorque();
            slope += source.stallTorque() / source.freeSpeed();
        }
        float demand = 0.0F;
        for (Load load : loads) {
            demand += load.draw();
        }

        if (sources.isEmpty() || slope <= 0.0F) {
            return new Reading(0.0F, demand, 0.0F, demand > 0.0F, false);
        }
        if (demand >= stall) {
            return new Reading(0.0F, demand, stall, true, false);
        }

        float rpm = (stall - demand) / slope;
        boolean sheared = shears(shaft, rpm);
        // A sheared line is reported at the speed that broke it, not at zero:
        // the player needs to see how far over the rating they went, and a
        // reading of 0 would say the opposite of what happened.
        return new Reading(Math.min(rpm, MAX_RPM), demand, stall, false, sheared);
    }

    /**
     * The speed the fussiest machine on a line is waiting for.
     *
     * <p>A line can turn and do nothing: seven millstones on one water wheel
     * settles at 6.7 rpm, which is below the 8 a millstone needs, so all seven
     * stop while the shaft keeps going round. The effort gauge reads 58% at
     * that moment and is telling the truth — the line is nowhere near stalling
     * — which is exactly why it is not enough on its own.
     *
     * <p>So this is the other half of the reading, and it is what the machine
     * itself says: <em>needs 8, turning at 6.7</em>. A number and a target is a
     * sentence a player can act on; "overstressed" is not.
     *
     * @return the highest least-speed among loads that are not turning fast
     *         enough, or 0 when everything on the line is working
     */
    public static float waitingFor(List<Load> loads, float rpm) {
        float wanted = 0.0F;
        for (Load load : loads) {
            if (!load.working(rpm) && load.leastRpm() > wanted) {
                wanted = load.leastRpm();
            }
        }
        return wanted;
    }

    /**
     * The same line with one more load on it, without rebuilding the list.
     *
     * Used by the preview in the wrench tooltip: a player about to place a
     * millstone is asking what it will do to the line, and the honest answer
     * is the speed it will settle at afterwards.
     */
    public static Reading with(List<Source> sources, List<Load> loads, Shaft shaft, Load extra) {
        java.util.ArrayList<Load> more = new java.util.ArrayList<>(loads);
        more.add(extra);
        return settle(sources, more, shaft);
    }

    // ==================================================================
    // Gearing
    // ==================================================================

    /**
     * What a pair of gears does to speed.
     *
     * A driving wheel of {@code teethIn} turning a wheel of {@code teethOut}
     * makes the second turn {@code teethIn / teethOut} times as fast, and in
     * the other direction — which is why a gearbox is not a gear pair and why
     * three cogs in a row turn the same way as one.
     */
    public static float ratio(int teethIn, int teethOut) {
        if (teethIn <= 0 || teethOut <= 0) {
            throw new IllegalArgumentException("a gear with no teeth is a smooth disc");
        }
        return -((float) teethIn / (float) teethOut);
    }

    /** The two sizes of cog Age I has, and the only ratios it can build. */
    public static final int SMALL_TEETH = 8;
    public static final int LARGE_TEETH = 24;

    /** The speed a load sees through a chain of ratios, direction included. */
    public static float geared(float rpm, float... ratios) {
        float out = rpm;
        for (float ratio : ratios) {
            out *= ratio;
        }
        return out;
    }

    // ==================================================================
    // What Age I is made of
    // ==================================================================
    //
    // Sources first, slowest and strongest at the top. The shape of this table
    // is the shape of the age: you start by turning a handle yourself, and
    // every step after that is about not having to.

    /** A handle, and your own arms. Enough for one millstone and nothing else. */
    public static final Source HAND_CRANK = new Source(32.0F, 8.0F);

    /** Falling water. Slow, strong, free forever, and it cannot be hurried. */
    public static final Source WATER_WHEEL = new Source(16.0F, 48.0F);

    /** One sail. A windmill is however many of these you bolted to the shaft. */
    public static final Source SAIL = new Source(24.0F, 6.0F);

    /** The most sails one bearing carries before it tears itself off. */
    public static final int MOST_SAILS = 16;

    /** Burning something. The end of Age I and the door into Age II. */
    public static final Source STEAM_ENGINE = new Source(128.0F, 40.0F);

    // Loads, cheapest first. leastRpm is the speed below which the machine is
    // a decoration: it is what makes a line that has been dragged slow fail in
    // a way a player can read.

    /** Grinds. The first machine anybody builds. */
    public static final Load MILLSTONE = new Load(4.0F, 8.0F);

    /** Presses. Slow by nature, so it asks for very little speed. */
    public static final Load PRESS = new Load(8.0F, 16.0F);

    /** Cuts. Wants speed more than it wants force. */
    public static final Load SAW = new Load(6.0F, 24.0F);

    /** Stirs. The expensive one, and the reason a water wheel stops being enough. */
    public static final Load MIXER = new Load(12.0F, 32.0F);

    /**
     * Torque into Forge Energy, badly.
     *
     * Age I can make electricity and should not want to. The dynamo is a load
     * like any other and its exchange rate is deliberately poor — a whole
     * water wheel spent on it lights a lamp. Age II does this properly; the
     * point of having it here at all is that a player meets Forge Energy
     * before the age that is about it, so the unit is familiar when it starts
     * to matter.
     */
    public static final Load DYNAMO = new Load(10.0F, 32.0F);

    /** FE per tick a dynamo makes at a given speed. Linear, and stingy. */
    public static final float DYNAMO_FE_PER_RPM = 0.25F;

    public static float dynamoOutput(float rpm) {
        if (Math.abs(rpm) < DYNAMO.leastRpm()) {
            return 0.0F;
        }
        return Math.abs(rpm) * DYNAMO_FE_PER_RPM;
    }

    // ==================================================================
    // Recipes
    // ==================================================================

    /**
     * How fast a line is turning, as a word.
     *
     * Recipes are gated on this rather than on a raw rpm so that a recipe book
     * can say "needs a steady line" instead of "needs 41.7 rpm", which is a
     * number nobody can plan against on a line whose speed moves as machines
     * are added.
     */
    public enum Pace {
        /** Not turning usefully. */
        STOPPED,
        /** A hand crank with something on it. */
        SLOW,
        /** A water wheel doing its job. */
        STEADY,
        /** Geared up, or steam. */
        FAST,
        /** Past what timber survives; Age II territory. */
        RACING
    }

    public static final float SLOW_AT = 8.0F;
    public static final float STEADY_AT = 24.0F;
    public static final float FAST_AT = 64.0F;
    public static final float RACING_AT = 192.0F;

    public static Pace pace(float rpm) {
        float speed = Math.abs(rpm);
        if (speed < SLOW_AT) {
            return Pace.STOPPED;
        }
        if (speed < STEADY_AT) {
            return Pace.SLOW;
        }
        if (speed < FAST_AT) {
            return Pace.STEADY;
        }
        if (speed < RACING_AT) {
            return Pace.FAST;
        }
        return Pace.RACING;
    }

    /** Whether a line at this speed is quick enough for a recipe wanting that pace. */
    public static boolean enough(float rpm, Pace wanted) {
        return pace(rpm).ordinal() >= wanted.ordinal();
    }

    // ==================================================================
    // Work
    // ==================================================================

    /**
     * How long one operation takes, in ticks.
     *
     * Inversely proportional to speed, floored so that a very fast line cannot
     * do a thing in less than a tick and produce a machine that is really a
     * multiplier on its own output.
     */
    public static final int SLOWEST_TICKS = 400;
    public static final int QUICKEST_TICKS = 10;
    public static final float REFERENCE_RPM = 32.0F;

    public static int ticksFor(float rpm, int baseTicks) {
        float speed = Math.abs(rpm);
        if (speed < STOPPED) {
            return SLOWEST_TICKS;
        }
        float scaled = baseTicks * REFERENCE_RPM / speed;
        return Math.max(QUICKEST_TICKS, Math.min(SLOWEST_TICKS, Math.round(scaled)));
    }
}
