package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Engines that burn something, and the one sum they may not get wrong.
 *
 * <h2>What was missing</h2>
 *
 * Age I had four sources: a hand crank, a water wheel, a sail and a steam
 * engine. Every one of them is free once it is built — the wheel wants a
 * river, the sail wants sky, and neither is spent. Which means the whole age
 * had no fuel in it, and a fuel is the thing that makes a machine hall a
 * decision rather than a shape.
 *
 * <p>So: engines. A burner takes a fuel, spends it, and drives a line while
 * it lasts.
 *
 * <h2>The sum</h2>
 *
 * An engine may not put more energy into a line than the fuel it burned
 * contains. That is not a balance decision, it is arithmetic, and getting it
 * wrong is a machine hall that makes energy — the same failure as a loop that
 * gains torque, arrived at from the other direction.
 *
 * <p>The game already prices every solid fuel: a furnace burn time is how
 * long one item keeps a furnace going, and a furnace smelts one thing every
 * two hundred ticks. So a fuel is worth {@code burn / 200} smelts, and
 * {@link #WORK_PER_SMELT} says what a smelt is worth in the units a line
 * works in. Every engine here is checked against that, at {@link #EFFICIENCY}
 * or less, and no engine anywhere is allowed above it.
 *
 * <h2>And the ladder</h2>
 *
 * Six fuels, each worth more than the one below and each wanting more work to
 * make. A fuel that is not worth more than the one below it is a fuel nobody
 * refines, and a fuel that needs no work is the end of the ladder.
 */
public final class EngineRules {

    private EngineRules() {
    }

    // ==================================================================
    // What a fuel is worth
    // ==================================================================

    /** How long the game's furnace takes over one thing. */
    public static final int TICKS_PER_SMELT = 200;

    /**
     * What one smelt is worth as rotation, in torque-rpm-seconds.
     *
     * <p>The one number here that is a decision rather than a fact, and it is
     * the exchange rate between the game's own economy and this module's. Set
     * so that a lump of coal drives a millstone for about the time it would
     * have smelted eight things, which is the comparison a player actually
     * makes when they decide whether to burn it.
     */
    public static final float WORK_PER_SMELT = 240.0F;

    /**
     * How much of a fuel's energy an engine may put into a line.
     *
     * <p>Under one, always. An engine at one would be a perfect machine, and
     * a perfect machine is the shape of a bug rather than of a reward.
     */
    public static final float EFFICIENCY = 0.60F;

    /**
     * One fuel.
     *
     * @param id    what the item is called
     * @param burn  how long one of it keeps a furnace going, in ticks
     * @param made  what has to be done to it, or "" when the ground gives it
     */
    public record Fuel(String id, int burn, String made) {

        public Fuel {
            Objects.requireNonNull(id, "a fuel with no name");
            Objects.requireNonNull(made, "null making");
            if (burn <= 0) {
                throw new IllegalArgumentException(id + " burns for " + burn
                        + " ticks, which is a fuel that is not a fuel");
            }
        }

        /** How many smelts' worth of energy is in one of it. */
        public float smelts() {
            return (float) burn / TICKS_PER_SMELT;
        }

        /** How much rotation one of it is worth at the very most. */
        public float energy() {
            return smelts() * WORK_PER_SMELT;
        }

        /** How much an engine is allowed to get out of one of it. */
        public float allowed() {
            return energy() * EFFICIENCY;
        }
    }

    /**
     * The fuels, cheapest first.
     *
     * <p>The first four are the game's own burn times and are not ours to
     * choose. The last two are this module's, and each is worth more than the
     * one below because it costs a step of machinery to make — which is what
     * makes a refinery worth building.
     */
    public static final List<Fuel> FUELS = List.of(
            new Fuel("minecraft:coal", 1_600, ""),
            new Fuel("minecraft:charcoal", 1_600, "burning wood"),
            new Fuel("minecraft:blaze_rod", 2_400, ""),
            new Fuel("minecraft:coal_block", 16_000, "pressing nine coal"),
            new Fuel("elysiumkinetics:creosote", 24_000, "pressing a coal block"),
            new Fuel("elysiumkinetics:distillate", 48_000, "distilling creosote"));

    /** A fuel by name, or null. */
    public static Fuel fuel(String id) {
        for (Fuel one : FUELS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The fuels this module has to make itself. */
    public static List<String> ours() {
        List<String> made = new ArrayList<>();
        for (Fuel one : FUELS) {
            if (one.id().startsWith("elysiumkinetics:")) {
                made.add(one.id().substring("elysiumkinetics:".length()));
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What an engine is
    // ==================================================================

    /**
     * One engine.
     *
     * @param id      what the block is called
     * @param source  what it drives a line with while it is burning
     * @param takes   the deepest fuel it will not accept, exclusive: an
     *                engine that takes everything is an engine that makes the
     *                ladder pointless
     * @param burning how many ticks of a fuel it spends a second of running
     */
    public record Engine(String id, KineticRules.Source source, int takes,
                         int burning) {

        public Engine {
            Objects.requireNonNull(id, "an engine with no name");
            Objects.requireNonNull(source, "an engine that drives nothing");
            if (burning <= 0) {
                throw new IllegalArgumentException(id + " spends " + burning
                        + " ticks of fuel a second, which is an engine that "
                        + "runs on nothing");
            }
            if (takes <= 0) {
                throw new IllegalArgumentException(id + " accepts no fuel at all");
            }
        }

        /**
         * How much rotation it gets out of one of a fuel.
         *
         * <p>Its power at half its free speed — which is where a linear
         * source makes the most of it — times how long the fuel lasts in it.
         * Half free speed rather than free speed, because a source at its
         * free speed pushes with nothing at all and an engine measured there
         * would appear to make no energy from any fuel.
         */
        public float outOf(Fuel one) {
            float best = source.powerAt(source.freeSpeed() / 2.0F);
            float seconds = (float) one.burn() / burning;
            return best * seconds / KineticRules.REFERENCE_RPM;
        }

        /** Whether it will take a fuel at all. */
        public boolean accepts(Fuel one) {
            int at = FUELS.indexOf(one);
            return at >= 0 && at < takes;
        }
    }

    /**
     * Three engines: one for each end of the ladder and one in between.
     *
     * <p>Not one engine that takes everything. An engine that burns a lump of
     * coal as happily as a barrel of distillate is an engine that makes the
     * whole refinery pointless, and the refinery is the content.
     */
    public static final List<Engine> ENGINES = List.of(
            new Engine("furnace_engine", new KineticRules.Source(64.0F, 24.0F), 4, 32),
            new Engine("oil_engine", new KineticRules.Source(160.0F, 48.0F), 5, 112),
            new Engine("distillate_engine",
                    new KineticRules.Source(256.0F, 72.0F), 6, 220));

    /**
     * How much of a fuel each engine actually gets out of it.
     *
     * <p>Rising, and only a little. A bigger engine is <b>not</b> a more
     * efficient one by very much — what it is is more <em>powerful</em>: it
     * gets about the same energy out of a lump of coal and gets it out in a
     * fifth of the time, which is the honest difference between a big engine
     * and a small one and is why the thirst goes up with the strength.
     *
     * <p>The small rise that is there is the reward for refining, and it is
     * the only reason to build the top of the ladder for its own sake.
     */
    public static float efficiencyOf(Engine engine, Fuel one) {
        float energy = one.energy();
        return energy <= 0.0F ? 0.0F : engine.outOf(one) / energy;
    }

    /** An engine by name, or null. */
    public static Engine engine(String id) {
        for (Engine one : ENGINES) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Every block this adds. */
    public static List<String> blocks() {
        List<String> made = new ArrayList<>();
        for (Engine one : ENGINES) {
            made.add(one.id());
        }
        made.add(REFINERY);
        return List.copyOf(made);
    }

    /**
     * The weakest shaft an engine can be run on without shearing it.
     *
     * <p>Worth stating rather than leaving for a player to find out by
     * building one: the top engine turns faster than brass survives, so it is
     * Age I's bridge into Age II's materials and not a block you bolt onto a
     * timber hall. An engine with no shaft that survives it is an engine
     * nobody can use at all, which is what the check reports.
     */
    public static KineticRules.Shaft shaftFor(Engine engine) {
        for (KineticRules.Shaft shaft : KineticRules.Shaft.values()) {
            if (!KineticRules.shears(shaft, engine.source().freeSpeed())) {
                return shaft;
            }
        }
        return null;
    }

    /** What turns one fuel into the next. */
    public static final String REFINERY = "refinery";

    /** How long the refinery takes over one step. */
    public static final int REFINING_TICKS = 300;

    /** What the refinery draws off a line while it is working. */
    public static final KineticRules.Load REFINERY_LOAD =
            new KineticRules.Load(10.0F, 48.0F);

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the engines. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInCeiling(EFFICIENCY));
        bad.addAll(faultsInFuels(FUELS));
        bad.addAll(faultsInEngines(ENGINES));
        bad.addAll(makingEnergy(ENGINES, FUELS));
        bad.addAll(notWorthRefining(ENGINES));
        bad.addAll(unburnable());
        return List.copyOf(bad);
    }

    /**
     * Whether the ceiling on an engine is a ceiling at all.
     *
     * <p>Handed the number rather than reading {@link #EFFICIENCY} itself, so
     * that a control can pass a ceiling built to be caught. Every other check
     * here measures an engine against this number; if the number itself is
     * one or more then all of them pass by being asked nothing, and the whole
     * section becomes a comment.
     *
     * @param ceiling how much of a fuel's energy an engine may have
     */
    public static List<String> faultsInCeiling(float ceiling) {
        List<String> bad = new ArrayList<>();
        if (ceiling <= 0.0F) {
            bad.add("an engine may have " + ceiling + " of the energy in a "
                    + "fuel, which is an engine that cannot be run at all");
        }
        if (ceiling >= 1.0F) {
            bad.add("a perfect engine is the shape of a bug rather than of a "
                    + "reward: an engine may have " + ceiling + " of the "
                    + "energy in a fuel, which is more than the fuel contains "
                    + "and makes every check below it pass by asking nothing");
        }
        return bad;
    }

    /** The fuels: named once, each worth more than the one below. */
    public static List<String> faultsInFuels(List<Fuel> fuels) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int at = 0; at < fuels.size(); at++) {
            Fuel one = fuels.get(at);
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two fuels");
            }
            if (at == 0) {
                continue;
            }
            Fuel under = fuels.get(at - 1);
            if (one.burn() < under.burn()) {
                bad.add(one.id() + " burns for " + one.burn() + " ticks and "
                        + under.id() + " below it burns for " + under.burn()
                        + ", so the ladder runs backwards and nobody refines "
                        + "anything");
            }
            if (one.burn() == under.burn() && one.made().isEmpty()) {
                bad.add(one.id() + " is worth exactly what " + under.id()
                        + " is worth and costs nothing to make, so it is the "
                        + "same fuel under two names");
            }
        }
        if (fuels.size() < 2) {
            bad.add("there are " + fuels.size() + " fuel(s), which is not a ladder");
        }
        return bad;
    }

    /** The engines: named once, each better than the one below, none free. */
    public static List<String> faultsInEngines(List<Engine> engines) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int at = 0; at < engines.size(); at++) {
            Engine one = engines.get(at);
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two engines");
            }
            if (one.takes() > FUELS.size()) {
                bad.add(one.id() + " accepts " + one.takes() + " fuel(s) and "
                        + "there are " + FUELS.size());
            }
            if (at == 0) {
                continue;
            }
            Engine under = engines.get(at - 1);
            if (one.source().powerAt(one.source().freeSpeed() / 2.0F)
                    <= under.source().powerAt(under.source().freeSpeed() / 2.0F)) {
                bad.add(one.id() + " is no stronger than " + under.id()
                        + " below it, so nobody would ever build it");
            }
            if (one.takes() <= under.takes()) {
                bad.add(one.id() + " takes no deeper a fuel than " + under.id()
                        + ", so the ladder of fuels has a rung with no engine "
                        + "that can burn it");
            }
        }
        // And nothing takes the whole ladder, or the refinery is decoration.
        for (Engine one : engines) {
            if (one.takes() >= FUELS.size() && engines.size() > 1
                    && one != engines.get(engines.size() - 1)) {
                bad.add("the engines above it are blocks nobody would ever "
                        + "make: " + one.id() + " takes every fuel there is, "
                        + "so the refinery is decoration and the ladder of "
                        + "fuels is a list");
            }
        }
        return bad;
    }

    /**
     * Every engine that gets more out of a fuel than the fuel contains.
     *
     * <p>Handed both rosters so a control can pass an engine built to be
     * caught. Over a healthy pair this can only ever answer no, and a rule
     * that has only ever answered no is a rule nobody has shown can answer
     * yes.
     */
    public static List<String> makingEnergy(List<Engine> engines,
                                            List<Fuel> fuels) {
        List<String> bad = new ArrayList<>();
        for (Engine engine : engines) {
            for (Fuel one : fuels) {
                if (!engine.accepts(one)) {
                    continue;
                }
                float got = engine.outOf(one);
                if (got > one.allowed() + 1.0E-3F) {
                    bad.add("a machine that makes energy is the same bug as a "
                            + "loop that gains torque, arrived at from the "
                            + "other end: " + engine.id() + " gets " + got
                            + " out of one " + one.id() + " and there is only "
                            + one.energy() + " in it, of which an engine may "
                            + "have " + one.allowed());
                }
                if (got <= 0.0F) {
                    bad.add(engine.id() + " gets nothing at all out of "
                            + one.id() + ", so it is a block that eats fuel");
                }
            }
        }
        return bad;
    }

    /**
     * Every place the engine ladder does not reward refining.
     *
     * <p>Each engine has to get a little more out of the same fuel than the
     * one below it, or the top of the ladder is a block that is merely
     * thirstier. Asked about the cheapest fuel every one of them takes, which
     * is the only fuel they can all be compared on.
     */
    public static List<String> notWorthRefining(List<Engine> engines) {
        List<String> bad = new ArrayList<>();
        Fuel cheapest = FUELS.get(0);
        for (int at = 1; at < engines.size(); at++) {
            Engine one = engines.get(at);
            Engine under = engines.get(at - 1);
            if (!one.accepts(cheapest) || !under.accepts(cheapest)) {
                continue;
            }
            if (efficiencyOf(one, cheapest) <= efficiencyOf(under, cheapest)) {
                bad.add(one.id() + " gets " + efficiencyOf(one, cheapest)
                        + " out of a " + cheapest.id() + " and " + under.id()
                        + " below it gets " + efficiencyOf(under, cheapest)
                        + ", so the engine further up the ladder is merely "
                        + "thirstier and nobody would build it");
            }
        }
        return bad;
    }

    /** Every fuel no engine will burn. */
    public static List<String> unburnable() {
        List<String> bad = new ArrayList<>();
        for (Fuel one : FUELS) {
            boolean any = false;
            for (Engine engine : ENGINES) {
                any |= engine.accepts(one);
            }
            if (!any) {
                bad.add(one.id() + " is a fuel no engine will take, so it is an "
                        + "item with a burn time and nothing to burn it in");
            }
        }
        return List.copyOf(bad);
    }
}
