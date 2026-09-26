package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Twenty-seven creatures in thirteen kindreds, and the proof that a variant is
 * worth meeting twice.
 *
 * <h2>The failure this file exists to stop</h2>
 *
 * A roster grows by variant. One scorpion becomes four: a scorpion, a desert
 * scorpion, a frost scorpion, a nether scorpion. The roster count goes up by
 * three and the number of fights in the game goes up by none, because all four
 * are the same fight in a different colour. The player learns the answer once
 * and the other three are scenery with a longer name.
 *
 * <p>What makes that failure hard to see from inside is that it never looks
 * like a mistake. Each variant is cheap to add, each one reads well in a list,
 * and the world does feel fuller. It only shows up hours later, as the quiet
 * sense that nothing new has happened in a while.
 *
 * <h2>What a kindred is</h2>
 *
 * A <b>kindred</b> is a base creature and the creatures that are versions of
 * it. Thirteen of them here: eight with kin and five that stand alone. Every
 * creature names its kindred and exactly one of each kindred is its
 * {@link Kin#base}.
 *
 * <p>Being in a kindred is a promise, and the promise is enforced three ways.
 *
 * <b>No two in a kindred do the same thing.</b> Every creature carries one
 * {@link Kin#trait} — the thing the player has to do differently about it —
 * and within a kindred no trait repeats. A frost scorpion freezes you and a
 * nether scorpion sets you alight; whichever you meet second is still a fight
 * you have not had. {@link #faultsInTraitNames} is that.
 *
 * <b>No two in a kindred live in the same place.</b> Meeting the same creature
 * twice in one biome is the roster padding itself where the player is
 * standing. {@link #faultsInHomeNames} is that, and the two together are the
 * whole claim: a variant differs in what it does <em>and</em> in where it is,
 * never in only one of them.
 *
 * <b>A variant is not a harder version.</b> This is the one that costs
 * something to hold to. It would be easy to make the nether scorpion simply
 * bigger, and then the desert and frost ones are the weak ones nobody bothers
 * with once they have the gear. So every member of a kindred sits on the same
 * rung of the beasts' ladder, and the spread of threat inside a kindred is
 * capped at {@link #WIDEST_KINDRED}. Harder belongs to {@code tier}, which is
 * what {@code tier} is for. {@link #faultsInSpread} and {@link #faultsInTiers}
 * are that.
 *
 * <h2>And the usual two</h2>
 *
 * Every trait is one something in this module actually makes happen, and every
 * home is a biome tag the game itself ships — checked against its own jar,
 * because a tag nothing fills is a creature that never spawns and it looks
 * exactly like a creature that spawns rarely.
 */
public final class KindredRules {

    private KindredRules() {
    }

    // ==================================================================
    // The eleven things a creature can do that its kin do not
    // ==================================================================

    /** It leaves something in you that keeps working after it is dead. */
    public static final String POISONS = "poisons";

    /** You stop sprinting, which is the whole of your escape. */
    public static final String FREEZES = "freezes";

    /** You are on fire, and so is the ground you were backing onto. */
    public static final String IGNITES = "ignites";

    /** Backing off does not open the distance, because it closes it in one. */
    public static final String LEAPS = "leaps";

    /** It goes under, so line of sight is not a thing you have. */
    public static final String BURROWS = "burrows";

    /** Killing it first is the difference between one fight and four. */
    public static final String SUMMONS = "summons";

    /** It hits from where you cannot, so cover matters more than armour. */
    public static final String THROWS = "throws";

    /** It takes what you were going to use to heal. */
    public static final String DRAINS = "drains";

    /** It is not what it looks like, so looking is the skill. */
    public static final String DISGUISES = "disguises";

    /** What it does to the others near it is worse than what it does to you. */
    public static final String HOWLS = "howls";

    /** The answer to it is to get on it. */
    public static final String CARRIES = "carries";

    /** The eleven, in the order everything walks them. */
    public static final List<String> TRAITS = List.of(
            POISONS, FREEZES, IGNITES, LEAPS, BURROWS, SUMMONS, THROWS,
            DRAINS, DISGUISES, HOWLS, CARRIES);

    // ==================================================================
    // The eight places, each one a tag the game really ships
    // ==================================================================

    /** Anywhere above ground, which is where a player certainly goes. */
    public static final String OPEN = "#minecraft:is_overworld";

    /** Under leaves. */
    public static final String WOODS = "#minecraft:is_forest";

    /** Where nothing grows. */
    public static final String DRYLANDS = "#minecraft:is_badlands";

    /** Where the ground is white. */
    public static final String COLD = "#minecraft:spawns_snow_foxes";

    /** The other side. */
    public static final String HELL = "#minecraft:is_nether";

    /** High up, and the holes that go into it. */
    public static final String PEAKS = "#minecraft:is_mountain";

    /** The cold woods, which are not the same as the woods. */
    public static final String CONIFER = "#minecraft:is_taiga";

    /** Along the water. */
    public static final String RIVERSIDE = "#minecraft:is_river";

    /**
     * The eight.
     *
     * <p>Every one is a tag the game itself has. {@code #c:is_cold} is not
     * here and never will be: it reads like a tag, the game does not have one,
     * and a spawn rule pointed at a tag nothing fills is a creature that never
     * appears — which is indistinguishable, from the outside, from a creature
     * that is simply rare.
     */
    public static final List<String> HOMES = List.of(
            OPEN, WOODS, DRYLANDS, COLD, HELL, PEAKS, CONIFER, RIVERSIDE);

    /**
     * One creature, and which of its kin it is.
     *
     * @param id      what it is called
     * @param kin     the kindred it belongs to
     * @param base    whether it is the one the others are versions of
     * @param trait   the one thing it does that its kin do not
     * @param home    the biome tag it is found in
     * @param tier    which rung of the beasts' ladder its whole kindred stands on
     * @param health  what it can take
     * @param damage  what it gives on contact
     * @param speed   how fast it closes
     * @param weight  how often it is chosen when something spawns
     * @param young   whether there is a smaller one of it in the world
     */
    public record Kin(String id, String kin, boolean base, String trait, String home,
                      int tier, double health, double damage, double speed,
                      int weight, boolean young) {

        public Kin {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a creature with no name");
            }
            if (kin == null || kin.isEmpty()) {
                throw new IllegalArgumentException(id + " belongs to no kindred, so "
                        + "nothing holds it to being different from anything");
            }
            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health, " + damage + " damage and " + speed
                        + " speed, and a creature with no health or no legs is "
                        + "not a creature");
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
            if (home == null || home.isEmpty()) {
                throw new IllegalArgumentException(id + " lives nowhere, which is a "
                        + "creature the rules describe and the world never places");
            }
            if (trait == null || trait.isEmpty()) {
                throw new IllegalArgumentException(id + " does nothing its kin do "
                        + "not, which is the whole failure this file is about");
            }
        }

        /**
         * How dangerous it is, on one crude number.
         *
         * <p>The same shape {@link BeastRules.Beast#threat} and
         * {@link MythRules.Myth#threat} use, and deliberately so: all three are
         * compared against each other, and three crude numbers that agree on
         * their crudeness compare honestly where three precise ones would not.
         */
        public double threat() {
            return health * (1.0D + damage) * speed / 0.25D;
        }
    }

    /**
     * The twenty-seven.
     *
     * <p>Read down the {@code kin} column and the shape of the roster is one
     * sentence: eight families and five things that are only themselves. Read
     * across a family and every row differs from the one above it in both of
     * the two columns that matter.
     */
    public static final List<Kin> KINDRED = List.of(
            //        id                 kin        base  trait      home       tier  hp    dmg   speed  wt  young
            new Kin("scorpion", "scorpion", true, POISONS, OPEN, 1, 16.0D, 3.0D, 0.30D, 40, true),
            new Kin("desert_scorpion", "scorpion", false, BURROWS, DRYLANDS, 1, 18.0D, 3.0D, 0.30D, 30, false),
            new Kin("frost_scorpion", "scorpion", false, FREEZES, COLD, 1, 20.0D, 3.0D, 0.28D, 24, false),
            new Kin("nether_scorpion", "scorpion", false, IGNITES, HELL, 1, 20.0D, 4.0D, 0.30D, 20, false),

            new Kin("ogre", "ogre", true, LEAPS, WOODS, 2, 40.0D, 6.0D, 0.23D, 22, false),
            new Kin("cave_ogre", "ogre", false, BURROWS, PEAKS, 2, 46.0D, 6.0D, 0.22D, 18, false),
            new Kin("hell_ogre", "ogre", false, IGNITES, HELL, 2, 44.0D, 7.0D, 0.24D, 14, false),

            new Kin("goblin", "goblin", true, SUMMONS, OPEN, 1, 14.0D, 2.0D, 0.34D, 55, false),
            new Kin("dart_goblin", "goblin", false, THROWS, WOODS, 1, 12.0D, 3.0D, 0.32D, 35, false),
            new Kin("imp", "goblin", false, IGNITES, HELL, 1, 14.0D, 3.0D, 0.36D, 40, false),

            new Kin("wild_wolf", "wolf", true, HOWLS, CONIFER, 2, 26.0D, 4.0D, 0.40D, 45, false),
            new Kin("werewolf", "wolf", false, LEAPS, WOODS, 2, 34.0D, 6.0D, 0.40D, 12, false),
            new Kin("werevillager", "wolf", false, DISGUISES, OPEN, 2, 30.0D, 5.0D, 0.38D, 10, false),

            new Kin("nightmare_horse", "horse", true, IGNITES, HELL, 1, 22.0D, 3.0D, 0.36D, 14, false),
            new Kin("skeleton_horse", "horse", false, CARRIES, OPEN, 1, 18.0D, 2.0D, 0.38D, 26, false),
            new Kin("zombie_horse", "horse", false, POISONS, RIVERSIDE, 1, 20.0D, 3.0D, 0.32D, 20, false),

            new Kin("ghost", "ghost", true, DRAINS, PEAKS, 2, 24.0D, 4.0D, 0.34D, 28, false),
            new Kin("fire_ghost", "ghost", false, IGNITES, HELL, 2, 26.0D, 5.0D, 0.34D, 16, false),

            new Kin("golem", "golem", true, LEAPS, PEAKS, 3, 60.0D, 8.0D, 0.20D, 9, false),
            new Kin("ice_golem", "golem", false, FREEZES, COLD, 3, 64.0D, 8.0D, 0.20D, 7, false),

            new Kin("evil_bat", "bat", true, DRAINS, PEAKS, 1, 12.0D, 2.0D, 0.44D, 60, false),
            new Kin("horse_bat", "bat", false, LEAPS, DRYLANDS, 1, 16.0D, 3.0D, 0.42D, 18, false),

            new Kin("mimic", "mimic", true, DISGUISES, OPEN, 2, 30.0D, 6.0D, 0.18D, 12, false),
            new Kin("owlbear", "owlbear", true, LEAPS, CONIFER, 2, 38.0D, 6.0D, 0.30D, 16, true),
            new Kin("purple_worm", "purple_worm", true, BURROWS, DRYLANDS, 3, 70.0D, 9.0D, 0.24D, 6, false),
            new Kin("shrike", "shrike", true, THROWS, COLD, 2, 26.0D, 5.0D, 0.42D, 15, true),
            new Kin("silver_skeleton", "silver_skeleton", true, SUMMONS, RIVERSIDE, 2, 28.0D, 5.0D, 0.30D, 24, false));

    /**
     * What each kindred is drawn with.
     *
     * <p>Per <em>kindred</em> rather than per creature, which is the whole
     * point of the word: kin look alike, and a frost scorpion that is drawn as
     * a bird is not a version of a scorpion, it is a second creature with a
     * scorpion's name. Storing it once means no rule is needed to hold the
     * members of a kindred to one shape — they cannot have two.
     *
     * <p>Every value is one of the shapes this module already has, which
     * {@link #faultsInShapes} holds it to. No new boxes: a bespoke model per
     * creature is twenty-seven more places for a part to be named wrong and a
     * walk cycle to fall out of step.
     */
    public static final Map<String, String> SHAPES = Map.ofEntries(
            Map.entry("scorpion", BeastRules.PROWLER),
            Map.entry("ogre", BeastRules.BRUTE),
            Map.entry("goblin", BeastRules.WIGHT),
            Map.entry("wolf", BeastRules.PROWLER),
            Map.entry("horse", FaunaRules.GRAZER),
            Map.entry("ghost", BeastRules.WIGHT),
            Map.entry("golem", BeastRules.BRUTE),
            Map.entry("bat", FaunaRules.FOWL),
            Map.entry("mimic", BeastRules.BRUTE),
            Map.entry("owlbear", BeastRules.BRUTE),
            Map.entry("purple_worm", BeastRules.PROWLER),
            Map.entry("shrike", FaunaRules.FOWL),
            Map.entry("silver_skeleton", BeastRules.WIGHT));

    /** The shape one of these is drawn with, or the empty string. */
    public static String shapeOf(Kin one) {
        String found = SHAPES.get(one.kin());
        return found == null ? "" : found;
    }

    /**
     * How much harder the hardest of a kindred may be than the easiest.
     *
     * <p>Two. Not one — kin that are numerically identical are a different kind
     * of padding, and some spread is what stops a kindred reading as one
     * creature with four hats. Not four either: past about double, the weak
     * ones stop being fights the player takes and the kindred is a ladder
     * wearing a family's name.
     */
    public static final double WIDEST_KINDRED = 2.0D;

    /** The rarest anything here may be, so that a variant is met and not read about. */
    public static final int LEAST_WEIGHT = 6;

    /** The creature of this name, or null. */
    public static Kin creature(String id) {
        for (Kin one : KINDRED) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Everything in the given kindred, out of the given roster. */
    public static List<Kin> family(String kin, List<Kin> roster) {
        List<Kin> found = new ArrayList<>();
        for (Kin one : roster) {
            if (one.kin().equals(kin)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** Every kindred named by the given roster, in the order they appear. */
    public static List<String> kindreds(List<Kin> roster) {
        Set<String> found = new LinkedHashSet<>();
        for (Kin one : roster) {
            found.add(one.kin());
        }
        return List.copyOf(found);
    }

    /**
     * The hardest beast standing on the same rung, or null.
     *
     * <p>Takes the beasts in rather than reading them, so a control can hand it
     * a ladder built to be caught.
     */
    public static BeastRules.Beast beside(Kin one, List<BeastRules.Beast> beasts) {
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
            "minecraft:string", "minecraft:bone", "minecraft:rotten_flesh",
            "minecraft:leather", "minecraft:gunpowder", "minecraft:flint",
            "minecraft:coal", "minecraft:iron_nugget", "minecraft:slime_ball");

    /** What this one leaves behind, by where it sits in the roster. */
    public static String leaves(Kin one) {
        int at = KINDRED.indexOf(one);
        return at < 0 ? "" : LEAVINGS.get(at % LEAVINGS.size());
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the kindred. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(KINDRED));
        bad.addAll(faultsInBases(KINDRED));
        bad.addAll(faultsInTraits(KINDRED, TRAITS));
        bad.addAll(faultsInTraitNames(KINDRED));
        bad.addAll(faultsInTraitGaps(KINDRED, TRAITS));
        bad.addAll(faultsInHomes(KINDRED, HOMES));
        bad.addAll(faultsInHomeNames(KINDRED));
        bad.addAll(faultsInHomeGaps(KINDRED, HOMES));
        bad.addAll(faultsInTiers(KINDRED));
        bad.addAll(faultsInSpread(KINDRED, WIDEST_KINDRED));
        bad.addAll(faultsInStanding(KINDRED, BeastRules.BEASTS));
        bad.addAll(faultsInYoung(KINDRED));
        bad.addAll(faultsInRarity(KINDRED, LEAST_WEIGHT));
        bad.addAll(faultsInLeavings(KINDRED));
        bad.addAll(faultsInShapes(KINDRED, MythRules.forms()));
        return List.copyOf(bad);
    }

    /** No two creatures written under one name. */
    public static List<String> faultsInNames(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Kin one : roster) {
            if (!seen.add(one.id())) {
                bad.add("two creatures are called " + one.id() + ", and whichever "
                        + "the game registers second is a row nothing ever spawns");
            }
        }
        return bad;
    }

    /**
     * Exactly one of every kindred is the one the others are versions of.
     *
     * <p>Two independent complaints rather than one with an else, because a
     * kindred with no base and a kindred with two are different mistakes and a
     * control that makes one of them has to be able to reach it.
     */
    public static List<String> faultsInBases(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        Map<String, Integer> bases = new LinkedHashMap<>();
        for (Kin one : roster) {
            bases.merge(one.kin(), one.base() ? 1 : 0, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : bases.entrySet()) {
            if (entry.getValue() == 0) {
                bad.add("the " + entry.getKey() + " kindred has no base, so nothing "
                        + "in it is the creature the others are versions of and "
                        + "every one of them is a variant of nothing");
            }
        }
        for (Map.Entry<String, Integer> entry : bases.entrySet()) {
            if (entry.getValue() > 1) {
                bad.add("the " + entry.getKey() + " kindred has " + entry.getValue()
                        + " bases, so it is two families sharing a name and the "
                        + "rules that hold a variant to its base hold it to two");
            }
        }
        return bad;
    }

    /** Every trait is one of the ones this module knows about. */
    public static List<String> faultsInTraits(List<Kin> roster, List<String> traits) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (!traits.contains(one.trait())) {
                bad.add(one.id() + " does " + one.trait() + ", which is not one of "
                        + "the " + traits.size() + " things this module makes "
                        + "happen — so it is a creature whose point is a word in "
                        + "a table");
            }
        }
        return bad;
    }

    /**
     * No two of one kindred do the same thing.
     *
     * <p>The first half of the claim this file is for.
     */
    public static List<String> faultsInTraitNames(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        for (String kin : kindreds(roster)) {
            Set<String> seen = new LinkedHashSet<>();
            for (Kin one : family(kin, roster)) {
                if (!seen.add(one.trait())) {
                    bad.add(one.id() + " does the same thing as another " + kin
                            + " — both of them " + one.trait() + " — so whichever "
                            + "the player meets first teaches them the answer and "
                            + "the second is scenery with a longer name");
                }
            }
        }
        return bad;
    }

    /** Every trait is carried by something, so none is a word nothing uses. */
    public static List<String> faultsInTraitGaps(List<Kin> roster, List<String> traits) {
        List<String> bad = new ArrayList<>();
        for (String trait : traits) {
            boolean carried = false;
            for (Kin one : roster) {
                if (one.trait().equals(trait)) {
                    carried = true;
                }
            }
            if (!carried) {
                bad.add("nothing " + trait + ", so it is a branch written, drawn "
                        + "and never reached by anything in the world");
            }
        }
        return bad;
    }

    /** Every home is one of the tags the game really ships. */
    public static List<String> faultsInHomes(List<Kin> roster, List<String> homes) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (!homes.contains(one.home())) {
                bad.add(one.id() + " lives in " + one.home() + ", which is not one "
                        + "of the " + homes.size() + " places this module knows — "
                        + "and a spawn rule pointed at a tag nothing fills looks "
                        + "exactly like a creature that is simply rare");
            }
        }
        return bad;
    }

    /**
     * No two of one kindred live in the same place.
     *
     * <p>The second half of the claim. With {@link #faultsInTraitNames} it says
     * the whole thing: a variant differs in what it does and in where it is,
     * never in only one of them.
     */
    public static List<String> faultsInHomeNames(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        for (String kin : kindreds(roster)) {
            Set<String> seen = new LinkedHashSet<>();
            for (Kin one : family(kin, roster)) {
                if (!seen.add(one.home())) {
                    bad.add(one.id() + " is in the same place as another " + kin
                            + " — both of them in " + one.home() + " — so the "
                            + "roster is padding itself where the player is "
                            + "standing");
                }
            }
        }
        return bad;
    }

    /** Every place has something in it, so none is a tag nothing uses. */
    public static List<String> faultsInHomeGaps(List<Kin> roster, List<String> homes) {
        List<String> bad = new ArrayList<>();
        for (String home : homes) {
            boolean lived = false;
            for (Kin one : roster) {
                if (one.home().equals(home)) {
                    lived = true;
                }
            }
            if (!lived) {
                bad.add("nothing lives in " + home + ", so it is a place named in "
                        + "the rules that the roster never puts anything in");
            }
        }
        return bad;
    }

    /**
     * A whole kindred stands on one rung.
     *
     * <p>Half of what stops a variant being a harder version. The other half is
     * {@link #faultsInSpread}; both are needed, because equal tiers with an
     * unbounded spread is a ladder that has stopped writing its rungs down.
     */
    public static List<String> faultsInTiers(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        for (String kin : kindreds(roster)) {
            List<Kin> family = family(kin, roster);
            if (family.isEmpty()) {
                continue;
            }
            int rung = family.get(0).tier();
            for (Kin one : family) {
                if (one.tier() != rung) {
                    bad.add(one.id() + " stands on tier " + one.tier() + " and the "
                            + "rest of the " + kin + " kindred on tier " + rung
                            + ", so what is meant to be a version of something is "
                            + "a rung of its own");
                }
            }
        }
        return bad;
    }

    /**
     * The hardest of a kindred is not more than {@code widest} times the easiest.
     *
     * <p>Takes the cap in rather than reading {@link #WIDEST_KINDRED}, so that a
     * control can narrow it and be caught by this rule rather than by the
     * arithmetic re-solving itself against the broken number.
     */
    public static List<String> faultsInSpread(List<Kin> roster, double widest) {
        List<String> bad = new ArrayList<>();
        for (String kin : kindreds(roster)) {
            List<Kin> family = family(kin, roster);
            if (family.isEmpty()) {
                continue;
            }
            Kin least = family.get(0);
            Kin most = family.get(0);
            for (Kin one : family) {
                if (one.threat() < least.threat()) {
                    least = one;
                }
                if (one.threat() > most.threat()) {
                    most = one;
                }
            }
            if (least.threat() > 0.0D && most.threat() > least.threat() * widest) {
                bad.add(most.id() + " is worth " + Math.round(most.threat())
                        + " against " + least.id() + "'s " + Math.round(least.threat())
                        + " in the same kindred, which is past the " + widest
                        + " this module allows — so the weak ones stop being "
                        + "fights anybody takes and the kindred is a ladder "
                        + "wearing a family's name");
            }
        }
        return bad;
    }

    /**
     * Nothing here out-fights the beast of its own rung.
     *
     * <p>The beasts are the spine of the pack. A creature that is a variant of
     * something and also the hardest thing on its tier makes the spine
     * decorative.
     */
    public static List<String> faultsInStanding(List<Kin> roster,
                                                List<BeastRules.Beast> beasts) {
        // Two independent passes, neither of which returns early. Folded into
        // one with a `continue`, deleting the null guard leaves the next line
        // to throw rather than report — and a checker that dies instead of
        // reporting is a checker that says nothing.
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (beside(one, beasts) == null) {
                bad.add(one.id() + " stands on tier " + one.tier() + " and there is "
                        + "no beast there, so nothing holds it to anything");
            }
        }
        for (Kin one : roster) {
            BeastRules.Beast beside = beside(one, beasts);
            if (beside != null && one.threat() >= beside.threat()) {
                bad.add(one.id() + " is worth " + Math.round(one.threat())
                        + " against " + beside.id() + "'s "
                        + Math.round(beside.threat()) + " on the same rung, so the "
                        + "ladder the whole pack is built on has a variant at the "
                        + "top of it");
            }
        }
        return bad;
    }

    /**
     * Only a base has young.
     *
     * <p>A small one of something is a smaller version of the family, not of one
     * costume of it. A frost scorpion with young and a scorpion without is a
     * world where the ordinary scorpion is the odd one out.
     */
    public static List<String> faultsInYoung(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (one.young() && !one.base()) {
                bad.add(one.id() + " has young and is not the base of the " + one.kin()
                        + " kindred, so the small one belongs to a costume rather "
                        + "than to the family");
            }
        }
        return bad;
    }

    /** Nothing so rare it is read about rather than met. */
    public static List<String> faultsInRarity(List<Kin> roster, int leastWeight) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (one.weight() < leastWeight) {
                bad.add(one.id() + " is chosen " + one.weight() + " times against a "
                        + "floor of " + leastWeight + ", so it is a creature most "
                        + "players will read about rather than meet");
            }
        }
        return bad;
    }

    /** Everything leaves something, and it is something that exists. */
    public static List<String> faultsInLeavings(List<Kin> roster) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            String left = leaves(one);
            if (left.isEmpty()) {
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

    /**
     * Every kindred is drawn with a shape this module has.
     *
     * <p>Two independent guards, because "no shape at all" and "a shape nobody
     * drew" are different mistakes with the same symptom — a creature that
     * renders as a black-and-magenta cube, found only by the player who meets
     * that one.
     */
    public static List<String> faultsInShapes(List<Kin> roster, List<String> forms) {
        List<String> bad = new ArrayList<>();
        for (Kin one : roster) {
            if (shapeOf(one).isEmpty()) {
                bad.add("the " + one.kin() + " kindred is drawn with nothing, so "
                        + one.id() + " is a creature this module cannot put on "
                        + "the screen");
            }
        }
        for (Kin one : roster) {
            String shape = shapeOf(one);
            if (!shape.isEmpty() && !forms.contains(shape)) {
                bad.add("the " + one.kin() + " kindred is drawn as a " + shape
                        + " and this module has no such shape, so " + one.id()
                        + " renders as a black-and-magenta cube for whoever "
                        + "meets it first");
            }
        }
        return bad;
    }
}
