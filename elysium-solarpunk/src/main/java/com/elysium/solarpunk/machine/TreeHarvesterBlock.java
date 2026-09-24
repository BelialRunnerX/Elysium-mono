package com.elysium.solarpunk.machine;

/** The tree harvester. Behaviour is in {@link TreeHarvesterEntity}. */
public class TreeHarvesterBlock extends SolarpunkMachineBlock {

    public TreeHarvesterBlock(Properties properties) {
        super(properties, SolarpunkMachines.TREE_HARVESTER_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new TreeHarvesterEntity(pos, state);
    }
}
