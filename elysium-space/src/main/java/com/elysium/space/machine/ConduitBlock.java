package com.elysium.space.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A cable (energy) or pipe (fluids): a core with an arm towards every
 * neighbour that can take or give what it carries, whichever mod made it.
 * Stellaris's cables and pipes, on NeoForge capabilities.
 */
public class ConduitBlock extends BaseEntityBlock {

    public enum Carries { ENERGY, FLUID }

    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11), Direction.UP, Block.box(5, 11, 5, 11, 16, 11));

    private final Carries carries;
    private final Supplier<? extends BlockEntityType<? extends ConduitBlockEntity>> type;
    private final BiFunction<BlockPos, BlockState, ? extends ConduitBlockEntity> factory;

    public ConduitBlock(Properties properties, Carries carries,
                        Supplier<? extends BlockEntityType<? extends ConduitBlockEntity>> type,
                        BiFunction<BlockPos, BlockState, ? extends ConduitBlockEntity> factory) {
        super(properties);
        this.carries = carries;
        this.type = type;
        this.factory = factory;
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new ConduitBlock(p, carries, type, factory));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
    }

    private BlockCapability<?, Direction> capability() {
        return carries == Carries.ENERGY ? Capabilities.EnergyStorage.BLOCK : Capabilities.FluidHandler.BLOCK;
    }

    private boolean connects(LevelAccessor level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        if (level.getBlockState(next).getBlock() == this) {
            return true;
        }
        return level instanceof Level real && real.getCapability(capability(), next, side.getOpposite()) != null;
    }

    private BlockState withArms(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : ARMS.entrySet()) {
            state = state.setValue(e.getValue(), connects(level, pos, e.getKey()));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withArms(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(ARMS.get(side), connects(level, pos, side));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> e : ARMS.entrySet()) {
            if (state.getValue(e.getValue())) {
                shape = Shapes.or(shape, ARM_SHAPES.get(e.getKey()));
            }
        }
        return shape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != type.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((ConduitBlockEntity) be).serverTick();
    }
}
