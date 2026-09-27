package com.elysium.foundry.block;

import com.elysium.lib.item.ElysiumSlots;
import com.elysium.foundry.recipe.AlloyRecipe;
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

/**
 * Two metals in, one alloy out. The only machine the fourth age adds.
 *
 * <h2>Why only one</h2>
 *
 * Because the fourth age is a <em>chain</em>, not a mechanism. Everything else
 * it needs already exists: the crusher makes dust, the rolling mill makes
 * plate, the arc furnace and the centrifuge do what they always did. The one
 * thing none of them can do is hold two different metals at once, and that is
 * exactly what an alloy is.
 *
 * <p>Adding six more machines would have been the obvious move and the wrong
 * one: a player who has built a crusher and a rolling mill should be able to
 * reach the top of the material chain by building <b>one</b> more thing and
 * then a great deal of supply.
 */
public class AlloySmelterBlock extends GridBlock {

    public static final MapCodec<AlloySmelterBlock> CODEC = simpleCodec(AlloySmelterBlock::new);

    public AlloySmelterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlloySmelterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, FoundryBlockEntities.ALLOY_SMELTER.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof AlloySmelterBlockEntity smelter)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && smelter.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AlloySmelterBlockEntity smelter)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = smelter.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(smelter.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof AlloySmelterBlockEntity smelter) {
            Containers.dropContents(level, pos, smelter.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: two slots in, one out, and a share of the grid. */
    public static class AlloySmelterBlockEntity extends GridBlockEntity {

        public static final int LEFT = 0;
        public static final int RIGHT = 1;
        public static final int OUT = 2;

        private final ItemStackHandler items = new ItemStackHandler(3) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (slot != OUT) {
                    done = 0;
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot != OUT;
            }
        };

        private int done;

        public AlloySmelterBlockEntity(BlockPos pos, BlockState state) {
            super(FoundryBlockEntities.ALLOY_SMELTER.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return FoundryRules.casing(1).draw().rated();
        }

        /**
         * What it asks the grid for.
         *
         * The first casing's draw, and a constant — never a function of what
         * the grid can afford, which would be a loop with no fixed point.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return FoundryRules.casing(1).draw();
        }

        /** Put something in whichever slot will take it. */
        public boolean offer(ItemStack held) {
            for (int slot : new int[] {LEFT, RIGHT}) {
                ItemStack there = items.getStackInSlot(slot);
                if (there.isEmpty()
                        || ItemStack.isSameItemSameComponents(there, held)) {
                    ItemStack left = items.insertItem(slot, held.copy(), false);
                    if (left.getCount() != held.getCount()) {
                        held.setCount(left.getCount());
                        return true;
                    }
                }
            }
            return false;
        }

        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(LEFT),
                    items.getStackInSlot(RIGHT), items.getStackInSlot(OUT));
        }

        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumfoundry.smelter.dark");
            }
            if (items.getStackInSlot(LEFT).isEmpty()
                    || items.getStackInSlot(RIGHT).isEmpty()) {
                return Component.translatable("elysiumfoundry.smelter.waiting",
                        String.format("%.0f%%", share() * 100.0F));
            }
            return Component.translatable("elysiumfoundry.smelter.melting",
                    String.format("%.0f%%", share() * 100.0F));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            float speed = share();
            AlloyRecipe.Input asked = new AlloyRecipe.Input(
                    items.getStackInSlot(LEFT), items.getStackInSlot(RIGHT));
            if (speed <= 0.0F || asked.left().isEmpty() || asked.right().isEmpty()) {
                done = 0;
                return;
            }
            Optional<RecipeHolder<AlloyRecipe>> found = level.getRecipeManager()
                    .getRecipeFor(FoundryRecipes.ALLOYING.get(), asked, level);
            if (found.isEmpty()) {
                done = 0;
                return;
            }
            AlloyRecipe recipe = found.get().value();
            ItemStack result = recipe.assemble(asked, level.registryAccess());
            if (!ElysiumSlots.roomFor(items, OUT, result)) {
                return;                        // no room; stop rather than lose it
            }
            // The same work units the ages below count in, so a share that
            // moves halfway through neither invents nor throws away progress.
            done += IndustryRules.workPerTick(speed);
            if (done < IndustryRules.workFor(recipe.ticks())) {
                return;
            }
            done = 0;
            items.extractItem(LEFT, recipe.takeFromLeft(asked), false);
            items.extractItem(RIGHT, recipe.takeFromRight(asked), false);
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
