package com.elysium.industry.block;

import com.elysium.industry.rules.IndustryRules;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A generator that burns something. The steam turbine and the diesel engine.
 *
 * <h2>It only burns when something wants the power</h2>
 *
 * An engine that consumed fuel into a grid with nothing on it would be an
 * engine every player learns to break rather than switch off, because there is
 * no switch. So it asks the grid whether anything is drawing before it lights
 * the next piece of fuel — and it finishes the piece it has already lit, which
 * is both what a firebox does and what stops it flickering on and off while a
 * machine between jobs decides whether it wants anything.
 *
 * <h2>What it is while it is not burning</h2>
 *
 * Not a generator at all: {@link #generator()} answers null. That matters for
 * more than the supply figure, because a grid measures its wire loss from the
 * nearest generator, and a cold engine is not where the power is coming from.
 */
public abstract class FueledBlock extends GridBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected FueledBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** What it makes while it is burning. */
    public abstract IndustryRules.Generator output();

    /**
     * How long one of these lasts in this engine, or zero if it will not take it.
     */
    public abstract int burns(ItemStack fuel);

    /** The prefix of its messages. */
    public abstract String saying();

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof FueledBlockEntity engine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && engine.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FueledBlockEntity engine)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(engine.says(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FueledBlockEntity engine) {
            Containers.dropContents(level, pos, engine.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: one slot of fuel and a fire that is lit or is not. */
    public static class FueledBlockEntity extends GridBlockEntity {

        public static final int FUEL = 0;

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                FueledBlock engine = block();
                return engine != null && engine.burns(stack) > 0;
            }
        };

        /** Ticks of the current piece of fuel still to burn. */
        private int burning;

        public FueledBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Nullable
        private FueledBlock block() {
            return getBlockState().getBlock() instanceof FueledBlock engine ? engine : null;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            FueledBlock engine = block();
            return engine == null ? IndustryRules.Voltage.LOW : engine.output().voltage();
        }

        @Nullable
        @Override
        public IndustryRules.Generator generator() {
            FueledBlock engine = block();
            return burning > 0 && engine != null ? engine.output() : null;
        }

        /** Whether the fire is lit. */
        public boolean lit() {
            return burning > 0;
        }

        /** Put fuel in it. True when it took some. */
        public boolean offer(ItemStack held) {
            if (!items.insertItem(FUEL, held.copyWithCount(1), false).isEmpty()) {
                return false;
            }
            held.shrink(1);
            return true;
        }

        /** Everything in it, for when the block is broken. */
        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(FUEL));
        }

        /** What it would say if it could talk. */
        public Component says() {
            FueledBlock engine = block();
            String prefix = engine == null ? "elysiumindustry.engine" : engine.saying();
            if (burning > 0) {
                return Component.translatable(prefix + ".burning",
                        String.format("%.0f", engine == null ? 0.0F : engine.output().output()),
                        String.valueOf(burning / 20));
            }
            if (items.getStackInSlot(FUEL).isEmpty()) {
                return Component.translatable(prefix + ".empty");
            }
            return Component.translatable(prefix + ".idle");
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            boolean wasLit = burning > 0;
            if (burning > 0) {
                burning--;
                if (burning == 0) {
                    // The supply just changed, so the grid has to be asked again.
                    unsettle();
                }
            } else if (wanted()) {
                ItemStack fuel = items.getStackInSlot(FUEL);
                FueledBlock engine = block();
                int lasts = engine == null ? 0 : engine.burns(fuel);
                if (lasts > 0) {
                    items.extractItem(FUEL, 1, false);
                    burning = lasts;
                    unsettle();
                }
            }
            if (wasLit != (burning > 0) && state.hasProperty(LIT)) {
                level.setBlock(pos, state.setValue(LIT, burning > 0), Block.UPDATE_ALL);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            burning = tag.getInt("burning");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("burning", burning);
        }
    }
}
