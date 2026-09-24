package com.elysium.lib.item;

/**
 * What a rune's tier is called, and what it is worth to grind one up.
 *
 * <h2>Runes are the gems</h2>
 *
 * Apotheosis has gems with purities — cracked, chipped, flawed, and so on —
 * and Elysium has runes with tiers, where a tier-N rune does N times what the
 * base one does and two of a tier make one of the next. Those are the same
 * system with different words, and shipping both would mean a player holding
 * two kinds of stone that do the same thing at different names.
 *
 * <p>So a purity <em>is</em> a rune tier. This is the vocabulary for it: the
 * first six have names, and above that a rune reads as "Tier N", the same way
 * a piece of gear past Unique reads as "Ascendant N".
 *
 * <h2>Dust</h2>
 *
 * A rune costs {@code 2^(tier-1)} base runes to reach. Grinding one up returns
 * that in dust, less a share taken by the grinding — so dust is a real way to
 * move value between runes and never a way to make it. Cutting spends dust to
 * climb a tier, at what the merge would have cost, which means a player with
 * the wrong rune at a high tier can turn it into the right one at a lower one
 * and work back up.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumPurity {

    private ElysiumPurity() {
    }

    /** How many of the six purities have names of their own. */
    public static final int NAMED = 6;

    /**
     * What a share of the grinding is lost.
     *
     * A quarter. Enough that turning a rune into another is a real cost and
     * not enough that it is never worth doing.
     */
    public static final double GRINDING_LOSS = 0.25D;

    /** The untranslated name of a tier, for a log line or a fallback. */
    public static String name(int tier) {
        return switch (Math.max(1, tier)) {
            case 1 -> "Cracked";
            case 2 -> "Chipped";
            case 3 -> "Flawed";
            case 4 -> "Clear";
            case 5 -> "Flawless";
            case 6 -> "Perfect";
            default -> "Tier " + tier;
        };
    }

    /** The translation key for a tier's name; everything past the named ones shares one. */
    public static String nameKey(int tier) {
        return tier > NAMED ? "elysium.purity.beyond"
                : "elysium.purity." + name(tier).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * What a rune of this tier cost, in base runes.
     *
     * {@code 2^(tier-1)}, which is the merge rule stated as a number. Saturates
     * rather than overflowing, because a hand-edited save can name any tier.
     */
    public static int baseRunes(int tier) {
        int at = Math.max(1, tier);
        if (at > 31) {
            return Integer.MAX_VALUE;
        }
        return 1 << (at - 1);
    }

    /** What grinding a rune of this tier yields, in dust. */
    public static int dustFrom(int tier) {
        long worth = baseRunes(tier);
        long kept = (long) Math.floor(worth * (1.0D - GRINDING_LOSS));
        // A rune is always worth grinding for something, even the first tier.
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, kept));
    }

    /**
     * What it costs in dust to cut a rune from this tier to the next.
     *
     * Exactly what a second rune of this tier would have been worth, so
     * cutting and merging come to the same price and a player chooses between
     * them on what they have rather than on which is cheaper.
     */
    public static int cutCost(int fromTier) {
        return baseRunes(Math.max(1, fromTier));
    }

    /**
     * Whether a rune at this tier can still be cut.
     *
     * The arithmetic stops being safe past {@link ElysiumRuneTiers#MAX_TIER},
     * and a tier that costs more dust than an int can hold is a tier no world
     * will reach anyway.
     */
    public static boolean canCut(int fromTier) {
        return fromTier >= 1 && fromTier < ElysiumRuneTiers.MAX_TIER;
    }

    /**
     * How likely a kill at this world tier is to leave a rune behind.
     *
     * A share of the world tier, so the Frontier drops none and no amount of
     * climbing makes a rune certain. The base is small on purpose: a rune is
     * meant to be a find.
     */
    public static double dropChance(int worldTier) {
        return 0.05D * com.elysium.lib.tier.ElysiumWorldTier.share(worldTier);
    }

    /**
     * What purity a rune found at this world tier comes out at.
     *
     * One step up for each time a roll comes in under the same share, so a
     * high tier finds better stones as well as more of them — and the chain
     * ends, because the share is below one.
     */
    public static int foundPurity(int worldTier, java.util.function.DoubleSupplier rolls) {
        int tier = 1;
        double chance = com.elysium.lib.tier.ElysiumWorldTier.share(worldTier) * 0.5D;
        while (tier < NAMED && rolls.getAsDouble() < chance) {
            tier++;
        }
        return tier;
    }
}
