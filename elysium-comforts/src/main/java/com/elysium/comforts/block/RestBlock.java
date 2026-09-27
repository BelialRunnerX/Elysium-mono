package com.elysium.comforts.block;

import com.elysium.comforts.rules.RestRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Somewhere to sleep that does not move your spawn.
 *
 * <h2>One class for thirty-two blocks</h2>
 *
 * Sixteen sleeping bags and sixteen hammocks differ in a colour and in which
 * half of the day they work in, and the half is decided by
 * {@link RestRules#usableAt}. Thirty-two classes would be thirty-two places
 * for one bug, and a seventeenth colour would be a seventeenth file rather
 * than a row.
 *
 * <h2>Every refusal says which refusal it is</h2>
 *
 * There are six ways this can decline, and each of them has its own sentence.
 * A block that answers "you cannot sleep here" to all six is a block a player
 * has to guess at — and the guess they usually make is that the pack is
 * broken.
 */
public class RestBlock extends HorizontalDirectionalBlock {

    public static final com.mojang.serialization.MapCodec<RestBlock> CODEC =
            simpleCodec(properties -> new RestBlock(properties, RestRules.BAG));

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Whether somebody is in it. */
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

    /** How tall one lies, in sixteenths of a block. */
    public static final int LIES = 4;

    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, LIES, 16.0D);

    private final String kind;

    public RestBlock(Properties properties, String kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OCCUPIED, Boolean.FALSE));
    }

    /** Which of the two this is. */
    public String kind() {
        return kind;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
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
     * Lie down in it, or be told exactly why not.
     *
     * <p>The order of the checks is the order a player would ask them in, and
     * every one of them names {@link RestRules}' own reason rather than a
     * sentence written here — so the six reasons the rules say exist are the
     * six a player can actually be given.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        String no = refusal(state, level, pos, player);
        if (!no.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "rest.elysiumcomforts." + no), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(OCCUPIED, Boolean.TRUE), 3);
        player.startSleepInBed(pos).ifLeft(problem ->
                player.displayClientMessage(Component.translatable(
                        "rest.elysiumcomforts." + RestRules.UNSAFE), true));
        return InteractionResult.CONSUME;
    }

    /**
     * Which of the rules' six reasons this is, or the empty string.
     *
     * <p>Pulled out of the click so that nothing here decides what a reason
     * <em>is</em>: every string it can return is one {@link RestRules#REFUSALS}
     * names, and the validator checks that all six are reachable from here.
     */
    public String refusal(BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(OCCUPIED)) {
            return RestRules.OCCUPIED;
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D) > RestRules.REACHES * RestRules.REACHES) {
            return RestRules.TOO_FAR;
        }
        if (!RestRules.usableAt((int) (level.getDayTime() % RestRules.DAY))
                .equals(kind)) {
            return RestRules.NOT_NOW;
        }
        if (RestRules.HAMMOCK.equals(kind) && !hangsFrom(level, pos)) {
            return RestRules.NO_ROPE;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return RestRules.NO_ROOM;
        }
        return "";
    }

    /** Whether there is something above a hammock to string it from. */
    public static boolean hangsFrom(Level level, BlockPos pos) {
        return level.getBlockState(pos.above(2)).getBlock()
                instanceof RopeBlock;
    }
}
