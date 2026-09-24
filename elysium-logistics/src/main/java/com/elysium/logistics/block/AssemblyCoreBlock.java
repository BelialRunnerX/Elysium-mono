package com.elysium.logistics.block;

import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.LogisticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The core: the half of autocrafting that thinks.
 *
 * <h2>What it does, in order</h2>
 *
 * Somebody asks for a number of something. The core reads every crafting
 * recipe the server has loaded, asks {@link LogisticRules#plan} what that
 * would take, and gets back either a refusal with a sentence in it or a list
 * of jobs in an order that works. It then spends the rest of its life handing
 * batches of those jobs to whatever assemblers are on its bus, taking the
 * ingredients out of the warehouse as it goes and putting the results back.
 *
 * <h2>Why it refuses before it takes anything</h2>
 *
 * A plan that cannot be finished is worse than no plan, because a half-run
 * plan is a warehouse full of intermediate parts nobody asked for. So the
 * whole thing is worked out first — including whether it is small enough to be
 * worth starting — and the answer arrives while the drawers are still full.
 * "That would take eleven thousand machine-runs" is a sentence a player can
 * act on; a factory quietly chewing for an hour is not.
 *
 * <h2>Why it is Age III</h2>
 *
 * It draws {@link LogisticRules#ASSEMBLY_CORE} at medium voltage. A player can
 * index a factory in Age II with a controller and a wall of drawers; making
 * the factory build itself wants a buffered grid behind it, and the voltage is
 * how the pack says so without a single advancement or gate.
 */
public class AssemblyCoreBlock extends GridBlock {

    public static final MapCodec<AssemblyCoreBlock> CODEC =
            simpleCodec(AssemblyCoreBlock::new);

    public AssemblyCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, LogisticBlockEntities.ASSEMBLY_CORE.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof AssemblyCoreBlockEntity core) {
            player.displayClientMessage(core.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------

    /** The working half: a queue of jobs, and the machines to give them to. */
    public static class AssemblyCoreBlockEntity extends GridBlockEntity {

        /** How often it looks along the bus again. The controller's number. */
        public static final int LOOKS_AGAIN_EVERY = 40;

        /** What is left to make, in the order it has to happen. */
        private final List<LogisticRules.Job> queue = new ArrayList<>();

        /** What one run of each queued job needs and makes. */
        private final Map<String, LogisticRules.Step> plan = new HashMap<>();

        /** What the whole request was for, so it can be described. */
        private String asked = "";

        private List<BlockPos> assemblers = List.of();
        private int since = LOOKS_AGAIN_EVERY;

        public AssemblyCoreBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.ASSEMBLY_CORE.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return LogisticRules.ASSEMBLY_CORE.rated();
        }

        /**
         * What it asks the grid for.
         *
         * Constant while it has work, nothing while it has none — never a
         * function of what the grid can give.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return queue.isEmpty() ? null : LogisticRules.ASSEMBLY_CORE;
        }

        public boolean busy() {
            return !queue.isEmpty();
        }

        /**
         * Work out what a request would take, and start it if it is sane.
         *
         * @return what to tell the player, always
         */
        public Component request(Level level, ItemStack what, long count) {
            if (busy()) {
                return Component.translatable("elysiumlogistics.core.busy", asked,
                        String.valueOf(queue.size()));
            }
            if (what.isEmpty()) {
                return Component.translatable("elysiumlogistics.core.nothing");
            }
            Map<String, LogisticRules.Step> steps = CraftingCatalogue.of(level);
            String target = CraftingCatalogue.idOf(what);
            LogisticRules.Plan worked = LogisticRules.plan(steps, target, count);
            if (!worked.possible()) {
                return Component.translatable("elysiumlogistics.core.refused",
                        worked.refused());
            }
            if (worked.jobs().isEmpty()) {
                return Component.translatable("elysiumlogistics.core.nothing_to_make",
                        what.getHoverName());
            }
            if (!LogisticRules.worthStarting(worked, assemblers.size())) {
                return Component.translatable("elysiumlogistics.core.enormous",
                        String.valueOf(worked.totalRuns()),
                        String.valueOf(Math.max(1, assemblers.size())));
            }
            queue.clear();
            queue.addAll(worked.jobs());
            plan.clear();
            for (LogisticRules.Job job : worked.jobs()) {
                plan.put(job.output(), steps.get(job.output()));
            }
            asked = target;
            unsettle();
            setChanged();
            return Component.translatable("elysiumlogistics.core.started",
                    String.valueOf(count), what.getHoverName(),
                    String.valueOf(worked.jobs().size()),
                    String.valueOf(worked.totalRuns()));
        }

        /** What it would say if it could talk. */
        public Component says() {
            if (assemblers.isEmpty()) {
                return Component.translatable("elysiumlogistics.core.handless");
            }
            if (!busy()) {
                return Component.translatable("elysiumlogistics.core.waiting",
                        String.valueOf(assemblers.size()));
            }
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumlogistics.core.dark", asked);
            }
            long left = 0;
            for (LogisticRules.Job job : queue) {
                left += job.runs();
            }
            return Component.translatable("elysiumlogistics.core.running", asked,
                    String.valueOf(queue.size()), String.valueOf(left));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++since >= LOOKS_AGAIN_EVERY) {
                since = 0;
                assemblers = LogisticNetwork.along(level, pos,
                        AssemblerBlock.AssemblerBlockEntity.class);
            }
            if (share() <= 0.0F) {
                return;
            }
            IItemHandler warehouse = warehouse(level, pos);
            if (warehouse == null) {
                return;
            }
            gather(level, warehouse);
            if (queue.isEmpty()) {
                return;
            }
            dispatch(level, warehouse);
        }

        /**
         * The drawers, as one inventory, through whichever controller is on
         * this bus.
         *
         * <p>A core without one is a brain with no warehouse: it can plan and
         * it can dispatch and it has nowhere to take ingredients from. Saying
         * so is better than inventing a second way to reach drawers.
         */
        @Nullable
        private IItemHandler warehouse(Level level, BlockPos pos) {
            for (BlockPos found : LogisticNetwork.along(level, pos,
                    ControllerBlock.ControllerBlockEntity.class)) {
                if (level.getBlockEntity(found)
                        instanceof ControllerBlock.ControllerBlockEntity controller) {
                    return controller.asOneInventory();
                }
            }
            return null;
        }

        /** Take everything the assemblers have finished and put it away. */
        private void gather(Level level, IItemHandler warehouse) {
            for (BlockPos pos : assemblers) {
                if (!(level.getBlockEntity(pos)
                        instanceof AssemblerBlock.AssemblerBlockEntity assembler)) {
                    continue;
                }
                for (ItemStack made : assembler.collect()) {
                    ItemStack left = made;
                    for (int slot = 0; slot < warehouse.getSlots() && !left.isEmpty();
                            slot++) {
                        left = warehouse.insertItem(slot, left, false);
                    }
                    if (!left.isEmpty()) {
                        // Nowhere to put it. Give it back rather than destroy
                        // it: the assembler will offer it again next tick, and
                        // a full warehouse is a thing a player can see.
                        assembler.take(assembler.making(), 0, List.of(), List.of(),
                                ItemStack.EMPTY);
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX(),
                                pos.getY() + 1, pos.getZ(), left);
                    }
                }
            }
        }

        /** Hand out as many batches as there are idle assemblers and jobs. */
        private void dispatch(Level level, IItemHandler warehouse) {
            int allowed = LogisticRules.jobsAtOnce(assemblers.size());
            for (BlockPos pos : assemblers) {
                if (allowed-- <= 0 || queue.isEmpty()) {
                    return;
                }
                if (!(level.getBlockEntity(pos)
                        instanceof AssemblerBlock.AssemblerBlockEntity assembler)
                        || !assembler.idle()) {
                    continue;
                }
                LogisticRules.Job job = queue.get(0);
                LogisticRules.Step step = plan.get(job.output());
                if (step == null) {
                    queue.remove(0);
                    continue;
                }
                int mostPerRun = 1;
                for (int need : step.needs().values()) {
                    mostPerRun = Math.max(mostPerRun, need);
                }
                int batch = LogisticRules.batchSize(job.runs(), mostPerRun);
                if (batch <= 0) {
                    queue.remove(0);
                    continue;
                }
                if (!hand(level, warehouse, assembler, step, batch)) {
                    // The ingredients are not there yet. Wait rather than
                    // skip: the order exists precisely so that what this job
                    // needs is being made by an earlier one.
                    return;
                }
                long rest = job.runs() - batch;
                if (rest > 0) {
                    queue.set(0, new LogisticRules.Job(job.output(), rest));
                } else {
                    queue.remove(0);
                }
                setChanged();
                if (queue.isEmpty()) {
                    unsettle();
                }
            }
        }

        /**
         * Take a batch's ingredients out of the warehouse and give them over.
         *
         * <p>Simulated first, all or nothing. Taking half the ingredients and
         * discovering the rest are missing would leave them stranded in a
         * machine that cannot use them, which is how an autocrafter ends up
         * holding a player's entire iron supply hostage.
         */
        private boolean hand(Level level, IItemHandler warehouse,
                             AssemblerBlock.AssemblerBlockEntity assembler,
                             LogisticRules.Step step, int batch) {
            Map<String, Integer> wanted = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                wanted.put(need.getKey(), need.getValue() * batch);
            }
            for (Map.Entry<String, Integer> need : wanted.entrySet()) {
                if (available(warehouse, need.getKey()) < need.getValue()) {
                    return false;
                }
            }
            List<ItemStack> taken = new ArrayList<>();
            for (Map.Entry<String, Integer> need : wanted.entrySet()) {
                taken.addAll(pull(warehouse, need.getKey(), need.getValue()));
            }
            List<ItemStack> each = new ArrayList<>();
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                ItemStack one = stackOf(need.getKey(), need.getValue());
                if (one.isEmpty()) {
                    give(warehouse, taken);
                    return false;
                }
                each.add(one);
            }
            ItemStack result = stackOf(step.output(), step.makes());
            if (result.isEmpty()
                    || !assembler.take(step.output(), batch, taken, each, result)) {
                give(warehouse, taken);
                return false;
            }
            return true;
        }

        private int available(IItemHandler warehouse, String id) {
            int found = 0;
            for (int slot = 0; slot < warehouse.getSlots(); slot++) {
                ItemStack held = warehouse.getStackInSlot(slot);
                if (!held.isEmpty() && CraftingCatalogue.idOf(held).equals(id)) {
                    found += held.getCount();
                }
            }
            return found;
        }

        private List<ItemStack> pull(IItemHandler warehouse, String id, int wanted) {
            List<ItemStack> got = new ArrayList<>();
            for (int slot = 0; slot < warehouse.getSlots() && wanted > 0; slot++) {
                ItemStack held = warehouse.getStackInSlot(slot);
                if (held.isEmpty() || !CraftingCatalogue.idOf(held).equals(id)) {
                    continue;
                }
                ItemStack out = warehouse.extractItem(slot, wanted, false);
                if (!out.isEmpty()) {
                    got.add(out);
                    wanted -= out.getCount();
                }
            }
            return got;
        }

        /** Put back what was taken, when a batch could not be handed over. */
        private void give(IItemHandler warehouse, List<ItemStack> taken) {
            for (ItemStack stack : taken) {
                ItemStack left = stack;
                for (int slot = 0; slot < warehouse.getSlots() && !left.isEmpty(); slot++) {
                    left = warehouse.insertItem(slot, left, false);
                }
                if (!left.isEmpty() && level != null) {
                    net.minecraft.world.Containers.dropItemStack(level,
                            getBlockPos().getX(), getBlockPos().getY() + 1,
                            getBlockPos().getZ(), left);
                }
            }
        }

        private ItemStack stackOf(String id, int count) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            return new ItemStack(item, count);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            queue.clear();
            plan.clear();
            asked = tag.getString("asked");
            ListTag jobs = tag.getList("queue", 10);
            for (int index = 0; index < jobs.size(); index++) {
                CompoundTag one = jobs.getCompound(index);
                long runs = one.getLong("runs");
                if (runs > 0) {
                    queue.add(new LogisticRules.Job(one.getString("output"), runs));
                }
                Map<String, Integer> needs = new HashMap<>();
                CompoundTag saved = one.getCompound("needs");
                for (String name : saved.getAllKeys()) {
                    needs.put(name, saved.getInt(name));
                }
                if (!needs.isEmpty()) {
                    plan.put(one.getString("output"), new LogisticRules.Step(
                            one.getString("output"), Math.max(1, one.getInt("makes")),
                            needs));
                }
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putString("asked", asked);
            ListTag jobs = new ListTag();
            for (LogisticRules.Job job : queue) {
                CompoundTag one = new CompoundTag();
                one.putString("output", job.output());
                one.putLong("runs", job.runs());
                LogisticRules.Step step = plan.get(job.output());
                if (step != null) {
                    one.putInt("makes", step.makes());
                    CompoundTag needs = new CompoundTag();
                    for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                        needs.putInt(need.getKey(), need.getValue());
                    }
                    one.put("needs", needs);
                }
                jobs.add(one);
            }
            tag.put("queue", jobs);
        }
    }
}
