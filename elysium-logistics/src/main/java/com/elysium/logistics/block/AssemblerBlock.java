package com.elysium.logistics.block;

import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.LogisticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * An assembler: one job at a time, and nothing clever.
 *
 * <h2>Why the hands are separate from the brain</h2>
 *
 * The core works out what has to be made and in what order. It does not make
 * anything. An assembler makes one thing repeatedly and knows nothing about
 * plans, orders or recipes beyond the one it was handed. Splitting them that
 * way is what lets a player answer "my factory is slow" by building another
 * assembler, which is a sentence with an action in it — and it is what makes
 * the number of assemblers a visible, buildable, power-hungry thing rather
 * than a config option.
 *
 * <h2>It holds the ingredients it was given, and nothing else</h2>
 *
 * The core takes a batch's worth of ingredients out of the warehouse and puts
 * them here. From that moment this machine owns them: they are saved with it,
 * they drop when it is broken, and no plan anywhere else can spend them twice.
 * That is the whole of why a job is dispatched in batches rather than whole —
 * what has not been dispatched is still in the drawers, where a player can
 * still use it.
 */
public class AssemblerBlock extends GridBlock {

    public static final MapCodec<AssemblerBlock> CODEC = simpleCodec(AssemblerBlock::new);

    /** Whether it is making something, so the model can say so. */
    public static final BooleanProperty BUSY = BooleanProperty.create("busy");

    public AssemblerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BUSY, false));
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BUSY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, LogisticBlockEntities.ASSEMBLER.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler) {
            player.displayClientMessage(assembler.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler) {
            // Everything it was holding goes on the floor. A machine that ate
            // a batch of somebody's iron when it was mined would be the one
            // place in this module where items are destroyed without anybody
            // asking for it.
            Containers.dropContents(level, pos, assembler.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: a batch of ingredients, and a countdown. */
    public static class AssemblerBlockEntity extends GridBlockEntity {

        /** As many slots as a crafting grid, because that is what it is doing. */
        public static final int INPUTS = 9;

        /** Somewhere to put what it made, until the core comes for it. */
        public static final int OUTPUTS = 4;

        private final ItemStackHandler inputs = new ItemStackHandler(INPUTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };

        private final ItemStackHandler outputs = new ItemStackHandler(OUTPUTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };

        /** What it is making, in the planner's vocabulary. Empty when idle. */
        private String making = "";

        /** How many runs of it are left in this batch. */
        private int left;

        /** What one run consumes and produces, decided by the core. */
        private final List<ItemStack> perRun = new ArrayList<>();
        private ItemStack yield = ItemStack.EMPTY;

        /** Work done towards the run in progress. */
        private int done;

        public AssemblerBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.ASSEMBLER.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return LogisticRules.ASSEMBLER.rated();
        }

        /**
         * What it asks the grid for.
         *
         * <p>A constant when it has work and nothing when it has not. Never a
         * function of what the grid can afford: a machine that asked for less
         * when the grid was short would change what the grid could afford,
         * which would change what it asked for, and that never settles.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return working() ? LogisticRules.ASSEMBLER : null;
        }

        public boolean working() {
            return left > 0;
        }

        public boolean idle() {
            return left <= 0 && outputs.getStackInSlot(0).isEmpty();
        }

        public String making() {
            return making;
        }

        /**
         * Take a batch on.
         *
         * @param output     what it is making, for the message and the core
         * @param runs       how many times
         * @param ingredients everything the whole batch consumes, already taken
         *                    out of the warehouse
         * @param each       what one run consumes
         * @param result     what one run produces
         * @return whether it took it
         */
        public boolean take(String output, int runs, List<ItemStack> ingredients,
                            List<ItemStack> each, ItemStack result) {
            if (!idle() || runs <= 0 || each.isEmpty() || result.isEmpty()) {
                return false;
            }
            for (int slot = 0; slot < INPUTS; slot++) {
                inputs.setStackInSlot(slot, ItemStack.EMPTY);
            }
            int slot = 0;
            for (ItemStack stack : ingredients) {
                if (slot >= INPUTS) {
                    return false;
                }
                inputs.setStackInSlot(slot++, stack.copy());
            }
            making = output;
            left = runs;
            done = 0;
            perRun.clear();
            for (ItemStack one : each) {
                perRun.add(one.copy());
            }
            yield = result.copy();
            unsettle();
            setChanged();
            return true;
        }

        /** Everything it has finished, handed over and forgotten. */
        public List<ItemStack> collect() {
            List<ItemStack> out = new ArrayList<>();
            for (int slot = 0; slot < OUTPUTS; slot++) {
                ItemStack stack = outputs.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    out.add(stack.copy());
                    outputs.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            return out;
        }

        /** What it would say if it could talk. */
        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (!working()) {
                return Component.translatable("elysiumlogistics.assembler.idle");
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumlogistics.assembler.dark", making);
            }
            return Component.translatable("elysiumlogistics.assembler.working", making,
                    String.valueOf(left),
                    String.valueOf(LogisticRules.assembleTicks(left, share()) / 20));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            boolean busy = working();
            if (busy != state.getValue(BUSY)) {
                level.setBlock(pos, state.setValue(BUSY, busy), 3);
            }
            if (!busy || share() <= 0.0F) {
                return;
            }
            done += IndustryRules.workPerTick(share());
            ElysiumVoices.hum(level, pos, "electric");
            while (done >= LogisticRules.assembleWork() && left > 0) {
                if (!consume()) {
                    // The batch and the recipe have disagreed, which can only
                    // happen if somebody reached in. Stop rather than make
                    // something out of nothing.
                    left = 0;
                    break;
                }
                done -= LogisticRules.assembleWork();
                left--;
                store(yield.copy());
            }
            if (left <= 0) {
                done = 0;
                unsettle();
            }
            setChanged();
        }

        /** Spend one run's ingredients, or say it could not. */
        private boolean consume() {
            for (ItemStack one : perRun) {
                int wanted = one.getCount();
                for (int slot = 0; slot < INPUTS && wanted > 0; slot++) {
                    ItemStack held = inputs.getStackInSlot(slot);
                    if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, one)) {
                        continue;
                    }
                    int taken = Math.min(wanted, held.getCount());
                    held.shrink(taken);
                    inputs.setStackInSlot(slot, held.isEmpty() ? ItemStack.EMPTY : held);
                    wanted -= taken;
                }
                if (wanted > 0) {
                    return false;
                }
            }
            return true;
        }

        /** Put what it made somewhere, or drop the batch if there is nowhere. */
        private void store(ItemStack made) {
            for (int slot = 0; slot < OUTPUTS && !made.isEmpty(); slot++) {
                made = outputs.insertItem(slot, made, false);
            }
            if (!made.isEmpty() && level != null) {
                // Only reachable if the core has stopped collecting, which
                // means it was mined. Better on the floor than gone.
                Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(),
                        getBlockPos().getZ(), made);
            }
        }

        /** Everything in it, for when it is broken. */
        public SimpleContainer spill() {
            SimpleContainer out = new SimpleContainer(INPUTS + OUTPUTS);
            for (int slot = 0; slot < INPUTS; slot++) {
                out.setItem(slot, inputs.getStackInSlot(slot));
            }
            for (int slot = 0; slot < OUTPUTS; slot++) {
                out.setItem(INPUTS + slot, outputs.getStackInSlot(slot));
            }
            return out;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            inputs.deserializeNBT(registries, tag.getCompound("inputs"));
            outputs.deserializeNBT(registries, tag.getCompound("outputs"));
            making = tag.getString("making");
            left = tag.getInt("left");
            done = tag.getInt("done");
            perRun.clear();
            CompoundTag each = tag.getCompound("each");
            for (int index = 0; index < each.getInt("size"); index++) {
                ItemStack.parse(registries, each.getCompound("i" + index))
                        .ifPresent(perRun::add);
            }
            yield = tag.contains("yield")
                    ? ItemStack.parse(registries, tag.getCompound("yield"))
                            .orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("inputs", inputs.serializeNBT(registries));
            tag.put("outputs", outputs.serializeNBT(registries));
            tag.putString("making", making);
            tag.putInt("left", left);
            tag.putInt("done", done);
            CompoundTag each = new CompoundTag();
            each.putInt("size", perRun.size());
            for (int index = 0; index < perRun.size(); index++) {
                each.put("i" + index, (CompoundTag) perRun.get(index)
                        .save(registries, new CompoundTag()));
            }
            tag.put("each", each);
            if (!yield.isEmpty()) {
                tag.put("yield", (CompoundTag) yield.save(registries, new CompoundTag()));
            }
        }
    }
}
