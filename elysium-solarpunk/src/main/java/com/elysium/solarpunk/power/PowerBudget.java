package com.elysium.solarpunk.power;

/**
 * Who gets how much, when there is not enough to go round.
 *
 * <h2>The two rules, and why they are different rules</h2>
 *
 * <b>Between classes, strictly.</b> A band is filled completely before the next
 * band sees a single unit. That is what "shed load by class" means: under a
 * deficit the looms stop, they do not all run at nine tenths. A base that is
 * short of power should look like a base that has made a decision, not like a
 * base where everything is slightly ill.
 *
 * <b>Within a class, proportionally.</b> Two kilns side by side both run at
 * half speed rather than one running and one dead. There is no fact that
 * distinguishes them — they are the same machine doing the same job — so
 * picking a winner would be picking one by position, and a player who rebuilt
 * the same row a block to the left would get a different answer.
 *
 * <h2>The remainder is handed out, not dropped</h2>
 *
 * Proportional shares are integer divisions and integer divisions lose units.
 * Three machines splitting ten leave one over. Dropping it is a leak that
 * scales with tick rate: one unit a tick is 1200 an hour, destroyed, and the
 * accounting would never balance. The leftovers go round the band one at a
 * time, in band order, only to machines that still want more.
 *
 * <h2>What this class guarantees</h2>
 *
 * <ul>
 *   <li>nobody is granted more than they asked for;</li>
 *   <li>the grants sum to exactly {@code min(supply, total want)} — nothing is
 *       created, nothing is destroyed;</li>
 *   <li>no grant is negative;</li>
 *   <li>the same inputs give the same outputs, every time, on every machine.</li>
 * </ul>
 *
 * Those four are executed in the harness rather than asserted here, because
 * they are the properties an edit to this file would break silently.
 *
 * <h2>No Minecraft here either</h2>
 *
 * Arrays of ints in, an array of ints out. The caller in {@code PowerGrid}
 * knows about block positions and capabilities; this file knows about
 * arithmetic, and that boundary is what makes the arithmetic testable.
 */
public final class PowerBudget {

    private PowerBudget() {
    }

    /**
     * Share {@code supply} among receivers, by class first and share second.
     *
     * @param supply how much there is to give this tick; negative is treated
     *               as zero rather than throwing, because a caller reading a
     *               drained buffer mid-tick is a normal thing and not a bug
     * @param ranks  each receiver's {@link PowerClass#rank()}
     * @param wants  each receiver's appetite; entries at or below zero get
     *               nothing and cost nothing
     * @return a new array, one grant per receiver, in the given order
     */
    public static int[] allocate(int supply, int[] ranks, int[] wants) {
        if (ranks.length != wants.length) {
            throw new IllegalArgumentException(
                    "ranks and wants describe the same receivers: "
                            + ranks.length + " != " + wants.length);
        }
        int[] grants = new int[wants.length];
        int remaining = Math.max(0, supply);
        if (remaining == 0 || wants.length == 0) {
            return grants;
        }

        // Bands are walked by rank rather than by sorting the receivers,
        // because sorting would have to be stable to keep the within-band order
        // the caller fixed, and "stable" is a property of the sort that a later
        // edit can lose without any test noticing. Walking the ranks touches
        // each receiver in its given order by construction.
        for (PowerClass band : PowerClass.values()) {
            if (remaining <= 0) {
                break;
            }
            int bandWant = 0;
            for (int i = 0; i < wants.length; i++) {
                if (ranks[i] == band.rank() && wants[i] > 0) {
                    bandWant += wants[i];
                    if (bandWant < 0) {          // overflowed
                        bandWant = Integer.MAX_VALUE;
                        break;
                    }
                }
            }
            if (bandWant <= 0) {
                continue;
            }
            int give = Math.min(remaining, bandWant);
            remaining -= give;

            if (give == bandWant) {
                for (int i = 0; i < wants.length; i++) {
                    if (ranks[i] == band.rank() && wants[i] > 0) {
                        grants[i] = wants[i];
                    }
                }
                continue;
            }

            // Short band. Proportional shares first; long arithmetic because
            // want * give overflows an int at around 46k each, which a bank of
            // buffered machines reaches easily.
            int handed = 0;
            for (int i = 0; i < wants.length; i++) {
                if (ranks[i] == band.rank() && wants[i] > 0) {
                    int share = (int) ((long) wants[i] * give / bandWant);
                    grants[i] = share;
                    handed += share;
                }
            }
            // Then the remainder, one unit at a time. Bounded by the band's
            // size times the shortfall, and the shortfall after a proportional
            // pass is smaller than the band, so this is one lap in practice —
            // the loop condition is written to terminate rather than to be
            // fast, because a leak here is silent and a slow tick is not.
            int leftover = give - handed;
            while (leftover > 0) {
                boolean gave = false;
                for (int i = 0; i < wants.length && leftover > 0; i++) {
                    if (ranks[i] == band.rank() && grants[i] < wants[i]) {
                        grants[i]++;
                        leftover--;
                        gave = true;
                    }
                }
                if (!gave) {
                    break;               // everyone in the band is full
                }
            }
        }
        return grants;
    }

    /** Convenience for the common shape: the classes themselves, not ranks. */
    public static int[] allocate(int supply, PowerClass[] classes, int[] wants) {
        int[] ranks = new int[classes.length];
        for (int i = 0; i < classes.length; i++) {
            ranks[i] = (classes[i] == null ? PowerClass.UNLABELLED : classes[i]).rank();
        }
        return allocate(supply, ranks, wants);
    }
}
