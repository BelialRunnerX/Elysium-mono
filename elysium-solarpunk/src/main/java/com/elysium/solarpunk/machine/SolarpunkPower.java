package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.GridState;
import com.elysium.solarpunk.power.PowerBudget;
import com.elysium.solarpunk.power.PowerClass;
import com.elysium.solarpunk.power.PowerGrid;
import com.elysium.solarpunk.power.PowerRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.EnergyStorage;

import java.util.List;
import java.util.Set;

/**
 * How a generator hands what it made to everything it can reach.
 *
 * <h2>Why this is one function and not one per generator</h2>
 *
 * The condenser had its own copy first, and the moment a second generator
 * existed that copy was a choice: duplicate it, or share it. Duplicating would
 * have been three lines shorter today and would have put the one genuinely
 * subtle rule in this file — offer, then take only what was taken — in two
 * places to get wrong independently.
 *
 * <h2>What changed when the grid arrived</h2>
 *
 * This used to walk the six neighbours in {@link net.minecraft.core.Direction}
 * order and give each of them as much as they would take until the buffer ran
 * out. That is a fine rule when there is enough, and a bad one when there is
 * not: the block underneath always won, so which machine survived a cloudy
 * afternoon was decided by which one the player happened to place first.
 *
 * Now the receivers are gathered by {@link PowerGrid} — the six neighbours
 * <em>and</em> everything on the far end of a run of conduit — and the buffer
 * is divided by {@link PowerBudget}, which serves whole classes in order. A
 * base that is short of power stops its looms and keeps its water.
 *
 * A grid's governors are applied here too: a class a rule is holding wants
 * nothing this tick, so its share goes to whatever is still running rather than
 * merely going unspent. See {@code PowerRule}.
 *
 * A generator with no conduit attached behaves exactly as it did before, which
 * matters: a player's existing factory floor keeps running, and the conduit is
 * something they add to get reach and priority rather than something they have
 * to retrofit to get power at all.
 *
 * <h2>Offered, then taken</h2>
 *
 * The one rule that has not changed and must not. The receiver says how much it
 * wants and only that much leaves the buffer. Extracting first and pushing
 * afterwards is how energy gets destroyed by a full receiver: the extraction
 * succeeds, the push does not, and the difference is simply gone.
 */
public final class SolarpunkPower {

    private SolarpunkPower() {
    }

    /**
     * Divide the buffer among everything this generator can reach.
     *
     * @param limit the most any one receiver may be given this tick. It is a
     *              per-receiver cap rather than a total, which is what the old
     *              six-neighbour push meant by the same argument — keeping the
     *              meaning means adding a conduit never makes an existing base
     *              slower.
     */
    public static void push(Level level, BlockPos from, EnergyStorage source, int limit) {
        if (source.getEnergyStored() <= 0 || level == null || level.isClientSide()) {
            return;
        }
        PowerGrid.Survey survey = PowerGrid.survey(level, from);
        List<PowerGrid.Receiver> receivers = survey.receivers();
        if (receivers.isEmpty()) {
            return;
        }

        // The grid's own rules, evaluated against the world as it is this tick.
        //
        // After the walk rather than during it, because a condition can ask how
        // full the grid is and that is not known until every buffer on it has
        // been counted. The state is measured once and every rule sees the same
        // one, so two governors can never disagree about what time it is.
        Set<PowerClass> held = PowerRule.held(survey.rules(),
                new GridState(level.isDay() && level.canSeeSky(from),
                        level.isRaining(), survey.charge()));

        PowerClass[] classes = new PowerClass[receivers.size()];
        int[] wants = new int[receivers.size()];
        int total = 0;
        for (int i = 0; i < receivers.size(); i++) {
            PowerGrid.Receiver receiver = receivers.get(i);
            classes[i] = receiver.kind();
            if (held.contains(classes[i])) {
                // Held: wants nothing, so the budget hands it nothing and its
                // share goes to whatever is still running. Zeroing the want
                // rather than skipping the receiver is what makes a held class
                // free up power instead of merely not spending it.
                continue;
            }
            // Simulated, so asking costs nothing. A receiver that is full
            // answers zero and drops out of the budget entirely rather than
            // being given a share it would refuse — which is how a full
            // critical machine stops starving the industry behind it.
            wants[i] = Math.min(limit, receiver.store().receiveEnergy(limit, true));
            total += wants[i];
        }
        if (total <= 0) {
            return;
        }

        int[] grants = PowerBudget.allocate(source.getEnergyStored(), classes, wants);
        for (int i = 0; i < grants.length; i++) {
            if (grants[i] <= 0) {
                continue;
            }
            int taken = receivers.get(i).store().receiveEnergy(grants[i], false);
            if (taken > 0) {
                source.extractEnergy(taken, false);
            }
        }
    }
}
