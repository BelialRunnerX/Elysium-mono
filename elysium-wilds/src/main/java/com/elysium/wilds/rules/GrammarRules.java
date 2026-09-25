package com.elysium.wilds.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Buildings, laid out rather than drawn. The JDK and nothing else.
 *
 * <h2>What this replaces</h2>
 *
 * Six and a half thousand of this unit's content weight is one jar full of
 * hand-built NBT templates. docs/CONVERSION.md section 11 says why they are
 * not being copied and what it costs: a hand-composed blacksmith has
 * character a generated one does not.
 *
 * <p>What it buys is that a generated building can be <em>proved</em>, and
 * every property worth proving about a structure is one nobody sees by
 * looking at it:
 *
 * <ol>
 *   <li><b>Every room is reachable from the door.</b> A sealed wing renders
 *       perfectly and generates perfectly, and it is a room a player can only
 *       reach by mining. Flood-filled here, over doors.
 *   <li><b>Nothing overlaps.</b> Two rooms in one space is a wall that is not
 *       there and a chest inside a staircase.
 *   <li><b>Every building fits the box it claimed</b>, so a structure cannot
 *       spill into the chunk next door and be cut in half.
 *   <li><b>Laying one out always finishes.</b> A grammar that can expand into
 *       itself for ever is a chunk that never generates, which is a server
 *       that stops rather than a building that looks wrong.
 * </ol>
 *
 * <p>And one more that is not about correctness: <b>the same seed lays out the
 * same building</b>. Without it the structure a player walks into is not the
 * structure the server thinks is there.
 */
public final class GrammarRules {

    private GrammarRules() {
    }

    // ==================================================================
    // The six ways out of a room
    // ==================================================================

    public static final int NORTH = 0;
    public static final int SOUTH = 1;
    public static final int EAST = 2;
    public static final int WEST = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    public static final int WAYS = 6;

    /** Which way is the other way. */
    public static int opposite(int way) {
        return switch (way) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            default -> throw new IllegalArgumentException("there is no way " + way);
        };
    }

    /** How far a step that way moves, in rooms. */
    public static int[] step(int way) {
        return switch (way) {
            case NORTH -> new int[] {0, 0, -1};
            case SOUTH -> new int[] {0, 0, 1};
            case EAST -> new int[] {1, 0, 0};
            case WEST -> new int[] {-1, 0, 0};
            case UP -> new int[] {0, 1, 0};
            case DOWN -> new int[] {0, -1, 0};
            default -> throw new IllegalArgumentException("there is no way " + way);
        };
    }

    // ==================================================================
    // The rooms
    // ==================================================================

    /**
     * One kind of room.
     *
     * @param name  what it is called
     * @param doors which of the six ways out it has, as a set of bits
     * @param ends  whether the grammar may stop here
     */
    public record Room(String name, int doors, boolean ends) {

        public Room {
            Objects.requireNonNull(name, "a room with no name");
            if (doors == 0) {
                throw new IllegalArgumentException(name + " has no doors at all, "
                        + "so it can only ever be reached by mining");
            }
            if (doors < 0 || doors >= (1 << WAYS)) {
                throw new IllegalArgumentException(name + " has doors that are "
                        + "not among the " + WAYS + " ways out of a room");
            }
        }

        /** Whether it has a door this way. */
        public boolean opens(int way) {
            return (doors & (1 << way)) != 0;
        }

        /** How many doors it has. */
        public int howManyDoors() {
            int count = 0;
            for (int way = 0; way < WAYS; way++) {
                if (opens(way)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static int ways(int... which) {
        int bits = 0;
        for (int way : which) {
            bits |= 1 << way;
        }
        return bits;
    }

    /** Every kind of room any grammar may use. */
    public static final List<Room> ROOMS = List.of(
            new Room("door", ways(NORTH, SOUTH, EAST, WEST), true),
            new Room("hall", ways(NORTH, SOUTH, EAST, WEST, UP, DOWN), false),
            new Room("chamber", ways(NORTH, SOUTH, EAST, WEST), true),
            // Two doors, and they are the point: a corner turns. It can only
            // be attached where the building was already going south or west,
            // which is what makes it a corner rather than another chamber.
            new Room("corner", ways(NORTH, EAST), true),
            new Room("passage", ways(NORTH, SOUTH, EAST, WEST), false),
            new Room("stair", ways(NORTH, SOUTH, EAST, WEST, UP, DOWN), false),
            new Room("cellar", ways(NORTH, SOUTH, EAST, WEST, UP), true),
            new Room("loft", ways(NORTH, SOUTH, EAST, WEST, DOWN), true),
            new Room("yard", ways(NORTH, SOUTH, EAST, WEST), true),
            new Room("shrine", ways(NORTH, SOUTH, EAST, WEST), true),
            new Room("store", ways(NORTH, SOUTH, EAST, WEST), true),
            new Room("watch", ways(NORTH, SOUTH, EAST, WEST, DOWN), true));

    /** A room by name, or null. */
    public static Room room(String name) {
        for (Room one : ROOMS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // The grammars
    // ==================================================================

    /**
     * One kind of building.
     *
     * @param name  what it is called
     * @param start the room a player walks in through
     * @param after what may follow each kind of room
     * @param rooms the most rooms one of these may have
     * @param box   how many rooms across, up and along it may claim
     */
    public record Grammar(String name, String start, Map<String, List<String>> after,
                          int rooms, int box) {

        public Grammar {
            Objects.requireNonNull(name, "a grammar with no name");
            after = Map.copyOf(after);
            if (rooms < 2) {
                throw new IllegalArgumentException(name + " may have " + rooms
                        + " room(s), which is a block rather than a building");
            }
            if (box < 2) {
                throw new IllegalArgumentException(name + " claims a box " + box
                        + " room(s) across");
            }
        }
    }

    private static Map<String, List<String>> after(Object... pairs) {
        Map<String, List<String>> made = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            made.put((String) pairs[at], List.of(((String) pairs[at + 1]).split(" ")));
        }
        return made;
    }

    /** Every kind of building. */
    public static final List<Grammar> GRAMMARS = List.of(
            new Grammar("hamlet", "door", after(
                    "door", "hall yard",
                    "hall", "chamber store yard passage",
                    "passage", "chamber yard",
                    "yard", "chamber store",
                    "chamber", "store",
                    "store", "store"), 14, 7),
            new Grammar("watchtower", "door", after(
                    "door", "stair",
                    "stair", "stair loft watch",
                    "loft", "watch",
                    "watch", "watch"), 8, 3),
            new Grammar("camp", "door", after(
                    "door", "yard",
                    "yard", "store chamber yard",
                    "chamber", "store",
                    "store", "store"), 7, 5),
            new Grammar("ruin", "door", after(
                    "door", "chamber passage",
                    "passage", "chamber corner cellar",
                    "corner", "chamber",
                    "chamber", "cellar store",
                    "cellar", "store",
                    "store", "store"), 11, 6),
            new Grammar("lodge", "door", after(
                    "door", "hall",
                    "hall", "chamber stair store",
                    "stair", "loft",
                    "loft", "store",
                    "chamber", "store chamber",
                    "store", "store"), 10, 5),
            new Grammar("mine", "door", after(
                    "door", "stair",
                    "stair", "cellar",
                    "cellar", "passage cellar",
                    "passage", "cellar store corner",
                    "corner", "cellar",
                    "store", "store"), 16, 7),
            new Grammar("shrine", "door", after(
                    "door", "passage",
                    "passage", "shrine chamber",
                    "chamber", "shrine",
                    "shrine", "shrine"), 6, 4),
            new Grammar("keep", "door", after(
                    "door", "hall",
                    "hall", "chamber stair yard passage",
                    "passage", "chamber corner",
                    "corner", "chamber",
                    "stair", "loft watch",
                    "loft", "watch store",
                    "yard", "chamber store",
                    "chamber", "store cellar",
                    "cellar", "store",
                    "watch", "watch",
                    "store", "store"), 20, 8));

    /** A grammar by name, or null. */
    public static Grammar grammar(String name) {
        for (Grammar one : GRAMMARS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // Laying one out
    // ==================================================================

    /** One room, put somewhere. */
    public record Placed(String room, int x, int y, int z) {
    }

    /** A building: where its rooms are, and the box they are all inside. */
    public record Plan(String grammar, List<Placed> rooms, int[] low, int[] high) {

        public Plan {
            rooms = List.copyOf(rooms);
            low = low.clone();
            high = high.clone();
        }

        /** How many rooms across, up and along it actually is. */
        public int[] size() {
            return new int[] {high[0] - low[0] + 1, high[1] - low[1] + 1,
                    high[2] - low[2] + 1};
        }
    }

    /**
     * How many attempts a layout may make before it gives up.
     *
     * <p>Not a safety net: it is the termination argument. Every attempt
     * either places a room or is discarded, and there are only so many
     * attempts, so laying a building out finishes whatever the grammar says.
     */
    public static final int TRIES = 512;

    /**
     * Lay out a building.
     *
     * <p>Deterministic in the seed and in nothing else: no clock, no hash of
     * an object's identity, no iteration order of anything unordered. A
     * structure the server lays out differently from the client is a building
     * a player walks through the wall of.
     */
    public static Plan layOut(String grammar, long seed) {
        Grammar rules = grammar(grammar);
        if (rules == null) {
            throw new IllegalArgumentException(grammar + " is not a grammar");
        }
        List<Placed> placed = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        placed.add(new Placed(rules.start(), 0, 0, 0));
        taken.add(at(0, 0, 0));
        long state = next(seed);
        // How far from the door a room may be. A box `box` rooms across holds
        // coordinates from -reach to +reach, which is 2 * reach + 1 of them --
        // so reach is (box - 1) / 2 and not box / 2. The other answer builds
        // one room too many along every axis, and a building one room too wide
        // is one cut in half by the chunk it spills into.
        int reach = (rules.box() - 1) / 2;
        // How big this one wants to be. Between half the budget and all of it,
        // worked out from the seed, so that two hamlets are not the same size
        // as well as the same shape.
        int wanted = rules.rooms() / 2 + 1
                + (int) Math.floorMod(state >> 16, rules.rooms() / 2 + 1);
        wanted = Math.min(wanted, rules.rooms());
        for (int tries = 0; tries < TRIES && placed.size() < wanted; tries++) {
            state = next(state);
            Placed from = placed.get((int) Math.floorMod(state >> 16, placed.size()));
            state = next(state);
            int way = (int) Math.floorMod(state >> 16, WAYS);
            Room here = room(from.room());
            if (here == null || !here.opens(way)) {
                continue;
            }
            List<String> allowed = rules.after().getOrDefault(from.room(), List.of());
            if (allowed.isEmpty()) {
                continue;
            }
            state = next(state);
            String want = allowed.get((int) Math.floorMod(state >> 16, allowed.size()));
            Room next = room(want);
            if (next == null || !next.opens(opposite(way))) {
                continue;
            }
            int[] moved = step(way);
            int x = from.x() + moved[0];
            int y = from.y() + moved[1];
            int z = from.z() + moved[2];
            if (Math.abs(x) > reach || Math.abs(y) > reach || Math.abs(z) > reach) {
                continue;
            }
            if (!taken.add(at(x, y, z))) {
                continue;
            }
            placed.add(new Placed(want, x, y, z));
        }
        int[] low = {0, 0, 0};
        int[] high = {0, 0, 0};
        for (Placed one : placed) {
            low[0] = Math.min(low[0], one.x());
            low[1] = Math.min(low[1], one.y());
            low[2] = Math.min(low[2], one.z());
            high[0] = Math.max(high[0], one.x());
            high[1] = Math.max(high[1], one.y());
            high[2] = Math.max(high[2], one.z());
        }
        return new Plan(grammar, placed, low, high);
    }

    private static String at(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * The next number in the sequence.
     *
     * <p>Written out rather than taken from {@code java.util.Random}, because
     * a structure has to lay out identically on a server and on a client that
     * may be a different Java, and the only way to be sure of that is to say
     * what the arithmetic is.
     */
    public static long next(long state) {
        long made = state * 6364136223846793005L + 1442695040888963407L;
        return made ^ (made >>> 29);
    }

    // ==================================================================
    // What has to be true of a building
    // ==================================================================

    /** Every room of a plan that cannot be walked to from the door. */
    public static List<String> unreachable(Plan plan) {
        Map<String, Placed> where = new LinkedHashMap<>();
        for (Placed one : plan.rooms()) {
            where.put(at(one.x(), one.y(), one.z()), one);
        }
        Set<String> seen = new LinkedHashSet<>();
        Deque<Placed> waiting = new ArrayDeque<>();
        if (plan.rooms().isEmpty()) {
            return List.of("a building with no rooms in it");
        }
        Placed door = plan.rooms().get(0);
        waiting.push(door);
        seen.add(at(door.x(), door.y(), door.z()));
        while (!waiting.isEmpty()) {
            Placed here = waiting.pop();
            Room kind = room(here.room());
            if (kind == null) {
                continue;
            }
            for (int way = 0; way < WAYS; way++) {
                if (!kind.opens(way)) {
                    continue;
                }
                int[] moved = step(way);
                String key = at(here.x() + moved[0], here.y() + moved[1],
                        here.z() + moved[2]);
                Placed there = where.get(key);
                if (there == null || seen.contains(key)) {
                    continue;
                }
                Room other = room(there.room());
                if (other == null || !other.opens(opposite(way))) {
                    continue;
                }
                seen.add(key);
                waiting.push(there);
            }
        }
        List<String> stranded = new ArrayList<>();
        for (Placed one : plan.rooms()) {
            if (!seen.contains(at(one.x(), one.y(), one.z()))) {
                stranded.add(one.room() + " at " + at(one.x(), one.y(), one.z()));
            }
        }
        return List.copyOf(stranded);
    }

    /** Every place two rooms of a plan are in. */
    public static List<String> overlapping(Plan plan) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        for (Placed one : plan.rooms()) {
            String key = at(one.x(), one.y(), one.z());
            if (!seen.add(key)) {
                bad.add("two rooms at " + key);
            }
        }
        return List.copyOf(bad);
    }

    /** Everything wrong with a plan. Must be empty. */
    public static List<String> faultsIn(Plan plan) {
        List<String> bad = new ArrayList<>(unreachable(plan));
        bad.addAll(overlapping(plan));
        Grammar rules = grammar(plan.grammar());
        if (rules == null) {
            bad.add(plan.grammar() + " is not a grammar");
            return List.copyOf(bad);
        }
        if (plan.rooms().size() > rules.rooms()) {
            bad.add(plan.grammar() + " laid out " + plan.rooms().size()
                    + " rooms and may have " + rules.rooms());
        }
        if (plan.rooms().isEmpty()) {
            bad.add(plan.grammar() + " laid out nothing at all");
            return List.copyOf(bad);
        }
        if (!plan.rooms().get(0).room().equals(rules.start())) {
            bad.add(plan.grammar() + " starts with a " + plan.rooms().get(0).room()
                    + " and its door is a " + rules.start());
        }
        int[] size = plan.size();
        for (int axis = 0; axis < 3; axis++) {
            if (size[axis] > rules.box()) {
                bad.add(plan.grammar() + " is " + size[axis] + " rooms across and "
                        + "claimed a box " + rules.box() + " across, so it spills "
                        + "into the chunk next door and is cut in half");
            }
        }
        for (Placed one : plan.rooms()) {
            if (room(one.room()) == null) {
                bad.add(one.room() + " is not a room");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Everything wrong with the grammars themselves. Must be empty.
     *
     * <p>A rule naming a room that does not exist, a room nothing can follow,
     * a grammar with no way to stop — each of them is a building that either
     * cannot be laid out or never stops being laid out.
     */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Room one : ROOMS) {
            if (!named.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        Set<String> grammars = new LinkedHashSet<>();
        for (Grammar one : GRAMMARS) {
            if (!grammars.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
            bad.addAll(faultsInGrammar(one));
        }
        for (Room one : ROOMS) {
            boolean used = false;
            for (Grammar grammar : GRAMMARS) {
                if (grammar.start().equals(one.name())) {
                    used = true;
                }
                for (List<String> into : grammar.after().values()) {
                    if (into.contains(one.name())) {
                        used = true;
                    }
                }
            }
            if (!used) {
                bad.add(one.name() + " is a room no grammar ever puts anywhere, so "
                        + "it is a room that does not exist");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * What is wrong with one grammar, if anything.
     *
     * <p>Split out from the walk so the harness can hand it one built to be
     * caught: over eight healthy grammars this can only ever answer no, which
     * is what a check that has stopped working also answers.
     */
    public static List<String> faultsInGrammar(Grammar one) {
        List<String> bad = new ArrayList<>();
        {
            if (room(one.start()) == null) {
                bad.add(one.name() + " starts with " + one.start()
                        + ", which is not a room");
            }
            boolean canStop = false;
            for (Map.Entry<String, List<String>> rule : one.after().entrySet()) {
                if (room(rule.getKey()) == null) {
                    bad.add(one.name() + " has a rule for " + rule.getKey()
                            + ", which is not a room");
                }
                for (String into : rule.getValue()) {
                    Room next = room(into);
                    if (next == null) {
                        bad.add(one.name() + " may put a " + into + " after a "
                                + rule.getKey() + ", and there is no such room");
                        continue;
                    }
                    if (next.ends()) {
                        canStop = true;
                    }
                }
            }
            if (!canStop) {
                bad.add(one.name() + " has no rule that reaches a room the grammar "
                        + "may stop at, so laying one out only ever stops by "
                        + "running out of tries");
            }
            if (!one.after().containsKey(one.start())) {
                bad.add(one.name() + " has no rule for what follows its own door, "
                        + "so every one of them is a single room");
            }
        }
        return List.copyOf(bad);
    }

    /** How many kinds of building there are. */
    public static int howMany() {
        return GRAMMARS.size();
    }
}
