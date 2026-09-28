package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.PowerClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * Condenses clean water out of air and rain, and stores it.
 *
 * <h2>What this unlocks</h2>
 *
 * The bioreactor needs water touching it, which until now meant a lake. That is
 * a fine constraint for the first bioreactor and a bad one for the tenth: it
 * ties the whole production chain to terrain the player did not choose, and the
 * usual answer — dig a channel — is busywork that looks like engineering.
 *
 * A reclaimer holding water counts as that supply. So the chain becomes
 * portable: power a reclaimer, put a bioreactor against it, and the set works in
 * a desert or on a platform in the sky. It costs a machine and the power to run
 * it, which is the right price for not having to move the water.
 *
 * <h2>Rain is a rate, not a switch</h2>
 *
 * It condenses in clear weather too, slowly. Making rain the only source would
 * mean a machine that is dead for most of a week and then floods, and a player
 * cannot plan around weather. Rain multiplies what it already does, which is
 * something they can feel without having to watch the sky.
 *
 * <h2>Why it exposes a fluid handler</h2>
 *
 * The same argument the energy capability made. Any mod's pipe can empty this
 * and any mod's tank can be filled from it, with nothing written on either side.
 * It also fills a cauldron sitting next to it, because that is the vanilla way
 * to hold water and a player will try it before they try anything else.
 */
public class WaterReclaimerEntity extends SolarpunkMachineEntity {

    /**
     * How much water it holds. Eight buckets: enough that a reclaimer keeps a
     * bioreactor supplied through a long clear spell, and not so much that it
     * is a water tank a player would build instead of a cistern.
     */
    public static final int TANK_CAPACITY = 8 * FluidType.BUCKET_VOLUME;

    /**
     * How much power it holds, which is a different quantity entirely and is
     * given its own name because sharing one constant between millibuckets and
     * energy units is how a later edit silently changes both.
     */
    public static final int ENERGY_CAPACITY = 16_000;

    public static final int DRAW = 8;

    /** Millibuckets condensed per tick of draw, in clear weather. */
    public static final int CLEAR_RATE = 1;

    /** What rain multiplies that by. */
    public static final int RAIN_FACTOR = 4;

    /** Below this it is not a supply — a film of damp is not a pond. */
    public static final int SUPPLY_THRESHOLD = FluidType.BUCKET_VOLUME;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid() == Fluids.WATER) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public WaterReclaimerEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.WATER_RECLAIMER_ENTITY.get(), pos, state,
                ENERGY_CAPACITY, DRAW * 20, 0);
    }

    public FluidTank getTank() {
        return tank;
    }

    /** Whether this reclaimer is holding enough to count as a water supply. */
    public boolean isSupply() {
        return tank.getFluidAmount() >= SUPPLY_THRESHOLD;
    }

    /** Millibuckets per tick, given the weather and whether it is powered. */
    public static int rateFor(boolean raining, boolean seesSky) {
        if (!seesSky) {
            return 0;                   // sealed in, there is no air to work on
        }
        return raining ? CLEAR_RATE * RAIN_FACTOR : CLEAR_RATE;
    }

    /**
     * Critical. A base without water is a base a player has to leave.
     *
     * This is the machine the whole priority system exists for: when the sun
     * goes in, the reclaimer keeps filling and the looms stop, rather than
     * eleven machines each running at a ninth of a job.
     */
    @Override
    public PowerClass powerClass() {
        return PowerClass.CRITICAL;
    }

    @Override
    public void serverTick() {
        if (tank.getFluidAmount() < TANK_CAPACITY) {
            int rate = rateFor(level.isRainingAt(worldPosition.above()),
                    level.canSeeSky(worldPosition.above()));
            if (rate > 0 && energy.canSpend(DRAW)) {
                energy.spend(DRAW);
                ElysiumVoices.hum(level, worldPosition, "pump");
                tank.fill(new FluidStack(Fluids.WATER, rate),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler
                                .FluidAction.EXECUTE);
            }
        }
        fillCauldrons();
    }

    /**
     * Top up a cauldron beside it, one layer at a time.
     *
     * Only an empty or partly filled water cauldron, and never a lava or powder
     * snow one — quietly converting somebody's lava cauldron into water would
     * be a machine destroying something it was not asked to touch.
     */
    private void fillCauldrons() {
        if (tank.getFluidAmount() < FluidType.BUCKET_VOLUME / 3) {
            return;
        }
        for (Direction face : Direction.values()) {
            BlockPos beside = worldPosition.relative(face);
            BlockState state = level.getBlockState(beside);

            if (state.is(Blocks.CAULDRON)) {
                level.setBlockAndUpdate(beside,
                        Blocks.WATER_CAULDRON.defaultBlockState());
            } else if (state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL)
                    < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                level.setBlockAndUpdate(beside, state.setValue(
                        LayeredCauldronBlock.LEVEL,
                        state.getValue(LayeredCauldronBlock.LEVEL) + 1));
            } else {
                continue;
            }
            // A third of a bucket per layer, which is what a cauldron layer is
            // worth in vanilla. Charging a whole bucket would make filling one
            // by machine three times more expensive than doing it by hand.
            tank.drain(FluidType.BUCKET_VOLUME / 3,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler
                            .FluidAction.EXECUTE);
            return;
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) {
            tank.readFromNBT(registries, tag.getCompound("tank"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
    }
}
