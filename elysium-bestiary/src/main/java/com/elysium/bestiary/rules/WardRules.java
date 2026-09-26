package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thirteen creatures that exist to be fought with particular gear, and the
 * proof that none of them is a gear check.
 *
 * <h2>The failure this file is about</h2>
 *
 * A mod adds an armour set and a creature the set is good against. That is a
 * fine thing to do and it is how most of a pack's middle hours get their shape.
 * It goes wrong in two directions, and both of them look like content while
 * they are happening.
 *
 * <p>Too far one way and the creature is <b>a gear check</b>: without the right
 * set it takes the player apart, with it the fight is over before it starts.
 * Nothing about it is a fight — it is a door, and the key is an inventory slot.
 * The player who has the set does not notice, and the player who does not is
 * simply stopped, which reads as difficulty.
 *
 * <p>Too far the other way and the gear is <b>decoration</b>: the set makes no
 * difference anybody can feel, so the creature is one more thing to hit and the
 * armour is a texture. This is the commoner of the two, because it is the one
 * that never generates a complaint.
 *
 * <h2>The band</h2>
 *
 * Every creature here names one {@link Ward} — the kind of protection that
 * helps against it — and two numbers: what it does to somebody without that
 * protection, and what it does to somebody with it. The ratio between them has
 * to land between {@link #LEAST_LEFT} and {@link #MOST_LEFT}.
 *
 * <p>Below the floor the creature is a door. Above the ceiling the gear is a
 * texture. Neither end is visible from inside a single fight, which is why the
 * arithmetic is here rather than in somebody's judgement.
 *
 * <h2>And one ward is not the answer to all of them</h2>
 *
 * The other way this roster could quietly collapse is for one kind of
 * protection to cover most of it, at which point five of the six wards are
 * decoration again and the player has one decision instead of six.
 * {@link #faultsInSpread} holds any single ward to {@link #MOST_COVERED} of the
 * roster — and it takes the roster in, because the number that matters is how
 * many creatures a player meets that one set answers, not how many wards were
 * declared.
 */
public final class WardRules {

    private WardRules() {
    }

    // ==================================================================
    // The six kinds of protection
    // ==================================================================

    /** It burns, and what helps is not being flammable. */
    public static final String FLAME = "flame";

    /** It hits from range, and what helps is what stops an arrow. */
    public static final String SHOT = "shot";

    /** It goes off, and what helps is what spreads a blow out. */
    public static final String BLAST = "blast";

    /** It leaves something in you, and what helps is what refuses it. */
    public static final String ROT = "rot";

    /** It takes the warmth, and what helps is what keeps it. */
    public static final String FROST = "frost";

    /** It works on you rather than on your armour, and what helps is charmed. */
    public static final String CURSE = "curse";

    /** The six, in the order everything walks them. */
    public static final List<String> WARDS = List.of(
            FLAME, SHOT, BLAST, ROT, FROST, CURSE);

    /**
     * One creature and the gear it is about.
     *
     * @param id      what it is called
     * @param ward    the kind of protection that helps against it
     * @param answer  something the pack really makes that carries that ward
     * @param tier    which rung of the beasts' ladder it stands on
     * @param health  what it can take
     * @param bite    what it does to somebody without the ward
     * @param blunted what it does to somebody with it
     * @param speed   how fast it closes
     * @param weight  how often it is chosen when something spawns
     */
    public record Warded(String id, String ward, String answer, int tier,
                         double health, double bite, double blunted,
                         double speed, int weight) {

        public Warded {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a creature with no name");
            }
            if (ward == null || ward.isEmpty()) {
                throw new IllegalArgumentException(id + " is not about any kind of "
                        + "protection, so nothing says what fighting it is for");
            }
            if (answer == null || answer.isEmpty()) {
                throw new IllegalArgumentException(id + " names nothing that carries "
                        + "its ward, so what the player is told to bring is a word");
            }
            if (health <= 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health and " + speed + " speed, and a creature with "
                        + "neither is not a creature");
            }
            if (bite < 0.0D || blunted < 0.0D) {
                throw new IllegalArgumentException(id + " heals what it hits");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " is chosen " + weight
                        + " times when something spawns, so it is a creature "
                        + "nobody ever meets");
            }
            if (tier < 1) {
                throw new IllegalArgumentException(id + " stands on tier " + tier
                        + ", which is below the bottom of the ladder");
            }
        }

        /** How dangerous it is to somebody with nothing on. */
        public double threat() {
            return health * (1.0D + bite) * speed / 0.25D;
        }

        /** And to somebody carrying the right thing. */
        public double blunt() {
            return health * (1.0D + blunted) * speed / 0.25D;
        }

        /**
         * How much of the fight the right gear leaves.
         *
         * <p>One means the gear did nothing. Zero means the gear did everything.
         * The whole of this file is about keeping that number away from both.
         */
        public double left() {
            return threat() <= 0.0D ? 1.0D : blunt() / threat();
        }
    }

    /**
     * The least of a fight the right gear may leave.
     *
     * <p>A bit over a third. Below that the creature is a door and the gear is
     * the key, and the player who has it never finds out there was a fight —
     * which is exactly as bad as the player who does not having no fight
     * either, and much harder to notice.
     */
    public static final double LEAST_LEFT = 0.35D;

    /**
     * And the most.
     *
     * <p>Four fifths. Above that the set makes no difference anybody can feel,
     * and the armour is a texture with a tooltip. This is the commoner failure,
     * because it is the one nobody ever complains about.
     */
    public static final double MOST_LEFT = 0.80D;

    /**
     * How many of the roster one kind of protection may answer.
     *
     * <p>Three of thirteen. Past that the player has one decision — which set
     * to wear — rather than six, and the other five wards are a list in a
     * language file.
     */
    public static final int MOST_COVERED = 3;

    /** The rarest anything here may be. */
    public static final int LEAST_WEIGHT = 8;

    /**
     * The thirteen.
     *
     * <p>Read the last two numbers of each row together: what it does to you
     * with nothing on, and what it does to you with the right thing on. Every
     * pair in this table lands between a third and four fifths, which is the
     * entire claim.
     */
    public static final List<Warded> WARDED = List.of(
            //          id               ward   answer                        tier  hp    bite  blunted speed  wt
            new Warded("scurf", ROT, "minecraft:golden_carrot", 1, 16.0D, 3.0D, 2.0D, 0.34D, 55),
            new Warded("slinger", SHOT, "minecraft:shield", 1, 14.0D, 3.5D, 2.0D, 0.36D, 50),
            new Warded("canker", ROT, "minecraft:milk_bucket", 1, 22.0D, 2.5D, 1.5D, 0.22D, 40),
            new Warded("residue", CURSE, "minecraft:totem_of_undying", 1, 12.0D, 4.0D, 2.0D, 0.30D, 38),

            new Warded("cinderling", FLAME, "minecraft:water_bucket", 2, 26.0D, 5.0D, 3.0D, 0.32D, 30),
            new Warded("sludge", ROT, "minecraft:honey_bottle", 2, 34.0D, 5.5D, 3.5D, 0.26D, 26),
            new Warded("pyrelet", FLAME, "minecraft:magma_cream", 2, 22.0D, 6.0D, 3.0D, 0.34D, 22),
            new Warded("charge", BLAST, "minecraft:iron_chestplate", 2, 18.0D, 8.0D, 3.5D, 0.30D, 24),
            new Warded("masker", CURSE, "minecraft:golden_apple", 2, 30.0D, 5.0D, 3.0D, 0.30D, 20),

            new Warded("cinderdam", FLAME, "minecraft:potion", 3, 48.0D, 8.0D, 5.0D, 0.28D, 12),
            new Warded("hullguard", SHOT, "minecraft:turtle_helmet", 3, 44.0D, 9.0D, 5.0D, 0.30D, 14),
            new Warded("rimeguard", FROST, "minecraft:leather_boots", 3, 52.0D, 9.5D, 5.5D, 0.28D, 10),
            new Warded("detonant", BLAST, "minecraft:netherite_ingot", 3, 60.0D, 10.0D, 6.0D, 0.26D, 8));

    /** What each court is drawn with — one shape for all of them, being knights. */
    public static final Map<String, String> SHAPES = Map.of(
            FLAME, BeastRules.BRUTE,
            SHOT, BeastRules.WIGHT,
            BLAST, BeastRules.BRUTE,
            ROT, BeastRules.PROWLER,
            FROST, BeastRules.WIGHT,
            CURSE, BeastRules.WIGHT);

    /** The shape one of these is drawn with, or the empty string. */
    public static String shapeOf(Warded one) {
        String found = SHAPES.get(one.ward());
        return found == null ? "" : found;
    }

    /** The creature of this name, or null. */
    public static Warded warded(String id) {
        for (Warded one : WARDED) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** How many of a roster one ward answers. */
    public static int covers(String ward, List<Warded> roster) {
        int found = 0;
        for (Warded one : roster) {
            if (one.ward().equals(ward)) {
                found++;
            }
        }
        return found;
    }

    /** The hardest beast standing on the same rung, or null. */
    public static BeastRules.Beast beside(Warded one, List<BeastRules.Beast> beasts) {
        BeastRules.Beast hardest = null;
        for (BeastRules.Beast beast : beasts) {
            if (beast.tier() != one.tier()) {
                continue;
            }
            if (hardest == null || beast.threat() > hardest.threat()) {
                hardest = beast;
            }
        }
        return hardest;
    }

    /** What one of these leaves behind. */
    public static final List<String> LEAVINGS = List.of(
            "minecraft:iron_nugget", "minecraft:string", "minecraft:slime_ball",
            "minecraft:bone", "minecraft:gunpowder", "minecraft:leather",
            "minecraft:coal");

    /** What this one leaves behind, by where it sits in the roster. */
    public static String leaves(Warded one) {
        int at = WARDED.indexOf(one);
        return at < 0 ? "" : LEAVINGS.get(at % LEAVINGS.size());
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the warded. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(WARDED));
        bad.addAll(faultsInWards(WARDED, WARDS));
        bad.addAll(faultsInGaps(WARDED, WARDS));
        bad.addAll(faultsInDoors(WARDED, LEAST_LEFT));
        bad.addAll(faultsInDecoration(WARDED, MOST_LEFT));
        bad.addAll(faultsInSpread(WARDED, MOST_COVERED));
        bad.addAll(faultsInStanding(WARDED, BeastRules.BEASTS));
        bad.addAll(faultsInAnswers(WARDED));
        bad.addAll(faultsInRarity(WARDED, LEAST_WEIGHT));
        bad.addAll(faultsInShapes(WARDED, MythRules.forms()));
        bad.addAll(faultsInLeavings(WARDED));
        return List.copyOf(bad);
    }

    /** No two creatures written under one name. */
    public static List<String> faultsInNames(List<Warded> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Warded one : roster) {
            if (!seen.add(one.id())) {
                bad.add("two creatures are called " + one.id() + ", and whichever "
                        + "the game registers second is a row nothing ever spawns");
            }
        }
        return bad;
    }

    /** Every creature is about a kind of protection this module knows. */
    public static List<String> faultsInWards(List<Warded> roster, List<String> wards) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (!wards.contains(one.ward())) {
                bad.add(one.id() + " is answered by " + one.ward() + ", which is not "
                        + "one of the " + wards.size() + " kinds of protection this "
                        + "module has — so what fighting it is for is a word");
            }
        }
        return bad;
    }

    /** Every ward is about something, so none is a kind of gear nothing wants. */
    public static List<String> faultsInGaps(List<Warded> roster, List<String> wards) {
        List<String> bad = new ArrayList<>();
        for (String ward : wards) {
            if (covers(ward, roster) == 0) {
                bad.add("nothing in the roster is answered by " + ward + ", so it is "
                        + "a kind of protection the pack sells and nothing in the "
                        + "world asks for");
            }
        }
        return bad;
    }

    /**
     * Nothing here is a door.
     *
     * <p>Half of the band, and the half a player never reports, because the one
     * who has the gear does not notice the fight was skipped.
     */
    public static List<String> faultsInDoors(List<Warded> roster, double floor) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (one.left() < floor) {
                bad.add(one.id() + " leaves " + Math.round(one.left() * 100.0D)
                        + "% of itself to somebody carrying " + one.answer()
                        + ", against a floor of " + Math.round(floor * 100.0D)
                        + "% — which is not a fight, it is a door, and the key is "
                        + "an inventory slot");
            }
        }
        return bad;
    }

    /**
     * And nothing here makes its gear decoration.
     *
     * <p>The other half, and the commoner one, because nobody complains about
     * armour that does nothing — they just stop wearing it.
     */
    public static List<String> faultsInDecoration(List<Warded> roster, double ceiling) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (one.left() > ceiling) {
                bad.add(one.id() + " leaves " + Math.round(one.left() * 100.0D)
                        + "% of itself to somebody carrying " + one.answer()
                        + ", against a ceiling of " + Math.round(ceiling * 100.0D)
                        + "% — so the thing the player was told to bring is a "
                        + "texture with a tooltip");
            }
        }
        return bad;
    }

    /**
     * No single kind of protection answers the roster.
     *
     * <p>Takes the cap in rather than reading {@link #MOST_COVERED}, so a
     * control can widen it and be caught by this rule rather than by arithmetic
     * that re-solved itself against the broken number.
     */
    public static List<String> faultsInSpread(List<Warded> roster, int mostCovered) {
        List<String> bad = new ArrayList<>();
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Warded one : roster) {
            found.merge(one.ward(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : found.entrySet()) {
            if (entry.getValue() > mostCovered) {
                bad.add(entry.getKey() + " answers " + entry.getValue() + " of the "
                        + roster.size() + " here against a cap of " + mostCovered
                        + ", so the player has one decision — which set to wear — "
                        + "rather than one per creature, and the rest of the wards "
                        + "are a list in a language file");
            }
        }
        return bad;
    }

    /**
     * Nothing here out-fights the beast of its own rung, even unwarded.
     *
     * <p>Two independent passes, neither returning early, for the reason every
     * other file in this module gives: folded into one, deleting the null guard
     * leaves the next line to throw rather than report.
     */
    public static List<String> faultsInStanding(List<Warded> roster,
                                                List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (beside(one, beasts) == null) {
                bad.add(one.id() + " stands on tier " + one.tier() + " and there is "
                        + "no beast there, so nothing holds it to anything");
            }
        }
        for (Warded one : roster) {
            BeastRules.Beast beside = beside(one, beasts);
            if (beside != null && one.threat() >= beside.threat()) {
                bad.add(one.id() + " is worth " + Math.round(one.threat())
                        + " unwarded against " + beside.id() + "'s "
                        + Math.round(beside.threat()) + " on the same rung, so a "
                        + "player without the gear meets something harder than the "
                        + "ladder says that rung contains");
            }
        }
        return bad;
    }

    /** What the player is told to bring has to be a thing. */
    public static List<String> faultsInAnswers(List<Warded> roster) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (!one.answer().contains(":")) {
                bad.add(one.id() + " is answered by " + one.answer() + ", which is "
                        + "not the name of anything — an answer with no namespace "
                        + "is a word in a table");
            }
        }
        return bad;
    }

    /** Nothing so rare it is read about rather than met. */
    public static List<String> faultsInRarity(List<Warded> roster, int leastWeight) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (one.weight() < leastWeight) {
                bad.add(one.id() + " is chosen " + one.weight() + " times against a "
                        + "floor of " + leastWeight + ", so it is a creature most "
                        + "players will read about rather than meet");
            }
        }
        return bad;
    }

    /** Every ward is drawn with a shape this module has. */
    public static List<String> faultsInShapes(List<Warded> roster, List<String> forms) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (shapeOf(one).isEmpty()) {
                bad.add("nothing draws what " + one.ward() + " answers, so "
                        + one.id() + " is a creature this module cannot put on "
                        + "the screen");
            }
        }
        for (Warded one : roster) {
            String shape = shapeOf(one);
            if (!shape.isEmpty() && !forms.contains(shape)) {
                bad.add(one.id() + " is drawn as a " + shape + " and this module "
                        + "has no such shape, so it renders as a "
                        + "black-and-magenta cube for whoever meets it first");
            }
        }
        return bad;
    }

    /** Everything leaves something, and it is something that exists. */
    public static List<String> faultsInLeavings(List<Warded> roster) {
        List<String> bad = new ArrayList<>();
        for (Warded one : roster) {
            if (leaves(one).isEmpty()) {
                bad.add(one.id() + " leaves nothing, so killing it is a thing the "
                        + "player does for the experience alone");
            }
        }
        for (String left : LEAVINGS) {
            if (!left.startsWith("minecraft:") && !left.startsWith("elysium")) {
                bad.add(left + " is left behind and nothing in this pack or the "
                        + "game makes one of those");
            }
        }
        return bad;
    }
}
