package com.elysium.solarpunk.power;

import com.elysium.solarpunk.machine.SolarpunkMachineEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The wire. It carries power and holds none.
 *
 * <h2>Why a conduit has no block entity</h2>
 *
 * It has nothing to remember. A buffer in the wire would be a third place
 * energy can sit — the generator, the wire, the machine — and every one of
 * those is a place it can be created or destroyed by a rounding error. A base
 * with a thousand conduits would also be a thousand block entities ticking to
 * move numbers that the two ends already agree on. The walk in
 * {@link PowerGrid} reads the blockstate, which costs nothing and saves the
 * whole class of bug where a wire and the machine it feeds disagree about how
 * much is in flight.
 *
 * <h2>Why it answers questions</h2>
 *
 * A priority system is a promise about what happens under pressure, and a
 * promise a player cannot check is not one. Right-clicking a conduit with an
 * empty hand prints the grid it belongs to: how many machines, in which
 * classes, and how many of them are browned out. That is the difference
 * between "my loom stopped" and "my loom stopped because the water reclaimer
 * is ahead of it and there is not enough sun" — the second is a base a player
 * can fix.
 *
 * The readout is chat rather than a screen on purpose. A screen is a container,
 * a menu type, a network packet and a texture, and none of those would tell the
 * player anything this does not.
 */
public class PowerConduitBlock extends Block {

    // --------------------------------------------------------------------
    // Shape
    // --------------------------------------------------------------------
    //
    // A pipe: an eight-cube core with a six-wide arm reaching out of each face
    // that has something on the other side. The same arrangement vanilla uses
    // for a chorus plant, and it is worth saying why the arms are state and not
    // a model trick — a run of conduit that drew six arms always would look
    // like a hedge, and one that drew none would look like a line of loose
    // cubes. Which arms exist is a fact about the neighbours, so it lives in
    // the blockstate where the model can read it.

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> ARMS =
            new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, NORTH);
        ARMS.put(Direction.EAST, EAST);
        ARMS.put(Direction.SOUTH, SOUTH);
        ARMS.put(Direction.WEST, WEST);
        ARMS.put(Direction.UP, UP);
        ARMS.put(Direction.DOWN, DOWN);
    }

    /** The eight-cube middle, present in every state. */
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    private static final Map<Direction, VoxelShape> ARM_SHAPES =
            new EnumMap<>(Direction.class);

    static {
        ARM_SHAPES.put(Direction.DOWN, Block.box(6, 0, 6, 10, 5, 10));
        ARM_SHAPES.put(Direction.UP, Block.box(6, 11, 6, 10, 16, 10));
        ARM_SHAPES.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 5));
        ARM_SHAPES.put(Direction.SOUTH, Block.box(6, 6, 11, 10, 10, 16));
        ARM_SHAPES.put(Direction.WEST, Block.box(0, 6, 6, 5, 10, 10));
        ARM_SHAPES.put(Direction.EAST, Block.box(11, 6, 6, 16, 10, 10));
    }

    /**
     * One shape per combination of arms, built once.
     *
     * Sixty-four entries, and the alternative is unioning up to seven voxel
     * shapes on every collision test against every conduit in a base. That is
     * the sort of cost that is invisible on one pipe and is the whole tick on a
     * factory floor.
     */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < SHAPES.length; mask++) {
            VoxelShape shape = CORE;
            for (Direction face : Direction.values()) {
                if ((mask & (1 << face.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARM_SHAPES.get(face));
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public PowerConduitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (state.getValue(ARMS.get(face))) {
                mask |= 1 << face.ordinal();
            }
        }
        return SHAPES[mask];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction face : Direction.values()) {
            state = state.setValue(ARMS.get(face),
                    connects(context.getLevel(),
                            context.getClickedPos().relative(face), face));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
                                     BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(ARMS.get(direction),
                connects(level, neighbourPos, direction));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction face : Direction.values()) {
            rotated = rotated.setValue(ARMS.get(rotation.rotate(face)),
                    state.getValue(ARMS.get(face)));
        }
        return rotated;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction face : Direction.values()) {
            mirrored = mirrored.setValue(ARMS.get(mirror.mirror(face)),
                    state.getValue(ARMS.get(face)));
        }
        return mirrored;
    }

    /**
     * Whether an arm should reach toward this neighbour.
     *
     * <h2>This is cosmetic, and deliberately so</h2>
     *
     * {@link PowerGrid} does not read these properties. It walks blocks and
     * asks each one for an energy capability, at the moment power is being
     * handed out — so a conduit whose arms are drawn wrong still carries power
     * correctly, and a conduit whose arms are drawn right cannot make power
     * flow somewhere it should not.
     *
     * That separation is worth the small duplication. A block entity can be
     * created a tick after the block it belongs to, so a connection worked out
     * during placement is sometimes computed against a neighbour that does not
     * have its capability yet. If the arms were the authority, that timing
     * would be a machine silently receiving nothing until the player broke and
     * replaced the pipe. Because they are only a picture, the worst case is one
     * frame of a pipe that looks unattached.
     *
     * @param face which way the arm would point, for the capability's side
     */
    private static boolean connects(LevelReader level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PowerConduitBlock) {
            return true;
        }
        // Capabilities live on Level, and updateShape can be handed a
        // WorldGenRegion during chunk generation. Anything that is not a real
        // level answers "no arm", which is right: there are no machines during
        // worldgen, and the neighbour update when the chunk is loaded fixes it.
        if (!(level instanceof Level real)) {
            return false;
        }
        return real.getCapability(Capabilities.EnergyStorage.BLOCK, pos,
                face.getOpposite()) != null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            // The walk is a server-side read of block entities the client does
            // not have. Reporting SUCCESS on the client is what stops the arm
            // swing from being eaten while the server does the work.
            return InteractionResult.SUCCESS;
        }
        report(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /**
     * Print what this conduit is attached to.
     *
     * The walk starts at the conduit rather than at a generator, so the player
     * gets the same answer wherever on the run they click. A generator on the
     * network shows up as a receiver that cannot receive, which is why supply
     * is counted separately from demand below rather than being read off the
     * same list.
     */
    private void report(Level level, BlockPos pos, Player player) {
        PowerGrid.Survey survey = PowerGrid.survey(level, pos);
        List<PowerGrid.Receiver> receivers = survey.receivers();
        int conduits = survey.conduits() + 1;                    // this one
        // The same state the generators will use this tick, so what the
        // readout says is held is what is actually held. Recomputing it from a
        // second measurement would let the two disagree at dusk, which is
        // exactly when somebody would be reading it.
        GridState state = new GridState(level.isDay() && level.canSeeSky(pos),
                level.isRaining(), survey.charge());
        Set<PowerClass> held = PowerRule.held(survey.rules(), state);

        Map<PowerClass, int[]> tally = new EnumMap<>(PowerClass.class);
        for (PowerGrid.Receiver receiver : receivers) {
            int[] row = tally.computeIfAbsent(receiver.kind(), key -> new int[2]);
            row[0]++;
            if (level.getBlockEntity(receiver.pos())
                    instanceof SolarpunkMachineEntity machine
                    && machine.isBrownedOut()) {
                row[1]++;
            }
        }

        player.displayClientMessage(Component.translatable(
                "elysiumsolarpunk.power.readout.header", conduits, receivers.size())
                .withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                        "elysiumsolarpunk.power.readout.charge",
                        Math.round(survey.charge() * 100.0F), survey.rules().size())
                .withStyle(ChatFormatting.GRAY), false);

        if (receivers.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "elysiumsolarpunk.power.readout.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        // Walked in declaration order, which is priority order, so the list
        // reads top to bottom as "this is what gets fed first".
        for (PowerClass kind : PowerClass.values()) {
            int[] row = tally.get(kind);
            if (row == null) {
                continue;
            }
            // A held class is reported as held rather than as browned out.
            // They look identical from the machine's side — nothing is being
            // paid — and the difference is the whole question a player is
            // asking: is my base short of power, or did I tell it to do this?
            Component line = held.contains(kind)
                    ? Component.translatable(
                            "elysiumsolarpunk.power.readout.held",
                            Component.translatable(kind.translationKey()), row[0])
                    : Component.translatable(
                            "elysiumsolarpunk.power.readout.class",
                            Component.translatable(kind.translationKey()),
                            row[0], row[1]);
            player.displayClientMessage(
                    line.copy().withStyle(held.contains(kind)
                            ? ChatFormatting.DARK_AQUA
                            : row[1] > 0 ? ChatFormatting.GOLD
                            : ChatFormatting.GRAY), false);
        }
    }
}
