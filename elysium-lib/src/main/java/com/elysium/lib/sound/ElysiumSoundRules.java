package com.elysium.lib.sound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every sound the pack plays, as numbers.
 *
 * <p>No sample is recorded or downloaded. A voice is a description — a pitch,
 * a timbre, how much growl, how long it holds — and {@code art/sound.py}
 * synthesises three clips from it (idle, hurt, death), the way every sprite is
 * drawn from rows. A hum is what a running machine sounds like, looped. A
 * click is what a station says when it opens. {@code tools/gen_sounds.py}
 * reads this table off the source and writes the {@code .ogg} files and
 * {@code sounds.json}; {@link ElysiumSounds} registers one event per clip
 * from the same rows; the validator holds the three to one another.
 *
 * <p>Pure: no game classes, so {@code SoundHarness} can run it on the JDK
 * alone and prove the table is sane before a single clip is rendered.
 */
public final class ElysiumSoundRules {

    private ElysiumSoundRules() {
    }

    /** The timbres {@code art/sound.py} knows. */
    public static final String THROAT = "throat";
    public static final String HISS = "hiss";
    public static final String CHIRP = "chirp";
    public static final String DRONE = "drone";
    public static final String METAL = "metal";
    public static final List<String> TIMBRES = List.of(THROAT, HISS, CHIRP, DRONE, METAL);

    /** The hums {@code art/sound.py} knows. */
    public static final List<String> HUM_KINDS = List.of(
            "shaft", "steam", "diesel", "electric", "reactor", "roar", "whine", "pump");

    /** The clicks {@code art/sound.py} knows. */
    public static final List<String> CLICK_KINDS = List.of("station", "snap", "switch");

    /** The three clips a voice has, in the order the files are named. */
    public static final List<String> CALLS = List.of("ambient", "hurt", "death");

    /**
     * A creature's voice.
     *
     * @param id           the voice's name; a creature names one of these
     * @param pitch        the fundamental, Hz
     * @param timbre       one of {@link #TIMBRES}
     * @param growl        0..1, soft clipping on the throat
     * @param breath       0..1, noise under the tone
     * @param length       seconds the idle call holds; hurt is shorter, death longer
     * @param vibratoRate  Hz of wobble on the pitch
     * @param vibratoDepth the wobble's depth as a share of the pitch
     * @param body         0..1, how much room the voice is heard in
     * @param seed         the noise's seed; two voices alike in every number still differ by it
     */
    public record Voice(String id, double pitch, String timbre, double growl, double breath,
                        double length, double vibratoRate, double vibratoDepth, double body, int seed) {
    }

    /** A running machine, looped. */
    public record Hum(String id, String kind, double pitch, int seed) {
    }

    /** A station opening, a plate snapping on, a thing switching. */
    public record Click(String id, String kind, double pitch, int seed) {
    }

    private static Voice voice(String id, double pitch, String timbre, double growl, double breath,
                               double length, double vibratoRate, double vibratoDepth, double body, int seed) {
        return new Voice(id, pitch, timbre, growl, breath, length, vibratoRate, vibratoDepth, body, seed);
    }

    /**
     * The voices, by the kind that wears each. The bestiary's kin, court,
     * denizens, wards, myths and companions speak with the voice of the form
     * they borrow, so a wight-shaped ward sounds like a wight.
     */
    public static final List<Voice> VOICES = List.of(
            // The bestiary's beasts, by family.
            voice("prowler", 140.0, THROAT, 0.35, 0.20, 0.80, 5.0, 0.020, 0.20, 11),
            voice("brute", 70.0, THROAT, 0.60, 0.15, 1.10, 3.5, 0.015, 0.50, 12),
            voice("wight", 190.0, METAL, 0.10, 0.30, 1.00, 6.0, 0.030, 0.60, 13),
            // The animals, by kind.
            voice("grazer", 160.0, THROAT, 0.05, 0.10, 0.70, 4.0, 0.010, 0.10, 21),
            voice("forager", 520.0, CHIRP, 0.00, 0.10, 0.35, 8.0, 0.040, 0.05, 22),
            voice("fowl", 900.0, CHIRP, 0.00, 0.05, 0.30, 9.0, 0.060, 0.05, 23),
            voice("swimmer", 300.0, HISS, 0.00, 0.50, 0.30, 0.0, 0.000, 0.30, 24),
            // The shade, and anything else that floats.
            voice("floater", 95.0, DRONE, 0.00, 0.20, 1.40, 2.0, 0.010, 0.80, 31),
            // The eight, by their ids, and the two that came after.
            voice("scavenger", 240.0, HISS, 0.00, 0.40, 0.60, 0.0, 0.000, 0.20, 41),
            voice("reaver", 110.0, THROAT, 0.70, 0.25, 0.90, 4.0, 0.020, 0.30, 42),
            voice("whisper", 400.0, DRONE, 0.00, 0.60, 1.20, 3.0, 0.030, 0.70, 43),
            voice("drone", 220.0, DRONE, 0.00, 0.05, 0.80, 30.0, 0.010, 0.10, 44),
            voice("lictor", 130.0, METAL, 0.30, 0.15, 0.90, 5.0, 0.015, 0.40, 45),
            voice("adept", 300.0, DRONE, 0.00, 0.20, 1.00, 5.0, 0.020, 0.50, 46),
            voice("harrier", 700.0, CHIRP, 0.10, 0.10, 0.40, 10.0, 0.050, 0.10, 47),
            voice("bulwark", 55.0, THROAT, 0.50, 0.20, 1.30, 3.0, 0.010, 0.60, 48),
            voice("choir", 260.0, DRONE, 0.00, 0.10, 1.60, 6.0, 0.040, 0.70, 49),
            voice("praetor", 90.0, METAL, 0.40, 0.20, 1.20, 4.0, 0.020, 0.50, 50),
            // The comforts' pigeon, the agronomy's bees and its fish by water.
            voice("pigeon", 650.0, CHIRP, 0.00, 0.10, 0.30, 12.0, 0.050, 0.05, 61),
            voice("bee", 190.0, DRONE, 0.00, 0.05, 0.60, 40.0, 0.020, 0.05, 62),
            voice("fish_fresh", 350.0, HISS, 0.00, 0.60, 0.25, 0.0, 0.000, 0.20, 63),
            voice("fish_salt", 280.0, HISS, 0.00, 0.60, 0.30, 0.0, 0.000, 0.30, 64),
            voice("fish_lava", 90.0, THROAT, 0.80, 0.50, 0.50, 3.0, 0.020, 0.40, 65),
            voice("fish_void", 500.0, DRONE, 0.00, 0.30, 0.70, 2.0, 0.020, 0.80, 66),
            // The space worlds' mobs.
            voice("moonfolk", 380.0, CHIRP, 0.00, 0.15, 0.50, 6.0, 0.030, 0.20, 71),
            voice("hollow_moonfolk", 120.0, THROAT, 0.40, 0.40, 0.90, 4.0, 0.030, 0.30, 72),
            voice("void_crawler", 200.0, HISS, 0.00, 0.60, 0.50, 0.0, 0.000, 0.30, 73),
            voice("frost_raptor", 180.0, THROAT, 0.30, 0.20, 0.70, 7.0, 0.030, 0.20, 74),
            voice("cinder_boar", 80.0, THROAT, 0.60, 0.20, 0.60, 3.0, 0.010, 0.30, 75),
            voice("cinderling", 230.0, THROAT, 0.30, 0.15, 0.50, 5.0, 0.020, 0.20, 76),
            voice("cinderling_brute", 100.0, THROAT, 0.60, 0.20, 0.80, 3.5, 0.015, 0.40, 77),
            // The empire's people.
            voice("envoy", 300.0, CHIRP, 0.00, 0.20, 0.50, 5.0, 0.020, 0.30, 81),
            voice("enforcer", 120.0, THROAT, 0.50, 0.30, 0.80, 4.0, 0.020, 0.40, 82));

    /** The hums, one per engine class; a module names one for each of its machines. */
    public static final List<Hum> HUMS = List.of(
            new Hum("shaft", "shaft", 110.0, 101),
            new Hum("steam", "steam", 90.0, 102),
            new Hum("diesel", "diesel", 55.0, 103),
            new Hum("electric", "electric", 60.0, 104),
            new Hum("reactor", "reactor", 40.0, 105),
            new Hum("roar", "roar", 70.0, 106),
            new Hum("whine", "whine", 220.0, 107),
            new Hum("pump", "pump", 45.0, 108));

    /** The clicks. */
    public static final List<Click> CLICKS = List.of(
            new Click("station", "station", 1800.0, 201),
            new Click("snap", "snap", 2400.0, 202),
            new Click("switch", "switch", 1200.0, 203));

    /** How often a hum is played again while a machine runs, in ticks: just under the loop's length. */
    public static final int HUM_EVERY = 36;

    /** How loud a hum is played, and how loud a voice. */
    public static final float HUM_VOLUME = 0.55F;
    public static final float VOICE_VOLUME = 1.0F;

    /** The voice with this id, or null. */
    public static Voice voice(String id) {
        for (Voice one : VOICES) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The hum with this id, or null. */
    public static Hum hum(String id) {
        for (Hum one : HUMS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The click with this id, or null. */
    public static Click click(String id) {
        for (Click one : CLICKS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /**
     * Everything wrong with the table, in words; empty when nothing is.
     *
     * <p>The harness prints these and fails on any. Pitches are held to what a
     * game speaker can carry and a player can hear as a creature rather than a
     * tone; lengths to what an idle call can be without becoming a song.
     */
    public static List<String> faults() {
        List<String> faults = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Voice one : VOICES) {
            if (!ids.add(one.id())) {
                faults.add("two voices are called " + one.id());
            }
            if (!one.id().matches("[a-z][a-z0-9_]*")) {
                faults.add("the voice " + one.id() + " is not a name a sound event can carry");
            }
            if (!TIMBRES.contains(one.timbre())) {
                faults.add(one.id() + " has a timbre nothing can synthesise: " + one.timbre());
            }
            if (one.pitch() < 30.0 || one.pitch() > 2000.0) {
                faults.add(one.id() + " is pitched at " + one.pitch() + " Hz, outside 30..2000");
            }
            if (one.growl() < 0.0 || one.growl() > 1.0 || one.breath() < 0.0 || one.breath() > 1.0
                    || one.body() < 0.0 || one.body() > 1.0) {
                faults.add(one.id() + " has a growl, breath or body outside 0..1");
            }
            if (one.length() < 0.2 || one.length() > 2.0) {
                faults.add(one.id() + " holds its call for " + one.length() + " s, outside 0.2..2");
            }
            if (one.vibratoRate() < 0.0 || one.vibratoRate() > 60.0 || one.vibratoDepth() < 0.0
                    || one.vibratoDepth() > 0.2) {
                faults.add(one.id() + " wobbles outside what reads as a voice");
            }
        }
        Set<String> seeds = new HashSet<>();
        for (Voice one : VOICES) {
            if (!seeds.add(String.valueOf(one.seed()))) {
                faults.add("two voices share the seed " + one.seed() + ", so their breath is the same breath");
            }
        }
        ids.clear();
        for (Hum one : HUMS) {
            if (!ids.add(one.id())) {
                faults.add("two hums are called " + one.id());
            }
            if (!HUM_KINDS.contains(one.kind())) {
                faults.add(one.id() + " is a hum of a kind nothing can synthesise: " + one.kind());
            }
            if (one.pitch() < 20.0 || one.pitch() > 1000.0) {
                faults.add(one.id() + " hums at " + one.pitch() + " Hz, outside 20..1000");
            }
        }
        ids.clear();
        for (Click one : CLICKS) {
            if (!ids.add(one.id())) {
                faults.add("two clicks are called " + one.id());
            }
            if (!CLICK_KINDS.contains(one.kind())) {
                faults.add(one.id() + " is a click of a kind nothing can synthesise: " + one.kind());
            }
        }
        if (HUM_EVERY < 20 || HUM_EVERY > 40) {
            faults.add("a hum is replayed every " + HUM_EVERY + " ticks, which either overlaps itself or falls silent");
        }
        return faults;
    }
}
