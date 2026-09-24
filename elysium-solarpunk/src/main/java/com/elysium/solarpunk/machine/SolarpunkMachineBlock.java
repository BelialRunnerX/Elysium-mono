package com.elysium.solarpunk.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A machine that does its job.
 *
 * <h2>What this is, next to {@code PendingMachineBlock}</h2>
 *
 * The same block, plus a reason to exist. Every solarpunk machine started in
 * {@code block/pending/} as a facing, a model and a promise written down in
 * {@code MACHINES.md}; a machine graduates by growing a block entity and moving
 * here. Nothing else about it changes — same recipe, same texture, same place in
 * the creative tab — so a player who built a factory floor out of props finds it
 * switched on rather than replaced.
 *
 * <h2>Why the facing behaviour is repeated rather than shared</h2>
 *
 * It is four short overrides and they are identical to the pending block's. The
 * alternative is a common superclass that both extend, which would mean the
 * class hierarchy encoded "finished" and "unfinished" as siblings — and the
 * whole point of the pending package is that being unfinished is a temporary
 * state recorded in a manifest, not a kind of block. When the last machine
 * graduates, {@code block/pending/} is deleted and nothing here has to change.
 */
public abstract class SolarpunkMachineBlock extends Block implements EntityBlock {

    /** Borrowed from {@link HorizontalDirectionalBlock}, as the pending block does. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final Supplier<? extends BlockEntityType<?>> type;

    protected SolarpunkMachineBlock(Properties properties,
                                    Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
        this.registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Spill whatever the machine was holding when it is broken.
     *
     * A machine that swallows its output on being mined is a machine a player
     * loses things to, and they will not find out until they go looking for the
     * stack that is not there. Only machines that hold items override
     * {@link SolarpunkMachineEntity#drops}.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof SolarpunkMachineEntity machine) {
                Containers.dropContents(level, pos, machine.drops());
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    /**
     * The server ticks, the client does not.
     *
     * Every machine here is a state change with no animation of its own, so a
     * client ticker would run the same arithmetic to reach a number nothing
     * draws. Returning one anyway is the usual way a mod ends up doing its work
     * twice and disagreeing with itself on a multiplayer server.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> given) {
        if (level.isClientSide()) {
            return null;
        }
        return given != type.get() ? null : (ticking, pos, ticked, entity) -> {
            if (entity instanceof SolarpunkMachineEntity machine) {
                machine.serverTick();
            }
        };
    }
}
