package com.elysium.solarpunk.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * What counts as "there is water here".
 *
 * <h2>Why this is shared rather than written twice</h2>
 *
 * The bioreactor asked this question first and the aquaponics tank asks exactly
 * the same one. Two copies would be two chances for the answers to drift — and
 * they would drift silently, because the symptom is a machine that refuses a
 * site the machine next to it accepts, which reads as a bug in the wrong place
 * entirely.
 *
 * <h2>Why a source block <em>or</em> a tank</h2>
 *
 * Requiring a lake is a fine constraint for a player's first water machine and
 * a bad one for their tenth: it ties the chain to terrain they did not choose,
 * and the usual answer — dig a channel to the machine room — is busywork that
 * looks like engineering. A {@link WaterReclaimerEntity} beside it does the
 * same job for the price of a machine and the power to run it.
 *
 * <h2>Why it asks the capability rather than the reclaimer</h2>
 *
 * Because then it is not the reclaimer specifically. Any tank from any mod
 * holding water supplies these machines, with nothing written on either side —
 * the same argument the energy capability makes, applied to the other fluid
 * this set cares about. Naming {@code WaterReclaimerEntity} here would have
 * been one line shorter and would have made the interlock private to Elysium.
 *
 * <h2>Why nothing is drained</h2>
 *
 * The algae sit in the water and the fish swim in it; neither drinks it. A lake
 * next to a machine is not consumed either, and a supply that quietly empties a
 * player's tank is a machine with a running cost nothing told them about.
 */
public final class SolarpunkWater {

    private SolarpunkWater() {
    }

    /**
     * How much water in a neighbouring tank counts as a supply.
     *
     * A bucket — a film of damp is not a pond, and one bucket is exactly what
     * it costs to place the source block this stands in for, so a tank is
     * never the cheap way round the requirement.
     */
    public static final int SUPPLY = FluidType.BUCKET_VOLUME;

    /** A source block against any face, or a tank beside it holding enough. */
    public static boolean touching(Level level, BlockPos position) {
        for (Direction face : Direction.values()) {
            BlockPos beside = position.relative(face);
            if (level.getFluidState(beside).isSource()) {
                return true;
            }
            IFluidHandler tank = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, beside, face.getOpposite());
            if (tank != null && holdsWater(tank)) {
                return true;
            }
        }
        return false;
    }

    /** At least {@link #SUPPLY} of water somewhere in the handler. */
    private static boolean holdsWater(IFluidHandler tank) {
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            FluidStack held = tank.getFluidInTank(slot);
            if (held.is(Fluids.WATER) && held.getAmount() >= SUPPLY) {
                return true;
            }
        }
        return false;
    }
}
