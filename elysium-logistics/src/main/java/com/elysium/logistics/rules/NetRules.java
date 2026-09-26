package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a network is made of, and what it can be told to do. The JDK alone.
 *
 * <h2>Two halves, and why they are one file</h2>
 *
 * The first half is the <b>devices</b>: the seven blocks that make a network
 * reach anything outside itself. An importer pulls from a chest, an exporter
 * pushes into one, a storage bus makes a chest part of the network, a monitor
 * shows what is being made, a detector turns a count into redstone, an
 * interface gives a machine a face to take from, and a pair of ender links
 * share one pool across any distance. That is what {@code refinedstorage},
 * {@code appliedenergistics2}, {@code ExtendedAE} and {@code enderchests} add
 * on top of a network, and docs/CONVERSION.md section 10 says why it is one
 * network rather than three.
 *
 * <h2>And the second half: telling it something</h2>
 *
 * {@code integrateddynamics}, {@code cyclopscore} and {@code commoncapabilities}
 * are a <b>logic layer</b>: read a value off the world, put it through
 * operators, write the answer back. That is a small programming language
 * living inside a Minecraft save, and a small programming language has
 * exactly two ways of ruining one:
 *
 * <ol>
 *   <li><b>It can be asked to add a number to a chest.</b> So every operator
 *       here says what it takes and what it gives, and the type of an
 *       expression is worked out <em>without running it</em>. An ill-typed
 *       program is refused when it is written rather than throwing on a
 *       server tick.
 *   <li><b>It can loop.</b> So a node may only refer to nodes written before
 *       it. Not "cycles are detected" — <em>a cycle cannot be written down</em>,
 *       which is a much smaller thing to get right and the harness proves the
 *       check can still say no.
 * </ol>
 */
public final class NetRules {

    private NetRules() {
    }

    // ==================================================================
    // The devices
    // ==================================================================

    /** Pulls from an inventory outside the network into it. */
    public static final String IMPORT = "import";

    /** Pushes from the network into an inventory outside it. */
    public static final String EXPORT = "export";

    /** Makes an inventory outside the network part of it. */
    public static final String EXPOSE = "expose";

    /** Shows what the network is doing. */
    public static final String WATCH = "watch";

    /** Turns something the network knows into redstone. */
    public static final String SIGNAL = "signal";

    /** Gives a machine a face to take from. */
    public static final String SERVE = "serve";

    /** Joins two networks that are nowhere near each other. */
    public static final String LINK = "link";

    public static final List<String> ROLES =
            List.of(IMPORT, EXPORT, EXPOSE, WATCH, SIGNAL, SERVE, LINK);

    /**
     * One device.
     *
     * @param name  what it is called, which is also its block id
     * @param role  which of {@link #ROLES}
     * @param draws how many times {@link LogisticRules#DEVICE_DRAW} it costs
     *              to keep on the network
     */
    public record Device(String name, String role, int draws) {

        public Device {
            Objects.requireNonNull(name, "a device with no name");
            if (!ROLES.contains(role)) {
                throw new IllegalArgumentException(name + " has the role " + role
                        + " and the roles are " + ROLES);
            }
            if (draws < 1) {
                throw new IllegalArgumentException(name + " draws " + draws
                        + " times a device's draw, so it is free to run and the "
                        + "one constraint a network has does not apply to it");
            }
        }

        /** What it actually costs the grid, in FE per tick. */
        public float draw() {
            return draws * LogisticRules.DEVICE_DRAW;
        }
    }

    /** The seven, in the order a player builds them. */
    public static final List<Device> DEVICES = List.of(
            new Device("importer", IMPORT, 1),
            new Device("exporter", EXPORT, 1),
            new Device("storage_bus", EXPOSE, 2),
            new Device("monitor", WATCH, 1),
            new Device("detector", SIGNAL, 1),
            new Device("interface", SERVE, 2),
            new Device("ender_link", LINK, 4));

    /** A device by name, or null. */
    public static Device device(String name) {
        for (Device one : DEVICES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /** What a network of these devices draws, in FE per tick. */
    public static float drawOf(List<Device> devices) {
        float total = 0;
        for (Device one : devices) {
            total += one.draw();
        }
        return total;
    }

    /**
     * What these devices cost the grid <em>beyond</em> the one draw every
     * slot on a controller already pays.
     *
     * <p>A drawer is one slot and one draw. A device is one slot too — it
     * counts against {@link LogisticRules#MOST_DEVICES} exactly once — but a
     * dear device pays more than one draw for that slot, and this is the
     * difference. {@link LogisticRules.Network#machine()} adds it to the
     * slot count so that a network of drawers and devices is one number the
     * grid can be asked for, and that number is the sum the harness proves.
     */
    public static int surcharge(List<Device> devices) {
        int extra = 0;
        for (Device one : devices) {
            extra += one.draws() - 1;
        }
        return extra;
    }

    /**
     * How often a device does its work, in ticks.
     *
     * <p>Once a second rather than every tick. A device is a face on a
     * network rather than a machine, and a wall of forty importers each
     * asking a chest what it holds twenty times a second is a server spent
     * on bookkeeping. A player cannot see the difference between one second
     * and one twentieth of one on a chest being emptied.
     */
    public static final int TICK_EVERY = 20;

    /** The most an importer or exporter moves in one round: one stack. */
    public static final int MOVES_PER_ROUND = LogisticRules.STACK;

    /**
     * The counts a detector can be stepped to.
     *
     * <p>A ladder rather than a number typed in, because there is no screen
     * to type it into and the thing a detector is <em>for</em> — "tell me
     * when I have a stack", "tell me when I have a chest full" — is a
     * handful of round numbers a player already thinks in.
     */
    public static final List<Integer> THRESHOLDS = List.of(1, 16, 64, 256, 1024, 4096);

    /** The threshold a fresh detector starts at. */
    public static int firstThreshold() {
        return THRESHOLDS.get(0);
    }

    /** The next rung up the ladder from {@code now}, back to the first at the top. */
    public static int nextThreshold(int now) {
        int at = THRESHOLDS.indexOf(now);
        if (at < 0) {
            return firstThreshold();
        }
        return THRESHOLDS.get((at + 1) % THRESHOLDS.size());
    }

    /**
     * Whether a count has reached a threshold: what a detector's redstone
     * says, written down once so that the expression the detector actually
     * runs can be checked against it.
     */
    public static boolean reaches(long count, int threshold) {
        return count >= threshold;
    }

    /** The most a network may draw: every slot filled with the dearest device. */
    public static float mostItCanDraw() {
        int dearest = 1;
        for (Device one : DEVICES) {
            dearest = Math.max(dearest, one.draws());
        }
        return dearest * LogisticRules.DEVICE_DRAW * LogisticRules.MOST_DEVICES;
    }

    /** Every role nothing fills, and every device declared twice. Must be empty. */
    public static List<String> gaps() {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Device one : DEVICES) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        for (String role : ROLES) {
            boolean found = false;
            for (Device one : DEVICES) {
                if (one.role().equals(role)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                bad.add("nothing on a network can " + role + ", so that role is a "
                        + "word in a list and not a thing a player can build");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The logic layer
    // ==================================================================

    /** A count, a length, a temperature: anything that is a number. */
    public static final String NUMBER = "number";

    /** Yes or no. */
    public static final String TRUTH = "truth";

    /** A kind of item. */
    public static final String ITEM = "item";

    /** Something to put on a sign. */
    public static final String TEXT = "text";

    public static final List<String> TYPES = List.of(NUMBER, TRUTH, ITEM, TEXT);

    /**
     * One operator.
     *
     * @param name  what it is called
     * @param takes the type of each of its inputs, in order
     * @param gives the type of its answer
     */
    public record Op(String name, List<String> takes, String gives) {

        public Op {
            Objects.requireNonNull(name, "an operator with no name");
            takes = List.copyOf(takes);
            for (String one : takes) {
                if (!TYPES.contains(one)) {
                    throw new IllegalArgumentException(name + " takes a " + one
                            + " and the types are " + TYPES);
                }
            }
            if (!TYPES.contains(gives)) {
                throw new IllegalArgumentException(name + " gives a " + gives
                        + " and the types are " + TYPES);
            }
        }

        /** How many inputs it wants. */
        public int arity() {
            return takes.size();
        }
    }

    /**
     * Every operator.
     *
     * <p>Four of them take nothing at all: those are the readers, the way a
     * value gets into an expression from the world. Everything else is
     * arithmetic on what they found.
     */
    public static final List<Op> OPS = List.of(
            // The readers: where a value comes from. "held" is the one that
            // makes an item nameable at all -- it is whatever a player put in
            // this step's own slot, and without it nothing in an expression
            // could ever be an item and half the operators below would be
            // unreachable.
            new Op("held", List.of(), ITEM),
            // A number the player stepped to rather than read off the world.
            // Without it "more than a stack" is not a thing an expression
            // can say, and a detector has nothing to compare a count with.
            new Op("constant", List.of(), NUMBER),
            new Op("read_count", List.of(ITEM), NUMBER),
            new Op("read_power", List.of(), NUMBER),
            new Op("read_devices", List.of(), NUMBER),
            new Op("read_busy", List.of(), TRUTH),

            // Arithmetic.
            new Op("add", List.of(NUMBER, NUMBER), NUMBER),
            new Op("subtract", List.of(NUMBER, NUMBER), NUMBER),
            new Op("multiply", List.of(NUMBER, NUMBER), NUMBER),
            new Op("smaller_of", List.of(NUMBER, NUMBER), NUMBER),
            new Op("larger_of", List.of(NUMBER, NUMBER), NUMBER),

            // Comparison.
            new Op("equal", List.of(NUMBER, NUMBER), TRUTH),
            new Op("less", List.of(NUMBER, NUMBER), TRUTH),
            new Op("greater", List.of(NUMBER, NUMBER), TRUTH),

            // Truth.
            new Op("both", List.of(TRUTH, TRUTH), TRUTH),
            new Op("either", List.of(TRUTH, TRUTH), TRUTH),
            new Op("not", List.of(TRUTH), TRUTH),

            // Words.
            new Op("named", List.of(ITEM), TEXT),
            new Op("spelled", List.of(NUMBER), TEXT),
            new Op("joined", List.of(TEXT, TEXT), TEXT));

    /** An operator by name, or null. */
    public static Op op(String name) {
        for (Op one : OPS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /**
     * How long an expression may be.
     *
     * <p>A ceiling rather than a guess: the whole point of the shape below is
     * that an expression cannot loop, so the only way it can take a long time
     * is by being long, and that is something a number can stop.
     */
    public static final int MOST_NODES = 64;

    /**
     * The most steps a ceiling may allow.
     *
     * <p>A network works an expression out every tick. A ceiling of a hundred
     * thousand is a ceiling in the same sense the sky is: nothing reaches it,
     * and a player who writes one long enough to matter finds out by watching
     * their server stop.
     */
    public static final int CEILING = 256;

    /**
     * Whether step {@code at} may read step {@code from}.
     *
     * <p><b>Backwards only.</b> This one line is the entire termination
     * argument for the logic layer: an expression is a list and every arrow in
     * it points left, so there is no cycle to detect because there is no cycle
     * that can be written down.
     */
    public static boolean mayRead(int at, int from) {
        return from >= 0 && from < at;
    }

    /**
     * One step of an expression.
     *
     * <p>{@code inputs} are the positions of earlier steps. <b>Earlier</b> is
     * the whole trick: a node that may only look backwards cannot take part in
     * a cycle, so there is no cycle detection here at all — there is nothing
     * to detect.
     *
     * @param op     the name of one of {@link #OPS}
     * @param inputs the positions in the expression its arguments come from
     * @param value  the item a reader reads, where the operator wants one
     */
    public record Node(String op, List<Integer> inputs, String value) {

        public Node {
            Objects.requireNonNull(op, "a step with no operator");
            inputs = List.copyOf(inputs);
        }
    }

    /**
     * The type an expression works out to, or null if it does not work out.
     *
     * <p>Worked out without running anything, which is the point: a player who
     * has asked the network to add a number to a chest finds out when they
     * write it down, not when a server tick throws.
     */
    public static String typeOf(List<Node> expression) {
        List<String> types = typesIn(expression);
        if (types == null || types.isEmpty()) {
            return null;
        }
        return types.get(types.size() - 1);
    }

    /**
     * The type of every step, or null if any step does not work out.
     */
    public static List<String> typesIn(List<Node> expression) {
        if (expression.isEmpty() || expression.size() > MOST_NODES) {
            return null;
        }
        List<String> types = new ArrayList<>();
        for (int at = 0; at < expression.size(); at++) {
            Node node = expression.get(at);
            Op op = op(node.op());
            if (op == null) {
                return null;
            }
            if (node.inputs().size() != op.arity()) {
                return null;
            }
            for (int which = 0; which < op.arity(); which++) {
                int from = node.inputs().get(which);
                if (!mayRead(at, from)) {
                    return null;
                }
                // Belt and braces, and deliberately: the line above is the
                // rule, and this is what stops a mistake in it becoming an
                // exception thrown out of a rules file on a server tick.
                if (from >= types.size()) {
                    return null;
                }
                if (!types.get(from).equals(op.takes().get(which))) {
                    return null;
                }
            }
            types.add(op.gives());
        }
        return List.copyOf(types);
    }

    /** Whether an expression works out at all. */
    public static boolean sound(List<Node> expression) {
        return typeOf(expression) != null;
    }

    /**
     * Why an expression was refused, in words a player could be shown.
     *
     * <p>Empty means it was not. Written separately from {@link #typesIn}
     * rather than returned by it, because the thing that has to be fast is
     * deciding, and the thing that has to be clear is explaining.
     */
    public static List<String> refuses(List<Node> expression) {
        List<String> why = new ArrayList<>();
        if (expression.isEmpty()) {
            why.add("an expression with no steps in it answers nothing");
            return List.copyOf(why);
        }
        if (expression.size() > MOST_NODES) {
            why.add("an expression of " + expression.size() + " steps is longer "
                    + "than the " + MOST_NODES + " a network will work out");
        }
        List<String> types = new ArrayList<>();
        for (int at = 0; at < expression.size(); at++) {
            Node node = expression.get(at);
            Op op = op(node.op());
            if (op == null) {
                why.add("step " + at + " asks for " + node.op()
                        + ", which is not an operator");
                return List.copyOf(why);
            }
            if (node.inputs().size() != op.arity()) {
                why.add("step " + at + " gives " + node.inputs().size()
                        + " thing(s) to " + op.name() + ", which takes "
                        + op.arity());
                return List.copyOf(why);
            }
            for (int which = 0; which < op.arity(); which++) {
                int from = node.inputs().get(which);
                if (!mayRead(at, from)) {
                    why.add("step " + at + " reads step " + from
                            + ", which is not a step before it — and an arrow "
                            + "pointing forwards is how an expression loops");
                    return List.copyOf(why);
                }
                if (from >= types.size()) {
                    return List.copyOf(why);
                }
                if (!types.get(from).equals(op.takes().get(which))) {
                    why.add("step " + at + " gives a " + types.get(from) + " to "
                            + op.name() + ", which takes a "
                            + op.takes().get(which) + " there");
                    return List.copyOf(why);
                }
            }
            types.add(op.gives());
        }
        return List.copyOf(why);
    }

    /**
     * How many steps an expression takes to work out.
     *
     * <p>Exactly its length, always, because every arrow points backwards and
     * nothing is ever worked out twice. That this is a one-line method is the
     * whole argument for the shape.
     */
    public static int stepsFor(List<Node> expression) {
        return expression.size();
    }

    /** Every type nothing produces, and every type nothing consumes. */
    public static List<String> deadTypes() {
        List<String> bad = new ArrayList<>();
        Set<String> made = new LinkedHashSet<>();
        Set<String> used = new LinkedHashSet<>();
        for (Op one : OPS) {
            made.add(one.gives());
            used.addAll(one.takes());
        }
        for (String type : TYPES) {
            if (!made.contains(type)) {
                bad.add("nothing gives a " + type + ", so no expression can ever "
                        + "have one in it");
            }
            if (!used.contains(type) && !TEXT.equals(type) && !TRUTH.equals(type)) {
                bad.add("nothing takes a " + type + ", so one is a dead end");
            }
        }
        boolean reader = false;
        for (Op one : OPS) {
            if (one.arity() == 0) {
                reader = true;
                break;
            }
        }
        if (!reader) {
            bad.add("no operator takes nothing, so there is no way for a value to "
                    + "get into an expression from the world at all");
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Running one
    // ==================================================================

    /**
     * What the world looks like to an expression: the four things a reader
     * can read. The game implements this; the harness implements it with
     * numbers it chose, which is how the evaluation is proved without a
     * network to run on.
     */
    public interface Readings {

        /** How many of this item the network holds. */
        long countOf(String item);

        /** What the grid is giving the network, in FE per tick. */
        long power();

        /** How many things are on the network. */
        long devices();

        /** Whether the network is making something. */
        boolean busy();

        /** What a player calls this item, rather than what the registry does. */
        String nameOf(String item);
    }

    /**
     * One worked-out value, tagged with its type.
     *
     * <p>One record with a field per type rather than four classes, because
     * the thing that matters about a value is that it is <em>the type the
     * expression said</em>, and that is one comparison against
     * {@link #typesIn} rather than four {@code instanceof}s.
     */
    public record Value(String type, long number, boolean truth, String item, String text) {

        public static Value ofNumber(long number) {
            return new Value(NUMBER, number, false, "", "");
        }

        public static Value ofTruth(boolean truth) {
            return new Value(TRUTH, 0, truth, "", "");
        }

        public static Value ofItem(String item) {
            return new Value(ITEM, 0, false, item == null ? "" : item, "");
        }

        public static Value ofText(String text) {
            return new Value(TEXT, 0, false, "", text == null ? "" : text);
        }
    }

    /**
     * The number a {@code constant} step stands for.
     *
     * <p>Lenient on purpose: an unreadable constant is zero rather than an
     * exception, because the only thing that writes constants is the
     * detector's threshold ladder and the only thing that could make one
     * unreadable is a save edited by hand. A tick must not throw over that.
     */
    public static long numberIn(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException wrong) {
            return 0;
        }
    }

    /**
     * Work an expression out, or null if it does not work out.
     *
     * <p>Typed first and run second, and the typing is the whole of the
     * safety argument: by the time a step is run every input it names is an
     * earlier step of exactly the type its operator takes, so there is no
     * branch in here for "the wrong kind of thing arrived" — there is
     * nowhere it could have come from. The harness proves that on forty
     * thousand random expressions: every one that types evaluates, to a value
     * of the type the typing said.
     */
    public static Value evaluate(List<Node> expression, Readings world) {
        List<String> types = typesIn(expression);
        if (types == null) {
            return null;
        }
        List<Value> values = new ArrayList<>();
        for (int at = 0; at < expression.size(); at++) {
            Node node = expression.get(at);
            List<Value> in = new ArrayList<>();
            for (int from : node.inputs()) {
                in.add(values.get(from));
            }
            Value made = step(node, in, world);
            if (made == null || !made.type().equals(types.get(at))) {
                return null;
            }
            values.add(made);
        }
        return values.get(values.size() - 1);
    }

    /** One step, on inputs the typing has already vouched for. */
    private static Value step(Node node, List<Value> in, Readings world) {
        switch (node.op()) {
            case "held":
                return Value.ofItem(node.value());
            case "constant":
                return Value.ofNumber(numberIn(node.value()));
            case "read_count":
                return Value.ofNumber(world.countOf(in.get(0).item()));
            case "read_power":
                return Value.ofNumber(world.power());
            case "read_devices":
                return Value.ofNumber(world.devices());
            case "read_busy":
                return Value.ofTruth(world.busy());
            case "add":
                return Value.ofNumber(in.get(0).number() + in.get(1).number());
            case "subtract":
                return Value.ofNumber(in.get(0).number() - in.get(1).number());
            case "multiply":
                return Value.ofNumber(in.get(0).number() * in.get(1).number());
            case "smaller_of":
                return Value.ofNumber(Math.min(in.get(0).number(), in.get(1).number()));
            case "larger_of":
                return Value.ofNumber(Math.max(in.get(0).number(), in.get(1).number()));
            case "equal":
                return Value.ofTruth(in.get(0).number() == in.get(1).number());
            case "less":
                return Value.ofTruth(in.get(0).number() < in.get(1).number());
            case "greater":
                return Value.ofTruth(in.get(0).number() > in.get(1).number());
            case "both":
                return Value.ofTruth(in.get(0).truth() && in.get(1).truth());
            case "either":
                return Value.ofTruth(in.get(0).truth() || in.get(1).truth());
            case "not":
                return Value.ofTruth(!in.get(0).truth());
            case "named":
                return Value.ofText(world.nameOf(in.get(0).item()));
            case "spelled":
                return Value.ofText(Long.toString(in.get(0).number()));
            case "joined":
                // With a space between, because the only reason to join two
                // pieces of text is to read them as one line.
                return Value.ofText(in.get(0).text() + " " + in.get(1).text());
            default:
                return null;
        }
    }

    /** Every operator {@link #step} does not know how to run. Must be empty. */
    public static List<String> unrunnable() {
        List<String> bad = new ArrayList<>();
        Readings nothing = new Readings() {
            @Override
            public long countOf(String item) {
                return 0;
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
        for (Op one : OPS) {
            List<Value> in = new ArrayList<>();
            for (String type : one.takes()) {
                in.add(new Value(type, 0, false, "", ""));
            }
            if (step(new Node(one.name(), List.of(), "0"), in, nothing) == null) {
                bad.add(one.name() + " is an operator the network cannot run, so an "
                        + "expression that uses it types and then does nothing");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // The two expressions the devices run
    // ==================================================================

    /**
     * What a detector asks: <em>does the network hold at least this many of
     * the thing I was shown?</em>
     *
     * <p>Five steps, and they are the reason the logic layer is a thing a
     * player meets rather than a file: the detector does not compare two
     * numbers in Java and call it logic, it writes the question down in the
     * language above and runs it. Change what {@code less} means and every
     * detector in the world changes with it.
     */
    public static List<Node> detectorExpression(String item, int threshold) {
        return List.of(
                new Node("held", List.of(), item),
                new Node("read_count", List.of(0), ""),
                new Node("constant", List.of(), Integer.toString(threshold)),
                new Node("less", List.of(1, 2), ""),
                new Node("not", List.of(3), ""));
    }

    /**
     * What a monitor says about the thing it was shown: its name and how
     * many the network holds, joined into one line of text.
     */
    public static List<Node> monitorExpression(String item) {
        return List.of(
                new Node("held", List.of(), item),
                new Node("read_count", List.of(0), ""),
                new Node("spelled", List.of(1), ""),
                new Node("named", List.of(0), ""),
                new Node("joined", List.of(2, 3), ""));
    }

    /** Every block id this file adds. */
    public static List<String> catalogue() {
        List<String> made = new ArrayList<>();
        for (Device one : DEVICES) {
            made.add(one.name());
        }
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }
}
