package com.elysium.lib.stats;

/**
 * Every percentage in Elysium, by one rule: <b>stat / (stat + b)</b>.
 *
 * <h2>The owner's ruling</h2>
 *
 * "Uncap everything, but give percentile effects stat/stat+b scaling." So no
 * number in the mod has a ceiling, and anything that is a <em>percentage</em> —
 * a chance, or a share of something removed — is not added up directly but
 * derived from an underlying stat that is. The stat grows without limit; the
 * percentage it buys climbs toward 100% and never arrives, because it cannot:
 * S / (S + b) is below one for every finite S.
 *
 * <h2>Odds: the stat in its natural unit</h2>
 *
 * With b = 1 the stat is simply the <b>odds</b>, p / (1 - p). Every source that
 * is already written as a percentage — a rune's 5% dodge, a doctrine's 20%
 * hazard share — is converted to odds, the odds are <b>added</b>, and the
 * total is converted back once. That is exactly "add the stats, then apply
 * S / (S + b)", for any sources on a shared b.
 *
 * It has the three properties the rest of the mod leans on:
 * <ul>
 *   <li>One source alone is unchanged — {@code add(p) == p}. Nothing that
 *       worked before is renumbered where it stands alone.</li>
 *   <li>N copies of a stat are N times the stat — {@code scaled(p, n)} is
 *       n times p's odds. So a tier-N rune is, literally, N times the rune.</li>
 *   <li>No total ever reaches 1 from finite inputs, so nothing needs a clamp,
 *       and none is applied.</li>
 * </ul>
 *
 * This replaces two older combinations, both of which capped: a product of
 * what each source let through, clamped per source at 99.9% or 95%; and a
 * straight sum, clamped at 60%.
 *
 * <p>Imports nothing, and must not: it is in {@code EXECUTED_PURE}.
 */
public final class ElysiumShares {

    private ElysiumShares() {
    }

    /**
     * How fast every percentage climbs. The owner's ruling: "we want the player
     * to scale really hard" — so every stat that becomes a percentage is worth
     * four times what it was, which is the same as cutting every b to a
     * quarter. Applied wherever a stat is turned into a share: rune stats,
     * gear hazard stats, elemental blunting, and the character stats' halfway
     * points. Still stat / (stat + b); still never 100%.
     *
     * One knob on purpose. Tuning it moves every percentage in the mod
     * together, and nothing can drift out of step with the rest.
     */
    public static final double PACE = 4.0D;

    /**
     * stat / (stat + b). The rule itself.
     *
     * Zero for no stat, a negative one or NaN; one only for an infinite stat,
     * which no finite input produces.
     */
    public static float share(double stat, double b) {
        if (!(stat > 0.0D) || !(b > 0.0D)) {
            return 0.0F;
        }
        if (Double.isInfinite(stat)) {
            return 1.0F;
        }
        float share = (float) (stat / (stat + b));
        // In exact arithmetic this is below one for every finite stat. In a
        // float it is not: past about 2^24 x b the nearest float is 1.0, which
        // is immunity arrived at by rounding. The largest float below one is
        // the honest answer — not a ceiling on the stat, but the formula's own
        // "never arrives" kept true in the type it is stored in.
        return share < 1.0F ? share : Math.nextDown(1.0F);
    }

    /** A percentage as a stat in odds units: p / (1 - p). The inverse of {@link #fromOdds}. */
    public static double odds(float share) {
        if (!(share > 0.0F)) {
            return 0.0D;
        }
        if (share >= 1.0F) {
            return Double.POSITIVE_INFINITY;   // an input that already said "certain"
        }
        return share / (1.0D - share);
    }

    /** Odds back to a percentage: odds / (odds + 1). */
    public static float fromOdds(double odds) {
        return share(odds, 1.0D);
    }

    /** Several percentages as one: their stats added, the rule applied once. */
    public static float add(float... shares) {
        double odds = 0.0D;
        for (float share : shares) {
            odds += odds(share);
        }
        return fromOdds(odds);
    }

    /** N times a percentage's stat. What a tier-N rune, or N of a thing, is worth. */
    public static float scaled(float share, double n) {
        if (!(n > 0.0D)) {
            return 0.0F;
        }
        return fromOdds(odds(share) * n);
    }

    /** Anything claiming to be a percentage, made into one: [0, 1], NaN as 0. Not a cap — a type check. */
    public static float sane(float share) {
        if (!(share > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, share);
    }
}
