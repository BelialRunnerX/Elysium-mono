package com.elysium.core.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The shape both P7 benches have: two things in, one thing out, and sometimes
 * something handed back.
 *
 * <h2>Why one class</h2>
 *
 * The Gem Cutting Table takes a rune and dust; the Augmenting Table takes gear
 * and a sigil. Everything else about them is the same — a result computed when
 * the inputs change, inputs consumed when it is taken, nothing kept when the
 * player walks away — and two copies of that would be two places for the same
 * bug.
 *
 * <p>What differs is {@link #assemble()} and what each slot will accept, which
 * is what a subclass says.
 */
public abstract class TwoInputBenchMenu extends AbstractContainerMenu {

    public static final int FIRST_X = 26;
    public static final int FIRST_Y = 24;
    public static final int SECOND_X = 26;
    public static final int SECOND_Y = 48;
    public static final int RESULT_X = 110;
    public static final int RESULT_Y = 36;
    public static final int SPARE_X = 140;
    public static final int SPARE_Y = 36;
    public static final int INVENTORY_Y = 84;

    protected final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            TwoInputBenchMenu.this.slotsChanged(this);
        }
    };
    /** The result, and whatever the bench hands back with it. */
    protected final Container outputs = new SimpleContainer(2);
    protected final Player owner;

    protected TwoInputBenchMenu(MenuType<?> type, int id, Inventory inventory) {
        super(type, id);
        this.owner = inventory.player;
        addSlot(new Slot(inputs, 0, FIRST_X, FIRST_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsFirst(stack);
            }
        });
        addSlot(new Slot(inputs, 1, SECOND_X, SECOND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsSecond(stack);
            }
        });
        addSlot(new Slot(outputs, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                pay();
                ItemStack spare = outputs.getItem(1);
                if (!spare.isEmpty()) {
                    // Whatever came back with it is the player's; the bench
                    // does not keep it and does not delete it.
                    player.getInventory().placeItemBackInInventory(spare.copy());
                    outputs.setItem(1, ItemStack.EMPTY);
                }
                super.onTake(player, stack);
                slotsChanged(inputs);
            }
        });
        addSlot(new Slot(outputs, 1, SPARE_X, SPARE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;               // it comes out with the result
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18,
                        INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    protected abstract boolean acceptsFirst(ItemStack stack);

    protected abstract boolean acceptsSecond(ItemStack stack);

    /** What these inputs make, and what comes back with it. */
    protected abstract void assemble();

    /** What taking the result costs. */
    protected abstract void pay();

    @Override
    public void slotsChanged(Container container) {
        outputs.setItem(0, ItemStack.EMPTY);
        outputs.setItem(1, ItemStack.EMPTY);
        assemble();
        super.slotsChanged(container);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, inputs);
        // The result was never the player's until they took it.
        outputs.setItem(0, ItemStack.EMPTY);
        outputs.setItem(1, ItemStack.EMPTY);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
