package com.elysium.lib.stress;

/**
 * How somebody is doing, as a word.
 *
 * <h2>Why Recovery is not a threshold</h2>
 *
 * The other four are ranges on one number and Recovery is not, because it is
 * not a level of strain — it is a <em>direction</em>. A villager sitting at
 * fifteen who has been there all week is Stable; one sitting at fifteen an hour
 * after their house burned down is Recovering, and the difference is the whole
 * reason the band is worth naming. A player who rebuilt the village wants to be
 * told it worked.
 *
 * That costs exactly one piece of remembered state — the worst band reached
 * lately — which is the only thing about stress that is saved at all. See
 * {@link ElysiumStress}.
 *
 * <h2>Why four levels and not seven</h2>
 *
 * A band is a word somebody says out loud. Five words are a vocabulary; a dozen
 * are a lookup table, and nobody can tell you the difference between Uneasy and
 * Unsettled without checking.
 */
public enum ElysiumStressBand {

    /** Nothing much wrong. */
    STABLE("stable", 0),

    /** Something is wrong and it is being coped with. */
    STRAINED("strained", 20),

    /** Coping is costing them. */
    DISTRESSED("distressed", 45),

    /** Not coping. This is the one that stops a villager working. */
    CRISIS("crisis", 75),

    /**
     * Was much worse recently, and is better now.
     *
     * Ranked last deliberately: it is not a level, so nothing may treat it as
     * one. Code asking "is this at least Distressed" must ask
     * {@link #atLeast}, which knows Recovery is not on that scale.
     */
    RECOVERY("recovery", -1);

    private final String id;
    private final int threshold;

    ElysiumStressBand(String id, int threshold) {
        this.id = id;
        this.threshold = threshold;
    }

    public String id() {
        return id;
    }

    /** Where this band starts, or -1 for Recovery, which is not a range. */
    public int threshold() {
        return threshold;
    }

    public String translationKey() {
        return "elysium.stress.band." + id;
    }

    /** The band a total falls in. Never Recovery — that needs history. */
    public static ElysiumStressBand of(int total) {
        ElysiumStressBand found = STABLE;
        for (ElysiumStressBand band : values()) {
            if (band != RECOVERY && total >= band.threshold()) {
                found = band;
            }
        }
        return found;
    }

    /**
     * Whether this is at least as bad as {@code other}.
     *
     * Recovery is never at least anything, and nothing is ever at least
     * Recovery. Asked rather than compared by ordinal, because the ordinal
     * would say Recovery is the worst band there is — and a villager who has
     * just got better would stop trading.
     */
    public boolean atLeast(ElysiumStressBand other) {
        if (this == RECOVERY || other == RECOVERY) {
            return false;
        }
        return threshold >= other.threshold;
    }

    /** The bands that are ranges, worst first. For a readout, and the tests. */
    public static ElysiumStressBand[] scale() {
        return new ElysiumStressBand[]{CRISIS, DISTRESSED, STRAINED, STABLE};
    }
}
