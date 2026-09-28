package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import com.elysium.lib.sound.ElysiumVoices;

/** Oil and power to rocket fuel and diesel (Stellaris's recipe: 10 oil to 5 fuel and 5 diesel for 100 FE). */
public class FuelRefineryBlockEntity extends MachineBlockEntity {

    private final FluidTank oil;
    private final FluidTank fuel;
    private final FluidTank diesel;
    private int ticks;

    public FuelRefineryBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, tank(MachineRules.REFINERY_TANK, SpaceFluids.OIL.source),
                tank(MachineRules.REFINERY_TANK, SpaceFluids.FUEL.source),
                tank(MachineRules.REFINERY_TANK, SpaceFluids.DIESEL.source));
    }

    private FuelRefineryBlockEntity(BlockPos pos, BlockState state, FluidTank oil, FluidTank fuel, FluidTank diesel) {
        super(SpaceBlockEntities.FUEL_REFINERY.get(), pos, state,
                new MachineEnergy(MachineRules.REFINERY_BUFFER, 1000, 0), List.of(oil), List.of(fuel, diesel));
        this.oil = oil;
        this.fuel = fuel;
        this.diesel = diesel;
    }

    public FluidTank fuelTank() {
        return fuel;
    }

    private boolean refining;

    @Override
    public void serverTick() {
        if (refining) {
            ElysiumVoices.hum(level, worldPosition, "roar");
        }
        if (++ticks < MachineRules.BATCH_TICKS) {
            return;
        }
        ticks = 0;
        refining = false;
        if (MachineRules.refineryBatches(oil.getFluidAmount(), fuel.getSpace(), diesel.getSpace(),
                energy.getEnergyStored()) > 0) {
            energy.consume(MachineRules.REFINERY_FE_PER_BATCH);
            refining = true;
            oil.drain(MachineRules.OIL_PER_BATCH, IFluidHandler.FluidAction.EXECUTE);
            fuel.fill(new FluidStack(SpaceFluids.FUEL.source.get(), MachineRules.FUEL_PER_BATCH),
                    IFluidHandler.FluidAction.EXECUTE);
            diesel.fill(new FluidStack(SpaceFluids.DIESEL.source.get(), MachineRules.DIESEL_PER_BATCH),
                    IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.refinery", mb(oil), mb(fuel), mb(diesel),
                energy.getEnergyStored(), energy.getMaxEnergyStored());
    }
}
