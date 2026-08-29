package com.elysium.dungeons.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The frame a rift is stretched across.
 *
 * Its only behaviour is that breaking one closes the portal. Without that a
 * player could mine a frame block and leave a floating sheet of portal with no
 * frame around it — which still works, still teleports, and no longer looks
 * like anything a player built, so there is nothing to break to undo it.
 */
public class RiftFrameBlock extends Block {

    public RiftFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            RiftPortal.close(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
