package com.elysium.lib.item;

/**
 * What a tier is worth, in one place.
 *
 * <h2>Why this class exists</h2>
 *
 * Ascension used to mean three unrelated things depending on which file you
 * read. A piece's psionic affix grew by a flat 15% a tier
 * ({@code ElysiumRarities#getAscendantScale}); its stat grant grew linearly as
 * {@code 1 + tier}; its reforge budget grew geometrically at 1.4x a tier past
 * Sovereign and in a hand-written table below it; and its armour, its
 * toughness and its attack damage did not grow at all. Three curves and two
 * omissions, none of them stated next to any of the others.
 *
 * The omissions were the visible part: ascending a chestplate made the wearer
 * tougher through the stat system while the chestplate's own armour number sat
 * exactly where it started, and ascending a blade did nothing to what it hit
 * for. Ascension read as a rarity label rather than an upgrade.
 *
 * So the curve lives here, once, and everything that grows with tier reads it.
 *
 * <h2>The curve</h2>
 *
 * Geometric: every tier multiplies by {@link #GROWTH}. That is a deliberate
 * choice over a linear or a soft-capped curve, and it is the right one here
 * <em>because the cost curve is also geometric</em> — a tier needs two pieces
 * of the tier below, so reaching tier <i>n</i> costs 2^<i>n</i> base pieces.
 * A linear reward against a doubling price means ascension stops being worth
 * doing at about tier four; matching exponentials means the decision stays
 * live at every tier, which is what makes an endless system endless in
 * practice rather than only in the type signature.
 *
 * At {@value #GROWTH} a piece is worth roughly 3x at Sovereign and 9x six
 * tiers later. The doubling price means tier 20 asks for a million base
 * pieces, so the interesting range is the first ten or so tiers, and that is
 * the range this constant is tuned for. It is one number: change it here and
 * armour, damage, stats and affixes all move together, which is the property
 * that was missing.
 *
 * <h2>Why there is a clamp, and why it is not a balance decision</h2>
 *
 * {@link #MAX_SCALE} exists so that a stack edited by hand, or written by a
 * future build with a different curve, cannot produce an infinite or NaN
 * attribute value. A NaN in an attribute map does not throw — it silently
 * makes the player's armour, damage or health unusable and follows the save
 * around. The clamp is unreachable through play (it sits far past the point
 * where the price is more pieces than exist), so it never has to be balanced;
 * it only has to be finite.
 */
public final class ElysiumAscension {

    private ElysiumAscension() {
    }

    /**
     * What one tier multiplies by.
     *
     * The single tuning knob for the whole progression. See the class comment
     * for why it is geometric and why this value.
     */
    public static final float GROWTH = 1.25F;

    /**
     * The ceiling on {@link #scale}. Not balance — see the class comment.
     *
     * A million times a base value is still a finite float with room to spare
     * for the arithmetic done on it afterwards, which is the only property
     * being asked for.
     */
    public static final float MAX_SCALE = 1.0e6F;

    /**
     * The multiplier a piece at this tier is worth, relative to tier 0.
     *
     * @return 1.0 at tier 0 or below, growing geometrically above it
     */
    public static float scale(int tier) {
        if (tier <= 0) {
            return 1.0F;
        }
        double raw = Math.pow(GROWTH, tier);
        return (float) Math.min(MAX_SCALE, raw);
    }

    /**
     * How much a tier <em>adds</em>, as a fraction of the base value.
     *
     * The form everything that adds a modifier wants: a tier-0 piece adds
     * nothing at all rather than adding a zero-valued modifier, which keeps
     * un-ascended gear's tooltip exactly as it was.
     */
    public static float bonusFraction(int tier) {
        return scale(tier) - 1.0F;
    }

    /**
     * The bonus to add to a base value at this tier.
     *
     * @return zero when there is nothing to add, so callers can skip the
     *         modifier entirely rather than adding a no-op one
     */
    public static double added(double base, int tier) {
        if (base <= 0.0D || tier <= 0) {
            return 0.0D;
        }
        return base * bonusFraction(tier);
    }

    /**
     * The stat weight a tier is worth — the exponential replacement for what
     * used to be {@code 1 + tier}.
     *
     * <h2>Why it is anchored rather than a bare power</h2>
     *
     * A bare {@code round(pow(g, tier))} at any growth gentle enough to be
     * sane produces ties at the bottom — 1, 1, 2, 2 — so two tiers of
     * ascension would grant the same stats and the player would reasonably
     * conclude the second one had not worked. Anchoring the curve so it passes
     * through the old linear values at the tiers people actually start at
     * (1, 2, 3 for Common, Uncommon, Rare) keeps early progression exactly as
     * it was, is strictly increasing at every tier up to the clamp below, and
     * only then pulls away: Sovereign grants 9 where it used to grant 6, and
     * tier 12 grants 55 where it used to grant 13.
     *
     * The clamp first bites at tier 56, which prices at 2^56 base pieces. Ties
     * above it are the clamp doing its job, not the curve failing.
     *
     * The stat curves themselves have diminishing returns built in
     * ({@code ElysiumStat.curve} approaches a ceiling it never reaches), so a
     * large weight saturates gracefully instead of needing a cap here.
     */
    public static int statWeight(int tier) {
        if (tier <= 0) {
            return 1;
        }
        // 4 * GROWTH^t - 3 passes through 1, 2, 3 at tiers 0, 1, 2 and is
        // strictly increasing from there. ANCHOR and OFFSET are what make it
        // pass through those points; they are not free parameters.
        double raw = 4.0D * Math.pow(GROWTH, tier) - 3.0D;
        return (int) Math.min(1_000_000.0D, Math.round(raw));
    }
}
