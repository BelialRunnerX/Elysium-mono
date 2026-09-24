package com.elysium.solarpunk.machine;

/** The harvester. Behaviour is in {@link HarvesterEntity}. */
public class HarvesterBlock extends SolarpunkMachineBlock {

    public HarvesterBlock(Properties properties) {
        super(properties, SolarpunkMachines.HARVESTER_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new HarvesterEntity(pos, state);
    }
}
