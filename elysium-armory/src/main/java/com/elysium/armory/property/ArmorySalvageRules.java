package com.elysium.armory.property;

/**
 * What comes back out of a piece of gear.
 *
 * <h2>Why salvage exists at all</h2>
 *
 * A modular piece is the player's own materials, chosen and assembled. Once it
 * is outclassed, the vanilla answer is that it sits in a chest forever, and
 * the honest answer is that they should be able to get the metal back. Salvage
 * is the loop that makes a bad roll survivable: it turns loot into materials
 * again.
 *
 * <h2>What it costs</h2>
 *
 * A fraction, always. Salvage that returned everything would make crafting a
 * free operation you could undo, and every choice a player made about which
 * material to use would stop being a choice. So a part comes back on a roll,
 * and the roll is worse for gear that has been used:
 *
 * <ul>
 *   <li>a whole piece returns each part at {@link #BASE_CHANCE};</li>
 *   <li>damage lowers that, down to {@link #WORN_FLOOR} for a piece about to
 *       break — the metal is still there, there is just less of it;</li>
 *   <li>a piece's own repair efficiency raises it, because a material that
 *       repairs well is one that survives being taken apart.</li>
 * </ul>
 *
 * <h2>Affixes</h2>
 *
 * An affix cannot be salvaged into a part — it was never a part. It comes back
 * as essence, one per level, which is what the reforging table spends. That is
 * the whole economy of P6 in one line: bad affixes on a good piece become the
 * means to reroll them.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ArmorySalvageRules {

    private ArmorySalvageRules() {
    }

    /** How likely an undamaged part is to survive being taken off. */
    public static final double BASE_CHANCE = 0.75D;

    /** How likely a part on a nearly-broken piece is to survive. */
    public static final double WORN_FLOOR = 0.25D;

    /** How much a material's repair efficiency moves the chance, at efficiency 1. */
    public static final double EFFICIENCY_WEIGHT = 0.15D;

    /**
     * The chance one part survives salvage.
     *
     * @param damageRatio     0 for an untouched piece, 1 for one at its last point
     * @param repairEfficiency the material's, where 1 is ordinary
     */
    public static double chance(double damageRatio, double repairEfficiency) {
        double wear = Math.max(0.0D, Math.min(1.0D, damageRatio));
        double base = BASE_CHANCE - (BASE_CHANCE - WORN_FLOOR) * wear;
        double adjusted = base + (Math.max(0.0D, repairEfficiency) - 1.0D) * EFFICIENCY_WEIGHT;
        // Never certain and never impossible: a salvage that always worked
        // would undo crafting, and one that never did would not be a loop.
        return Math.max(0.05D, Math.min(0.95D, adjusted));
    }

    /**
     * How much essence an affixed piece yields: one per level of each affix.
     *
     * The levels, not the count, so a piece carrying one strong affix is worth
     * as much as one carrying three weak ones — which is what keeps a player
     * from throwing away a good roll to farm the count.
     */
    public static int essence(int totalAffixLevels) {
        return Math.max(0, totalAffixLevels);
    }

    /**
     * What a reroll costs, in essence, for a target rarity.
     *
     * Linear in the rarity, so reaching for a Legendary reroll costs five
     * times what a reroll to Uncommon does and the player can always see why.
     */
    public static int rerollCost(int targetRarity) {
        return Math.max(1, targetRarity);
    }

    /**
     * How many results a reforge offers to choose between.
     *
     * Three, which is the number that makes a reroll a decision rather than a
     * coin flip, without turning it into a shopping trip.
     */
    public static final int CHOICES = 3;
}
