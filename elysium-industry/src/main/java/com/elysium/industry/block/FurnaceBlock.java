package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.registry.IndustryBlocks;
import com.elysium.industry.rules.FurnaceRules;
import com.elysium.industry.rules.ShapeRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The block a player prods to find out why their multiblock has not formed.
 *
 * <h2>The one thing this exists to do</h2>
 *
 * Every other failure in this pack tells you something. A multiblock that has
 * not formed, in every mod that has them, tells you nothing at all: you place
 * the last block, nothing happens, and the only way forward is to count
 * bricks. So this block's whole job is to answer the question — it finds the
 * orientation that is closest to right and names the first block that is
 * wrong, with its position relative to itself.
 *
 * <p>{@link ShapeRules} does the arithmetic, and it is swept in the harness
 * against every one-block mutation of every orientation of every shape, which
 * is the only way to be sure the sentence it gives back is the right one.
 */
public class FurnaceBlock extends BaseEntityBlock {

    public static final MapCodec<FurnaceBlock> CODEC =
            simpleCodec(properties -> new FurnaceBlock(properties,
                    FurnaceRules.SHAPES.get(0).id()));

    /** Whether the bricks around it are the machine. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final String id;

    public FurnaceBlock(Properties properties, String id) {
        super(properties);
        this.id = id;
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    /** Which shape this controller is the controller of. */
    public ShapeRules.Shape shape() {
        ShapeRules.Shape found = FurnaceRules.shape(id);
        return found != null ? found : FurnaceRules.SHAPES.get(0);
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
        builder.add(FORMED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            furnace.look(level, pos, state);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && furnace.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            player.displayClientMessage(furnace.saying(level, pos, state), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            Containers.dropContents(level, pos, new SimpleContainer(
                    furnace.items().getStackInSlot(0), furnace.items().getStackInSlot(1)));
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return type != IndustryBlockEntities.FURNACE.get() ? null
                : (world, pos, here, entity) -> FurnaceBlockEntity.tick(
                        world, pos, here, (FurnaceBlockEntity) entity);
    }

    /** Two slots, a shape to stand in, and a sentence when it is not standing. */
    public static class FurnaceBlockEntity extends BlockEntity {

        private final ItemStackHandler items = new ItemStackHandler(2);
        private int done;

        /** What the last look at the world found wrong, or null when formed. */
        private String wrong = "nothing has been looked at yet";

        public FurnaceBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.FURNACE.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        /** Which shape the controller here is for. */
        private ShapeRules.Shape shape() {
            return getBlockState().getBlock() instanceof FurnaceBlock block
                    ? block.shape() : FurnaceRules.SHAPES.get(0);
        }

        /**
         * Read the world around this block and decide whether it is the shape.
         *
         * <p>Tried in every orientation, keeping the one that got furthest,
         * because the sentence a player wants is about the way they were
         * trying to build it rather than about whichever rotation the loop
         * happened to reach first.
         */
        public void look(Level level, BlockPos pos, BlockState state) {
            ShapeRules.Shape shape = shape();
            String best = null;
            for (char[][][] want : ShapeRules.orientations(shape)) {
                int[] here = whereTheControllerIs(want);
                if (here == null) {
                    continue;
                }
                char[][][] found = read(level, pos, want, here, shape);
                if (ShapeRules.sameAs(want, found)) {
                    wrong = null;
                    if (!state.getValue(FORMED)) {
                        level.setBlock(pos, state.setValue(FORMED, true), UPDATE_ALL);
                    }
                    return;
                }
                String said = firstDifference(want, found, shape, here);
                if (best == null) {
                    best = said;
                }
            }
            wrong = best == null ? "this is not a shape anything can stand in"
                    : best;
            if (state.getValue(FORMED)) {
                level.setBlock(pos, state.setValue(FORMED, false), UPDATE_ALL);
            }
        }

        private static int[] whereTheControllerIs(char[][][] grid) {
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

        /** The blocks around this one, written in the shape's own alphabet. */
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

        /** Which of a shape's marks a block in the world is, or '?'. */
        private static char markOf(BlockState state, ShapeRules.Shape shape) {
            for (java.util.Map.Entry<Character, String> entry : shape.key().entrySet()) {
                Block block = IndustryBlocks.byName(entry.getValue());
                if (block != null && state.is(block)) {
                    return entry.getKey();
                }
            }
            return state.isAir() ? ShapeRules.NOTHING : '?';
        }

        /** The first place the world and the shape disagree, in words. */
        private static String firstDifference(char[][][] want, char[][][] found,
                                              ShapeRules.Shape shape, int[] here) {
            for (int layer = 0; layer < want.length; layer++) {
                for (int row = 0; row < want[0].length; row++) {
                    for (int column = 0; column < want[0][0].length; column++) {
                        if (want[layer][row][column] == ShapeRules.NOTHING) {
                            continue;
                        }
                        if (want[layer][row][column] == found[layer][row][column]) {
                            continue;
                        }
                        return (column - here[2]) + ", " + (layer - here[0])
                                + ", " + (row - here[1]) + " should be "
                                + ShapeRules.named(shape, want[layer][row][column]);
                    }
                }
            }
            return null;
        }

        /** Put something in, if there is a job for it. */
        public boolean offer(ItemStack held) {
            if (!items.getStackInSlot(0).isEmpty()) {
                return false;
            }
            items.setStackInSlot(0, held.split(1));
            setChanged();
            return true;
        }

        /** One line: what it is doing, or what is wrong with it. */
        public Component saying(Level level, BlockPos pos, BlockState state) {
            if (wrong != null) {
                return Component.translatable("elysiumindustry.furnace.unformed",
                        Component.translatable("block." + shape().id()
                                .replace(":", ".")), wrong);
            }
            if (items.getStackInSlot(0).isEmpty()) {
                return Component.translatable("elysiumindustry.furnace.empty");
            }
            return Component.translatable("elysiumindustry.furnace.working",
                    String.valueOf(done));
        }

        public static void tick(Level level, BlockPos pos, BlockState state,
                                FurnaceBlockEntity furnace) {
            if (level.isClientSide) {
                return;
            }
            if (level.getGameTime() % 20L == 0L) {
                furnace.look(level, pos, state);
            }
            if (!state.getValue(FORMED)) {
                return;
            }
            furnace.work(level);
        }

        /**
         * One tick of the job in front of it.
         *
         * <p>No fuel and no wire: what a brick machine spends is time. The
         * blast furnace spends coke as well, and it spends it at the end
         * rather than at the start, so a player who breaks one halfway
         * through has lost the wait and not the coke.
         */
        private void work(Level level) {
            ItemStack input = items.getStackInSlot(0);
            FurnaceRules.Firing firing = firingFor(level, input);
            if (firing == null) {
                if (done != 0) {
                    done = 0;
                    setChanged();
                }
                return;
            }
            ItemStack out = items.getStackInSlot(1);
            ItemStack result = new ItemStack(net.minecraft.core.registries.BuiltInRegistries
                    .ITEM.get(net.minecraft.resources.ResourceLocation
                            .parse(firing.output())), firing.makes());
            if (!out.isEmpty() && (!ItemStack.isSameItem(out, result)
                    || out.getCount() + firing.makes() > out.getMaxStackSize())) {
                return;
            }
            done++;
            setChanged();
            if (done < firing.ticks()) {
                return;
            }
            done = 0;
            input.shrink(firing.count());
            items.setStackInSlot(0, input);
            if (out.isEmpty()) {
                items.setStackInSlot(1, result);
            } else {
                out.grow(firing.makes());
                items.setStackInSlot(1, out);
            }
        }

        /** The job this machine would do with what is in it, or null. */
        private FurnaceRules.Firing firingFor(Level level, ItemStack input) {
            if (input.isEmpty()) {
                return null;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(input.getItem()).toString();
            for (FurnaceRules.Firing one : FurnaceRules.FIRINGS) {
                if (!one.shape().equals(shape().id())) {
                    continue;
                }
                if (input.getCount() < one.count()) {
                    continue;
                }
                if (one.input().startsWith("#")) {
                    if (input.is(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.ITEM,
                            net.minecraft.resources.ResourceLocation.parse(
                                    one.input().substring(1))))) {
                        return one;
                    }
                } else if (one.input().equals(id)) {
                    return one;
                }
            }
            return null;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            done = tag.getInt("done");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("done", done);
        }
    }
}
