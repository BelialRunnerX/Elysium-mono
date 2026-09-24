package com.elysium.solarpunk.machine;

/** Presses plant matter and mycelium into panels and fibre. Behaviour is in {@link MyceliumPressEntity}. */
public class MyceliumPressBlock extends SolarpunkMachineBlock {

    public MyceliumPressBlock(Properties properties) {
        super(properties, SolarpunkMachines.MYCELIUM_PRESS_ENTITY);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new MyceliumPressEntity(pos, state);
    }
}
