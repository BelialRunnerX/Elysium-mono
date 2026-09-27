package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The half of a world that does not attack you.
 *
 * <h2>What was missing</h2>
 *
 * {@link BeastRules} is nine things that hunt you, and until this file existed
 * that was the whole of what this module knew a creature could be. A world
 * where every animal is hostile is not a harder world; it is a thinner one.
 * There is nothing to keep, nothing to breed, and no reason to build a fence.
 *
 * <h2>What a farm animal actually is, as a number</h2>
 *
 * A hostile mob is priced against <em>danger</em>: what it costs you to kill
 * it. A farm animal costs you almost nothing to kill and everything to
 * <em>wait for</em>, so it is priced against time. One animal is worth what it
 * gives divided by how long the pen has to hold it before it gives again —
 * items per hour of floor space — and that is the only number a player
 * optimises when they decide what to keep.
 *
 * <p>Which is why {@link #patienceOf} derives the wait from the yield rather
 * than the other way round. A yield is a whole number of items a player can
 * see; a wait is a number nobody can see and everybody feels. Typing both is
 * how a roster ends up with one animal that is strictly the best thing to keep
 * and eleven that are decoration — and the symptom is not a complaint, it is
 * a server where every pen contains the same animal.
 *
 * <h2>And the loop that would end the game</h2>
 *
 * {@link #faultsInLoop} is the rule this file would be dangerous without. If
 * anything an animal drops is anything an animal is bred with, a pen is a
 * machine that makes food out of nothing, and hunger — which the whole first
 * hour of the game is about — stops existing. Elysium-kinetics learned the
 * same lesson about multiplying recipes; this is that lesson with legs.
 */
public final class FaunaRules {

    private FaunaRules() {
    }

    // ==================================================================
    // The four kinds
    // ==================================================================

    /** Large, slow, out in the open, eats what grows in a field. */
    public static final String GRAZER = "grazer";

    /** Small, quick, in the undergrowth, eats what grows under a tree. */
    public static final String FORAGER = "forager";

    /** Light, flighty, eats seed. */
    public static final String FOWL = "fowl";

    /** In the water, and does not come out of it. */
    public static final String SWIMMER = "swimmer";

    /** The four, in the order everything walks them. */
    public static final List<String> KINDS =
            List.of(GRAZER, FORAGER, FOWL, SWIMMER);

    /** The fewest animals a kind may have and still be a kind. */
    public static final int LEAST_PER_KIND = 3;

    // ==================================================================
    // The arithmetic
    // ==================================================================

    /** Ticks in an hour of a world's time. */
    public static final int TICKS_AN_HOUR = 72_000;

    /**
     * How many items an hour of pen the middling animal is worth.
     *
     * <p>Not a balance decision so much as a unit: every other number here is
     * a multiple of it, so moving it moves the whole roster together and
     * cannot tilt one animal against another.
     */
    public static final double ITEMS_AN_HOUR = 30.0D;

    /**
     * How much better or worse than that each kind is.
     *
     * <p>A grazer is worth keeping more per hour because it needs a field to
     * stand in; a fowl is worth less because six of them fit in a cupboard.
     * The spread between the best and the worst of these is the whole reason
     * a player keeps more than one kind, and {@link #MOST_TIMES_ANOTHER}
     * holds it to something a player would call a preference rather than an
     * answer.
     */
    public static final Map<String, Double> TILT = Map.of(
            GRAZER, 1.25D,
            FORAGER, 1.00D,
            FOWL, 0.85D,
            SWIMMER, 1.05D);

    /**
     * How much better the best kind may be than the worst.
     *
     * <p>Borrowed on purpose from {@link BeastRules#MOST_TIMES_A_ZOMBIE}'s
     * neighbour, {@code outfarmed}: the claim there is that no one mob is
     * worth farming half again as much as any other, because the moment one
     * is, every farm in the pack is a farm for that one thing. A pen is the
     * same argument with a fence around it.
     */
    public static final double MOST_TIMES_ANOTHER = 1.5D;

    /** The shortest wait that still reads as a wait. */
    public static final int LEAST_PATIENCE = 1_200;

    /** And the longest anybody will put up with. */
    public static final int MOST_PATIENCE = 24_000;

    /** The fewest things a dead animal may leave. */
    public static final int LEAST_YIELD = 1;

    // ==================================================================
    // An animal
    // ==================================================================

    /**
     * One animal.
     *
     * @param id      what it is
     * @param kind    which of the four
     * @param health  how much it takes to kill
     * @param speed   how fast it wanders
     * @param width   how much floor it stands on
     * @param height  how tall it is
     * @param eats    the item it is bred with
     * @param gives   what it leaves
     * @param yield   how many of that, on average
     * @param weight  how often the world chooses it
     */
    public record Animal(String id, String kind, double health, double speed,
                         float width, float height, String eats, String gives,
                         int yield, int weight) {

        public Animal {
            Objects.requireNonNull(id, "an animal with no name");
            Objects.requireNonNull(kind, "an animal that is nothing in particular");
            Objects.requireNonNull(eats, "an animal that eats nothing, and so never breeds");
            Objects.requireNonNull(gives, "an animal that leaves nothing");
            if (health <= 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has no health or no speed");
            }
            if (width <= 0.0F || height <= 0.0F) {
                throw new IllegalArgumentException(id + " is not any size at all");
            }
            if (yield < LEAST_YIELD) {
                throw new IllegalArgumentException(id + " leaves " + yield
                        + " things, and an animal worth killing leaves at least "
                        + LEAST_YIELD);
            }
            if (weight <= 0) {
                throw new IllegalArgumentException(id + " is never chosen by anything");
            }
        }

        /** How much floor one of them stands on. */
        public double footprint() {
            return width * (double) width;
        }
    }

    /**
     * The twelve.
     *
     * <p>Three of each kind, and within a kind no two of them leave the same
     * number of things — because two animals that leave the same amount after
     * the same wait are one animal wearing two skins, and the one that takes
     * up less room wins. The yields are what a player sees; the waits are
     * worked out from them.
     */
    public static final List<Animal> ANIMALS = List.of(
            new Animal("fenstag", GRAZER, 12.0D, 0.28D, 1.3F, 1.6F,
                    "minecraft:wheat", "minecraft:mutton", 4, 60),
            new Animal("dunecharger", GRAZER, 15.0D, 0.32D, 1.4F, 1.8F,
                    "minecraft:carrot", "minecraft:rabbit_hide", 5, 30),
            new Animal("ferrox", GRAZER, 18.0D, 0.20D, 1.5F, 1.7F,
                    "minecraft:wheat", "minecraft:beef", 6, 40),
            new Animal("cragmole", FORAGER, 6.0D, 0.24D, 0.6F, 0.5F,
                    "minecraft:melon_slice", "minecraft:clay_ball", 2, 90),
            new Animal("quillmite", FORAGER, 8.0D, 0.30D, 0.7F, 0.6F,
                    "minecraft:beetroot", "minecraft:brown_mushroom", 3, 70),
            new Animal("brackhide", FORAGER, 10.0D, 0.26D, 0.9F, 0.9F,
                    "minecraft:potato", "minecraft:porkchop", 4, 50),
            new Animal("cragwing", FOWL, 4.0D, 0.34D, 0.4F, 0.6F,
                    "minecraft:pumpkin_seeds", "minecraft:egg", 1, 100),
            new Animal("mirewing", FOWL, 5.0D, 0.25D, 0.5F, 0.8F,
                    "minecraft:wheat_seeds", "minecraft:feather", 2, 80),
            new Animal("ashwing", FOWL, 6.0D, 0.22D, 0.6F, 0.7F,
                    "minecraft:melon_seeds", "minecraft:chicken", 3, 55),
            new Animal("glassfin", SWIMMER, 4.0D, 0.40D, 0.4F, 0.4F,
                    "minecraft:kelp", "minecraft:salmon", 2, 85),
            new Animal("chromefin", SWIMMER, 6.0D, 0.36D, 0.5F, 0.4F,
                    "minecraft:kelp", "minecraft:cod", 3, 65),
            new Animal("shaleshell", SWIMMER, 9.0D, 0.18D, 0.7F, 0.5F,
                    "minecraft:seagrass", "minecraft:tropical_fish", 4, 45));

    /** The animal with this id, or null. */
    public static Animal animal(String id) {
        for (Animal one : ANIMALS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Everything of one kind, in the order it was written. */
    public static List<Animal> kind(String kind) {
        List<Animal> found = new ArrayList<>();
        for (Animal one : ANIMALS) {
            if (one.kind().equals(kind)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** How much an hour of pen holding this kind is worth, in items. */
    public static double rateOf(String kind) {
        Double tilt = TILT.get(kind);
        if (tilt == null) {
            throw new IllegalArgumentException(kind + " is not a kind of animal, "
                    + "so nothing knows what an hour of keeping one is worth");
        }
        return ITEMS_AN_HOUR * tilt;
    }

    /**
     * How long a pen has to hold one before it gives again.
     *
     * <p>Derived, because this is the number nobody can see. An animal that
     * leaves twice as much waits twice as long, so a bigger animal is a
     * bigger payoff and never a better one — which is the whole of what stops
     * a roster collapsing onto its best row.
     */
    public static int patienceOf(Animal animal) {
        return (int) Math.round(animal.yield() * TICKS_AN_HOUR / rateOf(animal.kind()));
    }

    /** What one animal is actually worth an hour, worked out from the wait. */
    public static double worthAnHour(Animal animal) {
        return animal.yield() * (double) TICKS_AN_HOUR / patienceOf(animal);
    }

    /** Everything this roster eats. */
    public static Set<String> eaten(List<Animal> animals) {
        Set<String> found = new LinkedHashSet<>();
        for (Animal one : animals) {
            found.add(one.eats());
        }
        return found;
    }

    /** And everything it leaves. */
    public static Set<String> given(List<Animal> animals) {
        Set<String> found = new LinkedHashSet<>();
        for (Animal one : animals) {
            found.add(one.gives());
        }
        return found;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the fauna. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInKinds(ANIMALS));
        bad.addAll(faultsInGroups(ANIMALS));
        bad.addAll(faultsInYields(ANIMALS));
        bad.addAll(faultsInPatience(ANIMALS, LEAST_PATIENCE, MOST_PATIENCE));
        bad.addAll(faultsInSpread(TILT));
        bad.addAll(faultsInLoop(ANIMALS));
        bad.addAll(faultsInOverlap(ANIMALS, BeastRules.BEASTS));
        bad.addAll(faultsInDaylight(ANIMALS, HauntRules.THE_FAUNA));
        bad.addAll(faultsInReach(ANIMALS, HauntRules.THE_FAUNA));
        return List.copyOf(bad);
    }

    /**
     * Whether every animal is one of the four kinds, once.
     *
     * <p>Separate from {@link #faultsInGroups} because a roster built to be
     * caught by one of them trips the other as well: a single animal of a
     * made-up kind is also three kinds short of a roster, and a rule that
     * answers a control aimed at its neighbour is a rule that can be deleted
     * without anything noticing.
     */
    public static List<String> faultsInKinds(List<Animal> animals) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Animal one : animals) {
            if (!KINDS.contains(one.kind())) {
                bad.add(one.id() + " is a " + one.kind() + ", which is not a kind "
                        + "of animal, so nothing knows what an hour of keeping "
                        + "one is worth");
            }
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "
                        + "a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether each kind has enough in it to read as a kind. */
    public static List<String> faultsInGroups(List<Animal> animals) {
        List<String> bad = new ArrayList<>();
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Animal one : animals) {
            counted.merge(one.kind(), 1, Integer::sum);
        }
        for (String kind : KINDS) {
            int many = counted.getOrDefault(kind, 0);
            if (many < LEAST_PER_KIND) {
                bad.add("there " + (many == 1 ? "is " : "are ") + many + " "
                        + kind + "(s), and " + LEAST_PER_KIND + " is the fewest "
                        + "that reads as a kind of animal rather than as one "
                        + "animal with a label on it");
            }
        }
        return bad;
    }

    /**
     * Whether any animal is a strictly better version of another to keep.
     *
     * <p>Within a kind, because a fowl and a grazer are not two sizes of one
     * idea — the same categorical axis the bestiary needed for its families
     * and the ability system needed for its domains, and a rule without one
     * reports nine times and gets switched off.
     *
     * <h2>The two things a pen costs</h2>
     *
     * Floor, and trips. An animal is worth {@link #worthAnHour} items an hour
     * however big it is, so a small one gives more per square block of pen and
     * a big one gives more per walk out to it. Those are the two axes a player
     * actually trades between, and an animal that wins on both is an animal
     * that ends the choice: every pen becomes that animal's pen and the rest
     * of the kind is a texture pack.
     *
     * <p>Which is why the widths in {@link #ANIMALS} climb with the yields.
     * That is not decoration — it is what makes this rule pass, and the first
     * roster written here failed it three times out of four.
     */
    public static List<String> faultsInYields(List<Animal> animals) {
        List<String> bad = new ArrayList<>();
        for (Animal worse : animals) {
            for (Animal better : animals) {
                if (worse == better || !worse.kind().equals(better.kind())
                        || !KINDS.contains(worse.kind())) {
                    continue;
                }
                double betterFloor = worthAnHour(better) / better.footprint();
                double worseFloor = worthAnHour(worse) / worse.footprint();
                boolean everywhere = better.yield() >= worse.yield()
                        && betterFloor >= worseFloor;
                boolean somewhere = better.yield() > worse.yield()
                        || betterFloor > worseFloor;
                if (everywhere && somewhere) {
                    bad.add(worse.id() + " gives no more per trip than "
                            + better.id() + " and no more per square block of "
                            + "pen, and less of one of the two — so there is no "
                            + "arrangement of a fence in which keeping one is "
                            + "the right answer");
                }
            }
        }
        return bad;
    }

    /**
     * Whether the wait each yield works out to is a wait anybody would sit through.
     *
     * <p>Handed the two ends as well as the roster, because over this roster
     * the floor is out of reach: with the tilts this module ships, the
     * shortest wait any yield can work out to is longer than
     * {@link #LEAST_PATIENCE}, so a rule that read the constant itself could
     * only ever answer no about half of what it claims to check.
     */
    public static List<String> faultsInPatience(List<Animal> animals,
                                                int least, int most) {
        List<String> bad = new ArrayList<>();
        for (Animal one : animals) {
            if (!KINDS.contains(one.kind())) {
                continue;
            }
            int waits = patienceOf(one);
            if (waits < least) {
                bad.add(one.id() + " is ready again after " + waits + " ticks, "
                        + "which is under " + least + " — a pen that produces "
                        + "faster than a player can walk to it is a pile of "
                        + "items, not an animal");
            }
            if (waits > most) {
                bad.add(one.id() + " is ready again after " + waits + " ticks, "
                        + "which is over " + most + " — nobody waits that long "
                        + "and the animal is decoration");
            }
        }
        return bad;
    }

    /**
     * Whether anything is worth keeping so much more than anything else that
     * nothing else is worth keeping.
     *
     * <p>Handed the tilts rather than the roster, because over a roster this
     * rule can only ever answer no: what an hour of pen is worth depends on
     * the kind of animal and on nothing else, so the only table that can be
     * wrong is this one. A rule that reads the constant it judges re-solves
     * against the broken value and reports nothing — which this module has
     * been caught doing before, in the engine that drives a dynamo.
     */
    public static List<String> faultsInSpread(Map<String, Double> tilt) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            Double one = tilt.get(kind);
            if (one == null) {
                bad.add("nothing says what an hour of keeping a " + kind
                        + " is worth, so every one of them is a row of numbers "
                        + "the arithmetic throws on");
            }
            if (one != null && one <= 0.0D) {
                bad.add("an hour of " + kind + " is worth " + one + " times the "
                        + "middling animal, so keeping one costs a player time "
                        + "and gives them nothing");
            }
        }
        double best = 0.0D;
        double worst = Double.MAX_VALUE;
        String bestOne = "";
        String worstOne = "";
        for (Map.Entry<String, Double> entry : tilt.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                bestOne = entry.getKey();
            }
            if (entry.getValue() < worst) {
                worst = entry.getValue();
                worstOne = entry.getKey();
            }
        }
        if (worst > 0.0D && best > worst * MOST_TIMES_ANOTHER) {
            bad.add("an hour of " + bestOne + " is worth " + (best / worst)
                    + " times an hour of " + worstOne + ", which is more than "
                    + MOST_TIMES_ANOTHER + " — so every pen in the pack is a "
                    + bestOne + " pen and the other kinds are scenery");
        }
        return bad;
    }

    /**
     * Whether a pen makes food out of nothing.
     *
     * <p>The rule this file would be dangerous without. If anything an animal
     * leaves is anything an animal is bred with, then two animals and a fence
     * are a machine that feeds itself, and hunger — which the whole first hour
     * of a world is about — quietly stops existing. It would never be reported
     * as a bug; it would be reported as the pack being easy.
     */
    public static List<String> faultsInLoop(List<Animal> animals) {
        List<String> bad = new ArrayList<>();
        Set<String> food = eaten(animals);
        for (Animal one : animals) {
            if (food.contains(one.gives())) {
                List<String> fed = new ArrayList<>();
                for (Animal other : animals) {
                    if (other.eats().equals(one.gives())) {
                        fed.add(other.id());
                    }
                }
                bad.add(one.id() + " leaves " + one.gives() + " and that is what "
                        + fed + " is bred with, so a fence and two animals are a "
                        + "machine that makes food out of nothing and hunger "
                        + "stops being a thing that happens");
            }
        }
        return bad;
    }

    /**
     * Whether anything you can farm is something you would otherwise fight for.
     *
     * <p>Handed both rosters so a control can pass a pair built to be caught.
     * A pen that produces what the nullprime produces is a pen that makes
     * the nullprime pointless, and the bestiary's whole ladder is built on
     * the assumption that the only way to get what a beast leaves is to meet
     * one.
     */
    public static List<String> faultsInOverlap(List<Animal> animals,
                                               List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        Set<String> hunted = new LinkedHashSet<>();
        for (BeastRules.Beast beast : beasts) {
            hunted.addAll(BeastRules.leaves(beast));
        }
        for (Animal one : animals) {
            if (hunted.contains(one.gives())) {
                bad.add(one.id() + " leaves " + one.gives() + ", which is what "
                        + "something in the dark leaves — so the thing in the "
                        + "dark is now optional and the ladder it is on is a "
                        + "ladder nobody climbs");
            }
        }
        return bad;
    }

    /** The places a player is certain to end up whatever they do. */
    public static final List<String> WHERE_ANYBODY_GOES =
            List.of("#minecraft:is_overworld", "#minecraft:is_ocean");

    /**
     * Whether the land animals are out in the daylight.
     *
     * <p>Which is the whole difference between meeting an animal and meeting
     * something in {@link BeastRules#BEASTS}. A grazer whose light band starts
     * at nought is a grazer that spawns in caves among the things that hunt
     * it, and the symptom is not a bug report — it is a player who says the
     * pack's animals feel wrong and cannot say why.
     *
     * <p>Swimmers are exempt because the game has never cared what the light
     * is under water, and a rule that demanded daylight of a fish would be a
     * rule that had to be switched off.
     */
    public static List<String> faultsInDaylight(List<Animal> animals,
                                                List<HauntRules.Haunt> haunts) {
        List<String> bad = new ArrayList<>();
        for (Animal one : animals) {
            if (SWIMMER.equals(one.kind())) {
                continue;
            }
            HauntRules.Haunt where = null;
            for (HauntRules.Haunt haunt : haunts) {
                if (haunt.beast().equals(one.id())) {
                    where = haunt;
                }
            }
            if (where == null) {
                continue;
            }
            if (where.brightest() < HauntRules.FULL_LIGHT) {
                bad.add(one.id() + " never spawns above light "
                        + where.brightest() + ", so a thing that is supposed to "
                        + "be standing in a field is a thing you find in a cave");
            }
            if (where.darkest() <= HauntRules.PITCH_DARK) {
                bad.add(one.id() + " spawns down to pitch dark, so it appears "
                        + "in caves among the things that eat it and the half "
                        + "of the roster that does not attack you stops being "
                        + "a different half");
            }
        }
        return bad;
    }

    /**
     * Whether each kind is somewhere a player is certain to end up.
     *
     * <p>The same claim the bestiary makes about its families. A roster where
     * every kind wants a particular biome is a roster most players meet a
     * third of; one of each kind is somewhere unavoidable and the rest have
     * somewhere to be.
     */
    public static List<String> faultsInReach(List<Animal> animals,
                                             List<HauntRules.Haunt> haunts) {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            boolean anywhere = false;
            boolean any = false;
            for (Animal one : animals) {
                if (!one.kind().equals(kind)) {
                    continue;
                }
                any = true;
                for (HauntRules.Haunt haunt : haunts) {
                    if (haunt.beast().equals(one.id())
                            && WHERE_ANYBODY_GOES.contains(haunt.where())) {
                        anywhere = true;
                    }
                }
            }
            if (any && !anywhere) {
                bad.add("no " + kind + " is in any of " + WHERE_ANYBODY_GOES
                        + ", so a player who does not go looking never meets "
                        + "one and a quarter of the fauna is a rumour");
            }
        }
        return bad;
    }
}
