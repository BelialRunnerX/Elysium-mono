package com.elysium.lib.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What a machine asks of its own slots.
 *
 * <p>A machine's output slot refuses every insert, so that a hopper cannot
 * fill the room a product needs; the machine itself then cannot ask that
 * slot for room through {@code insertItem} either - the answer is always
 * no, and the machine stands with its input in hand for ever. Twenty
 * machines did, until the live tests fed them (27 September 2026). A machine
 * asks {@link #roomFor} instead, which reads the slot, and fills it with
 * {@code setStackInSlot}.
 */
public final class ElysiumSlots {

    private ElysiumSlots() {
    }

    /** Whether {@code coming} fits in the slot as it is, ignoring who is allowed to insert. */
    public static boolean roomFor(IItemHandler items, int slot, ItemStack coming) {
        if (coming.isEmpty()) {
            return true;
        }
        ItemStack there = items.getStackInSlot(slot);
        int limit = Math.min(items.getSlotLimit(slot), coming.getMaxStackSize());
        if (there.isEmpty()) {
            return coming.getCount() <= limit;
        }
        return ItemStack.isSameItemSameComponents(there, coming)
                && there.getCount() + coming.getCount() <= limit;
    }

    /** The slot's stack with {@code added} on top: {@code added} itself when the slot was empty. */
    public static ItemStack grown(ItemStack there, ItemStack added) {
        if (there.isEmpty()) {
            return added.copy();
        }
        ItemStack out = there.copy();
        out.grow(added.getCount());
        return out;
    }
}
