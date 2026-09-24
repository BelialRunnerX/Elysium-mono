package com.elysium.space.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/**
 * A machine's tanks as one fluid handler. Pipes and buckets fill only the
 * input tanks and drain only the output tanks: a refinery takes oil and gives
 * fuel, and nothing outside can pour fuel back in.
 */
public class MachineTanks implements IFluidHandler {

    private final List<FluidTank> inputs;
    private final List<FluidTank> outputs;
    private final List<FluidTank> all;

    public MachineTanks(List<FluidTank> inputs, List<FluidTank> outputs) {
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.all = new java.util.ArrayList<>(inputs);
        this.all.addAll(outputs);
    }

    @Override
    public int getTanks() {
        return all.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return all.get(tank).getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return all.get(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return inputs.contains(all.get(tank)) && all.get(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (FluidTank tank : inputs) {
            if (tank.isFluidValid(resource)) {
                int n = tank.fill(resource, action);
                if (n > 0) {
                    return n;
                }
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (FluidTank tank : outputs) {
            if (FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                return tank.drain(resource, action);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank tank : outputs) {
            if (!tank.isEmpty()) {
                return tank.drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
