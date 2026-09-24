package com.elysium.solarpunk.machine;

/** The tank. Behaviour is in {@link AquaponicsTankEntity}. */
public class AquaponicsTankBlock extends SolarpunkMachineBlock {

    public AquaponicsTankBlock(Properties properties) {
        super(properties, SolarpunkMachines.AQUAPONICS_TANK_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new AquaponicsTankEntity(pos, state);
    }
}
