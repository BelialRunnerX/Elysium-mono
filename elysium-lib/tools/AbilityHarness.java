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

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + ElysiumAbilityRules.ABILITIES.size()
                + " abilities across " + ElysiumAbilityRules.domains().size()
                + " domains, none of them pointless, none of them worth more than "
                + "the ceiling, and no item able to carry the same one twice");
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
}
