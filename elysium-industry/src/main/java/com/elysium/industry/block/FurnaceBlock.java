package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.registry.IndustryBlocks;
import com.elysium.industry.rules.FurnaceRules;
import com.elysium.industry.rules.ShapeRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
import com.elysium.lib.sound.ElysiumVoices;

import java.util.function.Predicate;

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
 *
 * <h2>What goes in, and what comes out</h2>
 *
 * No hopper feeds one, so a player's hands do it all. A thing the machine has
 * a job for goes in one a click, and coke goes on the blast furnace's fire the
 * same way. A thing it has no job for is not its business: a brick placed
 * against the controller while building goes on the wall. An empty hand on a
 * formed controller takes what it made; with nothing to take, it says why.
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
        // Nothing here takes it, so the hand does what it would anywhere
        // else: a brick goes on the wall. Skipped rather than passed, because
        // passing would hand the click to the empty-hand path, which takes the
        // output and places nothing.
        if (FurnaceRules.slotFor(shape().id(), idOf(held), inTag(held))
                == FurnaceRules.REFUSED) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (furnace.offer(held)) {
                return ItemInteractionResult.SUCCESS;
            }
            // Something it has a job for, and no room for it yet.
            player.displayClientMessage(furnace.saying(level, pos, state), false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            // What it made comes out to an empty hand once the bricks are the
            // machine. With nothing to take, or with the bricks wrong, it says
            // why, which is the sentence this block exists to give.
            ItemStack made = state.getValue(FORMED) ? furnace.take() : ItemStack.EMPTY;
            if (!made.isEmpty()) {
                player.getInventory().placeItemBackInInventory(made);
            } else {
                player.displayClientMessage(furnace.saying(level, pos, state), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            Containers.dropContents(level, pos, furnace.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    /** A thing's id, spelled as the rules spell it. */
    static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Whether a thing is in a tag, named as the rules name it: no '#'. */
    static Predicate<String> inTag(ItemStack stack) {
        return tag -> stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag)));
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

    /** Three slots, a shape to stand in, and a sentence when it is not standing. */
    public static class FurnaceBlockEntity extends BlockEntity {

        private final ItemStackHandler items = new ItemStackHandler(FurnaceRules.SLOTS) {
            /**
             * Never fewer than three. A controller saved before the fuel slot
             * existed says it has two, and loading that as written would make
             * the first look at its fuel throw.
             */
            @Override
            public void setSize(int size) {
                super.setSize(Math.max(size, FurnaceRules.SLOTS));
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
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

        /**
         * Put one in, if there is a job for it: one a click, as every machine
         * in this module takes things. The rules say which slot, or that it
         * has no business here.
         */
        public boolean offer(ItemStack held) {
            int slot = held.isEmpty() ? FurnaceRules.REFUSED
                    : FurnaceRules.slotFor(shape().id(), idOf(held), inTag(held));
            if (slot == FurnaceRules.REFUSED) {
                return false;
            }
            if (!items.insertItem(slot, held.copyWithCount(1), false).isEmpty()) {
                return false;
            }
            held.shrink(1);
            return true;
        }

        /**
         * What it has made, all of it, for a player's empty hand.
         *
         * <p>With nothing made, anything in the input that no job here takes
         * comes out instead: a brick or a lump of coke put in before offer
         * asked, or a log a datapack has since taken out of the tag. Left, it
         * would sit there until the controller was broken.
         */
        public ItemStack take() {
            ItemStack made = items.getStackInSlot(FurnaceRules.OUTPUT);
            if (!made.isEmpty()) {
                return items.extractItem(FurnaceRules.OUTPUT, made.getCount(), false);
            }
            ItemStack stuck = items.getStackInSlot(FurnaceRules.INPUT);
            if (!stuck.isEmpty() && FurnaceRules.slotFor(shape().id(), idOf(stuck),
                    inTag(stuck)) != FurnaceRules.INPUT) {
                return items.extractItem(FurnaceRules.INPUT, stuck.getCount(), false);
            }
            return ItemStack.EMPTY;
        }

        /** Everything in it, for when the controller is broken: the coke too. */
        public SimpleContainer spill() {
            ItemStack[] every = new ItemStack[items.getSlots()];
            for (int slot = 0; slot < every.length; slot++) {
                every[slot] = items.getStackInSlot(slot);
            }
            return new SimpleContainer(every);
        }

        /** How much of what this machine burns is on its fire. */
        private int fuelHeld() {
            FurnaceRules.Fuel burns = FurnaceRules.fuelOf(shape().id());
            ItemStack fuel = items.getStackInSlot(FurnaceRules.FUEL);
            return burns == null || fuel.isEmpty() || !burns.item().equals(idOf(fuel))
                    ? 0 : fuel.getCount();
        }

        /** One line: what it is doing, or what is wrong with it. */
        public Component saying(Level level, BlockPos pos, BlockState state) {
            if (wrong != null) {
                return Component.translatable("elysiumindustry.furnace.unformed",
                        Component.translatable("block." + shape().id()
                                .replace(":", ".")), wrong);
            }
            FurnaceRules.Firing firing = firingFor(level,
                    items.getStackInSlot(FurnaceRules.INPUT));
            if (firing == null) {
                return Component.translatable("elysiumindustry.furnace.empty");
            }
            FurnaceRules.Fuel fuel = FurnaceRules.fuelOf(firing.shape());
            if (fuel != null && !FurnaceRules.mayFire(firing, fuelHeld())) {
                return Component.translatable("elysiumindustry.furnace.unfuelled",
                        Component.translatable("item." + fuel.item().replace(":", ".")),
                        String.valueOf(FurnaceRules.burnedBy(firing)),
                        String.valueOf(fuelHeld()));
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
            if (state.getValue(FORMED)) {
                ElysiumVoices.hum(level, pos, "roar");
            }
        }

        /**
         * One tick of the job in front of it.
         *
         * <p>No wire: what a brick machine spends is time. The blast furnace
         * spends coke as well. A job does not start without enough of it on
         * the fire, and the coke is spent at the end rather than at the
         * start, so a player who breaks one halfway through has lost the wait
         * and not the coke.
         */
        private void work(Level level) {
            ItemStack input = items.getStackInSlot(FurnaceRules.INPUT);
            FurnaceRules.Firing firing = firingFor(level, input);
            if (firing == null) {
                if (done != 0) {
                    done = 0;
                    setChanged();
                }
                return;
            }
            ItemStack out = items.getStackInSlot(FurnaceRules.OUTPUT);
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(firing.output())), firing.makes());
            if (!out.isEmpty() && (!ItemStack.isSameItem(out, result)
                    || out.getCount() + firing.makes() > out.getMaxStackSize())) {
                return;
            }
            if (!FurnaceRules.mayFire(firing, fuelHeld())) {
                return;
            }
            done++;
            setChanged();
            if (done < firing.ticks()) {
                return;
            }
            done = 0;
            input.shrink(firing.count());
            items.setStackInSlot(FurnaceRules.INPUT, input);
            ItemStack fuel = items.getStackInSlot(FurnaceRules.FUEL);
            fuel.shrink(FurnaceRules.burnedBy(firing));
            items.setStackInSlot(FurnaceRules.FUEL, fuel);
            if (out.isEmpty()) {
                items.setStackInSlot(FurnaceRules.OUTPUT, result);
            } else {
                out.grow(firing.makes());
                items.setStackInSlot(FurnaceRules.OUTPUT, out);
            }
        }

        /** The job this machine would do with what is in it, or null. */
        private FurnaceRules.Firing firingFor(Level level, ItemStack input) {
            if (input.isEmpty()) {
                return null;
            }
            FurnaceRules.Firing one = FurnaceRules.jobFor(shape().id(), idOf(input),
                    inTag(input));
            return one != null && input.getCount() >= one.count() ? one : null;
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
