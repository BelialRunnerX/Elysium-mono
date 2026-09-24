package com.elysium.lib.elite;

import java.util.function.DoubleSupplier;

/**
 * Which of the world's creatures are worth being afraid of (Apotheosis' AP-20
 * and AP-21).
 *
 * <h2>Three ranks, and why only three</h2>
 *
 * An ordinary mob, an <b>elite</b>, and an <b>invader</b>. More ranks than
 * that is a number a player has to learn; three is a thing they can see. An
 * elite is a mob that got lucky at spawn and carries a couple of augments; an
 * invader is the rare one that carries several and has a name.
 *
 * <h2>Gated by the world tier, like everything else that escalates</h2>
 *
 * The Frontier has neither. Past it, both chances are shares of the world
 * tier, so they rise forever and neither ever becomes certain — a world where
 * every zombie is an invader is a world with no invaders in it.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumEliteRules {

    private ElysiumEliteRules() {
    }

    /** The share of the world tier an elite promotion gets. */
    public static final double ELITE_SHARE = 0.20D;

    /** How much rarer an invader is than an elite. */
    public static final double INVADER_FRACTION = 0.04D;

    /** How many augments an elite carries at the least. */
    public static final int ELITE_AUGMENTS = 1;

    /** How many an invader carries at the least. */
    public static final int INVADER_AUGMENTS = 3;

    /**
     * How many augments a rank carries at a world tier.
     *
     * One more for every four tiers past the gate, forever — so the deep tiers
     * produce creatures that are not merely tougher but stranger.
     */
    public static final int TIERS_PER_EXTRA_AUGMENT = 4;

    /** The three ranks, in the order they get worse. */
    public enum Rank { ORDINARY, ELITE, INVADER }

    /** How likely a mob spawning at this world tier is to be an elite at all. */
    public static double eliteChance(int worldTier) {
        return ELITE_SHARE * com.elysium.lib.tier.ElysiumWorldTier.share(worldTier);
    }

    /** How likely one is to be an invader. */
    public static double invaderChance(int worldTier) {
        return eliteChance(worldTier) * INVADER_FRACTION;
    }

    /**
     * What a mob spawning here turns out to be.
     *
     * The invader roll is asked first and separately, so an invader is not
     * merely an elite that rolled twice — it is its own rarity and reads as
     * one in the numbers.
     */
    public static Rank roll(int worldTier, DoubleSupplier rolls) {
        if (worldTier <= 0) {
            return Rank.ORDINARY;           // the Frontier has neither
        }
        if (rolls.getAsDouble() < invaderChance(worldTier)) {
            return Rank.INVADER;
        }
        return rolls.getAsDouble() < eliteChance(worldTier) ? Rank.ELITE : Rank.ORDINARY;
    }

    /** How many augments a creature of this rank carries at this tier. */
    public static int augments(Rank rank, int worldTier) {
        int base = switch (rank) {
            case INVADER -> INVADER_AUGMENTS;
            case ELITE -> ELITE_AUGMENTS;
            default -> 0;
        };
        if (base == 0) {
            return 0;
        }
        return base + Math.max(0, worldTier) / TIERS_PER_EXTRA_AUGMENT;
    }

    /** What an invader's extra weight is worth to whoever kills it: more, and better. */
    public static double lootMultiplier(Rank rank) {
        return switch (rank) {
            case INVADER -> 3.0D;
            case ELITE -> 1.5D;
            default -> 1.0D;
        };
    }

    /** The translation key for a rank's name; ordinary creatures have none. */
    public static String nameKey(Rank rank) {
        return rank == Rank.ORDINARY ? "" : "elysium.rank." + rank.name().toLowerCase(
                java.util.Locale.ROOT);
    }
}
