package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eight creatures that are dangerous in a way better armour does not answer.
 *
 * <h2>What a myth is for</h2>
 *
 * The nine beasts in {@link BeastRules} are a ladder: the answer to every one
 * of them is the same answer, more of it. Better armour, more health, a bigger
 * weapon. That ladder is the spine of the pack and it should stay a ladder —
 * but a world where every fight has one answer is a world where the fights stop
 * being decisions by about the third hour.
 *
 * <p>A myth is the other kind. A basilisk's stare is not survived by wearing
 * more; a chupacabra taking the top off your health bar is not healed back by
 * eating more; a drake's breath goes through a shield. Each of the eight has
 * exactly one of these — its {@link Myth#bane} — and the whole design of this
 * file is three rules about that one field.
 *
 * <h2>The three</h2>
 *
 * <b>One bane each, and no two the same.</b> Two creatures whose danger is the
 * same danger are one creature with two models: whichever the player meets
 * first teaches them the answer, and the second is scenery.
 * {@link #faultsInBaneNames} is that.
 *
 * <b>Every bane has an answer.</b> A danger with no counter is not difficulty,
 * it is a wall — and the difference is invisible from inside the fight, because
 * both feel like losing. Each bane names what beats it, and the validator holds
 * that name to something the pack actually makes. {@link #faultsInAnswers} is
 * the rules' half.
 *
 * <b>A myth is not also the strongest thing in the world.</b> It already has a
 * way past armour; letting it out-fight the beast of its own tier on the plain
 * numbers as well makes the ladder pointless. {@link #faultsInRestraint} holds
 * every myth's raw threat under the beast it stands beside, and takes both
 * rosters in so a control can hand it a pair built to be caught.
 */
public final class MythRules {

    private MythRules() {
    }

    // ==================================================================
    // The ways past armour
    // ==================================================================

    /** It turns what looks at it to stone. */
    public static final String PETRIFY = "petrify";

    /** It takes the top off your health and keeps it. */
    public static final String DRAIN = "drain";

    /** It burns through what you are holding up. */
    public static final String BREATH = "breath";

    /** Nothing but the right metal marks it. */
    public static final String PLATING = "plating";

    /** There is never one of them. */
    public static final String NUMBERS = "numbers";

    /** It will not stand and fight, and it takes what it came for. */
    public static final String THIEVERY = "thievery";

    /** It is somewhere you cannot follow. */
    public static final String HEIGHT = "height";

    /** What it leaves behind grows. */
    public static final String SPORES = "spores";

    /** The eight, in the order everything walks them. */
    public static final List<String> BANES = List.of(
            PETRIFY, DRAIN, BREATH, PLATING, NUMBERS, THIEVERY, HEIGHT, SPORES);

    /**
     * One creature out of a story.
     *
     * @param id     what it is called
     * @param bane   the one way it gets past armour
     * @param answer what beats that, named so the pack can be asked for it
     * @param tier   which rung of the beasts' ladder it stands beside
     * @param health what it can take
     * @param damage what it gives on contact
     * @param speed  how fast it closes
     * @param weight how often it is chosen when something spawns
     * @param flees  whether it runs rather than finishes a fight
     * @param form   which shape this module already knows how to draw it with
     */
    public record Myth(String id, String bane, String answer, int tier,
                       double health, double damage, double speed, int weight,
                       boolean flees, String form) {

        public Myth {
            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health, " + damage + " damage and " + speed
                        + " speed, and a creature with no health or no legs is "
                        + "not a creature");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " is chosen " + weight
                        + " times when something spawns, so it is a story "
                        + "nobody ever meets");
            }
            if (tier < 1) {
                throw new IllegalArgumentException(id + " stands beside tier "
                        + tier + ", which is below the bottom of the ladder");
            }
            if (answer == null || answer.isEmpty()) {
                throw new IllegalArgumentException(id + " is dangerous in a way "
                        + "nothing answers, which is a wall rather than a fight");
            }
            if (form == null || form.isEmpty()) {
                throw new IllegalArgumentException(id + " has no shape, so it is "
                        + "a creature nothing can draw");
            }
        }

        /**
         * How dangerous it is on the plain numbers, ignoring its bane.
         *
         * <p>The same shape {@link BeastRules.Beast#threat} uses, and
         * deliberately so: the two are compared, and two crude numbers that
         * agree on their crudeness compare honestly where two precise ones
         * would not.
         */
        public double threat() {
            return health * (1.0D + damage) * speed / 0.25D;
        }
    }

    /**
     * The eight.
     *
     * <p>Each stands beside a rung of the beasts' ladder rather than on it: a
     * player who has geared for tier two meets the tier-two myth and finds that
     * their gear is not the whole answer. What every one of them is held to is
     * below.
     */
    public static final List<Myth> MYTHS = List.of(
            new Myth("glasseye", PETRIFY, "minecraft:carved_pumpkin", 3,
                    34.0D, 6.0D, 0.26D, 14, false, BeastRules.PROWLER),
            new Myth("siphon", DRAIN, "minecraft:golden_apple", 2,
                    22.0D, 5.0D, 0.40D, 30, false, BeastRules.PROWLER),
            new Myth("blastjaw", BREATH, "minecraft:magma_cream", 3,
                    44.0D, 7.0D, 0.30D, 12, false, BeastRules.BRUTE),
            new Myth("plateframe", PLATING, "minecraft:mace", 2,
                    40.0D, 5.0D, 0.20D, 22, false, BeastRules.BRUTE),
            new Myth("swarmhand", NUMBERS, "minecraft:shield", 1,
                    16.0D, 4.0D, 0.34D, 60, false, BeastRules.WIGHT),
            new Myth("pilferhand", THIEVERY, "minecraft:tripwire_hook", 1,
                    12.0D, 1.0D, 0.44D, 75, true, BeastRules.WIGHT),
            new Myth("skimwing", HEIGHT, "minecraft:bow", 2,
                    26.0D, 3.0D, 0.46D, 18, true, FaunaRules.GRAZER),
            new Myth("bloomlet", SPORES, "minecraft:shears", 1,
                    14.0D, 2.0D, 0.28D, 52, false, FaunaRules.FOWL));

    /**
     * Every shape this module can draw a creature with.
     *
     * <p>The beasts' three families and the fauna's four kinds, and nothing
     * else. A myth is given one of these rather than a model of its own,
     * because a creature with a shape nobody drew is a creature that renders as
     * a black-and-magenta cube — which this module shipped for a season with
     * the shade and the bolt, and which is only ever found by the player who
     * meets that one.
     */
    public static List<String> forms() {
        List<String> made = new ArrayList<>(BeastRules.FAMILIES);
        made.addAll(FaunaRules.KINDS);
        return List.copyOf(made);
    }

    /** The myth of this name, or null. */
    public static Myth myth(String id) {
        for (Myth one : MYTHS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The beast a myth stands beside, or null. */
    public static BeastRules.Beast beside(Myth myth, List<BeastRules.Beast> beasts) {
        BeastRules.Beast hardest = null;
        for (BeastRules.Beast beast : beasts) {
            if (beast.tier() != myth.tier()) {
                continue;
            }
            if (hardest == null || beast.threat() > hardest.threat()) {
                hardest = beast;
            }
        }
        return hardest;
    }

    /** What a myth leaves behind. */
    public static final List<String> LEAVINGS = List.of(
            "minecraft:copper_ingot", "minecraft:rotten_flesh",
            "minecraft:iron_nugget", "minecraft:mushroom_stew",
            "minecraft:emerald", "minecraft:feather", "minecraft:blaze_powder",
            "minecraft:stone");

    /** What this one leaves behind, by where it sits in the roster. */
    public static String leaves(Myth myth) {
        int at = MYTHS.indexOf(myth);
        return at < 0 ? "" : LEAVINGS.get(at % LEAVINGS.size());
    }

    /** The rarest anything here may be, so that a myth is met and not read about. */
    public static final int LEAST_WEIGHT = 8;

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the myths. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(MYTHS));
        bad.addAll(faultsInBanes(MYTHS, BANES));
        bad.addAll(faultsInBaneNames(MYTHS));
        bad.addAll(faultsInGaps(MYTHS, BANES));
        bad.addAll(faultsInAnswers(MYTHS));
        bad.addAll(faultsInRestraint(MYTHS, BeastRules.BEASTS));
        bad.addAll(faultsInStanding(MYTHS, BeastRules.BEASTS));
        bad.addAll(faultsInFleeing(MYTHS));
        bad.addAll(faultsInRarity(MYTHS, LEAST_WEIGHT));
        bad.addAll(faultsInLeavings(MYTHS));
        bad.addAll(faultsInForms(MYTHS, forms()));
        return List.copyOf(bad);
    }

    /** Whether two myths are written under one name. */
    public static List<String> faultsInNames(List<Myth> myths) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Myth one : myths) {
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "
                        + "a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether every myth's bane is one the module knows. */
    public static List<String> faultsInBanes(List<Myth> myths, List<String> banes) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            if (!banes.contains(one.bane())) {
                bad.add(one.id() + " gets past armour by " + one.bane()
                        + ", which is not one of the ways this module has, so "
                        + "nothing anywhere makes it happen");
            }
        }
        return bad;
    }

    /**
     * Whether two myths are dangerous in the same way.
     *
     * <p>Its own rule, and the one the file is built around. Two creatures
     * whose danger is the same danger are one creature with two models:
     * whichever the player meets first teaches them the answer, and the second
     * is scenery from then on.
     */
    public static List<String> faultsInBaneNames(List<Myth> myths) {
        List<String> bad = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (Myth one : myths) {
            if (!used.add(one.bane())) {
                bad.add(one.id() + " is dangerous by " + one.bane()
                        + " and so is something else, so whichever a player "
                        + "meets first teaches them the answer and the other is "
                        + "scenery");
            }
        }
        return bad;
    }

    /** Whether a way past armour has nothing that uses it. */
    public static List<String> faultsInGaps(List<Myth> myths, List<String> banes) {
        List<String> bad = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (Myth one : myths) {
            used.add(one.bane());
        }
        for (String bane : banes) {
            if (!used.contains(bane)) {
                bad.add("nothing is dangerous by " + bane + ", so a way past "
                        + "armour is written down and never happens");
            }
        }
        return bad;
    }

    /**
     * Whether every bane names something that beats it.
     *
     * <p>One item that beats three of the eight is one item a player carries
     * and then stops thinking. That an answer is named at all is the compact
     * constructor's job, and whether the pack really makes the thing named is
     * the validator's, because only the validator can see the other modules.
     */
    public static List<String> faultsInAnswers(List<Myth> myths) {
        List<String> bad = new ArrayList<>();
        Set<String> answered = new LinkedHashSet<>();
        // There is deliberately no clause here for an answer that is empty.
        // The compact constructor refuses one, so a clause for it would be
        // code no control could ever reach — and unreachable code in a rules
        // file is worse than none, because it reads like a check.
        for (Myth one : myths) {
            if (!answered.add(one.answer())) {
                bad.add(one.answer() + " answers " + one.id() + " and something "
                        + "else as well, so one thing carried in a pocket ends "
                        + "two of these and the player stops thinking about "
                        + "either");
            }
        }
        return bad;
    }

    /**
     * Whether a myth is also the hardest thing on its rung.
     *
     * <p>It already has a way past armour. Letting it out-fight the beast it
     * stands beside on the plain numbers as well makes the ladder pointless:
     * the player who geared up for tier three meets the tier-three myth and
     * finds their gear answered nothing at all.
     */
    public static List<String> faultsInRestraint(List<Myth> myths,
                                                 List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            BeastRules.Beast beside = beside(one, beasts);
            if (beside == null) {
                continue;
            }
            if (one.threat() >= beside.threat()) {
                bad.add(one.id() + " is worth " + Math.round(one.threat())
                        + " on the plain numbers against " + beside.id() + "'s "
                        + Math.round(beside.threat()) + " at the same tier, and "
                        + "it has a way past armour on top — so gearing up "
                        + "answers nothing and the ladder is pointless");
            }
        }
        return bad;
    }

    /**
     * Whether a myth has a rung to stand beside at all.
     *
     * <p>Kept apart from the rule above because that one is written to skip a
     * myth with no neighbour, which makes it exactly the rule that cannot
     * notice one.
     */
    public static List<String> faultsInStanding(List<Myth> myths,
                                                List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            if (beside(one, beasts) == null) {
                bad.add(one.id() + " stands beside tier " + one.tier()
                        + " and there is no beast on that rung, so nothing "
                        + "holds its numbers down and it is unchecked");
            }
        }
        return bad;
    }

    /**
     * Whether something that runs is priced as though it fought.
     *
     * <p>A creature that will not stand and finish a fight is worth less of a
     * player's time, so it had better be worth less of their health too.
     */
    public static List<String> faultsInFleeing(List<Myth> myths) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            if (!one.flees()) {
                continue;
            }
            for (Myth other : myths) {
                if (other.flees() || other.tier() != one.tier()) {
                    continue;
                }
                if (one.threat() > other.threat()) {
                    bad.add(one.id() + " runs rather than finishing a fight and "
                            + "is still worth more than " + other.id()
                            + ", which stands — so the safest thing on the rung "
                            + "is also the worst to meet");
                }
            }
        }
        return bad;
    }

    /** Whether anything here is so rare a player only reads about it. */
    public static List<String> faultsInRarity(List<Myth> myths, int least) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            if (one.weight() < least) {
                bad.add(one.id() + " is chosen " + one.weight() + " times where "
                        + least + " is the fewest this module allows, so it is a "
                        + "creature a player reads about and never meets");
            }
        }
        return bad;
    }

    /** Whether everything a myth leaves behind is something. */
    public static List<String> faultsInLeavings(List<Myth> myths) {
        return faultsInLeavings(myths, LEAVINGS);
    }

    /**
     * The same, with the table handed in.
     *
     * <p>Handed in, because a rule that reads the table it is judging cannot
     * be given a broken one — and a rule nothing can break is a rule that has
     * never been shown to work.
     */
    public static List<String> faultsInLeavings(List<Myth> myths,
                                                List<String> leavings) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            int at = myths.indexOf(one);
            String left = leavings.isEmpty() || at < 0
                    ? "" : leavings.get(at % leavings.size());
            if (left.isEmpty()) {
                bad.add(one.id() + " leaves nothing at all, so killing one is "
                        + "the only fight in the pack that pays nothing");
            }
            if (!left.isEmpty() && !left.contains(":")) {
                bad.add(one.id() + " leaves " + left + ", which is not a name "
                        + "anything can look up");
            }
        }
        return bad;
    }

    /** Whether every myth is a shape this module can actually draw. */
    public static List<String> faultsInForms(List<Myth> myths, List<String> forms) {
        List<String> bad = new ArrayList<>();
        for (Myth one : myths) {
            if (!forms.contains(one.form())) {
                bad.add(one.id() + " is drawn as a " + one.form() + ", which is "
                        + "not a shape this module has — so it renders as a "
                        + "black-and-magenta cube, and only the player who "
                        + "meets that one ever finds out");
            }
        }
        return bad;
    }
}
