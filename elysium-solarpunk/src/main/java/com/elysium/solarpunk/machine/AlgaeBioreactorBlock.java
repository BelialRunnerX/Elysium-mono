package com.elysium.solarpunk.machine;

/** The bioreactor. Behaviour is in {@link AlgaeBioreactorEntity}. */
public class AlgaeBioreactorBlock extends SolarpunkMachineBlock {

    public AlgaeBioreactorBlock(Properties properties) {
        super(properties, SolarpunkMachines.ALGAE_BIOREACTOR_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new AlgaeBioreactorEntity(pos, state);
    }
}
