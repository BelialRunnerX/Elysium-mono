package com.elysium.space.machine;

import com.elysium.lib.run.RunDyed;
import com.elysium.lib.run.RunNetwork;
import com.elysium.lib.run.RunRules;
import com.elysium.lib.run.Runs;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A cable or pipe segment: a small buffer (its throughput) that it empties
 * every tick into the devices on its network - every run joined to it by
 * the links the player sees, so a dyed line is its own network. A run never
 * hands to another run (that only sloshed the buffer back and forth along
 * the line and it never arrived) and never hands back out of a face it was
 * fed through this tick, so a tank pouring into a pipe does not get its
 * water straight back. Energy and fluid are only moved, never made or lost,
 * so a loop of conduits cannot duplicate anything.
 */
public abstract class ConduitBlockEntity extends MachineBlockEntity implements RunDyed {

    /** The run's dye, or undyed; see {@link RunRules}. */
    private String runDye = RunRules.UNDYED;

    @Override
    public String runDye() {
        return runDye;
    }

    @Override
    public void setRunDye(String dye) {
        runDye = RunRules.isDye(dye) ? dye : RunRules.UNDYED;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Runs.save(tag, runDye);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String before = runDye;
        runDye = Runs.load(tag);
        if (level != null && !level.isClientSide && !before.equals(runDye)) {
            Runs.relinkAround(level, worldPosition);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected ConduitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineEnergy energy,
                                 List<FluidTank> tanks) {
        super(type, pos, state, energy, tanks, tanks);
    }

    /** The faces something poured into this tick, as bits of {@link Direction#get3DDataValue()}. */
    private int fedFaces;

    private void fed(@Nullable Direction face) {
        if (face != null) {
            fedFaces |= 1 << face.get3DDataValue();
        }
    }

    /** The devices on this run's network, less the ones on faces that fed it this tick. */
    protected List<RunNetwork.Device> takers() {
        List<RunNetwork.Device> out = new ArrayList<>();
        for (RunNetwork.Device device : RunNetwork.walk(level, worldPosition).devices()) {
            Direction toward = null;
            for (Direction face : Direction.values()) {
                if ((fedFaces & 1 << face.get3DDataValue()) != 0 && worldPosition.relative(face).equals(device.pos())) {
                    toward = face;
                }
            }
            if (toward == null) {
                out.add(device);
            }
        }
        fedFaces = 0;
        return out;
    }

    /** Hands up to {@code max} FE to the network's devices that take it. */
    protected void carryEnergy(int max) {
        if (level == null || energy.getEnergyStored() <= 0) {
            return;
        }
        for (RunNetwork.Device device : takers()) {
            IEnergyStorage next = level.getCapability(Capabilities.EnergyStorage.BLOCK, device.pos(), device.face());
            if (next == null || !next.canReceive()) {
                continue;
            }
            int taken = next.receiveEnergy(Math.min(max, energy.getEnergyStored()), false);
            if (taken > 0) {
                energy.consume(taken);
                setChanged();
            }
            if (energy.getEnergyStored() <= 0) {
                return;
            }
        }
    }

    /** Hands up to {@code max} mB to the network's devices that take it. */
    protected void carryFluid(FluidTank from, int max) {
        if (level == null || from.isEmpty()) {
            return;
        }
        for (RunNetwork.Device device : takers()) {
            IFluidHandler next = level.getCapability(Capabilities.FluidHandler.BLOCK, device.pos(), device.face());
            if (next == null) {
                continue;
            }
            if (!FluidUtil.tryFluidTransfer(next, from, max, true).isEmpty()) {
                setChanged();
            }
            if (from.isEmpty()) {
                return;
            }
        }
    }

    /** The energy store as the neighbour on {@code face} sees it: the same store, remembering who fed it. */
    public IEnergyStorage energyFrom(@Nullable Direction face) {
        IEnergyStorage store = energy();
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                int taken = store.receiveEnergy(toReceive, simulate);
                if (taken > 0 && !simulate) {
                    fed(face);
                }
                return taken;
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return store.extractEnergy(toExtract, simulate);
            }

            @Override
            public int getEnergyStored() {
                return store.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return store.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return store.canExtract();
            }

            @Override
            public boolean canReceive() {
                return store.canReceive();
            }
        };
    }

    /** The tanks as the neighbour on {@code face} sees them: the same tanks, remembering who fed them. */
    public IFluidHandler fluidsFrom(@Nullable Direction face) {
        IFluidHandler tanks = fluids();
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return tanks.getTanks();
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return tanks.getFluidInTank(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return tanks.getTankCapacity(tank);
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return tanks.isFluidValid(tank, stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int taken = tanks.fill(resource, action);
                if (taken > 0 && action.execute()) {
                    fed(face);
                }
                return taken;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return tanks.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return tanks.drain(maxDrain, action);
            }
        };
    }

    /** Energy: a cable holding {@link MachineRules#CABLE_FE_PER_TICK}. */
    public static class Cable extends ConduitBlockEntity {
        public Cable(BlockPos pos, BlockState state) {
            super(com.elysium.space.registry.SpaceBlockEntities.CABLE.get(), pos, state,
                    new MachineEnergy(MachineRules.CABLE_FE_PER_TICK, MachineRules.CABLE_FE_PER_TICK,
                            MachineRules.CABLE_FE_PER_TICK), List.of());
        }

        @Override
        public void serverTick() {
            carryEnergy(MachineRules.CABLE_FE_PER_TICK);
        }

        @Override
        public Component status() {
            return Component.translatable("elysiumspace.machine.cable", energy.getEnergyStored(),
                    MachineRules.CABLE_FE_PER_TICK);
        }
    }

    /** Fluids: a pipe holding {@link MachineRules#PIPE_MB_PER_TICK} of any one fluid. */
    public static class Pipe extends ConduitBlockEntity {
        private final FluidTank buffer;

        public Pipe(BlockPos pos, BlockState state) {
            this(pos, state, new FluidTank(MachineRules.PIPE_MB_PER_TICK));
        }

        private Pipe(BlockPos pos, BlockState state, FluidTank buffer) {
            super(com.elysium.space.registry.SpaceBlockEntities.PIPE.get(), pos, state,
                    new MachineEnergy(0, 0, 0), List.of(buffer));
            this.buffer = buffer;
        }

        @Override
        public void serverTick() {
            carryFluid(buffer, MachineRules.PIPE_MB_PER_TICK);
        }

        @Override
        public Component status() {
            FluidStack in = buffer.getFluid();
            return Component.translatable("elysiumspace.machine.pipe",
                    in.isEmpty() ? Component.translatable("elysiumspace.machine.empty") : in.getHoverName(),
                    buffer.getFluidAmount(), buffer.getCapacity());
        }
    }
}
