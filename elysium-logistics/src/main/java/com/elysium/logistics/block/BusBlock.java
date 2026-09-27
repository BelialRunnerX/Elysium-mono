package com.elysium.logistics.block;

import com.elysium.logistics.registry.LogisticBlockEntities;
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

/**
 * A run of bus. The connective tissue of a network.
 *
 * <p>A bus is a run in the pack's shared sense ({@link Runs}): each face is
 * nothing, another bus it may join by its dye, or a device - any block
 * entity of this module that is not a bus, which is what the network's walk
 * will find there - and a device gets the plate. The walk itself
 * ({@link LogisticNetwork}) still reads the world block by block; the links
 * only draw what it will find, and a dyed bus's refusal to join another dye
 * is drawn the same way the walk sees it.
 */
public class BusBlock extends Block implements EntityBlock, RunBlock {

    public static final MapCodec<BusBlock> CODEC = simpleCodec(BusBlock::new);

    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, box(5, 5, 0, 11, 11, 5), Direction.SOUTH, box(5, 5, 11, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 5, 11, 11), Direction.EAST, box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, box(5, 0, 5, 11, 5, 11), Direction.UP, box(5, 11, 5, 11, 16, 11));

    public BusBlock(Properties properties) {
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

    /** A device is any block entity of this module that is not itself a bus. */
    private boolean device(LevelAccessor level, BlockPos at, Direction face) {
        BlockEntity entity = level.getBlockEntity(at);
        return entity != null && !(entity instanceof RunDyeBlockEntity)
                && "elysiumlogistics".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getKey(entity.getType()).getNamespace());
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
        return new RunDyeBlockEntity(LogisticBlockEntities.BUS.get(), pos, state);
    }
}
