package com.elysium.space.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a waygate, and what it costs to use.
 *
 * <h2>The problem this solves</h2>
 *
 * Five worlds, and the only way between them is a rocket that has to be fuelled,
 * boarded, flown and landed. That is the right weight for the <em>first</em>
 * trip — arriving somewhere should be an event — and it is the wrong weight for
 * the ninetieth, when a player simply wants to go and fetch something from a
 * base they have already built. A journey that was a story the first time is an
 * errand by the tenth, and a game that charges the same for both trains players
 * to stop going.
 *
 * <h2>What it costs</h2>
 *
 * Nothing, and that is deliberate. The gate is paid for once, in the building
 * of it, and then it is furniture. No fuel, no charge, no wear, no cooldown
 * worth the name — a system that asks a player to think about upkeep every time
 * they use it is a system they will avoid, which is the opposite of the point.
 *
 * <h2>What balances it</h2>
 *
 * Not a cost: a topology. A gate can only reach <em>another gate</em>, so the
 * first trip to any world is still a rocket, and the network a player ends up
 * with is exactly a map of where they have actually been and thought worth
 * coming back to. Nothing here enforces that — it falls out of the rule that
 * both ends must exist.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class GateRules {

    private GateRules() {
    }

    // ------------------------------------------------------------------
    // The ring
    // ------------------------------------------------------------------

    /**
     * How wide and tall the ring is, counting the frame.
     *
     * Five. A three would have a one-block hole nobody could see through and
     * would read as a decoration; a seven is a wall. Five leaves a three-by-
     * three opening, which is a doorway a player walks at rather than aims at.
     */
    public static final int SIZE = 5;

    /** The opening inside it. */
    public static final int OPENING = SIZE - 2;

    /**
     * How many blocks the outline is.
     *
     * The perimeter of a five-by-five square: sixteen. One of them is the
     * anchor, so fifteen are frame.
     */
    public static int perimeter() {
        return 4 * SIZE - 4;
    }

    /** Frame blocks needed, the anchor not counted. */
    public static int frameBlocks() {
        return perimeter() - 1;
    }

    /** Where the anchor sits in the ring: the middle of the bottom edge. */
    public static int anchorAcross() {
        return SIZE / 2;
    }

    /**
     * One position on the ring, relative to the anchor.
     *
     * The anchor is the origin, so everything is measured from the bottom
     * centre: {@code across} runs left and right along the gate's plane and
     * {@code up} runs upward. A ring is walked as sixteen of these, and the
     * one that comes out {@code (0, 0)} is the anchor's own square.
     *
     * @param across sideways from the anchor, negative to its left
     * @param up     height above the anchor
     */
    public record Offset(int across, int up) {

        /** Whether this is where the anchor itself stands. */
        public boolean isAnchor() {
            return across == 0 && up == 0;
        }
    }

    /**
     * Every square of the ring, anchor included, in a fixed order.
     *
     * Bottom edge left to right, then up the right side, then across the top
     * right to left, then down the left side — so a partially built gate is
     * reported as missing the blocks a builder would notice next.
     */
    public static List<Offset> ring() {
        List<Offset> out = new ArrayList<>();
        int half = SIZE / 2;
        for (int across = -half; across <= half; across++) {
            out.add(new Offset(across, 0));
        }
        for (int up = 1; up < SIZE - 1; up++) {
            out.add(new Offset(half, up));
        }
        for (int across = half; across >= -half; across--) {
            out.add(new Offset(across, SIZE - 1));
        }
        for (int up = SIZE - 2; up >= 1; up--) {
            out.add(new Offset(-half, up));
        }
        return List.copyOf(out);
    }

    /** Every square that must be frame — the ring without the anchor's own. */
    public static List<Offset> frame() {
        List<Offset> out = new ArrayList<>();
        for (Offset at : ring()) {
            if (!at.isAnchor()) {
                out.add(at);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Every square that must be clear.
     *
     * The opening. A gate with a block in the middle of it is a gate a player
     * built round something and then wondered why it did not work, so it is
     * checked and reported rather than ignored.
     */
    public static List<Offset> opening() {
        List<Offset> out = new ArrayList<>();
        int half = SIZE / 2;
        for (int across = -half + 1; across <= half - 1; across++) {
            for (int up = 1; up < SIZE - 1; up++) {
                out.add(new Offset(across, up));
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------
    // Arriving
    // ------------------------------------------------------------------

    /**
     * How far above the anchor a traveller appears.
     *
     * One. The anchor is a solid block and the square above it is the bottom
     * of the opening, which is where somebody walking through a doorway would
     * be. Arriving inside the gate rather than in front of it also means the
     * arrival is unambiguous however the gate is oriented.
     */
    public static final int ARRIVAL_UP = 1;

    /**
     * Ticks before the same gate will answer again.
     *
     * Four — a fifth of a second. This is not a cooldown in the sense of a
     * cost; it exists because a right-click that opens a screen can arrive
     * twice on a laggy server, and travelling twice is worse than waiting a
     * fifth of a second. Anything longer would be a toll, and the gate does
     * not charge tolls.
     */
    public static final int SETTLE = 4;

    /** Whether the gate will answer a click at this moment. */
    public static boolean ready(long now, long lastUsed) {
        return now - lastUsed >= SETTLE;
    }

    // ------------------------------------------------------------------
    // Names
    // ------------------------------------------------------------------

    /** The longest a gate's name may be, so the list stays readable. */
    public static final int NAME_LIMIT = 24;

    /** A name trimmed to fit, or a fallback when it is empty. */
    public static String nameOr(String given, String fallback) {
        String trimmed = given == null ? "" : given.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() <= NAME_LIMIT ? trimmed
                : trimmed.substring(0, NAME_LIMIT);
    }
}
