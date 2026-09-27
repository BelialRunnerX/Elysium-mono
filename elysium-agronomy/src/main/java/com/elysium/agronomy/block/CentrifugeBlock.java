package com.elysium.agronomy.block;

import com.elysium.lib.item.ElysiumSlots;
import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.registry.AgronomyItems;
import com.elysium.agronomy.rules.AgronomyRules;
import com.elysium.agronomy.rules.ApiaryRules;
import com.elysium.agronomy.rules.BeeRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A centrifuge: what a comb is actually for.
 *
 * <p>Until this block existed a comb was a thing you collected and then
 * looked at. The bee that makes it names a material, the material is
 * something this pack can make, and the step between the two was missing —
 * which is a whole production chain that stops one block short of producing
 * anything.
 *
 * <p>What comes out is not written here. {@link ApiaryRules#spun} says it,
 * from the bee's own kind: a resource comb gives its material, wax and a
 * bottle of honey, and a comb of honey gives the last two and no material at
 * all. A block that decided that for itself would be a second opinion about
 * what a bee produces, and two opinions is one too many.
 */
public class CentrifugeBlock extends GridBlock {

    public static final MapCodec<CentrifugeBlock> CODEC =
            simpleCodec(CentrifugeBlock::new);

    public CentrifugeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AgronomyBlockEntities.CENTRIFUGE.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity spinner) {
            player.displayClientMessage(spinner.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity spinner) {
            Containers.dropContents(level, pos, spinner.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: combs in one slot, what they come apart into in three. */
    public static class CentrifugeBlockEntity extends GridBlockEntity {

        /** Where combs go in. */
        public static final int IN = 0;

        /** Where the first of the three things comes out. */
        public static final int FIRST_OUT = 1;

        /** How many things a comb can come apart into: material, wax, honey. */
        public static final int OUTPUTS = 3;

        private final ItemStackHandler items =
                new ItemStackHandler(FIRST_OUT + OUTPUTS) {
                    @Override
                    protected void onContentsChanged(int slot) {
                        setChanged();
                    }

                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) {
                        return slot == IN && stack.is(AgronomyItems.COMB_TAG);
                    }
                };

        private int spun;

        public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.CENTRIFUGE.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return ApiaryRules.SPINNER.rated();
        }

        /**
         * What it asks the grid for.
         *
         * <p>Nothing when there is nothing to spin, so a centrifuge somebody
         * has walled off is not a permanent tax on their grid. The same rule
         * the accelerator follows and for the same reason.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return items.getStackInSlot(IN).isEmpty() ? null : ApiaryRules.SPINNER;
        }

        public SimpleContainer spill() {
            ItemStack[] held = new ItemStack[items.getSlots()];
            for (int slot = 0; slot < held.length; slot++) {
                held[slot] = items.getStackInSlot(slot);
            }
            return new SimpleContainer(held);
        }

        public Component says() {
            if (items.getStackInSlot(IN).isEmpty()) {
                return Component.translatable("elysiumagronomy.centrifuge.empty");
            }
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumagronomy.centrifuge.dark");
            }
            return Component.translatable("elysiumagronomy.centrifuge.running",
                    String.valueOf(items.getStackInSlot(IN).getCount()),
                    String.format("%.0f%%", share() * 100.0F));
        }

        /**
         * Which bee's comb is in the slot, or null.
         *
         * <p>By the item's own name against the roster, so a comb this block
         * has never heard of is a comb it refuses rather than a comb it
         * guesses about.
         */
        @Nullable
        public BeeRules.Bee holding() {
            ItemStack stack = items.getStackInSlot(IN);
            if (stack.isEmpty()) {
                return null;
            }
            net.minecraft.resources.ResourceLocation where =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            stack.getItem());
            for (BeeRules.Bee bee : BeeRules.BEES) {
                if (bee.comb().equals(where.getPath())) {
                    return bee;
                }
            }
            return null;
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            BeeRules.Bee bee = holding();
            if (bee == null || share() <= 0.0F || burned()) {
                return;
            }
            // Counted in the same work units the pot and the hive use, so a
            // grid that browns out halfway through a comb neither invents one
            // nor throws away the half that was done.
            spun += AgronomyRules.workPerTick(share());
            int whole = AgronomyRules.workFor(ApiaryRules.SPIN_TICKS);
            if (spun < whole) {
                return;
            }
            if (!room(bee)) {
                return;
            }
            spun -= whole;
            items.extractItem(IN, 1, false);
            java.util.List<String> made = ApiaryRules.spun(bee);
            java.util.List<Integer> counts = ApiaryRules.spunCounts(bee);
            for (int at = 0; at < made.size(); at++) {
                put(FIRST_OUT + at, AgronomyItems.thing(made.get(at)),
                        counts.get(at));
            }
        }

        /** Whether everything a comb comes apart into has somewhere to go. */
        private boolean room(BeeRules.Bee bee) {
            java.util.List<String> made = ApiaryRules.spun(bee);
            java.util.List<Integer> counts = ApiaryRules.spunCounts(bee);
            for (int at = 0; at < made.size(); at++) {
                ItemStack coming = AgronomyItems.thing(made.get(at));
                if (coming.isEmpty()) {
                    continue;
                }
                coming.setCount(counts.get(at));
                if (!ElysiumSlots.roomFor(items, FIRST_OUT + at, coming)) {
                    return false;
                }
            }
            return true;
        }

        private void put(int slot, ItemStack what, int count) {
            if (what.isEmpty()) {
                return;
            }
            ItemStack coming = what.copy();
            coming.setCount(count);
            // Placed, not inserted: the output slots refuse every insert so a
            // hopper cannot fill them, and the machine went through the same
            // door - every comb spun into nothing (live test, 27 September 2026).
            items.setStackInSlot(slot, ElysiumSlots.grown(items.getStackInSlot(slot), coming));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            spun = Math.max(0, tag.getInt("spun"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("spun", spun);
        }
    }
}
