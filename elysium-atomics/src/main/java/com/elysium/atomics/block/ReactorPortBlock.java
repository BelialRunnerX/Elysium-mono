package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.registry.AtomicBlocks;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.atomics.rules.HallRules;
import com.elysium.industry.rules.ShapeRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The block a player prods to find out what size of reactor they have built.
 *
 * <h2>A reactor whose size is a decision</h2>
 *
 * {@link HallRules} has the arithmetic and the reason there is a second
 * reactor at all: heat goes as the cube of the side and cooling goes as the
 * square, so a bigger hall makes more altogether and less per rod. This is
 * the block that reads the world, works out which size has been built, and
 * says so — including, when nothing has been built, which block is wrong,
 * because that is the one thing a multiblock in a tech mod never tells you.
 */
public class ReactorPortBlock extends BaseEntityBlock {

    public static final MapCodec<ReactorPortBlock> CODEC =
            simpleCodec(ReactorPortBlock::new);

    /** The side of the box that has formed around it, or zero for none. */
    public static final IntegerProperty SIDE =
            IntegerProperty.create("side", 0, HallRules.LARGEST);

    public ReactorPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SIDE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDE);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) {
            port.look(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) {
            if (player.isSecondaryUseActive()) {
                port.cycleStep();       // the throttle: nothing set it before 27 September 2026
            }
            player.displayClientMessage(port.saying(), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return Math.min(15, state.getValue(SIDE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorPortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return type != AtomicBlockEntities.REACTOR_PORT.get() ? null
                : (world, at, here, entity) -> ReactorPortBlockEntity.tick(
                        world, at, here, (ReactorPortBlockEntity) entity);
    }

    /** The size that has formed, the throttle, and what it is running at. */
    public static class ReactorPortBlockEntity extends BlockEntity {

        /** One of {@link AtomicRules#THROTTLE_STEPS}, as the player set it. */
        private int step = 1;

        /** The next throttle step, wrapping from the top back to the first. */
        public void cycleStep() {
            step = step % AtomicRules.THROTTLE_STEPS + 1;
            setChanged();
        }

        /** Where the hall is sitting, in degrees. */
        private float temperature = AtomicRules.AMBIENT;

        /** Coolant channels found in the wall last time it was looked at. */
        private int channels;

        /** What was wrong last time nothing formed, or null. */
        private String wrong = "nothing has been looked at yet";

        public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
            super(AtomicBlockEntities.REACTOR_PORT.get(), pos, state);
        }

        /**
         * Try every size, smallest first, and take the first that forms.
         *
         * <p>Smallest first on purpose: a seven-cube contains a three-cube's
         * worth of space but not a three-cube, so the order only matters for
         * the sentence when nothing forms — and the sentence a player wants
         * is about the smallest thing they might have been building.
         */
        public void look(Level level, BlockPos pos, BlockState state) {
            String best = null;
            for (int side = HallRules.SMALLEST; side <= HallRules.LARGEST; side++) {
                ShapeRules.Shape shape = HallRules.boxOf(side);
                String said = null;
                for (char[][][] want : ShapeRules.orientations(shape)) {
                    int[] here = controllerIn(want);
                    if (here == null) {
                        continue;
                    }
                    char[][][] found = read(level, pos, want, here, shape);
                    if (ShapeRules.sameAs(want, found)) {
                        channels = countChannels(level, pos, want, here);
                        wrong = null;
                        if (state.getValue(SIDE) != side) {
                            level.setBlock(pos, state.setValue(SIDE, side), UPDATE_ALL);
                        }
                        return;
                    }
                    if (said == null) {
                        said = ShapeRules.wrongAt(shape, found);
                    }
                }
                if (best == null) {
                    best = "for a " + side + "-cube, " + said;
                }
            }
            wrong = best;
            channels = 0;
            if (state.getValue(SIDE) != 0) {
                level.setBlock(pos, state.setValue(SIDE, 0), UPDATE_ALL);
            }
        }

        private static int[] controllerIn(char[][][] grid) {
            for (int layer = 0; layer < grid.length; layer++) {
                for (int row = 0; row < grid[0].length; row++) {
                    for (int column = 0; column < grid[0][0].length; column++) {
                        if (grid[layer][row][column] == ShapeRules.CONTROLLER) {
                            return new int[] {layer, row, column};
                        }
                    }
                }
            }
            return null;
        }

        private char[][][] read(Level level, BlockPos pos, char[][][] want,
                                int[] here, ShapeRules.Shape shape) {
            char[][][] found = new char[want.length][want[0].length][want[0][0].length];
            for (int layer = 0; layer < want.length; layer++) {
                for (int row = 0; row < want[0].length; row++) {
                    for (int column = 0; column < want[0][0].length; column++) {
                        BlockPos at = pos.offset(column - here[2],
                                layer - here[0], row - here[1]);
                        found[layer][row][column] = markOf(level.getBlockState(at), shape);
                    }
                }
            }
            return found;
        }

        /**
         * A coolant channel counts as casing and also as cooling.
         *
         * <p>Which is what makes the plumbing a decision about the wall
         * rather than a second structure: every channel a player puts in is
         * a block of casing they did not put in, and the shape does not care
         * which it was.
         */
        private static char markOf(BlockState state, ShapeRules.Shape shape) {
            if (state.is(AtomicBlocks.COOLANT_CHANNEL.get())) {
                return 'C';
            }
            for (java.util.Map.Entry<Character, String> entry : shape.key().entrySet()) {
                Block block = AtomicBlocks.byName(entry.getValue());
                if (block != null && state.is(block)) {
                    return entry.getKey();
                }
            }
            return state.isAir() ? ShapeRules.NOTHING : '?';
        }

        private int countChannels(Level level, BlockPos pos, char[][][] want, int[] here) {
            int found = 0;
            for (int layer = 0; layer < want.length; layer++) {
                for (int row = 0; row < want[0].length; row++) {
                    for (int column = 0; column < want[0][0].length; column++) {
                        if (want[layer][row][column] != 'C') {
                            continue;
                        }
                        BlockPos at = pos.offset(column - here[2],
                                layer - here[0], row - here[1]);
                        if (level.getBlockState(at).is(AtomicBlocks.COOLANT_CHANNEL.get())) {
                            found++;
                        }
                    }
                }
            }
            return found;
        }

        /** What it is making right now, in FE per tick. */
        public float output() {
            int side = getBlockState().getValue(SIDE);
            return side == 0 ? 0.0F
                    : HallRules.outputAt(side, AtomicRules.throttleOf(step));
        }

        /** One line: the size, the throttle and the temperature — or what is wrong. */
        public Component saying() {
            int side = getBlockState().getValue(SIDE);
            if (side == 0) {
                return Component.translatable("elysiumatomics.hall.unformed",
                        wrong == null ? "?" : wrong);
            }
            return Component.translatable("elysiumatomics.hall.running",
                    side + "x" + side + "x" + side,
                    String.valueOf(HallRules.rodsIn(side)),
                    String.valueOf(channels),
                    String.format("%.0f", output()),
                    String.format("%.0f", temperature),
                    String.format("%.0f",
                            HallRules.safeThrottle(side, channels) * 100.0F));
        }

        public static void tick(Level level, BlockPos pos, BlockState state,
                                ReactorPortBlockEntity port) {
            if (level.isClientSide) {
                return;
            }
            if (level.getGameTime() % 20L == 0L) {
                port.look(level, pos, state);
            }
            int side = state.getValue(SIDE);
            if (side == 0) {
                port.temperature = AtomicRules.nextTemperature(port.temperature,
                        0.0F, AtomicRules.BARE_LOSS);
                return;
            }
            float heat = port.output() * AtomicRules.HEAT_PER_FE;
            port.temperature = AtomicRules.nextTemperature(port.temperature, heat,
                    HallRules.coolingOf(side, port.channels));
            port.setChanged();
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            step = tag.getInt("step");
            temperature = tag.getFloat("temperature");
            channels = tag.getInt("channels");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("step", step);
            tag.putFloat("temperature", temperature);
            tag.putInt("channels", channels);
        }
    }
}
