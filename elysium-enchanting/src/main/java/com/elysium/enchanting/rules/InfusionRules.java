package com.elysium.enchanting.rules;

/**
 * Infusion: the path that is certain, and costs for it.
 *
 * <h2>Why a second way to enchant</h2>
 *
 * The Arcane Table sells a gamble. That is the right shape for a table — the
 * clues, the quanta, the decision about how much to pay to know — but it is
 * the wrong shape for the one enchantment a player actually wants. Somebody
 * who has found a Mending book and a pickaxe worth keeping should not be
 * asked to roll for it.
 *
 * <p>So infusion is the opposite trade in every respect. The book says what
 * you get, the room says how high it may go, and the price is several times
 * what the table charges for the same level. Nothing is random and nothing is
 * hidden; what it costs is the certainty.
 *
 * <h2>Which of the four numbers matter here</h2>
 *
 * Eterna and arcana, and neither in the way they work at the table.
 *
 * <ul>
 *   <li><b>Eterna</b> is the ceiling on the level: the room has to be worth
 *       {@link #ETERNA_PER_LEVEL} for every level past the first. A shed can
 *       infuse Sharpness I and nothing else.</li>
 *   <li><b>Arcana</b> is what lets a rare enchantment be infused at all —
 *       the same gate the table uses, for the same reason: something a player
 *       can simply buy with a book is something arcana no longer sells.</li>
 *   <li><b>Rectification</b> takes some of the price off, because it is the
 *       hardest of the four to build for and it would otherwise do nothing
 *       here at all.</li>
 *   <li><b>Quanta</b> does nothing. Infusion is the certain path: a mechanism
 *       whose whole point is that it does not roll cannot have a number that
 *       makes it roll harder.</li>
 * </ul>
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class InfusionRules {

    private InfusionRules() {
    }

    /** How much eterna a room needs for each level past the first. */
    public static final float ETERNA_PER_LEVEL = 8.0F;

    /** Experience levels per level of enchantment infused. */
    public static final int COST_PER_LEVEL = 5;

    /** And the floor, so that infusing anything at all is a decision. */
    public static final int MINIMUM_COST = 5;

    /** The share of the price full rectification takes off. */
    public static final float DISCOUNT = 0.4F;

    /** Lapis per level of enchantment infused. */
    public static final int LAPIS_PER_LEVEL = 3;

    /**
     * The highest level of anything this room can infuse.
     *
     * One for nothing, and one more for every {@link #ETERNA_PER_LEVEL}. A
     * room good enough to offer level fifty at the table infuses about level
     * seven, which is deliberately below what the table can roll: the certain
     * path should not also be the better one.
     */
    public static int highestLevel(float eterna) {
        return 1 + (int) Math.floor(Math.max(0.0F, eterna) / ETERNA_PER_LEVEL);
    }

    /**
     * The level a book's enchantment actually lands at.
     *
     * The smallest of three: what the book holds, what the room can reach, and
     * what the enchantment itself allows. Nothing here can put a level on an
     * item that the enchantment's own file says is impossible.
     */
    public static int levelFor(int bookLevel, float eterna, int enchantmentMax) {
        return Math.max(0, Math.min(Math.min(bookLevel, highestLevel(eterna)),
                Math.max(1, enchantmentMax)));
    }

    /** What infusing a level costs in experience levels, rectification taken off. */
    public static int cost(int level, float rectification) {
        float paid = COST_PER_LEVEL * Math.max(0, level)
                * (1.0F - DISCOUNT * clamp(rectification));
        return Math.max(MINIMUM_COST, Math.round(paid));
    }

    /** And in lapis, which rectification does not touch. */
    public static int lapis(int level) {
        return Math.max(1, LAPIS_PER_LEVEL * Math.max(0, level));
    }

    /**
     * Whether this room may infuse something this rare at all.
     *
     * The table's own gate, asked again: an ordinary enchantment needs no
     * arcana and a rare one needs a room built for rarity. Otherwise a chest
     * full of books would route straight past the one number that was meant
     * to be hard to raise.
     */
    public static boolean allows(EnchantingRules.Stats stats, int weight) {
        return EnchantingRules.available(stats, weight);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
