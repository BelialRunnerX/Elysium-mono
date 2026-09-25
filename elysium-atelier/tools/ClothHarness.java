package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The colour axis, executed.
 *
 * <p>The jar this replaces ships seven hundred and forty-eight blocks that are
 * eleven woods times sixteen colours times four seats. What is proved here is
 * that our five hundred and ninety-two are the same crossing with no holes in
 * it, that every one of them is partly cloth and partly not, that each costs
 * exactly the seat and the wool it was made of, and that none of the five
 * hundred and ninety-two ids is an id this module already uses — which, with
 * sixteen colours and thirty-three materials sharing one namespace, is not
 * obvious and is silent when it goes wrong.
 */
public final class ClothHarness {

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
        theColours();
        theCrossing();
        thePadding();
        thePrices();
        theNames();
        theGeometry();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: sixteen colours crossed with every "
                + "wood and every seat with no holes in it, every upholstered seat "
                + "partly cloth and partly not, every one costing exactly the seat "
                + "and the wool it was made of, and no id here an id this module "
                + "already uses");
    }

    // ==================================================================

    private static void theColours() {
        check(ClothRules.miscoloured().isEmpty(),
                "something wrong with the colours: " + ClothRules.miscoloured());
        check(ClothRules.COLOURS.size() == ClothRules.MANY,
                "there are " + ClothRules.COLOURS.size() + " colours and the game "
                        + "has " + ClothRules.MANY);
        for (String colour : ClothRules.COLOURS) {
            check(ClothRules.woolOf(colour).equals("minecraft:" + colour + "_wool"),
                    colour + " is cut from " + ClothRules.woolOf(colour));
            check(ClothRules.faceOf(colour).startsWith("minecraft:block/"),
                    colour + " wears a texture that is not one of the game's");
            check(ClothRules.faceOf(colour).endsWith("_wool"),
                    colour + " wears something that is not wool");
        }
        System.out.println("  the colours: " + ClothRules.COLOURS.size()
                + " dyes, every one of them the game's own wool and none of them a "
                + "material this module already has");
    }

    // ==================================================================

    private static void theCrossing() {
        int woods = ClothRules.woods().size();
        int seats = ClothRules.SEATS.size();
        check(woods > 0, "nothing here is framed in anything, because no material "
                + "is worked by hand");
        check(woods < AtelierRules.MATERIALS.size(),
                "every material is a wood, so the frame of a seat is not a wood at "
                        + "all, it is anything");
        check(seats > 0, "there are no seats to upholster");

        List<ClothRules.Upholstered> made = ClothRules.seats();
        check(made.size() == ClothRules.COLOURS.size() * woods * seats,
                "the crossing holds " + made.size() + " seats and sixteen colours "
                        + "times " + woods + " woods times " + seats + " seats is "
                        + ClothRules.COLOURS.size() * woods * seats);

        // And the crossing walked from the other end: every combination has to
        // be in it exactly once. A hole is one seat a player cannot make and
        // nothing else looks any different at all.
        Set<String> present = new LinkedHashSet<>();
        for (ClothRules.Upholstered one : made) {
            check(present.add(one.id()), one.id() + " is in the crossing twice");
        }
        for (String colour : ClothRules.COLOURS) {
            for (AtelierRules.Material wood : ClothRules.woods()) {
                for (ClothRules.Seat seat : ClothRules.SEATS) {
                    check(present.contains(colour + "_" + wood.name() + "_"
                                    + seat.shape()),
                            colour + " " + wood.name() + " " + seat.shape()
                                    + " is a hole in the crossing");
                }
            }
        }

        check(ClothRules.cloths().size()
                        == ClothRules.COLOURS.size() * ClothRules.CLOTHS.size(),
                "the cloth on its own does not cross every colour with every kind");
        check(ClothRules.howMany() == made.size() + ClothRules.cloths().size(),
                "the catalogue is not the two crossings put together");
        System.out.println("  the crossing: " + ClothRules.COLOURS.size()
                + " colour(s) x " + woods + " wood(s) x " + seats + " seat(s) = "
                + made.size() + " upholstered seat(s), and " + ClothRules.cloths()
                .size() + " thing(s) made of cloth alone");
    }

    // ==================================================================

    private static void thePadding() {
        for (ClothRules.Seat seat : ClothRules.SEATS) {
            check(!seat.padded().isEmpty(), seat.shape() + " is upholstered nowhere");
            check(seat.padded().size() < seat.boxes(), seat.shape()
                    + " is upholstered everywhere, which is a block of wool shaped "
                    + "like a chair");
            Set<Integer> once = new LinkedHashSet<>(seat.padded());
            check(once.size() == seat.padded().size(),
                    seat.shape() + " pads one of its boxes twice");
            for (int one : seat.padded()) {
                check(one >= 0 && one < seat.boxes(), seat.shape()
                        + " pads box " + one + " of " + seat.boxes());
            }
            check(AtelierRules.shape(seat.shape()) != null,
                    seat.shape() + " is upholstered and is not a shape this module "
                            + "makes");
            check(ClothRules.seat(seat.shape()) == seat,
                    seat.shape() + " cannot be looked up by its own name");
            check(ClothRules.misdrawn(seat.shape(), seat.boxes()) == null,
                    seat.shape() + " disagrees with itself about how it is drawn");
        }
        System.out.println("  the padding: " + ClothRules.SEATS.size()
                + " seat(s), each with at least one box of cloth and at least one "
                + "of wood");
    }

    // ==================================================================

    private static void thePrices() {
        check(ClothRules.mispriced().isEmpty(),
                "an upholstered seat that does not cost its own parts: "
                        + ClothRules.mispriced());
        check(ClothRules.profitable().isEmpty(),
                "a piece of cloth whose recipe pays: " + ClothRules.profitable());

        for (ClothRules.Upholstered one : ClothRules.seats()) {
            check(one.units() == one.spends(), one.id() + " costs " + one.units()
                    + " eighths and is made out of " + one.spends());
            check(one.units() == AtelierRules.shape(one.shape()).units()
                            + ClothRules.WHOLE,
                    one.id() + " is not its seat plus a block of wool");
        }
        for (ClothRules.Cloth cloth : ClothRules.CLOTHS) {
            int volume = ClothRules.volume(cloth.name());
            int units = cloth.units();
            check(units * ClothRules.EIGHTH >= volume, cloth.name() + " is priced "
                    + "at " + units + " and fills " + volume);
            check((units - 1) * ClothRules.EIGHTH < volume, cloth.name()
                    + " is dearer than its own shape");
            check(cloth.yields() <= cloth.spends(), cloth.name() + " yields "
                    + cloth.yields() + " out of " + cloth.spends());
        }

        // Upholster a seat, break it, upholster another: nothing comes back
        // with more wool than it started with.
        Random random = new Random(1_618_033L);
        int walks = 0;
        for (int walk = 0; walk < 200_000; walk++) {
            int wool = 1 + random.nextInt(32);
            int started = wool;
            for (int step = 0; step < 8; step++) {
                ClothRules.Seat seat = ClothRules.SEATS.get(
                        random.nextInt(ClothRules.SEATS.size()));
                if (wool < 1) {
                    break;
                }
                wool -= 1;
                wool += ClothRules.unitsOf(seat.shape()) / ClothRules.WHOLE
                        - AtelierRules.shape(seat.shape()).units()
                        / ClothRules.WHOLE;
            }
            walks++;
            check(wool <= started, "a chain of upholstering and breaking started "
                    + "with " + started + " wool and came back with " + wool);
        }
        System.out.println("  the prices: every upholstered seat costing exactly "
                + "its seat and its block of wool, every piece of cloth priced at "
                + "its own volume, and " + walks + " chains of upholstering and "
                + "breaking not one of which came back with more wool");
    }

    // ==================================================================

    private static void theNames() {
        check(ClothRules.renamed().isEmpty(),
                "a piece of cloth named after something else here: "
                        + ClothRules.renamed());

        // Every id this module already makes, from every roster, so that a
        // colour crossed with a wood cannot land on one of them.
        Set<String> already = everythingElse();
        check(already.size() > 1000, "only " + already.size() + " existing ids were "
                + "gathered, and a collision check against a fraction of the "
                + "catalogue passes whatever collides with the rest of it");
        check(ClothRules.collides(already).isEmpty(),
                "an id here is already something else: "
                        + ClothRules.collides(already));
        for (String id : ClothRules.catalogue()) {
            check(!already.contains(id), id + " is already a block");
        }
        System.out.println("  the names: " + ClothRules.howMany() + " id(s) against "
                + already.size() + " this module already makes, and not one of them "
                + "the same");
    }

    private static Set<String> everythingElse() {
        Set<String> already = new LinkedHashSet<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
                already.add(material.name() + "_" + shape.name());
            }
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                already.add(material.name() + "_" + piece.name());
            }
        }
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            already.add(opening.material().name() + "_" + opening.kind());
        }
        for (CourseRules.Course course : CourseRules.catalogue()) {
            already.add(course.id());
        }
        for (TrimRules.Fitting fitting : TrimRules.catalogue()) {
            already.add(fitting.id());
        }
        return already;
    }

    // ==================================================================

    private static void theGeometry() {
        for (ClothRules.Seat seat : ClothRules.SEATS) {
            StringBuilder line = new StringBuilder("PAD " + seat.shape() + " "
                    + seat.boxes());
            for (int one : seat.padded()) {
                line.append(' ').append(one);
            }
            System.out.println(line);
        }
        for (ClothRules.Cloth cloth : ClothRules.CLOTHS) {
            StringBuilder line = new StringBuilder("CLOTH " + cloth.name());
            for (ArchRules.Box box : ClothRules.boxesFor(cloth.name())) {
                line.append(' ').append(box.x1()).append(',').append(box.y1())
                        .append(',').append(box.z1()).append(',').append(box.x2())
                        .append(',').append(box.y2()).append(',').append(box.z2());
            }
            System.out.println(line);
        }
        System.out.println("  the geometry: " + ClothRules.SEATS.size()
                + " padding(s) and " + ClothRules.CLOTHS.size() + " shape(s) "
                + "printed, for the generator's tables to be checked against");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        check(ClothRules.misdrawn("chair", 99) != null,
                "a chair drawn as ninety-nine boxes is not reported, so the check "
                        + "that the padding is on the boxes that exist cannot say "
                        + "yes");
        check(ClothRules.misdrawn("chair", ClothRules.seat("chair").boxes()) == null,
                "a chair drawn as the boxes it has is reported as wrong, so that "
                        + "check says yes to everything");
        check(ClothRules.misdrawn("cabinet", 1) != null,
                "something that is not a seat at all is not reported as one");

        Set<String> pretend = new LinkedHashSet<>();
        pretend.add(ClothRules.catalogue().get(0));
        check(!ClothRules.collides(pretend).isEmpty(),
                "an id handed to the collision check as already taken is not "
                        + "reported, so the check cannot say yes");
        check(ClothRules.collides(new LinkedHashSet<>()).isEmpty(),
                "every id collides with nothing at all, so the check says yes to "
                        + "everything");

        List<String> boom = new ArrayList<>();
        try {
            new ClothRules.Seat("control", 3, List.of(0, 1, 2));
            boom.add("a seat upholstered on every box was allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ClothRules.Seat("control", 3, List.of());
            boom.add("a seat upholstered on no box was allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ClothRules.Seat("control", 3, List.of(7));
            boom.add("a seat upholstered on a box it does not have was allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(boom.isEmpty(), "the seat record allows what it says it forbids: "
                + boom);
        check(new ClothRules.Seat("control", 3, List.of(1)).padded().size() == 1,
                "an ordinary seat is refused, so the record forbids everything");

        check(ClothRules.unitsOf("chair")
                        > AtelierRules.shape("chair").units(),
                "an upholstered chair costs no more than a bare one, so the wool "
                        + "in it is free");
        check(ClothRules.recovered(ClothRules.WHOLE * 3) == 3,
                "something worth three blocks gives back "
                        + ClothRules.recovered(ClothRules.WHOLE * 3)
                        + ", so the check that upholstering does not make blocks "
                        + "is counting something else");
        check(ClothRules.recovered(ClothRules.WHOLE - 1) == 0,
                "something worth less than a block gives a block back");
        check(CourseRules.pays(8, 4, 2), "the profitability arithmetic cannot say "
                + "yes");
        check(!CourseRules.pays(1, 12, 2), "the profitability arithmetic says yes "
                + "to everything");

        System.out.println("  the controls: a chair drawn as the wrong number of "
                + "boxes caught, a seat upholstered on every box refused, and an "
                + "id already taken caught");
    }
}
