package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The awakening altar, the sixth rung, and the one thing awakening is not.
 *
 * <h2>Why there is no tier-six seed</h2>
 *
 * The jar this extends has a sixth tier of crops: a nether star crop, a dragon
 * egg crop, an awakened draconium crop. Every one of them grows something a
 * boss drops or a machine makes, and {@link SeedRules#growingWhatIsMade} is
 * the check that says a seed may only grow what the ground gives you. Which
 * is not the check being wrong. A field that yields nether stars is not a
 * farm competing with a pickaxe; it is a farm competing with the Wither.
 *
 * <p>So the sixth rung is not grown. {@link #INSANIUM} is made at an altar
 * out of the fifth, which is the only way to have a rung above the top of the
 * ladder without reaching around the rule the ladder is built on.
 *
 * <h2>What awakening actually buys</h2>
 *
 * Not more. A farm is held to {@link AgronomyRules#FARM_AT_MOST} of a mine and
 * an awakened farm is held to exactly the same share — awakening a seed
 * doubles what one plot yields and <em>halves</em> how many plots may be run
 * before the margin is crossed. The player gets the same metal out of half
 * the ground.
 *
 * <p>Which sounds like nothing and is in fact the most valuable thing this
 * module can give away, because the one cost of a resource farm that no rule
 * here prices is <em>space</em>. Two hundred plots is a room. A hundred is a
 * cellar. And it costs the economy nothing at all, which is why it is the
 * reward rather than a bigger number.
 *
 * <h2>The altar itself</h2>
 *
 * A centre, four pedestals on the diagonals and four relays further out on
 * the axes — a shape rather than a recipe, so the things that can be wrong
 * with it are geometric and can be proved: nothing outside the box it claims,
 * no two in one place, every one of them the same distance out as its three
 * mirror images, and the whole arrangement unchanged when it is turned a
 * quarter turn. An altar that is not symmetric is an altar that works facing
 * one way and not another, and nothing in the game would say which.
 */
public final class AltarRules {

    private AltarRules() {
    }

    // ==================================================================
    // The sixth rung
    // ==================================================================

    /** What the rung above the top of the seed ladder is called. */
    public static final String INSANIUM = "insanium_essence";

    /**
     * How many of the fifth make one of the sixth.
     *
     * <p>The same step the ladder uses everywhere else, so the sixth rung
     * costs what a rung costs and the altar is not a discount.
     */
    public static final int PER_INSANIUM = AgronomyRules.ESSENCE_PER_STEP;

    /** What the top of the seed ladder is, which is what feeds this. */
    public static int topTier() {
        return AgronomyRules.TIERS;
    }

    /**
     * How long a farm takes to make one insanium, in ticks of one plot.
     *
     * <p>Worked out from the ladder rather than stated, so a change to a
     * growth time moves it. It is the check below that matters: the sixth
     * rung must cost at least what four of the fifth cost, or the altar is a
     * machine for making essence out of nothing.
     */
    public static float ticksPerInsanium() {
        return (float) AgronomyRules.ticksAt(topTier()) * PER_INSANIUM
                / Math.max(1, AgronomyRules.FIRST_CROP.yield());
    }

    // ==================================================================
    // What an awakened seed is
    // ==================================================================

    /** How much more one plot of an awakened seed yields. */
    public static final int AWAKENED_YIELD = 2;

    /** What an awakened seed of a material is called. */
    public static String awakenedSeed(String material) {
        return "awakened_" + material + "_seed";
    }

    /** Every awakened seed there is, one per ordinary seed. */
    public static List<String> awakenedSeeds() {
        List<String> made = new ArrayList<>();
        for (SeedRules.Seed seed : SeedRules.SEEDS) {
            made.add(awakenedSeed(seed.material()));
        }
        return List.copyOf(made);
    }

    /** How much an awakened farm of this many plots yields in an hour. */
    public static float awakenedPerHour(int tier, int plots) {
        return AgronomyRules.farmedPerHour(tier, plots) * AWAKENED_YIELD;
    }

    /**
     * The largest awakened farm that stays under the same margin.
     *
     * <p>Half the ordinary one, near enough, and "near enough" is the whole
     * point: it is worked out from the same margin rather than stated as a
     * half, so the two can never drift apart.
     */
    public static int awakenedPlotsBefore(int tier) {
        float allowed = AgronomyRules.minedPerHour() * AgronomyRules.FARM_AT_MOST;
        float each = awakenedPerHour(tier, 1);
        return each <= 0.0F ? Integer.MAX_VALUE : (int) Math.floor(allowed / each);
    }

    /** What an awakened farm at its allowed size earns. */
    public static float awakenedAtItsLargest(int tier) {
        return awakenedPerHour(tier, awakenedPlotsBefore(tier));
    }

    /** What an ordinary farm at its allowed size earns. */
    public static float ordinaryAtItsLargest(int tier) {
        return AgronomyRules.farmedPerHour(tier, AgronomyRules.plotsBefore(tier));
    }

    /**
     * How far apart those two are allowed to be, as a share.
     *
     * <p>Not zero, because both are floors of a division and a floor loses
     * up to one plot's worth. One plot of the tier in question is the honest
     * tolerance and anything wider would let awakening quietly be a buff.
     */
    public static boolean sameEarnings(int tier) {
        return sameEarnings(awakenedAtItsLargest(tier),
                ordinaryAtItsLargest(tier), awakenedPerHour(tier, 1));
    }

    /**
     * The same, over two numbers handed in.
     *
     * <p>Split out so a control can hand it a pair built to be caught. Over a
     * healthy ladder the two are always the same by construction, which means
     * the rule could be {@code return true} and every check in this file would
     * go on passing — the exact shape of failure this repository has shipped
     * before.
     */
    public static boolean sameEarnings(float awakened, float ordinary,
                                       float onePlot) {
        return Math.abs(awakened - ordinary) <= onePlot + 0.001F;
    }

    // ==================================================================
    // The shape of the altar
    // ==================================================================

    /** One block of the altar, offset from the centre. */
    public record Place(String kind, int east, int north) {

        public Place {
            Objects.requireNonNull(kind, "a place with no kind");
        }

        /** How far out it sits, in blocks, on the longer axis. */
        public int reach() {
            return Math.max(Math.abs(east), Math.abs(north));
        }

        /** The same place a quarter turn clockwise. */
        public Place turned() {
            return new Place(kind, -north, east);
        }
    }

    /** What sits in the middle and holds what is being awakened. */
    public static final String CENTRE = "awakening_altar";

    /** What holds the essence. */
    public static final String PEDESTAL = "essence_pedestal";

    /** What carries it in from further out. */
    public static final String RELAY = "essence_relay";

    /** How far out a pedestal sits. */
    public static final int PEDESTAL_REACH = 2;

    /** How far out a relay sits. */
    public static final int RELAY_REACH = 4;

    /** The furthest anything may be from the centre. */
    public static final int THE_BOX = RELAY_REACH;

    /**
     * Every block of the altar besides the centre.
     *
     * <p>Built by turning one of each a quarter turn at a time rather than
     * typed out four times. Four typed corners is four places for a sign to
     * be wrong, and the symmetry check below would then be checking what
     * somebody typed rather than what the shape is.
     */
    public static final List<Place> AROUND = around();

    private static List<Place> around() {
        List<Place> made = new ArrayList<>();
        Place pedestal = new Place(PEDESTAL, PEDESTAL_REACH, PEDESTAL_REACH);
        Place relay = new Place(RELAY, RELAY_REACH, 0);
        for (Place one : List.of(pedestal, relay)) {
            Place turning = one;
            for (int quarter = 0; quarter < 4; quarter++) {
                made.add(turning);
                turning = turning.turned();
            }
        }
        return List.copyOf(made);
    }

    /** How many pedestals an altar has. */
    public static int pedestals() {
        return (int) AROUND.stream().filter(one -> PEDESTAL.equals(one.kind())).count();
    }

    /** How many relays. */
    public static int relays() {
        return (int) AROUND.stream().filter(one -> RELAY.equals(one.kind())).count();
    }

    /** Every block the altar is made of, centre first. */
    public static List<String> everything() {
        return List.of(CENTRE, PEDESTAL, RELAY);
    }

    // ==================================================================
    // What the altar does
    // ==================================================================

    /**
     * One awakening.
     *
     * @param id      what the recipe is called
     * @param takes   what goes in the middle, or "" when nothing does
     * @param each    how many essences each pedestal holds
     * @param essence which essence the pedestals hold
     * @param makes   what comes out
     */
    public record Awakening(String id, String takes, int each, String essence,
                            String makes) {

        public Awakening {
            Objects.requireNonNull(id, "an awakening with no name");
            Objects.requireNonNull(makes, "an awakening that makes nothing");
            if (each <= 0) {
                throw new IllegalArgumentException(id + " asks for " + each
                        + " essence a pedestal, which is an altar that does "
                        + "nothing for free");
            }
        }

        /** How much essence the whole altar swallows. */
        public int essences() {
            return each * pedestals();
        }
    }

    /**
     * Everything an altar can do.
     *
     * <p>The sixth rung, and one awakening per seed. Walked from the seed
     * roster rather than listed, so a seed added to the ladder is a seed that
     * can be awakened the same day — and the count check is what catches the
     * one somebody forgot.
     */
    public static List<Awakening> AWAKENINGS = awakenings();

    private static List<Awakening> awakenings() {
        List<Awakening> made = new ArrayList<>();
        String top = topEssence();
        made.add(new Awakening(INSANIUM, "", PER_INSANIUM, top, INSANIUM));
        for (SeedRules.Seed seed : SeedRules.SEEDS) {
            made.add(new Awakening(awakenedSeed(seed.material()), seed.id(),
                    PER_INSANIUM, INSANIUM, awakenedSeed(seed.material())));
        }
        return List.copyOf(made);
    }

    /**
     * What the essence at the top of the seed ladder is called.
     *
     * <p>Written here and checked there. The five names live in {@code
     * AgronomyItems}, which is a registry and imports the game, so this file
     * cannot read them — and a rules file that guessed {@code
     * essence_of_tier_5} would be naming an item that has never existed. So
     * it is stated once, and {@code validate.py} reads the registry's own
     * list and fails the build if the last of them is not this.
     */
    public static final String TOP_ESSENCE = "ascendant_essence";

    /** The same, as a call, for the places that read it as one. */
    public static String topEssence() {
        return TOP_ESSENCE;
    }

    /** How many awakenings there are. */
    public static int howMany() {
        return AWAKENINGS.size();
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the altar. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInShape(AROUND));
        bad.addAll(faultsInAwakenings(AWAKENINGS));
        bad.addAll(faultsInEconomy());
        return List.copyOf(bad);
    }

    /**
     * The shape: inside its box, nothing twice, and the same turned.
     *
     * <p>Taken as an argument so a control can hand it a shape built to be
     * caught. A rule that has only ever been asked about one shape is a rule
     * that has never been shown able to say no.
     */
    public static List<String> faultsInShape(List<Place> around) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInBox(around));
        bad.addAll(faultsInDoubles(around));
        bad.addAll(faultsInSymmetry(around));
        if (around.isEmpty()) {
            bad.add("the altar is a single block, which is a crafting table");
        }
        return bad;
    }

    /**
     * Anything outside the box the altar claims.
     *
     * <p>Its own rule rather than one of three in a heap, because a control
     * for this one built out of a single far-out pedestal is also asymmetric,
     * and then the symmetry rule answers it and this one could be deleted
     * with every check still green.
     */
    public static List<String> faultsInBox(List<Place> around) {
        List<String> bad = new ArrayList<>();
        for (Place one : around) {
            if (one.reach() > THE_BOX) {
                bad.add(one.kind() + " at " + one.east() + "," + one.north()
                        + " is " + one.reach() + " blocks out and the altar "
                        + "claims " + THE_BOX + ", so it reaches into the chunk "
                        + "next door");
            }
        }
        return bad;
    }

    /** Two blocks of the altar in one place, or one standing on the centre. */
    public static List<String> faultsInDoubles(List<Place> around) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Place one : around) {
            String where = one.east() + "," + one.north();
            if (!seen.add(where)) {
                bad.add("two blocks of the altar stand at " + where + ", and one "
                        + "of them is a block the other is already in");
            }
            if (one.east() == 0 && one.north() == 0) {
                bad.add(one.kind() + " stands where the centre is");
            }
        }
        return bad;
    }

    /**
     * A shape that is not the same shape turned a quarter turn.
     *
     * <p>An altar that is not symmetric works facing one way and not another,
     * and nothing in the game would ever say which.
     */
    public static List<String> faultsInSymmetry(List<Place> around) {
        List<String> bad = new ArrayList<>();
        Set<String> places = new LinkedHashSet<>();
        for (Place one : around) {
            places.add(one.kind() + "@" + one.east() + "," + one.north());
        }
        for (Place one : around) {
            Place turned = one.turned();
            String where = turned.kind() + "@" + turned.east() + "," + turned.north();
            if (!places.contains(where)) {
                bad.add(one.kind() + " at " + one.east() + "," + one.north()
                        + " has nothing at " + turned.east() + "," + turned.north()
                        + " a quarter turn away, so the altar is not the same "
                        + "altar turned round and it will work facing one way "
                        + "and not another");
            }
        }
        return bad;
    }

    /** The awakenings: named once, fed by something, making something else. */
    public static List<String> faultsInAwakenings(List<Awakening> awakenings) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Awakening one : awakenings) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two awakenings, so one of them is a "
                        + "recipe the altar will never reach");
            }
            if (one.makes().equals(one.takes())) {
                bad.add(one.id() + " makes what it takes, which is an altar that "
                        + "eats essence and hands the item straight back");
            }
            if (one.essence().isEmpty()) {
                bad.add(one.id() + " asks for no essence at all");
            }
            if (one.essences() <= 0) {
                bad.add(one.id() + " swallows no essence, so the altar is free");
            }
        }
        if (awakenings.stream().noneMatch(one -> INSANIUM.equals(one.makes()))) {
            bad.add("nothing an altar does makes " + INSANIUM + ", so the sixth "
                    + "rung is a name with no way up to it");
        }
        int seeds = 0;
        for (Awakening one : awakenings) {
            if (one.makes().startsWith("awakened_")) {
                seeds++;
            }
        }
        if (seeds != SeedRules.SEEDS.size()) {
            bad.add("there are " + SeedRules.SEEDS.size() + " seeds and " + seeds
                    + " of them can be awakened, so a seed somebody added is one "
                    + "the altar has never heard of");
        }
        return bad;
    }

    /**
     * The economy: awakening buys space and nothing else.
     *
     * <p>The thing this check exists for is the temptation. Doubling a yield
     * is a satisfying reward to write and it is a farm that beats a mine, and
     * the difference between the two is one line about how many plots are
     * allowed.
     */
    public static List<String> faultsInEconomy() {
        List<String> bad = new ArrayList<>();
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            if (!sameEarnings(tier)) {
                bad.add("an awakened farm of tier " + tier + " at its largest "
                        + "earns " + awakenedAtItsLargest(tier)
                        + " an hour and an ordinary one earns "
                        + ordinaryAtItsLargest(tier)
                        + ", so awakening is a bigger number rather than a "
                        + "smaller room and the margin against the mine has "
                        + "moved");
            }
            if (awakenedPlotsBefore(tier) >= AgronomyRules.plotsBefore(tier)) {
                bad.add("an awakened farm of tier " + tier + " may be "
                        + awakenedPlotsBefore(tier) + " plots and an ordinary "
                        + "one " + AgronomyRules.plotsBefore(tier)
                        + ", so awakening buys nothing at all");
            }
            if (awakenedPerHour(tier, 1)
                    <= AgronomyRules.farmedPerHour(tier, 1)) {
                bad.add("one plot of an awakened tier " + tier + " seed yields "
                        + "no more than an ordinary one");
            }
        }
        if (AWAKENED_YIELD < 2) {
            bad.add("an awakened seed yields " + AWAKENED_YIELD
                    + " times an ordinary one, which is an ordinary one");
        }
        if (PER_INSANIUM < AgronomyRules.ESSENCE_PER_STEP) {
            bad.add("the sixth rung costs " + PER_INSANIUM + " of the fifth and "
                    + "every other rung costs " + AgronomyRules.ESSENCE_PER_STEP
                    + ", so the altar is a discount on the ladder it sits on top "
                    + "of");
        }
        return bad;
    }
}
