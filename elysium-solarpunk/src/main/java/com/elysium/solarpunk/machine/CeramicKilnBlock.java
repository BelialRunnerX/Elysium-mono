package com.elysium.solarpunk.machine;

/** The kiln. Behaviour is in {@link CeramicKilnEntity}. */
public class CeramicKilnBlock extends SolarpunkMachineBlock {

    public CeramicKilnBlock(Properties properties) {
        super(properties, SolarpunkMachines.CERAMIC_KILN_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new CeramicKilnEntity(pos, state);
    }
}
