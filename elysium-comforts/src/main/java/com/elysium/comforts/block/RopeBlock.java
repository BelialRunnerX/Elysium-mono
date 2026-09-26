package com.elysium.comforts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rope and a nail: the thing a hammock hangs from.
 *
 * <p>Its own block rather than a tag, because a hammock strung from anything
 * that happened to be overhead is a hammock you can sleep in under a tree,
 * and the whole point of a hammock costing two blocks of setting up is that
 * it is the slower of the two ways to rest.
 */
public class RopeBlock extends Block {

    private static final VoxelShape SHAPE = box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    public RopeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }
}
