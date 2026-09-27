package com.elysium.atelier.block;

import com.elysium.atelier.rules.ArchRules;
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
 * One class for every architectural piece that faces a way.
 *
 * <p>The same argument as {@link ShapedBlock}: a roof slope and a window sill
 * differ in a box, and twenty-one classes would be twenty-one places for the
 * same bug. The box comes from the piece's role and how tall the rules say it
 * stands, so a piece retuned in {@link ArchRules} is a piece whose collision
 * changes with it rather than a piece whose collision quietly disagrees.
 */
public class ArchBlock extends HorizontalDirectionalBlock
        implements SimpleWaterloggedBlock {

    public static final MapCodec<ArchBlock> CODEC = simpleCodec(properties ->
            new ArchBlock(properties, ArchRules.PIECES.get(0)));

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final ArchRules.Piece piece;
    private final Map<Direction, VoxelShape> boxes = new EnumMap<>(Direction.class);

    public ArchBlock(Properties properties, ArchRules.Piece piece) {
        super(properties);
        this.piece = piece;
        VoxelShape north = boxFor(piece);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            boxes.put(facing, turned(north, facing));
        }
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    /** Which piece this is, in the rules' own terms. */
    public ArchRules.Piece piece() {
        return piece;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /**
     * The box a piece occupies, facing north.
     *
     * <p>Worked out from what the rules say it is rather than from its name.
     * A path is as deep as the rules say a path is, so the check that a path
     * is never as deep as a slab is a check about the block a player stands
     * on rather than about a number in a different file.
     */
    public static VoxelShape boxFor(ArchRules.Piece piece) {
        return boxOf(ArchRules.boxesFor(piece));
    }

    /**
     * A list of boxes, unioned into the shape a player walks into.
     *
     * <p>Public and taking boxes rather than a piece because
     * {@link CourseBlock} draws its stairs and balconies out of the same
     * rules' boxes, and two copies of this union is two places for the same
     * off-by-one.
     */
    public static VoxelShape boxOf(java.util.List<ArchRules.Box> boxes) {
        VoxelShape built = net.minecraft.world.phys.shapes.Shapes.empty();
        for (ArchRules.Box box : boxes) {
            built = net.minecraft.world.phys.shapes.Shapes.or(built,
                    Block.box(box.x1(), box.y1(), box.z1(),
                            box.x2(), box.y2(), box.z2()));
        }
        return built;
    }

    /**
     * One box, turned to face another way.
     *
     * <p>Rotating the numbers rather than writing four boxes: four boxes is
     * three chances to get one of them wrong, and a wrong one is a block you
     * can stand inside from exactly one direction.
     */
    public static VoxelShape turned(VoxelShape north, Direction facing) {
        return switch (facing) {
            case SOUTH -> flip(north, true, true);
            case WEST -> swap(north, false);
            case EAST -> swap(north, true);
            default -> north;
        };
    }

    private static VoxelShape flip(VoxelShape box, boolean x, boolean z) {
        VoxelShape[] turned = {net.minecraft.world.phys.shapes.Shapes.empty()};
        box.forAllBoxes((x1, y1, z1, x2, y2, z2) -> turned[0] =
                net.minecraft.world.phys.shapes.Shapes.or(turned[0],
                        Block.box(x ? (1 - x2) * 16 : x1 * 16, y1 * 16,
                                z ? (1 - z2) * 16 : z1 * 16,
                                x ? (1 - x1) * 16 : x2 * 16, y2 * 16,
                                z ? (1 - z1) * 16 : z2 * 16)));
        return turned[0];
    }

    private static VoxelShape swap(VoxelShape box, boolean mirror) {
        VoxelShape[] turned = {net.minecraft.world.phys.shapes.Shapes.empty()};
        box.forAllBoxes((x1, y1, z1, x2, y2, z2) -> turned[0] =
                net.minecraft.world.phys.shapes.Shapes.or(turned[0],
                        Block.box(mirror ? (1 - z2) * 16 : z1 * 16, y1 * 16,
                                mirror ? x1 * 16 : (1 - x2) * 16,
                                mirror ? (1 - z1) * 16 : z2 * 16, y2 * 16,
                                mirror ? x2 * 16 : (1 - x1) * 16)));
        return turned[0];
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
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                     BlockPos neighbourPos) {
        // A waterlogged shape is water to its neighbours: without this tick
        // the water in a balcony never flowed (the mechanics survey, 27 September 2026).
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, side, neighbour, level, pos, neighbourPos);
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
