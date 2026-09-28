package com.elysium.logistics.pack;

import com.elysium.logistics.rules.PackRules;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One page of a pack, in the game's own chest screen.
 *
 * <h2>Not a new screen</h2>
 *
 * The menu type is the game's generic nine-wide chest of one to six rows, so
 * the client opens the chest screen it already has and there is nothing here
 * to theme. What this class adds is on the server only: slots laid out exactly
 * where the chest screen expects them, which refuse a pack, and a lock on the
 * slot the open bag itself is sitting in.
 *
 * <h2>Pages</h2>
 *
 * The largest chest screen shows fifty-four slots and a vault fitted out holds
 * more than twice that. So a pack is shown a page at a time
 * ({@link PackRules#shownOn}) and each opening turns the page; capacity stays
 * the sum of what is fitted, which is the thing the rules exist to keep true.
 */
public class PackMenu extends AbstractContainerMenu {

    /** The game's chest menus, by how many rows they have. */

    /** The chest menu that shows this many rows. */
    public static MenuType<?> typeFor(int rows) {
        // The library's chest type for this many rows, so the kit's box screen opens
        // rather than the game's grey one (the client stage, 27 September 2026).
        return com.elysium.lib.menu.ElysiumChests.type(Math.max(1, Math.min(com.elysium.lib.menu.ElysiumChests.MOST_ROWS, rows)));
    }

    private final Page page;
    private final int rows;
    private final int locked;

    /**
     * @param locked the player-inventory index of the bag being looked into,
     *               which may not be moved while it is open, or -1 for a crate
     */
    public PackMenu(int id, Inventory inventory, PackStore store, int pageNumber,
                    Level level, BooleanSupplier still, int locked) {
        this(id, inventory, store, pageNumber, level, still, locked,
                PackRules.rowsOn(store.capacity(), pageNumber));
    }

    private PackMenu(int id, Inventory inventory, PackStore store, int pageNumber,
                     Level level, BooleanSupplier still, int locked, int rows) {
        super(typeFor(rows), id);
        this.rows = rows;
        this.locked = locked;
        this.page = new Page(store, PackRules.firstOn(pageNumber),
                PackRules.shownOn(store.capacity(), pageNumber), level, still);
        page.startOpen(inventory.player);
        int drop = (rows - 4) * 18;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Guarded(page, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                addSlot(slotFor(inventory, index, 8 + col * 18, 103 + row * 18 + drop));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(slotFor(inventory, col, 8 + col * 18, 161 + drop));
        }
    }

    private Slot slotFor(Inventory inventory, int index, int x, int y) {
        return index == locked ? new Locked(inventory, index, x, y)
                : new Slot(inventory, index, x, y);
    }

    @Override
    public boolean stillValid(Player player) {
        return page.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // A number key would swap the open bag out from under its own menu.
        if (locked >= 0 && type == ClickType.SWAP && button == locked) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int shown = rows * 9;
            if (index < shown) {
                if (!moveItemStackTo(stack, shown, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, shown, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
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
        page.stopOpen(player);
    }

    // ------------------------------------------------------------------

    /** A pack slot: takes anything a pack may hold, which is anything but a pack. */
    private static final class Guarded extends Slot {

        Guarded(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PackStore.mayHold(stack) && super.mayPlace(stack);
        }
    }

    /** The slot the open bag sits in: nothing comes out and nothing goes in. */
    private static final class Locked extends Slot {

        Locked(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * One page of a pack's slots as a container.
     *
     * <p>Reads the store's list every call rather than holding it, because
     * fitting a part or pressing a pile replaces the list.
     */
    private static final class Page implements Container {

        private final PackStore store;
        private final int first;
        private final int size;
        private final Level level;
        private final BooleanSupplier still;

        Page(PackStore store, int first, int size, Level level, BooleanSupplier still) {
            this.store = store;
            this.first = first;
            this.size = size;
            this.level = level;
            this.still = still;
        }

        private boolean inside(int slot) {
            return slot >= 0 && slot < size && first + slot < store.items().size();
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < size; slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return inside(slot) ? store.items().get(first + slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return inside(slot) ? ContainerHelper.removeItem(store.items(), first + slot, count)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return inside(slot) ? ContainerHelper.takeItem(store.items(), first + slot)
                    : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (inside(slot)) {
                store.items().set(first + slot, stack);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return PackStore.mayHold(stack);
        }

        @Override
        public void setChanged() {
            store.settle(level);
            store.changed();
        }

        @Override
        public boolean stillValid(Player player) {
            return still.getAsBoolean();
        }

        @Override
        public void clearContent() {
            // A pack is never emptied wholesale; everything leaves one slot at a time.
        }
    }
}
