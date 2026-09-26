package com.elysium.bestiary.rules;

import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.ElysiumAbilityShapes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The two things an ability puts in the world, and what they are worth.
 *
 * <h2>The rule this file exists for</h2>
 *
 * A summon must not outlive the cooldown of the ability that made it.
 *
 * <p>That is the whole of it. An ability with a nine-hundred-tick cooldown that
 * calls up something lasting a thousand is an ability a player can have two of
 * at once, and then three, and the cost stops being a limit — a cooldown only
 * limits anything if what it produces is gone before the next one is allowed.
 * It is invisible in every screenshot, it looks exactly like a mod that is
 * generous, and the player who finds it never reports it.
 * {@link #faultsInLifetimes} is the rule, and it is measured against the
 * library's own cooldowns rather than against a number written here.
 *
 * <h2>And the second one: nothing here decides how hard it hits</h2>
 *
 * {@link ElysiumAbilityShapes#hurtOf} already works out what an ability is
 * worth in damage, from the power it was priced at. A bolt that carries its own
 * number is a second set of dials beside that one, and the two drift the first
 * time anybody tunes either. So {@link #hurtOfBolt} is a lookup into the
 * library, {@link #faultsInHurt} proves every thrown ability reaches it, and
 * the game-side class is forbidden a number of its own by the validator.
 *
 * <h2>Why a shade is weaker than what it fights</h2>
 *
 * A summon that out-fights the player is a summon the player stands behind, and
 * an ability that replaces you is not an ability. So a shade is held under the
 * weakest thing in {@link BeastRules#BEASTS} on damage — it helps — while being
 * allowed to outlast it on health, because something that dies on contact helps
 * with nothing. {@link #faultsInStanding} is that pair, and it takes the roster
 * in so a control can hand it one built to be caught.
 */
public final class SummonRules {

    private SummonRules() {
    }

    // ==================================================================
    // The two
    // ==================================================================

    /** What a thrown ability throws. */
    public static final String BOLT = ElysiumAbilityShapes.BOLT;

    /** What a summoning ability calls up. */
    public static final String SHADE = "shade";

    /** The two, in the order everything walks them. */
    public static final List<String> SUMMONS = List.of(BOLT, SHADE);

    /**
     * One thing an ability puts in the world.
     *
     * @param id      what it is called
     * @param shape   which of the library's shapes makes it
     * @param lasts   how long it stands before it goes out, in ticks
     * @param health  what it has, or 0 for something that cannot be hurt
     * @param damage  what it does on contact
     * @param wide    how wide it is
     * @param tall    and how tall
     * @param fights  whether it picks its own targets
     */
    public record Summon(String id, String shape, int lasts, double health,
                         double damage, float wide, float tall, boolean fights) {

        public Summon {
            if (lasts <= 0) {
                throw new IllegalArgumentException(id + " lasts " + lasts
                        + " ticks, which is no time at all, so nothing would "
                        + "ever see one");
            }
            if (health < 0.0D || damage < 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health and does " + damage + " damage, and neither "
                        + "is an amount of anything");
            }
            if (wide <= 0.0F || tall <= 0.0F) {
                throw new IllegalArgumentException(id + " is " + wide + " by "
                        + tall + ", which is not a size anything can be drawn at");
            }
        }
    }

    /**
     * What a thrown thing does on contact whatever else it is for.
     *
     * <p>The one number in this file that is a decision rather than a lookup,
     * and it is here because the first run of these rules found a bolt that did
     * nothing. A hunter's mark is priced in the domain "marking", which the
     * library rightly does not count as hurting — so its thrown bolt went out,
     * hit, and had no effect at all. Adding "marking" to the library's list of
     * hurting domains would have been a lie about what marking is; the truth is
     * that a thing thrown hard enough to reach you does something when it
     * arrives, whatever it was thrown for.
     */
    public static final double IMPACT = 2.0D;

    /**
     * The two, and their numbers.
     *
     * <p>A bolt cannot be hurt and does not pick targets: it goes where it was
     * thrown and stops at the first thing it meets. A shade can be hurt, and
     * the reason it can is that a summon nothing can kill is a summon with no
     * cost at all.
     */
    public static final List<Summon> SUMMONED = List.of(
            new Summon(BOLT, ElysiumAbilityShapes.BOLT, 60, 0.0D, IMPACT,
                    0.5F, 0.5F, false),
            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 20.0D, 3.0D,
                    0.6F, 1.9F, true));

    /** The summon of this name, or null. */
    public static Summon summon(String id) {
        for (Summon one : SUMMONED) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The summon a shape produces, or null. */
    public static Summon forShape(String shape) {
        for (Summon one : SUMMONED) {
            if (one.shape().equals(shape)) {
                return one;
            }
        }
        return null;
    }

    /** How fast a shade walks, against the beasts it fights beside. */
    public static final double SHADE_SPEED = 0.32D;

    /** How far it notices anything. */
    public static final double SHADE_NOTICES = 16.0D;

    /**
     * What a bolt thrown by this ability hits for.
     *
     * <p>The library priced the ability and derived the damage; this asks, and
     * puts a floor under the answer. Nothing here decides how much an ability
     * is worth — only that a thrown object is never weightless.
     */
    public static double hurtOfBolt(ElysiumAbilityRules.Ability ability) {
        return Math.max(IMPACT, ElysiumAbilityShapes.hurtOf(ability));
    }

    /** Every ability whose shape needs one of these to exist. */
    public static List<ElysiumAbilityRules.Ability> abilitiesThatSummon() {
        List<ElysiumAbilityRules.Ability> made = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            String shape = ElysiumAbilityShapes.shapeOf(ability.id());
            if (ElysiumAbilityShapes.NEED_AN_ENTITY.contains(shape)) {
                made.add(ability);
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the summons. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(SUMMONED));
        bad.addAll(faultsInShapes(SUMMONED, ElysiumAbilityShapes.NEED_AN_ENTITY));
        bad.addAll(faultsInGaps(SUMMONED, ElysiumAbilityShapes.NEED_AN_ENTITY));
        bad.addAll(faultsInLifetimes(SUMMONED, abilitiesThatSummon()));
        bad.addAll(faultsInFloor(IMPACT));
        bad.addAll(faultsInHurt(abilitiesThatSummon(), IMPACT));
        bad.addAll(faultsInPricing(abilitiesThatSummon(), IMPACT));
        bad.addAll(faultsInShadeExists(summon(SHADE)));
        bad.addAll(faultsInMeasure(BeastRules.BEASTS));
        bad.addAll(faultsInStanding(summon(SHADE), BeastRules.BEASTS));
        bad.addAll(faultsInFighting(SUMMONED));
        bad.addAll(faultsInSizes(SUMMONED));
        return List.copyOf(bad);
    }

    /** Whether two summons are written under one name. */
    public static List<String> faultsInNames(List<Summon> summons) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Summon one : summons) {
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "
                        + "a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether every summon is made by a shape the library actually has. */
    public static List<String> faultsInShapes(List<Summon> summons,
                                              List<String> shapes) {
        List<String> bad = new ArrayList<>();
        for (Summon one : summons) {
            if (!shapes.contains(one.shape())) {
                bad.add(one.id() + " is made by the shape " + one.shape()
                        + ", which is not one the library says needs an entity, "
                        + "so it is a mob nothing ever puts in the world");
            }
        }
        return bad;
    }

    /**
     * Whether a shape that needs an entity has one.
     *
     * <p>Its own rule, because the one above walks the summons and this walks
     * the shapes: a shape with nothing behind it is an ability that fires, pays
     * its cost, plays its sound and does nothing at all, and the summons list
     * cannot notice its own absence.
     */
    public static List<String> faultsInGaps(List<Summon> summons,
                                            List<String> shapes) {
        List<String> bad = new ArrayList<>();
        Set<String> made = new LinkedHashSet<>();
        for (Summon one : summons) {
            made.add(one.shape());
        }
        for (String shape : shapes) {
            if (!made.contains(shape)) {
                bad.add("nothing exists for the " + shape + " shape, so every "
                        + "ability of that shape pays its cost, plays its sound "
                        + "and does nothing at all");
            }
        }
        return bad;
    }

    /**
     * Whether anything outlives the cooldown that is supposed to limit it.
     *
     * <p>The rule this file exists for. Measured against the library's own
     * cooldowns, handed in, because a rule that reads the cooldown it is
     * judging re-solves against the broken value and goes on passing.
     */
    public static List<String> faultsInLifetimes(
            List<Summon> summons,
            List<ElysiumAbilityRules.Ability> abilities) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : abilities) {
            String shape = ElysiumAbilityShapes.shapeOf(ability.id());
            Summon made = null;
            for (Summon one : summons) {
                if (one.shape().equals(shape)) {
                    made = one;
                }
            }
            if (made == null) {
                continue;
            }
            if (made.lasts() >= ability.cooldown()) {
                bad.add(made.id() + " stands for " + made.lasts()
                        + " ticks and " + ability.id() + " comes round again "
                        + "after " + ability.cooldown() + ", so a player can "
                        + "have two at once and then three, and the cooldown "
                        + "stops being a limit");
            }
        }
        return bad;
    }

    /**
     * Whether a thrown thing arrives weightless by design.
     *
     * <p>Its own rule rather than the first clause of the one below, because a
     * floor of nought also makes every bolt worthless — so the control aimed
     * here was answered there, and this clause could have been deleted without
     * anything noticing.
     */
    public static List<String> faultsInFloor(double floor) {
        List<String> bad = new ArrayList<>();
        if (floor <= 0.0D) {
            bad.add("a thrown thing does " + floor + " on contact whatever it "
                    + "was thrown for, so a bolt goes out, hits, and nothing "
                    + "happens");
        }
        return bad;
    }

    /**
     * Whether any particular bolt arrives weightless.
     *
     * <p>Handed the floor, because a rule that reads the floor it is judging
     * re-solves against the broken value and goes on passing.
     */
    public static List<String> faultsInHurt(
            List<ElysiumAbilityRules.Ability> abilities, double floor) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : abilities) {
            if (!ElysiumAbilityShapes.BOLT.equals(
                    ElysiumAbilityShapes.shapeOf(ability.id()))) {
                continue;
            }
            if (Math.max(floor, ElysiumAbilityShapes.hurtOf(ability)) <= 0.0D) {
                bad.add(ability.id() + " throws something worth nothing on "
                        + "contact, so the bolt goes out, hits, and nothing "
                        + "happens");
            }
        }
        return bad;
    }

    /**
     * Whether the pricing buys anything for a bolt thrown to hurt.
     *
     * <p>Its own rule rather than a clause above, because the floor answers the
     * question the clause above asks: with a floor in place nothing is ever
     * weightless, which makes that rule exactly the one that cannot notice an
     * ability priced to hurt and hurting no more than one priced to mark.
     */
    public static List<String> faultsInPricing(
            List<ElysiumAbilityRules.Ability> abilities, double floor) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : abilities) {
            if (!ElysiumAbilityShapes.BOLT.equals(
                    ElysiumAbilityShapes.shapeOf(ability.id()))) {
                continue;
            }
            if (!ElysiumAbilityShapes.HURTS.contains(ability.domain())) {
                continue;
            }
            if (hurtOfBolt(ability) <= floor) {
                bad.add(ability.id() + " is priced in the hurting domain "
                        + ability.domain() + " and the bolt it throws hits for "
                        + hurtOfBolt(ability) + ", no more than the " + floor
                        + " anything thrown does by arriving, so what it was "
                        + "priced for bought nothing");
            }
        }
        return bad;
    }

    /**
     * Whether there is a shade at all.
     *
     * <p>Three rules where there was one, and the split is the same lesson
     * twice. The first draft returned early on a missing shade, so deleting
     * that guard did not report a missing shade — it threw on the next line.
     * And it returned early on an empty roster, so deleting *that* guard let
     * the comparison run against a floor of infinity and report something else
     * entirely. Neither control could reach the clause it was aimed at.
     */
    public static List<String> faultsInShadeExists(Summon shade) {
        List<String> bad = new ArrayList<>();
        if (shade == null) {
            bad.add("there is no shade at all, so the one summoning ability in "
                    + "the game calls up nothing");
        }
        return bad;
    }

    /** Whether there is anything to measure a shade against. */
    public static List<String> faultsInMeasure(List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        if (beasts.isEmpty()) {
            bad.add("there is nothing for a shade to be measured against, so "
                    + "whatever it is worth is unchecked");
        }
        return bad;
    }

    /**
     * Whether a shade helps rather than replaces.
     *
     * <p>Under the weakest beast on damage, and over it on health. The pair
     * matters: a summon that out-hits what it fights is a summon the player
     * stands behind, and a summon that dies on contact is one they stop
     * bothering to call.
     */
    public static List<String> faultsInStanding(Summon shade,
                                                List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        if (shade == null || beasts.isEmpty()) {
            return bad;
        }
        double weakestHurt = Double.MAX_VALUE;
        double weakestHealth = Double.MAX_VALUE;
        String onHurt = "";
        String onHealth = "";
        for (BeastRules.Beast beast : beasts) {
            if (beast.damage() < weakestHurt) {
                weakestHurt = beast.damage();
                onHurt = beast.id();
            }
            if (beast.health() < weakestHealth) {
                weakestHealth = beast.health();
                onHealth = beast.id();
            }
        }
        if (shade.damage() > weakestHurt) {
            bad.add("a shade hits for " + shade.damage() + " and the weakest "
                    + "thing in the world, " + onHurt + ", hits for "
                    + weakestHurt + ", so the summon out-fights what it was "
                    + "called to help with and the player stands behind it");
        }
        if (shade.health() < weakestHealth) {
            bad.add("a shade has " + shade.health() + " health and " + onHealth
                    + " has " + weakestHealth + ", so it dies on contact and "
                    + "nobody calls one twice");
        }
        return bad;
    }

    /**
     * Whether the thing that picks targets is the one that should.
     *
     * <p>A bolt that chose its own target would be a homing bolt, which is a
     * different ability from the one that was priced. A shade that did not
     * would stand there.
     */
    public static List<String> faultsInFighting(List<Summon> summons) {
        List<String> bad = new ArrayList<>();
        for (Summon one : summons) {
            boolean thrown = ElysiumAbilityShapes.BOLT.equals(one.shape());
            if (thrown && one.fights()) {
                bad.add(one.id() + " is thrown and picks its own targets, which "
                        + "is a homing bolt rather than the ability that was "
                        + "priced");
            }
            if (!thrown && !one.fights()) {
                bad.add(one.id() + " is called up and picks no targets, so it "
                        + "stands where it was put and does nothing");
            }
        }
        return bad;
    }

    /** Whether anything is a size the game can draw. */
    public static List<String> faultsInSizes(List<Summon> summons) {
        List<String> bad = new ArrayList<>();
        for (Summon one : summons) {
            if (one.wide() > 4.0F || one.tall() > 4.0F) {
                bad.add(one.id() + " is " + one.wide() + " by " + one.tall()
                        + ", which is bigger than any door a player has built, "
                        + "so it is a summon that cannot follow them indoors");
            }
        }
        return bad;
    }
}
