package com.elysium.lib.item;

import java.util.List;

/**
 * The gear-type tree, executed.
 *
 * The tree is the vocabulary four systems share — traits, affixes, gems and
 * runes — so the thing worth checking is not that it parses but that a rule
 * written one way finds the shapes it ought to and none that it ought not.
 * Every claim below is one a data file could make.
 */
public final class GearTreeHarness {

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
        shape();
        lineages();
        selectors();
        everyShapeTheModShips();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + ElysiumGearTree.ALL.size() + " kinds, "
                + ElysiumGearTree.leaves().size() + " shapes an item can be; lineage, "
                + "selectors and the negations of them");
    }

    private static void shape() {
        check(ElysiumGearTree.named(ElysiumGearTree.ROOT) != null, "the tree has no root");
        check(ElysiumGearTree.named(ElysiumGearTree.ROOT).parent() == null,
                "the root has a parent, so the tree is not one");
        for (ElysiumGearTree.Kind kind : ElysiumGearTree.ALL.values()) {
            if (kind.parent() != null) {
                check(ElysiumGearTree.exists(kind.parent()),
                        kind.name() + " descends from " + kind.parent() + ", which is not a kind");
            }
            check(ElysiumGearTree.lineage(kind.name()).contains(ElysiumGearTree.ROOT),
                    kind.name() + " does not reach the root, so a rule on 'all' would miss it");
        }
        check(!ElysiumGearTree.exists("greatsword"), "a kind nobody declared exists");
        check(ElysiumGearTree.named(null) == null, "a null name resolved to a kind");
        check(ElysiumGearTree.lineage("greatsword").isEmpty(),
                "an unknown kind has a lineage, so bad data would be silently placed in the tree");
    }

    private static void lineages() {
        List<String> hammer = ElysiumGearTree.lineage("hammer");
        check(hammer.equals(List.of("hammer", "pickaxe", "harvest_tool", "tool", "all")),
                "a hammer's lineage is " + hammer + ", which is not a pickaxe's with a hammer on it");
        check(ElysiumGearTree.is("hammer", "pickaxe") && ElysiumGearTree.is("hammer", "tool"),
                "a hammer is not a pickaxe, or not a tool");
        check(!ElysiumGearTree.is("pickaxe", "hammer"),
                "every pickaxe is a hammer, which would give every pickaxe a hammer's rules");
        check(ElysiumGearTree.is("katana", "melee_weapon") && ElysiumGearTree.is("katana", "weapon")
                && ElysiumGearTree.is("katana", "tool"),
                "a katana is not a melee weapon, or the weapon branch does not hang off tool");
        check(ElysiumGearTree.is("trident", "hybrid_weapon") && ElysiumGearTree.is("trident", "weapon"),
                "a trident is not a hybrid weapon");
        check(!ElysiumGearTree.is("trident", "melee_weapon"),
                "a trident is a plain melee weapon, so nothing could write a rule for the thrown ones");
        check(ElysiumGearTree.is("elytra", "armor") && !ElysiumGearTree.is("elytra", "tool"),
                "elytra are not armour, or are also a tool");
        check(ElysiumGearTree.is("ring", "curio") && ElysiumGearTree.is("necklace", "curio")
                && ElysiumGearTree.is("bracelet", "curio"),
                "the jewellery is not curios");
        check(!ElysiumGearTree.is("arrow", "weapon") && ElysiumGearTree.is("arrow", "projectile"),
                "an arrow is a weapon rather than a projectile, so weapon affixes would roll on ammunition");
    }

    private static void selectors() {
        check(ElysiumGearTree.matches("sword", "any") && ElysiumGearTree.matches("nonsense", "any"),
                "'any' does not match everything, which is what it is for");
        check(!ElysiumGearTree.matches("sword", ""), "an empty selector matched");
        check(!ElysiumGearTree.matches("sword", null), "a missing selector matched");
        check(ElysiumGearTree.matches("hammer", "tool"), "a selector does not read the lineage");
        check(ElysiumGearTree.matches("hammer", "not:armor")
                && !ElysiumGearTree.matches("helmet", "not:armor"),
                "negation does not negate");
        check(ElysiumGearTree.matches("hammer", "not:not:tool"),
                "a doubled negation is not the thing itself");
        check(ElysiumGearTree.matchesAll("hammer", List.of("tool", "not:armor")),
                "two conditions that both hold did not both hold");
        check(!ElysiumGearTree.matchesAll("hammer", List.of("tool", "armor")),
                "a condition that fails did not fail the set");
        check(ElysiumGearTree.matchesAll("hammer", List.of()),
                "a rule with no conditions applies to nothing, so no pool could say 'anything'");
        check(ElysiumGearTree.matchesAny("hammer", List.of("armor", "pickaxe")),
                "one of two alternatives holding was not enough");
        check(!ElysiumGearTree.matchesAny("hammer", List.of()),
                "a pool that names no kinds still accepted something");
    }

    /**
     * Every shape the mod actually ships gear in has to be in the tree, or the
     * item exists and no rule can name it.
     */
    private static void everyShapeTheModShips() {
        String[] shipped = {"sword", "katana", "machete", "spear", "mace", "dagger", "knife",
                "pickaxe", "shovel", "axe", "hoe", "shears", "sickle", "mattock", "paxel",
                "hammer", "excavator", "saw", "prospector_hammer", "bow", "crossbow", "slingshot",
                "trident", "fishing_rod", "shield", "helmet", "chestplate", "leggings", "boots",
                "elytra", "arrow", "ring", "bracelet", "necklace"};
        for (String kind : shipped) {
            check(ElysiumGearTree.exists(kind), "the mod ships " + kind + " and the tree has no such kind");
        }
        check(shipped.length == 34, "the shipped list is no longer the 34 gear items the armory registers");
    }
}
