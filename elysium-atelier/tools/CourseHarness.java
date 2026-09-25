package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The courses, executed.
 *
 * <p>The one property this exists for is that <b>a stair can be walked up</b>.
 * Eight of the jars this module replaces add stair variants between them and
 * not one of them checks it, because there is nothing to look at: a stair you
 * have to jump up renders exactly like a stair you do not, and the player
 * blames their keyboard.
 *
 * <p>Everything else here is the arithmetic this module has proved from the
 * start — that no shape is a way of turning two blocks into three — applied to
 * a family whose prices are not typed at all but counted off the model. Which
 * moves the failure: a course cannot be mispriced any more, only mis-shaped,
 * and a mis-shaped one is the kind of mistake you can see.
 */
public final class CourseHarness {

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
        theKinds();
        theCuts();
        theWaysUp();
        theDecks();
        theClimb();
        theCutting();
        thePrices();
        theJoining();
        theTags();
        theGeometry();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every way up walkable without "
                + "jumping, every course priced at its own volume, nothing here "
                + "the game already has, no two of them the same shape, and not "
                + "one recipe that makes more than it takes");
    }

    // ==================================================================

    private static void theKinds() {
        check(!CourseRules.KINDS.isEmpty(), "there are no courses at all");
        check(CourseRules.repeated().isEmpty(),
                "a course is declared twice, or shares a name with something "
                        + "this module already has: " + CourseRules.repeated());
        check(CourseRules.sameShape().isEmpty(),
                "two courses fill exactly the same sixteenths: "
                        + CourseRules.sameShape());

        Set<String> families = new LinkedHashSet<>();
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            families.add(kind.family());
            check(CourseRules.kind(kind.name()) == kind,
                    kind.name() + " cannot be looked up by its own name");
            check(!CourseRules.boxesFor(kind.name()).isEmpty(),
                    kind.name() + " is made of no boxes at all, so it is an "
                            + "invisible block you cannot walk through");
            check(CourseRules.volume(kind.name()) > 0,
                    kind.name() + " fills nothing");
            check(CourseRules.volume(kind.name())
                            < CourseRules.SPAN * CourseRules.SPAN * CourseRules.SPAN,
                    kind.name() + " fills the whole block, and the game has one "
                            + "of those in every material already");
        }
        check(families.equals(new LinkedHashSet<>(CourseRules.FAMILIES)),
                "the families in use are " + families + " and the families are "
                        + CourseRules.FAMILIES + ", so one of them is a list nothing "
                        + "walks");
        System.out.println("  the kinds: " + CourseRules.KINDS.size()
                + " courses in " + families.size() + " families, none of them the "
                + "same shape as another, none of them a whole block");
    }

    // ==================================================================

    private static void theCuts() {
        // Every material named in the "the game has this already" list has to
        // be a material this module actually has, or the subtraction quietly
        // takes nothing away.
        Set<String> known = new LinkedHashSet<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            known.add(material.name());
        }
        for (Map.Entry<String, Set<String>> entry
                : CourseRules.VANILLA_ALREADY.entrySet()) {
            check(CourseRules.kind(entry.getKey()) != null,
                    entry.getKey() + " is in the list of what the game already "
                            + "has and is not a course at all");
            for (String material : entry.getValue()) {
                check(known.contains(material), material + " is named as something "
                        + "the game already has a " + entry.getKey() + " of, and "
                        + "is not a material this module has");
            }
        }
        for (String material : CourseRules.AS_THE_GAME_SPELLS_IT.keySet()) {
            check(known.contains(material), material + " has a spelling written "
                    + "down for it and is not a material this module has");
        }

        // The catalogue is the crossing minus what the game has, and it is
        // walked rather than listed.
        int expected = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (CourseRules.Kind kind : CourseRules.KINDS) {
                if (!CourseRules.alreadyInTheGame(kind.name(), material)) {
                    expected++;
                }
            }
        }
        check(CourseRules.howMany() == expected, "the catalogue holds "
                + CourseRules.howMany() + " courses and the crossing minus what "
                + "the game has is " + expected);
        check(CourseRules.alreadyHad().size()
                        + CourseRules.howMany()
                        == AtelierRules.MATERIALS.size() * CourseRules.KINDS.size(),
                "what we add and what the game already has do not between them "
                        + "account for every material crossed with every kind");

        Set<String> ids = new LinkedHashSet<>();
        for (CourseRules.Course course : CourseRules.catalogue()) {
            check(ids.add(course.id()), course.id() + " is in the catalogue twice");
            check(!CourseRules.alreadyInTheGame(course.kind(), course.material()),
                    course.id() + " is something the game already ships");
        }
        System.out.println("  the cuts: " + CourseRules.howMany() + " course(s) in "
                + AtelierRules.MATERIALS.size() + " material(s), with the "
                + CourseRules.alreadyHad().size() + " the game already ships left out");
    }

    // ==================================================================

    private static void theWaysUp() {
        check(CourseRules.unwalkable().isEmpty(),
                "a way up that is not one: " + CourseRules.unwalkable());

        int ways = 0;
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            if (!CourseRules.STEP.equals(kind.family())) {
                continue;
            }
            ways++;
            List<Integer> treads = CourseRules.treads(kind.name());
            check(treads.size() >= 2, kind.name() + " stands at one height");
            check(treads.get(treads.size() - 1) == CourseRules.SPAN,
                    kind.name() + " does not reach the block above it");
            int below = 0;
            for (int tread : treads) {
                check(tread - below <= CourseRules.LONGEST_STRIDE,
                        kind.name() + " steps from " + below + " to " + tread);
                below = tread;
            }
            // And it has to be a way up in every material, which is the whole
            // catalogue rather than the roster: a kind left out of the
            // crossing is a stair nobody can reach.
            for (AtelierRules.Material material : AtelierRules.MATERIALS) {
                check(!CourseRules.alreadyInTheGame(kind.name(), material),
                        kind.name() + " in " + material.name() + " is claimed to be "
                                + "in the game already, and the game has no such "
                                + "block in any material");
            }
        }
        check(ways >= 4, "only " + ways + " ways up, and the jars this replaces "
                + "have five distinct shapes between them");
        // The ceiling itself. Raised past a whole block it stops being a
        // ceiling, every staircase passes, and nothing above would say so.
        check(CourseRules.LONGEST_STRIDE < CourseRules.SPAN,
                "a player is said to walk up " + CourseRules.LONGEST_STRIDE
                        + " of " + CourseRules.SPAN + " sixteenths without "
                        + "jumping, so the stride ceiling would allow any "
                        + "staircase at all");
        System.out.println("  the ways up: " + ways + " of them, every one rising "
                + "in more than one go, none of those goes taller than "
                + CourseRules.LONGEST_STRIDE + " sixteenths, and every one reaching "
                + "the block above");
    }

    // ==================================================================

    private static void theDecks() {
        check(CourseRules.misdecked().isEmpty(),
                "a deck piece that is not what its role says: "
                        + CourseRules.misdecked());
        int floors = 0;
        int rails = 0;
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            if (!CourseRules.DECK.equals(kind.family())) {
                continue;
            }
            if (CourseRules.FLOOR.equals(kind.role())) {
                floors++;
                check(CourseRules.standable(kind.name()) * 2
                                >= CourseRules.SPAN * CourseRules.SPAN,
                        kind.name() + " is a floor you cannot stand on");
            } else {
                rails++;
                check(CourseRules.footprint(kind.name()) * 4
                                <= CourseRules.SPAN * CourseRules.SPAN,
                        kind.name() + " is a rail you cannot see through");
                check(kind.joins(), kind.name() + " is a rail that does not join");
            }
        }
        check(floors > 0 && rails > 0, "a deck with " + floors + " floor(s) and "
                + rails + " rail(s) is not a deck: a balcony without a rail is a "
                + "hole, and a rail without a floor is a fence");
        System.out.println("  the decks: " + floors + " floor(s) a player can "
                + "stand on and " + rails + " rail(s) they can see through");
    }

    // ==================================================================

    private static void theClimb() {
        check(CourseRules.unclimbable().isEmpty(),
                "something climbable that is not against a wall: "
                        + CourseRules.unclimbable());
        int climbs = 0;
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            if (!CourseRules.CLIMB.equals(kind.family())) {
                continue;
            }
            climbs++;
            for (CourseRules.Course course : CourseRules.catalogue()) {
                if (!course.kind().equals(kind.name())) {
                    continue;
                }
                check(CourseRules.tagsFor(course).contains("minecraft:climbable"),
                        course.id() + " is climbable and is not in the game's own "
                                + "climbable tag, so a player slides down it");
            }
        }
        check(climbs > 0, "nothing here can be climbed, and the game has exactly "
                + "one ladder and it is made of oak");
        System.out.println("  the climb: " + climbs + " climbable kind(s), every "
                + "one of them flat against the face of its block and in the "
                + "game's own climbable tag");
    }

    // ==================================================================

    private static void theCutting() {
        check(CourseRules.miscut().isEmpty(),
                "a stonecutter recipe that does not take one block: "
                        + CourseRules.miscut());
        int cut = 0;
        int crafted = 0;
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            if (CourseRules.stonecut(kind.name())) {
                cut++;
                check(kind.of() == 1, kind.name() + " is stonecut out of "
                        + kind.of() + " blocks");
                check(CourseRules.CUT.equals(kind.family())
                                || CourseRules.CLIMB.equals(kind.family()),
                        kind.name() + " is stonecut and is a " + kind.family()
                                + ", and a stonecutter makes cuts of a block");
            } else {
                crafted++;
                check(kind.of() > 1, kind.name() + " is crafted out of one block, "
                        + "which is a stonecutter's job and not a grid's");
            }
        }
        check(cut + crafted == CourseRules.KINDS.size(),
                "some course is neither cut nor crafted");
        check(cut > 0 && crafted > 0, cut + " cut and " + crafted + " crafted");
        System.out.println("  the cutting: " + cut + " cut on a stonecutter one "
                + "block at a time, " + crafted + " crafted on a grid, and nothing "
                + "in both");
    }

    // ==================================================================

    private static void thePrices() {
        check(CourseRules.profitable().isEmpty(),
                "a recipe that makes more than it takes: " + CourseRules.profitable());
        check(CourseRules.recoverable().isEmpty(),
                "a recipe you could craft and break for a profit: "
                        + CourseRules.recoverable());
        check(CourseRules.wasteful().isEmpty(),
                "a recipe nobody would ever use: " + CourseRules.wasteful());

        for (CourseRules.Kind kind : CourseRules.KINDS) {
            int volume = CourseRules.volume(kind.name());
            int units = kind.units();
            check(units * CourseRules.EIGHTH >= volume, kind.name() + " is priced "
                    + "at " + units + " eighths and fills " + volume
                    + " sixteenths, which is more than it costs");
            check((units - 1) * CourseRules.EIGHTH < volume, kind.name()
                    + " is priced at " + units + " eighths and would fit in "
                    + (units - 1) + ", so it is dearer than its own shape");
            check(kind.yields() <= kind.spends(), kind.name() + " yields "
                    + kind.yields() + " out of " + kind.spends());
            check(kind.spends() - kind.yields() <= CourseRules.MOST_WASTED,
                    kind.name() + " wastes " + (kind.spends() - kind.yields()));
        }

        // Every pair, both ways round: craft one, break it, craft the other.
        // A single recipe can be honest and a pair of them still pay.
        for (CourseRules.Kind one : CourseRules.KINDS) {
            for (CourseRules.Kind other : CourseRules.KINDS) {
                int in = one.of() * CourseRules.WHOLE;
                int out = one.made() * one.recovers() * CourseRules.WHOLE;
                check(out <= in, one.name() + " into " + other.name()
                        + ": " + in + " units in and " + out + " back out");
            }
        }

        // And a long random walk of craft-and-break, which is the only way to
        // catch a loop that needs four steps to come out ahead.
        Random random = new Random(1_414_213L);
        int walks = 0;
        for (int walk = 0; walk < 200_000; walk++) {
            int blocks = 1 + random.nextInt(64);
            int started = blocks;
            for (int step = 0; step < 8; step++) {
                CourseRules.Kind kind = CourseRules.KINDS.get(
                        random.nextInt(CourseRules.KINDS.size()));
                int crafts = blocks / kind.of();
                if (crafts == 0) {
                    break;
                }
                blocks -= crafts * kind.of();
                blocks += crafts * kind.made() * kind.recovers();
            }
            walks++;
            check(blocks <= started, "a chain of crafting and breaking started "
                    + "with " + started + " blocks and came back with " + blocks);
        }
        System.out.println("  the prices: every course priced at its own volume "
                + "rounded up, no pair of them paying, and " + walks + " random "
                + "chains of crafting and breaking not one of which came back "
                + "with more than it started with");
    }

    // ==================================================================

    private static void theJoining() {
        check(CourseRules.lopsided().isEmpty(),
                "two courses disagree about whether they meet: "
                        + CourseRules.lopsided());
        check(CourseRules.crossJoined().isEmpty(),
                "a course joins a different kind of thing: "
                        + CourseRules.crossJoined());

        List<CourseRules.Course> all = CourseRules.catalogue();
        int pairs = 0;
        int joining = 0;
        for (CourseRules.Course one : all) {
            for (CourseRules.Course other : all) {
                pairs++;
                boolean here = CourseRules.reaches(one, other);
                check(here == CourseRules.reaches(other, one),
                        one.id() + " and " + other.id() + " disagree");
                if (here) {
                    joining++;
                    check(one.kind().equals(other.kind()),
                            one.id() + " joins " + other.id());
                }
            }
        }
        check(joining > 0, "nothing in the catalogue joins anything, so the "
                + "symmetry above is about a relation that is never true");
        check(joining < pairs, "everything joins everything");
        System.out.println("  the joining: " + pairs + " ordered pair(s), "
                + joining + " of which meet, none of them one way only and none "
                + "of them across kinds");
    }

    // ==================================================================

    private static void theTags() {
        check(CourseRules.disagreeing().isEmpty(),
                "a course in a tag it has no business in: "
                        + CourseRules.disagreeing());

        Map<String, Integer> counted = new LinkedHashMap<>();
        for (CourseRules.Course course : CourseRules.catalogue()) {
            Set<String> tags = CourseRules.tagsFor(course);
            check(!tags.isEmpty(), course.id() + " is in no tag at all, so no "
                    + "tool mines it faster than a fist");
            check(tags.contains(course.material().soft()
                            ? "minecraft:mineable/axe" : "minecraft:mineable/pickaxe"),
                    course.id() + " is worked by the wrong tool");
            for (String tag : tags) {
                counted.merge(tag, 1, Integer::sum);
            }
        }
        for (CourseRules.Course course : CourseRules.catalogue()) {
            StringBuilder line = new StringBuilder("CTAG " + course.id());
            for (String tag : new java.util.TreeSet<>(
                    CourseRules.tagsFor(course))) {
                line.append(' ').append(tag);
            }
            System.out.println(line);
        }
        Map<String, List<String>> files = CourseRules.tagFiles();
        check(files.keySet().equals(counted.keySet()),
                "the tag files and the tags the courses claim are different sets");
        for (Map.Entry<String, List<String>> entry : files.entrySet()) {
            check(entry.getValue().size() == counted.get(entry.getKey()),
                    entry.getKey() + " holds " + entry.getValue().size()
                            + " entries and " + counted.get(entry.getKey())
                            + " courses claim it");
            Set<String> once = new LinkedHashSet<>(entry.getValue());
            check(once.size() == entry.getValue().size(),
                    entry.getKey() + " names the same block twice");
        }
        System.out.println("  the tags: " + files.size() + " tag file(s) derived "
                + "from what each course actually is, every entry in them once");
    }

    // ==================================================================

    private static void theGeometry() {
        for (CourseRules.Kind kind : CourseRules.KINDS) {
            List<ArchRules.Box> boxes = CourseRules.boxesFor(kind.name());
            StringBuilder line = new StringBuilder("COURSE " + kind.name());
            for (ArchRules.Box box : boxes) {
                line.append(' ').append(box.x1()).append(',').append(box.y1())
                        .append(',').append(box.z1()).append(',').append(box.x2())
                        .append(',').append(box.y2()).append(',').append(box.z2());
            }
            System.out.println(line);
        }
        System.out.println("  the geometry: " + CourseRules.KINDS.size()
                + " course(s) printed, for the generator's table to be checked "
                + "against");
    }

    // ==================================================================

    /**
     * The checks, checked.
     *
     * <p>Every predicate above answers "no" on a healthy roster, which is what
     * a predicate that has stopped working also does. So each of them is handed
     * something built to be caught.
     */
    private static void theControls() {
        check(!CourseRules.faultsInTreads("control", List.of(16)).isEmpty(),
                "a way up that stands at one height is not reported, so the "
                        + "walkability check cannot say yes");
        check(!CourseRules.faultsInTreads("control", List.of(4, 16)).isEmpty(),
                "a way up that jumps from four sixteenths to sixteen is not "
                        + "reported, so the stride check cannot say yes");
        check(!CourseRules.faultsInTreads("control", List.of(4, 8)).isEmpty(),
                "a way up that stops at half a block is not reported, so the "
                        + "check that it reaches the block above cannot say yes");
        check(CourseRules.faultsInTreads("control", List.of(8, 16)).isEmpty(),
                "a perfectly ordinary stair is reported as unwalkable, so the "
                        + "check says yes to everything");

        check(!CourseRules.faultsInDeck("control", CourseRules.FLOOR, false, 8, 256)
                        .isEmpty(),
                "a floor a player can stand on eight squares of is not reported");
        check(CourseRules.faultsInDeck("control", CourseRules.FLOOR, false, 200, 256)
                        .isEmpty(),
                "an ordinary floor is reported as unstandable");
        check(!CourseRules.faultsInDeck("control", CourseRules.RAIL, true, 0, 200)
                        .isEmpty(),
                "a rail filling most of its block is not reported as a wall");
        check(!CourseRules.faultsInDeck("control", CourseRules.RAIL, false, 0, 40)
                        .isEmpty(),
                "a rail that does not join is not reported");
        check(CourseRules.faultsInDeck("control", CourseRules.RAIL, true, 0, 40)
                        .isEmpty(),
                "an ordinary rail is reported as a wall");

        check(!CourseRules.faultsInClimb("control",
                        List.of(new ArchRules.Box(0, 0, 0, 16, 16, 4))).isEmpty(),
                "something climbable drawn in the middle of its block is not "
                        + "reported, so the climbable check cannot say yes");
        check(CourseRules.faultsInClimb("control",
                        List.of(new ArchRules.Box(0, 0, 14, 16, 16, 16))).isEmpty(),
                "an ordinary ladder is reported as being off the wall");

        // The economy, built out of numbers rather than kinds.
        check(CourseRules.pays(8, 4, 2), "four things worth a block each out of "
                + "two blocks is not reported as paying, so the profitability "
                + "check cannot say yes");
        check(!CourseRules.pays(4, 6, 3), "six half-blocks out of three blocks is "
                + "reported as paying, so the check says yes to everything");
        check(CourseRules.recovered(CourseRules.WHOLE) == 1,
                "something worth a whole block gives nothing back");
        check(CourseRules.recovered(CourseRules.WHOLE - 1) == 0,
                "something worth less than a block gives a block back, which is "
                        + "the loop this whole file exists to stop");

        // A recipe built to be far past the waste ceiling. Without this,
        // raising the ceiling to something absurd reports nothing -- because
        // the roster is healthy -- and the check goes on passing while
        // allowing anything.
        check(CourseRules.spent(4) - CourseRules.yielded(1, 1)
                        > CourseRules.MOST_WASTED,
                "a recipe eating four blocks and giving back one eighth is "
                        + "inside the waste ceiling, so the ceiling would allow "
                        + "anything");

        Random random = new Random(2_236_067L);
        int reported = 0;
        int rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            int units = 1 + random.nextInt(CourseRules.WHOLE * 2);
            int made = 1 + random.nextInt(8);
            int of = 1 + random.nextInt(4);
            rounds++;
            check(CourseRules.pays(units, made, of)
                            == (made * units > of * CourseRules.WHOLE),
                    "profitability disagreed with the arithmetic at " + units + "/"
                            + made + "/" + of);
            if (CourseRules.pays(units, made, of)) {
                reported++;
            }
        }
        check(reported > rounds / 10, "only " + reported + " of " + rounds
                + " built recipes were reported as paying");
        check(reported < rounds, "every built recipe was reported as paying");

        // And the volume counter, on a shape whose answer is known by hand.
        boolean[] grid = CourseRules.filled(CourseRules.SLAB);
        check(CourseRules.volume(CourseRules.SLAB) == 16 * 8 * 16,
                "a slab does not fill half its block, so the volume counter is "
                        + "not counting");
        check(CourseRules.heightAt(grid, 0, 0) == 8,
                "a slab does not stand half a block tall");
        check(CourseRules.units(CourseRules.SLAB) == CourseRules.WHOLE / 2,
                "a slab is not priced at half a block");

        System.out.println("  the controls: " + reported + " of " + rounds
                + " built recipes reported as paying, an unwalkable stair caught, "
                + "a floor nobody can stand on caught, and a ladder off the wall "
                + "caught");
    }
}
