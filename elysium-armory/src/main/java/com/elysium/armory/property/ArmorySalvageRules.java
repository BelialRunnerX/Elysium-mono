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
 * <p>Any affixed piece will do, modular or not ({@link #salvageable}): loot
 * carries affixes from world tier 1 up, and taking a found piece apart is where
 * the first essence comes from. A piece of the base game's gear has no parts
 * to give back, so it yields its essence and nothing else.
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
     * Whether a piece is worth putting on the salvager: modular gear with
     * parts to give back, or any piece carrying an affix.
     *
     * <p>The second half is where essence starts. A modular piece has affixes
     * only once it has been reforged, and a reforge is paid in essence, so
     * while the salvager took modular gear alone nobody could ever hold an
     * essence (found 29 September 2026). Loot rolls affixes onto the gear it
     * drops from world tier 1 up, and that is the way in.
     *
     * @param modular          modular gear with at least one part
     * @param totalAffixLevels every level of every affix on the piece
     */
    public static boolean salvageable(boolean modular, int totalAffixLevels) {
        return modular || totalAffixLevels > 0;
    }

    /**
     * How much essence a piece with found affixes yields: one per level of
     * each affix.
     *
     * The levels, not the count, so a piece carrying one strong affix is worth
     * as much as one carrying three weak ones — which is what keeps a player
     * from throwing away a good roll to farm the count.
     */
    public static int essence(int totalAffixLevels) {
        return essence(totalAffixLevels, 0);
    }

    /**
     * How much essence a piece yields: one per level of each affix, and never
     * more than a reforge was paid for them.
     *
     * The cap is what keeps the loop from printing essence. A reroll to rarity
     * N costs N and lands N affixes at one to four levels each, two on average
     * over the library's 94 affixes, so a reforged piece paid back at one a
     * level would return about twice its price: a wooden sword rerolled and
     * taken apart, again and again, would make essence out of nothing. Affixes
     * found on loot were never paid for, and yield their levels.
     *
     * @param totalAffixLevels every level of every affix on the piece
     * @param paid             what a reforge cost for those affixes; 0 when they were found
     */
    public static int essence(int totalAffixLevels, int paid) {
        int levels = Math.max(0, totalAffixLevels);
        return paid > 0 ? Math.min(levels, paid) : levels;
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
