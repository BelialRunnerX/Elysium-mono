package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.registry.AtomicBlocks;
import com.elysium.lib.run.RunDyeBlockEntity;
import com.elysium.lib.run.RunBlock;
import com.elysium.lib.run.RunSides;
import com.elysium.lib.run.Runs;
import com.mojang.serialization.MapCodec;
import java.util.Map;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * A coolant channel: a run in the pack's shared sense ({@link Runs}).
 *
 * <p>Each face is nothing, another channel it may join by its dye, or a
 * device - a reactor core, a cell or a block of the hall, the things a
 * channel exists to sit against - which gets the plate. The reactor still
 * counts channels by walking its hall ({@link ReactorPortBlock}); the links
 * only draw what that count will find.
 */
public class CoolantChannelBlock extends Block implements EntityBlock, RunBlock {

    public static final MapCodec<CoolantChannelBlock> CODEC = simpleCodec(CoolantChannelBlock::new);

    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, box(5, 5, 0, 11, 11, 5), Direction.SOUTH, box(5, 5, 11, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 5, 11, 11), Direction.EAST, box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, box(5, 0, 5, 11, 5, 11), Direction.UP, box(5, 11, 5, 11, 16, 11));

    public CoolantChannelBlock(Properties properties) {
        super(Runs.properties(properties));
        registerDefaultState(Runs.untouched(stateDefinition.any()));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Runs.define(builder);
    }

    /** A device is what a channel cools: a core, a cell, or the hall around them. */
    private boolean device(LevelAccessor level, BlockPos at, Direction face) {
        BlockState there = level.getBlockState(at);
        if (there.is(AtomicBlocks.REACTOR_CORE.get()) || there.is(AtomicBlocks.BASIC_CELL.get())
                || there.is(AtomicBlocks.ADVANCED_CELL.get()) || there.is(AtomicBlocks.QUANTUM_CELL.get())) {
            return true;
        }
        for (DeferredBlock<Block> hall : AtomicBlocks.HALL.values()) {
            if (there.is(hall.get())) {
                return true;
            }
        }
        return false;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Runs.shape(state, CORE, ARMS);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RunDyeBlockEntity(AtomicBlockEntities.COOLANT_CHANNEL.get(), pos, state);
    }
}
