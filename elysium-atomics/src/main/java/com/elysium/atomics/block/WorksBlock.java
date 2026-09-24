package com.elysium.atomics.block;

import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.PlantBlock;

/**
 * An Age III machine: Age II's machine, with this age's numbers in it.
 *
 * <h2>What it changes, and what it deliberately does not</h2>
 *
 * A machine on a grid is the same thing in every age — one slot in, one slot
 * out, a job that takes longer when the grid is short — so the machine itself
 * is Age II's and this changes exactly two answers: what a shaft is worth to
 * an Age III machine, and the share above which it lets go of one.
 *
 * <p>Both get <em>worse</em> as the spine climbs. The shaft route exists so
 * that a player whose power has failed is slowed rather than stopped, and the
 * further up the ages a machine is the less a rope and a water wheel should be
 * able to do for it. By Age IV it should be gone entirely.
 */
public abstract class WorksBlock extends PlantBlock {

    protected WorksBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float shaftShare(float rpm) {
        return AtomicRules.shaftShare(rpm);
    }

    @Override
    public float bestOnAShaft() {
        return AtomicRules.ON_A_SHAFT;
    }
}
