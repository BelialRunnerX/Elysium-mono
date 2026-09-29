package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.GridState;
import com.elysium.solarpunk.power.PowerBudget;
import com.elysium.solarpunk.power.PowerClass;
import com.elysium.solarpunk.power.PowerCondition;
import com.elysium.solarpunk.power.PowerGrid;
import com.elysium.solarpunk.power.PowerRule;

/**
 * Runs the machines' arithmetic outside a world.
 *
 * <h2>What is worth executing here</h2>
 *
 * Not the ticking — that needs a level, a light engine and a fluid at a
 * position, and mocking those would be testing the mock. What can be checked is
 * the part that is pure arithmetic and that decides how the pair *feels*:
 *
 * <ul>
 *   <li>each generator's output curve, which has to be zero in the cases its
 *       promise names and to fall off smoothly in between rather than in a
 *       step;</li>
 *   <li>the two generators being a choice rather than a ranking — each has to
 *       win somewhere the other loses, or one of them is a reskin;</li>
 *   <li>the loop closing at all — one condenser has to be able to keep one
 *       bioreactor fed, or the machines are two halves that never meet;</li>
 *   <li>the buffers being sized so neither starves the other across a night.</li>
 * </ul>
 *
 * The last two are the ones that could not be checked by reading. A dozen
 * constants spread over four files decide them and none of them mentions the
 * others.
 *
 * <h2>Measured, not asserted</h2>
 *
 * Several of these print a figure rather than only passing. Comparing peak
 * output against draw is the check that looks right and says almost nothing —
 * peak is one instant of one day. The numbers that decide whether a base stalls
 * (the crossover light level, the carry across a night, what a tank costs to
 * fill) are derived here and printed, so an edit that quietly flattens one is
 * visible in the output even while every assertion still passes.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-solarpunk" \
 *         -d /tmp/mh elysium-solarpunk/tools/MachineHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-solarpunk:/tmp/mh" \
 *         com.elysium.solarpunk.machine.MachineHarness
 */
public final class MachineHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** The promise: power from direct skylight, and less of it under cover. */
    private static void condenserCurve() {
        // Under a roof is nothing, whatever the light says. This is the half a
        // light-level-only rule would have got wrong, and it would have let a
        // condenser run in a torch-lit cellar.
        for (int light = 0; light <= 15; light++) {
            check(SolarCondenserEntity.outputFor(false, light) == 0,
                    "roofed condenser generated at light " + light);
        }

        // Night is nothing even under open sky. The other half.
        check(SolarCondenserEntity.outputFor(true, 0) == 0,
                "condenser generated at night");

        // Noon is the peak, and nothing exceeds it.
        check(SolarCondenserEntity.outputFor(true, 15)
                        == SolarCondenserEntity.PEAK_OUTPUT,
                "open sky at full light is not peak output");
        for (int light = 0; light <= 15; light++) {
            check(SolarCondenserEntity.outputFor(true, light)
                            <= SolarCondenserEntity.PEAK_OUTPUT,
                    "output exceeded peak at light " + light);
        }

        // Monotonic: more light is never worth less. A curve that dipped would
        // make dusk better than afternoon, which no player would ever guess.
        int previous = -1;
        for (int light = 0; light <= 15; light++) {
            int output = SolarCondenserEntity.outputFor(true, light);
            check(output >= previous,
                    "output fell as light rose, at light " + light);
            previous = output;
        }

        // And it is a slope, not a switch: dimmed light has to cost something
        // and not everything, or rain would simply turn the machine off.
        int dim = SolarCondenserEntity.outputFor(true, 7);
        check(dim > 0 && dim < SolarCondenserEntity.PEAK_OUTPUT,
                "half light is not between nothing and peak: " + dim);
    }

    /** The loop: can one condenser keep one bioreactor running? */
    private static void loopCloses() {
        check(SolarCondenserEntity.PEAK_OUTPUT >= AlgaeBioreactorEntity.DRAW,
                "a condenser at noon (" + SolarCondenserEntity.PEAK_OUTPUT
                        + "/t) cannot keep up with a bioreactor ("
                        + AlgaeBioreactorEntity.DRAW + "/t), so the two "
                        + "machines this mod ships together do not work "
                        + "together");

        // A briquette has to be reachable inside one day's light. Minecraft's
        // day is 24000 ticks with roughly half of it lit; the bioreactor needs
        // GROW_TICKS of draw.
        int litTicks = 12_000;
        check(AlgaeBioreactorEntity.GROW_TICKS < litTicks,
                "one briquette takes " + AlgaeBioreactorEntity.GROW_TICKS
                        + " ticks of power and daylight is only " + litTicks
                        + " — the machine could never finish one in a day");

        // The bioreactor's buffer has to hold more than one batch, or it stalls
        // the instant a cloud passes.
        int perBatch = AlgaeBioreactorEntity.DRAW * AlgaeBioreactorEntity.GROW_TICKS;
        check(AlgaeBioreactorEntity.CAPACITY >= perBatch,
                "a bioreactor holds " + AlgaeBioreactorEntity.CAPACITY
                        + " and one batch costs " + perBatch
                        + ", so it can never bank a whole one");

        // The condenser's buffer has to be worth having: enough to carry the
        // bioreactor through some of the night, or there was no reason to give
        // the generator storage at all.
        // Reported as the combined buffer, because that is the number that
        // decides what a player sees. The condenser pushes its surplus into the
        // bioreactor all day, so by dusk both are full and both are drawn down
        // — quoting the generator's buffer alone understates the carry by more
        // than half.
        int nightTicks = 12_000;
        int banked = SolarCondenserEntity.CAPACITY + AlgaeBioreactorEntity.CAPACITY;
        int carried = banked / AlgaeBioreactorEntity.DRAW;
        check(carried > 0, "the pair banks nothing at all");
        System.out.println("  one pair banks " + banked + " by dusk and runs "
                + carried + " ticks into the night ("
                + (100 * carried / nightTicks) + "% of it)");
    }

    /**
     * The promise: power from open sky, and more of it with height.
     */
    private static void capstanCurve() {
        int ground = WindCapstanEntity.BASE_HEIGHT;
        int top = WindCapstanEntity.FULL_HEIGHT;

        // Roofed is nothing, at any height and any exposure. A turbine under a
        // floor is not in the weather whatever its neighbours are doing.
        for (int exposed = 0; exposed <= 4; exposed++) {
            check(WindCapstanEntity.outputFor(false, exposed, top) == 0,
                    "roofed capstan generated with " + exposed + " open sides");
        }

        // Bricked in on all four sides is nothing, at any height. This is the
        // check that makes the capstan a different machine from the condenser
        // rather than a reskin of it: the condenser does not care what is
        // beside it and this one has to.
        for (int height = ground; height <= top; height += 16) {
            check(WindCapstanEntity.outputFor(true, 0, height) == 0,
                    "enclosed capstan generated at height " + height);
        }

        // The top of the world, fully exposed, is the peak and nothing beats it.
        check(WindCapstanEntity.outputFor(true, 4, top)
                        == WindCapstanEntity.PEAK_OUTPUT,
                "open sky at full height is not peak output");
        for (int height = -64; height <= 320; height += 8) {
            for (int exposed = 0; exposed <= 4; exposed++) {
                check(WindCapstanEntity.outputFor(true, exposed, height)
                                <= WindCapstanEntity.PEAK_OUTPUT,
                        "output exceeded peak at height " + height
                                + " with " + exposed + " open sides");
            }
        }

        // Monotonic in height: climbing is never worth less. Clamped at both
        // marks, so a capstan in the void does not earn more than one on a
        // mountain and one above the build limit does not earn more than one at
        // it.
        int previous = -1;
        for (int height = -64; height <= 320; height++) {
            int output = WindCapstanEntity.outputFor(true, 4, height);
            check(output >= previous,
                    "output fell as height rose, at " + height);
            previous = output;
        }
        check(WindCapstanEntity.outputFor(true, 4, -64)
                        == WindCapstanEntity.outputFor(true, 4, ground),
                "below sea level pays differently from sea level");
        check(WindCapstanEntity.outputFor(true, 4, 320)
                        == WindCapstanEntity.outputFor(true, 4, top),
                "above the full-height mark still pays more");

        // Monotonic in exposure too, and walling a side in is a cost rather
        // than a disqualification: one open face still generates.
        previous = -1;
        for (int exposed = 0; exposed <= 4; exposed++) {
            int output = WindCapstanEntity.outputFor(true, exposed, top);
            check(output >= previous,
                    "output fell as sides opened, at " + exposed);
            previous = output;
        }
        check(WindCapstanEntity.outputFor(true, 1, top) > 0,
                "a capstan with one open side generates nothing, so walling "
                        + "one in is a disqualification rather than a cost");
        check(WindCapstanEntity.outputFor(true, 1, top)
                        < WindCapstanEntity.outputFor(true, 4, top),
                "exposure costs nothing, so the four-sides rule is decoration");
    }

    /**
     * The two generators have to be genuinely different machines.
     *
     * This is the check the feature exists for. Two generators that differ only
     * in their numbers are one generator and a reskin, and a player builds
     * whichever has the bigger number. What makes this a decision is that each
     * one wins somewhere the other loses.
     */
    private static void generatorsDiffer() {
        // Midnight, on a tower, in the open. The capstan works and the
        // condenser is off. This is the capstan's whole reason to exist.
        int capstanAtNight = WindCapstanEntity.outputFor(
                true, 4, WindCapstanEntity.FULL_HEIGHT);
        int condenserAtNight = SolarCondenserEntity.outputFor(true, 0);
        check(condenserAtNight == 0 && capstanAtNight > 0,
                "at midnight the condenser makes " + condenserAtNight
                        + " and the capstan " + capstanAtNight
                        + " — the capstan does not cover the condenser's gap, "
                        + "so it is a second generator with no reason to be");

        // Noon, at sea level, in the open. The condenser wins. If the capstan
        // beat it here too it would simply be the better machine.
        int condenserAtNoon = SolarCondenserEntity.outputFor(true, 15);
        int capstanAtSeaLevel = WindCapstanEntity.outputFor(
                true, 4, WindCapstanEntity.BASE_HEIGHT);
        check(condenserAtNoon > capstanAtSeaLevel,
                "at noon at sea level the condenser makes " + condenserAtNoon
                        + " and the capstan " + capstanAtSeaLevel
                        + " — the capstan is not worse anywhere, so there is "
                        + "no reason to build a condenser");

        // And neither strictly dominates over a day. Reported rather than
        // asserted into a corner: the numbers are the design, and printing them
        // is how a later edit that flattens the choice gets noticed.
        System.out.println("  condenser: " + condenserAtNoon + "/t at noon, "
                + condenserAtNight + "/t at midnight");
        System.out.println("  capstan:   " + capstanAtSeaLevel
                + "/t at y=" + WindCapstanEntity.BASE_HEIGHT + ", "
                + capstanAtNight + "/t at y=" + WindCapstanEntity.FULL_HEIGHT
                + ", either hour");
    }

    /**
     * The reclaimer: rain is a rate, not a switch, and the water it banks has
     * to be worth more than the power it cost.
     */
    private static void reclaimerRate() {
        // Sealed in is nothing, raining or not. There is no air to work on.
        check(WaterReclaimerEntity.rateFor(false, false) == 0
                        && WaterReclaimerEntity.rateFor(true, false) == 0,
                "a roofed reclaimer condenses water");

        // Clear weather still works, or the machine is dead for most of a week
        // and a player cannot plan around it.
        check(WaterReclaimerEntity.rateFor(false, true) > 0,
                "a reclaimer does nothing in clear weather, so it is a rain "
                        + "sensor rather than a machine");

        // And rain is a multiplier they can feel.
        check(WaterReclaimerEntity.rateFor(true, true)
                        > WaterReclaimerEntity.rateFor(false, true),
                "rain is worth nothing extra");

        // The interlock it was written for: a full-enough reclaimer has to be
        // reachable, and the threshold has to be inside the tank. A supply
        // threshold above capacity would be a machine that can never satisfy
        // the condition it exists to satisfy.
        check(WaterReclaimerEntity.SUPPLY_THRESHOLD
                        <= WaterReclaimerEntity.TANK_CAPACITY,
                "a reclaimer holding everything it can hold ("
                        + WaterReclaimerEntity.TANK_CAPACITY
                        + ") still does not reach the supply threshold ("
                        + WaterReclaimerEntity.SUPPLY_THRESHOLD + ")");
        check(WaterReclaimerEntity.SUPPLY_THRESHOLD
                        == SolarpunkWater.SUPPLY,
                "the reclaimer calls itself a supply at "
                        + WaterReclaimerEntity.SUPPLY_THRESHOLD
                        + " and the bioreactor wants "
                        + SolarpunkWater.SUPPLY
                        + ", so there is a band where a reclaimer says it is "
                        + "supplying and the bioreactor beside it is stopped");

        // How long from dry to supplying, in clear weather, at full power.
        int clear = WaterReclaimerEntity.rateFor(false, true);
        int ticks = WaterReclaimerEntity.SUPPLY_THRESHOLD / clear;
        check(ticks < 24_000,
                "a dry reclaimer takes " + ticks + " ticks to become a supply, "
                        + "which is more than a Minecraft day");
        System.out.println("  reclaimer: dry to supplying in " + ticks
                + " ticks clear (" + (ticks * 100 / 24_000) + "% of a day), "
                + (ticks / WaterReclaimerEntity.RAIN_FACTOR) + " in rain");
    }

    /**
     * A generator has to be able to run the consumers it ships with.
     */
    private static void reclaimerIsAffordable() {
        check(SolarCondenserEntity.PEAK_OUTPUT >= WaterReclaimerEntity.DRAW,
                "a condenser at noon (" + SolarCondenserEntity.PEAK_OUTPUT
                        + "/t) cannot run a reclaimer ("
                        + WaterReclaimerEntity.DRAW + "/t)");
        check(WindCapstanEntity.PEAK_OUTPUT >= WaterReclaimerEntity.DRAW,
                "a capstan at its best (" + WindCapstanEntity.PEAK_OUTPUT
                        + "/t) cannot run a reclaimer ("
                        + WaterReclaimerEntity.DRAW + "/t)");

        // The whole chain on one generator: reclaimer plus bioreactor.
        //
        // Comparing peak output against draw is the check that looks right and
        // says almost nothing. Peak is one instant of one day; what decides
        // whether a player's base stalls is how much of the day the generator
        // is ahead. So measure the crossover instead: the dimmest skylight at
        // which one condenser still covers the load. Every light level below
        // that is the buffer draining.
        int chain = WaterReclaimerEntity.DRAW + AlgaeBioreactorEntity.DRAW;
        int bioreactorFrom = crossover(AlgaeBioreactorEntity.DRAW);
        int chainFrom = crossover(chain);

        // The bioreactor alone has to be covered for most of the lit half, or
        // the first machine a player builds is one that never quite runs.
        check(bioreactorFrom >= 0 && bioreactorFrom < 15,
                "one condenser only covers one bioreactor at full noon light"
                        + " (crossover " + bioreactorFrom + "), so the pair "
                        + "this mod ships together is knife-edge");

        System.out.println("  one condenser covers a bioreactor from sky light "
                + bioreactorFrom + "/15, and the reclaimer too from "
                + (chainFrom < 0 ? "never" : chainFrom + "/15")
                + " (chain draws " + chain + "/t, peak is "
                + SolarCondenserEntity.PEAK_OUTPUT + "/t)");

        // The reclaimer's load is transient and that is the only reason the
        // chain works on one generator: it draws nothing once the tank is full.
        // Bank the whole fill cost against the generator's buffer plus what it
        // makes over a lit half-day at the crossover light it is guaranteed —
        // if the fill costs more than that, "portable" was a claim rather than
        // a fact.
        int fillCost = (WaterReclaimerEntity.TANK_CAPACITY
                / WaterReclaimerEntity.rateFor(false, true))
                * WaterReclaimerEntity.DRAW;
        int litHalf = 12_000;
        int surplusAtCrossover = (SolarCondenserEntity.PEAK_OUTPUT
                - AlgaeBioreactorEntity.DRAW) * litHalf;
        check(fillCost <= SolarCondenserEntity.CAPACITY + surplusAtCrossover,
                "filling a reclaimer from dry costs " + fillCost
                        + " and a condenser's buffer plus a noon day's surplus "
                        + "over the bioreactor is only "
                        + (SolarCondenserEntity.CAPACITY + surplusAtCrossover)
                        + " — the portable chain needs a second generator and "
                        + "nothing tells the player so");
        System.out.println("  filling a reclaimer from dry costs " + fillCost
                + " once, then it draws nothing until the tank is spent");
    }

    /**
     * The dimmest sky light at which one condenser covers a given draw.
     *
     * -1 when it never does. This is the number the design actually turns on,
     * and it is not visible in any single constant.
     */
    private static int crossover(int draw) {
        for (int light = 0; light <= 15; light++) {
            if (SolarCondenserEntity.outputFor(true, light) >= draw) {
                return light;
            }
        }
        return -1;
    }

/**
     * The kiln's promise: less fuel than a furnace needs.
     *
     * Two constants in one file and one in vanilla decide this, and none of
     * them mentions the others. Derive the ratio instead of asserting a number
     * somebody typed.
     */
    private static void kilnSavesFuel() {
        int coal = 1600;                 // vanilla's burn time for one coal
        int briquette = 1600;            // the bioreactor's output, deliberately equal

        int kilnPerCoal = CeramicKilnEntity.itemsPerFuel(coal);
        int furnacePerCoal = CeramicKilnEntity.furnaceItemsPerFuel(coal);

        check(kilnPerCoal > furnacePerCoal,
                "one coal fires " + kilnPerCoal + " items in the kiln and "
                        + furnacePerCoal + " in a furnace — the block's whole "
                        + "promise is that it needs less fuel, and it does not");

        // ...and not so much less that the furnace stops being worth using for
        // the things the kiln refuses. A tenfold saving on bulk blocks would
        // make every other heat source irrelevant to anyone who built one.
        check(kilnPerCoal <= furnacePerCoal * 4,
                "one coal fires " + kilnPerCoal + " items in the kiln against a "
                        + "furnace's " + furnacePerCoal + ", which is more than "
                        + "a fourfold saving — nothing else would be worth "
                        + "lighting");

        // Faster too, or the saving is paid for in a way a player feels more
        // than they feel the saving.
        check(CeramicKilnEntity.COOK_TICKS
                        <= net.minecraft.world.level.block.entity
                        .AbstractFurnaceBlockEntity.BURN_TIME_STANDARD,
                "the kiln takes " + CeramicKilnEntity.COOK_TICKS
                        + " ticks per item and a furnace takes 200");

        // A partly-spent fuel must never buy less than one item, or a player
        // feeding it by hand loses the tail of every stack.
        check(CeramicKilnEntity.itemsPerFuel(briquette) >= 1,
                "a biomass briquette fires less than one item, so the "
                        + "bioreactor's own output cannot run the kiln");

        System.out.println("  kiln: " + kilnPerCoal + " items per coal against "
                + "a furnace's " + furnacePerCoal + " ("
                + (kilnPerCoal * 100 / furnacePerCoal) + "%), "
                + CeramicKilnEntity.COOK_TICKS + " ticks each");
    }

    /**
     * The loop the whole set has been building, closed and costed.
     *
     * Sun to power, power to algae, algae to briquette, briquette to fired
     * brick. Each step is a machine somebody built for its own reasons; this is
     * the check that the chain they form is actually worth walking, rather than
     * four machines that happen to be in the same mod.
     */
    private static void chainPaysForItself() {
        int briquette = 1600;
        int perBriquette = AlgaeBioreactorEntity.DRAW
                * AlgaeBioreactorEntity.GROW_TICKS;
        int firedByBriquette = CeramicKilnEntity.itemsPerFuel(briquette);

        // The same energy, spent directly on the kiln instead of on growing a
        // briquette to burn in it. If the detour is not worth taking, the
        // bioreactor is a machine with no reason to exist downstream of itself.
        int firedDirectly = perBriquette / (CeramicKilnEntity.DRAW
                * CeramicKilnEntity.COOK_TICKS);

        // A band, not a floor. Below it the bioreactor is a detour that loses
        // and nobody builds one; far above it the kiln's power input is
        // decoration, because no one wires up a machine to run it four times
        // worse than the fuel they already have. Both failures are silent and
        // both are one constant away.
        check(firedByBriquette > firedDirectly,
                "growing a briquette costs " + perBriquette + " and fires "
                        + firedByBriquette + " items; spending that power "
                        + "straight into the kiln fires " + firedDirectly
                        + " — the bioreactor is a detour that loses");
        check(firedByBriquette <= firedDirectly * 3,
                "a briquette fires " + firedByBriquette + " items against "
                        + firedDirectly + " for the same power spent directly. "
                        + "The kiln's power input is decoration at that ratio");

        System.out.println("  the chain: " + perBriquette + " energy becomes one "
                + "briquette and fires " + firedByBriquette + " items; the same "
                + "energy spent directly fires " + firedDirectly);
    }

    /**
     * The one piece of recipe arithmetic that fails silently.
     *
     * Recipes name a total cost and machines spend per tick. Round that
     * division down and any recipe costing less than one unit per tick is free
     * — and the cheapest recipe in a set is precisely where it lands, so the
     * bug ships as "the loom seems to work without power" and nobody reports
     * it as a bug.
     */
    private static void recipeCostRoundsUp() {
        check(com.elysium.solarpunk.recipe.ProcessCost.perTick(800, 200) == 4,
                "an exact division came out wrong");

        // Every cost below one per tick still costs one.
        for (int energy = 1; energy < 200; energy++) {
            int perTick = com.elysium.solarpunk.recipe.ProcessCost.perTick(energy, 200);
            check(perTick >= 1,
                    "a recipe costing " + energy + " over 200 ticks is free");
        }

        // And rounding up never turns a cheap recipe into an expensive one:
        // the overcharge is under one tick's worth.
        for (int energy = 1; energy <= 2000; energy++) {
            for (int time : new int[] {1, 7, 20, 140, 200, 240}) {
                int spent = com.elysium.solarpunk.recipe.ProcessCost.perTick(energy, time) * time;
                check(spent >= energy,
                        "rounding lost energy at " + energy + "/" + time);
                check(spent < energy + time,
                        "rounding overcharged by a whole tick or more at "
                                + energy + "/" + time);
            }
        }

        // A zero-length run cannot divide by its time; it costs its whole total
        // at once rather than throwing.
        check(com.elysium.solarpunk.recipe.ProcessCost.perTick(500, 0) == 500,
                "a zero-time recipe divides by zero");
    }

    /**
     * The tank: where it will live, and what the fish cost.
     *
     * The siting rule is the reason this machine is not a second bioreactor,
     * so it is the part worth executing. Both ask for water; the bioreactor
     * asks for sky and this asks for a light level, which is the difference
     * between an outdoor machine and one a player can put in a lit cellar.
     */
    private static void tankSiting() {
        // No water is no tank, at any light. This is the half shared with the
        // bioreactor and the half a light-only rule would have got wrong.
        for (int light = 0; light <= 15; light++) {
            check(!AquaponicsTankEntity.sited(false, light),
                    "a dry tank grew at light " + light);
        }

        // Dark is no tank, at any water. The other half.
        for (int light = 0; light < AquaponicsTankEntity.MIN_LIGHT; light++) {
            check(!AquaponicsTankEntity.sited(true, light),
                    "a tank grew in the dark at light " + light);
        }
        for (int light = AquaponicsTankEntity.MIN_LIGHT; light <= 15; light++) {
            check(AquaponicsTankEntity.sited(true, light),
                    "a watered, lit tank refused to grow at light " + light);
        }

        // And it is a threshold a player can already read off the world: at or
        // above the level vanilla crops need, which is also one above where
        // hostile mobs stop spawning. A number nobody can see the meaning of
        // is a number they have to look up.
        check(AquaponicsTankEntity.MIN_LIGHT == 9,
                "the tank's light floor is "
                        + AquaponicsTankEntity.MIN_LIGHT + ", not the 9 that "
                        + "vanilla crops and mob spawning already teach");

        // The bioreactor and the tank must not want the same site, or one of
        // them is a reskin. The bioreactor needs sky; the tank does not care.
        check(!AlgaeBioreactorEntity.sited(false, true)
                        && AquaponicsTankEntity.sited(true, 15),
                "under a roof beside water the bioreactor and the tank behave "
                        + "the same, so the second machine adds no decision "
                        + "about where to build");

        System.out.println("  tank: water plus light " + AquaponicsTankEntity.MIN_LIGHT
                + "+, roof or no roof; the bioreactor needs sky");
    }

    /**
     * The fish are a bonus, not a second job.
     *
     * Counted in crops rather than ticks, so a tank nobody feeds produces
     * nothing. The check is that the ratio is a real cost and not a rounding:
     * one fish per crop would make the crop recipes irrelevant, and one per
     * fifty would make the fish a rumour.
     */
    private static void tankFish() {
        check(AquaponicsTankEntity.CROPS_PER_FISH > 1,
                "the tank yields a fish for every crop, so the fish are not a "
                        + "by-product, they are the output");
        check(AquaponicsTankEntity.CROPS_PER_FISH <= 8,
                "the tank needs " + AquaponicsTankEntity.CROPS_PER_FISH
                        + " crops per fish, which is long enough that a player "
                        + "will never connect the two");

        // What a fish actually costs, through the cheapest crop the set ships.
        // Derived rather than asserted: the figure moves whenever a recipe
        // does, and printing it is how a later edit that makes fish free gets
        // noticed while every assertion still passes.
        int cheapestCrop = 480;          // kelp_from_kelp, the cheapest farming run
        int perFish = cheapestCrop * AquaponicsTankEntity.CROPS_PER_FISH;
        check(perFish > 0, "a fish costs nothing");
        System.out.println("  tank: one fish per "
                + AquaponicsTankEntity.CROPS_PER_FISH + " crops — about "
                + perFish + " energy through the cheapest run, and nothing at "
                + "all if the tank is not fed");
    }

    /**
     * The harvester's sweep must cover its field exactly once.
     *
     * The cursor walks an index and the index becomes an offset, and an
     * off-by-one anywhere in that produces a machine that ignores one edge of
     * its own square — which a player reads as "this row will not grow" and
     * never as a bug in the machine. Nothing in a running game would report it.
     */
    private static void theHarvesterSweep() {
        int side = HarvesterEntity.RADIUS * 2 + 1;
        int expected = side * side;
        check(HarvesterEntity.fieldSize() == expected,
                "the field is " + HarvesterEntity.fieldSize() + " columns, not the "
                        + expected + " a radius of " + HarvesterEntity.RADIUS
                        + " covers");

        // One full sweep visits every column of the square once.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int index = 0; index < expected; index++) {
            net.minecraft.core.BlockPos offset = HarvesterEntity.offsetFor(index);
            check(offset.getY() == 0,
                    "the sweep left the machine's own level at index " + index);
            check(Math.abs(offset.getX()) <= HarvesterEntity.RADIUS
                            && Math.abs(offset.getZ()) <= HarvesterEntity.RADIUS,
                    "the sweep reached " + offset + ", outside a radius of "
                            + HarvesterEntity.RADIUS);
            check(seen.add(offset.getX() + "," + offset.getZ()),
                    "the sweep visited " + offset + " twice in one pass, so some "
                            + "other column is never visited at all");
        }
        check(seen.size() == expected,
                "one sweep covered " + seen.size() + " of " + expected
                        + " columns; the rest are never harvested");

        // The machine's own column is inside the field and is skipped when it
        // comes up — but it must be *in* the walk, or the walk is not a square.
        check(seen.contains("0,0"),
                "the machine's own column is not part of the square it sweeps");

        // And the cursor wraps rather than running off: it is a saved int that
        // grows for as long as the machine exists.
        for (int index : new int[] {expected, expected * 7 + 3, Integer.MAX_VALUE, -1}) {
            net.minecraft.core.BlockPos offset = HarvesterEntity.offsetFor(index);
            check(Math.abs(offset.getX()) <= HarvesterEntity.RADIUS
                            && Math.abs(offset.getZ()) <= HarvesterEntity.RADIUS,
                    "cursor " + index + " maps to " + offset + ", outside the field");
        }

        System.out.println("  harvester: a " + side + "x" + side + " field, "
                + expected + " columns, swept once each with no gaps and no "
                + "repeats; the cursor wraps at every extreme");
    }

    /**
     * The harvester has to be worth powering and must not be free.
     */
    private static void theHarvesterCosts() {
        check(HarvesterEntity.COST_PER_HARVEST > 0,
                "the harvester cuts a block for nothing");

        // A full field on one charge would make the buffer the only limit and
        // the generator irrelevant. A field must cost a visible fraction of it.
        int fullField = HarvesterEntity.COST_PER_HARVEST * HarvesterEntity.fieldSize();
        check(fullField > HarvesterEntity.CAPACITY / 8,
                "cutting the whole field costs " + fullField + " against a buffer "
                        + "of " + HarvesterEntity.CAPACITY + ", so the machine "
                        + "never meaningfully draws power");
        check(fullField < HarvesterEntity.CAPACITY * 8,
                "cutting the whole field costs " + fullField
                        + ", so a full buffer barely dents one harvest");

        // It has to keep up with the crops. Vanilla wheat takes minutes to
        // ripen; a sweep that took longer than that would leave a field
        // permanently half-cut.
        int ticksPerSweep = HarvesterEntity.fieldSize() / HarvesterEntity.SCAN_PER_TICK;
        check(ticksPerSweep < 200,
                "one sweep of the field takes " + ticksPerSweep + " ticks even "
                        + "with nothing to cut, which is slower than crops ripen");

        System.out.println("  harvester: " + HarvesterEntity.COST_PER_HARVEST
                + " a block, " + fullField + " for the whole field against a "
                + HarvesterEntity.CAPACITY + " buffer; a sweep takes "
                + ticksPerSweep + " ticks");
    }

    /**
     * The tree harvester's bounds, which are the whole safety argument.
     *
     * A machine that clears a connected mass of logs is one bad constant away
     * from being a tool that eats a player's warehouse, and none of the three
     * limits that stop it can be checked by running the game — you would have
     * to build the warehouse first.
     */
    private static void theTreeHarvesterBounds() {
        int side = TreeHarvesterEntity.RADIUS * 2 + 1;
        check(TreeHarvesterEntity.fieldSize() == side * side,
                "the tree harvester's field is " + TreeHarvesterEntity.fieldSize()
                        + ", not the " + side * side + " its radius covers");

        // The sweep, on the same terms as the crop harvester's.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int index = 0; index < TreeHarvesterEntity.fieldSize(); index++) {
            net.minecraft.core.BlockPos offset = TreeHarvesterEntity.offsetFor(index);
            check(offset.getY() == 0,
                    "the tree sweep left its own level at index " + index);
            check(Math.abs(offset.getX()) <= TreeHarvesterEntity.RADIUS
                            && Math.abs(offset.getZ()) <= TreeHarvesterEntity.RADIUS,
                    "the tree sweep reached " + offset + ", outside its radius");
            check(seen.add(offset.getX() + "," + offset.getZ()),
                    "the tree sweep visited " + offset + " twice in one pass");
        }
        check(seen.size() == TreeHarvesterEntity.fieldSize(),
                "one tree sweep covers " + seen.size() + " of "
                        + TreeHarvesterEntity.fieldSize() + " columns");
        for (int index : new int[] {TreeHarvesterEntity.fieldSize() * 5 + 2,
                                    Integer.MAX_VALUE, -1}) {
            net.minecraft.core.BlockPos offset = TreeHarvesterEntity.offsetFor(index);
            check(Math.abs(offset.getX()) <= TreeHarvesterEntity.RADIUS
                            && Math.abs(offset.getZ()) <= TreeHarvesterEntity.RADIUS,
                    "tree cursor " + index + " maps outside the field");
        }

        // The cap has to be above every vanilla tree and far below a build.
        // A large jungle tree is roughly two hundred logs; a dark oak about a
        // hundred. Anything a player stacks on purpose is thousands.
        check(TreeHarvesterEntity.MAX_TREE >= 256,
                "the log cap is " + TreeHarvesterEntity.MAX_TREE
                        + ", below a large jungle tree — the machine would "
                        + "refuse the biggest trees in the game");
        check(TreeHarvesterEntity.MAX_TREE <= 2048,
                "the log cap is " + TreeHarvesterEntity.MAX_TREE
                        + ", high enough that a player's log warehouse reads as "
                        + "a tree and is felled");

        // A canopy requirement of zero would make every log wall a tree.
        check(TreeHarvesterEntity.LEAVES_REQUIRED > 0,
                "no leaves are required, so a stack of logs is a tree and the "
                        + "machine will fell somebody's cabin");
        check(TreeHarvesterEntity.LEAVES_REQUIRED <= 16,
                "the machine wants " + TreeHarvesterEntity.LEAVES_REQUIRED
                        + " leaves, which a small or partly decayed tree may "
                        + "not have — it would leave real trees standing");

        System.out.println("  tree harvester: a " + side + "x" + side + " stand, "
                + "logs capped at " + TreeHarvesterEntity.MAX_TREE + ", "
                + TreeHarvesterEntity.LEAVES_REQUIRED + " leaves required "
                + "before anything is a tree");
    }

    /** Felling a tree has to cost more than cutting a wheat block. */
    private static void theTreeHarvesterCosts() {
        check(TreeHarvesterEntity.COST_PER_TREE > HarvesterEntity.COST_PER_HARVEST,
                "a whole tree costs " + TreeHarvesterEntity.COST_PER_TREE
                        + " and one wheat costs "
                        + HarvesterEntity.COST_PER_HARVEST
                        + " — a hundred logs should not be the cheaper job");

        int fullStand = TreeHarvesterEntity.COST_PER_TREE
                * TreeHarvesterEntity.fieldSize();
        check(fullStand > TreeHarvesterEntity.CAPACITY / 4,
                "clearing the whole stand costs " + fullStand + " against a "
                        + TreeHarvesterEntity.CAPACITY + " buffer, so the "
                        + "machine never meaningfully draws power");

        int ticksPerSweep = TreeHarvesterEntity.fieldSize()
                / TreeHarvesterEntity.SCAN_PER_TICK;
        check(ticksPerSweep < 200,
                "one sweep of the stand takes " + ticksPerSweep + " ticks with "
                        + "nothing to cut");

        System.out.println("  tree harvester: "
                + TreeHarvesterEntity.COST_PER_TREE + " a tree against "
                + HarvesterEntity.COST_PER_HARVEST + " a crop, " + fullStand
                + " for the whole stand; a sweep takes " + ticksPerSweep + " ticks");
    }

        /** Transfer has to be able to move what is made. */
    private static void transferKeepsUp() {
        check(SolarCondenserEntity.TRANSFER >= SolarCondenserEntity.PEAK_OUTPUT,
                "a condenser makes " + SolarCondenserEntity.PEAK_OUTPUT
                        + "/t and will only hand over "
                        + SolarCondenserEntity.TRANSFER
                        + "/t, so its buffer fills and the surplus is lost");
        check(WindCapstanEntity.TRANSFER >= WindCapstanEntity.PEAK_OUTPUT,
                "a capstan makes " + WindCapstanEntity.PEAK_OUTPUT
                        + "/t and will only hand over "
                        + WindCapstanEntity.TRANSFER
                        + "/t, so its buffer fills and the surplus is lost");
    }


    // ======================================================================
    // The power budget
    // ======================================================================
    //
    // This is the part of C1 that is pure arithmetic, and the part whose bugs
    // are silent. A budget that leaks a unit a tick destroys 1200 an hour and
    // nothing ever prints an error; a budget that serves a band out of order
    // looks fine in every screenshot and only shows up as "my water stopped
    // before my lamps did", months later, in somebody else's world.

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    /** Nothing is created and nothing is destroyed, at any supply. */
    private static void budgetConserves() {
        int[] ranks = {0, 0, 2, 3, 3, 4};
        int[] wants = {10, 7, 13, 20, 20, 5};
        int totalWant = sum(wants);
        for (int supply = 0; supply <= totalWant + 25; supply++) {
            int[] grants = PowerBudget.allocate(supply, ranks, wants);
            int given = sum(grants);
            check(given == Math.min(supply, totalWant),
                    "at supply " + supply + " the budget handed out " + given
                            + ", expected " + Math.min(supply, totalWant));
            for (int i = 0; i < grants.length; i++) {
                check(grants[i] >= 0 && grants[i] <= wants[i],
                        "receiver " + i + " got " + grants[i]
                                + " against a want of " + wants[i]
                                + " at supply " + supply);
            }
        }
    }

    /**
     * A band is filled before the next one is touched.
     *
     * Walked one unit at a time across the whole range rather than spot-checked,
     * because the failure this is guarding against is an off-by-one at exactly
     * the supply where a band fills — which is one value out of several hundred
     * and is precisely the one a hand-picked example would miss.
     */
    private static void budgetShedsByClass() {
        int[] ranks = {0, 2, 3, 4};
        int[] wants = {30, 30, 30, 30};
        for (int supply = 0; supply <= 120; supply++) {
            int[] grants = PowerBudget.allocate(supply, ranks, wants);
            for (int i = 1; i < grants.length; i++) {
                if (grants[i] > 0) {
                    check(grants[i - 1] == wants[i - 1],
                            "at supply " + supply + " class rank " + ranks[i]
                                    + " was given " + grants[i]
                                    + " while rank " + ranks[i - 1]
                                    + " was still short ("
                                    + grants[i - 1] + " of " + wants[i - 1] + ")");
                }
            }
        }
    }

    /** Equal machines in one band are treated equally, to within a unit. */
    private static void budgetSharesWithinAClass() {
        int[] ranks = {3, 3, 3};
        int[] wants = {40, 40, 40};
        for (int supply = 0; supply <= 120; supply++) {
            int[] grants = PowerBudget.allocate(supply, ranks, wants);
            int low = Math.min(grants[0], Math.min(grants[1], grants[2]));
            int high = Math.max(grants[0], Math.max(grants[1], grants[2]));
            check(high - low <= 1,
                    "three identical kilns at supply " + supply + " got "
                            + grants[0] + "/" + grants[1] + "/" + grants[2]
                            + " — one of them is being starved for its position");
        }
    }

    /**
     * A band nobody in it can use does not swallow the supply.
     *
     * The case: a critical machine whose buffer is full wants nothing, and the
     * industry behind it must still be fed. An implementation that reserved the
     * band's capacity rather than its demand would deadlock a base every time
     * its water tank topped up — the machine that matters most would be the one
     * stopping everything else.
     */
    private static void budgetSkipsAFullBand() {
        int[] ranks = {0, 3};
        int[] wants = {0, 50};
        int[] grants = PowerBudget.allocate(50, ranks, wants);
        check(grants[0] == 0 && grants[1] == 50,
                "a full critical machine held back the whole grid: "
                        + grants[0] + "/" + grants[1]);
    }

    /** Big numbers: the proportional share must not overflow an int. */
    private static void budgetSurvivesLargeBuffers() {
        int[] ranks = {3, 3};
        int[] wants = {1_000_000, 2_000_000};
        int[] grants = PowerBudget.allocate(1_500_000, ranks, wants);
        check(sum(grants) == 1_500_000,
                "a large grid lost energy to overflow: handed out " + sum(grants));
        check(grants[0] > 0 && grants[1] > grants[0],
                "the larger appetite should get the larger share: "
                        + grants[0] + "/" + grants[1]);
    }

    /** The same base twice gives the same answer. */
    private static void budgetIsDeterministic() {
        int[] ranks = {0, 3, 3, 2, 4};
        int[] wants = {9, 17, 23, 4, 31};
        int[] first = PowerBudget.allocate(41, ranks, wants);
        for (int repeat = 0; repeat < 8; repeat++) {
            int[] again = PowerBudget.allocate(41, ranks, wants);
            for (int i = 0; i < first.length; i++) {
                check(first[i] == again[i],
                        "the budget is not deterministic at receiver " + i);
            }
        }
    }

    /** The ranks are the spec's order, and nothing else decides it. */
    private static void classesRankInSpecOrder() {
        check(PowerClass.CRITICAL.outranks(PowerClass.DEFENSE)
                        && PowerClass.DEFENSE.outranks(PowerClass.LOGISTICS)
                        && PowerClass.LOGISTICS.outranks(PowerClass.INDUSTRY)
                        && PowerClass.INDUSTRY.outranks(PowerClass.COMFORT),
                "the power classes are no longer in the spec's priority order");
        check(PowerClass.UNLABELLED != PowerClass.CRITICAL
                        && PowerClass.UNLABELLED != PowerClass.COMFORT,
                "a machine from another mod is being ranked at one extreme or "
                        + "the other; it should sit in the middle");
    }

    /**
     * The scenario the whole feature exists for, played out.
     *
     * One reclaimer, one harvester and two kilns on a grid that can pay for
     * about half of them. Printed as well as checked, because the shape of the
     * shedding is the thing a player feels and an assertion cannot show it.
     */
    private static void aCloudyAfternoon() {
        PowerClass[] kinds = {PowerClass.CRITICAL, PowerClass.LOGISTICS,
                              PowerClass.INDUSTRY, PowerClass.INDUSTRY};
        int[] wants = {8, 12, 20, 20};
        int full = sum(wants);
        System.out.println("  a cloudy afternoon (reclaimer 8, harvester 12, "
                + "two kilns 20 each, " + full + " to be fully fed):");
        for (int supply : new int[]{full, 40, 25, 15, 8, 4}) {
            int[] grants = PowerBudget.allocate(supply, kinds, wants);
            System.out.println("    " + String.format("%3d", supply)
                    + " in: reclaimer " + grants[0] + ", harvester " + grants[1]
                    + ", kilns " + grants[2] + "+" + grants[3]);
        }
        int[] lean = PowerBudget.allocate(8, kinds, wants);
        check(lean[0] == 8 && lean[1] == 0 && lean[2] == 0 && lean[3] == 0,
                "on the worst afternoon the water must still run and nothing "
                        + "else should: " + lean[0] + "/" + lean[1] + "/"
                        + lean[2] + "/" + lean[3]);
        int[] half = PowerBudget.allocate(40, kinds, wants);
        check(half[0] == 8 && half[1] == 12 && half[2] == 10 && half[3] == 10,
                "at 40 the water and the field should be whole and the kilns "
                        + "should share what is left evenly: " + half[0] + "/"
                        + half[1] + "/" + half[2] + "/" + half[3]);
    }


    // ======================================================================
    // The automation rules
    // ======================================================================
    //
    // Small vocabulary, large claims: that the conditions are exhaustive and
    // consistent, that rules combine without order mattering, and that a rule
    // can never make a base *more* power-hungry. All three are the sort of
    // thing that is obviously true while writing it and quietly false a month
    // later.

    /** Every state a grid can be in, for exhaustive walks. */
    private static java.util.List<GridState> everyState() {
        java.util.List<GridState> states = new java.util.ArrayList<>();
        for (boolean daylight : new boolean[]{false, true}) {
            for (boolean raining : new boolean[]{false, true}) {
                for (int percent = 0; percent <= 100; percent++) {
                    states.add(new GridState(daylight, raining, percent / 100.0F));
                }
            }
        }
        return states;
    }

    /**
     * Day and night, rain and clear, are exact opposites at every state.
     *
     * Written as a partition rather than as two spot checks, because the way
     * this breaks is somebody adding a third weather and making both false at
     * once — at which point a base with "hold Industry while raining" and
     * "hold nothing while clear" silently does neither.
     */
    private static void conditionsPartition() {
        for (GridState state : everyState()) {
            check(PowerCondition.DAY.holds(state) != PowerCondition.NIGHT.holds(state),
                    "day and night are both " + PowerCondition.DAY.holds(state)
                            + " at " + state);
            check(PowerCondition.RAINING.holds(state)
                            != PowerCondition.CLEAR.holds(state),
                    "raining and clear agree at " + state);
            check(PowerCondition.ALWAYS.holds(state),
                    "ALWAYS did not hold at " + state);
        }
    }

    /** The charge thresholds are ordered and never contradict each other. */
    private static void chargeThresholdsAreOrdered() {
        for (GridState state : everyState()) {
            boolean quarter = PowerCondition.CHARGE_UNDER_QUARTER.holds(state);
            boolean half = PowerCondition.CHARGE_UNDER_HALF.holds(state);
            boolean high = PowerCondition.CHARGE_OVER_THREE_QUARTERS.holds(state);
            check(!quarter || half,
                    "under a quarter but not under half at " + state.charge());
            check(!(half && high),
                    "under half and over three quarters at once at "
                            + state.charge());
        }
        check(PowerCondition.CHARGE_UNDER_QUARTER.holds(
                        new GridState(false, false, 0.0F)),
                "an empty grid does not read as under a quarter");
        check(PowerCondition.CHARGE_OVER_THREE_QUARTERS.holds(
                        new GridState(false, false, 1.0F)),
                "a full grid does not read as over three quarters");
    }

    /** A charge outside 0..1 is clamped rather than firing a rule. */
    private static void chargeIsClamped() {
        check(new GridState(false, false, 5.0F).charge() == 1.0F,
                "an over-full measurement is not clamped");
        check(new GridState(false, false, -3.0F).charge() == 0.0F,
                "a negative measurement is not clamped");
        check(PowerGrid.Survey.class != null, "the survey type is gone");
    }

    /** Holding a class holds everything below it, and nothing above. */
    private static void holdingIsDownward() {
        for (PowerClass kind : PowerClass.values()) {
            java.util.Set<PowerClass> held =
                    new PowerRule(PowerCondition.ALWAYS, kind).heldClasses();
            for (PowerClass other : PowerClass.values()) {
                boolean shouldHold = other.rank() >= kind.rank();
                check(held.contains(other) == shouldHold,
                        "holding " + kind + " " + (shouldHold ? "missed " : "caught ")
                                + other);
            }
        }
        check(new PowerRule(PowerCondition.ALWAYS, PowerClass.CRITICAL)
                        .heldClasses().size() == PowerClass.values().length,
                "holding CRITICAL does not stop everything; that is the base's "
                        + "main switch and it has to work");
    }

    /**
     * Rules combine by union, so the order they are found in cannot matter.
     *
     * The load-bearing property of the whole design: governors are found by
     * walking blocks, and a walk order is not something a player can see. Run
     * over every permutation of a four-rule set at every state rather than
     * argued from the code.
     */
    private static void rulesDoNotCareAboutOrder() {
        java.util.List<PowerRule> rules = java.util.List.of(
                new PowerRule(PowerCondition.NIGHT, PowerClass.INDUSTRY),
                new PowerRule(PowerCondition.RAINING, PowerClass.LOGISTICS),
                new PowerRule(PowerCondition.CHARGE_UNDER_QUARTER, PowerClass.DEFENSE),
                new PowerRule(PowerCondition.DAY, PowerClass.COMFORT));
        java.util.List<java.util.List<PowerRule>> orders = new java.util.ArrayList<>();
        permute(new java.util.ArrayList<>(rules), 0, orders);
        for (GridState state : everyState()) {
            java.util.Set<PowerClass> first = PowerRule.held(orders.get(0), state);
            for (java.util.List<PowerRule> order : orders) {
                check(PowerRule.held(order, state).equals(first),
                        "the same rules in a different order held a different "
                                + "set at " + state);
            }
        }
        System.out.println("  rules: " + orders.size() + " orderings of 4 rules "
                + "across " + everyState().size() + " grid states, all agreeing");
    }

    private static void permute(java.util.List<PowerRule> rules, int at,
                                java.util.List<java.util.List<PowerRule>> into) {
        if (at == rules.size()) {
            into.add(new java.util.ArrayList<>(rules));
            return;
        }
        for (int i = at; i < rules.size(); i++) {
            java.util.Collections.swap(rules, at, i);
            permute(rules, at + 1, into);
            java.util.Collections.swap(rules, at, i);
        }
    }

    /** No rule set can ever let more classes run than no rules at all. */
    private static void rulesOnlySubtract() {
        for (GridState state : everyState()) {
            check(PowerRule.held(java.util.List.of(), state).isEmpty(),
                    "an empty rule set held something at " + state);
            check(PowerRule.held(null, state).isEmpty(),
                    "a null rule set threw or held something");
        }
    }

    /** A held class is given nothing, and its share goes to what is running. */
    private static void aHeldClassFreesItsShare() {
        PowerClass[] kinds = {PowerClass.CRITICAL, PowerClass.INDUSTRY};
        int[] wantsAll = {10, 30};
        int[] wantsHeld = {10, 0};          // industry held: wants nothing
        int[] free = PowerBudget.allocate(40, kinds, wantsAll);
        int[] gated = PowerBudget.allocate(40, kinds, wantsHeld);
        check(free[1] == 30 && gated[1] == 0,
                "holding industry did not stop it: " + gated[1]);
        check(gated[0] == 10,
                "the critical machine's own share changed when industry was "
                        + "held: " + gated[0]);
        // And with the critical machine already full, a held industry means the
        // generator simply keeps its charge rather than spending it.
        int[] idle = PowerBudget.allocate(40, kinds, new int[]{0, 0});
        check(sum(idle) == 0,
                "a fully held grid still spent " + sum(idle));
    }

    /** The governor's default is the safest rule in the language. */
    private static void theDefaultRuleIsHarmless() {
        PowerRule fresh = new PowerRule(PowerCondition.ALWAYS, PowerClass.COMFORT);
        check(!fresh.heldClasses().contains(PowerClass.CRITICAL),
                "a governor straight out of the inventory can stop a base's "
                        + "water supply before anybody has configured it");
        check(fresh.heldClasses().contains(PowerClass.COMFORT),
                "a fresh governor does nothing at all, so a player cannot tell "
                        + "it works");
    }

    /** Every condition and class is reachable by clicking. */
    private static void everythingIsReachableByClicking() {
        for (int click = 0; click < PowerCondition.values().length * 3; click++) {
            check(PowerCondition.byIndex(click) != null,
                    "cycling past " + click + " clicks gave nothing");
        }
        java.util.Set<PowerCondition> seen = new java.util.HashSet<>();
        for (int click = 0; click < PowerCondition.values().length; click++) {
            seen.add(PowerCondition.byIndex(click));
        }
        check(seen.size() == PowerCondition.values().length,
                "one lap of clicking reaches only " + seen.size() + " of "
                        + PowerCondition.values().length + " conditions");
        check(PowerCondition.byIndex(-1) == PowerCondition.values()[
                        PowerCondition.values().length - 1],
                "cycling backwards past zero does not wrap");
    }

    /**
     * What mines a block: the rules answer for every material the table uses
     * and for the glass, and the rule that reads a tag reports the three ways
     * a list can be wrong. A block in no tool's tag breaks at a fist's pace.
     */
    private static void theHarvest() {
        java.util.List<String> tools = com.elysium.solarpunk.rules.HarvestRules.TOOLS;
        check(tools.contains(com.elysium.solarpunk.rules.HarvestRules.MACHINE_TOOL),
                "the machines are mined by a tool the game keeps no list for");
        for (String tool : com.elysium.solarpunk.rules.HarvestRules.TOOL_OF_MATERIAL.values()) {
            check(tools.contains(tool), "a material is mined by " + tool
                    + ", which is no tool the game keeps a list for");
        }
        check(com.elysium.solarpunk.rules.HarvestRules.toolFor("bloom_glass", "bloom", false)
                .isEmpty(), "the glass is given a tool, and glass breaks by hand");
        check(com.elysium.solarpunk.rules.HarvestRules.toolFor("bloom_lantern", "bloom", false)
                .equals("pickaxe"), "the lantern is not a pickaxe's");
        check(com.elysium.solarpunk.rules.HarvestRules.toolFor("solar_condenser", "brass", true)
                .equals(com.elysium.solarpunk.rules.HarvestRules.MACHINE_TOOL),
                "a machine is not mined by the machines' tool");
        boolean refused = false;
        try {
            com.elysium.solarpunk.rules.HarvestRules.toolFor("bog_brick", "peat", false);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a material nothing decides a tool for is given one anyway");
        // The controls on the tag reader: a block no tool is decided for, one
        // decided twice, and a name that is no block of ours are each reported.
        java.util.List<String> blocks = java.util.List.of("rammed_earth", "living_wall", "bloom_glass");
        java.util.Map<String, java.util.List<String>> minedBy = java.util.Map.of(
                "pickaxe", java.util.List.of("rammed_earth"), "axe", java.util.List.of("living_wall"));
        java.util.List<String> byHand = java.util.List.of("bloom_glass");
        check(com.elysium.solarpunk.rules.HarvestRules.untooled(blocks, minedBy, byHand).isEmpty(),
                "a list that answers once for every block is reported anyway");
        check(!com.elysium.solarpunk.rules.HarvestRules.untooled(
                java.util.List.of("rammed_earth", "living_wall", "bloom_glass", "bog_brick"),
                minedBy, byHand).isEmpty(), "a block no tool is decided for is not reported");
        check(!com.elysium.solarpunk.rules.HarvestRules.untooled(blocks, minedBy,
                java.util.List.of("bloom_glass", "rammed_earth")).isEmpty(),
                "a block mined by a tool and broken by hand is not reported");
        check(!com.elysium.solarpunk.rules.HarvestRules.untooled(blocks,
                java.util.Map.of("pickaxe", java.util.List.of("rammed_earth", "bog_brick"),
                        "axe", java.util.List.of("living_wall")), byHand).isEmpty(),
                "a tool given to a block nobody registers is not reported");
    }

    public static void main(String[] args) {
        condenserCurve();
        capstanCurve();
        generatorsDiffer();
        reclaimerRate();
        reclaimerIsAffordable();
        kilnSavesFuel();
        theHarvesterSweep();
        theTreeHarvesterBounds();
        theTreeHarvesterCosts();
        theHarvesterCosts();
        tankSiting();
        tankFish();
        recipeCostRoundsUp();
        chainPaysForItself();
        loopCloses();
        transferKeepsUp();
        budgetConserves();
        budgetShedsByClass();
        budgetSharesWithinAClass();
        budgetSkipsAFullBand();
        budgetSurvivesLargeBuffers();
        budgetIsDeterministic();
        classesRankInSpecOrder();
        aCloudyAfternoon();
        conditionsPartition();
        chargeThresholdsAreOrdered();
        chargeIsClamped();
        holdingIsDownward();
        rulesDoNotCareAboutOrder();
        rulesOnlySubtract();
        aHeldClassFreesItsShare();
        theDefaultRuleIsHarmless();
        everythingIsReachableByClicking();
        theHarvest();

        System.out.println("machines: two generator curves, the choice between "
                + "them, the reclaimer's rate, the kiln's saving, the tank's "
                + "siting, both harvesters' bounds, the chain, "
                + "the recipe rounding "
                + "and the buffers, and the power budget's conservation, "
                + "shedding order and shares, and the rule vocabulary's "
                + "partitions, downward holding and order-independence — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("the loop closes");
    }
}
