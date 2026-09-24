package com.elysium.kinetics.block;

import com.elysium.kinetics.recipe.KineticRecipe;
import com.elysium.kinetics.registry.KineticRecipes;
import com.elysium.kinetics.rules.KineticRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A machine that takes one thing and gives back another, while it is turning.
 *
 * <h2>Four machines, one behaviour</h2>
 *
 * The millstone, press, saw and mixer differ in exactly three ways: how much
 * torque they draw, how fast they must turn, and which recipes they read.
 * Everything else — two slots, no screen, a hopper can feed it and one
 * underneath can take from it, it stops when the line slows, it spills what is
 * in it when broken — is the same for all four, and is here.
 *
 * <p>A fifth machine is a subclass with three answers in it. That is the point.
 *
 * <h2>Why no screen</h2>
 *
 * A screen for these would be four more classes each to say what the block is
 * already showing: what is on it, and whether it is turning. Prodding one with
 * an empty hand takes what it has made, or says why it has made nothing —
 * which is the whole of what a screen would have told you.
 */
public abstract class MachineBlock extends KineticBlock {

    protected MachineBlock(Properties properties) {
        super(properties);
    }

    /** What this machine draws out of the line. */
    public abstract KineticRules.Load load();

    /** Which recipes it reads. */
    public abstract KineticRecipe.Kind kind();

    /** The prefix of this machine's messages, e.g. {@code elysiumkinetics.millstone}. */
    public abstract String saying();

    /** Which axis it turns about, or null to take it from the placement. */
    @Nullable
    protected Direction.Axis fixedAxis() {
        return null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis fixed = fixedAxis();
        return defaultBlockState().setValue(AXIS,
                fixed != null ? fixed : context.getClickedFace().getAxis());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && machine.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = machine.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(machine.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: two slots and a job in progress. */
    public static class MachineBlockEntity extends KineticBlockEntity {

        public static final int IN = 0;
        public static final int OUT = 1;

        private final ItemStackHandler items = new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (slot == IN) {
                    done = 0;
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == IN;
            }
        };

        /** How far through the current job, in reference ticks. */
        private int done;

        public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        private MachineBlock block() {
            return getBlockState().getBlock() instanceof MachineBlock machine ? machine : null;
        }

        @Override
        public Direction.Axis axis() {
            MachineBlock machine = block();
            Direction.Axis fixed = machine == null ? null : machine.fixedAxis();
            return fixed != null ? fixed : getBlockState().getValue(AXIS);
        }

        @Nullable
        @Override
        public KineticRules.Load load() {
            MachineBlock machine = block();
            return machine == null ? null : machine.load();
        }

        /** Put something on it. True when it took some. */
        public boolean offer(ItemStack held) {
            if (!items.insertItem(IN, held.copyWithCount(1), false).isEmpty()) {
                return false;
            }
            held.shrink(1);
            return true;
        }

        /** Take what it has made. */
        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        /** Everything in it, for when the block is broken. */
        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(IN), items.getStackInSlot(OUT));
        }

        /**
         * What it would say if it could talk.
         *
         * The two failures a player will actually hit are "nothing is turning
         * it" and "it is turning, but not fast enough", and those need
         * different sentences or the second looks like the first.
         */
        public Component says() {
            MachineBlock machine = block();
            String prefix = machine == null ? "elysiumkinetics.machine" : machine.saying();
            if (!running()) {
                return Component.translatable(prefix + ".still");
            }
            float speed = Math.abs(rpm());
            KineticRules.Load load = load();
            if (load != null && speed < load.leastRpm()) {
                return Component.translatable(prefix + ".slow",
                        String.format("%.1f", speed),
                        String.format("%.0f", load.leastRpm()));
            }
            if (items.getStackInSlot(IN).isEmpty()) {
                return Component.translatable(prefix + ".empty",
                        String.format("%.1f", speed));
            }
            return Component.translatable(prefix + ".working", String.format("%.1f", speed));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            MachineBlock machine = block();
            ItemStack input = items.getStackInSlot(IN);
            if (machine == null || input.isEmpty() || !running()) {
                done = 0;
                return;
            }
            float speed = Math.abs(rpm());
            SingleRecipeInput asked = new SingleRecipeInput(input);
            Optional<RecipeHolder<KineticRecipe>> found = level.getRecipeManager()
                    .getRecipeFor(KineticRecipes.type(machine.kind()), asked, level);
            if (found.isEmpty()) {
                done = 0;
                return;
            }
            KineticRecipe recipe = found.get().value();
            if (!recipe.possibleAt(speed)) {
                done = 0;
                return;
            }
            ItemStack result = recipe.assemble(asked, level.registryAccess());
            if (!items.insertItem(OUT, result, true).isEmpty()) {
                return;                            // no room; stop rather than lose it
            }
            if (++done < recipe.ticksAt(speed)) {
                return;
            }
            done = 0;
            items.extractItem(IN, 1, false);
            // Set rather than inserted: isItemValid guards what a hopper may
            // push in, and the machine is allowed to fill its own output.
            items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), result));
            setChanged();
        }

        private static ItemStack grow(ItemStack there, ItemStack added) {
            if (there.isEmpty()) {
                return added;
            }
            ItemStack more = there.copy();
            more.grow(added.getCount());
            return more;
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
