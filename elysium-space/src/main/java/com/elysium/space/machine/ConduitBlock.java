package com.elysium.space.machine;

import com.elysium.lib.run.RunBlock;
import com.elysium.lib.run.RunSides;
import com.elysium.lib.run.Runs;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * A cable or a pipe: a run, in the pack's shared sense.
 *
 * <p>Each face is {@code none}, {@code run} or {@code device}. A run is
 * another block of this kind whose dye this one may join; a device is
 * anything that offers the capability this run carries - energy for a
 * cable, fluid for a pipe - and gets the plate. The rule about dyes, the
 * dyeing itself, the pick and the shape all come from {@link Runs}, so a
 * cable behaves like a wire behaves like a bus.
 */
public class ConduitBlock extends BaseEntityBlock implements RunBlock {

    public enum Carries { ENERGY, FLUID }

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
        super(Runs.properties(properties));
        this.carries = carries;
        this.type = type;
        this.factory = factory;
        registerDefaultState(Runs.untouched(stateDefinition.any()));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new ConduitBlock(p, carries, type, factory));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Runs.define(builder);
    }

    private BlockCapability<?, Direction> capability() {
        return carries == Carries.ENERGY ? Capabilities.EnergyStorage.BLOCK : Capabilities.FluidHandler.BLOCK;
    }

    /** A device is whatever offers what this run carries, on the face it is touched. */
    private boolean device(LevelAccessor level, BlockPos at, Direction face) {
        return level instanceof Level real && real.getCapability(capability(), at, face) != null;
    }

    @Override
    public Runs.DeviceTest runDevice() {
        return this::device;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Runs.placedBlind(level, pos, state, this::device);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Runs.relink(context.getLevel(), context.getClickedPos(), defaultBlockState(), this::device);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        Runs.placed(level, pos, stack, this::device);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(RunSides.BY_SIDE.get(side), Runs.link(level, pos, side, this, this::device));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return Runs.dye(held, level, pos, player, hand, this::device);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return Runs.clone(super.getCloneItemStack(state, target, level, pos, player), level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Runs.shape(state, CORE, ARM_SHAPES);
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
