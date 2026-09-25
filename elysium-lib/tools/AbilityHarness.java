package com.elysium.lib.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thirty abilities, executed.
 *
 * <p>The claim that matters: none of them makes another pointless. It cannot
 * be met by numbers alone — trying gave eleven dominated passives on the first
 * run — and what makes it meetable is that two abilities are only comparable
 * when they do the same kind of thing. That is a design decision with a
 * checker behind it rather than a hope.
 */
public final class AbilityHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        theRoster();
        theBalance();
        theItem();
        theControls();
        theShapes();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + ElysiumAbilityRules.ABILITIES.size()
                + " abilities across " + ElysiumAbilityRules.domains().size()
                + " domains, none of them pointless, none of them worth more than "
                + "the ceiling, no item able to carry the same one twice, and "
                + "every one of them saying what it does rather than only what "
                + "it is worth");
    }

    // ==================================================================

    private static void theRoster() {
        check(!ElysiumAbilityRules.ABILITIES.isEmpty(), "there are no abilities");
        check(ElysiumAbilityRules.repeated().isEmpty(),
                "two abilities with the same name: "
                        + ElysiumAbilityRules.repeated());
        check(ElysiumAbilityRules.unusedTriggers().isEmpty(),
                "a trigger nothing fires on, so it is a word in a table: "
                        + ElysiumAbilityRules.unusedTriggers());
        check(ElysiumAbilityRules.thinKinds().isEmpty(),
                "a kind with one thing in it: " + ElysiumAbilityRules.thinKinds());

        Set<String> ids = new HashSet<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            check(ids.add(ability.id()), "two abilities called " + ability.id());
            check(!ability.domain().isEmpty(),
                    ability.id() + " has no domain, so nothing can be compared with "
                            + "it and it can never be dominated");
            check(ElysiumAbilityRules.TRIGGERS.get(ability.kind())
                            .contains(ability.trigger()),
                    ability.id() + " is a " + ability.kind() + " that fires on "
                            + ability.trigger());
            check((ability.cooldown() == 0)
                            == ElysiumAbilityRules.PASSIVE.equals(ability.kind()),
                    ability.id() + "'s cooldown and its kind disagree");
            check((ability.cost() == 0)
                            != ElysiumAbilityRules.ACTIVE.equals(ability.kind()),
                    ability.id() + "'s cost and its kind disagree");
        }
    }

    // ==================================================================

    private static void theBalance() {
        check(ElysiumAbilityRules.dominated().isEmpty(),
                "an ability another ability makes pointless: "
                        + ElysiumAbilityRules.dominated());
        check(ElysiumAbilityRules.indistinguishable().isEmpty(),
                "two abilities that are the same ability under two names: "
                        + ElysiumAbilityRules.indistinguishable());
        check(ElysiumAbilityRules.badValue().isEmpty(),
                "an ability worth too much to carry or too little to bother with: "
                        + ElysiumAbilityRules.badValue());

        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            check(ability.worth() > 0.0F, ability.id() + " is worth nothing");
            check(ability.worth() <= ElysiumAbilityRules.CEILING,
                    ability.id() + " is over the ceiling");
        }

        // A rescue is valued by how much it does, not how often. Valuing it by
        // throughput said a thing that saves your life twice an hour is worth a
        // fifth of a walking-speed bonus, which is the wrong answer and was the
        // first thing this harness caught.
        // The two triggers are named here rather than read out of
        // BY_MAGNITUDE, because a check guarded by the very set it is testing
        // passes by never running. Taking on_low_health out of that set made
        // this loop skip every rescue and report nothing, which a negative
        // test caught and reading never would have.
        for (String trigger : List.of("always", "on_low_health")) {
            for (ElysiumAbilityRules.Ability ability
                    : ElysiumAbilityRules.ABILITIES) {
                if (!ability.trigger().equals(trigger)) {
                    continue;
                }
                check(ability.worth() == ability.power(),
                        ability.id() + " fires on " + trigger + " and is still being "
                                + "priced by how often it comes round rather than by "
                                + "how much it does");
            }
        }
    }

    // ==================================================================

    private static void theItem() {
        check(ElysiumAbilityRules.cannotShareAnItem(List.of("blink")).isEmpty(),
                "one ability on one item is refused");
        check(!ElysiumAbilityRules.cannotShareAnItem(List.of("blink", "blink"))
                        .isEmpty(),
                "the same ability twice on one item is allowed, and it is not twice "
                        + "as good, it is a bug");
        List<String> tooMany = new ArrayList<>();
        for (int index = 0;
                index <= ElysiumAbilityRules.MOST_ON_AN_ITEM
                        && index < ElysiumAbilityRules.ABILITIES.size();
                index++) {
            tooMany.add(ElysiumAbilityRules.ABILITIES.get(index).id());
        }
        check(!ElysiumAbilityRules.cannotShareAnItem(tooMany).isEmpty(),
                "more abilities than the limit on one item is allowed");
        check(!ElysiumAbilityRules.cannotShareAnItem(List.of("not_an_ability"))
                        .isEmpty(),
                "an item can carry something that is not an ability at all");

        // The strongest three there are must not fit on one item, or the
        // worth ceiling does nothing.
        List<ElysiumAbilityRules.Ability> sorted =
                new ArrayList<>(ElysiumAbilityRules.ABILITIES);
        sorted.sort((one, other) -> Float.compare(other.worth(), one.worth()));
        List<String> strongest = new ArrayList<>();
        for (int index = 0; index < Math.min(3, sorted.size()); index++) {
            strongest.add(sorted.get(index).id());
        }
        check(!ElysiumAbilityRules.cannotShareAnItem(strongest).isEmpty(),
                "the three strongest abilities in the game fit on one item, so the "
                        + "worth ceiling does nothing at all");
    }

    // ==================================================================

    /**
     * Positive controls.
     *
     * <p>A domination check that answers no to everything passes every check
     * above it and proves nothing. This is the clearest possible domination
     * and the two clearest non-dominations.
     */
    private static void theControls() {
        ElysiumAbilityRules.Ability weak = new ElysiumAbilityRules.Ability(
                "a_test_ability", ElysiumAbilityRules.TRIGGERED, "on_hit", 200, 0,
                0.5F, "", "testing");
        ElysiumAbilityRules.Ability strong = new ElysiumAbilityRules.Ability(
                "a_stronger_test_ability", ElysiumAbilityRules.TRIGGERED, "on_hit",
                100, 0, 1.5F, "", "testing");
        check(strong.dominates(weak),
                "the domination check cannot see an ability that is stronger and "
                        + "more often available, so it answers no to everything and "
                        + "the whole roster has been checked by nothing");
        check(!weak.dominates(strong),
                "the domination check says the weaker ability dominates the stronger");

        ElysiumAbilityRules.Ability elsewhere = new ElysiumAbilityRules.Ability(
                "a_third_test_ability", ElysiumAbilityRules.TRIGGERED, "on_hit", 200,
                0, 0.5F, "", "somewhere_else");
        check(!strong.dominates(elsewhere),
                "the domination check compares abilities that do different kinds of "
                        + "thing, so a damage passive can make a night-vision passive "
                        + "pointless");

        ElysiumAbilityRules.Ability otherTrigger = new ElysiumAbilityRules.Ability(
                "a_fourth_test_ability", ElysiumAbilityRules.TRIGGERED, "on_kill",
                200, 0, 0.5F, "", "testing");
        check(!strong.dominates(otherTrigger),
                "the domination check compares abilities that fire on different "
                        + "things, so something that happens on every hit can make "
                        + "something that happens on a kill pointless");
        check(!strong.dominates(strong), "an ability makes itself pointless");
    }

    /**
     * What each ability does, as opposed to what it is worth.
     *
     * <p>This is the section that exists because the whole system was once
     * complete and inert: thirty abilities priced against each other, thirty
     * relics carrying them, every check passing, and pressing the button did
     * nothing. A rule that a thing is worth 3.2 says nothing about whether it
     * happens.
     */
    private static void theShapes() {
        java.util.List<String> faults = ElysiumAbilityShapes.faults();
        check(faults.isEmpty(), "something wrong with the shapes: " + faults);

        // Total, both ways.
        int covered = 0;
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            String shape = ElysiumAbilityShapes.shapeOf(ability.id());
            check(!shape.isEmpty(), ability.id() + " does nothing at all");
            check(ElysiumAbilityShapes.SHAPES.contains(shape),
                    ability.id() + " is a " + shape + ", which is not a shape");
            covered++;
        }
        check(covered == ElysiumAbilityRules.ABILITIES.size(),
                "only " + covered + " ability(ies) were asked what they do");
        int accounted = 0;
        for (String shape : ElysiumAbilityShapes.SHAPES) {
            int here = ElysiumAbilityShapes.ofShape(shape).size();
            check(here > 0, "nothing is a " + shape + ", so one of the six "
                    + "ways an ability can happen is a branch nothing reaches");
            accounted += here;
        }
        check(accounted == ElysiumAbilityRules.ABILITIES.size(),
                "the shapes account for " + accounted + " ability(ies) and "
                        + "there are " + ElysiumAbilityRules.ABILITIES.size());

        // The magnitudes are derived, and derived monotonically: a costlier
        // ability does more because it is costlier and not because somebody
        // remembered to type a bigger number.
        int compared = 0;
        for (ElysiumAbilityRules.Ability one : ElysiumAbilityRules.ABILITIES) {
            float reach = ElysiumAbilityShapes.reachOf(one);
            check(reach >= ElysiumAbilityShapes.LEAST_REACH - 1.0E-3F
                            && reach <= ElysiumAbilityShapes.MOST_REACH + 1.0E-3F,
                    one.id() + " reaches " + reach + " blocks");
            float where = ElysiumAbilityShapes.fraction(one);
            check(where >= 0.0F && where <= 1.0F, one.id() + " sits at " + where
                    + " between the floor and the ceiling");
            int lasts = ElysiumAbilityShapes.lastsOf(one);
            check(lasts >= 0, one.id() + " lasts " + lasts + " ticks");
            check(ElysiumAbilityRules.PASSIVE.equals(one.kind())
                            ? lasts == 0
                            : lasts >= ElysiumAbilityShapes.LEAST_TICKS,
                    one.id() + " is a " + one.kind() + " and lasts " + lasts
                            + " ticks");
            check(ElysiumAbilityShapes.hurtOf(one) >= 0.0F,
                    one.id() + " does negative damage");
            check(ElysiumAbilityShapes.HURTS.contains(one.domain())
                            == (ElysiumAbilityShapes.hurtOf(one) > 0.0F),
                    one.id() + " is in the " + one.domain() + " domain and "
                            + "hurts " + ElysiumAbilityShapes.hurtOf(one));
            for (ElysiumAbilityRules.Ability other : ElysiumAbilityRules.ABILITIES) {
                compared++;
                if (one.worth() <= other.worth() + 1.0E-3F) {
                    continue;
                }
                check(ElysiumAbilityShapes.reachOf(one)
                                >= ElysiumAbilityShapes.reachOf(other) - 1.0E-3F,
                        one.id() + " is worth more than " + other.id()
                                + " and reaches less far");
            }
        }
        check(compared == ElysiumAbilityRules.ABILITIES.size()
                        * ElysiumAbilityRules.ABILITIES.size(),
                "only " + compared + " pair(s) were compared");

        // The three that need something to exist in the world before they can
        // happen at all. Named, because "using an active does nothing" was
        // the state this whole section is here to end.
        java.util.List<String> needing = ElysiumAbilityShapes.needingAnEntity();
        check(!needing.isEmpty(),
                "nothing throws or summons anything, so two of the six shapes "
                        + "are branches nothing reaches");
        for (String id : needing) {
            ElysiumAbilityRules.Ability ability = ElysiumAbilityRules.ability(id);
            check(ability != null, id + " needs an entity and is not an ability");
            if (ability == null) {
                continue;
            }
            check(ElysiumAbilityShapes.countOf(ability) >= 1,
                    id + " throws or summons nothing at all");
            check(ElysiumAbilityShapes.countOf(ability)
                            <= ElysiumAbilityShapes.MOST_AT_ONCE,
                    id + " puts more than " + ElysiumAbilityShapes.MOST_AT_ONCE
                            + " things in the world at once");
        }

        // Controls, built to be caught, because over this roster every rule
        // above can only ever answer no.
        java.util.List<ElysiumAbilityRules.Ability> one =
                java.util.List.of(ElysiumAbilityRules.ABILITIES.get(0));
        check(!ElysiumAbilityShapes.faultsInEachShape(one, java.util.Map.of())
                        .isEmpty(),
                "an ability nothing says anything about is not reported, so "
                        + "the whole roster could go back to being priced and "
                        + "inert");
        check(!ElysiumAbilityShapes.faultsInEachShape(one, java.util.Map.of(
                        one.get(0).id(), "wibble")).isEmpty(),
                "an ability that is a shape nothing can perform is not "
                        + "reported");
        check(ElysiumAbilityShapes.faultsInEachShape(one, java.util.Map.of(
                        one.get(0).id(), ElysiumAbilityShapes.SELF)).isEmpty(),
                "an ability with a shape it really has is reported as faulty");
        check(!ElysiumAbilityShapes.faultsInOrphans(one, java.util.Map.of(
                        one.get(0).id(), ElysiumAbilityShapes.SELF,
                        "not_an_ability", ElysiumAbilityShapes.SELF)).isEmpty(),
                "a shape given to something that is not an ability is not "
                        + "reported");
        check(ElysiumAbilityShapes.faultsInOrphans(one, java.util.Map.of(
                        one.get(0).id(), ElysiumAbilityShapes.SELF)).isEmpty(),
                "a table naming only real abilities is reported as faulty");
        check(!ElysiumAbilityShapes.faultsInCoverage(
                        ElysiumAbilityRules.ABILITIES,
                        java.util.Map.of("blink", ElysiumAbilityShapes.STEP))
                        .isEmpty(),
                "a roster where five of the six shapes are nothing at all is "
                        + "not reported");
        check(ElysiumAbilityShapes.faultsInCoverage(
                        ElysiumAbilityRules.ABILITIES,
                        ElysiumAbilityShapes.SHAPE_OF).isEmpty(),
                "the shapes this library ships leave one of the six empty");
        check(ElysiumAbilityShapes.faultsInShapes(ElysiumAbilityRules.ABILITIES,
                        ElysiumAbilityShapes.SHAPE_OF).isEmpty(),
                "the shapes this library ships are reported as faulty");
        check(!ElysiumAbilityShapes.faultsInThrowing(java.util.List.of(
                        ElysiumAbilityRules.ability("gravewake")),
                        java.util.Map.of("gravewake", 0)).isEmpty(),
                "a summoning ability that summons nothing is not reported, so "
                        + "a relic could go on being pressed and doing "
                        + "nothing");
        check(!ElysiumAbilityShapes.faultsInThrowing(java.util.List.of(
                        ElysiumAbilityRules.ability("steadfast")),
                        java.util.Map.of("steadfast", 3)).isEmpty(),
                "an always-on ability counted as putting three things in the "
                        + "world is not reported");
        check(ElysiumAbilityShapes.faultsInThrowing(
                        ElysiumAbilityRules.ABILITIES,
                        ElysiumAbilityShapes.counts(
                                ElysiumAbilityRules.ABILITIES)).isEmpty(),
                "the throwing this library ships is reported as faulty");
        check(ElysiumAbilityShapes.faultsInCeiling(
                        ElysiumAbilityRules.ABILITIES).isEmpty(),
                "the counts this library ships are over the ceiling");
        check(ElysiumAbilityShapes.faultsInCount(ElysiumAbilityRules.ABILITIES)
                        .isEmpty(),
                "the counts this library ships are reported as faulty");
        check(ElysiumAbilityShapes.faultsInReach(ElysiumAbilityRules.ABILITIES)
                        .isEmpty(),
                "the reaches this library ships are reported as faulty");

        System.out.println("  the shapes: all "
                + ElysiumAbilityRules.ABILITIES.size() + " abilities say what "
                + "they do across " + ElysiumAbilityShapes.SHAPES.size()
                + " shapes, every magnitude derived from what the ability is "
                + "already worth rather than typed beside it, and "
                + needing.size() + " of them needing something in the world "
                + "before they can happen at all");
    }
}
