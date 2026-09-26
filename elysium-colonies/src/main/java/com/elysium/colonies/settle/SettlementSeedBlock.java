package com.elysium.colonies.settle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A survey stake: the world's promise that a town will stand here.
 *
 * The structure set puts one of these at every settlement site as the world
 * generates. It does nothing until the chunk it stands in is ticking, which
 * means somebody has come within simulation distance; then, on its first
 * random tick, it founds the town and is gone. So a settlement is created the
 * first time anyone is near enough to ever see it, and a world nobody walks
 * across is not running a hundred colonies nobody visits.
 *
 * <p>Deliberately not a block entity: a block entity ticks every game tick in
 * every loaded chunk, and this needs to act once. A random tick is the game's
 * own "occasionally, when somebody is around", and it costs nothing when
 * nobody is.
 */
public final class SettlementSeedBlock extends Block {

    private static final VoxelShape STAKE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 14.0D, 10.0D);

    public SettlementSeedBlock(Properties properties) {
        super(properties.randomTicks());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return STAKE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        // The stake is spent before the town is founded: it was projected
        // onto the world's generation surface, which in a forest is the
        // canopy, and the ground the town stands on is read from the column
        // below it once the stake itself is out of the way. Founded, the town
        // hall stands there; refused, the site was wrong and stays plain.
        level.removeBlock(pos, false);
        Settlements.found(level, pos);
    }
}
