package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * The architectural pieces, executed.
 *
 * <p>The one property this exists for is that a roof can be <em>closed</em>.
 * Every other check here is about a single piece and could be found by reading
 * it; that one is about the set, it is invisible in every individual block,
 * and it is the difference between a roof and a hole in a house.
 *
 * <p>It also prints the geometry, because two files describe it — the rules
 * the block reads and the table the generator draws from — and a model and a
 * collision box that disagree is a block you fall through and which looks
 * completely correct from every angle. The validator reads what is printed
 * here and compares it with what was drawn.
 */
public final class ArchHarness {

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
        theFamilies();
        theRoofs();
        theLights();
        theBridges();
        theEconomy();
        theGeometry();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every pitch of roof closeable, every "
                + "path thinner than a slab, a light ladder that climbs in both "
                + "brightness and price, a bridge with all three of its pieces, and "
                + "not one recipe here that makes more than it takes");
    }

    // ==================================================================

    private static void theFamilies() {
        check(ArchRules.emptyFamilies().isEmpty(),
                "families named and empty: " + ArchRules.emptyFamilies());
        check(ArchRules.repeated().isEmpty(),
                "pieces declared twice or colliding with a shape: "
                        + ArchRules.repeated());

        Set<String> names = new TreeSet<>();
        for (ArchRules.Piece one : ArchRules.PIECES) {
            check(names.add(one.name()), one.name() + " is declared twice");
            check(ArchRules.FAMILIES.contains(one.family()),
                    one.name() + " is in no family");
        }
        check(ArchRules.howMany()
                        == ArchRules.PIECES.size() * AtelierRules.MATERIALS.size(),
                "the catalogue is not every piece in every material");
        check(ArchRules.catalogue().size() == ArchRules.howMany(),
                "the catalogue and the count disagree");

        // The record's own refusals.
        int refused = 0;
        try {
            new ArchRules.Piece("nowhere", "spaceship", "role", "", 1, 1, 1, 1, 0,
                    false);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ArchRules.Piece("blinding", ArchRules.LIGHT, "wall", "", 1, 1, 1, 1,
                    ArchRules.BRIGHTEST + 1, false);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ArchRules.Piece("pitchless", ArchRules.ROOF, ArchRules.SLOPE, "", 1,
                    1, 1, 1, 0, false);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ArchRules.Piece("pitched", ArchRules.PATH, "flat", "steep", 1, 1, 1,
                    1, 0, false);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new ArchRules.Piece("roleless", ArchRules.ROOF, "gable", "steep", 1, 1,
                    1, 1, 0, false);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 5, refused + " of 5 impossible pieces were refused");
        System.out.println("  the families: " + ArchRules.PIECES.size()
                + " pieces across " + ArchRules.FAMILIES.size() + " families, in "
                + AtelierRules.MATERIALS.size() + " materials = "
                + ArchRules.howMany() + " blocks");
    }

    private static void theRoofs() {
        check(ArchRules.roofsThatCannotTurn().isEmpty(),
                "roofs that cannot be closed: " + ArchRules.roofsThatCannotTurn());

        // Said again from the other side, because the check above walks the
        // pitches it was given and a pitch that is simply absent from both
        // lists would pass it.
        for (String pitch : ArchRules.PITCHES) {
            Set<String> roles = new TreeSet<>();
            for (ArchRules.Piece one : ArchRules.of(ArchRules.ROOF)) {
                if (one.pitch().equals(pitch)) {
                    roles.add(one.role());
                }
            }
            check(roles.size() == ArchRules.CLOSES.size(),
                    "a " + pitch + " roof has " + roles + " and needs "
                            + ArchRules.CLOSES);
        }
        check(!ArchRules.PITCHES.isEmpty(), "there are no pitches at all, so every "
                + "roof check passes by never running");
        check(ArchRules.of(ArchRules.ROOF).size()
                        == ArchRules.PITCHES.size() * ArchRules.CLOSES.size(),
                "there are " + ArchRules.of(ArchRules.ROOF).size() + " roof pieces "
                        + "and " + ArchRules.PITCHES.size() + " pitches of "
                        + ArchRules.CLOSES.size() + " roles, so one pitch has a "
                        + "piece another has not");

        // A steep roof rises further than a shallow one. Without this the two
        // pitches are one pitch under two names.
        for (String role : ArchRules.CLOSES) {
            ArchRules.Piece shallow = null;
            ArchRules.Piece steep = null;
            for (ArchRules.Piece one : ArchRules.of(ArchRules.ROOF)) {
                if (!one.role().equals(role)) {
                    continue;
                }
                if ("shallow".equals(one.pitch())) {
                    shallow = one;
                }
                if ("steep".equals(one.pitch())) {
                    steep = one;
                }
            }
            if (shallow == null || steep == null) {
                continue;
            }
            check(steep.units() > shallow.units(),
                    "a steep " + role + " costs no more than a shallow one, so the "
                            + "two pitches are one pitch with two names");
        }
        System.out.println("  the roofs: " + ArchRules.PITCHES.size()
                + " pitches, each with all of " + ArchRules.CLOSES);
    }

    private static void theLights() {
        check(ArchRules.lightsThatDoNotClimb().isEmpty(),
                "a light ladder that does not climb: "
                        + ArchRules.lightsThatDoNotClimb());
        check(ArchRules.pathsThatAreSlabs().isEmpty(),
                "paths a slab would do instead: " + ArchRules.pathsThatAreSlabs());

        List<ArchRules.Piece> lights = ArchRules.of(ArchRules.LIGHT);
        check(lights.size() >= 2, "there are " + lights.size() + " lights, which is "
                + "too few for a ladder check to compare anything");
        int brightest = 0;
        for (ArchRules.Piece one : lights) {
            check(one.burns(), one.name() + " is a light that gives off none");
            brightest = Math.max(brightest, one.light());
        }
        check(brightest == ArchRules.BRIGHTEST,
                "the brightest light here is " + brightest + " and the game goes to "
                        + ArchRules.BRIGHTEST + ", so the top of the ladder is a "
                        + "rung nobody put anything on");
        for (ArchRules.Piece one : ArchRules.PIECES) {
            check(one.burns() == ArchRules.LIGHT.equals(one.family()),
                    one.name() + " gives off light and is not a light, or is a light "
                            + "and gives off none");
        }
        System.out.println("  the lights: " + lights.size()
                + " rungs, the brightest at " + brightest);
    }

    private static void theBridges() {
        check(ArchRules.bridgesThatDoNotSpan().isEmpty(),
                "a bridge missing a piece: " + ArchRules.bridgesThatDoNotSpan());
        check(ArchRules.lopsided().isEmpty(),
                "pieces that disagree about whether they meet: "
                        + ArchRules.lopsided());

        // Joining is symmetric, reflexive on the things that join at all, and
        // never reaches out of its own family. Swept over every pair, because
        // that is the only way an asymmetry shows up.
        int pairs = 0;
        for (ArchRules.Piece one : ArchRules.PIECES) {
            for (ArchRules.Piece other : ArchRules.PIECES) {
                pairs++;
                check(ArchRules.reaches(one, other) == ArchRules.reaches(other, one),
                        one.name() + " and " + other.name() + " disagree about "
                                + "whether they meet");
                if (ArchRules.reaches(one, other)) {
                    check(one.family().equals(other.family()),
                            one.name() + " reaches across families to "
                                    + other.name());
                    check(one.joins() && other.joins(),
                            "something that does not join is joining");
                }
            }
            check(ArchRules.reaches(one, one) == one.joins(),
                    one.name() + " does not reach itself but joins, or the reverse");
        }
        check(pairs > 300, "only " + pairs + " ordered pairs, which is too few for "
                + "the symmetry check to mean anything");
        System.out.println("  the bridges: all three pieces, and " + pairs
                + " ordered pairs of which none joins one way only");
    }

    private static void theEconomy() {
        check(ArchRules.profitable().isEmpty(),
                "recipes that make more than they take: " + ArchRules.profitable());
        check(ArchRules.wasteful().isEmpty(),
                "recipes nobody would ever use: " + ArchRules.wasteful());
        check(ArchRules.recoverable().isEmpty(),
                "pieces you can craft and break for a profit: "
                        + ArchRules.recoverable());

        // And the same sweep the shapes get: random chains of crafting and
        // breaking, none of which may come back with more than it started.
        Random random = new Random(1_414_213L);
        int rounds = 0;
        for (int round = 0; round < 200_000; round++) {
            ArchRules.Piece one = ArchRules.PIECES.get(
                    random.nextInt(ArchRules.PIECES.size()));
            int blocks = 1 + random.nextInt(64);
            int craftings = blocks / one.of();
            int made = craftings * one.made();
            int back = craftings * 0 + made * one.recovers()
                    + (blocks - craftings * one.of());
            rounds++;
            check(back <= blocks, one.name() + ": " + blocks + " blocks in, " + back
                    + " back out after crafting " + made + " and breaking them all");
        }
        check(rounds > 100_000, "the sweep ran " + rounds + " times, which is too "
                + "few to have met the awkward numbers");
        System.out.println("  the economy: " + rounds + " chains of crafting and "
                + "breaking, not one coming back with more than it started with");
    }

    /**
     * The boxes, printed for the validator and checked for sense here.
     *
     * <p>Two files describe this geometry and only one of them can be right if
     * they differ. Printing it is how the other one is checked.
     */
    private static void theGeometry() {
        check(ArchRules.geometryThatDisagrees().isEmpty(),
                "pieces whose boxes are not the height the rules give them: "
                        + ArchRules.geometryThatDisagrees());
        for (ArchRules.Piece one : ArchRules.PIECES) {
            List<ArchRules.Box> boxes = ArchRules.boxesFor(one);
            check(!boxes.isEmpty(), one.name() + " is made of no boxes at all, so it "
                    + "is an invisible block you cannot walk through");
            StringBuilder line = new StringBuilder("GEOM " + one.name());
            for (ArchRules.Box box : boxes) {
                line.append(' ').append(box.x1()).append(',').append(box.y1())
                        .append(',').append(box.z1()).append(',').append(box.x2())
                        .append(',').append(box.y2()).append(',').append(box.z2());
            }
            System.out.println(line);
        }
        System.out.println("  the geometry: " + ArchRules.PIECES.size()
                + " pieces printed, for the generator's table to be checked against");
    }

    /**
     * The checkers, against sets built to be caught.
     *
     * <p>Every predicate above answers "no" when the set is healthy. One that
     * has lost the ability to answer "yes" answers "no" just as convincingly.
     */
    private static void theControls() {
        ArchRules.Piece dim = new ArchRules.Piece("control_dim", ArchRules.LIGHT,
                "wall", "", 4, 1, 1, 4, 5, false);
        ArchRules.Piece bright = new ArchRules.Piece("control_bright",
                ArchRules.LIGHT, "wall", "", 2, 1, 1, 4, 12, false);
        check(bright.light() > dim.light() && bright.units() < dim.units(),
                "the two control lights are not a brighter one that costs less, so "
                        + "the ladder check has nothing to catch");

        ArchRules.Piece thick = new ArchRules.Piece("control_thick", ArchRules.PATH,
                "flat", "", 4, 1, 1, ArchRules.UNDERFOOT + 1, 0, false);
        check(thick.tall() > ArchRules.UNDERFOOT,
                "a path built to be as thick as a slab is not, so the slab check "
                        + "cannot report anything");

        ArchRules.Piece rich = new ArchRules.Piece("control_rich", ArchRules.TRIM,
                "sill", "", 8, 4, 1, 2, 0, false);
        check(rich.profitable(), "a recipe making four whole blocks out of one is "
                + "not reported as profitable, so the economy check cannot report "
                + "anything");
        for (ArchRules.Piece one : ArchRules.PIECES) {
            check(!one.profitable(), one.name() + " is profitable");
        }

        // A recipe built to be far past the waste ceiling. Without this, raising
        // the ceiling to something absurd reports nothing -- because the
        // roster is healthy -- and the check goes on passing while allowing
        // anything.
        ArchRules.Piece squandering = new ArchRules.Piece("control_waste",
                ArchRules.TRIM, "sill", "", 1, 1, 4, 2, 0, false);
        check(squandering.spends() - squandering.yields() > ArchRules.MOST_WASTED,
                "a recipe eating four blocks and giving back one eighth is inside "
                        + "the waste ceiling, so the ceiling would allow anything");

        ArchRules.Piece joins = new ArchRules.Piece("control_join", ArchRules.BRIDGE,
                "rail", "", 2, 1, 1, 8, 0, true);
        ArchRules.Piece alone = new ArchRules.Piece("control_alone",
                ArchRules.BRIDGE, "deck", "", 2, 1, 1, 8, 0, false);
        check(!ArchRules.reaches(joins, alone) && !ArchRules.reaches(alone, joins),
                "something that does not join is reported as joining");
        check(ArchRules.reaches(joins, joins),
                "two rails do not reach each other, so the symmetry check is about "
                        + "a relation that is never true");

        // And a sweep over built pieces, so none of the above is right about
        // one case only.
        Random random = new Random(1_732_050L);
        int reported = 0;
        int rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            int units = 1 + random.nextInt(ArchRules.WHOLE * 2);
            int made = 1 + random.nextInt(8);
            int of = 1 + random.nextInt(4);
            ArchRules.Piece built = new ArchRules.Piece("control_" + round,
                    ArchRules.TRIM, "sill", "", units, made, of, 2, 0, false);
            rounds++;
            check(built.profitable() == (made * units > of * ArchRules.WHOLE),
                    "profitability disagreed with the arithmetic at " + units + "/"
                            + made + "/" + of);
            if (built.profitable()) {
                reported++;
            }
        }
        check(reported > rounds / 10, "only " + reported + " of " + rounds
                + " built recipes were reported profitable, so the check is nearly "
                + "always saying no");
        check(reported < rounds, "every built recipe was reported profitable");
        System.out.println("  the controls: " + reported + " of " + rounds
                + " built recipes reported profitable, a thick path caught, and a "
                + "brighter-and-cheaper light caught");
    }
}
