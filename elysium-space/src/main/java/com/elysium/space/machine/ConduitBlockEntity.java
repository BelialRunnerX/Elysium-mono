package com.elysium.space.machine;

import com.elysium.space.world.MachineRules;
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

import java.util.List;

/**
 * A cable or pipe segment: a small buffer (its throughput) that it empties
 * into its neighbours every tick. Energy and fluid are only moved, never made
 * or lost, so a loop of conduits cannot duplicate anything.
 */
public abstract class ConduitBlockEntity extends MachineBlockEntity {

    protected ConduitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineEnergy energy,
                                 List<FluidTank> tanks) {
        super(type, pos, state, energy, tanks, tanks);
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
            pushEnergy(MachineRules.CABLE_FE_PER_TICK);
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
            pushFluid(buffer, MachineRules.PIPE_MB_PER_TICK);
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
