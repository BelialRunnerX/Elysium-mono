package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Packs, executed.
 *
 * <p>Four jars' worth of one idea, and four things that have to be true of
 * it. Three of them are arithmetic and one is the one every mod of this kind
 * gets wrong: <b>pulling a space upgrade out of a full bag</b>. The honest
 * answers are to spill the items or to refuse the removal; the dishonest one,
 * which is what actually ships, is to shrink the bag and let whatever was in
 * the slots past the end stop existing. Nobody reports it, because the player
 * has no way of knowing what was in there.
 *
 * <p>So the sweep here is over bags, over what is fitted to them, over what is
 * inside, and over every part that could come out — and what it proves is that
 * after every one of those, what is inside still fits inside.
 */
public final class PackHarness {

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
        theRoster();
        theCapacity();
        theFitting();
        theRemoval();
        theVoiding();
        theSweep();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: capacity the sum of what is fitted "
                + "and never more, no part fitted twice, no bag inside a bag, and "
                + "every removal of every part from every bag at every fullness "
                + "leaving what is inside still fitting inside");
    }

    // ==================================================================

    private static void theRoster() {
        check(PackRules.repeated().isEmpty(),
                "a body or a part declared twice: " + PackRules.repeated());
        check(PackRules.unladdered().isEmpty(),
                "a rung nobody climbs, or a kind nothing is: "
                        + PackRules.unladdered());
        check(!PackRules.BODIES.isEmpty(), "there are no bodies at all");
        check(!PackRules.PARTS.isEmpty(), "there are no parts at all");
        int carried = 0;
        int placed = 0;
        for (PackRules.Body one : PackRules.BODIES) {
            check(PackRules.body(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            check(one.slots() > 0, one.name() + " holds nothing");
            if (one.carried()) {
                carried++;
            } else {
                placed++;
            }
        }
        check(carried > 0 && placed > 0, carried + " bag(s) and " + placed
                + " crate(s): the four jars this replaces are two mods with two "
                + "upgrade systems, and building one of the two is building half");
        int room = 0;
        for (PackRules.Part one : PackRules.PARTS) {
            check(PackRules.part(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            if (PackRules.SPACE.equals(one.kind())) {
                room++;
            }
            check(one.slots() > 0 == PackRules.SPACE.equals(one.kind()),
                    one.name() + " adds room and is not a space part, or is one "
                            + "and adds none");
        }
        check(room >= 2, "there are " + room + " sizes of pocket, and a ladder "
                + "with one rung is a switch");
        System.out.println("  the roster: " + PackRules.BODIES.size()
                + " bodies (" + carried + " carried, " + placed + " placed) and "
                + PackRules.PARTS.size() + " parts in " + PackRules.KINDS.size()
                + " kinds, no two of them sharing a name");
    }

    // ==================================================================

    private static void theCapacity() {
        for (PackRules.Body body : PackRules.BODIES) {
            check(PackRules.capacity(body, List.of()) == body.slots(),
                    body.name() + " with nothing fitted holds "
                            + PackRules.capacity(body, List.of()) + " and its own "
                            + "size is " + body.slots());
            for (PackRules.Part one : PackRules.PARTS) {
                int with = PackRules.capacity(body, List.of(one));
                check(with == body.slots() + one.slots(),
                        body.name() + " with " + one.name() + " holds " + with
                                + " and the sum is " + (body.slots() + one.slots()));
                check(with >= body.slots(), one.name() + " takes room away");
                for (PackRules.Part other : PackRules.PARTS) {
                    if (one == other) {
                        continue;
                    }
                    int both = PackRules.capacity(body, List.of(one, other));
                    check(both == body.slots() + one.slots() + other.slots(),
                            body.name() + " with " + one.name() + " and "
                                    + other.name() + " holds " + both
                                    + " and the sum is "
                                    + (body.slots() + one.slots() + other.slots()));
                    check(both >= with, "adding " + other.name() + " made "
                            + body.name() + " smaller");
                }
            }
        }
        System.out.println("  the capacity: every body with every part and every "
                + "pair of parts, and in all of them the room is the sum of the "
                + "parts and nothing besides");
    }

    // ==================================================================

    private static void theFitting() {
        for (PackRules.Body body : PackRules.BODIES) {
            check(PackRules.fits(body, List.of()),
                    body.name() + " refuses to be empty");
            for (PackRules.Part one : PackRules.PARTS) {
                check(PackRules.fits(body, List.of(one)) == (body.fittings() >= 1),
                        body.name() + " takes " + body.fittings()
                                + " part(s) and " + (PackRules.fits(body,
                                        List.of(one)) ? "took" : "refused") + " one");
                check(!PackRules.fits(body, List.of(one, one)),
                        body.name() + " took " + one.name()
                                + " twice, which either does nothing or does it "
                                + "twice and both are wrong");
            }
            // One more part than it takes, always refused.
            List<PackRules.Part> toomany = new ArrayList<>();
            for (PackRules.Part one : PackRules.PARTS) {
                toomany.add(one);
                if (toomany.size() > body.fittings()) {
                    break;
                }
            }
            check(!PackRules.fits(body, toomany), body.name() + " takes "
                    + body.fittings() + " part(s) and took " + toomany.size());
        }
        System.out.println("  the fitting: every body refusing one part more than "
                + "it takes, and every body refusing the same part twice");
    }

    // ==================================================================

    private static void theRemoval() {
        int refused = 0;
        int allowed = 0;
        for (PackRules.Body body : PackRules.BODIES) {
            for (PackRules.Part one : PackRules.PARTS) {
                if (!PackRules.fits(body, List.of(one))) {
                    continue;
                }
                List<PackRules.Part> fitted = List.of(one);
                int room = PackRules.capacity(body, fitted);
                for (int stored = 0; stored <= room; stored++) {
                    boolean may = PackRules.mayRemove(body, fitted, one, stored);
                    int[] after = PackRules.afterRemoving(body, fitted, one, stored);
                    check(after[0] == stored, "taking " + one.name() + " out of "
                            + body.name() + " changed what was inside from "
                            + stored + " to " + after[0]);
                    check(after[0] <= after[1], "taking " + one.name() + " out of "
                            + body.name() + " left " + after[0] + " inside a thing "
                            + "that holds " + after[1] + ", so the difference stops "
                            + "existing and nobody can tell what was in it");
                    if (may) {
                        allowed++;
                    } else {
                        refused++;
                        check(stored > body.slots(), "taking " + one.name()
                                + " out of " + body.name() + " with " + stored
                                + " inside was refused, and the body alone holds "
                                + body.slots());
                    }
                }
            }
        }
        check(refused > 0, "no removal was ever refused, so the check that stops a "
                + "full bag losing what is in it has never once said no");
        check(allowed > 0, "no removal was ever allowed, so nothing can be "
                + "unfitted at all");
        System.out.println("  the removal: " + (refused + allowed)
                + " removals at every fullness, " + refused + " of them refused "
                + "because what was inside would not have fitted after");
    }

    // ==================================================================

    private static void theVoiding() {
        for (PackRules.Body body : PackRules.BODIES) {
            PackRules.Part seal = PackRules.part("void_seal");
            check(seal != null, "there is no void part, so the one exception to "
                    + "nothing being destroyed is an exception to nothing");
            List<PackRules.Part> without = List.of();
            List<PackRules.Part> with = List.of(seal);
            if (!PackRules.fits(body, with)) {
                continue;
            }
            int room = PackRules.capacity(body, without);
            for (int stored = 0; stored <= room; stored += 3) {
                for (int coming = 0; coming <= room + 8; coming += 5) {
                    int in = PackRules.accepted(body, without, stored, coming);
                    int back = PackRules.returned(body, without, stored, coming);
                    int gone = PackRules.destroyed(body, without, stored, coming);
                    check(in + back + gone == coming, "putting " + coming
                            + " into " + body.name() + " with " + stored
                            + " inside accounted for " + (in + back + gone));
                    check(gone == 0, "a " + body.name() + " with no void part "
                            + "destroyed " + gone + " items, and there is exactly "
                            + "one place in this module where anything may be "
                            + "destroyed");
                    check(in <= room - stored, body.name() + " took " + in
                            + " into " + (room - stored) + " of room");

                    int sealedIn = PackRules.accepted(body, with, stored, coming);
                    int sealedGone = PackRules.destroyed(body, with, stored, coming);
                    check(sealedIn + PackRules.returned(body, with, stored, coming)
                                    + sealedGone == coming,
                            "a sealed " + body.name() + " did not account for "
                                    + coming);
                    check(sealedGone <= coming - sealedIn, "a sealed " + body.name()
                            + " destroyed " + sealedGone + " of " + coming
                            + ", which is more than would not have fitted");
                    check(PackRules.returned(body, with, stored, coming) == 0,
                            "a sealed " + body.name() + " handed something back, "
                                    + "which is the one thing a seal is for not "
                                    + "doing");
                }
            }
        }
        System.out.println("  the voiding: everything put into an unsealed body "
                + "either goes in or comes back, nothing is destroyed, and a "
                + "sealed one destroys only what would not have fitted");
    }

    // ==================================================================

    private static void theSweep() {
        // Random bodies, random fittings, random contents, random removals.
        // Every single check above is about one thing at a time; this is what
        // a player actually does to a bag over a hundred hours.
        Random random = new Random(1_123_581L);
        int rounds = 0;
        int refused = 0;
        for (int round = 0; round < 200_000; round++) {
            PackRules.Body body = PackRules.BODIES.get(
                    random.nextInt(PackRules.BODIES.size()));
            List<PackRules.Part> fitted = new ArrayList<>();
            Set<String> already = new LinkedHashSet<>();
            while (fitted.size() < body.fittings() && random.nextInt(4) > 0) {
                PackRules.Part one = PackRules.PARTS.get(
                        random.nextInt(PackRules.PARTS.size()));
                if (already.add(one.name())) {
                    fitted.add(one);
                }
            }
            if (!PackRules.fits(body, fitted)) {
                check(false, "a set of parts built inside the limits was refused: "
                        + PackRules.refuses(body, fitted));
                continue;
            }
            int room = PackRules.capacity(body, fitted);
            check(room == PackRules.capacity(body, fitted),
                    "capacity is not the same twice");
            int stored = random.nextInt(room + 1);
            if (!fitted.isEmpty()) {
                PackRules.Part going = fitted.get(random.nextInt(fitted.size()));
                int[] after = PackRules.afterRemoving(body, fitted, going, stored);
                check(after[0] == stored, "a removal changed what was inside");
                check(after[0] <= after[1], "a removal left " + after[0]
                        + " inside something that holds " + after[1]);
                if (!PackRules.mayRemove(body, fitted, going, stored)) {
                    refused++;
                }
            }
            int coming = random.nextInt(room + 16);
            int in = PackRules.accepted(body, fitted, stored, coming);
            int back = PackRules.returned(body, fitted, stored, coming);
            int gone = PackRules.destroyed(body, fitted, stored, coming);
            check(in + back + gone == coming, "a transfer did not account for "
                    + coming);
            check(stored + in <= room, "a transfer put " + (stored + in)
                    + " into " + room + " of room");
            check(gone == 0 || PackRules.voids(fitted),
                    "something was destroyed by a body with no void part fitted");
            rounds++;
        }
        check(refused > 0, "not one of " + rounds + " random removals was refused, "
                + "so the sweep never reached the case this file exists for");
        System.out.println("  the sweep: " + rounds + " random bags, fittings, "
                + "contents and removals, " + refused + " removals refused, and "
                + "not one item made or destroyed outside a void seal");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        PackRules.Body body = PackRules.BODIES.get(0);
        PackRules.Part pocket = PackRules.part("deep_pocket");
        check(pocket != null, "there is no deep pocket to take out of a full bag");

        // The removal check, handed a bag too full for it.
        check(!PackRules.mayRemove(body, List.of(pocket), pocket,
                        body.slots() + pocket.slots()),
                "a full bag let its deep pocket out, so the check that stops a bag "
                        + "losing what is in it cannot say no");
        check(PackRules.mayRemove(body, List.of(pocket), pocket, 0),
                "an empty bag refused to let its pocket out, so the check says no "
                        + "to everything");

        // A bag inside a bag, built to be caught.
        PackRules.Part asPart = new PackRules.Part(body.name(), PackRules.SPACE, 9);
        check(!PackRules.fits(body, List.of(asPart)),
                "a bag fitted inside a bag was allowed, so capacity is a recursion "
                        + "with no bottom to it");
        check(PackRules.refuses(body, List.of(asPart)).toString()
                        .contains("recursion"),
                "a bag inside a bag is refused for the wrong reason");

        // The same part twice, built to be caught.
        PackRules.Body roomy = PackRules.BODIES.get(PackRules.BODIES.size() - 1);
        check(!PackRules.fits(roomy, List.of(pocket, pocket)),
                "the same part twice was allowed in a body with room for two");
        check(PackRules.fits(roomy, List.of(pocket,
                        PackRules.part("large_pocket"))),
                "two different parts were refused in a body with room for two, so "
                        + "the check refuses everything");

        // The sum, against an answer known by hand.
        check(PackRules.capacity(body, List.of(pocket))
                        == body.slots() + pocket.slots(),
                "a bag plus a deep pocket is not the two added together");
        check(PackRules.accepted(body, List.of(), body.slots(), 10) == 0,
                "a full bag took something in");
        check(PackRules.returned(body, List.of(), body.slots(), 10) == 10,
                "a full bag with no seal did not hand back what would not fit");
        check(PackRules.destroyed(body, List.of(), body.slots(), 10) == 0,
                "a bag with no seal destroyed something");
        check(PackRules.destroyed(body, List.of(PackRules.part("void_seal")),
                        body.slots(), 10) == 10,
                "a sealed full bag did not void what would not fit, so the one "
                        + "exception in this module is an exception to nothing");

        // And the refusal list itself, on something that should pass.
        check(PackRules.refuses(body, List.of()).isEmpty(),
                "an empty bag is refused, so the refusal check says no to "
                        + "everything");

        // The records' own guards, which are the first line and the one thing
        // nothing above can exercise: the rosters are static, so a bad entry
        // never reaches a check, it takes the whole class down.
        List<String> allowed = new ArrayList<>();
        try {
            new PackRules.Part("control", PackRules.GATHER, 9);
            allowed.add("a part that is not a space part but adds room");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new PackRules.Part("control", PackRules.SPACE, 0);
            allowed.add("a space part that adds no room");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new PackRules.Body("control", 0, 1, true);
            allowed.add("a body that holds nothing");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "a part or a body may be built that the rules "
                + "say is impossible, so capacity is no longer the sum of what is "
                + "fitted: " + allowed);
        check(new PackRules.Part("control", PackRules.SPACE, 9).slots() == 9,
                "an ordinary space part is refused, so the guard refuses "
                        + "everything");

        System.out.println("  the controls: a full bag refusing to give up its "
                + "pocket, a bag fitted inside a bag caught, the same part twice "
                + "caught, and a sealed bag proved to void exactly what would not "
                + "have fitted");
    }
}
