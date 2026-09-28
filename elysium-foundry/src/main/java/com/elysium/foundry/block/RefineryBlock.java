package com.elysium.foundry.block;

import com.elysium.lib.item.ElysiumSlots;
import com.elysium.foundry.recipe.RefineRecipe;
import com.elysium.foundry.registry.FoundryBlockEntities;
import com.elysium.foundry.registry.FoundryRecipes;
import com.elysium.foundry.rules.FoundryRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * Raw ore in, the metal and its companion out. The fourth age's second and
 * last machine (docs/SPEC_FOUNDRY.md §3).
 *
 * <h2>Why a second machine after all</h2>
 *
 * The alloy smelter's own documentation argues against a wall of machines,
 * and the argument stands: every step the ages below can do, they keep doing.
 * The one thing none of them does is give back what the rock held beside the
 * metal — Age III's enricher takes three units of tin out of an ore and
 * discards the zinc that was in it. "Age IV is the only full recovery" was a
 * promise with no machine behind it. This is the machine.
 *
 * <p>It is a tier-III machine, on the third casing's draw, because recovery
 * is the reward for having built Age II and reached Age III, not a shortcut
 * past them.
 */
public class RefineryBlock extends GridBlock {

    public static final MapCodec<RefineryBlock> CODEC = simpleCodec(RefineryBlock::new);

    public RefineryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RefineryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, FoundryBlockEntities.REFINERY.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && refinery.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean gave = false;
            for (ItemStack out : refinery.takeAll()) {
                if (!out.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(out);
                    gave = true;
                }
            }
            if (!gave) {
                player.displayClientMessage(refinery.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery) {
            Containers.dropContents(level, pos, refinery.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: one slot in, two out, and a share of the grid. */
    public static class RefineryBlockEntity extends GridBlockEntity {

        public static final int IN = 0;
        public static final int OUT = 1;
        public static final int RECOVERED = 2;

        private final ItemStackHandler items = new ItemStackHandler(3) {
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

        private int done;

        public RefineryBlockEntity(BlockPos pos, BlockState state) {
            super(FoundryBlockEntities.REFINERY.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return FoundryRules.refineryDraw().rated();
        }

        /** The third casing's draw, and a constant: never a function of what the grid can afford. */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return FoundryRules.refineryDraw();
        }

        public boolean offer(ItemStack held) {
            ItemStack there = items.getStackInSlot(IN);
            if (there.isEmpty() || ItemStack.isSameItemSameComponents(there, held)) {
                ItemStack left = items.insertItem(IN, held.copy(), false);
                if (left.getCount() != held.getCount()) {
                    held.setCount(left.getCount());
                    return true;
                }
            }
            return false;
        }

        public ItemStack[] takeAll() {
            return new ItemStack[] {
                    items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false),
                    items.extractItem(RECOVERED, items.getStackInSlot(RECOVERED).getCount(), false)};
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(IN),
                    items.getStackInSlot(OUT), items.getStackInSlot(RECOVERED));
        }

        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumfoundry.refinery.dark");
            }
            if (items.getStackInSlot(IN).isEmpty()) {
                return Component.translatable("elysiumfoundry.refinery.waiting",
                        String.format("%.0f%%", share() * 100.0F));
            }
            return Component.translatable("elysiumfoundry.refinery.refining",
                    String.format("%.0f%%", share() * 100.0F));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            float speed = share();
            RefineRecipe.Input asked = new RefineRecipe.Input(items.getStackInSlot(IN));
            if (speed <= 0.0F || asked.ore().isEmpty()) {
                done = 0;
                return;
            }
            Optional<RecipeHolder<RefineRecipe>> found = level.getRecipeManager()
                    .getRecipeFor(FoundryRecipes.REFINING.get(), asked, level);
            if (found.isEmpty()) {
                done = 0;
                return;
            }
            RefineRecipe recipe = found.get().value();
            ItemStack result = recipe.assemble(asked, level.registryAccess());
            ItemStack recovered = recipe.recovered();
            if (!ElysiumSlots.roomFor(items, OUT, result)
                    || !ElysiumSlots.roomFor(items, RECOVERED, recovered)) {
                return;                        // no room for both; stop rather than lose either
            }
            done += IndustryRules.workPerTick(speed);
            ElysiumVoices.hum(level, pos, "roar");
            if (done < IndustryRules.workFor(recipe.ticks())) {
                return;
            }
            done = 0;
            items.extractItem(IN, 1, false);
            items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), result));
            items.setStackInSlot(RECOVERED, grow(items.getStackInSlot(RECOVERED), recovered));
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
            done = Math.max(0, tag.getInt("done"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("done", done);
        }
    }
}
