package com.elysium.atomics.rules;

import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.rules.KineticRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The third age, executed.
 *
 * <p>Three claims are worth more than all the others here, because each of
 * them would be invisible until it had already ruined a save: that a cell
 * cannot give back more than was put into it, that a reactor settles rather
 * than running away, and that a servo driving an alternator cannot pay for
 * itself. The first two are swept over thousands of random states; the third
 * is proved in closed form and then checked against the age below's own
 * solver, with the two ages' motors mixed on one line.
 */
public final class AtomicHarness {

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

    /** A list of faults, one line each, so no complaint buries another. */
    private static void none(java.util.List<String> faults, String what) {
        check(faults.isEmpty(),
                what + ": " + (faults.isEmpty() ? "" : faults.get(0)));
        for (int at = 1; at < faults.size(); at++) {
            checks++;
            failures++;
            System.out.println("  FAIL: " + what + ": " + faults.get(at));
        }
    }

    private static void near(float got, float want, float slack, String what) {
        check(Math.abs(got - want) <= slack,
                what + ": expected about " + want + ", got " + got);
    }

    public static void main(String[] args) {
        theCells();
        theBuffer();
        theEdges();
        theBank();
        theHeat();
        theReactor();
        theServo();
        theLadder();
        theShaft();
        theHalls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a cell never returns more than was put in "
                + "it, a reactor settles instead of running away and is fatal without "
                + "coolant, a servo driving alternators cannot pay for itself at any "
                + "gearing or mixture, and the ore ladder is 1, 2, 3");
    }

    // ==================================================================

    private static void theCells() {
        List<AtomicRules.Cell> ladder = List.of(AtomicRules.BASIC_CELL,
                AtomicRules.ADVANCED_CELL, AtomicRules.QUANTUM_CELL);
        for (int index = 1; index < ladder.size(); index++) {
            AtomicRules.Cell below = ladder.get(index - 1);
            AtomicRules.Cell here = ladder.get(index);
            check(here.capacity() > below.capacity(),
                    "a cell tier holds no more than the one under it");
            check(here.mostOut() >= below.mostOut(),
                    "a cell tier moves power no faster than the one under it");
        }
        for (AtomicRules.Cell cell : ladder) {
            check(cell.mostIn() <= IndustryRules.MOST_FE
                            && cell.mostOut() <= IndustryRules.MOST_FE,
                    "a cell moves more power than the best wire in the game carries, so "
                            + "its only possible use is burning that wire");
            check(cell.fillsIn() > 0, "a cell fills in no time");
        }
        near(AtomicRules.QUANTUM_CELL.mostOut(), IndustryRules.MOST_FE, 0.001F,
                "the top cell does not saturate the top wire exactly");

        boolean refused = false;
        try {
            new AtomicRules.Cell(1000, IndustryRules.MOST_FE * 2.0F, 1.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a cell that moves more than any wire carries can be declared");

        refused = false;
        try {
            new AtomicRules.Cell(0, 1.0F, 1.0F);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a cell that holds nothing can be declared");
    }

    // ==================================================================

    private static void theBuffer() {
        IndustryRules.Voltage wire = IndustryRules.Voltage.EXTREME;
        List<AtomicRules.Cell> one = List.of(AtomicRules.BASIC_CELL);

        // Short, with something in the bank.
        AtomicRules.Buffered drawing = AtomicRules.settle(100.0F, 300.0F, one,
                List.of(10_000), wire);
        check(drawing.draining() && !drawing.charging(),
                "a short grid with a full cell on it does not draw from the cell");
        near(drawing.fromCells(), 200.0F, 0.001F,
                "a short grid draws something other than the shortfall from its cells");
        AtomicRules.Buffered starved = AtomicRules.settle(100.0F, 9_000.0F, one,
                List.of(1_000_000), wire);
        near(starved.fromCells(), AtomicRules.BASIC_CELL.mostOut(), 0.001F,
                "a grid short by more than a cell can deliver draws more than the cell "
                        + "will give");
        check(starved.share() < 1.0F,
                "a cell that cannot keep up still runs the grid at full speed");
        check(drawing.share() > 100.0F / 300.0F,
                "a cell on a short grid makes no difference to what the machines get");

        // Long, with room in the bank.
        AtomicRules.Buffered filling = AtomicRules.settle(500.0F, 100.0F, one,
                List.of(0), wire);
        check(filling.charging() && !filling.draining(),
                "a grid with a surplus does not put it anywhere");
        near(filling.share(), 1.0F, 0.001F,
                "a grid with a surplus does not run its machines at full speed");

        // Exactly matched: nothing moves either way.
        AtomicRules.Buffered level = AtomicRules.settle(200.0F, 200.0F, one,
                List.of(5_000), wire);
        check(!level.charging() && !level.draining(),
                "a grid that is exactly fed still moves power in or out of its cells");

        // An empty cell cannot help.
        AtomicRules.Buffered empty = AtomicRules.settle(100.0F, 300.0F, one,
                List.of(0), wire);
        near(empty.fromCells(), 0.0F, 0.001F, "an empty cell gives power away");

        // A full cell cannot take.
        AtomicRules.Buffered full = AtomicRules.settle(500.0F, 100.0F, one,
                List.of(AtomicRules.BASIC_CELL.capacity()), wire);
        near(full.toCells(), 0.0F, 0.001F, "a full cell takes more power in");

        // A cell does not make a wire safe. What flows through it is what a
        // wire burns on, whether it is on its way to a machine or to storage.
        IndustryRules.Voltage thin = IndustryRules.Voltage.LOW;
        int overWire = thin.capacity() * 2;
        AtomicRules.Buffered stuffing = AtomicRules.settle(overWire, 0.0F,
                List.of(AtomicRules.QUANTUM_CELL), List.of(0), thin);
        check(stuffing.burned(),
                "a surplus far past what the wire carries does not burn it as long as it "
                        + "is on its way into a battery");
        AtomicRules.Buffered emptying = AtomicRules.settle(0.0F, overWire,
                List.of(AtomicRules.QUANTUM_CELL), List.of(overWire * 10), thin);
        check(emptying.burned(),
                "a cell discharging far past what the wire carries does not burn it");
        check(emptying.share() == 0.0F && emptying.delivered() == 0.0F,
                "a grid whose wire has burned still runs its machines");
        AtomicRules.Buffered gentle = AtomicRules.settle(100.0F, 100.0F,
                List.of(AtomicRules.QUANTUM_CELL), List.of(50_000), thin);
        check(!gentle.burned(),
                "a wire well within its rating burns because there is a cell on the grid");

        // No cells at all: the same answer Age II would give.
        for (float supply : new float[] {0.0F, 50.0F, 100.0F, 400.0F}) {
            AtomicRules.Buffered bare = AtomicRules.settle(supply, 200.0F, List.of(),
                    List.of(), wire);
            IndustryRules.Reading aged = new IndustryRules.Reading(supply, 200.0F, false);
            near(bare.share(), aged.share(), 0.001F,
                    "a grid with no cells on it behaves differently from Age II's, so a "
                            + "machine has to know whether there is a cell behind it");
        }
    }

    /**
     * The two ends of a cell, which a random walk never reaches.
     *
     * <p>A bank driven by random supply and demand wanders by a few hundred
     * units a tick against a capacity of a million, so it spends its whole
     * life in the middle and the code that handles "nearly full" and "nearly
     * empty" is never run. Those are exactly the two places a cell can be made
     * to overflow or go negative, so they are visited on purpose.
     */
    private static void theEdges() {
        IndustryRules.Voltage wire = IndustryRules.Voltage.EXTREME;
        for (AtomicRules.Cell cell : List.of(AtomicRules.BASIC_CELL,
                AtomicRules.ADVANCED_CELL, AtomicRules.QUANTUM_CELL)) {
            List<AtomicRules.Cell> one = List.of(cell);
            int capacity = cell.capacity();
            int[] corners = {
                0, 1, 2,
                (int) cell.mostOut() - 1, (int) cell.mostOut(), (int) cell.mostOut() + 1,
                capacity / 2,
                capacity - (int) cell.mostIn() - 1, capacity - (int) cell.mostIn(),
                capacity - 2, capacity - 1, capacity,
            };
            for (int held : corners) {
                if (held < 0 || held > capacity) {
                    continue;
                }
                List<Integer> stored = List.of(held);
                float couldGive = AtomicRules.couldGive(one, stored);
                float couldTake = AtomicRules.couldTake(one, stored);

                // Asked for far more than it has.
                AtomicRules.Buffered drawn = AtomicRules.settle(0.0F, capacity, one,
                        stored, wire);
                int afterDrawn = AtomicRules.after(cell, held, drawn, couldGive,
                        couldTake);
                check(afterDrawn >= 0, "a cell holding " + held + " of " + capacity
                        + " went to " + afterDrawn + " when the grid drew on it");
                check(afterDrawn <= held, "a cell gained power while being drained");
                check(held - afterDrawn >= (long) Math.floor(drawn.fromCells()),
                        "a cell holding " + held + " gave up less than the "
                                + drawn.fromCells() + " the grid was told it drew");

                // Offered far more than it can hold.
                AtomicRules.Buffered filled = AtomicRules.settle(capacity, 0.0F, one,
                        stored, wire);
                int afterFilled = AtomicRules.after(cell, held, filled, couldGive,
                        couldTake);
                check(afterFilled <= capacity, "a cell holding " + held + " of "
                        + capacity + " went to " + afterFilled + " when offered a "
                        + "surplus");
                check(afterFilled >= held, "a cell lost power while being charged");
                check(afterFilled - held <= (long) Math.ceil(filled.toCells()),
                        "a cell holding " + held + " took in more than the "
                                + filled.toCells() + " the grid offered it");

                // And the two ends refuse what they cannot do.
                if (held == 0) {
                    check(!drawn.draining(), "an empty cell supplied power");
                }
                if (held == capacity) {
                    check(!filled.charging(), "a full cell accepted power");
                }
            }
        }
    }

    /**
     * The bank, over time.
     *
     * A cell is the first thing in the pack that carries state between ticks,
     * and state between ticks is where conservation bugs live. So this runs
     * banks of mixed cells through thousands of random ticks and checks the
     * invariants after every one of them.
     */
    private static void theBank() {
        Random random = new Random(20260924L);
        IndustryRules.Voltage wire = IndustryRules.Voltage.EXTREME;
        List<AtomicRules.Cell> bank = List.of(AtomicRules.BASIC_CELL,
                AtomicRules.BASIC_CELL, AtomicRules.ADVANCED_CELL);

        int[] held = new int[bank.size()];
        for (int index = 0; index < held.length; index++) {
            held[index] = (int) (random.nextFloat() * bank.get(index).capacity());
        }

        boolean bothWays = false;
        boolean overflowed = false;
        boolean wentNegative = false;
        boolean invented = false;
        boolean shortChanged = false;
        boolean vanished = false;
        boolean overGave = false;
        double intoTheBank = 0.0;
        double outOfTheBank = 0.0;
        double started = 0.0;
        for (int amount : held) {
            started += amount;
        }

        for (int tick = 0; tick < 50_000; tick++) {
            float supply = random.nextFloat() * 8000.0F;
            float demand = random.nextFloat() * 8000.0F;
            List<Integer> stored = new ArrayList<>();
            for (int amount : held) {
                stored.add(amount);
            }

            AtomicRules.Buffered grid = AtomicRules.settle(supply, demand, bank, stored,
                    wire);
            if (grid.charging() && grid.draining()) {
                bothWays = true;
            }

            float couldGive = AtomicRules.couldGive(bank, stored);
            float couldTake = AtomicRules.couldTake(bank, stored);
            if (grid.fromCells() > couldGive + 0.01F) {
                overGave = true;
            }

            long was = 0;
            long now = 0;
            for (int index = 0; index < bank.size(); index++) {
                AtomicRules.Cell cell = bank.get(index);
                was += held[index];
                held[index] = AtomicRules.after(cell, held[index], grid, couldGive,
                        couldTake);
                now += held[index];
                if (held[index] < 0) {
                    wentNegative = true;
                }
                if (held[index] > cell.capacity()) {
                    overflowed = true;
                }
            }

            // Exactly, because these are whole units. A bank takes at most what
            // it was offered and gives up at least what was drawn from it.
            long moved = now - was;
            if (grid.charging() && moved > (long) Math.ceil(grid.toCells())) {
                invented = true;
            }
            if (grid.draining() && -moved < (long) Math.floor(grid.fromCells())) {
                shortChanged = true;
            }
            // And the mirror of it. The grid tells every machine on it that
            // this much came out of storage; if the bank did not actually
            // accept that much, the difference was destroyed and the readout
            // is describing a grid that does not exist.
            if (grid.charging()
                    && moved < (long) Math.floor(grid.toCells()) - bank.size()) {
                vanished = true;
            }
            if (!grid.charging() && !grid.draining() && moved != 0) {
                invented = true;                 // a settled grid moved power anyway
            }
            intoTheBank += grid.toCells();
            outOfTheBank += grid.fromCells();
        }

        double ended = 0.0;
        for (int amount : held) {
            ended += amount;
        }

        check(!bothWays, "a grid charged and drained its cells in the same tick, which is "
                + "a loop and is the one thing this whole file exists to prevent");
        check(!overGave, "a bank gave away more than its cells could deliver");
        check(!wentNegative, "a cell went below empty");
        check(!overflowed, "a cell went above full");
        check(!invented, "a bank ended a tick holding more than it was given");
        check(!shortChanged, "a bank gave up less than the grid was told it drew, so the "
                + "difference was made out of nothing");
        check(!vanished, "a bank took in less than the grid was told it stored, so the "
                + "difference was destroyed and the readout describes a grid that is not "
                + "there");
        check(ended <= started + intoTheBank - outOfTheBank + 1.0,
                "over fifty thousand ticks the bank ended with " + ended + " having "
                        + "started with " + started + ", taken in " + intoTheBank
                        + " and given out " + outOfTheBank + " — energy appeared");
        check(ended >= started + intoTheBank - outOfTheBank - bank.size() * 50_000.0,
                "the bank lost far more to rounding than one unit per cell per tick, so "
                        + "storage leaks");
    }

    // ==================================================================

    private static void theHeat() {
        // The readout and the reactor have to agree, at every cooling.
        for (int channels = 0; channels <= AtomicRules.MOST_CHANNELS; channels++) {
            float cooling = AtomicRules.coolingWith(channels);
            for (float heat : new float[] {0.0F, 1.0F, 20.0F, 82.0F, 163.84F}) {
                float temperature = AtomicRules.AMBIENT;
                for (int tick = 0; tick < 20_000; tick++) {
                    temperature = AtomicRules.nextTemperature(temperature, heat, cooling);
                }
                near(temperature, AtomicRules.settlesAt(heat, cooling), 0.5F,
                        "a core with " + channels + " channel(s) making " + heat
                                + " heat settles somewhere the readout does not predict");
            }
        }

        check(AtomicRules.coolingWith(AtomicRules.MOST_CHANNELS) < 1.0F,
                "a fully cooled core sheds more than its whole excess temperature in one "
                        + "tick, so its temperature oscillates instead of settling");
        check(AtomicRules.coolingWith(0) == AtomicRules.BARE_LOSS,
                "a bare core is not bare");
        for (int channels = 1; channels <= AtomicRules.MOST_CHANNELS; channels++) {
            check(AtomicRules.coolingWith(channels) > AtomicRules.coolingWith(channels - 1),
                    "the " + channels + "th coolant channel does nothing, so a player "
                            + "cannot see what plumbing one more buys them");
        }
        check(AtomicRules.coolingWith(AtomicRules.MOST_CHANNELS + 3)
                        == AtomicRules.coolingWith(AtomicRules.MOST_CHANNELS),
                "a core takes more channels than it says it does");

        // It must approach, never overshoot: a temperature that oscillated
        // would cross a threshold it never actually settles at.
        float cooling = AtomicRules.coolingWith(2);
        float fixed = AtomicRules.settlesAt(100.0F, cooling);
        float temperature = AtomicRules.AMBIENT;
        boolean overshot = false;
        for (int tick = 0; tick < 5_000; tick++) {
            float next = AtomicRules.nextTemperature(temperature, 100.0F, cooling);
            if (next > fixed + 0.01F || next < temperature - 0.01F) {
                overshot = true;
            }
            temperature = next;
        }
        check(!overshot, "a core's temperature overshoots or falls back on the way up, so "
                + "it crosses thresholds it does not settle at");

        // Cooling down again, from hot, with nothing being made.
        temperature = 1500.0F;
        for (int tick = 0; tick < 5_000; tick++) {
            temperature = AtomicRules.nextTemperature(temperature, 0.0F, cooling);
        }
        near(temperature, AtomicRules.AMBIENT, 0.5F,
                "a core that has been switched off does not cool back to ambient");

        check(AtomicRules.settlesAt(1.0F, 0.0F) == Float.POSITIVE_INFINITY,
                "a core with no cooling whatever settles at a temperature");

        // Wear.
        check(AtomicRules.wearAt(AtomicRules.SAFE) == 0.0F,
                "a core at exactly the safe temperature is already wearing out, so the "
                        + "number a player is shown is not the number the game uses");
        check(AtomicRules.wearAt(AtomicRules.AMBIENT) == 0.0F, "a cold core wears out");
        check(AtomicRules.wearAt(AtomicRules.CRITICAL) > 0.0F,
                "a critical core takes no damage");
        check(AtomicRules.wearAt(AtomicRules.MELTDOWN)
                        > AtomicRules.wearAt(AtomicRules.CRITICAL) * 2.0F,
                "the last stretch before meltdown costs no more than the first, so "
                        + "overshooting slightly and ignoring it entirely are the same "
                        + "mistake");
        near(AtomicRules.wearAt(AtomicRules.MELTDOWN), AtomicRules.WEAR_AT_MELTDOWN,
                0.0001F, "wear at meltdown is not what the constant says");
        check(AtomicRules.wearAt(AtomicRules.MELTDOWN * 4.0F)
                        == AtomicRules.wearAt(AtomicRules.MELTDOWN),
                "wear keeps climbing past meltdown, which is a temperature nothing "
                        + "survives to be worn by");
        check(AtomicRules.meltingDown(AtomicRules.MELTDOWN),
                "a core sitting exactly at meltdown has not melted down");
        check(!AtomicRules.meltingDown(AtomicRules.CRITICAL),
                "a critical core has already melted down, so the warning band does not "
                        + "exist");
        check(AtomicRules.SAFE < AtomicRules.CRITICAL
                        && AtomicRules.CRITICAL < AtomicRules.MELTDOWN,
                "the three temperature thresholds are not in order");
    }

    private static void theReactor() {
        // Bare and wide open: fatal, and by a wide margin.
        float bare = AtomicRules.settlesAt(AtomicRules.REACTOR, 1.0F, 0);
        check(bare > AtomicRules.MELTDOWN * 2.0F,
                "a reactor with no coolant at all settles at " + bare + ", which is "
                        + "survivable, so cooling is decoration");

        // Fully plumbed and wide open: fine, with something in hand.
        float cooled = AtomicRules.settlesAt(AtomicRules.REACTOR, 1.0F,
                AtomicRules.MOST_CHANNELS);
        check(cooled < AtomicRules.SAFE,
                "a fully cooled reactor at full throttle settles at " + cooled
                        + ", above the safe line, so there is no way to run one properly");
        check(cooled > AtomicRules.SAFE * 0.8F,
                "a fully cooled reactor at full throttle settles at " + cooled
                        + ", far below the safe line, so the last coolant channels are "
                        + "not decisions");

        // Every channel between those two matters, and the ladder is a ladder.
        float last = Float.POSITIVE_INFINITY;
        for (int channels = 1; channels <= AtomicRules.MOST_CHANNELS; channels++) {
            float at = AtomicRules.settlesAt(AtomicRules.REACTOR, 1.0F, channels);
            check(at < last, "plumbing the " + channels + "th channel does not make a "
                    + "reactor at full throttle any cooler");
            last = at;
        }
        check(AtomicRules.settlesAt(AtomicRules.REACTOR, 1.0F,
                AtomicRules.MOST_CHANNELS - 1) > AtomicRules.SAFE,
                "a reactor is safe at full throttle one channel short of full plumbing, "
                        + "so the last channel is optional");

        // What a player may do before they have plumbed anything.
        float bareThrottle = AtomicRules.throttleUnder(AtomicRules.REACTOR, 0,
                AtomicRules.SAFE);
        check(bareThrottle > 0.0F && bareThrottle < 0.25F,
                "a bare reactor may be run at " + bareThrottle + " of full, which is "
                        + "either no help at all when bringing one online or most of the "
                        + "way to not needing coolant");
        for (int channels = 0; channels <= AtomicRules.MOST_CHANNELS; channels++) {
            float allowed = AtomicRules.throttleUnder(AtomicRules.REACTOR, channels,
                    AtomicRules.SAFE);
            float reached = AtomicRules.settlesAt(AtomicRules.REACTOR, allowed, channels);
            check(reached <= AtomicRules.SAFE + 0.5F,
                    "the throttle the readout says is safe with " + channels
                            + " channel(s) settles at " + reached);
            if (allowed < 1.0F) {
                float over = AtomicRules.settlesAt(AtomicRules.REACTOR,
                        Math.min(1.0F, allowed + 0.02F), channels);
                check(over > AtomicRules.SAFE,
                        "the throttle the readout says is safe with " + channels
                                + " channel(s) is not the highest one that is");
            }
        }

        // The wiring lesson, one tier above Age II's arc furnace.
        check(AtomicRules.REACTOR.mostOutput() * 2.0F <= IndustryRules.MOST_FE,
                "two reactors cannot share the best wire in the game, so the second one "
                        + "is a dead end rather than a wiring problem");
        check(AtomicRules.REACTOR.mostOutput() * 3.0F > IndustryRules.MOST_FE,
                "three reactors fit down one wire, so nothing in the age ever forces a "
                        + "second run of cable");
        check(IndustryRules.burns(IndustryRules.Voltage.HIGH,
                        AtomicRules.REACTOR.mostOutput()),
                "one reactor's output fits down Age II's second-best wire, so reaching "
                        + "Age III costs no wiring at all");
        check(AtomicRules.reactorAt(1.0F).voltage() == IndustryRules.Voltage.EXTREME,
                "a reactor is not on the top tier of wire");
        near(AtomicRules.reactorAt(0.5F).output(),
                AtomicRules.REACTOR.mostOutput() / 2.0F, 0.001F,
                "a reactor at half throttle does not make half the power");
        near(AtomicRules.reactorAt(-1.0F).output(), 0.0F, 0.001F,
                "a reactor at a negative throttle makes power");
        near(AtomicRules.reactorAt(5.0F).output(), AtomicRules.REACTOR.mostOutput(),
                0.001F, "a reactor can be throttled past full");
    }

    // ==================================================================

    private static void theServo() {
        float best = AtomicRules.servoRoundTrip();
        check(best < 1.0F, "a servo driving alternators returns " + best + " of what it "
                + "drew, so Age III is a free-energy machine");
        near(best, IndustryRules.roundTripBest(), 0.001F,
                "Age III's loop does not return what Age II's does; the two are supposed "
                        + "to be pinned together so that one number governs the whole "
                        + "spine");

        // The closed form again, against arrangements built out of the servo.
        float highest = 0.0F;
        for (int servos = 1; servos <= 6; servos++) {
            for (int alternators = 1; alternators <= 6; alternators++) {
                for (int step = 1; step <= 120; step++) {
                    float ratio = step / 20.0F;
                    highest = Math.max(highest, IndustryRules.roundTrip(
                            AtomicRules.SERVO_FREE_RPM, AtomicRules.SERVO_STALL_TORQUE,
                            AtomicRules.SERVO_RATED_FE, servos, alternators, ratio));
                }
            }
        }
        check(highest <= best + 0.001F,
                "an arrangement of servos returns " + highest + ", which beats the closed "
                        + "form's " + best);

        // And now the case neither age's own proof covers: both motors on one
        // line, settled by Age I's own solver rather than by a formula.
        float mixed = 0.0F;
        String at = "nothing";
        for (int servos = 0; servos <= 4; servos++) {
            for (int motors = 0; motors <= 4; motors++) {
                if (servos + motors == 0) {
                    continue;
                }
                for (int alternators = 1; alternators <= 6; alternators++) {
                    List<KineticRules.Source> sources = new ArrayList<>();
                    for (int index = 0; index < servos; index++) {
                        sources.add(AtomicRules.servoAsSource(AtomicRules.SERVO_RATED_FE));
                    }
                    for (int index = 0; index < motors; index++) {
                        sources.add(IndustryRules.motorAsSource(
                                IndustryRules.MOTOR_RATED_FE));
                    }
                    List<KineticRules.Load> loads = new ArrayList<>();
                    for (int index = 0; index < alternators; index++) {
                        loads.add(IndustryRules.ALTERNATOR_LOAD);
                    }
                    KineticRules.Reading line = KineticRules.settle(sources, loads,
                            KineticRules.Shaft.ASCENDANT);
                    float made = alternators
                            * IndustryRules.alternatorOutput(line.rpm());
                    float drew = servos * AtomicRules.SERVO_RATED_FE
                            + motors * IndustryRules.MOTOR_RATED_FE;
                    float got = drew <= 0.0F ? 0.0F : made / drew;
                    if (got > mixed) {
                        mixed = got;
                        at = servos + " servo(s), " + motors + " motor(s), " + alternators
                                + " alternator(s)";
                    }
                }
            }
        }
        check(mixed < 1.0F, "mixing the ages' motors on one line — " + at + " — returns "
                + mixed + " of what it drew, which neither age's own proof covers");

        near(AtomicRules.SERVO_FREE_RPM, KineticRules.MAX_RPM, 0.001F,
                "a servo does not drive a line at the fastest speed in the pack, which is "
                        + "the whole reason to build one");
        check(AtomicRules.SERVO_FREE_RPM > IndustryRules.MOTOR_FREE_RPM,
                "a servo is no faster than Age II's motor");
        check(KineticRules.shears(KineticRules.Shaft.BRASS, AtomicRules.SERVO_FREE_RPM),
                "a servo at full speed does not shear a brass shaft, so reaching Age III "
                        + "costs nothing in the machine hall below");
        check(!KineticRules.shears(KineticRules.Shaft.ASCENDANT,
                        AtomicRules.SERVO_FREE_RPM),
                "nothing in the game survives a servo at full speed, so it cannot be "
                        + "used at all");
        near(AtomicRules.servoAsSource(0.0F).stallTorque(), 0.0F, 0.01F,
                "an unpowered servo still pushes");
        near(AtomicRules.servoAsSource(AtomicRules.SERVO_RATED_FE).stallTorque(),
                AtomicRules.SERVO_STALL_TORQUE, 0.01F,
                "a fully fed servo does not push with its rated torque");
    }

    // ==================================================================

    private static void theLadder() {
        check(AtomicRules.ENRICHER_MULTIPLIER == IndustryRules.SEPARATOR_MULTIPLIER + 1,
                "Age III's ore ladder is not one rung above Age II's");
        check(AtomicRules.ENRICHER_MULTIPLIER == 3,
                "Age III does not triple ore, which is what the spine says it does");

        for (IndustryRules.Machine machine : List.of(AtomicRules.ENRICHER,
                AtomicRules.REACTION_CHAMBER, AtomicRules.CENTRIFUGE, AtomicRules.SERVO)) {
            check(machine.rated().ordinal()
                            >= IndustryRules.Voltage.HIGH.ordinal(),
                    "an Age III machine runs off a wire Age II could have built, so "
                            + "nothing in the age forces the step up to steel");
            check(machine.draw() > IndustryRules.SEPARATOR.draw(),
                    "an Age III machine is cheaper to run than Age II's most expensive "
                            + "one, so the ages overlap rather than stack");
        }
        check(AtomicRules.ENRICHER.draw() < AtomicRules.REACTOR.mostOutput(),
                "one reactor cannot run one enricher, so the age's first machine needs "
                        + "the age's second reactor");

        check(AtomicRules.SOLAR_PANEL.output() < IndustryRules.STEAM_TURBINE.output() / 20,
                "a solar panel is a serious generator, so a roof of them replaces the "
                        + "age rather than making its cells worth building");
        check(AtomicRules.SOLAR_PANEL.output() > 0.0F, "a solar panel makes nothing");
        check(AtomicRules.SOLAR_PANEL.voltage() == IndustryRules.Voltage.LOW,
                "a solar panel needs a wire a player would not have when they build one");
        near(AtomicRules.SOLAR_SHADED, 0.0F, 0.001F,
                "a panel with no sky above it still makes power");

        // A night's worth of storage is a build, not a purchase.
        float night = 12_000.0F;                // half a Minecraft day, in ticks
        float panels = 64.0F;
        float made = AtomicRules.SOLAR_PANEL.output() * panels * night;
        check(made < AtomicRules.ADVANCED_CELL.capacity(),
                "sixty-four panels fill an advanced cell in one day, so the top two cell "
                        + "tiers are never worth building");
        check(made > AtomicRules.BASIC_CELL.capacity() / 8.0F,
                "sixty-four panels barely register against the smallest cell, so solar "
                        + "is not a route at all");
    }

    private static void theShaft() {
        check(AtomicRules.ON_A_SHAFT < IndustryRules.ON_A_SHAFT,
                "a shaft drives an Age III machine as well as it drives an Age II one, "
                        + "so the fallback route never gets worse as the spine climbs");
        check(AtomicRules.ON_A_SHAFT > 0.0F,
                "a shaft cannot drive an Age III machine at all, which the spine says it "
                        + "must be able to, at a penalty");
        check(AtomicRules.SHAFT_LEAST_RPM > IndustryRules.SHAFT_LEAST_RPM,
                "an Age III machine is no harder to drive from a shaft than an Age II one");
        check(AtomicRules.SHAFT_LEAST_RPM > KineticRules.Shaft.BRASS.rating(),
                "a brass shaft can drive an Age III machine, so the age below has no "
                        + "reason to upgrade its shafts before reaching up — the same "
                        + "off-by-one the harness caught in Age II against timber");
        check(AtomicRules.SHAFT_LEAST_RPM <= KineticRules.Shaft.STEEL.rating(),
                "no shaft in the game can turn fast enough to drive an Age III machine, "
                        + "so the fallback route does not exist");
        near(AtomicRules.shaftShare(AtomicRules.SHAFT_LEAST_RPM), AtomicRules.ON_A_SHAFT,
                0.001F, "a shaft at exactly the speed required drives nothing");
        near(AtomicRules.shaftShare(AtomicRules.SHAFT_LEAST_RPM - 1.0F), 0.0F, 0.001F,
                "a shaft below the speed required still drives an Age III machine");
        near(AtomicRules.shaftShare(-AtomicRules.SHAFT_LEAST_RPM), AtomicRules.ON_A_SHAFT,
                0.001F, "a line running backwards drives nothing");

        for (IndustryRules.Machine machine : List.of(AtomicRules.ENRICHER,
                AtomicRules.CENTRIFUGE)) {
            near(AtomicRules.asLoad(machine).draw(),
                    machine.draw() / IndustryRules.SHAFT_FE_PER_TORQUE, 0.001F,
                    "an Age III machine's draw on a shaft is not its draw on a wire at "
                            + "the exchange rate the spine already fixed");
            check(AtomicRules.asLoad(machine).leastRpm() == AtomicRules.SHAFT_LEAST_RPM,
                    "an Age III machine wants its own speed off a shaft");
            check(AtomicRules.asLoad(machine).draw()
                            > IndustryRules.asLoad(IndustryRules.SEPARATOR).draw(),
                    "an Age III machine is lighter on a shaft than Age II's heaviest");
        }
    }

    /**
     * Reactor halls: a reactor built at the size you want.
     *
     * <p>Two jars in this unit are about exactly this and nothing else, and
     * the thing they usually get wrong is that bigger is simply better. Here
     * the claim is a trade — bigger makes more altogether and less per rod —
     * and a trade is a thing to sweep rather than to assert. Every size, every
     * amount of plumbing, every throttle.
     */
    private static void theHalls() {
        none(HallRules.faults(), "the reactor halls");

        int swept = 0;
        float perRodBefore = Float.MAX_VALUE;
        float wholeBefore = -1.0F;
        for (int side = HallRules.SMALLEST; side <= HallRules.LARGEST; side++) {
            // The box adds up: what is casing plus what is inside it is the
            // whole cube, and the rods are what is inside it.
            check(HallRules.casingFor(side) + HallRules.inside(side)
                            == side * side * side,
                    "a hall " + side + " on a side is not a cube");
            check(HallRules.rodsIn(side) == HallRules.inside(side),
                    "a hall " + side + " on a side has room it cannot put a "
                            + "rod in");
            check(HallRules.surfaceOf(side) == 6 * side * side,
                    "a box has six faces");

            // The shape of that size really is that box.
            com.elysium.industry.rules.ShapeRules.Shape box =
                    HallRules.boxOf(side);
            check(box.tall() == side && box.deep() == side
                            && box.wide() == side,
                    "the shape for a hall " + side + " on a side is not "
                            + side + " on a side");
            java.util.Map<String, Integer> bill = HallRules.billFor(side);
            check(bill.getOrDefault(HallRules.CASING, 0)
                            == HallRules.casingFor(side) - 1,
                    "a hall " + side + " on a side wants "
                            + bill.getOrDefault(HallRules.CASING, 0)
                            + " casing and its wall is "
                            + (HallRules.casingFor(side) - 1)
                            + " blocks once the port is in it");
            check(bill.getOrDefault(HallRules.ROD, 0) == HallRules.rodsIn(side),
                    "a hall " + side + " on a side wants "
                            + bill.getOrDefault(HallRules.ROD, 0)
                            + " rod(s) and holds " + HallRules.rodsIn(side));
            check(com.elysium.industry.rules.ShapeRules.controllerOf(box) != null,
                    "a hall " + side + " on a side has no port");

            // The trade, at every amount of plumbing.
            float before = -1.0F;
            for (int channels = 0;
                    channels <= HallRules.mostChannelsOn(side); channels++) {
                swept++;
                float cooling = HallRules.coolingOf(side, channels);
                check(cooling > 0.0F, "a hall " + side + " with " + channels
                        + " channel(s) sheds nothing at all, so it settles "
                        + "nowhere");
                check(cooling >= before, "a hall " + side
                        + " sheds less with " + channels + " channel(s) than "
                        + "with " + (channels - 1));
                before = cooling;
                float safe = HallRules.safeThrottle(side, channels);
                check(safe >= 0.0F && safe <= 1.0F, "a hall " + side
                        + " with " + channels + " channel(s) has a safe "
                        + "throttle of " + safe);
                // The solved throttle is the throttle: run it there and it
                // settles at or under safe; run it a little above and it does
                // not. Solved rather than searched, and swept rather than
                // trusted.
                check(HallRules.settlesAt(side, safe, channels)
                                <= AtomicRules.SAFE + 1.0F,
                        "a hall " + side + " with " + channels
                                + " channel(s) run at the throttle solved as "
                                + "safe settles at "
                                + HallRules.settlesAt(side, safe, channels));
                if (safe < 0.999F) {
                    check(HallRules.settlesAt(side, safe + 0.01F, channels)
                                    > AtomicRules.SAFE,
                            "a hall " + side + " with " + channels
                                    + " channel(s) is still safe a notch above "
                                    + "the throttle solved as its limit, so "
                                    + "the limit is under where it could be");
                }
            }
            // And a hall never sheds more than a single core with the same
            // number of channels would, because nothing about being big helps.
            check(HallRules.coolingOf(side, AtomicRules.MOST_CHANNELS)
                            <= AtomicRules.coolingWith(AtomicRules.MOST_CHANNELS)
                            + 1.0E-4F,
                    "a hall sheds better per channel than a single core, so "
                            + "building bigger cools better as well as making "
                            + "more");

            float whole = HallRules.safeOutput(side, HallRules.mostChannelsOn(side));
            float perRod = whole / HallRules.rodsIn(side);
            check(whole > wholeBefore, "a hall " + side + " on a side safely "
                    + "makes " + whole + " and the one below makes "
                    + wholeBefore + ", so nobody would build it");
            check(perRod <= perRodBefore + 1.0E-4F, "a hall " + side
                    + " on a side makes " + perRod + " a rod and the one "
                    + "below makes " + perRodBefore + ", so bigger is free");
            wholeBefore = whole;
            perRodBefore = perRod;
        }
        check(swept > 100, "only " + swept + " hall(s) were swept");

        // No two sizes are the same arrangement, in any rotation of either.
        check(com.elysium.industry.rules.ShapeRules.faultsInSet(HallRules.SHAPES)
                        .isEmpty(),
                "two sizes of hall are the same pile of blocks");
        for (com.elysium.industry.rules.ShapeRules.Shape shape : HallRules.SHAPES) {
            check(com.elysium.industry.rules.ShapeRules.matches(shape,
                            com.elysium.industry.rules.ShapeRules.gridOf(shape)),
                    shape.id() + " is not its own shape");
        }

        // Controls, built to be caught. Each one is pointed at its own rule:
        // a control aimed at the lot is answered by whichever fires first,
        // and the rest could then be deleted with every check still green.
        check(!HallRules.faultsInRange(2, 7).isEmpty(),
                "a smallest hall with nothing inside it is not reported");
        check(!HallRules.faultsInRange(5, 5).isEmpty(),
                "a range with one size in it is not reported, so the size of "
                        + "a reactor could stop being a decision");
        check(HallRules.faultsInRange(HallRules.SMALLEST, HallRules.LARGEST)
                        .isEmpty(),
                "the range this module ships is reported as faulty");
        check(!HallRules.faultsInBoxes(2, 2).isEmpty(),
                "a box with no room in it is not reported");
        check(HallRules.faultsInBoxes(HallRules.SMALLEST, HallRules.LARGEST)
                        .isEmpty(),
                "the boxes this module ships are reported as faulty");

        // The ladder, handed a ladder built to be caught.
        check(!HallRules.faultsInLadder(java.util.List.of(
                        new float[] {100.0F, 100.0F},
                        new float[] {400.0F, 200.0F})).isEmpty(),
                "a ladder that makes more per rod the bigger it gets is not "
                        + "reported, so building bigger could be free");
        check(!HallRules.faultsInLadder(java.util.List.of(
                        new float[] {400.0F, 100.0F},
                        new float[] {300.0F, 50.0F})).isEmpty(),
                "a ladder whose bigger rung makes less altogether is not "
                        + "reported, so it could be a rung nobody builds");
        check(!HallRules.faultsInLadder(java.util.List.of(
                        new float[] {400.0F, 100.0F})).isEmpty(),
                "a ladder with one rung on it is not reported");
        check(HallRules.faultsInLadder(java.util.List.of(
                        new float[] {100.0F, 100.0F},
                        new float[] {400.0F, 50.0F})).isEmpty(),
                "a ladder that makes more altogether and less per rod is "
                        + "reported as faulty, which is the shape this module "
                        + "ships");
        check(HallRules.faultsInLadder(HallRules.ladderOf(HallRules.SMALLEST,
                        HallRules.LARGEST)).isEmpty(),
                "the ladder this module ships is reported as faulty");

        // The plumbing, handed rods weak enough to need none.
        check(!HallRules.faultsInUnplumbed(HallRules.SMALLEST,
                        HallRules.LARGEST, 0.1F).isEmpty(),
                "rods weak enough that a hall is safe at full throttle with "
                        + "no cooling at all are not reported, so a reactor "
                        + "could be a generator you simply place");
        check(HallRules.faultsInUnplumbed(HallRules.SMALLEST,
                        HallRules.LARGEST, HallRules.PER_ROD).isEmpty(),
                "the rods this module ships are reported as needing no "
                        + "cooling");
        check(!HallRules.faultsInThrottle(HallRules.SMALLEST,
                        HallRules.PER_ROD).isEmpty(),
                "a range whose largest hall never needs throttling is not "
                        + "reported, so the throttle could be a slider nobody "
                        + "touches");
        check(HallRules.faultsInThrottle(HallRules.LARGEST, HallRules.PER_ROD)
                        .isEmpty(),
                "the largest hall this module ships is reported as needing no "
                        + "throttle");
        check(HallRules.faultsInPlumbing(HallRules.SMALLEST, HallRules.LARGEST,
                        HallRules.PER_ROD).isEmpty(),
                "the plumbing this module ships is reported as faulty");

        System.out.println("  the halls: " + (HallRules.LARGEST
                - HallRules.SMALLEST + 1) + " size(s) of reactor, " + swept
                + " of them swept at every amount of plumbing - each one "
                + "making more altogether than the one below and less per rod, "
                + "from "
                + String.format("%.0f", HallRules.safeOutput(HallRules.SMALLEST,
                        HallRules.mostChannelsOn(HallRules.SMALLEST)))
                + " FE/t to "
                + String.format("%.0f", HallRules.safeOutput(HallRules.LARGEST,
                        HallRules.mostChannelsOn(HallRules.LARGEST)))
                + " FE/t, and heat that goes as the cube against cooling that "
                + "goes as the square");
    }
}
