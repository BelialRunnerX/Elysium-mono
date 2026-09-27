package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.PowerClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * What every solarpunk machine has: a buffer, a tick, and a way to be emptied.
 *
 * <h2>The energy is NeoForge's, not Elysium's</h2>
 *
 * {@link EnergyStorage} and the {@code IEnergyStorage} capability are the
 * platform's own, and using them rather than inventing a parallel unit is the
 * single most valuable decision in this file. A solar condenser charges a
 * machine from any tech mod a player already has, and any of their generators
 * runs an Elysium bioreactor, with nothing written on either side. A private
 * "solar charge" would have been one line different to implement and would have
 * walled this set off from every other mod in the pack.
 *
 * <h2>Why the buffer is saved and the progress is not</h2>
 *
 * Energy is a thing a player accumulated and would notice losing. Progress
 * toward the next output is at most a few seconds of work, and saving it means
 * another tag to read, write, migrate and get wrong. Losing it on a chunk
 * unload is invisible; losing a full buffer is a complaint.
 *
 * <h2>How a machine knows it is browned out</h2>
 *
 * It does not have to be told, and no machine's tick had to be edited to teach
 * it. Every machine in this set asks the same question the same way — simulate
 * the draw, and if the simulation comes up short, give up for the tick — so
 * the buffer itself can count. A simulated extraction that could not be paid
 * is a starved tick; a real one that was paid clears the count.
 *
 * That placement is the whole reason this is reliable. The alternative was a
 * {@code paid()} call in each of seven ticks, which is seven chances to forget
 * one, and a machine that forgot would simply never report a brownout — no
 * error, no log, just a machine that is never listed as the reason the base is
 * struggling.
 */
public abstract class SolarpunkMachineEntity extends BlockEntity {

    /**
     * Consecutive ticks this machine asked for power and could not pay.
     *
     * Two seconds of them is a brownout. One tick is not — a machine that
     * drains its buffer on the tick a cloud passes is not a base in trouble,
     * and a readout that flickered every time one would be noise a player
     * learns to ignore.
     */
    public static final int BROWNOUT_AFTER = 40;

    protected final Store energy;

    private int unpaidTicks;

    protected SolarpunkMachineEntity(BlockEntityType<?> type, BlockPos pos,
                                     BlockState state,
                                     int capacity, int maxReceive, int maxExtract) {
        super(type, pos, state);
        this.energy = new Store(capacity, maxReceive, maxExtract);
    }

    /**
     * The machine's buffer. What a neighbour may put in or take out is
     * bounded by the receive and extract limits, as any FE store; what the
     * machine itself makes or spends is not, because a consumer lets no
     * cable drain it (extract limit 0) and a source takes nothing from a
     * cable (receive limit 0), and each still has to do its own work.
     * Before this every machine went through the outward limits for its
     * own work, so a condenser made nothing and a kiln never fired.
     */
    protected class Store extends EnergyStorage {
        Store(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        /** Adds what the machine made itself; returns what fit. */
        public int produce(int amount) {
            int added = Math.max(0, Math.min(capacity - energy, amount));
            if (added > 0) {
                energy += added;
                setChanged();
            }
            return added;
        }

        /** Whether the buffer covers a cost; a short buffer is noted as unpaid, the way a starved machine is. */
        public boolean canSpend(int amount) {
            if (energy < amount) {
                noteUnpaid();
                return false;
            }
            return true;
        }

        /** Spends a cost the machine's own work incurred. */
        public boolean spend(int amount) {
            if (!canSpend(amount)) {
                return false;
            }
            energy -= amount;
            notePaid();
            setChanged();
            return true;
        }
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int extracted = super.extractEnergy(toExtract, simulate);
            if (toExtract > 0) {
                if (extracted < toExtract) {
                    // Asked, and the buffer could not cover it. Counted on
                    // the simulated call as well as the real one, because
                    // the simulated call is the only one a starved machine
                    // ever makes — it gives up before spending.
                    noteUnpaid();
                } else if (!simulate) {
                    notePaid();
                }
            }
            if (extracted > 0 && !simulate) {
                setChanged();
            }
            return extracted;
        }
    }

    public EnergyStorage getEnergy() {
        return energy;
    }

    /**
     * Which class of load this machine is, for {@code PowerBudget}.
     *
     * Industry by default, because most machines are. A machine whose loss
     * would cost a player their base says so by overriding this, and the
     * override is one line beside the machine's other constants rather than an
     * entry in a table somewhere else that can be forgotten when the machine is
     * renamed.
     */
    public PowerClass powerClass() {
        return PowerClass.INDUSTRY;
    }

    /** Whether this machine has been going unpaid long enough to say so. */
    public boolean isBrownedOut() {
        return unpaidTicks >= BROWNOUT_AFTER;
    }

    /** How long it has been short. Reported; also what the harness reads. */
    public int unpaidTicks() {
        return unpaidTicks;
    }

    private void noteUnpaid() {
        // Saturating rather than wrapping. A machine left unpowered for a
        // fortnight would otherwise roll past Integer.MAX_VALUE into a negative
        // count and report itself healthy, which is the exact moment a player
        // would be looking at the readout to find out why nothing works.
        if (unpaidTicks < BROWNOUT_AFTER) {
            unpaidTicks++;
        }
    }

    private void notePaid() {
        unpaidTicks = 0;
    }

    /** One server tick's work. */
    public abstract void serverTick();

    /**
     * What spills when the block is broken.
     *
     * Empty for a machine that holds nothing, which is most of them. A machine
     * that holds items and does not override this eats them, and the player
     * finds out later.
     */
    public NonNullList<ItemStack> drops() {
        return NonNullList.create();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
    }
}
