package com.elysium.comforts.block;

import com.elysium.comforts.rules.StationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One class for eight stations.
 *
 * <p>Which job a station does is its row in {@link StationRules#STATIONS},
 * looked up by the name it was registered under — the same argument the
 * bestiary makes about its beasts, and for the same reason: eight classes
 * would be eight places for one bug, and a ninth station would be a ninth
 * file rather than a row.
 *
 * <p>Nothing here decides how long a job takes or what it hands back. Those
 * are the two numbers the whole feature turns on, and a station that chose
 * its own would be a station no harness had ever measured against a player's
 * own hands.
 */
public class StationBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final com.mojang.serialization.MapCodec<StationBlock> CODEC =
            simpleCodec(properties -> new StationBlock(properties,
                    StationRules.STATIONS.get(0).id()));

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Whether there is a villager in it. */
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);

    private final String id;

    public StationBlock(Properties properties, String id) {
        super(properties);
        this.id = id;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OCCUPIED, Boolean.FALSE));
    }

    /** The row this station is. */
    public StationRules.Station station() {
        StationRules.Station found = StationRules.station(id);
        return found == null ? StationRules.STATIONS.get(0) : found;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /**
     * It wakes as often as its row says and no oftener.
     *
     * <p>A scheduled tick rather than a random one, for the same reason a
     * chime uses one: the rules say how long a go takes, and a random tick is
     * whatever the game feels like.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState was, boolean moving) {
        super.onPlace(state, level, pos, was, moving);
        if (station().holds()) {
            level.scheduleTick(pos, this, station().ticks());
        }
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                        BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!station().holds()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof StationBlockEntity kept) {
            kept.aGoAt(level, pos, state, station());
        }
        level.scheduleTick(pos, this, station().ticks());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return station().holds() ? new StationBlockEntity(pos, state) : null;
    }

    /**
     * Put a villager in, or take the one that is in out.
     *
     * <p>Both halves, in one click, because a block you can put a villager
     * into and not get one out of is this module's own nightmare with a lid on
     * it. The villager is moved rather than copied: it is written out of the
     * item's component and into the block's, and there is never a moment when
     * two of it exist.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide || !station().holds()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof StationBlockEntity kept)) {
            return InteractionResult.PASS;
        }
        if (!kept.empty()) {
            ItemStack back = kept.handBack();
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            level.setBlock(pos, state.setValue(OCCUPIED, Boolean.FALSE), 3);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getMainHandItem();
        if (!kept.take(held)) {
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(OCCUPIED, Boolean.TRUE), 3);
        return InteractionResult.CONSUME;
    }

    /** And whatever is in it comes out when the block does. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState to, boolean moving) {
        if (!state.is(to.getBlock())
                && level.getBlockEntity(pos) instanceof StationBlockEntity kept
                && !kept.empty()) {
            net.minecraft.world.Containers.dropContents(level, pos,
                    new net.minecraft.world.SimpleContainer(kept.handBack()));
        }
        super.onRemove(state, level, pos, to, moving);
    }
}
