package com.elysium.arcana.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crystal growing out of a wall.
 *
 * <p>The game's own amethyst cluster in every respect that matters — it faces
 * the block it grew from, it holds water, and it comes off if that block goes
 * away — because a player already knows how amethyst behaves and a second set
 * of rules for the same idea is a second thing to learn for no reason.
 *
 * <p>What is not the game's is the height: four sizes on one class, given at
 * construction, so the three buds and the cluster are one file rather than
 * four that have to be kept identical.
 */
public class GeodeClusterBlock extends Block implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final int height;
    private final int across;
    private final VoxelShape[] shapes;

    public GeodeClusterBlock(BlockBehaviour.Properties properties, int height,
                             int across) {
        super(properties);
        this.height = height;
        this.across = across;
        this.shapes = shapesFor(height, across);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false));
    }

    /** How tall this one stands, in pixels. */
    public int height() {
        return height;
    }

    private static VoxelShape[] shapesFor(int height, int across) {
        int low = (16 - across) / 2;
        int high = low + across;
        return new VoxelShape[]{
                Block.box(low, 16 - height, low, high, 16, high),          // DOWN
                Block.box(low, 0, low, high, height, high),                // UP
                Block.box(low, low, 16 - height, high, high, 16),          // NORTH
                Block.box(low, low, 0, high, high, height),                // SOUTH
                Block.box(16 - height, low, low, 16, high, high),          // WEST
                Block.box(0, low, low, height, high, high),                // EAST
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return shapes[state.getValue(FACING).ordinal()];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
                                     BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return direction == state.getValue(FACING).getOpposite()
                && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbour, level, pos,
                        neighbourPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos())
                .getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }
}
