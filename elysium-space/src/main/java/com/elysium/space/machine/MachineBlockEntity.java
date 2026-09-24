package com.elysium.space.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/**
 * What every space machine shares: an FE store, its tanks as one handler, a
 * server tick, saving, and handing energy or fluid to its neighbours. The
 * neighbours can be our machines or anything from another mod that takes FE or
 * fluids, so cables and pipes are optional.
 */
public abstract class MachineBlockEntity extends BlockEntity {

    protected final MachineEnergy energy;
    private final List<FluidTank> tanks;
    private final MachineTanks handler;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineEnergy energy,
                                 List<FluidTank> inputs, List<FluidTank> outputs) {
        super(type, pos, state);
        this.energy = energy;
        this.handler = new MachineTanks(inputs, outputs);
        this.tanks = new java.util.ArrayList<>(inputs);
        this.tanks.addAll(outputs);
    }

    public MachineEnergy energy() {
        return energy;
    }

    public IFluidHandler fluids() {
        return handler;
    }

    /** Every tank, inputs then outputs, as the machine screen shows them. */
    public List<FluidTank> tankList() {
        return tanks;
    }

    /** The machine's item slots for its screen, or null when it has none (only the coal generator has one). */
    public net.neoforged.neoforge.items.IItemHandler slots() {
        return null;
    }

    /** Called on the server every tick by the block's ticker. */
    public abstract void serverTick();

    /** One line for the player: what it holds and whether it is working. */
    public abstract Component status();

    protected static FluidTank tank(int capacity, java.util.function.Supplier<? extends net.minecraft.world.level.material.Fluid> only) {
        return new FluidTank(capacity, stack -> stack.getFluid().isSame(only.get()));
    }

    /** Hand up to {@code max} FE to each neighbour that takes it. */
    protected void pushEnergy(int max) {
        if (level == null || energy.getEnergyStored() <= 0) {
            return;
        }
        for (Direction side : Direction.values()) {
            IEnergyStorage next = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(side), side.getOpposite());
            if (next == null || !next.canReceive() || next == energy) {
                continue;
            }
            int offer = Math.min(max, energy.getEnergyStored());
            int taken = next.receiveEnergy(offer, false);
            if (taken > 0) {
                energy.consume(taken);
                setChanged();
            }
        }
    }

    /** Hand up to {@code max} mB from an output tank to each neighbour that takes it. */
    protected void pushFluid(FluidTank from, int max) {
        if (level == null || from.isEmpty()) {
            return;
        }
        for (Direction side : Direction.values()) {
            IFluidHandler next = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(side), side.getOpposite());
            if (next == null || next == handler) {
                continue;
            }
            FluidStack moved = FluidUtil.tryFluidTransfer(next, from, max, true);
            if (!moved.isEmpty()) {
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        for (int i = 0; i < tanks.size(); i++) {
            tag.put("Tank" + i, tanks.get(i).writeToNBT(registries, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            energy.deserializeNBT(registries, tag.get("Energy"));
        }
        for (int i = 0; i < tanks.size(); i++) {
            tanks.get(i).readFromNBT(registries, tag.getCompound("Tank" + i));
        }
    }

    protected static String mb(FluidTank t) {
        return t.getFluidAmount() + "/" + t.getCapacity();
    }
}
