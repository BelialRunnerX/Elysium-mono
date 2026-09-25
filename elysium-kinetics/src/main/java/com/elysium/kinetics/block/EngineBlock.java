package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.registry.KineticItems;
import com.elysium.kinetics.rules.EngineRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * An engine: a source that costs something to run.
 *
 * <p>Age I had four sources and every one of them was free once it was
 * built — the wheel wants a river, the sail wants sky, and neither is spent.
 * Which meant the whole age had no fuel in it, and a fuel is the thing that
 * makes a machine hall a decision rather than a shape.
 *
 * <p>One class for all three engines. They differ in how hard they push, how
 * thirsty they are and how deep a fuel they will take, and all three of those
 * are {@link EngineRules}' business. Three classes differing in three numbers
 * is three places for the three numbers to drift.
 */
public class EngineBlock extends KineticBlock {

    /** The first engine, which is what a codec with no context has to guess. */
    public static final String FIRST = "furnace_engine";

    public static final MapCodec<EngineBlock> CODEC =
            simpleCodec(properties -> new EngineBlock(properties, FIRST));

    private final String engine;

    public EngineBlock(Properties properties, String engine) {
        super(properties);
        this.engine = engine;
    }

    /** Which engine this is. */
    public String engine() {
        return engine;
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.ENGINE.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof EngineBlockEntity engine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && engine.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            player.displayClientMessage(engine.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            Containers.dropContents(level, pos, engine.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: one slot of fuel, burnt at the engine's own thirst. */
    public static class EngineBlockEntity extends KineticBlockEntity {

        /** How many ticks of a second there are, for the thirst arithmetic. */
        public static final int TICKS_PER_SECOND = 20;

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return fuelOf(stack) != null;
            }
        };

        /** Ticks of fuel left in the tank. */
        private int left;

        public EngineBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.ENGINE.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        /** Which engine this block is, out of the rules. */
        public EngineRules.Engine rules() {
            if (getBlockState().getBlock() instanceof EngineBlock block) {
                EngineRules.Engine found = EngineRules.engine(block.engine());
                if (found != null) {
                    return found;
                }
            }
            return EngineRules.ENGINES.get(0);
        }

        /**
         * A source exactly while there is fuel left.
         *
         * <p>Nothing else. An engine that pushes on an empty tank is an
         * engine that is free, and a free engine is every other source in the
         * age with an extra recipe in front of it.
         */
        @Nullable
        @Override
        public KineticRules.Source source() {
            return left > 0 ? rules().source() : null;
        }

        /** What is in the slot, as the rules name it, or null. */
        @Nullable
        public EngineRules.Fuel fuelOf(ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            net.minecraft.resources.ResourceLocation where =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem());
            EngineRules.Fuel found = EngineRules.fuel(where.toString());
            return found != null && rules().accepts(found) ? found : null;
        }

        public boolean offer(ItemStack coming) {
            if (!items.isItemValid(0, coming)) {
                return false;
            }
            ItemStack over = items.insertItem(0, coming.copy(), false);
            if (over.getCount() == coming.getCount()) {
                return false;
            }
            coming.setCount(over.getCount());
            return true;
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(0));
        }

        public Component says() {
            if (left > 0) {
                return Component.translatable("elysiumkinetics.engine.burning",
                        String.valueOf(left / TICKS_PER_SECOND),
                        String.format("%.0f", rpm()));
            }
            if (items.getStackInSlot(0).isEmpty()) {
                return Component.translatable("elysiumkinetics.engine.empty");
            }
            return Component.translatable("elysiumkinetics.engine.wrongfuel");
        }

        /**
         * One tick of burning.
         *
         * <p>The thirst is the rules' — ticks of fuel spent per second of
         * running — so a bigger engine empties the same lump of coal faster
         * and gets the same work out of it in less time. That is the honest
         * difference between a big engine and a small one, and it is the
         * reason the harness can prove no engine makes energy.
         */
        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            EngineRules.Engine rules = rules();
            if (left > 0) {
                left -= Math.max(1, rules.burning() / TICKS_PER_SECOND);
                if (left <= 0) {
                    left = 0;
                    unsettle();
                }
                setChanged();
                return;
            }
            EngineRules.Fuel fuel = fuelOf(items.getStackInSlot(0));
            if (fuel == null) {
                return;
            }
            items.extractItem(0, 1, false);
            left = fuel.burn();
            unsettle();
            setChanged();
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            left = Math.max(0, tag.getInt("left"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("left", left);
        }
    }
}
