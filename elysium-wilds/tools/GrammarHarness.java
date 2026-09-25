package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The room grammar, executed.
 *
 * <p>Eight kinds of building, laid out from ten thousand seeds apiece, and
 * every one of the eighty thousand proved to have the four properties a
 * hand-built template is simply assumed to have:
 *
 * <ul>
 *   <li>every room walkable from the door,
 *   <li>no two rooms in one place,
 *   <li>the whole thing inside the box it claimed,
 *   <li>and the same seed laying out the same building every time.
 * </ul>
 *
 * <p>The first of those is the one worth the trouble. A sealed wing generates
 * perfectly, renders perfectly, and is a room a player can only get into by
 * mining — and there is no way to notice it except by walking every building
 * the generator will ever make, which is what this does.
 */
public final class GrammarHarness {

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

    /** How many buildings of each kind to lay out and walk. */
    public static final int SEEDS = 10_000;

    public static void main(String[] args) {
        theGrammars();
        theBuildings();
        theSameSeed();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + GrammarRules.howMany()
                + " kinds of building laid out from " + SEEDS + " seeds apiece, "
                + "every room in every one of them walkable from the door, no two "
                + "rooms in one place, nothing outside the box it claimed, and the "
                + "same seed laying out the same building twice");
    }

    // ==================================================================

    private static void theGrammars() {
        check(GrammarRules.faults().isEmpty(),
                "something wrong with the grammars themselves: "
                        + GrammarRules.faults());
        check(GrammarRules.howMany() >= 4, "there are only "
                + GrammarRules.howMany() + " kinds of building, and the jar this "
                + "replaces has hundreds of templates");
        for (int way = 0; way < GrammarRules.WAYS; way++) {
            check(GrammarRules.opposite(GrammarRules.opposite(way)) == way,
                    "the other way from the other way from " + way + " is not "
                            + way);
            int[] there = GrammarRules.step(way);
            int[] back = GrammarRules.step(GrammarRules.opposite(way));
            check(there[0] + back[0] == 0 && there[1] + back[1] == 0
                            && there[2] + back[2] == 0,
                    "stepping " + way + " and stepping back does not come home, "
                            + "so a door leads somewhere its neighbour's door "
                            + "does not come back from");
        }
        Set<String> used = new LinkedHashSet<>();
        for (GrammarRules.Grammar one : GrammarRules.GRAMMARS) {
            check(GrammarRules.grammar(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            check(used.add(one.name()), one.name() + " is declared twice");
            check(one.rooms() >= 2, one.name() + " is one room");
            for (Map.Entry<String, List<String>> rule : one.after().entrySet()) {
                check(GrammarRules.room(rule.getKey()) != null,
                        one.name() + " has a rule for " + rule.getKey());
                for (String into : rule.getValue()) {
                    check(GrammarRules.room(into) != null,
                            one.name() + " may put a " + into + " somewhere");
                }
            }
        }
        for (GrammarRules.Room one : GrammarRules.ROOMS) {
            check(one.howManyDoors() > 0, one.name() + " has no doors");
            check(GrammarRules.room(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            for (int way = 0; way < GrammarRules.WAYS; way++) {
                check(one.opens(way) == ((one.doors() & (1 << way)) != 0),
                        one.name() + " disagrees with itself about the door " + way);
            }
        }
        System.out.println("  the grammars: " + GrammarRules.howMany()
                + " kind(s) of building out of " + GrammarRules.ROOMS.size()
                + " kind(s) of room, every rule naming a room that exists and "
                + "every one of them able to stop");
    }

    // ==================================================================

    private static void theBuildings() {
        Map<String, int[]> sizes = new LinkedHashMap<>();
        int walked = 0;
        for (GrammarRules.Grammar one : GrammarRules.GRAMMARS) {
            int fewest = Integer.MAX_VALUE;
            int most = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                GrammarRules.Plan plan = GrammarRules.layOut(one.name(), seed);
                List<String> faults = GrammarRules.faultsIn(plan);
                check(faults.isEmpty(), one.name() + " from seed " + seed + ": "
                        + faults);
                check(GrammarRules.unreachable(plan).isEmpty(),
                        one.name() + " from seed " + seed + " has a room nobody "
                                + "can walk to");
                check(GrammarRules.overlapping(plan).isEmpty(),
                        one.name() + " from seed " + seed + " has two rooms in "
                                + "one place");
                check(plan.rooms().size() >= 2, one.name() + " from seed " + seed
                        + " is " + plan.rooms().size() + " room(s)");
                fewest = Math.min(fewest, plan.rooms().size());
                most = Math.max(most, plan.rooms().size());
                walked++;
            }
            sizes.put(one.name(), new int[] {fewest, most});
            check(most > fewest, one.name() + " is always exactly " + most
                    + " rooms, so every one of them is the same size and the "
                    + "grammar is a template with the serial numbers filed off");
            check(most <= one.rooms(), one.name() + " went past its own budget");
        }
        check(walked == SEEDS * GrammarRules.howMany(),
                "only " + walked + " buildings were laid out");
        StringBuilder said = new StringBuilder();
        for (Map.Entry<String, int[]> entry : sizes.entrySet()) {
            if (said.length() > 0) {
                said.append(", ");
            }
            said.append(entry.getKey()).append(' ').append(entry.getValue()[0])
                    .append('-').append(entry.getValue()[1]);
        }
        System.out.println("  the buildings: " + walked + " laid out and walked "
                + "room by room — " + said);
    }

    // ==================================================================

    private static void theSameSeed() {
        // A structure the server lays out differently from the client is a
        // building a player walks through the wall of.
        for (GrammarRules.Grammar one : GrammarRules.GRAMMARS) {
            for (long seed = 0; seed < 500; seed++) {
                GrammarRules.Plan first = GrammarRules.layOut(one.name(), seed);
                GrammarRules.Plan again = GrammarRules.layOut(one.name(), seed);
                check(first.rooms().equals(again.rooms()),
                        one.name() + " from seed " + seed + " laid out differently "
                                + "the second time, so the building on the server "
                                + "is not the building on the screen");
            }
        }
        // And different seeds lay out different buildings, or the seed is
        // decoration.
        Set<String> seen = new LinkedHashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            seen.add(GrammarRules.layOut("keep", seed).rooms().toString());
        }
        check(seen.size() > 100, "five hundred seeds made " + seen.size()
                + " different keeps, so the seed is decoration and every keep in "
                + "the world is the same building");
        System.out.println("  the same seed: " + GrammarRules.howMany() * 500
                + " buildings laid out twice apiece and identical both times, and "
                + seen.size() + " different keeps out of five hundred seeds");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        // A building with a room nobody can walk to, built to be caught.
        GrammarRules.Plan stranded = new GrammarRules.Plan("hamlet", List.of(
                new GrammarRules.Placed("door", 0, 0, 0),
                new GrammarRules.Placed("chamber", 3, 0, 0)),
                new int[] {0, 0, 0}, new int[] {3, 0, 0});
        check(!GrammarRules.unreachable(stranded).isEmpty(),
                "a room three squares away from the door with nothing between is "
                        + "not reported as unreachable, so the one property this "
                        + "file exists for is unguarded");

        GrammarRules.Plan joined = new GrammarRules.Plan("hamlet", List.of(
                new GrammarRules.Placed("door", 0, 0, 0),
                new GrammarRules.Placed("chamber", 0, 0, 1)),
                new int[] {0, 0, 0}, new int[] {0, 0, 1});
        check(GrammarRules.unreachable(joined).isEmpty(),
                "a room next door to the door with doors between them is reported "
                        + "as unreachable, so the check says no to everything");

        // A room that has no door back the way it came: reachable one way and
        // not the other, which is the half of connectivity that gets missed.
        // North of the door, so reaching it means the corner would have to
        // open south — and a corner opens north and east and nothing else.
        GrammarRules.Plan oneWay = new GrammarRules.Plan("hamlet", List.of(
                new GrammarRules.Placed("door", 0, 0, 0),
                new GrammarRules.Placed("corner", 0, 0, -1)),
                new int[] {0, 0, -1}, new int[] {0, 0, 0});
        check(!GrammarRules.unreachable(oneWay).isEmpty(),
                "a corner with no door on the side it was reached from is counted "
                        + "as reachable, so a room is walked into through a wall");

        // Two rooms in one place.
        GrammarRules.Plan stacked = new GrammarRules.Plan("hamlet", List.of(
                new GrammarRules.Placed("door", 0, 0, 0),
                new GrammarRules.Placed("chamber", 0, 0, 0)),
                new int[] {0, 0, 0}, new int[] {0, 0, 0});
        check(!GrammarRules.overlapping(stacked).isEmpty(),
                "two rooms at the same place are not reported, so a chest can be "
                        + "inside a staircase");
        check(GrammarRules.overlapping(joined).isEmpty(),
                "two rooms side by side are reported as overlapping");

        // A building outside the box it claimed.
        List<GrammarRules.Placed> along = new ArrayList<>();
        GrammarRules.Grammar keep = GrammarRules.grammar("keep");
        for (int at = 0; at <= keep.box(); at++) {
            along.add(new GrammarRules.Placed(at == 0 ? "door" : "passage",
                    0, 0, at));
        }
        GrammarRules.Plan spilling = new GrammarRules.Plan("keep", along,
                new int[] {0, 0, 0}, new int[] {0, 0, keep.box()});
        check(!GrammarRules.faultsIn(spilling).isEmpty(),
                "a building one room wider than the box it claimed is not "
                        + "reported, so it spills into the chunk next door and is "
                        + "cut in half");

        // An empty building, and one that is not a grammar.
        GrammarRules.Plan nothing = new GrammarRules.Plan("hamlet", List.of(),
                new int[] {0, 0, 0}, new int[] {0, 0, 0});
        check(!GrammarRules.faultsIn(nothing).isEmpty(),
                "a building with no rooms in it is reported as fine");

        // A grammar that can never stop, built to be caught. Over eight
        // healthy grammars the check can only ever answer no.
        GrammarRules.Grammar endless = new GrammarRules.Grammar("control", "door",
                Map.of("door", List.of("passage"), "passage", List.of("hall"),
                        "hall", List.of("passage")), 6, 4);
        check(!GrammarRules.faultsInGrammar(endless).isEmpty(),
                "a grammar that can only ever reach rooms it may not stop at is "
                        + "not reported, so laying one out would stop by running "
                        + "out of tries and the building would be whatever that "
                        + "left behind");
        GrammarRules.Grammar ordinary = new GrammarRules.Grammar("control", "door",
                Map.of("door", List.of("chamber")), 6, 4);
        check(GrammarRules.faultsInGrammar(ordinary).isEmpty(),
                "an ordinary grammar is reported as faulty: "
                        + GrammarRules.faultsInGrammar(ordinary));
        GrammarRules.Grammar noDoor = new GrammarRules.Grammar("control", "cellar",
                Map.of("chamber", List.of("store")), 6, 4);
        check(!GrammarRules.faultsInGrammar(noDoor).isEmpty(),
                "a grammar with no rule for what follows its own door is not "
                        + "reported, so every one of them is a single room");

        check(GrammarRules.TRIES > 0, "laying a building out is allowed "
                + GrammarRules.TRIES + " tries");
        for (GrammarRules.Grammar one : GrammarRules.GRAMMARS) {
            check(GrammarRules.TRIES > one.rooms() * 4, one.name() + " may have "
                    + one.rooms() + " rooms and laying it out is allowed "
                    + GrammarRules.TRIES + " tries, which is not enough room for "
                    + "the ones that are discarded");
        }

        List<String> allowed = new ArrayList<>();
        try {
            new GrammarRules.Room("control", 0, true);
            allowed.add("a room with no doors");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new GrammarRules.Grammar("control", "door", Map.of(), 1, 4);
            allowed.add("a building of one room");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the guards allow what they say they forbid: "
                + allowed);

        // And the arithmetic the seed rests on.
        check(GrammarRules.next(0L) != 0L,
                "the sequence stays where it started, so every building from "
                        + "every seed is the same building");
        check(GrammarRules.next(1L) != GrammarRules.next(2L),
                "two seeds give the same number");

        System.out.println("  the controls: a stranded room caught, a room walked "
                + "into through a wall caught, two rooms in one place caught, and "
                + "a building a room wider than its own box caught");
    }
}
