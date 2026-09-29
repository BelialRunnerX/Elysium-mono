package com.elysium.colonies.settle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ruin marker: the world's own mark at a site where a building once stood.
 *
 * <p>Placed by the ruins structure set (RuinRules' grid), and like the survey
 * stake it acts once: on its first random tick it is removed and asks
 * {@link Ruins#raise} to leave what the building became. A plain block with
 * random ticks, not a block entity, for the same reason as the stake: a thing
 * that acts once must not tick every tick in every loaded chunk.
 */
public class RuinSeedBlock extends Block {

    public RuinSeedBlock(Properties properties) {
        super(properties.randomTicks());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The marker is spent before the ruin is raised: it was projected
        // onto the generation surface and the ruin looks for its own ground.
        level.removeBlock(pos, false);
        Ruins.raise(level, pos);
    }
}
