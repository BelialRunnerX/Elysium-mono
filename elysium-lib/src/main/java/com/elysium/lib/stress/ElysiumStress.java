package com.elysium.lib.stress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The arithmetic: a set of reasons in, a band and an explanation out.
 *
 * <h2>The reading is the explanation</h2>
 *
 * {@link Reading} carries the reasons it was computed from, not a copy of them.
 * That is the whole design: §8.3 asks that a player be able to ask <em>why</em>,
 * and the only answer that can never be wrong is the one the number was made of.
 *
 * <h2>No Minecraft here</h2>
 *
 * Everything in this package is executed by the harness against hand-built sets
 * of reasons, which is the only way to walk the whole band model — every
 * combination of a dozen stressors is four thousand villagers, and no amount of
 * playing would reach them.
 */
public final class ElysiumStress {

    private ElysiumStress() {
    }

    /**
     * How long after a peak a villager still counts as recovering.
     *
     * Ten minutes of game time. Long enough that a player who fixed something
     * sees it acknowledged when they come back round the village; short enough
     * that "Recovering" does not become the permanent state of everybody who
     * ever had a bad day.
     */
    public static final int RECOVERY_WINDOW = 20 * 60 * 10;

    /**
     * How far below the peak counts as having got better.
     *
     * A full band. Dropping two points out of Crisis is not recovery, it is
     * noise, and a villager flickering between Crisis and Recovering every
     * time a zombie wandered off would make the readout useless.
     */
    public static final int RECOVERY_DROP = 1;

    /** What somebody is going through, and what it is made of. */
    public record Reading(int total, ElysiumStressBand band,
                          List<ElysiumStressor> reasons) {

        /** The reasons, heaviest burden first, relief last. */
        public List<ElysiumStressor> ordered() {
            List<ElysiumStressor> sorted = new ArrayList<>(reasons);
            sorted.sort(Comparator.comparingInt(
                    (ElysiumStressor one) -> one.weight()).reversed());
            return sorted;
        }

        /** The single heaviest thing wrong, or null if nothing is. */
        public ElysiumStressor worst() {
            ElysiumStressor found = null;
            for (ElysiumStressor reason : reasons) {
                if (!reason.relieves()
                        && (found == null || reason.weight() > found.weight())) {
                    found = reason;
                }
            }
            return found;
        }

        /** Whether this is bad enough to stop somebody working. */
        public boolean inCrisis() {
            return band == ElysiumStressBand.CRISIS;
        }
    }

    /**
     * Add up a set of reasons.
     *
     * Floored at zero. A villager at home, employed and among friends with
     * nothing wrong would otherwise total minus forty-five, which is not a
     * calmer villager than one at zero — it is a number with nowhere to go, and
     * it would mean relief silently banking against a future disaster.
     */
    public static int total(Collection<ElysiumStressor> reasons) {
        if (reasons == null) {
            return 0;
        }
        int sum = 0;
        for (ElysiumStressor reason : reasons) {
            if (reason != null) {
                sum += reason.weight();
            }
        }
        return Math.max(0, sum);
    }

    /**
     * Work out the band, taking the recent past into account.
     *
     * @param peak        the worst band reached lately, or null if none is
     *                    remembered
     * @param sincePeak   ticks since that peak was recorded
     */
    public static Reading read(Collection<ElysiumStressor> reasons,
                               ElysiumStressBand peak, long sincePeak) {
        int total = total(reasons);
        ElysiumStressBand now = ElysiumStressBand.of(total);
        List<ElysiumStressor> carried = reasons == null
                ? List.of() : List.copyOf(reasons);

        boolean recovering = peak != null
                && peak != ElysiumStressBand.RECOVERY
                && sincePeak >= 0
                && sincePeak <= RECOVERY_WINDOW
                && peak.atLeast(ElysiumStressBand.STRAINED)
                // At least a full band better than the worst of it. Compared by
                // band rather than by points so that the test is the same one
                // the player is reading: they were Distressed, now they are
                // Stable.
                && ordinalOf(now) + RECOVERY_DROP <= ordinalOf(peak);

        return new Reading(total, recovering ? ElysiumStressBand.RECOVERY : now,
                carried);
    }

    /** Where a band sits on the scale of four. Recovery is not on it. */
    private static int ordinalOf(ElysiumStressBand band) {
        ElysiumStressBand[] scale = ElysiumStressBand.scale();
        for (int i = 0; i < scale.length; i++) {
            if (scale[i] == band) {
                return scale.length - 1 - i;     // STABLE 0 .. CRISIS 3
            }
        }
        return 0;
    }

    /**
     * Whether a new band should replace the remembered peak.
     *
     * Worse always replaces. Better never does — that is what lets Recovery
     * exist at all — and the window in {@link #read} is what eventually clears
     * it, rather than anything having to notice that things improved.
     */
    public static boolean isNewPeak(ElysiumStressBand candidate,
                                    ElysiumStressBand peak) {
        if (candidate == null || candidate == ElysiumStressBand.RECOVERY) {
            return false;
        }
        if (peak == null || peak == ElysiumStressBand.RECOVERY) {
            return candidate.atLeast(ElysiumStressBand.STRAINED);
        }
        return candidate.atLeast(peak) && candidate != peak;
    }
}
