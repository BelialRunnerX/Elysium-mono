package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Something that makes a sound when the wind moves it.
 *
 * <h2>The way an ambient block goes wrong</h2>
 *
 * Not by being annoying on its own. One wind chime under an eave is lovely.
 * The failure is that a player who likes it hangs eleven more, and the eleven
 * do not add up to something eleven times as pleasant — they add up to a wall
 * of noise, and on a bad client they add up to a stutter. Every ambient block
 * that has ever had to be muted got there the same way: each one rolled its
 * own dice, and nothing anywhere knew how many were rolling.
 *
 * <p>So the roll is not per block. {@link #chanceOf} is handed how many chimes
 * are within earshot and returns a chance that has already been divided by
 * them, and the claim the harness proves is that the expected number of
 * sounds a second inside {@link #EARSHOT} never exceeds
 * {@link #MOST_A_SECOND} — for one chime or for two hundred, at any strength
 * of wind. A row of them is a chord rather than a crowd.
 *
 * <h2>And why every one of them is a different note</h2>
 *
 * Because the alternative is a row of identical sounds slightly out of phase,
 * which is the single most tiring noise a game can make. Six materials, six
 * pitches, derived from where the material sits in the list rather than typed
 * beside it — so a seventh is a row and not an argument about what note is
 * still free.
 */
public final class ChimeRules {

    private ChimeRules() {
    }

    // ==================================================================
    // What they are made of
    // ==================================================================

    /** The six, in the order everything walks them, low to high. */
    public static final List<String> MATERIALS = List.of(
            "oak", "bamboo", "copper", "iron", "glass", "amethyst");

    /** What each one is made of, which has to be a thing the game has. */
    public static final Map<String, String> MADE_OF = madeOf();

    private static Map<String, String> madeOf() {
        Map<String, String> made = new LinkedHashMap<>();
        made.put("oak", "minecraft:oak_planks");
        made.put("bamboo", "minecraft:bamboo");
        made.put("copper", "minecraft:copper_ingot");
        made.put("iron", "minecraft:iron_ingot");
        made.put("glass", "minecraft:glass_pane");
        made.put("amethyst", "minecraft:amethyst_shard");
        return Map.copyOf(made);
    }

    /** Every chime there is: a material and a cord colour. */
    public static List<String> roster() {
        List<String> made = new ArrayList<>();
        for (String material : MATERIALS) {
            for (String colour : RestRules.COLOURS) {
                made.add(colour + "_" + material + "_chimes");
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // The note
    // ==================================================================

    /** The lowest note any chime plays. */
    public static final float LOWEST = 0.6F;

    /** And the highest. */
    public static final float HIGHEST = 1.8F;

    /**
     * What note this material rings at.
     *
     * <p>Derived from where it sits in {@link #MATERIALS} rather than typed,
     * so six materials are six notes spread evenly across the band and a
     * seventh re-spaces all of them rather than squeezing in beside one.
     */
    public static float pitchOf(String material) {
        int at = MATERIALS.indexOf(material);
        if (at < 0 || MATERIALS.size() < 2) {
            return LOWEST;
        }
        return LOWEST + (HIGHEST - LOWEST) * at / (MATERIALS.size() - 1.0F);
    }

    // ==================================================================
    // The wind
    // ==================================================================

    /**
     * How far away one can be heard, in blocks, measured across rather than
     * up.
     *
     * <p>Across, because a chime hangs under something and a row of them runs
     * along an eave. Counting a cube of this would be thirty-five thousand
     * block lookups; counting the two layers a row of them can be spread over
     * is under a thousand, and it is done once per sound rather than once per
     * tick — see {@link #waitOf}.
     */
    public static final int EARSHOT = 8;

    /** And how many blocks up and down that count reaches. */
    public static final int LAYERS = 1;

    /** The most sounds a second anything inside earshot may add up to. */
    public static final double MOST_A_SECOND = 2.0D;

    /** Ticks in a second. */
    public static final int A_SECOND = 20;

    /** The height at which the wind is as strong as it gets. */
    public static final int FULL_WIND = 192;

    /** And the height below which there is none at all. */
    public static final int NO_WIND = 48;

    /**
     * How much wind there is here, nought to one.
     *
     * <p>Height and sky, because those are the two things a player can see and
     * therefore the two things they can build around. A chime in a cellar is
     * silent and a chime on a tower is loud, and nobody has to be told that.
     */
    public static double windAt(int height, boolean underSky) {
        return windAt(height, underSky, NO_WIND, FULL_WIND);
    }

    /**
     * The same, with the two heights handed in.
     *
     * <p>Handed in because the rules below judge this function, and a rule
     * that reads the same constants it is checking re-solves against a broken
     * one and reports nothing — which is the fault the engine that drives a
     * dynamo was caught with, and the fauna's patience after it.
     */
    public static double windAt(int height, boolean underSky, int none, int full) {
        if (!underSky) {
            return 0.0D;
        }
        if (full <= none) {
            return height >= full ? 1.0D : 0.0D;
        }
        if (height <= none) {
            return 0.0D;
        }
        if (height >= full) {
            return 1.0D;
        }
        return (height - none) / (double) (full - none);
    }

    /**
     * The longest a chime will ever sleep before looking at the sky again.
     *
     * <p>Not the longest it goes between sounds — those are different numbers
     * and conflating them was this file's first bug. A chime in a weak wind
     * with a hundred neighbours ought to sound once every forty minutes, and
     * a block that slept forty minutes would not notice the weather changing
     * for forty minutes. So it wakes on this cadence and sounds only some of
     * the times it wakes; {@link #chanceOnWaking} is what fraction.
     */
    public static final int LONGEST_WAIT = 20 * 60;

    /**
     * How long one chime ought to go between sounds, given its company.
     *
     * <p>Multiplied by the company rather than divided, because it is a wait:
     * twice as many chimes each wait twice as long, and between them they
     * sound exactly as often as one of them would alone. Infinite in a still
     * world, which is the honest answer and the reason this returns a double.
     */
    public static double idealWait(int nearby, double wind) {
        double blowing = Math.max(0.0D, Math.min(1.0D, wind));
        if (blowing <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return A_SECOND * Math.max(1, nearby) / (blowing * MOST_A_SECOND);
    }

    /**
     * How long it actually sleeps: the wait it wants, capped so it keeps an
     * eye on the weather.
     */
    public static int waitOf(int nearby, double wind) {
        double ideal = idealWait(nearby, wind);
        if (!Double.isFinite(ideal)) {
            return LONGEST_WAIT;
        }
        return (int) Math.max(1L, Math.min(LONGEST_WAIT,
                (long) Math.ceil(ideal - 1.0E-9D)));
    }

    /**
     * The chance it sounds on one of those wakings, which is one unless the
     * cap above has shortened its sleep.
     */
    public static double chanceOnWaking(int nearby, double wind) {
        double ideal = idealWait(nearby, wind);
        if (!Double.isFinite(ideal) || ideal <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, waitOf(nearby, wind) / ideal);
    }

    /**
     * How many sounds a second that comes to, for all of them together.
     *
     * <p>Nothing at all in a still world: a chime with no wind in it is still
     * woken, but it is woken to look at the sky rather than to make a noise,
     * and a rate that counted those would be counting silences.
     */
    public static double soundsASecond(int nearby, double wind) {
        if (wind <= 0.0D) {
            return 0.0D;
        }
        int many = Math.max(1, nearby);
        return many * (double) A_SECOND * chanceOnWaking(many, wind)
                / waitOf(many, wind);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the chimes. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInMaterials(MATERIALS, MADE_OF));
        bad.addAll(faultsInNotes(MATERIALS));
        bad.addAll(faultsInBand(MATERIALS, LOWEST, HIGHEST));
        bad.addAll(faultsInWindRange(NO_WIND, FULL_WIND));
        bad.addAll(faultsInRoof(NO_WIND, FULL_WIND));
        bad.addAll(faultsInSlope(NO_WIND, FULL_WIND));
        bad.addAll(faultsInCeiling(MOST_A_SECOND));
        bad.addAll(faultsInLoudness(MOST_A_SECOND));
        bad.addAll(faultsInWaking(LONGEST_WAIT));
        bad.addAll(faultsInRoster(roster()));
        return List.copyOf(bad);
    }

    /** Whether every material is one thing, once, and made of something. */
    public static List<String> faultsInMaterials(List<String> materials,
                                                 Map<String, String> madeOf) {
        List<String> bad = new ArrayList<>();
        if (materials.size() < 2) {
            bad.add("there " + (materials.size() == 1 ? "is " : "are ")
                    + materials.size() + " material(s) of chime, and a chime "
                    + "that is the only note there is has nothing to be a chord "
                    + "with");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String material : materials) {
            if (!seen.add(material)) {
                bad.add(material + " is listed twice, so two of the notes are "
                        + "one note and a row of them is a row of the same "
                        + "sound slightly out of phase");
            }
            if (!madeOf.containsKey(material) || madeOf.get(material).isEmpty()) {
                bad.add("nothing says what a " + material + " chime is made of, "
                        + "so sixteen of them have no recipe that can fire");
            }
        }
        return bad;
    }

    /** Whether the six notes are six different notes. */
    public static List<String> faultsInNotes(List<String> materials) {
        List<String> bad = new ArrayList<>();
        Map<Float, String> heard = new LinkedHashMap<>();
        for (String material : materials) {
            String already = heard.put(pitchOf(material), material);
            if (already != null) {
                bad.add(material + " and " + already + " ring at the same note, "
                        + "so hanging one of each is the most tiring noise a "
                        + "game can make");
            }
        }
        return bad;
    }

    /**
     * Whether every note is inside the band.
     *
     * <p>Its own rule with the band handed in, because two materials that are
     * not materials both land on the same note as well as outside the band,
     * and a control aimed at either was answered by the other.
     */
    public static List<String> faultsInBand(List<String> materials,
                                            float lowest, float highest) {
        List<String> bad = new ArrayList<>();
        for (String material : materials) {
            float note = pitchOf(material);
            if (note < lowest || note > highest) {
                bad.add(material + " rings at " + note + " and the band is "
                        + lowest + " to " + highest);
            }
        }
        return bad;
    }

    /**
     * Whether the wind gets stronger as you climb rather than weaker.
     *
     * <p>Handed both ends so a control can pass a pair built to be caught.
     */
    public static List<String> faultsInWindRange(int none, int full) {
        List<String> bad = new ArrayList<>();
        if (full <= none) {
            bad.add("the wind is as strong as it gets at " + full + " and there "
                    + "is none of it at " + none + ", so every chime in the "
                    + "world is either silent or as loud as it can be and "
                    + "nothing a player builds changes it");
        }
        return bad;
    }

    /** Whether a roof is still the answer to a noisy chime. */
    public static List<String> faultsInRoof(int none, int full) {
        List<String> bad = new ArrayList<>();
        for (int height : new int[]{none, (none + full) / 2, full}) {
            if (windAt(height, false, none, full) != 0.0D) {
                bad.add("a chime under a roof at " + height + " hears the wind, "
                        + "so the one thing a player can do about a noisy chime "
                        + "does nothing");
            }
        }
        return bad;
    }

    /**
     * Whether height is something to build with rather than a switch.
     *
     * <p>Nought at the bottom, one at the top, and something strictly between
     * them in the middle.
     */
    public static List<String> faultsInSlope(int none, int full) {
        List<String> bad = new ArrayList<>();
        if (full <= none) {
            return bad;
        }
        if (windAt(none, true, none, full) != 0.0D) {
            bad.add("there is wind at the height there is meant to be none, so "
                    + "a chime in a cellar rings");
        }
        if (windAt(full, true, none, full) != 1.0D) {
            bad.add("the wind is not full at the height it is meant to be, so "
                    + "nothing anybody builds is ever as loud as the rules say");
        }
        double half = windAt((none + full) / 2, true, none, full);
        if (half <= 0.0D || half >= 1.0D) {
            bad.add("the wind goes from nothing to everything with no middle, "
                    + "so height is a switch rather than something to build "
                    + "with");
        }
        return bad;
    }

    /**
     * Whether the ceiling is a ceiling at all.
     *
     * <p>Its own rule, because a ceiling of nothing is also a ceiling every
     * room is over, and the control aimed at one was answered by the other.
     */
    public static List<String> faultsInCeiling(double ceiling) {
        List<String> bad = new ArrayList<>();
        if (ceiling <= 0.0D) {
            bad.add("chimes are allowed " + ceiling + " sounds a second between "
                    + "them, so none of them ever makes a sound at all");
        }
        return bad;
    }

    /**
     * Whether a room full of chimes is louder than one chime.
     *
     * <p>The rule this file exists for. Handed the ceiling so it cannot
     * re-solve itself against a broken one.
     */
    public static List<String> faultsInLoudness(double ceiling) {
        List<String> bad = new ArrayList<>();
        for (int many : new int[]{1, 2, 8, 64, 200}) {
            double loudest = soundsASecond(many, 1.0D);
            if (loudest > ceiling + 1.0E-9D) {
                bad.add(many + " chimes in one place come to " + loudest
                        + " sounds a second and the ceiling is " + ceiling
                        + ", so a player who likes one and hangs twelve gets a "
                        + "wall of noise and a stutter");
            }
        }
        return bad;
    }

    /**
     * Whether a chime looks at the sky often enough to notice the weather.
     *
     * <p>A chime in a weak wind with a hundred neighbours ought to sound once
     * in forty minutes. A block that slept forty minutes would not notice a
     * roof being built over it for forty minutes, which reads to a player as
     * a block that has stopped working.
     */
    public static List<String> faultsInWaking(int longest) {
        List<String> bad = new ArrayList<>();
        if (longest < 1) {
            bad.add("a chime is woken every " + longest + " ticks, which is not "
                    + "a length of time");
        }
        if (longest > 20 * 60 * 5) {
            bad.add("a chime goes " + longest + " ticks between looking at the "
                    + "sky, so a player who builds a roof over one waits "
                    + (longest / 1200) + " minutes to find out whether it "
                    + "worked");
        }
        return bad;
    }

    /** Whether there is one of every material in every colour, once. */
    public static List<String> faultsInRoster(List<String> roster) {
        List<String> bad = new ArrayList<>();
        int wanted = MATERIALS.size() * RestRules.COLOURS.size();
        if (roster.size() != wanted) {
            bad.add("there are " + roster.size() + " chime(s) and six materials "
                    + "in sixteen colours is " + wanted);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String one : roster) {
            if (!seen.add(one)) {
                bad.add(one + " is in the roster twice, so one of the others "
                        + "does not exist");
            }
        }
        for (String material : MATERIALS) {
            for (String colour : RestRules.COLOURS) {
                if (!roster.contains(colour + "_" + material + "_chimes")) {
                    bad.add("there is no " + colour + " " + material + " chime, "
                            + "so a player who dyes the cord that colour has "
                            + "nothing to craft");
                }
            }
        }
        return bad;
    }
}
