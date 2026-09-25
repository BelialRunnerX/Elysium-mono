package com.elysium.lib.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an ability actually does, as opposed to what it is worth.
 *
 * <h2>The gap this closes</h2>
 *
 * {@link ElysiumAbilityRules} prices thirty abilities against each other and
 * proves no two of them are the same idea at two strengths. Thirty relics
 * carry them, every relic is registered, the harness passes — and pressing
 * the button did nothing at all, because nothing anywhere said what any of
 * them <em>does</em>.
 *
 * <p>That is the worst state a system can be in. It is not missing; it is
 * complete and inert, and every check that exists passes.
 *
 * <h2>Six shapes and no more</h2>
 *
 * An ability does one of six things, and the list is deliberately short. A
 * seventh shape is a seventh branch in the one place that performs them, and
 * the thing that goes wrong with ability systems is that they grow one bespoke
 * branch per ability until nothing can be said about them as a set.
 *
 * <p>So: what an ability's shape is, is a decision, written down once here.
 * <b>How much it does is not.</b> Every magnitude — a radius, a duration, a
 * number of summons — is derived from what the ability is already worth, so
 * an ability that is priced higher does more by arithmetic rather than by
 * somebody remembering to type a bigger number.
 */
public final class ElysiumAbilityShapes {

    private ElysiumAbilityShapes() {
    }

    // ==================================================================
    // The six
    // ==================================================================

    /** Something happens to you. */
    public static final String SELF = "self";

    /** Something happens to what you just hit. */
    public static final String TOUCH = "touch";

    /** Something happens to everything near you. */
    public static final String AREA = "area";

    /** Something is thrown. */
    public static final String BOLT = "bolt";

    /** Something is called up that fights for you. */
    public static final String SUMMON = "summon";

    /** You are somewhere else. */
    public static final String STEP = "step";

    /** Every shape there is. */
    public static final List<String> SHAPES =
            List.of(SELF, TOUCH, AREA, BOLT, SUMMON, STEP);

    /** The shapes that need an entity to exist before they can happen at all. */
    public static final List<String> NEED_AN_ENTITY = List.of(BOLT, SUMMON);

    /**
     * What each ability does.
     *
     * <p>The one table here that is a set of decisions rather than a
     * derivation, and every one of them is a sentence: a cleave hits
     * everything around you, a sunder hits the one thing you hit, a stormcall
     * is thrown. Total over the roster, and {@link #faultsInShapes} says so.
     */
    public static final Map<String, String> SHAPE_OF = shapeOf();

    private static Map<String, String> shapeOf() {
        Map<String, String> made = new LinkedHashMap<>();
        // Always on: a modifier while it is worn, and nothing else.
        for (String id : List.of("steadfast", "swiftfoot", "keen_edge",
                "deep_lungs", "sure_grip", "night_eyes", "ironhide", "scholar")) {
            made.put(id, SELF);
        }
        // What happens to the thing in front of you.
        for (String id : List.of("riposte", "thornmail", "sunder",
                "chill_touch", "ember_brand")) {
            made.put(id, TOUCH);
        }
        // What happens to everything around one of you.
        for (String id : List.of("cleave", "static_arc", "gleaner",
                "shockwave", "sanctuary")) {
            made.put(id, AREA);
        }
        // What happens to you, after something else happened.
        for (String id : List.of("bloodscent", "soul_harvest", "second_wind",
                "lifeline", "mender", "ward_pulse", "bulwark", "veilstep")) {
            made.put(id, SELF);
        }
        made.put("hunters_mark", BOLT);
        made.put("stormcall", BOLT);
        made.put("gravewake", SUMMON);
        made.put("blink", STEP);
        return Map.copyOf(made);
    }

    /** What one ability does, or empty when nothing says. */
    public static String shapeOf(String id) {
        return SHAPE_OF.getOrDefault(id, "");
    }

    /** Every ability of one shape. */
    public static List<String> ofShape(String shape) {
        List<String> made = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            if (shape.equals(shapeOf(ability.id()))) {
                made.add(ability.id());
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // How much, derived rather than typed
    // ==================================================================

    /**
     * The reach of anything that reaches, in blocks.
     *
     * <p>Off what the ability is worth rather than off a number typed beside
     * it, so a costlier ability reaches further because it is costlier. The
     * square root is there because area goes as the square of a radius and
     * worth ought to go with the area rather than with the line: a reach
     * proportional to worth would make the top ability cover sixteen times
     * the ground of the bottom one rather than four.
     */
    public static float reachOf(ElysiumAbilityRules.Ability ability) {
        return LEAST_REACH + (MOST_REACH - LEAST_REACH)
                * (float) Math.sqrt(fraction(ability));
    }

    /** The smallest reach anything has. */
    public static final float LEAST_REACH = 2.0F;

    /** The largest reach anything has. */
    public static final float MOST_REACH = 8.0F;

    /**
     * How long what an ability does lasts, in ticks.
     *
     * <p>A passive is always on and lasts for ever, which is written as
     * nought rather than as a large number, because a large number is a
     * number somebody will one day compare against.
     */
    public static int lastsOf(ElysiumAbilityRules.Ability ability) {
        if (ElysiumAbilityRules.PASSIVE.equals(ability.kind())) {
            return 0;
        }
        return Math.round(LEAST_TICKS + (MOST_TICKS - LEAST_TICKS)
                * fraction(ability));
    }

    /** The shortest anything an ability does lasts. */
    public static final int LEAST_TICKS = 60;

    /** The longest anything an ability does lasts. */
    public static final int MOST_TICKS = 600;

    /**
     * How hard an ability hits, as a multiple of a bare hand.
     *
     * <p>Zero for anything whose domain is not about hurting, because an
     * ability that healed you and also did damage would be two abilities and
     * the rule that nothing dominates anything else would have nothing to
     * compare.
     */
    public static float hurtOf(ElysiumAbilityRules.Ability ability) {
        return HURTS.contains(ability.domain())
                ? ability.power() * HURT_PER_POWER : 0.0F;
    }

    /** The domains that are about hurting something. */
    public static final Set<String> HURTS =
            Set.of("offence", "sundering", "burning", "chaining");

    /** How much damage one point of power is worth. */
    public static final float HURT_PER_POWER = 2.0F;

    /**
     * How many things an ability brings with it.
     *
     * <p>One, and more only for the things that are about numbers. A summon
     * that called up six of something would be six entities to path and six
     * to render, which is how an ability system becomes a performance
     * problem.
     */
    public static int countOf(ElysiumAbilityRules.Ability ability) {
        String shape = shapeOf(ability.id());
        if (!SUMMON.equals(shape) && !BOLT.equals(shape)) {
            return 0;
        }
        return Math.max(1, Math.min(MOST_AT_ONCE,
                Math.round(ability.worth())));
    }

    /** The most of anything one ability may put in the world. */
    public static final int MOST_AT_ONCE = 4;

    /** Where an ability sits between the floor and the ceiling, nought to one. */
    public static float fraction(ElysiumAbilityRules.Ability ability) {
        float span = ElysiumAbilityRules.CEILING - ElysiumAbilityRules.FLOOR;
        if (span <= 0.0F) {
            return 0.0F;
        }
        float where = (ability.worth() - ElysiumAbilityRules.FLOOR) / span;
        return Math.max(0.0F, Math.min(1.0F, where));
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the shapes. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInShapes(ElysiumAbilityRules.ABILITIES, SHAPE_OF));
        bad.addAll(faultsInReach(ElysiumAbilityRules.ABILITIES));
        bad.addAll(faultsInCount(ElysiumAbilityRules.ABILITIES));
        return List.copyOf(bad);
    }

    /**
     * Whether every ability does something and everything that is said to do
     * something exists.
     *
     * <p>Handed both sides so a control can pass a pair built to be caught.
     * Over the roster this ships, both answers are always no.
     */
    public static List<String> faultsInShapes(
            List<ElysiumAbilityRules.Ability> abilities,
            Map<String, String> shapes) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInEachShape(abilities, shapes));
        bad.addAll(faultsInOrphans(abilities, shapes));
        bad.addAll(faultsInCoverage(abilities, shapes));
        return bad;
    }

    /**
     * Whether every ability does something, and something real.
     *
     * <p>Kept apart from the two rules below it for the reason this project
     * keeps finding: a method holding all three answers every control with
     * whichever fires first. Hand it one ability and no shapes at all to
     * prove it notices an ability that does nothing, and it reports instead
     * that five of the six shapes have nothing of that shape.
     */
    public static List<String> faultsInEachShape(
            List<ElysiumAbilityRules.Ability> abilities,
            Map<String, String> shapes) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : abilities) {
            String shape = shapes.getOrDefault(ability.id(), "");
            // Two ifs rather than an if/else, and the second guarded on the
            // shape being there at all. An else-if answers the control aimed
            // at the if above it — switch the first off and the second
            // reports the same ability for a different reason, the control
            // passes, and a rule that no longer works has never been seen not
            // to work.
            if (shape.isEmpty()) {
                bad.add(ability.id() + " is priced, carried and registered and "
                        + "nothing says what it does, so pressing the button "
                        + "does nothing and every check there is passes");
            }
            if (!shape.isEmpty() && !SHAPES.contains(shape)) {
                bad.add(ability.id() + " is a " + shape + ", which is not one "
                        + "of the six things an ability can be");
            }
        }
        return bad;
    }

    /** Whether anything is given a shape that is not an ability. */
    public static List<String> faultsInOrphans(
            List<ElysiumAbilityRules.Ability> abilities,
            Map<String, String> shapes) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (ElysiumAbilityRules.Ability ability : abilities) {
            named.add(ability.id());
        }
        for (String id : shapes.keySet()) {
            if (!named.contains(id)) {
                bad.add(id + " is given a shape and is not an ability, so the "
                        + "table is a list of what somebody meant to write");
            }
        }
        return bad;
    }

    /** Whether every one of the six shapes is something an ability is. */
    public static List<String> faultsInCoverage(
            List<ElysiumAbilityRules.Ability> abilities,
            Map<String, String> shapes) {
        List<String> bad = new ArrayList<>();
        for (String shape : SHAPES) {
            boolean any = false;
            for (ElysiumAbilityRules.Ability ability : abilities) {
                any |= shape.equals(shapes.getOrDefault(ability.id(), ""));
            }
            if (!any) {
                bad.add("nothing is a " + shape + ", so one of the six ways an "
                        + "ability can happen is a branch nothing reaches");
            }
        }
        return bad;
    }

    /**
     * Whether a costlier ability reaches further.
     *
     * <p>The whole reason the magnitudes are derived. Handed the roster so a
     * control can pass one where they are not.
     */
    public static List<String> faultsInReach(
            List<ElysiumAbilityRules.Ability> abilities) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability one : abilities) {
            float reach = reachOf(one);
            if (reach < LEAST_REACH - 1.0E-3F || reach > MOST_REACH + 1.0E-3F) {
                bad.add(one.id() + " reaches " + reach + " blocks and nothing "
                        + "reaches less than " + LEAST_REACH + " or more than "
                        + MOST_REACH);
            }
            for (ElysiumAbilityRules.Ability other : abilities) {
                if (one.worth() > other.worth() + 1.0E-3F
                        && reachOf(one) < reachOf(other) - 1.0E-3F) {
                    bad.add(one.id() + " is worth more than " + other.id()
                            + " and reaches less far, so what an ability does "
                            + "and what it costs have come apart");
                }
                if (one.worth() > other.worth() + 1.0E-3F
                        && lastsOf(one) < lastsOf(other)
                        && !ElysiumAbilityRules.PASSIVE.equals(one.kind())
                        && !ElysiumAbilityRules.PASSIVE.equals(other.kind())) {
                    bad.add(one.id() + " is worth more than " + other.id()
                            + " and lasts less long");
                }
            }
        }
        return bad;
    }

    /** Whether anything puts an unreasonable number of things in the world. */
    public static List<String> faultsInCount(
            List<ElysiumAbilityRules.Ability> abilities) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInThrowing(abilities, counts(abilities)));
        bad.addAll(faultsInCeiling(abilities));
        return bad;
    }

    /**
     * Whether what throws something throws something, and nothing else does.
     *
     * <p><b>Handed the counts</b> rather than working them out. Working them
     * out is what makes this rule unable to fail: {@link #countOf} floors a
     * throwing ability at one, so the rule can only fire if the thing it is
     * checking is already broken, and a control that re-derives the count
     * finds the derivation agrees with itself.
     *
     * @param counts how many things each ability puts in the world
     */
    public static List<String> faultsInThrowing(
            List<ElysiumAbilityRules.Ability> abilities,
            Map<String, Integer> counts) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability one : abilities) {
            String shape = shapeOf(one.id());
            int count = counts.getOrDefault(one.id(), 0);
            boolean throwsSomething = NEED_AN_ENTITY.contains(shape);
            if (throwsSomething && count < 1) {
                bad.add(one.id() + " is a " + shape + " and puts " + count
                        + " things in the world, so it is an ability that "
                        + "does nothing");
            }
            if (!throwsSomething && count != 0) {
                bad.add(one.id() + " is a " + shape + " and is counted as "
                        + "putting " + count + " things in the world");
            }
        }
        return bad;
    }

    /** What each ability puts in the world, as this module works it out. */
    public static Map<String, Integer> counts(
            List<ElysiumAbilityRules.Ability> abilities) {
        Map<String, Integer> made = new LinkedHashMap<>();
        for (ElysiumAbilityRules.Ability one : abilities) {
            made.put(one.id(), countOf(one));
        }
        return Map.copyOf(made);
    }

    /** Whether anything puts too many things in the world at once. */
    public static List<String> faultsInCeiling(
            List<ElysiumAbilityRules.Ability> abilities) {
        List<String> bad = new ArrayList<>();
        for (ElysiumAbilityRules.Ability one : abilities) {
            if (countOf(one) > MOST_AT_ONCE) {
                bad.add(one.id() + " puts " + countOf(one) + " things in the "
                        + "world at once and nothing may put more than "
                        + MOST_AT_ONCE + ", which is the line between an "
                        + "ability and a performance problem");
            }
        }
        return bad;
    }

    /** Every ability that needs an entity before it can happen at all. */
    public static List<String> needingAnEntity() {
        List<String> made = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            if (NEED_AN_ENTITY.contains(shapeOf(ability.id()))) {
                made.add(ability.id());
            }
        }
        return List.copyOf(made);
    }
}
