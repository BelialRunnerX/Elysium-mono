package com.elysium.solarpunk.machine;

/** The reclaimer. Behaviour is in {@link WaterReclaimerEntity}. */
public class WaterReclaimerBlock extends SolarpunkMachineBlock {

    public WaterReclaimerBlock(Properties properties) {
        super(properties, SolarpunkMachines.WATER_RECLAIMER_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new WaterReclaimerEntity(pos, state);
    }
}
