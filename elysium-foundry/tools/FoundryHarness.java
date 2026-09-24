package com.elysium.foundry.rules;

import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.rules.IndustryRules;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The material chain, executed.
 *
 * <p>Two properties, and both of them are the kind nobody can check by
 * reading: the chain has no cycles in it, and no step of it makes matter out
 * of nothing. A pack that gets either wrong does not crash — it leaks, and by
 * the time anybody notices, every save has a chest of free metal in it.
 */
public final class FoundryHarness {

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

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        theForms();
        theChain();
        theCycles();
        theConservation();
        theCasings();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no material is made of itself however far "
                + "down, no alloy costs less than what goes into it, every material is "
                + "reachable from something a player can dig up, and no casing is made "
                + "of anything above its own rung");
    }

    // ==================================================================

    private static void theForms() {
        check(FoundryRules.UNIT == 9,
                "the unit is not nine, so a nugget is not a whole number of them and "
                        + "every recipe in the chain has to be checked in floating point");
        check(FoundryRules.Form.NUGGET.worth() * 9 == FoundryRules.Form.INGOT.worth(),
                "nine nuggets are not an ingot, which is the one conversion every player "
                        + "already knows");
        check(FoundryRules.Form.BLOCK.worth() == FoundryRules.Form.INGOT.worth() * 9,
                "nine ingots are not a block");
        check(FoundryRules.Form.PLATE.worth() == FoundryRules.Form.INGOT.worth(),
                "a plate is not one ingot, so Age II's rolling mill and this age's "
                        + "material chain disagree about what a plate costs");
        check(FoundryRules.Form.GEAR.worth() > FoundryRules.Form.PLATE.worth(),
                "a gear costs no more than a plate, so nothing in the chain is expensive");
        for (FoundryRules.Form form : FoundryRules.Form.values()) {
            check(form.worth() > 0, form + " is worth nothing");
            check(form.worth() % FoundryRules.Form.NUGGET.worth() == 0,
                    form + " is not a whole number of nuggets");
        }
    }

    private static void theChain() {
        check(FoundryRules.MATERIALS.size() >= 15,
                "there are fewer than fifteen materials, which is not a chain, it is a "
                        + "list");
        Set<String> names = new HashSet<>();
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            check(names.add(material.name()),
                    material.name() + " is declared twice, so one of the two is "
                            + "unreachable and which one depends on map ordering");
            check(material.tier() >= 0 && material.tier() <= FoundryRules.TIERS,
                    material.name() + " sits outside the tiers the chain declares");
        }

        // Everything has to come from somewhere a player can dig.
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            Set<String> roots = FoundryRules.rootsOf(material.name());
            check(!roots.isEmpty(),
                    material.name() + " is made of nothing and is not mined either, so "
                            + "there is no way to get any");
            for (String root : roots) {
                FoundryRules.Material dug = FoundryRules.material(root);
                check(dug != null && dug.mined(),
                        material.name() + " is ultimately made of " + root + ", which is "
                                + "not something the ground gives you and not declared "
                                + "either");
            }
        }

        // An alloy may not be made of something above it.
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            for (String part : material.madeOf().keySet()) {
                FoundryRules.Material ingredient = FoundryRules.material(part);
                check(ingredient != null,
                        material.name() + " is made of " + part + ", which is not a "
                                + "material this chain declares");
                if (ingredient != null) {
                    check(ingredient.tier() < material.tier(),
                            material.name() + " (tier " + material.tier() + ") is made of "
                                    + part + " (tier " + ingredient.tier() + "), so the "
                                    + "chain runs sideways or backwards and the tiers "
                                    + "stop meaning anything");
                }
            }
        }

        // Every tier has to have something on it, or it is not a rung.
        for (int tier = 0; tier <= 3; tier++) {
            check(!FoundryRules.atTier(tier).isEmpty(),
                    "tier " + tier + " has nothing on it, so the ladder has a gap in it");
        }
    }

    private static void theCycles() {
        Set<String> caught = FoundryRules.cycles();
        check(caught.isEmpty(),
                "these materials are made out of themselves: " + caught + " — which is "
                        + "either something nobody can ever make or a loop that turns one "
                        + "unit into two");

        // And the walker itself has to work, or the check above is vacuous.
        check(FoundryRules.needs("steel", "iron"), "steel is not seen to need iron");
        check(FoundryRules.needs("stainless", "iron"),
                "stainless is not seen to need iron, three steps down");
        check(!FoundryRules.needs("iron", "steel"),
                "iron is seen to need steel, which is the chain backwards");
        check(!FoundryRules.needs("iron", "iron"),
                "iron is seen to need itself, and it is mined");
        check(!FoundryRules.needs("nothing_at_all", "iron"),
                "a material that does not exist has requirements");

        check(FoundryRules.rootsOf("stainless").contains("iron"),
                "stainless does not trace back to iron");
        check(FoundryRules.rootsOf("iron").equals(Set.of("iron")),
                "something the ground gives you is not its own root");
    }

    private static void theConservation() {
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            if (material.mined()) {
                check(FoundryRules.deepCost(material.name()) == FoundryRules.UNIT,
                        material.name() + " is mined and costs something other than one "
                                + "unit, so the whole scale is relative to a moving "
                                + "number");
                continue;
            }
            int costs = material.costsInUnits();
            check(costs >= FoundryRules.UNIT,
                    material.name() + " costs " + costs + " units and gives back "
                            + FoundryRules.UNIT + ", so making it creates matter — which "
                            + "is a duplication loop waiting for somebody to find it");
            check(costs <= FoundryRules.UNIT * 8,
                    material.name() + " costs " + costs + " units for one, which is so "
                            + "much that nobody will ever make it and the rung above is "
                            + "unreachable");

            int deep = FoundryRules.deepCost(material.name());
            check(deep >= FoundryRules.UNIT,
                    material.name() + " traces back to less than one unit of ore, so "
                            + "somewhere down the chain a step makes matter");
            check(deep >= costs,
                    material.name() + " costs more at one remove than it does all the "
                            + "way down, which cannot happen unless a step multiplies");
        }

        // The ore ladder ends where Age III left it.
        check(FoundryRules.fromOneOre()
                        == AtomicRules.ENRICHER_MULTIPLIER * FoundryRules.UNIT,
                "the fourth age extends the ore ladder, which is Age III's to end — this "
                        + "age is about what you make out of the metal, not how much of "
                        + "it there is");
        check(AtomicRules.ENRICHER_MULTIPLIER == 3,
                "the ladder is not 1, 2, 3, so every module that priced itself against "
                        + "it is priced against something else");

        // And the deepest thing in the chain has to be genuinely deep, or the
        // fourth age is a colour swap.
        int deepest = 0;
        String worst = "nothing";
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            int deep = FoundryRules.deepCost(material.name());
            if (deep > deepest) {
                deepest = deep;
                worst = material.name();
            }
        }
        check(deepest >= FoundryRules.UNIT * 6,
                "the most expensive material in the chain is " + worst + " at " + deepest
                        + " units, which is not a supply problem, it is a crafting recipe");
    }

    private static void theCasings() {
        check(FoundryRules.CASINGS.size() == FoundryRules.TIERS,
                "there are not as many casings as there are tiers");
        int last = -1;
        float lastDraw = -1.0F;
        for (FoundryRules.Casing casing : FoundryRules.CASINGS) {
            check(casing.tier() > last,
                    "the casings are not declared in order, so a table read in order "
                            + "gives them in the wrong one");
            last = casing.tier();
            check(casing.draw().draw() > lastDraw,
                    "a tier " + casing.tier() + " machine draws no more than the one "
                            + "below it, so the tiers cost nothing to climb");
            lastDraw = casing.draw().draw();

            FoundryRules.Material made = FoundryRules.material(casing.madeOf());
            check(made != null,
                    "a tier " + casing.tier() + " casing is made of " + casing.madeOf()
                            + ", which is not a material this chain declares");
            if (made != null) {
                check(made.tier() <= casing.tier(),
                        "a tier " + casing.tier() + " casing is made of something from "
                                + "tier " + made.tier() + ", so it skips the rung under "
                                + "it");
                check(FoundryRules.fitsIn(casing.madeOf(), casing.tier()),
                        casing.madeOf() + " does not fit in the casing it is the material "
                                + "for, so the two ways of asking disagree");
            }
        }

        check(FoundryRules.casing(1) != null && FoundryRules.casing(FoundryRules.TIERS)
                        != null,
                "either the first or the last casing is missing");
        check(FoundryRules.casing(99) == null,
                "a casing past the top of the table exists, so a data file can invent a "
                        + "fifth tier");

        // A casing may not be made of something above its rung, however asked.
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            for (int tier = 0; tier <= FoundryRules.TIERS; tier++) {
                boolean fits = FoundryRules.fitsIn(material.name(), tier);
                check(fits == (material.tier() <= tier),
                        material.name() + " at tier " + material.tier() + " " + (fits
                                ? "fits" : "does not fit") + " in a tier " + tier
                                + " casing, which is the opposite of what the tiers say");
            }
        }
        check(!FoundryRules.fitsIn("nothing_at_all", 4),
                "a material that does not exist fits in a casing");

        // The top casing has to be on the top wire, or Age III's ceiling is
        // never reached by anything.
        FoundryRules.Casing top = FoundryRules.casing(FoundryRules.TIERS);
        check(top != null && top.draw().rated() == IndustryRules.Voltage.EXTREME,
                "the top casing does not run on the top wire, so the voltage Age II left "
                        + "for later is never used by anything at all");

        Map<String, FoundryRules.Material> all = FoundryRules.everything();
        check(all.size() == FoundryRules.MATERIALS.size(),
                "the list of materials and the map of them are different sizes, so one "
                        + "of them has a duplicate in it");
        List<FoundryRules.Material> top_tier = FoundryRules.atTier(3);
        check(!top_tier.isEmpty(), "nothing sits at the top of the chain");
    }
}
