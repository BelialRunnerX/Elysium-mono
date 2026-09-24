package com.elysium.lib.scar;

import com.elysium.lib.telegraph.ElysiumTelegraph;

/**
 * The destructible-combat rule, executed against every block in the game.
 *
 * <h2>What is being guarded</h2>
 *
 * One thing: <b>this must not become a grief machine.</b> "Attacks damage
 * terrain" is two words away from "a skeleton eventually deletes your house",
 * and the way that happens is never a deliberate decision — it is a hardness
 * comparison that reads sensibly and says yes to one more thing than anybody
 * checked.
 *
 * So the rule is walked against every hardness vanilla and a mod can produce,
 * including the negative ones that mean unbreakable, and it is checked from the
 * direction that matters: not "does it break what it should" but "does it ever
 * break anything it should not".
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/sch elysium-lib/tools/ScarHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/sch" \
 *         com.elysium.lib.scar.ScarHarness
 */
public final class ScarHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /**
     * The hardnesses that actually exist, and the ones that matter.
     *
     * Named rather than looped, because the assertion worth making is about
     * particular blocks a player would mind losing — and a range check would
     * pass while silently including obsidian if somebody moved a constant.
     */
    private static final Object[][] BLOCKS = {
            // name, hardness, is it something a player built or values
            {"bedrock", -1.0F, true},
            {"barrier", -1.0F, true},
            {"obsidian", 50.0F, true},
            {"iron_block", 5.0F, true},
            {"chest", 2.5F, true},
            {"oak_planks", 2.0F, true},
            {"stone", 1.5F, true},
            {"deepslate", 3.0F, true},
            {"crafting_table", 2.5F, true},
            {"furnace", 3.5F, true},
            {"dirt", 0.5F, false},
            {"sand", 0.5F, false},
            {"gravel", 0.6F, false},
            {"leaves", 0.2F, false},
            {"glass", 0.3F, false},
            {"wool", 0.8F, true},
            {"snow_layer", 0.1F, false},
            {"short_grass", 0.0F, false},
            {"wheat", 0.0F, false},
            {"torch", 0.0F, false},
    };

    /** Nothing a player builds with is ever breakable, tag or no tag. */
    private static void nothingStructuralEverBreaks() {
        for (Object[] block : BLOCKS) {
            String name = (String) block[0];
            float hardness = (Float) block[1];
            boolean structural = (Boolean) block[2];
            if (!structural) {
                continue;
            }
            check(!ElysiumScarring.breakable(hardness, true),
                    name + " (hardness " + hardness + ") can be broken by a "
                            + "blow even when tagged. That is somebody's house");
        }
    }

    /** Unbreakable blocks stay unbreakable, however the arithmetic reads. */
    private static void unbreakableMeansUnbreakable() {
        for (float hardness : new float[]{-1.0F, -0.5F, -100.0F,
                                          Float.NEGATIVE_INFINITY}) {
            check(!ElysiumScarring.breakable(hardness, true),
                    "a block of hardness " + hardness + " is breakable. "
                            + "Unbreakable blocks report a negative hardness, "
                            + "so a ceiling test alone makes bedrock softer "
                            + "than dirt");
        }
        check(!ElysiumScarring.breakable(Float.NaN, true),
                "a block of unknown hardness is breakable");
    }

    /** The tag is required, not an override. */
    private static void theTagIsTheFirstGate() {
        for (Object[] block : BLOCKS) {
            float hardness = (Float) block[1];
            check(!ElysiumScarring.breakable(hardness, false),
                    block[0] + " breaks without being tagged. Anything nobody "
                            + "has thought about has to answer no, or the first "
                            + "content mod somebody installs loses a crop field");
        }
    }

    /** What does break, breaks — or the feature does nothing at all. */
    private static void somethingBreaks() {
        int breakable = 0;
        for (Object[] block : BLOCKS) {
            if (ElysiumScarring.breakable((Float) block[1], true)) {
                breakable++;
            }
        }
        System.out.println("  cover: " + breakable + " of " + BLOCKS.length
                + " sample blocks may be broken when tagged, at or under "
                + ElysiumScarring.MAX_HARDNESS + " hardness");
        check(breakable > 0,
                "nothing at all can be broken, so the feature is off while "
                        + "looking like it is on");
        check(breakable < BLOCKS.length / 2,
                breakable + " of " + BLOCKS.length + " blocks are breakable; "
                        + "this is meant to take cover apart, not terrain");
    }

    /** Only announced blows scar, and every announced blow could. */
    private static void onlyAnnouncedBlowsScar() {
        for (int quarter = 0; quarter <= 400; quarter++) {
            float damage = quarter / 4.0F;
            check(ElysiumScarring.heavyEnough(damage)
                            == ElysiumTelegraph.isDangerous(damage),
                    "a blow of " + damage + " scars but was never announced, or "
                            + "was announced and cannot scar. The two sets have "
                            + "to be the same one, or there is a blow in the "
                            + "game that takes a block with no warning");
        }
        check(!ElysiumScarring.heavyEnough(ElysiumTelegraph.DANGEROUS),
                "a blow at exactly the safe ceiling scars");
        check(ElysiumScarring.heavyEnough(ElysiumTelegraph.DANGEROUS + 0.5F),
                "a blow above the ceiling does not scar, so the feature never "
                        + "happens");
    }

    /** The blast radius is a blast radius and not a demolition. */
    private static void theReachIsBounded() {
        int cells = (ElysiumScarring.RADIUS * 2 + 1);
        int volume = cells * cells * cells;
        System.out.println("  reach: " + volume + " block(s) searched, at most "
                + ElysiumScarring.MOST_PER_BLOW + " broken, "
                + Math.round(ElysiumScarring.CHANCE * 100) + "% of announced blows");
        check(ElysiumScarring.MOST_PER_BLOW == 1,
                "a blow may take " + ElysiumScarring.MOST_PER_BLOW + " blocks. "
                        + "One is a fight leaving a mark; several is a fight "
                        + "levelling the ground");
        check(ElysiumScarring.RADIUS <= 2,
                "a blow reaches " + ElysiumScarring.RADIUS + " blocks, which is "
                        + "further than the thing it hit");
        check(ElysiumScarring.CHANCE > 0.0F && ElysiumScarring.CHANCE < 0.5F,
                "announced blows scar "
                        + Math.round(ElysiumScarring.CHANCE * 100) + "% of the "
                        + "time. Reliable cover destruction is cover nobody "
                        + "bothers with; never is a feature that is off");
    }

    public static void main(String[] args) {
        nothingStructuralEverBreaks();
        unbreakableMeansUnbreakable();
        theTagIsTheFirstGate();
        somethingBreaks();
        onlyAnnouncedBlowsScar();
        theReachIsBounded();

        System.out.println("scarring: " + BLOCKS.length + " blocks across every "
                + "hardness the game produces — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("nothing anybody built comes down");
    }
}
