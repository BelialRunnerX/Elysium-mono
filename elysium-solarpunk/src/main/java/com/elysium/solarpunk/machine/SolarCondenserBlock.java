package com.elysium.solarpunk.machine;

/** The condenser. Behaviour is in {@link SolarCondenserEntity}. */
public class SolarCondenserBlock extends SolarpunkMachineBlock {

    public SolarCondenserBlock(Properties properties) {
        super(properties, SolarpunkMachines.SOLAR_CONDENSER_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new SolarCondenserEntity(pos, state);
    }
}
