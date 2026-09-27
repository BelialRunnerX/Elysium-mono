package com.elysium.lib.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A run is a line of one kind of block that carries something: a pipe, a
 * cable, a wire, a conduit, a bus, a coolant channel. These are the numbers
 * every run in the pack shares, and the one rule about who joins whom.
 *
 * <h2>The plate</h2>
 *
 * Dylan, 26 September 2026: <i>when pipes connect to a block they need to
 * have a large flat square in the cube face touching a block, coloured to
 * match the pipe or cable, which is a visual cue for the player that it is
 * working.</i> So a run's face against a device (anything it feeds that is
 * not another run) carries a {@link #PLATE}-pixel square, {@link #PLATE_DEPTH}
 * deep, in the run's colour. A face against another run carries an arm and no
 * plate; a face against nothing carries nothing.
 *
 * <h2>The dye</h2>
 *
 * Same day: <i>dyed pipes and cables only connect to their own colour, undyed
 * ones, or a block.</i> A run is undyed or one of the sixteen dyes. Two runs
 * join when either is undyed or both are the same dye; a run joins a device
 * whatever its dye. The plate and the body take the dye's colour; an undyed
 * run's plate takes the run's own colour, so a copper wire's plates are
 * copper and a pipe's are steel.
 *
 * <h2>Why the numbers live here</h2>
 *
 * Six modules draw runs. A plate that is ten pixels on the pipe and eight on
 * the wire is two visual languages for one idea, and a player who has learned
 * one will read the other as broken. The generators read these constants off
 * this file, so the models cannot disagree with the rule.
 */
public final class RunRules {

    private RunRules() {
    }

    /** The run's cross-section, from and to, in pixels of the block: 6 wide, centred. */
    public static final int ARM_FROM = 5;
    public static final int ARM_TO = 11;

    /** The plate's side, in pixels. Large: it is the cue, not a detail. */
    public static final int PLATE = 10;

    /** How far the plate stands off the device's face, in pixels. */
    public static final int PLATE_DEPTH = 1;

    /** Where the plate starts on the face, so it sits centred. */
    public static final int PLATE_FROM = (16 - PLATE) / 2;
    public static final int PLATE_TO = PLATE_FROM + PLATE;

    /** The undyed run, as its name reads in a component or a tag. */
    public static final String UNDYED = "";

    /** The sixteen dyes by the game's names, and the colour each paints. */
    public static final Map<String, Integer> DYE_RGB = Map.ofEntries(
            Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D),
            Map.entry("magenta", 0xC74EBD), Map.entry("light_blue", 0x3AB3DA),
            Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52),
            Map.entry("light_gray", 0x9D9D97), Map.entry("cyan", 0x169C9C),
            Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16),
            Map.entry("red", 0xB02E26), Map.entry("black", 0x1D1D21));

    /** The tint index the body's faces carry: white when undyed, the dye when dyed. */
    public static final int BODY_TINT = 0;

    /** The tint index the plate carries: the run's own colour when undyed, the dye when dyed. */
    public static final int PLATE_TINT = 1;

    /** Whether a dye name is one a run may wear. */
    public static boolean isDye(String dye) {
        return dye != null && !dye.isEmpty() && DYE_RGB.containsKey(dye);
    }

    /**
     * Whether two runs of the same kind join across a face.
     *
     * @param a one run's dye, or {@link #UNDYED}
     * @param b the other's
     */
    public static boolean mayJoin(String a, String b) {
        String da = a == null ? UNDYED : a;
        String db = b == null ? UNDYED : b;
        return da.isEmpty() || db.isEmpty() || da.equals(db);
    }

    /** The colour the body's faces are tinted: white for an undyed run, the dye otherwise. */
    public static int bodyTint(String dye) {
        return isDye(dye) ? DYE_RGB.get(dye) : 0xFFFFFF;
    }

    /** The colour the plate is tinted: the run's own colour for an undyed run, the dye otherwise. */
    public static int plateTint(String dye, int own) {
        return isDye(dye) ? DYE_RGB.get(dye) : own;
    }

    /** Everything wrong with these numbers, for the harness. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        if (PLATE < 8 || PLATE > 14) {
            bad.add("a " + PLATE + "-pixel plate is not the large square asked for, or is the whole face");
        }
        if (PLATE_FROM < 1 || PLATE_TO > 15) {
            bad.add("the plate runs off the face");
        }
        if (PLATE_DEPTH < 1 || PLATE_DEPTH > 2) {
            bad.add("a plate " + PLATE_DEPTH + " deep is either invisible or a block of its own");
        }
        if (ARM_FROM >= ARM_TO || ARM_TO - ARM_FROM < 4 || ARM_TO - ARM_FROM > 10) {
            bad.add("the arm's cross-section is not a run: " + ARM_FROM + ".." + ARM_TO);
        }
        if (PLATE <= ARM_TO - ARM_FROM) {
            bad.add("the plate is no wider than the arm, so it reads as the arm's end and not as a cue");
        }
        if (DYE_RGB.size() != 16) {
            bad.add("the game has sixteen dyes; this table has " + DYE_RGB.size());
        }
        if (!mayJoin(UNDYED, "red") || !mayJoin("red", UNDYED) || !mayJoin("red", "red")
                || mayJoin("red", "blue")) {
            bad.add("the dye rule is not: undyed joins anything, a dye joins itself");
        }
        if (bodyTint(UNDYED) != 0xFFFFFF || plateTint(UNDYED, 0xB87333) != 0xB87333
                || plateTint("red", 0xB87333) != DYE_RGB.get("red")) {
            bad.add("an undyed run does not keep its own colours, or a dyed one does not take the dye's");
        }
        return bad;
    }
}
