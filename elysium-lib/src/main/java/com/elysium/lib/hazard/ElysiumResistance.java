package com.elysium.lib.hazard;

/**
 * How independent protections combine.
 *
 * <h2>One rule, everywhere</h2>
 *
 * {@code 1 − Π(1 − share)}. Each source removes a fraction of what is left
 * rather than a fraction of the whole, so four sources of a quarter come to 68%
 * and not to 100%.
 *
 * The specification asks for this shape specifically, and for the same shape to
 * be reused by armour, trinkets, runes, food and shelter — its words are "to
 * reduce the number of unrelated stacking models players must learn". That is
 * the real argument. Additive shares need a cap, a cap needs a rule about what
 * happens at the cap, and a player has to learn both; this needs neither,
 * because it approaches total protection and never arrives.
 *
 * <h2>Why nothing may contribute 1.0</h2>
 *
 * A single source of 1.0 makes the product zero and the player immune, and
 * every other source they own instantly becomes worthless. That is not a
 * balance question, it is the arithmetic collapsing: immunity is the one value
 * this formula cannot express usefully. {@link #combine} therefore clamps each
 * share below one rather than trusting its callers, and the harness checks that
 * no amount of stacking ever reaches it.
 */
public final class ElysiumResistance {

    private ElysiumResistance() {
    }

    /**
     * Combine independent protection shares.
     *
     * Each share's stat added, then stat / (stat + b) once — the one rule
     * every percentage in Elysium follows; see ElysiumShares. No source is
     * clamped: this used to stop every share at 95% and multiply what each
     * let through, which is a cap twice over.
     *
     * @param shares each in 0..1; negative values and NaN count as nothing
     * @return the total fraction of incoming hazard removed, below 1 for any
     *         finite inputs
     */
    public static float combine(float... shares) {
        return com.elysium.lib.stats.ElysiumShares.add(shares);
    }

    /**
     * What one piece of gear removes: the base share's stat, plus
     * {@code stepPerTier} of stat for each tier, times {@code fraction} (1 for
     * a matching element, the ring fraction for one that beats it), turned
     * into a share once.
     *
     * Here rather than in ElysiumProtection so the harness executes the same
     * arithmetic the game does. It used to rebuild the old linear formula
     * by hand, which would have gone on passing against a formula nothing
     * used any more.
     */
    public static float pieceShare(float baseShare, float stepPerTier, int tier,
                                   float fraction) {
        double stat = com.elysium.lib.stats.ElysiumShares.odds(baseShare)
                + (double) stepPerTier * Math.max(0, tier);
        return com.elysium.lib.stats.ElysiumShares.fromOdds(
                stat * fraction * com.elysium.lib.stats.ElysiumShares.PACE);
    }

    /** One share, made a share: [0, 1], NaN as 0. A type check, not a ceiling. */
    public static float clampShare(float share) {
        return com.elysium.lib.stats.ElysiumShares.sane(share);
    }

    /**
     * What is left of an amount after protection.
     *
     * The one call sites should use, so nothing has to remember which way round
     * the subtraction goes.
     */
    public static float after(float amount, float... shares) {
        return amount * (1.0F - combine(shares));
    }
}
