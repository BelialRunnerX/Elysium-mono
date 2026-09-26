package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The devices and the logic layer, executed.
 *
 * <p>The devices are arithmetic: seven blocks, a draw apiece, and a network at
 * its limit is a stated load on the grid rather than a surprise.
 *
 * <p>The logic layer is the interesting half, because it is a small
 * programming language living in a save file, and it is proved to have the two
 * properties that stop one ruining a world:
 *
 * <ul>
 *   <li><b>It is typed, and decidably so.</b> Twenty thousand expressions built
 *       at random are each asked what type they are <em>without being run</em>,
 *       and the answer is checked against the operators they are made of.
 *   <li><b>It cannot loop.</b> Not "loops are detected": an arrow that points
 *       forwards is not a legal expression, so there is no cycle to detect. The
 *       sweep builds expressions that point forwards on purpose and proves
 *       every one of them is refused.
 * </ul>
 */
public final class NetHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theDevices();
        theOperators();
        theTyping();
        theTermination();
        theSweep();
        theRunning();
        theFaces();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: seven devices whose draw the grid "
                + "can be sized for, every operator saying what it takes and what "
                + "it gives, every expression's type worked out without running "
                + "it, and no expression able to point forwards at all");
    }

    // ==================================================================

    private static void theDevices() {
        check(NetRules.gaps().isEmpty(),
                "a role nothing fills, or a device declared twice: "
                        + NetRules.gaps());
        Set<String> roles = new LinkedHashSet<>();
        for (NetRules.Device one : NetRules.DEVICES) {
            roles.add(one.role());
            check(NetRules.device(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            check(one.draw() > 0, one.name() + " is free to run");
            check(one.draw() == one.draws() * LogisticRules.DEVICE_DRAW,
                    one.name() + " draws " + one.draw() + " and its rung on the "
                            + "ladder says " + one.draws() * LogisticRules.DEVICE_DRAW);
            check(one.draw() <= LogisticRules.DEVICE_DRAW * 8,
                    one.name() + " draws " + one.draw() + ", which is more than "
                            + "eight ordinary devices, so a wall of them is a "
                            + "choice between lighting a factory and finding a stick");
        }
        check(roles.size() == NetRules.ROLES.size(),
                "the devices fill " + roles.size() + " of " + NetRules.ROLES.size()
                        + " roles");
        check(NetRules.drawOf(NetRules.DEVICES) > 0,
                "one of every device draws nothing");
        check(NetRules.drawOf(List.of()) == 0,
                "a network with no devices on it draws something");
        check(NetRules.mostItCanDraw()
                        >= NetRules.drawOf(NetRules.DEVICES),
                "the stated ceiling is below what one of every device already "
                        + "draws");
        check(NetRules.mostItCanDraw()
                        <= LogisticRules.DEVICE_DRAW * 8 * LogisticRules.MOST_DEVICES,
                "a full network can draw " + NetRules.mostItCanDraw()
                        + " FE/t, which is a power station rather than a warehouse");
        System.out.println("  the devices: " + NetRules.DEVICES.size()
                + " device(s) filling all " + NetRules.ROLES.size() + " roles, "
                + "from " + LogisticRules.DEVICE_DRAW + " FE/t apiece up to "
                + NetRules.mostItCanDraw() + " FE/t for a network at its limit");
    }

    // ==================================================================

    private static void theOperators() {
        check(NetRules.deadTypes().isEmpty(),
                "a type nothing makes or nothing takes: " + NetRules.deadTypes());
        Set<String> named = new LinkedHashSet<>();
        int readers = 0;
        for (NetRules.Op one : NetRules.OPS) {
            check(named.add(one.name()), one.name() + " is declared twice");
            check(NetRules.op(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            check(one.arity() == one.takes().size(),
                    one.name() + " wants " + one.arity() + " things and lists "
                            + one.takes().size() + " types for them");
            check(NetRules.TYPES.contains(one.gives()),
                    one.name() + " gives a " + one.gives());
            if (one.arity() == 0) {
                readers++;
            }
        }
        check(readers > 0, "no operator takes nothing, so nothing can get a value "
                + "out of the world and into an expression");
        check(readers < NetRules.OPS.size(), "every operator is a reader, so "
                + "nothing can be done with what is read");
        check(NetRules.MOST_NODES <= NetRules.CEILING,
                "an expression may be " + NetRules.MOST_NODES + " steps long, a "
                        + "network works one out every tick, and the most a "
                        + "ceiling may allow is " + NetRules.CEILING + " — so the "
                        + "ceiling is not a ceiling, it is the sky");
        check(NetRules.MOST_NODES >= 8, "an expression may be only "
                + NetRules.MOST_NODES + " steps long, which is not a logic layer");
        System.out.println("  the operators: " + NetRules.OPS.size()
                + " operator(s) over " + NetRules.TYPES.size() + " type(s), "
                + readers + " of them reading from the world and every type both "
                + "made by something and taken by something");
    }

    // ==================================================================

    private static void theTyping() {
        // Every operator, on inputs of exactly the types it asks for: it must
        // work out, and to the type it said.
        for (NetRules.Op one : NetRules.OPS) {
            List<NetRules.Node> built = new ArrayList<>();
            List<Integer> where = new ArrayList<>();
            boolean can = true;
            for (String wanted : one.takes()) {
                int at = makerOf(built, wanted);
                if (at < 0) {
                    can = false;
                    break;
                }
                where.add(at);
            }
            if (!can) {
                check(false, "nothing can make a " + one.takes()
                        + " for " + one.name() + " to be tried on");
                continue;
            }
            built.add(new NetRules.Node(one.name(), where, "minecraft:iron_ingot"));
            check(NetRules.sound(built), one.name()
                    + " does not work out even when given exactly what it asks "
                    + "for: " + NetRules.refuses(built));
            check(one.gives().equals(NetRules.typeOf(built)),
                    one.name() + " says it gives a " + one.gives()
                            + " and the expression works out to "
                            + NetRules.typeOf(built));

            // And on inputs of the wrong type, where a wrong type exists.
            for (int which = 0; which < one.arity(); which++) {
                for (String other : NetRules.TYPES) {
                    if (other.equals(one.takes().get(which))) {
                        continue;
                    }
                    List<NetRules.Node> bent = new ArrayList<>(built);
                    bent.remove(bent.size() - 1);
                    int at = makerOf(bent, other);
                    if (at < 0) {
                        continue;
                    }
                    List<Integer> swapped = new ArrayList<>(where);
                    swapped.set(which, at);
                    bent.add(new NetRules.Node(one.name(), swapped,
                            "minecraft:iron_ingot"));
                    check(!NetRules.sound(bent), one.name() + " took a " + other
                            + " where it says it takes a " + one.takes().get(which)
                            + ", so the types are a comment rather than a check");
                    check(!NetRules.refuses(bent).isEmpty(),
                            one.name() + " was refused with no reason given");
                }
            }
        }
        System.out.println("  the typing: every operator tried on exactly what it "
                + "asks for and on every wrong type there is, and refused every "
                + "time it was given the wrong one");
    }

    /**
     * Adds whatever is needed to make a value of this type, and says where.
     *
     * <p>Recursive, and it has to be: the only thing that makes a piece of
     * text is an operator that wants an item or a number first, so a helper
     * that could only reach for a reader would quietly skip three of the
     * operators and report that everything it did try was fine.
     */
    private static int makerOf(List<NetRules.Node> built, String type) {
        return makerOf(built, type, 0);
    }

    private static int makerOf(List<NetRules.Node> built, String type, int deep) {
        for (int at = 0; at < built.size(); at++) {
            NetRules.Op made = NetRules.op(built.get(at).op());
            if (made != null && made.gives().equals(type)) {
                return at;
            }
        }
        if (deep > NetRules.TYPES.size()) {
            return -1;
        }
        for (NetRules.Op one : NetRules.OPS) {
            if (one.arity() == 0 && one.gives().equals(type)) {
                built.add(new NetRules.Node(one.name(), List.of(), ""));
                return built.size() - 1;
            }
        }
        for (NetRules.Op one : NetRules.OPS) {
            if (!one.gives().equals(type)) {
                continue;
            }
            List<Integer> where = new ArrayList<>();
            boolean can = true;
            for (String wanted : one.takes()) {
                int at = makerOf(built, wanted, deep + 1);
                if (at < 0) {
                    can = false;
                    break;
                }
                where.add(at);
            }
            if (can) {
                built.add(new NetRules.Node(one.name(), where, ""));
                return built.size() - 1;
            }
        }
        return -1;
    }

    // ==================================================================

    private static void theTermination() {
        // An arrow that points forwards, at every distance, in expressions of
        // every length: every one refused.
        int tried = 0;
        for (int length = 1; length <= 8; length++) {
            for (int at = 0; at < length; at++) {
                for (int to = at; to < length + 2; to++) {
                    List<NetRules.Node> built = new ArrayList<>();
                    for (int step = 0; step < length; step++) {
                        built.add(new NetRules.Node("read_power", List.of(), ""));
                    }
                    built.set(at, new NetRules.Node("not", List.of(to), ""));
                    check(!NetRules.sound(built),
                            "step " + at + " reading step " + to + " of " + length
                                    + " was allowed, and an arrow that points "
                                    + "forwards is how an expression loops");
                    tried++;
                }
            }
        }
        // A node reading itself, which is the smallest loop there is.
        for (int length = 1; length <= 8; length++) {
            List<NetRules.Node> built = new ArrayList<>();
            for (int step = 0; step < length; step++) {
                built.add(new NetRules.Node("read_busy", List.of(), ""));
            }
            built.set(length - 1, new NetRules.Node("not", List.of(length - 1), ""));
            check(!NetRules.sound(built), "a step that reads itself was allowed");
            tried++;
        }
        check(tried > 100, "only " + tried + " forward arrows were tried");
        System.out.println("  the termination: " + tried + " expressions with an "
                + "arrow pointing forwards or at itself, every one of them "
                + "refused — there is no cycle detection here because a cycle "
                + "cannot be written down");
    }

    // ==================================================================

    private static void theSweep() {
        Random random = new Random(1_729_000L);
        int sound = 0;
        int refused = 0;
        for (int round = 0; round < 40_000; round++) {
            int length = 1 + random.nextInt(8);
            List<NetRules.Node> built = new ArrayList<>();
            for (int at = 0; at < length; at++) {
                NetRules.Op one = NetRules.OPS.get(
                        random.nextInt(NetRules.OPS.size()));
                List<Integer> inputs = new ArrayList<>();
                for (int which = 0; which < one.arity(); which++) {
                    inputs.add(random.nextInt(length + 1) - 1);
                }
                built.add(new NetRules.Node(one.name(), inputs,
                        "minecraft:iron_ingot"));
            }
            boolean ok = NetRules.sound(built);
            List<String> why = NetRules.refuses(built);
            check(ok == why.isEmpty(), "an expression was "
                    + (ok ? "accepted" : "refused") + " and the reasons say "
                    + (why.isEmpty() ? "nothing is wrong" : why.toString()));
            if (ok) {
                sound++;
                List<String> types = NetRules.typesIn(built);
                check(types != null && types.size() == built.size(),
                        "an expression that works out has no type for every step");
                for (int at = 0; at < built.size(); at++) {
                    NetRules.Op one = NetRules.op(built.get(at).op());
                    check(types.get(at).equals(one.gives()),
                            "step " + at + " is a " + types.get(at)
                                    + " and its operator gives a " + one.gives());
                    for (int which = 0; which < one.arity(); which++) {
                        int from = built.get(at).inputs().get(which);
                        check(from < at, "an accepted expression points forwards");
                        check(types.get(from).equals(one.takes().get(which)),
                                "an accepted expression gives a "
                                        + types.get(from) + " where a "
                                        + one.takes().get(which) + " was wanted");
                    }
                }
                check(NetRules.stepsFor(built) == built.size(),
                        "an expression of " + built.size() + " steps takes "
                                + NetRules.stepsFor(built) + " to work out, so "
                                + "something is being worked out twice and the "
                                + "cost of an expression is not its length");
                // And it runs: every expression that types works out to a
                // value, and the value is of the type the typing promised.
                NetRules.Value made = NetRules.evaluate(built, WORLD);
                check(made != null, "an expression that types could not be run: "
                        + built);
                check(made != null && made.type().equals(NetRules.typeOf(built)),
                        "an expression typed as " + NetRules.typeOf(built)
                                + " ran to a " + (made == null ? "nothing" : made.type()));
            } else {
                refused++;
                check(NetRules.evaluate(built, WORLD) == null,
                        "an expression the typing refused was run anyway");
            }
        }
        check(sound > 0, "not one of forty thousand random expressions worked out, "
                + "so the typing says no to everything");
        check(refused > 0, "every one of forty thousand random expressions worked "
                + "out, so the typing says yes to everything");
        System.out.println("  the sweep: 40,000 random expressions, " + sound
                + " of them sound and " + refused + " refused, and every sound one "
                + "typed step by step against the operators it is made of");
    }

    // ==================================================================

    /** A world with numbers in it the harness chose, so answers are known. */
    private static final NetRules.Readings WORLD = new NetRules.Readings() {
        @Override
        public long countOf(String item) {
            return item.length() * 100L;
        }

        @Override
        public long power() {
            return 48;
        }

        @Override
        public long devices() {
            return 12;
        }

        @Override
        public boolean busy() {
            return true;
        }

        @Override
        public String nameOf(String item) {
            return item.toUpperCase();
        }
    };

    /** A world holding exactly this many of one thing, and nothing else. */
    private static NetRules.Readings holding(String thing, long count) {
        return new NetRules.Readings() {
            @Override
            public long countOf(String item) {
                return thing.equals(item) ? count : -1;
            }

            @Override
            public long power() {
                return 0;
            }

            @Override
            public long devices() {
                return 0;
            }

            @Override
            public boolean busy() {
                return false;
            }

            @Override
            public String nameOf(String item) {
                return item;
            }
        };
    }

    /**
     * The layer, run rather than only typed: the two expressions the devices
     * actually put through it, against answers worked out the long way.
     */
    private static void theRunning() {
        check(NetRules.unrunnable().isEmpty(),
                "an operator the network cannot run: " + NetRules.unrunnable());
        check(NetRules.op("constant") != null && NetRules.op("constant").arity() == 0
                        && NetRules.NUMBER.equals(NetRules.op("constant").gives()),
                "there is no constant, so no expression can name a number a player "
                        + "chose and a detector has nothing to compare a count with");
        check(NetRules.numberIn("4096") == 4096 && NetRules.numberIn("nonsense") == 0
                        && NetRules.numberIn(null) == 0,
                "a constant does not read as the number written in it, or throws on "
                        + "one that is not a number");

        // The threshold ladder: climbs, cycles, and every rung is reachable.
        int last = 0;
        for (int rung : NetRules.THRESHOLDS) {
            check(rung > last, "the detector's ladder goes " + last + " then " + rung
                    + ", so a step up is not a step up");
            last = rung;
        }
        Set<Integer> reached = new LinkedHashSet<>();
        int at = NetRules.firstThreshold();
        for (int step = 0; step < NetRules.THRESHOLDS.size(); step++) {
            reached.add(at);
            at = NetRules.nextThreshold(at);
        }
        check(reached.size() == NetRules.THRESHOLDS.size()
                        && at == NetRules.firstThreshold(),
                "stepping a detector " + NetRules.THRESHOLDS.size() + " times reaches "
                        + reached.size() + " rungs and lands on " + at + ", so some "
                        + "count can never be asked for or the ladder does not come "
                        + "back round");
        check(NetRules.nextThreshold(7) == NetRules.firstThreshold(),
                "a threshold off the ladder does not step back onto it");

        // The detector's expression: sound, a truth, and it agrees with the
        // arithmetic at every rung for every count near it.
        int agreed = 0;
        for (int rung : NetRules.THRESHOLDS) {
            List<NetRules.Node> asks = NetRules.detectorExpression("stone", rung);
            check(NetRules.sound(asks), "the detector's own expression is refused: "
                    + NetRules.refuses(asks));
            check(NetRules.TRUTH.equals(NetRules.typeOf(asks)),
                    "the detector's expression is a " + NetRules.typeOf(asks)
                            + " and redstone wants a truth");
            for (long count = Math.max(0, rung - 3); count <= rung + 3L; count++) {
                NetRules.Value said = NetRules.evaluate(asks, holding("stone", count));
                check(said != null && said.truth() == NetRules.reaches(count, rung),
                        "holding " + count + " with the detector at " + rung + " the "
                                + "expression says " + (said == null ? "nothing"
                                : said.truth()) + " and the arithmetic says "
                                + NetRules.reaches(count, rung));
                agreed++;
            }
            NetRules.Value other = NetRules.evaluate(asks, holding("dirt", 100_000));
            check(other != null && !other.truth(),
                    "a detector shown stone fires on a network full of dirt");
        }
        check(NetRules.reaches(64, 64) && !NetRules.reaches(63, 64)
                        && NetRules.reaches(65, 64),
                "reaching a threshold is not 'at least', so a detector set to a "
                        + "stack fires one short or one late");

        // The monitor's: sound, text, and it says the count and the name.
        List<NetRules.Node> shows = NetRules.monitorExpression("stone");
        check(NetRules.sound(shows), "the monitor's own expression is refused: "
                + NetRules.refuses(shows));
        check(NetRules.TEXT.equals(NetRules.typeOf(shows)),
                "the monitor's expression is a " + NetRules.typeOf(shows)
                        + " and a monitor shows text");
        NetRules.Value line = NetRules.evaluate(shows, WORLD);
        check(line != null && line.text().equals("500 STONE"),
                "the monitor shown 'stone' in a world holding 500 says '"
                        + (line == null ? "" : line.text()) + "'");

        // Every reader's answer is visible in what an expression works out
        // to, so none of them is a name with nothing behind it.
        NetRules.Value power = NetRules.evaluate(List.of(
                new NetRules.Node("read_power", List.of(), "")), WORLD);
        NetRules.Value busy = NetRules.evaluate(List.of(
                new NetRules.Node("read_busy", List.of(), ""),
                new NetRules.Node("not", List.of(0), "")), WORLD);
        NetRules.Value counted = NetRules.evaluate(List.of(
                new NetRules.Node("read_devices", List.of(), ""),
                new NetRules.Node("constant", List.of(), "30"),
                new NetRules.Node("multiply", List.of(0, 1), "")), WORLD);
        check(power != null && power.number() == 48, "read_power does not read the power");
        check(busy != null && !busy.truth(), "not(read_busy) in a busy world is true");
        check(counted != null && counted.number() == 360,
                "twelve devices times a constant of thirty is "
                        + (counted == null ? "nothing" : counted.number()));

        System.out.println("  the running: every operator runnable, the detector's "
                + "expression agreeing with the arithmetic at " + agreed
                + " counts across " + NetRules.THRESHOLDS.size() + " rungs, and the "
                + "monitor's reading back as text");
    }

    // ==================================================================

    /**
     * What the devices cost a controller, and how that is counted: one slot
     * each, and a surcharge for the dear ones, adding up to exactly the draw
     * the roster states.
     */
    private static void theFaces() {
        int slots = NetRules.DEVICES.size();
        int extra = NetRules.surcharge(NetRules.DEVICES);
        check(extra >= 0, "the devices' surcharge is negative");
        LogisticRules.Network faced = new LogisticRules.Network(slots, extra);
        check(Math.abs(faced.machine().draw() - NetRules.drawOf(NetRules.DEVICES)) < 1e-4,
                "a network of one of every device is billed "
                        + faced.machine().draw() + " and the devices draw "
                        + NetRules.drawOf(NetRules.DEVICES));
        LogisticRules.Network drawers = new LogisticRules.Network(slots);
        check(faced.machine().draw() >= drawers.machine().draw(),
                "a network of devices is cheaper than the same number of drawers, "
                        + "so the dear devices are not dear");
        check(faced.overloaded() == drawers.overloaded(),
                "a device takes more or less than one slot on a controller");
        LogisticRules.Network mixed = new LogisticRules.Network(
                LogisticRules.MOST_DEVICES, extra);
        check(!mixed.overloaded(), "a full controller with devices on it is overloaded "
                + "by their draw rather than by their number");
        check(new LogisticRules.Network(LogisticRules.MOST_DEVICES + 1, 0).overloaded(),
                "one too many is not overloaded");
        // Every device costs something, and the roster the blocks are
        // registered from is exactly the devices, once each, in order.
        List<String> names = new ArrayList<>();
        for (NetRules.Device one : NetRules.DEVICES) {
            check(one.draws() >= 1 && one.draw() > 0.0F, one.name() + " draws "
                    + one.draw() + ", so it is free to run");
            names.add(one.name());
        }
        check(NetRules.catalogue().equals(names), "the blocks registered from the "
                + "roster are " + NetRules.catalogue() + " and the devices are " + names);
        check(new LinkedHashSet<>(names).size() == names.size()
                        && NetRules.howMany() == names.size(),
                "two devices share a name, or the count registered is not the roster");
        for (NetRules.Device one : NetRules.DEVICES) {
            check(NetRules.surcharge(List.of(one)) == one.draws() - 1,
                    one.name() + " pays a surcharge of " + NetRules.surcharge(List.of(one))
                            + " on a draw of " + one.draws());
        }
        check(NetRules.surcharge(List.of()) == 0, "no devices pay a surcharge");
        check(NetRules.TICK_EVERY >= 1 && NetRules.TICK_EVERY <= 100,
                "a device works every " + NetRules.TICK_EVERY + " ticks, which is "
                        + "either every tick or never");
        check(NetRules.MOVES_PER_ROUND >= 1 && NetRules.MOVES_PER_ROUND <= LogisticRules.STACK,
                "a device moves " + NetRules.MOVES_PER_ROUND + " a round, which is "
                        + "nothing or more than a stack");
        boolean refused = false;
        try {
            new LogisticRules.Network(1, -1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a network whose devices pay less than a slot was accepted");
        System.out.println("  the faces: " + slots + " devices on " + slots
                + " slots paying " + extra + " draw(s) over, " + faced.machine().draw()
                + " FE/t in all, one slot apiece against the limit of "
                + LogisticRules.MOST_DEVICES);
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        List<NetRules.Node> good = List.of(
                new NetRules.Node("read_power", List.of(), ""),
                new NetRules.Node("read_devices", List.of(), ""),
                new NetRules.Node("add", List.of(0, 1), ""),
                new NetRules.Node("spelled", List.of(2), ""));
        check(NetRules.sound(good), "an ordinary expression is refused: "
                + NetRules.refuses(good));
        check(NetRules.TEXT.equals(NetRules.typeOf(good)),
                "adding two numbers and spelling the answer is a "
                        + NetRules.typeOf(good));

        List<NetRules.Node> crossed = List.of(
                new NetRules.Node("read_busy", List.of(), ""),
                new NetRules.Node("read_power", List.of(), ""),
                new NetRules.Node("add", List.of(0, 1), ""));
        check(!NetRules.sound(crossed),
                "adding a truth to a number was allowed, so the types are a "
                        + "comment rather than a check");
        check(!NetRules.refuses(crossed).isEmpty(),
                "adding a truth to a number was refused with no reason given");

        List<NetRules.Node> forwards = List.of(
                new NetRules.Node("not", List.of(1), ""),
                new NetRules.Node("read_busy", List.of(), ""));
        check(!NetRules.sound(forwards),
                "an expression whose first step reads its second was allowed, "
                        + "which is a loop written down");

        List<NetRules.Node> unknown = List.of(
                new NetRules.Node("read_the_future", List.of(), ""));
        check(!NetRules.sound(unknown), "an operator that does not exist was "
                + "allowed");

        List<NetRules.Node> miscounted = List.of(
                new NetRules.Node("read_power", List.of(), ""),
                new NetRules.Node("add", List.of(0), ""));
        check(!NetRules.sound(miscounted),
                "adding with one argument was allowed");

        check(!NetRules.sound(List.of()), "an expression with no steps works out");

        List<NetRules.Node> toolong = new ArrayList<>();
        for (int at = 0; at <= NetRules.MOST_NODES; at++) {
            toolong.add(new NetRules.Node("read_power", List.of(), ""));
        }
        check(!NetRules.sound(toolong), "an expression of " + toolong.size()
                + " steps was allowed and the ceiling is " + NetRules.MOST_NODES);
        check(NetRules.sound(toolong.subList(0, NetRules.MOST_NODES)),
                "an expression of exactly " + NetRules.MOST_NODES
                        + " steps was refused, so the ceiling is one short of "
                        + "what it says");

        // The one line the whole termination argument rests on, asked about
        // answers that are known.
        check(NetRules.mayRead(3, 2),
                "a step was not allowed to read the step before it, so no "
                        + "expression can have two steps in it");
        check(!NetRules.mayRead(3, 3),
                "a step was allowed to read itself, which is the smallest loop "
                        + "there is");
        check(!NetRules.mayRead(3, 4),
                "a step was allowed to read a step after it, and an arrow that "
                        + "points forwards is how an expression loops");
        check(!NetRules.mayRead(0, -1),
                "a step was allowed to read one before the beginning");

        // And the devices' own guards.
        List<String> allowed = new ArrayList<>();
        try {
            new NetRules.Device("control", NetRules.IMPORT, 0);
            allowed.add("a device that is free to run");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new NetRules.Device("control", "loiter", 1);
            allowed.add("a device with a role that is not a role");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new NetRules.Op("control", List.of("colour"), NetRules.NUMBER);
            allowed.add("an operator taking a type that is not a type");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the guards allow what they say they forbid: "
                + allowed);

        System.out.println("  the controls: a truth added to a number caught, an "
                + "arrow pointing forwards caught, an operator that does not "
                + "exist caught, and an expression one step past the ceiling "
                + "caught");
    }
}
