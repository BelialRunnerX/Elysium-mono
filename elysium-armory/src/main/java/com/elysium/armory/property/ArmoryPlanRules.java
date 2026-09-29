package com.elysium.armory.property;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Plans: which parts can be drawn, what a part asks for beside its materials,
 * and where a plan is found rather than drawn.
 *
 * <h2>Found plans</h2>
 *
 * A blueprint is drawn at the Gear Smithing Table and comes back out of every
 * grid it is used in. A template is the other kind: a plan somebody else drew
 * and left behind, used up by the one part it makes. Nothing handed one out
 * until 29 September 2026 (the table draws blueprints only), so the template
 * was an item nobody could hold. It is found now, in the base game's chests,
 * at the chances in {@link #TEMPLATES}, and it reads as one of the plans the
 * table offers the first time a player carries it.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ArmoryPlanRules {

    private ArmoryPlanRules() {
    }

    /**
     * Whether a part can be planned: it is built from materials, and it is
     * not an upgrade, which has a crafting recipe of its own. The Gear
     * Smithing Table offers exactly these, and a found template is one of
     * them.
     */
    public static boolean plannable(boolean upgrade, int materials) {
        return !upgrade && materials > 0;
    }

    /**
     * Which plan a found template turns out to be: any plan the table offers,
     * each as likely as the next.
     *
     * @param plans how many plans the table offers
     * @param roll  a draw in [0, 1)
     * @return the index of the plan, or -1 when there is none to draw
     */
    public static int drawn(int plans, double roll) {
        if (plans <= 0) {
            return -1;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, roll));
        return Math.min(plans - 1, (int) Math.floor(clamped * plans));
    }

    /**
     * Which of the things on the bench beside the plan are the part's extras,
     * or null when one of them is missing.
     *
     * <p>Three parts take something besides their materials: a coating a
     * glass bottle, a mace core a heavy core, a setting a bort. Each extra
     * takes the first thing that answers to it, and nothing else takes that
     * thing, so two extras need two things. What is left has to be the part's
     * materials, which the recipe checks. The extras were read from the data
     * and never asked for until 29 September 2026, so a coating needed no
     * bottle and a mace no heavy core.
     *
     * @param extras  what the part asks for, each an item id or a #tag
     * @param bench   how many things lie on the bench beside the plan
     * @param answers whether the thing at an index answers to an extra
     * @return one flag a thing, true for an extra; null when an extra is missing
     */
    public static boolean[] extras(List<String> extras, int bench, BiPredicate<Integer, String> answers) {
        boolean[] taken = new boolean[Math.max(0, bench)];
        for (String extra : extras) {
            int found = -1;
            for (int index = 0; index < taken.length && found < 0; index++) {
                if (!taken[index] && answers.test(index, extra)) {
                    found = index;
                }
            }
            if (found < 0) {
                return null;
            }
            taken[found] = true;
        }
        return taken;
    }

    /** One chest a template turns up in: a loot table of the base game, and the chance a chest. */
    public record Found(String table, double chance) {
    }

    /**
     * Where templates are found, and how often.
     *
     * <p>At a village smith's, one chest in four: the weaponsmith, the
     * toolsmith and the armourer are where plans for blades, heads and plates
     * would be kept. In a dungeon, one chest in ten: whatever its last visitor
     * was carrying. {@code tools/gen_loot.py} writes one loot modifier a row,
     * and the validator holds each file to its row.
     */
    public static final List<Found> TEMPLATES = List.of(
            new Found("minecraft:chests/village/village_weaponsmith", 0.25D),
            new Found("minecraft:chests/village/village_toolsmith", 0.25D),
            new Found("minecraft:chests/village/village_armorer", 0.25D),
            new Found("minecraft:chests/simple_dungeon", 0.10D));
}
