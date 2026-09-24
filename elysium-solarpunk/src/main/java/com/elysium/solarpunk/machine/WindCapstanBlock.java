package com.elysium.solarpunk.machine;

/** The capstan. Behaviour is in {@link WindCapstanEntity}. */
public class WindCapstanBlock extends SolarpunkMachineBlock {

    public WindCapstanBlock(Properties properties) {
        super(properties, SolarpunkMachines.WIND_CAPSTAN_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new WindCapstanEntity(pos, state);
    }
}
