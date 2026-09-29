package com.elysium.atelier.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What mines each block, executed.
 *
 * <p>The one thing this exists for is that every block of every roster is on
 * exactly one tool's list or excused by its kind, with wood on the axe's and
 * stone on the pickaxe's as the game's own are. The fault it was written
 * after was a whole roster on no list at all, which no single block shows
 * and which a survival player finds by breaking a stone roof and holding
 * nothing.
 *
 * <p>It also prints the catalogue, one line a block, because the tag files
 * that ship are compared against what the rules say and not against the
 * generator that wrote them: comparing generated files to the generator only
 * proves it agrees with itself. The validator reads what is printed here.
 */
public final class HarvestHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theCatalogue();
        theRule();
        theExemptions();
        theListing();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every block of every roster on one "
                + "tool's list or excused by its kind, wood on the axe's and stone "
                + "on the pickaxe's as the game's own, no material split between "
                + "two tools, and no id registered twice");
    }

    // ==================================================================

    private static void theCatalogue() {
        List<HarvestRules.Harvest> all = HarvestRules.catalogue();
        check(all.size() == HarvestRules.howMany(),
                "the catalogue has " + all.size() + " blocks and the rosters add up to "
                        + HarvestRules.howMany());
        check(HarvestRules.repeated().isEmpty(),
                "ids registered twice: " + HarvestRules.repeated());

        Map<String, Integer> perRoster = new LinkedHashMap<>();
        for (HarvestRules.Harvest one : all) {
            perRoster.merge(one.roster(), 1, Integer::sum);
        }
        for (String roster : HarvestRules.ROSTERS) {
            check(perRoster.getOrDefault(roster, 0) > 0,
                    "the " + roster + " roster is walked and has no blocks in it");
        }
        check(perRoster.keySet().equals(new TreeSet<>(HarvestRules.ROSTERS))
                        || new TreeSet<>(perRoster.keySet())
                        .equals(new TreeSet<>(HarvestRules.ROSTERS)),
                "blocks came from rosters the rules do not name: " + perRoster.keySet());
        check(perRoster.getOrDefault("pieces", 0) == ArchRules.howMany(),
                "the architectural pieces, which were the roster nothing tagged, "
                        + "are " + perRoster.getOrDefault("pieces", 0) + " here and "
                        + ArchRules.howMany() + " in their own rules");

        // The record's own refusals.
        int refused = 0;
        try {
            new HarvestRules.Harvest("oak_chair", "shapes", "chair", "oak", "spoon");
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new HarvestRules.Harvest("oak_chair", "spaceship", "chair", "oak",
                    HarvestRules.AXE);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 2, refused + " of 2 impossible blocks were refused");
        System.out.println("  the catalogue: " + all.size() + " blocks over "
                + perRoster.size() + " rosters");
    }

    private static void theRule() {
        int axe = 0;
        int pickaxe = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            String tool = HarvestRules.toolFor(material);
            check(tool.equals(material.soft() ? HarvestRules.AXE : HarvestRules.PICKAXE),
                    material.name() + " is " + (material.soft() ? "wood" : "stone")
                            + " and the rules hand it to the " + tool);
        }
        Map<String, List<String>> lists = HarvestRules.lists();
        for (HarvestRules.Harvest one : HarvestRules.catalogue()) {
            if (one.byHand()) {
                continue;
            }
            check(HarvestRules.TOOLS.contains(one.tool()),
                    one.id() + " is mined by a " + one.tool());
            check(lists.getOrDefault(one.tool(), List.of()).contains(one.id()),
                    one.id() + " is not on the " + one.tool() + "'s list it is "
                            + "mined by");
            if (HarvestRules.AXE.equals(one.tool())) {
                axe++;
            } else if (HarvestRules.PICKAXE.equals(one.tool())) {
                pickaxe++;
            }
        }
        check(axe > 0 && pickaxe > 0,
                "one of the two lists is empty: " + axe + " by axe, " + pickaxe
                        + " by pickaxe");
        check(HarvestRules.CLOTH.equals(HarvestRules.AXE),
                "cloth alone is not on the axe's list with the seats it is sewn onto");
        check(HarvestRules.undecided(HarvestRules.catalogue(), HarvestRules.BY_HAND)
                        .isEmpty(),
                "blocks nothing says how to mine, or two things are said of: "
                        + HarvestRules.undecided(HarvestRules.catalogue(),
                        HarvestRules.BY_HAND));
        check(HarvestRules.split().isEmpty(),
                "materials mined by two tools: " + HarvestRules.split());

        // The check notices a broken pair: a kind excused that the catalogue
        // puts on a tool's list, and a block by hand whose kind nothing
        // excuses.
        check(!HarvestRules.undecided(HarvestRules.catalogue(), List.of("chair"))
                        .isEmpty(),
                "a chair by hand and on the axe's list at once was let through");
        check(!HarvestRules.undecided(List.of(new HarvestRules.Harvest("oak_thing",
                        "shapes", "thing", "oak", HarvestRules.HAND)), List.of())
                        .isEmpty(),
                "a block on no list whose kind nothing excuses was let through");
        System.out.println("  the rule: " + axe + " by axe, " + pickaxe
                + " by pickaxe, " + lists.getOrDefault(HarvestRules.HAND, List.of())
                .size() + " by hand");
    }

    private static void theExemptions() {
        check(HarvestRules.exemptingNothing().isEmpty(),
                "exemptions for kinds nobody registers: "
                        + HarvestRules.exemptingNothing());
        Set<String> excused = new TreeSet<>(HarvestRules.BY_HAND);
        for (HarvestRules.Harvest one : HarvestRules.catalogue()) {
            check(one.byHand() == excused.contains(one.kind()),
                    one.id() + " is " + (one.byHand() ? "by hand" : "on a list")
                            + " and its kind " + one.kind() + " is "
                            + (excused.contains(one.kind()) ? "" : "not ") + "excused");
        }
        for (String tool : HarvestRules.TOOLS) {
            check(HarvestRules.tag(tool).equals("minecraft:mineable/" + tool),
                    "the " + tool + "'s list is " + HarvestRules.tag(tool));
        }
        System.out.println("  the exemptions: " + excused.size() + " kind(s) by hand");
    }

    /** One line a block, for the validator to hold the tag files to. */
    private static void theListing() {
        for (HarvestRules.Harvest one : HarvestRules.catalogue()) {
            System.out.println("HARVEST " + one.id() + " " + one.roster() + " "
                    + one.kind() + " " + one.material() + " " + one.tool());
        }
    }
}
