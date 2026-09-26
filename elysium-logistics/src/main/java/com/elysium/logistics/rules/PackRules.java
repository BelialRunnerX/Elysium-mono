package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A container whose capacity and behaviour come from the parts fitted to it.
 * The JDK and nothing else.
 *
 * <h2>One idea in four jars</h2>
 *
 * {@code modular_backpacks}, {@code sophisticatedbackpacks},
 * {@code sophisticatedstorage} and {@code sophisticatedcore} are one idea
 * between them: a bag or a barrel with slots down the side, and what you put
 * in those slots decides how big it is and what it does. So it is built once,
 * here, and both a bag and a barrel are bodies it is fitted to.
 *
 * <h2>The four things that have to be true</h2>
 *
 * <ol>
 *   <li><b>Capacity is the sum of what is fitted, and never more.</b> A bag
 *       that reports more room than its parts add up to is a bag that drops
 *       the difference the first time it is filled.
 *   <li><b>A part cannot be fitted twice.</b> Two of the same upgrade either
 *       do nothing, which is a player robbed, or stack, which is a hole in
 *       the ladder.
 *   <li><b>Taking a part out never destroys what is inside.</b> The one that
 *       every mod of this kind gets wrong: pull the space upgrade out of a
 *       full bag and where do the items go? Here the answer is that the
 *       removal is <em>refused</em> while the bag is too full for it, and
 *       that refusal is the thing the sweep proves.
 *   <li><b>A bag may not go inside a bag.</b> Not because it would be
 *       confusing, but because capacity would then be a recursion and there
 *       is no bottom to it.
 * </ol>
 *
 * <p>The one place anything here is allowed to destroy an item is a void
 * fitting, and then only what would not have fitted anyway. That is the same
 * rule the rest of this module already lives under.
 */
public final class PackRules {

    private PackRules() {
    }

    // ==================================================================
    // The bodies
    // ==================================================================

    /**
     * A thing parts are fitted to.
     *
     * @param name     what it is called, which is also its item id
     * @param slots    how much it holds before anything is fitted
     * @param fittings how many parts it takes
     * @param carried  whether a player wears it rather than places it
     */
    public record Body(String name, int slots, int fittings, boolean carried) {

        public Body {
            Objects.requireNonNull(name, "a body with no name");
            if (slots < 1) {
                throw new IllegalArgumentException(name + " holds nothing");
            }
            if (fittings < 0) {
                throw new IllegalArgumentException(name + " takes " + fittings
                        + " parts");
            }
        }
    }

    /**
     * The bodies, in the order a player meets them.
     *
     * <p>Two kinds, and the same parts go in both: a bag you wear and a crate
     * you put down. The jars this replaces ship those as two mods with two
     * upgrade systems that do not share an upgrade.
     */
    public static final List<Body> BODIES = List.of(
            new Body("satchel", 9, 1, true),
            new Body("knapsack", 18, 2, true),
            new Body("haversack", 27, 3, true),
            new Body("rucksack", 36, 4, true),
            new Body("expedition_pack", 45, 5, true),
            new Body("crate", 27, 2, false),
            new Body("strongbox", 45, 4, false),
            new Body("vault", 63, 6, false));

    /** A body by name, or null. */
    public static Body body(String name) {
        for (Body one : BODIES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // The parts
    // ==================================================================

    /** A part that makes the thing bigger. */
    public static final String SPACE = "space";

    /** A part that picks things up off the ground. */
    public static final String GATHER = "gather";

    /** A part that throws away what would not have fitted. */
    public static final String VOID = "void";

    /** A part that feeds its wearer. */
    public static final String FEED = "feed";

    /** A part that cooks what goes in. */
    public static final String COOK = "cook";

    /** A part that compresses what goes in. */
    public static final String PRESS = "press";

    /** A part that keeps a stack topped up in the player's hand. */
    public static final String RESTOCK = "restock";

    /** A part that decides what may go in at all. */
    public static final String SIEVE = "sieve";

    public static final List<String> KINDS = List.of(
            SPACE, GATHER, VOID, FEED, COOK, PRESS, RESTOCK, SIEVE);

    /**
     * One part.
     *
     * @param name  what it is called, which is also its item id
     * @param kind  which of {@link #KINDS}
     * @param slots how much room it adds, which is zero for everything that
     *              is not a {@link #SPACE} part
     */
    public record Part(String name, String kind, int slots) {

        public Part {
            Objects.requireNonNull(name, "a part with no name");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException(name + " is a " + kind
                        + " and the kinds are " + KINDS);
            }
            if (slots < 0) {
                throw new IllegalArgumentException(name + " adds " + slots
                        + " slots, which takes room away rather than adding it");
            }
            if (slots > 0 != SPACE.equals(kind)) {
                throw new IllegalArgumentException(name + " is a " + kind
                        + " part and adds " + slots + " slots; only a " + SPACE
                        + " part changes how much a thing holds, or capacity is "
                        + "no longer the sum of what is fitted");
            }
        }
    }

    /** Every part, the three sizes of room first. */
    public static final List<Part> PARTS = List.of(
            new Part("small_pocket", SPACE, 9),
            new Part("large_pocket", SPACE, 18),
            new Part("deep_pocket", SPACE, 27),
            new Part("gathering_lens", GATHER, 0),
            new Part("void_seal", VOID, 0),
            new Part("ration_tin", FEED, 0),
            new Part("ember_plate", COOK, 0),
            new Part("press_screw", PRESS, 0),
            new Part("restocker", RESTOCK, 0),
            new Part("sieve_mesh", SIEVE, 0));

    /** A part by name, or null. */
    public static Part part(String name) {
        for (Part one : PARTS) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // Capacity
    // ==================================================================

    /**
     * How much a body fitted with these parts holds.
     *
     * <p>The sum, and only the sum. There is no bonus anywhere: a number that
     * is not the sum of its parts is a number nobody can check against the
     * thing in their hand.
     */
    public static int capacity(Body body, List<Part> fitted) {
        int room = body.slots();
        for (Part one : fitted) {
            room += one.slots();
        }
        return room;
    }

    /**
     * Why this set of parts cannot go in this body, if it cannot.
     *
     * <p>Empty means it can. Every entry is something a player would see as
     * the fitting simply being refused.
     */
    public static List<String> refuses(Body body, List<Part> fitted) {
        List<String> why = new ArrayList<>();
        if (fitted.size() > body.fittings()) {
            why.add(body.name() + " takes " + body.fittings() + " part(s) and "
                    + fitted.size() + " were fitted");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Part one : fitted) {
            if (!seen.add(one.name())) {
                why.add(one.name() + " is fitted twice, which either does nothing "
                        + "or does it twice and both are wrong");
            }
            if (body(one.name()) != null) {
                why.add(one.name() + " is a body, and a bag inside a bag makes "
                        + "capacity a recursion with no bottom to it");
            }
        }
        return List.copyOf(why);
    }

    /** Whether these parts go in this body at all. */
    public static boolean fits(Body body, List<Part> fitted) {
        return refuses(body, fitted).isEmpty();
    }

    // ==================================================================
    // Taking a part out
    // ==================================================================

    /**
     * Whether a part may come out while this much is inside.
     *
     * <p>The check every mod of this kind gets wrong. Pulling the deep pocket
     * out of a full bag has to do <em>something</em> with twenty-seven slots
     * of items, and the two honest answers are "spill them" and "refuse".
     * This refuses, because spilling into a chest that is not there is how
     * items go missing.
     */
    public static boolean mayRemove(Body body, List<Part> fitted, Part going,
                                    int stored) {
        List<Part> after = new ArrayList<>(fitted);
        if (!after.remove(going)) {
            return false;
        }
        return stored <= capacity(body, after);
    }

    /**
     * What is inside after a part is taken out, and how much room there is
     * for it: {@code {stored, capacity}}.
     *
     * <p>Two numbers rather than one, because the property is a relation
     * between them. Saying "nothing was lost" on its own is a tautology —
     * of course the number did not change, nothing subtracted from it. The
     * check that means something is that what is still inside <b>still
     * fits</b>, and that is only visible with the room beside it.
     */
    public static int[] afterRemoving(Body body, List<Part> fitted, Part going,
                                      int stored) {
        if (!mayRemove(body, fitted, going, stored)) {
            return new int[] {stored, capacity(body, fitted)};
        }
        List<Part> after = new ArrayList<>(fitted);
        after.remove(going);
        return new int[] {stored, capacity(body, after)};
    }

    // ==================================================================
    // The one place anything here may destroy something
    // ==================================================================

    /**
     * How much of an incoming pile actually goes in.
     *
     * <p>What is left over is handed back to the player — unless a
     * {@link #VOID} part is fitted, which is the single exception in this
     * module to the rule that nothing is destroyed, and it is an exception a
     * player fitted on purpose.
     */
    public static int accepted(Body body, List<Part> fitted, int stored,
                               int coming) {
        int room = capacity(body, fitted) - stored;
        return Math.max(0, Math.min(room, coming));
    }

    /** What comes back to the player after putting a pile in. */
    public static int returned(Body body, List<Part> fitted, int stored,
                               int coming) {
        int over = coming - accepted(body, fitted, stored, coming);
        return voids(fitted) ? 0 : over;
    }

    /** Whether a void part is fitted. */
    public static boolean voids(List<Part> fitted) {
        for (Part one : fitted) {
            if (VOID.equals(one.kind())) {
                return true;
            }
        }
        return false;
    }

    /**
     * How much a transfer destroyed.
     *
     * <p>Must be zero unless a void part is fitted, and must never be more
     * than what would not have fitted anyway.
     */
    public static int destroyed(Body body, List<Part> fitted, int stored,
                                int coming) {
        return coming - accepted(body, fitted, stored, coming)
                - returned(body, fitted, stored, coming);
    }

    // ==================================================================
    // Showing one
    // ==================================================================

    /** How wide a row of slots is, in the game's own container screens. */
    public static final int ROW = 9;

    /**
     * The most slots one screen of a pack shows: six rows, which is the
     * largest of the game's own generic container menus.
     *
     * <p>A pack is not capped at this. An expedition pack with every pocket
     * fitted holds ninety-nine and a vault a hundred and seventeen, and a cap
     * would make capacity something other than the sum of what is fitted —
     * the first of the four things this file exists to keep true. So a big
     * pack is shown a page at a time, and each opening turns the page.
     */
    public static final int MOST_SHOWN = 6 * ROW;

    /** How many pages a pack of this capacity is shown across. */
    public static int pages(int capacity) {
        return Math.max(1, (capacity + MOST_SHOWN - 1) / MOST_SHOWN);
    }

    /** The first slot shown on a page. */
    public static int firstOn(int page) {
        return page * MOST_SHOWN;
    }

    /** How many slots a page shows: a full screen, or what is left on the last. */
    public static int shownOn(int capacity, int page) {
        if (page < 0 || page >= pages(capacity)) {
            return 0;
        }
        return Math.min(MOST_SHOWN, capacity - firstOn(page));
    }

    /** How many rows that is, which is what picks the menu. */
    public static int rowsOn(int capacity, int page) {
        return (shownOn(capacity, page) + ROW - 1) / ROW;
    }

    /** The page after this one, back to the first after the last. */
    public static int nextPage(int capacity, int page) {
        if (page < 0) {
            return 0;
        }
        return (page + 1) % pages(capacity);
    }

    // ==================================================================
    // What may go in, and what the parts do to it
    // ==================================================================

    /** Whether a part of this kind is fitted. */
    public static boolean has(List<Part> fitted, String kind) {
        for (Part one : fitted) {
            if (one.kind().equals(kind)) {
                return true;
            }
        }
        return false;
    }

    /** The parts these names stand for, in order, leaving out any that are not parts. */
    public static List<Part> partsNamed(List<String> names) {
        List<Part> found = new ArrayList<>();
        for (String name : names) {
            Part one = part(name);
            if (one != null) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /**
     * Whether a thing of this name may be put inside a pack at all.
     *
     * <p>Every body is refused, carried or placed: a bag inside a bag makes
     * capacity a recursion, and a crate item inside a bag is a bag inside a
     * bag with a different sprite. Everything else is welcome.
     */
    public static boolean mayHold(String name) {
        return body(name) == null;
    }

    /**
     * Whether something coming in on its own — picked up, or pushed by a
     * hopper — may go in.
     *
     * <p>A sieve makes what is already inside the filter: a sieved pack takes
     * more of what it holds and nothing new. Only intake nobody chose is
     * sieved. A player putting a thing in by hand has chosen it, which is
     * also how a sieved pack is ever given its first thing.
     */
    public static boolean admits(List<Part> fitted, boolean alreadyHeld) {
        return !has(fitted, SIEVE) || alreadyHeld;
    }

    /**
     * Whether what does not fit is destroyed rather than left where it was.
     *
     * <p>Only with a void part fitted, and only for a thing the pack already
     * holds: a seal is for the cobblestone a player is sick of, not for the
     * diamond that happened to be picked up while the bag was full.
     */
    public static boolean voidsOverflow(List<Part> fitted, boolean alreadyHeld) {
        return voids(fitted) && alreadyHeld;
    }

    /** How often a feeding part looks at its wearer's hunger, in ticks. */
    public static final int FEED_EVERY = 20;

    /** How often a restocking part looks at its wearer's hand, in ticks. */
    public static final int RESTOCK_EVERY = 10;

    /**
     * Whether a food worth this much is eaten by a hungry wearer.
     *
     * <p>Only when all of it is wanted. A ration tin that eats a steak to
     * fill one missing point of hunger is a tin that empties the bag.
     */
    public static boolean worthEating(int missing, int nutrition) {
        return nutrition > 0 && nutrition <= missing;
    }

    /**
     * How many to bring into a hand holding {@code held} of something that
     * stacks to {@code most}.
     *
     * <p>Nothing into an empty hand: a restocker keeps a stack topped up, it
     * does not decide what a player is holding.
     */
    public static int restockWanted(int held, int most) {
        if (held <= 0 || held >= most) {
            return 0;
        }
        return most - held;
    }

    /**
     * What a press makes of a pile: {@code {made, left}}.
     *
     * <p>The step is the compression's own, so the press and the crafting
     * grid agree to the item and a pressed bag can be unpacked on a grid
     * back into exactly what went in.
     */
    public static int[] pressed(int count) {
        int step = CompressRules.PER_STEP;
        return new int[] {count / step, count % step};
    }

    /**
     * How many a pile cooks into, or zero if it is left raw.
     *
     * <p>Left raw rather than cooked in part when the result would not fit
     * in the slot, because a half-cooked pile is two things in one slot.
     */
    public static int cooked(int count, int yield, int most) {
        if (count <= 0 || yield <= 0) {
            return 0;
        }
        long made = (long) count * yield;
        return made <= most ? (int) made : 0;
    }

    // ==================================================================
    // What has to be true of the set
    // ==================================================================

    /** Every body or part declared twice, or sharing a name. Must be empty. */
    public static List<String> repeated() {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Body one : BODIES) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        for (Part one : PARTS) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice, or shares its name with "
                        + "a body, and two items with one id is one of them not "
                        + "existing");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every kind of part that nothing is, and every body that takes fewer
     * parts than the one below it. Must be empty.
     */
    public static List<String> unladdered() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            boolean found = false;
            for (Part one : PARTS) {
                if (one.kind().equals(kind)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                bad.add(kind + " is a kind of part and nothing is one, so the list "
                        + "of kinds is a list nothing walks");
            }
        }
        int slots = 0;
        int fittings = 0;
        for (Body one : BODIES) {
            if (!one.carried()) {
                continue;
            }
            if (one.slots() <= slots || one.fittings() < fittings) {
                bad.add(one.name() + " holds " + one.slots() + " in " + one.fittings()
                        + " fitting(s) and the bag below it holds " + slots + " in "
                        + fittings + ", so one of the two is a rung nobody climbs");
            }
            slots = one.slots();
            fittings = one.fittings();
        }
        return List.copyOf(bad);
    }

    /** Every item id this file adds. */
    public static List<String> catalogue() {
        List<String> made = new ArrayList<>();
        for (Body one : BODIES) {
            made.add(one.name());
        }
        for (Part one : PARTS) {
            made.add(one.name());
        }
        return List.copyOf(made);
    }

    /** How many items that is. */
    public static int howMany() {
        return catalogue().size();
    }
}
