package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each beast <em>does</em>, as opposed to what it is worth.
 *
 * <h2>What was missing</h2>
 *
 * {@link BeastRules} prices nine beasts and proves the ladder; {@link
 * HauntRules} puts them somewhere a player can meet them. Between them they
 * make a bestiary that is nine rows of numbers and three sets of goals — a
 * prowler runs at you, a brute walks at you, a wight does neither in daylight
 * — so meeting a prowlframe and meeting a shearjaw are the same fight with a
 * different model on it.
 *
 * <p>That is the thing four of the jars this module is meant to replace are
 * actually for. friendsandfoes is fourteen creatures and fourteen mechanics: a
 * golem that presses buttons, a glare that shows you where the dark is, an
 * iceologer that drops a cloud on your head. Not one of those is a stat block.
 * A mob is a verb.
 *
 * <h2>Nine verbs, and where they came from</h2>
 *
 * They were not invented here. Every beast in this module already had a line
 * of flavour text written for it — the almanac prints them — and each of those
 * lines is a description of a mechanic nobody had implemented:
 *
 * <pre>
 *   spallet     "It comes apart when you hit it and the pieces keep coming."
 *   shearjaw    "Fast, and it has worked out that you are slower going uphill."
 *   prowlframe  "You will hear it stop following you. That is the bad part."
 *   drayhulk    "Too heavy to knock back and too slow to run from. Pick one."
 *   pileframe   "It goes through the wall rather than round it, every time."
 *   siegehulk   "Three metres of it. Bring the door down and leave."
 *   carrier     "It is not chasing you. It is going the same way you are."
 *   sidetone    "The noise arrives first and the cold arrives with it."
 *   nullprime   "It waited. Whatever it was waiting for, it was not you."
 * </pre>
 *
 * <p>So the roster below is a bijection: nine beasts, nine verbs, one each and
 * none spare. That is stronger than "every beast has a behaviour" on purpose —
 * a verb given to two beasts is two beasts that fight the same way, which is
 * the failure this whole file exists to make impossible, and a verb given to
 * none is a mechanic that was written, branched and never once performed.
 *
 * <h2>And how much of it, which is not typed</h2>
 *
 * How hard a behaviour hits, how far it reaches and how often it happens are
 * all worked out from what the beast already is. A behaviour with its own
 * numbers would be a second set of dials beside the first, and the two would
 * disagree the first time anybody edited one of them — which is exactly what
 * {@link BeastRules} found when it wrote its derived rewards back as literals.
 */
public final class BehaviourRules {

    private BehaviourRules() {
    }

    // ==================================================================
    // The nine verbs
    // ==================================================================

    /** Comes apart when it dies, and the pieces keep coming. */
    public static final String SPLIT = "split";

    /** Jumps the gap you were counting on. */
    public static final String LEAP = "leap";

    /** Stops dead when you look at it, and moves when you do not. */
    public static final String STALK = "stalk";

    /** Cannot be pushed, and pushes hard. */
    public static final String ANCHOR = "anchor";

    /** Goes through the wall rather than round it. */
    public static final String SUNDER = "sunder";

    /** Winds up, then crosses the room in a straight line. */
    public static final String CHARGE = "charge";

    /** Walks to where you are going to be rather than where you are. */
    public static final String DRIFT = "drift";

    /** What it hits gets slower and colder. */
    public static final String CHILL = "chill";

    /** Calls one more of its own family. */
    public static final String CALL = "call";

    /** The nine, in the order everything walks them. */
    public static final List<String> VERBS = List.of(
            SPLIT, LEAP, STALK, ANCHOR, SUNDER, CHARGE, DRIFT, CHILL, CALL);

    /** Which beast does which. */
    public static final Map<String, String> DOES = does();

    private static Map<String, String> does() {
        Map<String, String> made = new LinkedHashMap<>();
        made.put("spallet", SPLIT);
        made.put("shearjaw", LEAP);
        made.put("prowlframe", STALK);
        made.put("drayhulk", ANCHOR);
        made.put("pileframe", SUNDER);
        made.put("siegehulk", CHARGE);
        made.put("carrier", DRIFT);
        made.put("sidetone", CHILL);
        made.put("nullprime", CALL);
        return Map.copyOf(made);
    }

    /** What this beast does, or the empty string if nothing says. */
    public static String verbOf(String beast) {
        String found = DOES.get(beast);
        return found == null ? "" : found;
    }

    /** Which beast does this, or the empty string. */
    public static String whoDoes(String verb) {
        for (Map.Entry<String, String> one : DOES.entrySet()) {
            if (one.getValue().equals(verb)) {
                return one.getKey();
            }
        }
        return "";
    }

    // ==================================================================
    // How much of it
    // ==================================================================

    /** The shortest a behaviour may go between happenings, in ticks. */
    public static final int SOONEST = 40;

    /** And the longest. */
    public static final int LATEST = 200;

    /** The least a behaviour may be worth as a multiple of a hit. */
    public static final float LEAST_STRENGTH = 0.5F;

    /** And the most. */
    public static final float MOST_STRENGTH = 2.5F;

    /** The least far a behaviour reaches, in blocks. */
    public static final float LEAST_REACH = 2.0F;

    /** And the furthest. */
    public static final float MOST_REACH = 10.0F;

    /**
     * Where this beast sits between the least and the most dangerous, nought
     * to one.
     *
     * <p>Handed the roster so a control can pass one built to be caught, and
     * so a tenth beast moves every magnitude in the file by arriving rather
     * than by anybody editing a number.
     */
    public static double fraction(BeastRules.Beast beast, List<BeastRules.Beast> all) {
        double least = Double.MAX_VALUE;
        double most = 0.0D;
        for (BeastRules.Beast one : all) {
            least = Math.min(least, one.threat());
            most = Math.max(most, one.threat());
        }
        if (most - least <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (beast.threat() - least) / (most - least)));
    }

    /**
     * How often it happens.
     *
     * <p>Backwards on purpose: the more dangerous the beast, the shorter the
     * wait. A mechanic that fires more often on the thing that is already
     * hardest is what makes a tier-three fight feel like a different fight
     * rather than the same fight with more health in it.
     */
    public static int waitsOf(BeastRules.Beast beast, List<BeastRules.Beast> all) {
        return (int) Math.round(LATEST - (LATEST - SOONEST) * fraction(beast, all));
    }

    /** How hard it lands, as a multiple of what the beast hits for. */
    public static float strengthOf(BeastRules.Beast beast, List<BeastRules.Beast> all) {
        return (float) (LEAST_STRENGTH
                + (MOST_STRENGTH - LEAST_STRENGTH) * fraction(beast, all));
    }

    /**
     * How far it reaches.
     *
     * <p>Off how big the beast is rather than off how dangerous it is,
     * because reach is a thing a player judges with their eyes: something
     * three metres across that hits you from two blocks away reads as broken,
     * and the same reach on a spallet reads as a surprise.
     */
    public static float reachOf(BeastRules.Beast beast) {
        float wide = beast.width();
        return Math.max(LEAST_REACH, Math.min(MOST_REACH, LEAST_REACH + wide * 2.0F));
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the behaviours. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInCoverage(BeastRules.BEASTS, DOES));
        bad.addAll(faultsInSilence(BeastRules.BEASTS, DOES));
        bad.addAll(faultsInSpares(VERBS, DOES));
        bad.addAll(faultsInTimings(BeastRules.BEASTS, SOONEST, LATEST));
        bad.addAll(faultsInStrengths(BeastRules.BEASTS, LEAST_STRENGTH,
                MOST_STRENGTH));
        bad.addAll(faultsInReaches(BeastRules.BEASTS, LEAST_REACH, MOST_REACH));
        bad.addAll(faultsInLadder(BeastRules.BEASTS));
        bad.addAll(faultsInPace(BeastRules.BEASTS));
        return List.copyOf(bad);
    }

    /**
     * Whether every beast does exactly one thing, and no two do the same one.
     *
     * <p>Handed both sides so a control can pass a pair built to be caught.
     */
    public static List<String> faultsInCoverage(List<BeastRules.Beast> beasts,
                                                Map<String, String> does) {
        List<String> bad = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        for (BeastRules.Beast beast : beasts) {
            String verb = does.get(beast.id());
            if (verb == null || verb.isEmpty()) {
                continue;
            }
            if (!VERBS.contains(verb)) {
                bad.add(beast.id() + " does " + verb + ", which is not one of the "
                        + "nine things anything here knows how to perform, so it "
                        + "does nothing and says nothing");
                continue;
            }
            if (!taken.add(verb)) {
                bad.add(verb + " is what " + beast.id() + " does and what "
                        + "something else does too, so two of the nine are one "
                        + "fight wearing two skins");
            }
        }
        return bad;
    }

    /**
     * Whether anything is left with nothing in particular to do.
     *
     * <p>Its own rule rather than the first arm of the one above, because the
     * arm below it answered the control aimed at this one: a beast with no
     * verb also has a verb that is not one of the nine, so deleting this
     * report changed nothing anybody could see.
     */
    public static List<String> faultsInSilence(List<BeastRules.Beast> beasts,
                                               Map<String, String> does) {
        List<String> bad = new ArrayList<>();
        for (BeastRules.Beast beast : beasts) {
            String verb = does.get(beast.id());
            if (verb == null || verb.isEmpty()) {
                bad.add(beast.id() + " does nothing in particular, so meeting one "
                        + "is meeting whichever of the three family goal sets it "
                        + "happens to have — which is the fight you have already "
                        + "had, with a different model on it");
            }
        }
        return bad;
    }

    /**
     * Whether any verb was written and given to nothing.
     *
     * <p>Its own rule rather than a second arm of the one above, because a
     * spare verb and a missing one are two different mistakes and a control
     * aimed at either would otherwise be answered by the other.
     */
    public static List<String> faultsInSpares(List<String> verbs,
                                              Map<String, String> does) {
        List<String> bad = new ArrayList<>();
        for (String verb : verbs) {
            boolean anybody = false;
            for (String doing : does.values()) {
                anybody |= doing.equals(verb);
            }
            if (!anybody) {
                bad.add(verb + " is a thing nothing does, so it is a mechanic "
                        + "that was written, given a branch to happen in, and "
                        + "will never once be performed");
            }
        }
        for (String beast : does.keySet()) {
            boolean real = false;
            for (BeastRules.Beast one : BeastRules.BEASTS) {
                real |= one.id().equals(beast);
            }
            if (!real) {
                bad.add(beast + " is given something to do and is not a beast");
            }
        }
        return bad;
    }

    /**
     * Whether every wait is one a player would notice and live through.
     *
     * <p>Handed the two ends rather than reading them, because {@link
     * #waitsOf} is worked out <em>between</em> them: a rule that read the same
     * two constants would re-solve against a broken one and report nothing,
     * which is the fault this module has already been caught with twice.
     */
    public static List<String> faultsInTimings(List<BeastRules.Beast> beasts,
                                               int soonest, int latest) {
        List<String> bad = new ArrayList<>();
        for (BeastRules.Beast beast : beasts) {
            int waits = waitsOf(beast, beasts);
            if (waits < soonest) {
                bad.add(beast.id() + " does what it does every " + waits
                        + " ticks, which is under " + soonest + " — a mechanic "
                        + "that never stops happening is not a mechanic, it is "
                        + "the weather");
            }
            if (waits > latest) {
                bad.add(beast.id() + " does what it does every " + waits
                        + " ticks, which is over " + latest + " — most fights "
                        + "are over before that, so it may as well not have one");
            }
        }
        return bad;
    }

    /** Whether every strength is inside its band, which is handed in. */
    public static List<String> faultsInStrengths(List<BeastRules.Beast> beasts,
                                                 float least, float most) {
        List<String> bad = new ArrayList<>();
        for (BeastRules.Beast beast : beasts) {
            float hard = strengthOf(beast, beasts);
            if (hard < least || hard > most) {
                bad.add(beast.id() + "'s mechanic lands at " + hard + " times a "
                        + "hit and the band is " + least + " to " + most);
            }
        }
        return bad;
    }

    /** Whether every reach is one a player can see coming. */
    public static List<String> faultsInReaches(List<BeastRules.Beast> beasts,
                                              float least, float most) {
        List<String> bad = new ArrayList<>();
        for (BeastRules.Beast beast : beasts) {
            float reach = reachOf(beast);
            if (reach < least || reach > most) {
                bad.add(beast.id() + " reaches " + reach + " blocks and the band "
                        + "is " + least + " to " + most);
            }
            if (reach < beast.width()) {
                bad.add(beast.id() + " is " + beast.width() + " blocks across and "
                        + "reaches " + reach + ", so its mechanic cannot touch "
                        + "anything standing against it");
            }
        }
        return bad;
    }

    /**
     * Whether a harder beast's mechanic is ever weaker than an easier one's.
     *
     * <p>Within a family and up the tiers, which is the ladder a player
     * actually climbs. A tier-three beast whose mechanic happens less often
     * and lands softer than its tier-one family-mate's is a beast that is
     * harder on paper and easier in the room, and that is the oldest mistake
     * in this module — {@link BeastRules} was written twice before it stopped
     * making it with rewards.
     */
    public static List<String> faultsInLadder(List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (List<BeastRules.Beast> rungs : ladders(beasts)) {
            for (int at = 1; at < rungs.size(); at++) {
                BeastRules.Beast below = rungs.get(at - 1);
                BeastRules.Beast above = rungs.get(at);
                if (strengthOf(above, beasts) <= strengthOf(below, beasts)) {
                    bad.add(above.id() + "'s mechanic lands no harder than "
                            + below.id() + "'s and it is a tier above it, so "
                            + "climbing the " + above.family() + " ladder buys a "
                            + "bigger health bar and nothing else");
                }
            }
        }
        return bad;
    }

    /**
     * And whether a harder beast's mechanic happens less often than an easier
     * one's.
     *
     * <p>Separate from the strength above, and not because the two are
     * unrelated — they are both monotone in the same number, which is exactly
     * why. A control aimed at either was answered by the other, so deleting
     * one of them reported nothing and passed.
     */
    public static List<String> faultsInPace(List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (List<BeastRules.Beast> rungs : ladders(beasts)) {
            for (int at = 1; at < rungs.size(); at++) {
                BeastRules.Beast below = rungs.get(at - 1);
                BeastRules.Beast above = rungs.get(at);
                if (waitsOf(above, beasts) >= waitsOf(below, beasts)) {
                    bad.add(above.id() + " does what it does no more often than "
                            + below.id() + " and it is a tier above it");
                }
            }
        }
        return bad;
    }

    /** Every family, in tier order. */
    private static List<List<BeastRules.Beast>> ladders(List<BeastRules.Beast> beasts) {
        List<List<BeastRules.Beast>> made = new ArrayList<>();
        for (String family : BeastRules.FAMILIES) {
            List<BeastRules.Beast> rungs = new ArrayList<>();
            for (BeastRules.Beast beast : beasts) {
                if (beast.family().equals(family)) {
                    rungs.add(beast);
                }
            }
            rungs.sort((first, second) -> Integer.compare(first.tier(), second.tier()));
            made.add(rungs);
        }
        return made;
    }
}
