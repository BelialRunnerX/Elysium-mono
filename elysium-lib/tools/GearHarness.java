package com.elysium.lib.item;

/**
 * Runs the gear test against the things a player actually holds.
 *
 * <h2>Why this is executed rather than read</h2>
 *
 * {@code ElysiumGear.classify} decides which items join Elysium's gear systems,
 * and both of its failure modes are silent. Admit too much and a player finds a
 * socketable creeper head granting stats — which is exactly what the first
 * version of the rule did, because vanilla's own "can this be worn on a head"
 * says yes to mob heads and carved pumpkins. Admit too little and somebody's
 * whole armour set is quietly outside the game, with no error to explain it.
 *
 * Neither fails to compile, neither appears in a log, and the table below is
 * long enough that reading the rule is not the same as checking it.
 *
 * <h2>The facts, and where they come from</h2>
 *
 * Each row states the four things the rule looks at, filled in from what the
 * item genuinely does in 1.21.1:
 *
 * <ul>
 *   <li><b>armourSlot</b> — vanilla's {@code Equipable.get} returns a humanoid
 *       armour slot. True for armour, and also for skulls, carved pumpkins and
 *       an elytra.</li>
 *   <li><b>suppliesArmour</b> — the item's default attribute modifiers add to
 *       {@code Attributes.ARMOR}. True for real armour only.</li>
 *   <li><b>tiered</b> — it extends {@code TieredItem}: a sword, pickaxe, axe,
 *       shovel or hoe.</li>
 *   <li><b>attackDamage</b> — its modifiers add to attack damage in the main
 *       hand.</li>
 * </ul>
 *
 * Compile against the library and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/h elysium-lib/tools/GearHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/h" \
 *         com.elysium.lib.item.GearHarness
 *
 * It declares the library's own package so it can reach {@code classify} and
 * {@code Kind} without either of them having to be public. They are internal —
 * the rule is this mod's business, and widening the API to test it would be the
 * test changing the design.
 */
public final class GearHarness {

    private static int failures = 0;
    private static int checked = 0;

    private record Case(String what, boolean armourSlot, boolean suppliesArmour,
                        boolean tiered, boolean attackDamage,
                        ElysiumGear.Kind expected) {
    }

    private static final Case[] CASES = {
            // --- armour, vanilla and modded alike -------------------------
            new Case("iron chestplate",     true,  true,  false, false, ElysiumGear.Kind.ARMOUR),
            new Case("leather cap",         true,  true,  false, false, ElysiumGear.Kind.ARMOUR),
            new Case("netherite boots",     true,  true,  false, false, ElysiumGear.Kind.ARMOUR),
            new Case("turtle shell helmet", true,  true,  false, false, ElysiumGear.Kind.ARMOUR),
            new Case("a mod's steel greaves", true, true, false, false, ElysiumGear.Kind.ARMOUR),

            // --- worn, and not armour -------------------------------------
            //
            // Every one of these is why the rule needs both halves. They all
            // pass the "can be worn on a humanoid" test and none of them is
            // armour, so the first version of this rule made all of them
            // socketable gear with a stat grant.
            new Case("carved pumpkin",      true,  false, false, false, ElysiumGear.Kind.NONE),
            new Case("creeper head",        true,  false, false, false, ElysiumGear.Kind.NONE),
            new Case("player head",         true,  false, false, false, ElysiumGear.Kind.NONE),
            new Case("elytra",              true,  false, false, false, ElysiumGear.Kind.NONE),

            // --- held ------------------------------------------------------
            new Case("iron sword",          false, false, true,  true,  ElysiumGear.Kind.HELD),
            new Case("diamond pickaxe",     false, false, true,  true,  ElysiumGear.Kind.HELD),
            new Case("wooden hoe",          false, false, true,  true,  ElysiumGear.Kind.HELD),
            new Case("golden shovel",       false, false, true,  true,  ElysiumGear.Kind.HELD),

            // Neither descends from TieredItem, and a rule written only around
            // tools would have left both out of a combat system that is mostly
            // about what you are swinging.
            new Case("trident",             false, false, false, true,  ElysiumGear.Kind.HELD),
            new Case("mace",                false, false, false, true,  ElysiumGear.Kind.HELD),
            new Case("a mod's warhammer",   false, false, false, true,  ElysiumGear.Kind.HELD),

            // --- not gear --------------------------------------------------
            new Case("stick",               false, false, false, false, ElysiumGear.Kind.NONE),
            new Case("cobblestone",         false, false, false, false, ElysiumGear.Kind.NONE),
            new Case("golden apple",        false, false, false, false, ElysiumGear.Kind.NONE),
            new Case("bow",                 false, false, false, false, ElysiumGear.Kind.NONE),
            new Case("shield",              false, false, false, false, ElysiumGear.Kind.NONE),
            new Case("fishing rod",         false, false, false, false, ElysiumGear.Kind.NONE),

            // --- the odd corners -------------------------------------------
            //
            // Armour is tested before held, so a piece that is somehow both is
            // worn. That is the slot it spends its life in, and reading it as a
            // weapon would take it out of the defensive matrix.
            new Case("armour that also hits", true, true, false, true, ElysiumGear.Kind.ARMOUR),
            // A pickaxe with its damage modifier stripped by a datapack is
            // still a pickaxe.
            new Case("a damage-less tool",  false, false, true,  false, ElysiumGear.Kind.HELD),
    };

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static void classification() {
        for (Case one : CASES) {
            ElysiumGear.Kind actual = ElysiumGear.classify(
                    one.armourSlot(), one.suppliesArmour(),
                    one.tiered(), one.attackDamage());
            check(actual == one.expected(),
                    one.what() + " classified as " + actual
                            + ", expected " + one.expected());
        }
    }

    /**
     * Every combination, so the rule is total rather than merely right about
     * the cases somebody thought of.
     *
     * The properties are the ones the design actually rests on, stated so that
     * a future change to classify() has to keep them or say why.
     */
    private static void exhaustive() {
        for (int bits = 0; bits < 16; bits++) {
            boolean armourSlot = (bits & 1) != 0;
            boolean suppliesArmour = (bits & 2) != 0;
            boolean tiered = (bits & 4) != 0;
            boolean attackDamage = (bits & 8) != 0;
            ElysiumGear.Kind kind =
                    ElysiumGear.classify(armourSlot, suppliesArmour, tiered, attackDamage);
            String state = "armourSlot=" + armourSlot + " suppliesArmour=" + suppliesArmour
                    + " tiered=" + tiered + " attackDamage=" + attackDamage;

            // 1. Armour needs both halves — never one.
            if (kind == ElysiumGear.Kind.ARMOUR) {
                check(armourSlot && suppliesArmour,
                        "called it armour without both halves: " + state);
            }
            // 2. Anything that supplies armour in an armour slot is armour,
            //    whatever else it does.
            if (armourSlot && suppliesArmour) {
                check(kind == ElysiumGear.Kind.ARMOUR,
                        "real armour not classified as armour: " + state);
            }
            // 3. Anything wieldable is gear.
            if (tiered || attackDamage) {
                check(kind != ElysiumGear.Kind.NONE,
                        "a wieldable item was turned away: " + state);
            }
            // 4. An item with none of the four facts is never gear. This is the
            //    one that keeps cobblestone out.
            if (!armourSlot && !suppliesArmour && !tiered && !attackDamage) {
                check(kind == ElysiumGear.Kind.NONE,
                        "something with no gear property was admitted: " + state);
            }
        }
    }

    public static void main(String[] args) {
        classification();
        exhaustive();

        System.out.println("gear classification: " + CASES.length
                + " named items, all 16 fact combinations, "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("every item lands where it should");
    }
}
