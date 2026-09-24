package com.elysium.solarpunk.block.pending;

import com.elysium.solarpunk.block.PendingMachine;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The shared body of a machine that is still decoration.
 *
 * <h2>What it does do</h2>
 *
 * It faces the player who placed it, and it keeps facing the right way when
 * the chunk it is in is rotated or mirrored by a structure block. That is the
 * whole behaviour, and it is here rather than in each subclass because it is
 * the same four lines eight times.
 *
 * <p>Facing is not a placeholder for the machinery. It is what makes a row of
 * these read as a factory floor instead of as eight cubes: the model has a
 * distinct working face, so a bioreactor that always pointed north would be
 * facing the wall half the time it was placed. A player can build with these
 * today, which is the point of shipping them inert rather than not shipping
 * them.
 *
 * <h2>Why it extends Block and borrows FACING</h2>
 *
 * {@link HorizontalDirectionalBlock} would be the obvious parent and declares
 * exactly this property — but it leaves {@code codec()} abstract, and the
 * codec helpers available for it all assume a constructor taking nothing but
 * {@code Properties}. These take a second argument, so satisfying it would
 * mean hand-writing a {@code MapCodec} per machine for a block that is
 * <em>about to be replaced</em> by a real one.
 *
 * <p>{@link Block} already implements {@code codec()}, and {@code FACING} is a
 * public static property that any block may use. Borrowing it produces the
 * same blockstate JSON, the same four variants and the same behaviour, at the
 * cost of one import that names where the property came from.
 *
 * <h2>The unfinished part</h2>
 *
 * See {@link PendingMachine}. Every subclass of this class lives in this
 * package, and that is deliberate: the directory listing is the to-do list.
 */
public abstract class PendingMachineBlock extends Block implements PendingMachine {

    /** Borrowed from {@link HorizontalDirectionalBlock}; see the class note. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final String owed;

    protected PendingMachineBlock(Properties properties, String owed) {
        super(properties);
        this.owed = owed;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public String owes() {
        return this.owed;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The opposite of where the player is looking, so the working face
        // ends up pointing back at them. This is what every furnace in the
        // game does, and doing anything else here reads as a bug.
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
}
