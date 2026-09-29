package com.elysium.wilds.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a way into the Vale is built from.
 *
 * <p>Its one behaviour is that breaking it closes the gate it holds. Without
 * that a player could mine a frame and leave a sheet of gate hanging in the air
 * that still works, still sends people to the Vale, and no longer looks like
 * anything anybody built, so there is nothing left to break to undo it.
 */
public class ValeFrameBlock extends Block {

    public ValeFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            ValePortal.close(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
