package com.elysium.lib.item;

/**
 * Rune tiers: how a rune grows, and what growing is worth.
 *
 * <h2>The rule</h2>
 *
 * <b>A rune of tier N does N times what the base rune does.</b> Tier 1 is the
 * rune as found; tier 3 is three times it; tier 4, four times. Linear, and
 * stated as a rule the player can check on the tooltip rather than a curve
 * they have to trust.
 *
 * <h2>How a rune climbs</h2>
 *
 * Two runes of the same kind and the same tier merge into one of the next
 * tier, at the Ascension Forge — the same verb gear uses, two of a thing
 * becoming one better thing. So a tier-N rune costs 2^(N-1) base runes.
 *
 * That is the whole balance, and it is deliberate: power rises linearly and
 * the price doubles. Tier 2 is twice the effect for twice the runes, which is
 * a fair trade a player makes early. Tier 5 is five times the effect for
 * sixteen runes, which is a decision. Tier 10 is ten times for 512, which is
 * a project. Nothing needs a ceiling for gameplay; the cost is the ceiling.
 *
 * <h2>Nothing is capped; percentages are stat / (stat + b)</h2>
 *
 * The owner's ruling: "uncap everything, but give percentile effects
 * stat/stat+b scaling". So a tier-N rune has N times its base <em>stat</em>.
 * Where that stat is a flat number — attack damage, healing, a potion level
 * that is a bonus like Strength — the effect is N times too. Where it is a
 * percentage — dodge, heat reduction, knockback resistance, Resistance's
 * share of damage — the stat is N times and the percentage is
 * stat / (stat + b): tier 1 exactly as before, every tier higher, no ceiling
 * and no arrival at 100%. See com.elysium.lib.stats.ElysiumShares.
 *
 * Two things here look like caps and are not. {@link #MAX_TIER} is where the
 * arithmetic stops being safe, at a tier costing 2^31 base runes that no world
 * can produce. {@link #MAX_AMPLIFIER} is vanilla's own byte: an effect level
 * past it does not get stronger, it wraps round to a weak one, so saturating
 * there is the only way a stronger rune stays stronger.
 *
 * <p>Imports nothing, and must not: it is in {@code EXECUTED_PURE}.
 */
public final class ElysiumRuneTiers {

    private ElysiumRuneTiers() {
    }

    /** The rune as found. */
    public static final int BASE = 1;

    /**
     * The highest tier arithmetic is asked to handle.
     *
     * Not a gameplay ceiling — a tier-32 rune costs 2^31 base runes, which no
     * world will produce. It exists so that every number derived from a tier
     * fits in the type it is stored in, whatever a hand-edited save says.
     */
    public static final int MAX_TIER = 32;

    /** A stored tier made sane: anything below the base is the base; anything past the top is the top. */
    public static int clamp(int tier) {
        return Math.max(BASE, Math.min(MAX_TIER, tier));
    }

    /** The rule itself. Tier N does N times the base. */
    public static float multiplier(int tier) {
        return (float) clamp(tier);
    }

    /** Whether two runes of these tiers can merge. Same tier, and room above it. */
    public static boolean canMerge(int a, int b) {
        return a == b && a >= BASE && a < MAX_TIER;
    }

    /** What two runes of this tier merge into. */
    public static int merged(int tier) {
        return clamp(clamp(tier) + 1);
    }

    /** How many base runes one rune of this tier took to make. */
    public static long baseRunesIn(int tier) {
        return 1L << (clamp(tier) - 1);
    }

    // ------------------------------------------------------------------
    // Potion effects
    // ------------------------------------------------------------------

    /** Vanilla stores an effect's amplifier in a byte. */
    public static final int MAX_AMPLIFIER = 255;

    /**
     * The amplifier for a potion effect whose base level is {@code baseLevel}
     * (1 is level I), at this tier.
     *
     * Level times N, returned as the amplifier vanilla wants (level minus one).
     * Saturating, because a level past 256 wraps in vanilla's byte into a
     * small or negative amplifier — the strongest rune in the world quietly
     * becoming the weakest.
     */
    public static int amplifier(int baseLevel, int tier) {
        long level = (long) Math.max(1, baseLevel) * clamp(tier);
        return (int) Math.min(MAX_AMPLIFIER, level - 1L);
    }


    /** Vanilla Resistance: twenty per cent a level, and level V is all of it. */
    public static final float RESISTANCE_PER_LEVEL = 0.2F;

    /**
     * The amplifier for a potion effect that is a <em>percentage</em> — each
     * level worth {@code sharePerLevel} of something, so that some level is
     * the whole of it. Resistance is the one Elysium grants.
     *
     * Percentages follow stat / (stat + b) (ElysiumShares): the base level's
     * share is the stat at tier 1, a tier-N rune has N times that stat, and
     * the level is the share it buys, rounded down to whole levels. Tier 1
     * gives exactly the base level; every tier climbs; and the level that
     * would be 100% is never reached, because the share never is.
     *
     * The small epsilon absorbs float noise at exact boundaries — 0.25 odds is
     * exactly 20%, which must read as level I and not a hair under it.
     */
    public static int percentileAmplifier(int baseLevel, int tier, float sharePerLevel) {
        float baseShare = Math.max(1, baseLevel) * sharePerLevel;
        float share = com.elysium.lib.stats.ElysiumShares.scaled(baseShare,
                clamp(tier) * com.elysium.lib.stats.ElysiumShares.PACE);
        int level = (int) Math.floor(share / sharePerLevel + 1.0e-4D);
        return Math.max(0, Math.min(MAX_AMPLIFIER, level - 1));
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V",
            "VI", "VII", "VIII", "IX", "X"};

    /** "III" for tier 3; plain digits past ten, where numerals stop helping. */
    public static String numeral(int tier) {
        int t = clamp(tier);
        return t <= NUMERALS.length ? NUMERALS[t - 1] : Integer.toString(t);
    }
}
