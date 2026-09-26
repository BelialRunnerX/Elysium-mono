package com.elysium.logistics.pack;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.PackRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A crate, a strongbox or a vault: a body from {@link PackRules#BODIES} put
 * down rather than worn, with the same parts and the same rules.
 *
 * <ul>
 *   <li>Right-click to open it, a page at a time when it is bigger than one
 *       screen.
 *   <li>Right-click holding a part to fit it.
 *   <li>Crouch and right-click empty-handed to take the last part out, which
 *       the rules refuse while what is inside would not fit without it.
 * </ul>
 *
 * <p>A hopper, a pipe or a network's storage bus reaches it through the item
 * capability, where a sieve sieves and a seal voids exactly as they do on a
 * bag. Broken, it drops what is in it and what is fitted to it, as a chest
 * does, and then itself.
 */
public class PackBlock extends BaseEntityBlock {

    public static final MapCodec<PackBlock> CODEC =
            simpleCodec(properties -> new PackBlock(properties, placedBodies().get(0)));

    private final PackRules.Body body;

    public PackBlock(Properties properties, PackRules.Body body) {
        super(properties);
        this.body = body;
    }

    /** The bodies that are put down rather than worn, off the roster. */
    public static List<PackRules.Body> placedBodies() {
        List<PackRules.Body> placed = new ArrayList<>();
        for (PackRules.Body one : PackRules.BODIES) {
            if (!one.carried()) {
                placed.add(one);
            }
        }
        return placed;
    }

    public PackRules.Body body() {
        return body;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PackBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (PackItem.partOf(held) == null
                || !(level.getBlockEntity(pos) instanceof PackBlockEntity crate)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(PackItem.refit(crate.store(), player, hand), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PackBlockEntity crate)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PackStore store = crate.store();
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(PackItem.refit(store, player, InteractionHand.MAIN_HAND),
                    true);
            return InteractionResult.CONSUME;
        }
        int room = store.capacity();
        int page = crate.page >= PackRules.pages(room) ? 0 : crate.page;
        crate.page = PackRules.nextPage(room, page);
        ItemStack shown = new ItemStack(state.getBlock().asItem());
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, who) -> new PackMenu(id, inventory, store, page, level,
                        () -> !crate.isRemoved() && who.distanceToSqr(pos.getCenter()) <= 64.0,
                        -1),
                PackItem.titleOf(shown, room, page)));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                            boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PackBlockEntity crate) {
            // Everything inside and everything fitted, on the floor, before the
            // crate itself drops: a container never loses what is in it.
            Containers.dropContents(level, pos, crate.store().items());
            NonNullList<ItemStack> parts = NonNullList.create();
            for (String part : crate.store().partNames()) {
                parts.add(new ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(ElysiumLogistics.MODID, part))));
            }
            Containers.dropContents(level, pos, parts);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** What a placed pack holds: the same store a bag has, kept in the world. */
    public static class PackBlockEntity extends BlockEntity {

        private List<String> parts = new ArrayList<>();
        private NonNullList<ItemStack> items;
        private PackStore store;
        private int page;
        private final IItemHandler handler = new Handler();

        public PackBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.PACK.get(), pos, state);
        }

        private PackRules.Body body() {
            return getBlockState().getBlock() instanceof PackBlock block ? block.body()
                    : placedBodies().get(0);
        }

        /** The store, built on first use from what was saved. */
        public PackStore store() {
            if (store == null) {
                store = new PackStore(body(), parts,
                        items == null ? NonNullList.create() : items, this::setChanged);
            }
            return store;
        }

        /** What a hopper, a pipe or a storage bus reaches. */
        public IItemHandler handler() {
            return handler;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            parts = new ArrayList<>();
            ListTag named = tag.getList("parts", Tag.TAG_STRING);
            for (int at = 0; at < named.size(); at++) {
                parts.add(named.getString(at));
            }
            int size = Math.max(tag.getInt("size"), 0);
            items = NonNullList.withSize(size, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
            page = tag.getInt("page");
            store = null;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            PackStore now = store();
            ListTag named = new ListTag();
            for (String part : now.partNames()) {
                named.add(StringTag.valueOf(part));
            }
            tag.put("parts", named);
            tag.putInt("size", now.items().size());
            ContainerHelper.saveAllItems(tag, now.items(), registries);
            tag.putInt("page", page);
        }

        /** The store as an item handler, sieved and sealed as a bag's intake is. */
        private final class Handler implements IItemHandler {

            @Override
            public int getSlots() {
                return store().items().size();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                NonNullList<ItemStack> all = store().items();
                return slot >= 0 && slot < all.size() ? all.get(slot) : ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return store().insert(stack, simulate, false, level);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return store().extract(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 99;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return PackStore.mayHold(stack);
            }
        }
    }
}
