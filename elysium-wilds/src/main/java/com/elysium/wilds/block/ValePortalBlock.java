package com.elysium.wilds.block;

import com.elysium.wilds.level.ValeTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The gate. Standing in it takes you to the Vale, or back out of it.
 *
 * <p>Travel happens in {@code entityInside} for the rift's reason: it fires for
 * the entity actually touching the block, which is the question being asked. A
 * tick handler would test every player against every gate every tick to answer
 * the same thing less exactly.
 *
 * <p>No collision, so a player walks into it rather than onto it. The axis is
 * which way the ring faces, and the outline is a thin sheet across it, the way
 * a Nether portal's is, so the block behind a gate can still be aimed at.
 */
public class ValePortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final VoxelShape ALONG_X = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape ALONG_Z = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public ValePortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    /**
     * Sends a player through. Which way, where to and whether the far side
     * needs building are all ValeTravel's, because they depend on things this
     * block does not know. Server side only: moving the player on the client
     * is corrected a moment later and reads as a flicker of the wrong world.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        ValeTravel.use(player, pos);
    }
}
