package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * Water and power to hydrogen and oxygen (Stellaris: 5 water to 10 hydrogen
 * and 5 oxygen for 50 FE). It hands its oxygen to neighbours, so a
 * distributor placed against it breathes without pipes.
 */
public class WaterSeparatorBlockEntity extends MachineBlockEntity {

    private final FluidTank water;
    private final FluidTank hydrogen;
    private final FluidTank oxygen;
    private int ticks;

    public WaterSeparatorBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, tank(MachineRules.SEPARATOR_WATER_TANK, () -> Fluids.WATER),
                tank(MachineRules.SEPARATOR_GAS_TANK, SpaceFluids.HYDROGEN.source),
                tank(MachineRules.SEPARATOR_GAS_TANK, SpaceFluids.OXYGEN.source));
    }

    private WaterSeparatorBlockEntity(BlockPos pos, BlockState state, FluidTank water, FluidTank hydrogen,
                                      FluidTank oxygen) {
        super(SpaceBlockEntities.WATER_SEPARATOR.get(), pos, state,
                new MachineEnergy(MachineRules.SEPARATOR_BUFFER, 1000, 0), List.of(water), List.of(oxygen, hydrogen));
        this.water = water;
        this.hydrogen = hydrogen;
        this.oxygen = oxygen;
    }

    public FluidTank hydrogenTank() {
        return hydrogen;
    }

    public FluidTank oxygenTank() {
        return oxygen;
    }

    private boolean separating;

    @Override
    public void serverTick() {
        if (separating) {
            ElysiumVoices.hum(level, worldPosition, "whine");
        }
        if (++ticks >= MachineRules.BATCH_TICKS) {
            ticks = 0;
            separating = false;
            if (MachineRules.separatorBatches(water.getFluidAmount(), hydrogen.getSpace(), oxygen.getSpace(),
                    energy.getEnergyStored()) > 0) {
                energy.consume(MachineRules.SEPARATOR_FE_PER_BATCH);
                separating = true;
                water.drain(MachineRules.WATER_PER_BATCH, IFluidHandler.FluidAction.EXECUTE);
                hydrogen.fill(new FluidStack(SpaceFluids.HYDROGEN.source.get(), MachineRules.HYDROGEN_PER_BATCH),
                        IFluidHandler.FluidAction.EXECUTE);
                oxygen.fill(new FluidStack(SpaceFluids.OXYGEN.source.get(), MachineRules.OXYGEN_PER_BATCH),
                        IFluidHandler.FluidAction.EXECUTE);
                setChanged();
            }
        }
        pushFluid(oxygen, 100);
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.separator", mb(water), mb(oxygen), mb(hydrogen),
                energy.getEnergyStored(), energy.getMaxEnergyStored());
    }
}
