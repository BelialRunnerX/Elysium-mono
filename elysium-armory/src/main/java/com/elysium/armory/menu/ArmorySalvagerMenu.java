package com.elysium.armory.menu;

import com.elysium.armory.craft.ArmorySalvage;
import com.elysium.armory.registry.ArmoryMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Salvager's menu: a piece of gear in, its materials out.
 *
 * <h2>A button, not a result slot</h2>
 *
 * Salvage yields several stacks at once, and a result slot that holds several
 * stacks is a lie about how taking one works. So the player presses a button:
 * the server rolls once, consumes the piece, and fills the output grid. That
 * also means the roll cannot be retried by closing the screen, which is what a
 * preview would have allowed.
 */
public class ArmorySalvagerMenu extends AbstractContainerMenu {

    /** How many stacks a salvage can produce: the biggest gear has six parts, plus essence. */
    public static final int OUTPUTS = 8;

    public static final int INPUT_X = 26;
    public static final int INPUT_Y = 35;
    public static final int OUTPUT_X = 80;
    public static final int OUTPUT_Y = 17;
    public static final int INVENTORY_Y = 84;

    /** The button id the screen sends when the lever is pulled. */
    public static final int SALVAGE = 0;

    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArmorySalvagerMenu.this.slotsChanged(this);
        }
    };
    private final Container output = new SimpleContainer(OUTPUTS);
    /** 0 when there is nothing to take apart, 1 when there is: read by the screen. */
    private final ContainerData ready = new SimpleContainerData(1);

    public ArmorySalvagerMenu(int id, Inventory inventory) {
        super(ArmoryMenus.SALVAGER.get(), id);
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ArmorySalvage.canSalvage(stack);
            }
        });
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                addSlot(new Slot(output, row * 4 + column,
                        OUTPUT_X + column * 18, OUTPUT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18,
                        INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(ready);
    }

    /** Whether the piece in the slot has anything in it worth taking. */
    public boolean ready() {
        return ready.get(0) != 0;
    }

    @Override
    public void slotsChanged(Container container) {
        ready.set(0, ArmorySalvage.canSalvage(input.getItem(0)) ? 1 : 0);
        super.slotsChanged(container);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != SALVAGE || player.level().isClientSide()) {
            return false;
        }
        ItemStack gear = input.getItem(0);
        if (!ArmorySalvage.canSalvage(gear) || !outputEmpty()) {
            return false;
        }
        List<ItemStack> results = ArmorySalvage.salvage(gear, player.getRandom());
        input.setItem(0, ItemStack.EMPTY);
        for (ItemStack result : results) {
            if (!place(result)) {
                // Nowhere left on the bench: it goes to the player rather than
                // being deleted, because it was their metal.
                player.getInventory().placeItemBackInInventory(result);
            }
        }
        slotsChanged(input);
        broadcastChanges();
        return true;
    }

    private boolean outputEmpty() {
        for (int slot = 0; slot < OUTPUTS; slot++) {
            if (!output.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean place(ItemStack stack) {
        for (int slot = 0; slot < OUTPUTS; slot++) {
            if (output.getItem(slot).isEmpty()) {
                output.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = 1 + OUTPUTS;
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
        // Nothing on a bench is kept when the player walks away.
        clearContainer(player, input);
        clearContainer(player, output);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
