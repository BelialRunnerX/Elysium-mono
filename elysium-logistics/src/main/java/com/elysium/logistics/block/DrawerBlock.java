package com.elysium.logistics.block;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.LogisticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
 * crouching says what is in it and how much, or takes a void seal back out
 * if one is fitted. A comparator reads how full it is, which is what a
 * player wants to automate on.
 *
 * <h2>What a player can fit to one</h2>
 *
 * A void seal, the same part a bag takes, used on it: from then on what will
 * not fit is destroyed rather than refused. That is the one thing in this
 * module that destroys anything, so it is the one thing a player has to do
 * by hand. The seal comes back out into an empty hand, and a broken drawer
 * drops it with everything else in it.
 *
 * <p>And a key, used on it, which locks it to what it holds: emptied, it
 * keeps its kinds and takes no new ones, so a hopper cannot re-type a wall of
 * drawers the first time one runs dry. The key is never spent. Neither shows
 * on the front, so the drawer says both whenever it says what it holds.
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
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        // The key and the seal are asked about before anything goes in, or
        // the first key a player turns in a drawer is a key filed away in it.
        if (held.is(ours(LogisticRules.DRAWER_KEY))) {
            player.displayClientMessage(into.turnKey(), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (held.is(ours(LogisticRules.DRAWER_SEAL))) {
            player.displayClientMessage(into.seal(held), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (into.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        // Nothing went in: it is full, it holds something else, or it is
        // locked to what it holds. Say so, since a lock does not show on the
        // front and a drawer that silently refuses looks broken.
        player.displayClientMessage(into.says(), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrawerBlockEntity from)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && from.store().voiding()) {
                // The seal back into the empty hand, as a crate gives up its
                // last part: what was fitted by hand comes out by hand.
                ItemStack seal = from.unseal();
                player.displayClientMessage(Component.translatable(
                        "elysiumlogistics.drawer.unsealed", seal.getHoverName()), true);
                if (player.getMainHandItem().isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, seal);
                } else {
                    player.getInventory().placeItemBackInInventory(seal);
                }
            } else if (player.isShiftKeyDown()) {
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
            // And the seal, if one is fitted: a part is never lost with the
            // block it was fitted to, as a crate's are not.
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), from.sealsHeld());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    /**
     * The item this module registered under a name the rules give.
     *
     * <p>By name, as a crate names the parts it drops, so the drawer and the
     * rules agree on which item is the seal and which the key.
     */
    private static Item ours(String name) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(ElysiumLogistics.MODID, name));
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

        /**
         * Fit a void seal off the held stack, and say so.
         *
         * <p>One, and only while there is none. A second is refused and stays
         * in the hand, because a seal spent on nothing is a seal destroyed.
         */
        public Component seal(ItemStack held) {
            Component name = held.getHoverName();
            if (!LogisticRules.takesSeal(store.voiding())) {
                return Component.translatable("elysiumlogistics.drawer.sealed_twice", name);
            }
            store.voiding(true);
            held.shrink(1);
            return Component.translatable("elysiumlogistics.drawer.sealed", name);
        }

        /** Take the seal back out, voiding off, and hand over what came out. */
        public ItemStack unseal() {
            ItemStack seal = sealsHeld();
            store.voiding(false);
            return seal;
        }

        /** The seal fitted to it, as an item: the one it took, or nothing. */
        public ItemStack sealsHeld() {
            return new ItemStack(ours(LogisticRules.DRAWER_SEAL),
                    LogisticRules.sealsIn(store.voiding()));
        }

        /**
         * Turn a key in it: locked if it was not, unlocked if it was, and say
         * which. Nothing here touches the key, which is never spent.
         */
        public Component turnKey() {
            if (store.locked()) {
                store.locked(false);
                return Component.translatable("elysiumlogistics.drawer.unlocked");
            }
            if (!LogisticRules.mayLock(store.kindsKept())) {
                return Component.translatable("elysiumlogistics.drawer.nothing_to_lock");
            }
            store.locked(true);
            return Component.translatable("elysiumlogistics.drawer.locked_now");
        }

        /** What it would say if it could talk. */
        public Component says() {
            MutableComponent line;
            if (store.bare()) {
                line = Component.translatable("elysiumlogistics.drawer.empty",
                        String.format("%,d", store.drawer().room()));
            } else {
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
                line = Component.translatable("elysiumlogistics.drawer.holding",
                        said.toString(), String.format("%,d", store.drawer().room()));
            }
            // What a key or a seal has made of it, since neither shows on the
            // front and both change what it does with the next thing offered.
            if (store.locked()) {
                line.append(" ").append(Component.translatable("elysiumlogistics.drawer.locked"));
            }
            if (store.voiding()) {
                line.append(" ").append(Component.translatable("elysiumlogistics.drawer.voids"));
            }
            return line;
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
