package com.elysium.logistics.block;

import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.LogisticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A drawer. One thing, a great deal of it, and no power at all.
 *
 * <h2>Why this is the first thing in the module</h2>
 *
 * A player meets storage long before they meet a grid. Everything else here —
 * the bus, the controller, the core — is worth building only once you already
 * have more cobblestone than a chest will hold, and a storage mod whose first
 * rung needs electricity has no first rung. So a drawer costs nothing to run,
 * needs no network, and works the day it is crafted.
 *
 * <h2>What you can see</h2>
 *
 * Its blockstate carries how full it is, in five steps, so a wall of them
 * reads at a glance and from across the room. That is deliberately not a
 * screen and not a floating label: this pack's rule is that the block says
 * what it is doing, and "how full" is the question a wall of drawers is asked
 * a hundred times more often than "what exactly is in slot two".
 *
 * <p>Prodding one with an empty hand takes a stack. Prodding it while
 * crouching says what is in it and how much. A comparator reads how full it
 * is, which is what a player wants to automate on.
 */
public class DrawerBlock extends BaseEntityBlock {

    public static final MapCodec<DrawerBlock> CODEC =
            simpleCodec(properties -> new DrawerBlock(properties,
                    LogisticRules.TIMBER_DRAWER));

    /** Which way it faces, so a wall of them all face the room. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How full it is, in five steps, because that is what a wall is read for. */
    public static final IntegerProperty FULL = IntegerProperty.create("full", 0, 4);

    private final LogisticRules.Drawer drawer;

    public DrawerBlock(Properties properties, LogisticRules.Drawer drawer) {
        super(properties);
        this.drawer = drawer;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FULL, 0));
    }

    public LogisticRules.Drawer drawer() {
        return drawer;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrawerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof DrawerBlockEntity into)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && into.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrawerBlockEntity from)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(from.says(), true);
            } else {
                ItemStack out = from.takeStack(hit.getDirection());
                if (out.isEmpty()) {
                    player.displayClientMessage(from.says(), true);
                } else {
                    player.getInventory().placeItemBackInInventory(out);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof DrawerBlockEntity from) {
            for (ItemStack stack : from.store().spill()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DrawerBlockEntity from)) {
            return 0;
        }
        float full = from.fullness();
        return full <= 0.0F ? 0 : 1 + Math.round(full * 14.0F);
    }

    // ------------------------------------------------------------------

    /** The working half: a store, and a blockstate that follows it. */
    public static class DrawerBlockEntity extends BlockEntity {

        private final DrawerStore store;

        public DrawerBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.DRAWER.get(), pos, state);
            LogisticRules.Drawer kind = state.getBlock() instanceof DrawerBlock block
                    ? block.drawer() : LogisticRules.TIMBER_DRAWER;
            this.store = new DrawerStore(kind, this::filled);
        }

        public DrawerStore store() {
            return store;
        }

        /** How full it is, across every kind in it, from nothing to one. */
        public float fullness() {
            long held = 0;
            for (int slot = 0; slot < store.getSlots(); slot++) {
                held += store.countIn(slot);
            }
            return (float) held / store.drawer().total();
        }

        /** Put a held stack in, and say whether any of it went. */
        public boolean offer(ItemStack held) {
            int slot = store.slotFor(held);
            if (slot < 0) {
                return false;
            }
            ItemStack left = store.insertItem(slot, held.copy(), false);
            if (left.getCount() == held.getCount()) {
                return false;
            }
            held.setCount(left.getCount());
            return true;
        }

        /** Take one stack out, of whichever kind the clicked face shows. */
        public ItemStack takeStack(Direction face) {
            for (int slot = 0; slot < store.getSlots(); slot++) {
                ItemStack out = store.extractItem(slot, Integer.MAX_VALUE, false);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return ItemStack.EMPTY;
        }

        /** What it would say if it could talk. */
        public Component says() {
            if (store.bare()) {
                return Component.translatable("elysiumlogistics.drawer.empty",
                        String.format("%,d", store.drawer().room()));
            }
            StringBuilder said = new StringBuilder();
            for (int slot = 0; slot < store.getSlots(); slot++) {
                if (store.kindIn(slot).isEmpty()) {
                    continue;
                }
                if (said.length() > 0) {
                    said.append(", ");
                }
                said.append(String.format("%,d", store.countIn(slot)))
                        .append(" ")
                        .append(store.kindIn(slot).getHoverName().getString());
            }
            return Component.translatable("elysiumlogistics.drawer.holding",
                    said.toString(), String.format("%,d", store.drawer().room()));
        }

        /**
         * The store changed, so the blockstate may have.
         *
         * Only when the step it lands in actually moves: a wall of fifty
         * drawers being filled by a hopper would otherwise send fifty block
         * updates a second to every player who can see it.
         */
        private void filled() {
            setChanged();
            if (level == null || level.isClientSide) {
                return;
            }
            int step = Math.round(Math.min(1.0F, fullness()) * 4.0F);
            BlockState state = getBlockState();
            if (state.hasProperty(FULL) && state.getValue(FULL) != step) {
                level.setBlock(getBlockPos(), state.setValue(FULL, step),
                        Block.UPDATE_ALL);
            }
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            store.load(tag.getCompound("store"), registries);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("store", store.save(registries));
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = super.getUpdateTag(registries);
            saveAdditional(tag, registries);
            return tag;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
