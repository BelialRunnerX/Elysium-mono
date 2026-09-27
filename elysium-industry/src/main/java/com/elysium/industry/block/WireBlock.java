package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.lib.run.RunDyed;
import com.elysium.lib.run.RunRules;
import com.elysium.lib.run.RunBlock;
import com.elysium.lib.run.RunLink;
import com.elysium.lib.run.RunSides;
import com.elysium.lib.run.Runs;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A run of wire. The connective tissue of the second age.
 *
 * <p>A wire is a run in the pack's shared sense ({@link Runs}): each face is
 * nothing, another wire of this metal it may join, or a device - a grid node
 * that is not a wire and takes a line on that face, which gets the plate.
 * Two metals never join: they are different voltages and different blocks.
 * A wire has no state of its own beyond its links; the grid it belongs to is
 * read off the world by {@link GridLine}, the way it always was, and the
 * links only draw what that read will find.
 */
public class WireBlock extends GridBlock implements RunBlock {

    public static final MapCodec<WireBlock> CODEC =
            simpleCodec(properties -> new WireBlock(properties, IndustryRules.Voltage.LOW));

    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, box(5, 5, 0, 11, 11, 5), Direction.SOUTH, box(5, 5, 11, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 5, 11, 11), Direction.EAST, box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, box(5, 0, 5, 11, 5, 11), Direction.UP, box(5, 11, 5, 11, 16, 11));

    private final IndustryRules.Voltage voltage;

    public WireBlock(Properties properties, IndustryRules.Voltage voltage) {
        super(Runs.properties(properties));
        this.voltage = voltage;
        registerDefaultState(Runs.untouched(stateDefinition.any()));
    }

    public IndustryRules.Voltage voltage() {
        return voltage;
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Runs.define(builder);
    }

    /** A device is a grid node that is not a wire and takes a line on the face touched. */
    private boolean device(LevelAccessor level, BlockPos at, Direction face) {
        if (!(level instanceof Level real)) {
            return false;
        }
        GridNode there = GridLine.nodeAt(real, at);
        return there != null && !(there instanceof WireBlockEntity) && there.connects(face);
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
        return new WireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.WIRE.get());
    }

    /** The node a wire is on the grid: a line of this block's voltage, and its dye. */
    public static class WireBlockEntity extends GridBlockEntity implements RunDyed {

        private String runDye = RunRules.UNDYED;

        public WireBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.WIRE.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return getBlockState().getBlock() instanceof WireBlock wire
                    ? wire.voltage() : IndustryRules.Voltage.LOW;
        }

        @Override
        public boolean carries() {
            return true;
        }

        /** A wire joins through the faces its links say it does: a dyed wire stops where its colour stops. */
        @Override
        public boolean connects(Direction face) {
            return getBlockState().getBlock() instanceof WireBlock
                    && getBlockState().getValue(RunSides.BY_SIDE.get(face)) != RunLink.NONE;
        }

        @Override
        public String runDye() {
            return runDye;
        }

        @Override
        public void setRunDye(String dye) {
            runDye = RunRules.isDye(dye) ? dye : RunRules.UNDYED;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            Runs.save(tag, runDye);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            String before = runDye;
            runDye = Runs.load(tag);
            if (level != null && !level.isClientSide && !before.equals(runDye)) {
                Runs.relinkAround(level, worldPosition);
            }
        }
    }
}
