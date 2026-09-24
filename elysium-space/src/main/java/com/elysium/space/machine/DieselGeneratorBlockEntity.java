package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/** Burns the refinery's diesel: 40 FE a tick, twice a coal generator. */
public class DieselGeneratorBlockEntity extends MachineBlockEntity {

    private final FluidTank diesel;
    private int ticks;
    private boolean running;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, tank(MachineRules.DIESEL_TANK, SpaceFluids.DIESEL.source));
    }

    private DieselGeneratorBlockEntity(BlockPos pos, BlockState state, FluidTank diesel) {
        super(SpaceBlockEntities.DIESEL_GENERATOR.get(), pos, state,
                new MachineEnergy(MachineRules.GENERATOR_BUFFER, 0, MachineRules.PUSH_PER_TICK),
                List.of(diesel), List.of());
        this.diesel = diesel;
    }

    @Override
    public void serverTick() {
        if (++ticks >= MachineRules.DIESEL_BURN_TICKS) {
            ticks = 0;
            running = diesel.getFluidAmount() >= MachineRules.DIESEL_MB_PER_BURN
                    && energy.getMaxEnergyStored() - energy.getEnergyStored() >= MachineRules.DIESEL_FE_PER_BURN;
            if (running) {
                diesel.drain(MachineRules.DIESEL_MB_PER_BURN, IFluidHandler.FluidAction.EXECUTE);
                energy.produce(MachineRules.DIESEL_FE_PER_BURN);
                setChanged();
            }
        }
        pushEnergy(MachineRules.PUSH_PER_TICK);
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.diesel", mb(diesel), energy.getEnergyStored(),
                energy.getMaxEnergyStored(), Component.translatable(running ? "elysiumspace.machine.running"
                        : "elysiumspace.machine.idle"));
    }
}
