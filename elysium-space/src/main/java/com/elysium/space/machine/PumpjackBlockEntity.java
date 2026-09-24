package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceData;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/**
 * Pumps the oil under its chunk (Stellaris's pumpjack): 10 mB a tick for
 * 10 FE, until the chunk's deposit runs dry. Deposits come from the seed and
 * the chunk position ({@link MachineRules#oilDeposit}); half of all chunks
 * have none. It hands its oil to neighbours, so a refinery against it runs.
 */
public class PumpjackBlockEntity extends MachineBlockEntity {

    private final FluidTank oil;
    private int lastDeposit = -1;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, tank(MachineRules.PUMP_TANK, SpaceFluids.OIL.source));
    }

    private PumpjackBlockEntity(BlockPos pos, BlockState state, FluidTank oil) {
        super(SpaceBlockEntities.PUMPJACK.get(), pos, state,
                new MachineEnergy(MachineRules.PUMP_BUFFER, 1000, 0), List.of(), List.of(oil));
        this.oil = oil;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        LevelChunk chunk = server.getChunkAt(worldPosition);
        int deposit = chunk.getData(SpaceData.CHUNK_OIL);
        if (deposit < 0) {
            deposit = MachineRules.oilDeposit(server.getSeed(), chunk.getPos().x, chunk.getPos().z);
            chunk.setData(SpaceData.CHUNK_OIL, deposit);
        }
        lastDeposit = deposit;
        int taken = MachineRules.pumped(deposit, oil.getSpace(), energy.getEnergyStored());
        if (taken > 0) {
            energy.consume(MachineRules.PUMP_FE_PER_TICK);
            oil.fill(new FluidStack(SpaceFluids.OIL.source.get(), taken), IFluidHandler.FluidAction.EXECUTE);
            chunk.setData(SpaceData.CHUNK_OIL, deposit - taken);
            setChanged();
        }
        pushFluid(oil, 100);
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.pumpjack", Math.max(0, lastDeposit), mb(oil),
                energy.getEnergyStored(), energy.getMaxEnergyStored());
    }
}
