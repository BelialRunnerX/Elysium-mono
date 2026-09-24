package com.elysium.enchanting.rules;

/**
 * The anvil, and the wall every long-running world hits.
 *
 * <h2>"Too Expensive!"</h2>
 *
 * Vanilla's anvil refuses any operation costing forty levels or more, and the
 * cost of working on an item doubles every time it is worked on. So every
 * piece of gear a player cares about has a fixed number of operations in it —
 * about six — after which the anvil will not touch it again at any price. The
 * item is not broken and the player is not poor; the game has simply stopped
 * accepting the transaction.
 *
 * <p>That rule exists to stop infinite combination in a game where levels are
 * easy. It is answered here in two ways rather than removed outright:
 *
 * <ul>
 *   <li>The cap does not apply to what this module computes. A player who has
 *       the levels may spend them.</li>
 *   <li>The prior-work penalty stays, because it is the thing that makes the
 *       order of operations a decision — and the Tome of Patience is how a
 *       player clears it, at a price, deliberately, rather than by never
 *       having had to think about it.</li>
 * </ul>
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class AnvilRules {

    private AnvilRules() {
    }

    /** The level at which vanilla's anvil says no. Kept as a number to *not* apply. */
    public static final int VANILLA_CAP = 40;

    /** What the prior-work penalty is allowed to climb to before it stops doubling. */
    public static final int PRIOR_WORK_LIMIT = 6;

    /** The share of an item's durability a repair with a like item returns. */
    public static final float REPAIR_SHARE = 0.12F;

    /**
     * The level a merged enchantment comes out at.
     *
     * Vanilla's rule, kept because everybody knows it: the higher of the two,
     * or one higher than that when they are equal and there is room. What is
     * not kept is any ceiling of this module's own — the enchantment's file
     * says how high it goes, and that is the only thing that may say so.
     */
    public static int merged(int onto, int from, int maximum) {
        int best = Math.max(onto, from);
        int combined = onto == from ? best + 1 : best;
        return Math.max(1, Math.min(combined, Math.max(1, maximum)));
    }

    /**
     * What an item's history costs before anything is done to it.
     *
     * Doubling, as vanilla does, and stopped at {@link #PRIOR_WORK_LIMIT}
     * rather than running away: past six the number is larger than any player
     * will ever hold and the difference between 63 and 8191 is only whether
     * the answer is no.
     */
    public static int priorWorkCost(int priorWork) {
        int held = Math.max(0, Math.min(PRIOR_WORK_LIMIT, priorWork));
        return (1 << held) - 1;
    }

    /** The history an item carries after being worked on. */
    public static int nextPriorWork(int left, int right) {
        return Math.max(0, Math.max(left, right)) + 1;
    }

    /**
     * What the whole operation costs.
     *
     * The enchantments' own cost plus both histories, and nothing else: no
     * cap, and no discount either. A player looking at a large number is
     * looking at an honest one.
     */
    public static int cost(int enchantments, int leftPriorWork, int rightPriorWork) {
        long total = (long) Math.max(0, enchantments)
                + priorWorkCost(leftPriorWork) + priorWorkCost(rightPriorWork);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, total));
    }

    /** Whether vanilla would have refused this, which is the whole reason this exists. */
    public static boolean refusedByVanilla(int cost) {
        return cost >= VANILLA_CAP;
    }

    /** How much durability one like item gives back to another. */
    public static int repair(int maxDamage, int damage) {
        int given = Math.max(1, Math.round(maxDamage * REPAIR_SHARE));
        return Math.max(0, damage - given);
    }

    /** What the Tome of Patience leaves behind. */
    public static int forgiven() {
        return 0;
    }
}
