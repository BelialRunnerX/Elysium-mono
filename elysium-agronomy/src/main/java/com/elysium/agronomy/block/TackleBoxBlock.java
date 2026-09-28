package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.FishingBlockEntities;
import com.elysium.agronomy.rules.TackleRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A tackle box: a chest that holds tackle and nothing else.
 *
 * <p>It opens on the game's own three-row chest screen, so there is no new
 * screen to draw. What it holds is {@link TackleRules#isTackle}: rods, hooks,
 * bobbers and bait. The slots ask the box, so a stack of cobblestone does
 * not go in by hand, by shift-click or by hopper.
 */
public class TackleBoxBlock extends BaseEntityBlock {

    public static final MapCodec<TackleBoxBlock> CODEC = simpleCodec(TackleBoxBlock::new);

    /** Three rows of nine, which is the game's chest screen. */
    public static final int ROWS = 3;

    public TackleBoxBlock(Properties properties) {
        super(properties);
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
        return new Box(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Box box) {
            player.openMenu(box);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof Box box) {
            Containers.dropContents(level, pos, box);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** Whether a stack is tackle, by the rules' own list. */
    public static boolean holds(ItemStack stack) {
        return TackleRules.isTackle(AgronomyComponents.ourId(stack));
    }

    /** The box's contents. */
    public static class Box extends BaseContainerBlockEntity {

        private NonNullList<ItemStack> items = NonNullList.withSize(ROWS * 9, ItemStack.EMPTY);

        public Box(BlockPos pos, BlockState state) {
            super(FishingBlockEntities.TACKLE_BOX.get(), pos, state);
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("block.elysiumagronomy." + TackleRules.TACKLE_BOX);
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> replaced) {
            this.items = replaced;
        }

        @Override
        public int getContainerSize() {
            return ROWS * 9;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return holds(stack);
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new Menu(id, inventory, this);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ContainerHelper.saveAllItems(tag, items, registries);
        }
    }

    /**
     * The game's three-row chest menu, laid out exactly as the game lays it
     * out so the game's own screen draws it, with slots that ask the box.
     */
    public static class Menu extends AbstractContainerMenu {

        private final Container box;

        public Menu(int id, Inventory inventory, Container box) {
            super(com.elysium.lib.menu.ElysiumChests.type(ROWS), id);     // the kit's box screen, not the game's grey one
            checkContainerSize(box, ROWS * 9);
            this.box = box;
            box.startOpen(inventory.player);
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(box, column + row * 9, 8 + column * 18, 18 + row * 18) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return container.canPlaceItem(getContainerSlot(), stack);
                        }
                    });
                }
            }
            int shift = (ROWS - 4) * 18;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18,
                            103 + row * 18 + shift));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, 8 + column * 18, 161 + shift));
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return box.stillValid(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack moved = ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (slot.hasItem()) {
                ItemStack there = slot.getItem();
                moved = there.copy();
                int inBox = ROWS * 9;
                if (index < inBox) {
                    if (!moveItemStackTo(there, inBox, slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(there, 0, inBox, false)) {
                    return ItemStack.EMPTY;
                }
                if (there.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }
            return moved;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            box.stopOpen(player);
        }
    }
}
