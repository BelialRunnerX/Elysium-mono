package com.elysium.atelier.block;

import com.elysium.atelier.rules.AtelierRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * One class for every shape that faces a way.
 *
 * <h2>Why there is one block class and not twelve</h2>
 *
 * Because a stool and a shelf differ in a box and nothing else. Twelve classes
 * would be twelve places for the same bug, and a thirteenth shape would be a
 * thirteenth file rather than a line in {@link AtelierRules#SHAPES}. The
 * collision box comes from the shape's name, the facing rotates it, and that
 * is the whole of the difference between two hundred blocks.
 */
public class ShapedBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<ShapedBlock> CODEC =
            simpleCodec(properties -> new ShapedBlock(properties, "crate"));

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final String shape;
    private final Map<Direction, VoxelShape> boxes = new EnumMap<>(Direction.class);

    public ShapedBlock(Properties properties, String shape) {
        super(properties);
        this.shape = shape;
        VoxelShape north = boxFor(shape);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            boxes.put(facing, turned(north, facing));
        }
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public String shape() {
        return shape;
    }

    /**
     * The box each shape occupies, facing north.
     *
     * <p>Written once, here, rather than in a data file: it has to agree with
     * the model the generator draws, and two files that must agree are one
     * file too many. The generator reads these numbers out of this method.
     */
    public static VoxelShape boxFor(String shape) {
        return switch (shape) {
            case "stool" -> Block.box(3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D);
            case "chair" -> Shapes.or(Block.box(3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D),
                    Block.box(3.0D, 9.0D, 11.0D, 13.0D, 17.0D, 13.0D));
            case "bench" -> Block.box(0.0D, 0.0D, 3.0D, 16.0D, 9.0D, 13.0D);
            case "table" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
            case "shelf" -> Block.box(0.0D, 4.0D, 10.0D, 16.0D, 13.0D, 16.0D);
            case "lattice" -> Block.box(0.0D, 0.0D, 7.0D, 16.0D, 16.0D, 9.0D);
            case "shutter" -> Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
            case "rug" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
            case "beam" -> Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 16.0D);
            case "counter" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);
            case "stove" -> Shapes.block();
            case "sink" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
            case "wardrobe" -> Block.box(1.0D, 0.0D, 3.0D, 15.0D, 16.0D, 15.0D);
            case "drawers" -> Block.box(0.0D, 0.0D, 1.0D, 16.0D, 15.0D, 16.0D);
            case "nightstand" -> Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
            case "desk" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);
            case "planter" -> Block.box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D);
            case "trough" -> Block.box(0.0D, 0.0D, 3.0D, 16.0D, 9.0D, 13.0D);
            case "rack" -> Block.box(0.0D, 2.0D, 12.0D, 16.0D, 16.0D, 16.0D);
            case "screen" -> Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
            case "post" -> Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
            default -> Shapes.block();          // crate, cabinet, table, pillar
        };
    }

    /**
     * The same box, turned to face somewhere else.
     *
     * <p>Three rotations of the horizontal plane, written out rather than
     * derived, because a rotation written cleverly is a rotation that is
     * wrong by a reflection and nobody notices until a shelf hangs off the
     * wrong wall.
     */
    private static VoxelShape turned(VoxelShape north, Direction facing) {
        if (facing == Direction.NORTH) {
            return north;
        }
        VoxelShape[] turning = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            VoxelShape one = switch (facing) {
                case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
                case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
                default -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
            };
            turning[0] = Shapes.or(turning[0], one);
        });
        return turning[0];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return boxes.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos())
                .getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, water);
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
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
