package com.elysium.atelier.block;

import com.elysium.atelier.rules.CourseRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * One class for every course the game has no class of its own for.
 *
 * <p>The cuts the game already knows — the stair, the slab, the wall, the
 * ladder — are registered as the game's own classes, because their behaviour
 * is the interesting part and reimplementing it would be reimplementing a bug.
 * What is left is the shapes the game has never had: a vertical slab, five
 * ways up, a balcony and a platform. Those get this, and their collision comes
 * out of {@link CourseRules#boxesFor(String)} — the same boxes the harness
 * proved were walkable and the generator draws the model from.
 *
 * <p>So a stair that cannot be walked up is a build failure in three places at
 * once, rather than a block that renders correctly and puts the player back
 * where they started.
 */
public class CourseBlock extends HorizontalDirectionalBlock
        implements SimpleWaterloggedBlock {

    public static final MapCodec<CourseBlock> CODEC = simpleCodec(properties ->
            new CourseBlock(properties, CourseRules.VERTICAL_SLAB));

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final String course;
    private final Map<Direction, VoxelShape> boxes = new EnumMap<>(Direction.class);

    public CourseBlock(Properties properties, String course) {
        this(properties, course, CourseRules.boxesFor(course));
    }

    /**
     * The same block, drawn out of boxes somebody else looked up.
     *
     * <p>{@link TrimBlock} is the fittings and its boxes come from
     * {@link com.elysium.atelier.rules.TrimRules}, and everything else about
     * the two is identical — facing, waterlogging, rotation. Two classes for
     * that would be two places for one off-by-one in the rotation.
     */
    protected CourseBlock(Properties properties, String course,
                          java.util.List<com.elysium.atelier.rules.ArchRules.Box>
                                  drawn) {
        super(properties);
        this.course = course;
        VoxelShape north = ArchBlock.boxOf(drawn);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            boxes.put(facing, ArchBlock.turned(north, facing));
        }
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    /** Which course this is, in the rules' own terms. */
    public String course() {
        return course;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return boxes.getOrDefault(state.getValue(FACING),
                boxes.get(Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, context.getLevel()
                        .getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
