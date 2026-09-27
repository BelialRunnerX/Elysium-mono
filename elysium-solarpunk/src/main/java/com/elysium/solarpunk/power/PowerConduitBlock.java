package com.elysium.solarpunk.power;

import com.elysium.lib.run.RunDyeBlockEntity;
import com.elysium.lib.run.RunBlock;
import com.elysium.lib.run.RunSides;
import com.elysium.lib.run.Runs;
import com.elysium.solarpunk.machine.SolarpunkMachineEntity;
import com.elysium.solarpunk.machine.SolarpunkMachines;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The wire that joins solarpunk's generators to its machines.
 *
 * <p>A conduit is a run in the pack's shared sense ({@link Runs}): each face
 * is nothing, another conduit it may join by its dye, or a device - anything
 * offering energy on the face touched, which gets the plate. The grid it
 * belongs to is still walked block by block by {@link PowerGrid}; the links
 * draw what the walk will find, and a right-click with an empty hand reads
 * the grid back, as it always did.
 */
public class PowerConduitBlock extends Block implements EntityBlock, RunBlock {

    public static final MapCodec<PowerConduitBlock> CODEC = simpleCodec(PowerConduitBlock::new);

    /** The eight-cube middle, present in every state. */
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    private static final Map<Direction, VoxelShape> ARM_SHAPES = new EnumMap<>(Direction.class);

    static {
        ARM_SHAPES.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        ARM_SHAPES.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
        ARM_SHAPES.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        ARM_SHAPES.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        ARM_SHAPES.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        ARM_SHAPES.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
    }

    public PowerConduitBlock(Properties properties) {
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

    /**
     * A device is anything that takes or gives energy on the face touched.
     * Capabilities live on Level, and updateShape can be handed a lesser
     * accessor; a lesser accessor has no devices in it.
     */
    private boolean device(LevelAccessor level, BlockPos at, Direction face) {
        return level instanceof Level real
                && real.getCapability(Capabilities.EnergyStorage.BLOCK, at, face) != null;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Runs.shape(state, CORE, ARM_SHAPES);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return Runs.clone(super.getCloneItemStack(state, target, level, pos, player), level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RunDyeBlockEntity(SolarpunkMachines.POWER_CONDUIT_ENTITY.get(), pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return Runs.dye(held, level, pos, player, hand, this::device);
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
            // They look identical from the machine's side - nothing is being
            // paid - and the difference is the whole question a player is
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
