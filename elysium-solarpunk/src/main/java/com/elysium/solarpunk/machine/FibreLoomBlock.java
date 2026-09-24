package com.elysium.solarpunk.machine;

/** Weaves cane and fibre into cloth, cord and panels. Behaviour is in {@link FibreLoomEntity}. */
public class FibreLoomBlock extends SolarpunkMachineBlock {

    public FibreLoomBlock(Properties properties) {
        super(properties, SolarpunkMachines.FIBRE_LOOM_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new FibreLoomEntity(pos, state);
    }
}
