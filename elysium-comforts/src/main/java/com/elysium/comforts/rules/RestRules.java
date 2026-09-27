package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Somewhere to sleep that is not a bed.
 *
 * <h2>What a bed costs you</h2>
 *
 * A bed moves your spawn. That is the whole of what makes exploring
 * expensive: three hundred blocks out, night falls, and the choice is to stand
 * in the dark until morning or to put down a bed and quietly throw away the
 * base you came from. Every player solves it the same way — they carry a bed
 * and they never place it — and the night becomes a thing you wait out rather
 * than a thing you sleep through.
 *
 * <h2>Two ways to rest, and the clock they divide between them</h2>
 *
 * A sleeping bag is for the night and a hammock is for the day, and neither of
 * them touches where you wake up when you die. That division is not
 * decoration: if one thing could be used at any hour, it would delete the
 * night outright — lie down, stand up, lie down again — and the whole of the
 * game's day would stop happening.
 *
 * <p>So {@link #usableAt} is a <em>partition</em> of the clock, and the
 * harness proves it over every tick of the day: exactly one of the two kinds
 * is usable at any moment, never both and never neither. And you always wake
 * on the other side of that line, so the thing you just used cannot be used
 * again until the world has moved on — which is what stops a hammock and a
 * sleeping bag in the same pocket from being a button that skips time.
 */
public final class RestRules {

    private RestRules() {
    }

    // ==================================================================
    // The two kinds
    // ==================================================================

    /** Rolled out on the floor, for the night. */
    public static final String BAG = "sleeping_bag";

    /** Strung between two supports, for the day. */
    public static final String HAMMOCK = "hammock";

    /** Both, in the order everything walks them. */
    public static final List<String> KINDS = List.of(BAG, HAMMOCK);

    /** What a hammock has to hang from. */
    public static final String ROPE = "rope_and_nail";

    /**
     * The sixteen colours, which are the game's own.
     *
     * <p>Named here rather than read off the game's dye registry because the
     * rules are the JDK alone and a harness cannot ask a game that is not
     * running. {@code validate.py} checks every one of them against the
     * game's own language file, so a seventeenth invented colour is a
     * finding rather than a block nobody can craft.
     */
    public static final List<String> COLOURS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
            "green", "red", "black");

    /** Every block this module makes for resting in. */
    public static List<String> roster() {
        List<String> made = new ArrayList<>();
        for (String kind : KINDS) {
            for (String colour : COLOURS) {
                made.add(colour + "_" + kind);
            }
        }
        made.add(ROPE);
        return List.copyOf(made);
    }

    // ==================================================================
    // The clock
    // ==================================================================

    /** Ticks in a day. */
    public static final int DAY = 24_000;

    /** The tick the game itself will first let you into a bed. */
    public static final int DUSK = 12_542;

    /** And the tick it throws you out of one. */
    public static final int DAWN = 23_460;

    /** Whether the world is dark enough to want a sleeping bag. */
    public static boolean isNight(int tick) {
        int at = Math.floorMod(tick, DAY);
        return at >= DUSK && at < DAWN;
    }

    /**
     * Which kind may be used at this moment, and there is always exactly one.
     *
     * <p>Never an empty string and never both: the harness sweeps all
     * {@value #DAY} ticks and proves it, because a gap in the partition is an
     * hour of the day when neither works and nobody could say why, and an
     * overlap is the button that deletes the night.
     */
    public static String usableAt(int tick) {
        return isNight(tick) ? BAG : HAMMOCK;
    }

    /** The tick you wake at, having rested in this. */
    public static int wakesAt(String kind) {
        return BAG.equals(kind) ? DAWN : DUSK;
    }

    /** How long this rest skips, from here. */
    public static int skipsFrom(int tick, String kind) {
        int at = Math.floorMod(tick, DAY);
        int wakes = wakesAt(kind);
        return Math.floorMod(wakes - at, DAY);
    }

    // ==================================================================
    // Why it might refuse
    // ==================================================================

    /** Somebody else is in it. */
    public static final String OCCUPIED = "occupied";

    /** You are not near enough to get into it. */
    public static final String TOO_FAR = "too_far_away";

    /** There is no room to lie down. */
    public static final String NO_ROOM = "no_space";

    /** A hammock with nothing to hang from. */
    public static final String NO_ROPE = "missing_rope";

    /** The wrong half of the day for this one. */
    public static final String NOT_NOW = "not_now";

    /** Something is standing over you that should not be. */
    public static final String UNSAFE = "not_safe";

    /**
     * Every way this can go wrong.
     *
     * <p>Each with its own sentence, and every sentence reachable. A refusal
     * that shares a message with another is a player who is told "you cannot
     * sleep here" and has to guess which of six reasons it was; a refusal
     * with no message at all is a block that does nothing when you click it.
     */
    public static final List<String> REFUSALS =
            List.of(OCCUPIED, TOO_FAR, NO_ROOM, NO_ROPE, NOT_NOW, UNSAFE);

    /** How far away you may be and still get in, in blocks. */
    public static final double REACHES = 3.0D;

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the resting. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInColours(COLOURS));
        bad.addAll(faultsInCount(roster()));
        bad.addAll(faultsInDuplicates(roster()));
        bad.addAll(faultsInMissing(roster()));
        bad.addAll(faultsInClock(DAY));
        bad.addAll(faultsInWaking(KINDS));
        bad.addAll(faultsInRefusals(REFUSALS));
        return List.copyOf(bad);
    }

    /** Whether the colours are sixteen distinct ones. */
    public static List<String> faultsInColours(List<String> colours) {
        List<String> bad = new ArrayList<>();
        if (colours.size() != 16) {
            bad.add("there are " + colours.size() + " colours and the game has "
                    + "sixteen, so either one of ours is invented or one of the "
                    + "game's has no bag and no hammock in it");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String colour : colours) {
            if (!seen.add(colour)) {
                bad.add(colour + " is listed twice, so one of the sixteen is "
                        + "missing and nothing else would say which");
            }
        }
        return bad;
    }

    /** Whether the roster is as long as two kinds in sixteen colours plus a rope. */
    public static List<String> faultsInCount(List<String> roster) {
        List<String> bad = new ArrayList<>();
        int wanted = KINDS.size() * COLOURS.size() + 1;
        if (roster.size() != wanted) {
            bad.add("the roster is " + roster.size() + " block(s) and two kinds "
                    + "in sixteen colours plus the rope is " + wanted);
        }
        return bad;
    }

    /**
     * Whether anything is in the roster twice.
     *
     * <p>Its own rule, because a roster with a duplicate in it is also a
     * roster with something missing, and a control aimed at either was
     * answered by the other.
     */
    public static List<String> faultsInDuplicates(List<String> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String one : roster) {
            if (!seen.add(one)) {
                bad.add(one + " is in the roster twice, so one of the others is "
                        + "a block that does not exist");
            }
        }
        return bad;
    }

    /** Whether every kind exists in every colour. */
    public static List<String> faultsInMissing(List<String> roster) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            for (String colour : COLOURS) {
                if (!roster.contains(colour + "_" + kind)) {
                    bad.add("there is no " + colour + " " + kind + ", so a player "
                            + "who dyes one that colour has nothing to craft");
                }
            }
        }
        return bad;
    }

    /**
     * Whether the clock is divided between the two kinds with no gap and no
     * overlap.
     *
     * <p>Handed the length of a day so a control can pass one built to be
     * caught, and so this rule cannot re-solve itself against a broken
     * constant — which is how the engine that drives a dynamo once passed a
     * check it should have failed.
     */
    public static List<String> faultsInClock(int day) {
        List<String> bad = new ArrayList<>();
        java.util.Map<String, Integer> hours = new java.util.LinkedHashMap<>();
        for (String kind : KINDS) {
            hours.put(kind, 0);
        }
        for (int tick = 0; tick < day; tick++) {
            String kind = usableAt(tick);
            if (!KINDS.contains(kind)) {
                bad.add("at tick " + tick + " the thing you may rest in is "
                        + kind + ", which is not a thing this module makes — so "
                        + "the day is covered to tick " + tick + " of " + day
                        + " and no further");
                break;
            }
            hours.merge(kind, 1, Integer::sum);
        }
        for (String kind : KINDS) {
            if (hours.get(kind) == 0 && day > 0) {
                bad.add("there is no hour of the day a " + kind + " may be "
                        + "used, so sixteen of the blocks in this module are "
                        + "decoration and nothing anywhere says so");
            }
        }
        return bad;
    }

    /**
     * Whether waking up puts you where the other kind is the one that works.
     *
     * <p>The rule that stops a hammock and a sleeping bag in one pocket being
     * a button that skips time. If you woke from a bag into the night you
     * could lie straight back down in it, and the night would be a thing
     * nobody ever saw again.
     */
    public static List<String> faultsInWaking(List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (String kind : kinds) {
            if (!KINDS.contains(kind)) {
                bad.add(kind + " is not a thing anybody can rest in, so nothing "
                        + "knows when it would wake you and it would quietly "
                        + "take whichever answer the last branch gave");
                continue;
            }
            int wakes = wakesAt(kind);
            if (wakes < 0 || wakes >= DAY) {
                bad.add("resting in a " + kind + " wakes you at tick " + wakes
                        + " and a day is " + DAY + " ticks long");
                continue;
            }
            if (usableAt(wakes).equals(kind)) {
                bad.add("resting in a " + kind + " wakes you at an hour a "
                        + kind + " may be used, so you can lie straight back "
                        + "down in it — which is a button that deletes half the "
                        + "day and reads to a player as the pack being broken");
            }
            if (skipsFrom(wakes, kind) != 0) {
                bad.add("resting in a " + kind + " at the moment it wakes you "
                        + "would skip " + skipsFrom(wakes, kind) + " ticks "
                        + "rather than none");
            }
        }
        return bad;
    }

    /** Whether every refusal is a distinct, nameable reason. */
    public static List<String> faultsInRefusals(List<String> refusals) {
        List<String> bad = new ArrayList<>();
        if (refusals.size() < 4) {
            bad.add("there are " + refusals.size() + " ways this can refuse "
                    + "you, which is fewer than the ways it can actually "
                    + "refuse you — so at least one of them is silent");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String refusal : refusals) {
            if (refusal == null || refusal.isEmpty()) {
                bad.add("one of the refusals has no name at all, so a player is "
                        + "told nothing and the block reads as broken");
                continue;
            }
            if (!seen.add(refusal)) {
                bad.add(refusal + " is two different reasons wearing one "
                        + "sentence, so a player is told why and still cannot "
                        + "tell which of the two it was");
            }
        }
        return bad;
    }
}
