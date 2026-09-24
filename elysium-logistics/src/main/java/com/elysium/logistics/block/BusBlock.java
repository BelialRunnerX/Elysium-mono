package com.elysium.logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A run of bus. The connective tissue of a network.
 *
 * <p>It holds nothing and indexes nothing: it exists so that a controller and
 * a wall of drawers further apart than one block can be the same network. A
 * network is a thing you <em>lay out</em>, which is why drawers packed against
 * each other do not chain — otherwise every base would turn out to be one
 * enormous accidental network.
 */
public class BusBlock extends Block {

    public static final MapCodec<BusBlock> CODEC = simpleCodec(BusBlock::new);

    private static final VoxelShape SHAPE = box(5, 5, 5, 11, 11, 11);

    public BusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        LogisticNetwork.disturb(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            LogisticNetwork.disturb(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
