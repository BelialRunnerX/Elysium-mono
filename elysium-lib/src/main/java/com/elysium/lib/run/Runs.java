package com.elysium.lib.run;

import com.elysium.lib.item.ElysiumComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What every run block does the same way, so that six modules' pipes, cables,
 * wires, conduits, buses and channels are one thing to a player.
 *
 * <p>A run block keeps the six {@link RunSides} properties, computes them
 * with {@link #relink}, draws itself with {@link #shape}, takes a dye with
 * {@link #dye}, hands its dye on when picked with {@link #clone}, and is
 * tinted by {@link #tint}. The block itself still says what a device is -
 * an energy handler for a cable, a fluid handler for a pipe, a grid node for
 * a wire - through a {@link DeviceTest}; the rule about who joins whom is
 * {@link RunRules#mayJoin} and lives in one place.
 */
public final class Runs {

    private Runs() {
    }

    /** Whether the block at {@code device}, touched on {@code face}, is something this run feeds. */
    @FunctionalInterface
    public interface DeviceTest {
        boolean test(LevelAccessor level, BlockPos device, Direction face);
    }

    private record Declared(Supplier<? extends Block> block, int own) {
    }

    private static final List<Declared> DECLARED = new ArrayList<>();
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /**
     * A module says which of its blocks are runs and what colour each one's
     * plates are when undyed. The client's colour handlers read this list.
     */
    public static void declare(Supplier<? extends Block> block, int ownRgb) {
        DECLARED.add(new Declared(block, ownRgb));
    }

    /** Every run block declared so far. */
    public static List<Block> declared() {
        List<Block> out = new ArrayList<>();
        for (Declared one : DECLARED) {
            out.add(one.block().get());
        }
        return out;
    }

    /** The colour a block's plates are when it is undyed; white for a block nobody declared. */
    public static int ownColour(Block block) {
        for (Declared one : DECLARED) {
            if (one.block().get() == block) {
                return one.own();
            }
        }
        return 0xFFFFFF;
    }

    /**
     * What a run block's properties must say. A run's collision box is a
     * thin core with arms, and the game calls a block that small not solid
     * and lets water flow into its place: a bucket poured beside a cable
     * washed the line away (live test, 27 September 2026). Solid, then,
     * whatever the box; and never occluding, because the arms are not a
     * cube and the faces behind them are drawn.
     */
    public static net.minecraft.world.level.block.state.BlockBehaviour.Properties properties(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return properties.noOcclusion().forceSolidOn();
    }

    /** Adds the six link properties to a block's state definition. */
    public static void define(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction side : Direction.values()) {
            builder.add(RunSides.BY_SIDE.get(side));
        }
    }

    /** A state with nothing touched on any side. */
    public static BlockState untouched(BlockState state) {
        for (Direction side : Direction.values()) {
            state = state.setValue(RunSides.BY_SIDE.get(side), RunLink.NONE);
        }
        return state;
    }

    /** What one face touches, by the rule. */
    public static RunLink link(LevelAccessor level, BlockPos pos, Direction side, Block self,
                               DeviceTest device) {
        BlockPos next = pos.relative(side);
        BlockState there = level.getBlockState(next);
        if (there.is(self)) {
            return RunRules.mayJoin(dyeAt(level, pos), dyeAt(level, next)) ? RunLink.RUN : RunLink.NONE;
        }
        return device.test(level, next, side.getOpposite()) ? RunLink.DEVICE : RunLink.NONE;
    }

    /** The state with all six faces read off the world. */
    public static BlockState relink(LevelAccessor level, BlockPos pos, BlockState state, DeviceTest device) {
        for (Direction side : Direction.values()) {
            state = state.setValue(RunSides.BY_SIDE.get(side),
                    link(level, pos, side, state.getBlock(), device));
        }
        return state;
    }

    /**
     * Re-reads this run and tells the runs around it to re-read too. Called
     * after a dye changes, because a dye lives in the block entity and a
     * neighbour's link to this block depends on it without any state of this
     * block having changed.
     */
    public static void relinkAround(Level level, BlockPos pos, DeviceTest device) {
        BlockState state = level.getBlockState(pos);
        BlockState linked = relink(level, pos, state, device);
        level.setBlock(pos, linked, Block.UPDATE_ALL);
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState there = level.getBlockState(next);
            if (there.is(state.getBlock())) {
                level.setBlock(next, there.updateShape(side.getOpposite(), linked, level, next, pos),
                        Block.UPDATE_ALL);
            }
        }
        level.sendBlockUpdated(pos, state, linked, Block.UPDATE_ALL);
    }

    /**
     * Re-reads a run placed by something other than a player - a command, a
     * structure, a machine - whose state came straight from the registry with
     * nothing linked. Called from the block's onPlace.
     */
    public static void placedBlind(Level level, BlockPos pos, BlockState state, DeviceTest device) {
        if (level.isClientSide) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (state.getValue(RunSides.BY_SIDE.get(side)) != RunLink.NONE) {
                return;                       // a player's placement already read the world
            }
        }
        relinkAround(level, pos, device);
    }

    /** Re-reads the run at a position, asking its block what a device is. */
    public static void relinkAround(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof RunBlock run) {
            relinkAround(level, pos, run.runDevice());
        }
    }

    /** The dye of the run at a position, or undyed when there is no run or it wears none. */
    public static String dyeAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RunDyed dyed ? dyed.runDye() : RunRules.UNDYED;
    }

    /** The dye an item carries, or undyed. */
    public static String dyeOf(ItemStack stack) {
        String dye = stack.get(ElysiumComponents.RUN_DYE.get());
        return RunRules.isDye(dye) ? dye : RunRules.UNDYED;
    }

    /** A copy of the stack wearing that dye, or wearing none. */
    public static ItemStack withDye(ItemStack stack, String dye) {
        ItemStack out = stack.copy();
        if (RunRules.isDye(dye)) {
            out.set(ElysiumComponents.RUN_DYE.get(), dye);
        } else {
            out.remove(ElysiumComponents.RUN_DYE.get());
        }
        return out;
    }

    /** What a player picks up from a placed run: the block, wearing the run's dye. */
    public static ItemStack clone(ItemStack plain, BlockGetter level, BlockPos pos) {
        return withDye(plain, dyeAt(level, pos));
    }

    /**
     * A freshly placed run takes the dye its item wore. Called from
     * {@code setPlacedBy}, once the block entity exists.
     */
    public static void placed(Level level, BlockPos pos, ItemStack stack, DeviceTest device) {
        String dye = dyeOf(stack);
        if (RunRules.isDye(dye) && level.getBlockEntity(pos) instanceof RunDyed dyed) {
            dyed.setRunDye(dye);
            relinkAround(level, pos, device);
        }
    }

    /**
     * A dye used on a placed run dyes it. Anything else is not this method's
     * business, so the block's own use handling runs.
     */
    public static ItemInteractionResult dye(ItemStack held, Level level, BlockPos pos, Player player,
                                            InteractionHand hand, DeviceTest device) {
        if (!(held.getItem() instanceof DyeItem dyeItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        String dye = dyeItem.getDyeColor().getName();
        if (!(level.getBlockEntity(pos) instanceof RunDyed dyed) || dye.equals(dyed.runDye())) {
            return ItemInteractionResult.CONSUME;
        }
        dyed.setRunDye(dye);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        relinkAround(level, pos, device);
        return ItemInteractionResult.SUCCESS;
    }

    /** The core plus an arm towards every touched face. */
    public static VoxelShape shape(BlockState state, VoxelShape core, Map<Direction, VoxelShape> arms) {
        return SHAPES.computeIfAbsent(state, s -> {
            VoxelShape shape = core;
            for (Direction side : Direction.values()) {
                if (s.getValue(RunSides.BY_SIDE.get(side)).reaches()) {
                    shape = Shapes.or(shape, arms.get(side));
                }
            }
            return shape;
        });
    }

    /** The block colour handler: body white or dyed, plate the run's own colour or dyed. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                           int index) {
        String dye = level != null && pos != null ? dyeAt(level, pos) : RunRules.UNDYED;
        return index == RunRules.PLATE_TINT
                ? RunRules.plateTint(dye, ownColour(state.getBlock()))
                : RunRules.bodyTint(dye);
    }

    /** The item colour handler: the same, read off the stack's dye. */
    public static int itemTint(ItemStack stack, int index) {
        String dye = dyeOf(stack);
        Block block = stack.getItem() instanceof BlockItem item ? item.getBlock() : null;
        return index == RunRules.PLATE_TINT
                ? RunRules.plateTint(dye, block == null ? 0xFFFFFF : ownColour(block))
                : RunRules.bodyTint(dye);
    }

    /** Saves a dye into a block entity's tag. */
    public static void save(net.minecraft.nbt.CompoundTag tag, String dye) {
        if (RunRules.isDye(dye)) {
            tag.putString("run_dye", dye);
        }
    }

    /** Reads a dye back, undyed when the tag has none. */
    public static String load(net.minecraft.nbt.CompoundTag tag) {
        String dye = tag.getString("run_dye");
        return RunRules.isDye(dye) ? dye : RunRules.UNDYED;
    }
}
