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
