package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The fittings that light a room, and the proof that lighting one is still a
 * decision.
 *
 * <h2>What a decoration roster does to a bestiary</h2>
 *
 * A pack adds thirty pretty light sources. Every one of them is nicer than a
 * torch, most of them are cheaper, and a good many are brighter. Nobody decides
 * anything; each block is added on its own and each one is fine on its own.
 *
 * <p>What happens next is that light stops being a resource. Lighting a room is
 * no longer a thing a player spends anything on or thinks about, which would be
 * a small loss by itself — except that somewhere else in the pack a bestiary
 * has been built on light. {@code elysium-bestiary}'s haunts each name the
 * band of light they appear in, and almost all of them are dark bands. A
 * decoration roster where every fitting is bright and free does not make those
 * creatures rarer; it makes them impossible anywhere a player has decorated,
 * which is everywhere a player spends time.
 *
 * <p>Nothing reports that. The blocks work, the rooms look good, and the pack
 * simply stops having a night in it.
 *
 * <h2>The four</h2>
 *
 * <b>Nothing here reaches full light.</b> {@link #BRIGHTEST} is fourteen. The
 * game's own fifteen belongs to glowstone and a sea lantern — things a player
 * goes and gets — and a decoration block that matches them makes both of those
 * pointless and any room instantly proof against everything in the dark.
 * {@link #faultsInGlare}.
 *
 * <b>Brighter costs more.</b> Within a kind, a fitting that gives more light
 * than another and costs no more is the only one of that kind anybody will
 * ever place. {@link #faultsInBargains}.
 *
 * <b>Something gives no light at all.</b> A roster where every way of fitting
 * out a room also brightens it leaves a player who wants a dark room with
 * nothing to decorate it with. {@link #faultsInUnlit}.
 *
 * <b>And the roster stays dark enough to be decorated with.</b> At least
 * {@link #DIM_SHARE} of it sits at or under {@link #STILL_DARK}, counted in
 * shapes rather than blocks so that sixteen colours of one thing cannot answer
 * the question sixteen times. That is the cross-module half of the claim, and
 * the one no amount of looking at this module alone would ever suggest: a
 * player must be able to make a room look finished without having made it
 * safe. {@link #faultsInDarkness}.
 *
 * <h2>And colour is not a stat</h2>
 *
 * Sixteen colours of candle holder is decoration. Sixteen colours of candle
 * holder at sixteen different light levels is a puzzle, and the answer is
 * always the same colour — at which point fifteen of them are decoration
 * nobody uses and one of them is the fitting. {@link #faultsInColour} holds
 * every colour of a kind to one light level and one price.
 *
 * <p>The dyed set is also the set a player places by the dozen, because it is
 * the one that matches what they are building. {@link #faultsInMatching} keeps
 * it dim for that reason: the share rule counts it once, and a player counts it
 * once a room.
 *
 * <h2>What this module does not take</h2>
 *
 * Sconces. They light a room and they belong in this story, and they are
 * {@code elysium-atelier}'s row in {@code docs/SPLITS.md} — six of them, in
 * the materials that module already has. A block converted twice is a block
 * registered twice, and the second one is the bug.
 */
public final class LightRules {

    private LightRules() {
    }

    // ==================================================================
    // The six kinds of fitting
    // ==================================================================

    /** It stands on a surface and holds candles. */
    public static final String HELD = "candle_holder";

    /** It hangs from a ceiling or a rope. */
    public static final String HUNG = "lantern";

    /** It is a block of stone that glows. */
    public static final String FLOOR = "lamp";

    /** It is a fire you sit round. */
    public static final String HEARTH = "fire_pit";

    /** It is on the ground and does nothing to the light at all. */
    public static final String UNDERFOOT = "doormat";

    /**
     * It hangs down a wall, and a lantern hangs off it.
     *
     * <p>The module already has one of these — {@link RestRules#ROPE}, which a
     * hammock is strung from — so this kind is filled by that block rather
     * than by a second rope beside it. {@link #ALREADY} is the list of such
     * things and {@link #faultsInDoubles} is why the list exists.
     */
    public static final String STRUNG = "rope";

    /** The six, in the order everything walks them. */
    public static final List<String> KINDS =
            List.of(HELD, HUNG, FLOOR, HEARTH, UNDERFOOT, STRUNG);

    /** The sixteen the game dyes things in. */
    public static final List<String> COLOURS = RestRules.COLOURS;

    /**
     * Fittings this module already has, under the names it already has them
     * under.
     *
     * <p>A rope is a rope. {@code elysium-comforts} registered one the day it
     * learned to hang a hammock, and a lighting roster that wants something to
     * hang a lantern from wants that block and not a new one spelled slightly
     * differently. Two ropes in one module is the sort of thing that survives
     * review — both entries look correct — and reaches a player as two
     * identical items in the creative tab, one of which does half of what the
     * other does.
     */
    public static final List<String> ALREADY = List.of(RestRules.ROPE);

    /**
     * One fitting.
     *
     * @param id     what it is called
     * @param kind   which of the six it is
     * @param light  what it gives, on the game's own nought-to-fifteen
     * @param cost   what it takes to make, counted in whole items
     * @param colour which dye it is, or empty for the ones that have no colour
     */
    public record Fitting(String id, String kind, int light, int cost,
                          String colour) {

        public Fitting {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a fitting with no name");
            }
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException(id + " is a " + kind
                        + " and there is no such kind of fitting");
            }
            if (light < 0 || light > 15) {
                throw new IllegalArgumentException(id + " gives " + light
                        + " light and the game counts nought to fifteen");
            }
            if (cost < 1) {
                throw new IllegalArgumentException(id + " costs " + cost
                        + ", so lighting a room is free and the whole question "
                        + "of whether to bother stops being one");
            }
            if (colour == null) {
                throw new IllegalArgumentException(id + " has a null colour; use "
                        + "the empty string for the ones that have none");
            }
        }

        /** Whether it is one of a dyed set. */
        public boolean dyed() {
            return !colour.isEmpty();
        }
    }

    /**
     * The brightest anything here may be.
     *
     * <p>Fourteen. Fifteen is the game's own, and it belongs to the two blocks
     * a player has to go somewhere to get. A decoration fitting that matched
     * them would make both pointless and would make any decorated room proof
     * against everything in the dark — which is not a buff to decoration, it is
     * the removal of a whole half of the game from anywhere anybody lives.
     */
    public static final int BRIGHTEST = 14;

    /**
     * The brightest a room can be and still have most of the bestiary in it.
     *
     * <p>Seven, and not a number anybody picked. {@code elysium-bestiary}'s
     * nine monsters each name a band of light they spawn in; five of the nine
     * include light seven and only two include light eight. Seven is therefore
     * the last light level at which a majority of the pack's monsters will
     * still appear, and eight is the level at which a room quietly becomes a
     * place where they do not.
     *
     * <p>Copied rather than imported, because the rules of this module are the
     * JDK and nothing else. {@code validate.py} works the number out again
     * from {@code HauntRules.HAUNTS} itself, so the copy cannot drift from the
     * bestiary without a build failure — and if somebody widens a monster's
     * band, this number moves and this module has to answer for it.
     */
    public static final int STILL_DARK = 7;

    /**
     * How much of the roster has to be dim enough to decorate with.
     *
     * <p>A third. Not because a third is special, but because a roster where
     * every fitting is bright is a roster a player cannot use in a room they
     * want anything to happen in — and the way that failure presents itself is
     * that they stop decorating, rather than that they complain.
     */
    public static final double DIM_SHARE = 1.0D / 3.0D;

    /**
     * How many things fit in a crafting grid.
     *
     * <p>Nine, and the reason it is a rule rather than an obvious fact is the
     * way the game fails when it is broken: a recipe with ten ingredients is
     * not refused loudly, it is dropped during data load with one line in a
     * log nobody reads, and the block it made simply has no recipe. Every
     * other test passes. The block is registered, modelled, named, dropped and
     * priced, and no player can make one.
     */
    public static final int GRID = 9;

    /**
     * Every fitting.
     *
     * <p>The dyed set is built rather than written out, because sixteen
     * colours of one thing is one thing and sixteen rows would be sixteen
     * chances for one of them to quietly differ — which is precisely what
     * {@link #faultsInColour} exists to catch, and there is no reason to hand
     * it the opportunity.
     */
    public static final List<Fitting> FITTINGS = fittings();

    private static List<Fitting> fittings() {
        List<Fitting> made = new ArrayList<>();
        // The dyed set: one light, one price, sixteen colours.
        for (String colour : COLOURS) {
            made.add(new Fitting(colour + "_" + HELD, HELD, 6, 3, colour));
        }
        made.add(new Fitting(HELD, HELD, 6, 3, ""));
        // The rest, each one its own row because each one is its own thing.
        made.add(new Fitting("soul_" + HELD, HELD, 4, 2, ""));
        made.add(new Fitting("cupric_" + HELD, HELD, 7, 4, ""));
        made.add(new Fitting("ender_" + HELD, HELD, 9, 5, ""));
        made.add(new Fitting("candelabra", HELD, 12, 7, ""));
        made.add(new Fitting("gold_" + HUNG, HUNG, 12, 6, ""));
        made.add(new Fitting("lead_" + HUNG, HUNG, 9, 4, ""));
        made.add(new Fitting("stone_" + FLOOR, FLOOR, 13, 8, ""));
        made.add(new Fitting("deepslate_" + FLOOR, FLOOR, 13, 8, ""));
        made.add(new Fitting("blackstone_" + FLOOR, FLOOR, BRIGHTEST, 9, ""));
        made.add(new Fitting("end_stone_" + FLOOR, FLOOR, BRIGHTEST, 9, ""));
        made.add(new Fitting(HEARTH, HEARTH, 11, 5, ""));
        made.add(new Fitting(UNDERFOOT, UNDERFOOT, 0, 2, ""));
        made.add(new Fitting(RestRules.ROPE, STRUNG, 0, 1, ""));
        return List.copyOf(made);
    }

    /** The fitting of this name, or null. */
    public static Fitting fitting(String id) {
        for (Fitting one : FITTINGS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Everything of one kind, out of the given roster. */
    public static List<Fitting> ofKind(String kind, List<Fitting> roster) {
        List<Fitting> found = new ArrayList<>();
        for (Fitting one : roster) {
            if (one.kind().equals(kind)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /**
     * The roster with each dyed set counted once.
     *
     * <p>A player who wants one candle holder has one decision to make, not
     * seventeen: which kind of thing to put in the room. The colour is picked
     * afterwards and is not that decision. Anything that asks what the roster
     * is <em>like</em> has to ask it of these, or a dyed set of sixteen answers
     * every such question sixteen times over and drowns out the rest.
     */
    public static List<Fitting> shapes(List<Fitting> roster) {
        List<Fitting> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Fitting one : roster) {
            String key = one.dyed() ? one.kind() + " (dyed)" : one.id();
            if (seen.add(key)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** How many of a roster are dim enough to decorate a dark room with. */
    public static int dim(List<Fitting> roster, int stillDark) {
        int found = 0;
        for (Fitting one : roster) {
            if (one.light() <= stillDark) {
                found++;
            }
        }
        return found;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the fittings. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(FITTINGS));
        bad.addAll(faultsInKinds(FITTINGS, KINDS));
        bad.addAll(faultsInGaps(FITTINGS, KINDS));
        bad.addAll(faultsInGlare(FITTINGS, BRIGHTEST));
        bad.addAll(faultsInBargains(FITTINGS));
        bad.addAll(faultsInColour(FITTINGS));
        bad.addAll(faultsInUnlit(FITTINGS));
        bad.addAll(faultsInDoubles(FITTINGS, ALREADY));
        bad.addAll(faultsInGrid(FITTINGS, ALREADY, GRID));
        bad.addAll(faultsInMatching(FITTINGS, STILL_DARK));
        bad.addAll(faultsInDarkness(FITTINGS, STILL_DARK, DIM_SHARE));
        bad.addAll(faultsInDyedSets(FITTINGS, COLOURS));
        return List.copyOf(bad);
    }

    /** No two fittings written under one name. */
    public static List<String> faultsInNames(List<Fitting> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Fitting one : roster) {
            if (!seen.add(one.id())) {
                bad.add("two fittings are called " + one.id() + ", and whichever "
                        + "the game registers second is a block nobody can place");
            }
        }
        return bad;
    }

    /** Every fitting is one of the kinds this module has. */
    public static List<String> faultsInKinds(List<Fitting> roster, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (Fitting one : roster) {
            if (!kinds.contains(one.kind())) {
                bad.add(one.id() + " is a " + one.kind() + " and this module has "
                        + "no such kind of fitting, so nothing decides how it is "
                        + "placed or what it costs");
            }
        }
        return bad;
    }

    /** Every kind is something in the roster. */
    public static List<String> faultsInGaps(List<Fitting> roster, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (String kind : kinds) {
            if (ofKind(kind, roster).isEmpty()) {
                bad.add("nothing in the roster is a " + kind + ", so it is a way "
                        + "of fitting out a room that this module names and never "
                        + "makes");
            }
        }
        return bad;
    }

    /** Nothing reaches the light the game keeps for itself. */
    public static List<String> faultsInGlare(List<Fitting> roster, int brightest) {
        List<String> bad = new ArrayList<>();
        for (Fitting one : roster) {
            if (one.light() > brightest) {
                bad.add(one.id() + " gives " + one.light() + " light against a "
                        + "ceiling of " + brightest + ", so glowstone and a sea "
                        + "lantern are both pointless and any room somebody has "
                        + "decorated is proof against everything in the dark");
            }
        }
        return bad;
    }

    /**
     * Within a kind, brighter costs more.
     *
     * <p>Not across kinds: a lamp is a block of stone and a candle holder is a
     * candle, and comparing their prices would be comparing two different
     * decisions. Within a kind it is one decision, and a fitting that wins it
     * outright is the only one of that kind anybody ever places.
     */
    public static List<String> faultsInBargains(List<Fitting> roster) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            List<Fitting> these = ofKind(kind, roster);
            for (Fitting one : these) {
                for (Fitting other : these) {
                    if (one.light() > other.light() && one.cost() <= other.cost()) {
                        bad.add(one.id() + " gives " + one.light() + " light for "
                                + one.cost() + " and " + other.id() + " gives "
                                + other.light() + " for " + other.cost()
                                + ", so nobody ever places the second one");
                    }
                }
            }
        }
        return bad;
    }

    /**
     * Colour is not a stat.
     *
     * <p>Every dyed fitting of a kind gives the same light for the same price.
     * Sixteen colours at sixteen light levels is not decoration, it is a puzzle
     * whose answer is always one colour — and then fifteen of them are things
     * nobody places and one of them is the fitting.
     */
    public static List<String> faultsInColour(List<Fitting> roster) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            Fitting first = null;
            for (Fitting one : ofKind(kind, roster)) {
                if (!one.dyed()) {
                    continue;
                }
                if (first == null) {
                    first = one;
                    continue;
                }
                if (one.light() != first.light() || one.cost() != first.cost()) {
                    bad.add(one.id() + " gives " + one.light() + " light for "
                            + one.cost() + " and " + first.id() + " gives "
                            + first.light() + " for " + first.cost()
                            + " — so which colour a player picks has stopped "
                            + "being a decision about how the room looks");
                }
            }
        }
        return bad;
    }

    /**
     * Something in the roster gives no light at all.
     *
     * <p>The smallest of the rules and the one that says what the module is
     * for. A decoration roster in which every single block also brightens the
     * room is not a decoration roster, it is a lighting roster with pictures
     * on it, and a player who wants a room that is furnished and dark has
     * nothing in it to furnish with.
     */
    public static List<String> faultsInUnlit(List<Fitting> roster) {
        List<String> bad = new ArrayList<>();
        boolean any = false;
        for (Fitting one : roster) {
            any |= one.light() == 0;
        }
        if (!any) {
            bad.add("every one of " + roster.size() + " fitting(s) gives light, "
                    + "so there is no way to furnish a room out of this module "
                    + "without also lighting it, and a player who wants somewhere "
                    + "furnished and dark has to leave it empty");
        }
        return bad;
    }

    /**
     * Everything is cheap enough to be laid out in a crafting grid.
     *
     * <p>Skips whatever this module already has, because those were priced
     * somewhere else and are made somewhere else; this rule is about the
     * recipes written from this table.
     */
    public static List<String> faultsInGrid(List<Fitting> roster,
                                            List<String> already, int grid) {
        List<String> bad = new ArrayList<>();
        for (Fitting one : roster) {
            if (already.contains(one.id())) {
                continue;
            }
            if (one.cost() > grid) {
                bad.add(one.id() + " costs " + one.cost() + " and a crafting "
                        + "grid holds " + grid + ", so its recipe is dropped "
                        + "during data load with one line in a log and the "
                        + "block is registered, modelled, named and impossible "
                        + "to make");
            }
        }
        return bad;
    }

    /**
     * Nothing here is a second spelling of something the module already has.
     *
     * <p>Compared by the parts of a name rather than the whole of it, on
     * purpose: {@code rope} and {@code rope_and_nail} are not equal strings and
     * are very plainly the same block, and the bug this catches is exactly the
     * one where somebody writes the shorter name because the longer one did not
     * come to mind. A fitting that <em>is</em> one of them, spelled the way the
     * module already spells it, is the correct answer and passes.
     */
    public static List<String> faultsInDoubles(List<Fitting> roster,
                                               List<String> already) {
        List<String> bad = new ArrayList<>();
        for (String have : already) {
            boolean any = false;
            for (Fitting one : roster) {
                any |= one.id().equals(have);
            }
            if (!any) {
                bad.add(have + " is named as a block this module already has "
                        + "and nothing in the roster is it, so the count the "
                        + "registry checks itself against is the size of the "
                        + "roster less a thing that was never in it, and one "
                        + "fitting can go missing without the count moving");
            }
        }
        for (Fitting one : roster) {
            for (String have : already) {
                if (one.id().equals(have)) {
                    continue;
                }
                boolean same = have.startsWith(one.id() + "_")
                        || one.id().startsWith(have + "_")
                        || have.endsWith("_" + one.id())
                        || one.id().endsWith("_" + have);
                if (same) {
                    bad.add(one.id() + " is a fitting and " + have + " is "
                            + "already a block in this module, and those are the "
                            + "same thing under two names — so the pack ships "
                            + "two of it, one of which does half of what the "
                            + "other does");
                }
            }
        }
        return bad;
    }

    /**
     * The set a player places by the dozen is a dim one.
     *
     * <p>{@link #faultsInDarkness} counts a dyed set once, which is right for
     * asking what the roster is like and wrong for asking what a room ends up
     * like: the dyed set is the one that matches whatever is being built, so it
     * is the one that goes in every room, sixteen times over. If it is bright,
     * the share rule can be satisfied by things nobody places and the rooms are
     * lit anyway.
     */
    public static List<String> faultsInMatching(List<Fitting> roster, int stillDark) {
        List<String> bad = new ArrayList<>();
        for (Fitting one : roster) {
            if (one.dyed() && one.light() > stillDark) {
                bad.add(one.id() + " gives " + one.light() + " light and is one "
                        + "of a dyed set, which is the set that matches whatever "
                        + "a player is building and therefore the one that goes "
                        + "in every room — so every room is over " + stillDark
                        + " light however dim the rest of the roster is");
            }
        }
        return bad;
    }

    /**
     * The roster stays dark enough to decorate a dark room with.
     *
     * <p>The cross-module half, and the one nothing about this module alone
     * would ever suggest. Counted in {@link #shapes} rather than fittings,
     * because a dyed set of sixteen is one thing to choose and would otherwise
     * settle this question on its own.
     *
     * <p>Takes both numbers in rather than reading the constants, so a control
     * can move one and be caught by this rule rather than by arithmetic that
     * re-solved itself against the broken value.
     */
    public static List<String> faultsInDarkness(List<Fitting> roster,
                                                int stillDark, double share) {
        List<String> bad = new ArrayList<>();
        List<Fitting> these = shapes(roster);
        if (these.isEmpty()) {
            return bad;
        }
        int dim = dim(these, stillDark);
        if (dim < these.size() * share) {
            bad.add("only " + dim + " of " + these.size() + " shape(s) of fitting "
                    + "are at or under " + stillDark + " light, which is under "
                    + "the share this module keeps dim — so a player cannot make "
                    + "a room look finished without also making it safe, and "
                    + "everything the bestiary puts in the dark stops happening "
                    + "anywhere anybody lives");
        }
        return bad;
    }

    /**
     * A dyed set is the whole set.
     *
     * <p>Fifteen colours of a thing is the kind of gap nobody notices until a
     * player with the sixteenth dye finds they cannot make one — which reads as
     * a bug, and is.
     */
    public static List<String> faultsInDyedSets(List<Fitting> roster,
                                                List<String> colours) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            Set<String> found = new LinkedHashSet<>();
            for (Fitting one : ofKind(kind, roster)) {
                if (one.dyed()) {
                    found.add(one.colour());
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            for (String colour : colours) {
                if (!found.contains(colour)) {
                    bad.add("there is no " + colour + " " + kind + " and there "
                            + "are " + found.size() + " other colours of one, so "
                            + "a player with that dye finds a gap that reads as "
                            + "a bug");
                }
            }
        }
        return bad;
    }
}
