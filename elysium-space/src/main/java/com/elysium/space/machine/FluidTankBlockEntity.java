package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceData;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/**
 * 16 000 mB of any one fluid. It keeps its contents when broken (the item
 * carries them), and it drains downwards into whatever takes fluid below it,
 * like a hopper, so a tank on top of a machine feeds it.
 */
public class FluidTankBlockEntity extends MachineBlockEntity {

    private final FluidTank tank;

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, new FluidTank(MachineRules.FLUID_TANK));
    }

    private FluidTankBlockEntity(BlockPos pos, BlockState state, FluidTank tank) {
        super(SpaceBlockEntities.FLUID_TANK.get(), pos, state, new MachineEnergy(0, 0, 0), List.of(tank), List.of(tank));
        this.tank = tank;
    }

    @Override
    public void serverTick() {
        if (level == null || tank.isEmpty()) {
            return;
        }
        IFluidHandler below = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (below != null && !FluidUtil.tryFluidTransfer(below, tank, 100, true).isEmpty()) {
            setChanged();
        }
    }

    @Override
    public Component status() {
        FluidStack in = tank.getFluid();
        return Component.translatable("elysiumspace.machine.pipe",
                in.isEmpty() ? Component.translatable("elysiumspace.machine.empty") : in.getHoverName(),
                tank.getFluidAmount(), tank.getCapacity());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        SimpleFluidContent held = input.getOrDefault(SpaceData.TANK_FLUID.get(), SimpleFluidContent.EMPTY);
        tank.setFluid(held.copy());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!tank.isEmpty()) {
            builder.set(SpaceData.TANK_FLUID.get(), SimpleFluidContent.copyOf(tank.getFluid()));
        }
    }
}
