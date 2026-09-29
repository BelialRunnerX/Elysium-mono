package com.elysium.logistics.block;

import com.elysium.logistics.rules.LogisticRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What a drawer actually holds: a few kinds, and a great many of each.
 *
 * <h2>Why not an ItemStackHandler</h2>
 *
 * Because the whole idea of a drawer is a number larger than a stack. A
 * handler built out of {@code ItemStack}s can hold sixty-four of a thing per
 * slot, so a "drawer" made from one is a chest with fewer slots. This keeps a
 * <em>prototype</em> — what kind of thing is in there, with no count attached
 * — and the count beside it, which is the only arrangement in which "two
 * thousand and forty-eight cobblestone" is a thing a block can say.
 *
 * <h2>One place where a transfer is decided</h2>
 *
 * Every insert and extract goes through {@link LogisticRules#accepted} and
 * {@link LogisticRules#leftOver}, which are a matched pair proved by the
 * harness to add up to what was offered over two hundred thousand random
 * transfers. Items appearing or vanishing is the one bug in a storage mod that
 * ruins a save and leaves no trace, so there is exactly one piece of
 * arithmetic that can be wrong and it is executed on every build.
 */
public class DrawerStore implements IItemHandler {

    private final LogisticRules.Drawer drawer;
    private final ItemStack[] kinds;
    private final int[] counts;
    private final Runnable changed;

    /** Whether a void seal is fitted, so it throws away what will not fit. */
    private boolean voiding;

    /**
     * Whether a key has locked it: it keeps its kinds when emptied, so a
     * hopper cannot re-type it.
     */
    private boolean locked;

    public DrawerStore(LogisticRules.Drawer drawer, Runnable changed) {
        this.drawer = drawer;
        this.kinds = new ItemStack[drawer.kinds()];
        this.counts = new int[drawer.kinds()];
        this.changed = changed;
        java.util.Arrays.fill(kinds, ItemStack.EMPTY);
    }

    public LogisticRules.Drawer drawer() {
        return drawer;
    }

    public boolean voiding() {
        return voiding;
    }

    public boolean locked() {
        return locked;
    }

    public void voiding(boolean now) {
        voiding = now;
        changed.run();
    }

    public void locked(boolean now) {
        locked = now;
        // Unlocked, a slot that has run dry forgets its kind, as it would
        // have the moment it ran dry had it never been locked. Without this an
        // unlocked drawer stays locked to whatever it last held, and says it
        // is empty while it refuses everything else.
        for (int slot = 0; slot < kinds.length; slot++) {
            if (!LogisticRules.keepsKind(locked, counts[slot])) {
                kinds[slot] = ItemStack.EMPTY;
            }
        }
        changed.run();
    }

    /** How many of its slots have a kind, held or kept by the lock. */
    public int kindsKept() {
        int kept = 0;
        for (ItemStack kind : kinds) {
            if (!kind.isEmpty()) {
                kept++;
            }
        }
        return kept;
    }

    /** What kind of thing is in that slot, with no count attached. */
    public ItemStack kindIn(int slot) {
        return kinds[slot];
    }

    /** How many of it. */
    public int countIn(int slot) {
        return counts[slot];
    }

    /** How much more of that slot's kind it will take. */
    public int roomIn(int slot) {
        return Math.max(0, drawer.room() - counts[slot]);
    }

    /** Whether there is nothing in it at all and nothing it insists on. */
    public boolean bare() {
        for (int slot = 0; slot < kinds.length; slot++) {
            if (counts[slot] > 0 || (locked && !kinds[slot].isEmpty())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getSlots() {
        return kinds.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (kinds[slot].isEmpty() || counts[slot] <= 0) {
            return ItemStack.EMPTY;
        }
        return kinds[slot].copyWithCount(counts[slot]);
    }

    @Override
    public int getSlotLimit(int slot) {
        return drawer.room();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return kinds[slot].isEmpty() || ItemStack.isSameItemSameComponents(kinds[slot], stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!kinds[slot].isEmpty()
                && !ItemStack.isSameItemSameComponents(kinds[slot], stack)) {
            return stack;
        }
        if (kinds[slot].isEmpty() && locked) {
            return stack;
        }
        int took = LogisticRules.accepted(stack.getCount(), roomIn(slot));
        int left = LogisticRules.leftOver(stack.getCount(), roomIn(slot));
        // The one place in this module where items may be destroyed, and only
        // because somebody asked for it in as many words.
        if (left > 0 && LogisticRules.voids(voiding)) {
            left = 0;
        }
        if (simulate) {
            return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }
        if (took > 0) {
            if (kinds[slot].isEmpty()) {
                kinds[slot] = stack.copyWithCount(1);
            }
            counts[slot] += took;
            changed.run();
        } else if (left == 0) {
            changed.run();                     // voided: the drawer still changed
        }
        return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
    }

    @Override
    public ItemStack extractItem(int slot, int wanted, boolean simulate) {
        if (wanted <= 0 || counts[slot] <= 0) {
            return ItemStack.EMPTY;
        }
        int given = Math.min(Math.min(wanted, counts[slot]), kinds[slot].getMaxStackSize());
        ItemStack out = kinds[slot].copyWithCount(given);
        if (simulate) {
            return out;
        }
        counts[slot] -= given;
        if (!LogisticRules.keepsKind(locked, counts[slot])) {
            kinds[slot] = ItemStack.EMPTY;
        }
        changed.run();
        return out;
    }

    /**
     * Which slot would take this, preferring one that already holds it.
     *
     * A drawer with two kinds and a hopper on it must not start a second pile
     * of cobblestone in the empty slot while the first one has room, or the
     * second kind can never be stored at all.
     */
    public int slotFor(ItemStack stack) {
        int empty = -1;
        for (int slot = 0; slot < kinds.length; slot++) {
            if (!kinds[slot].isEmpty()
                    && ItemStack.isSameItemSameComponents(kinds[slot], stack)) {
                return slot;
            }
            if (empty < 0 && kinds[slot].isEmpty() && !locked) {
                empty = slot;
            }
        }
        return empty;
    }

    /** Everything in it, for when the block is broken. */
    public java.util.List<ItemStack> spill() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int slot = 0; slot < kinds.length; slot++) {
            int left = counts[slot];
            while (left > 0 && !kinds[slot].isEmpty()) {
                int some = Math.min(left, kinds[slot].getMaxStackSize());
                out.add(kinds[slot].copyWithCount(some));
                left -= some;
            }
        }
        return out;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int slot = 0; slot < kinds.length; slot++) {
            CompoundTag one = new CompoundTag();
            if (!kinds[slot].isEmpty()) {
                one.put("kind", kinds[slot].copyWithCount(1).save(registries));
            }
            one.putInt("count", counts[slot]);
            list.add(one);
        }
        tag.put("slots", list);
        tag.putBoolean("voiding", voiding);
        tag.putBoolean("locked", locked);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < kinds.length; slot++) {
            kinds[slot] = ItemStack.EMPTY;
            counts[slot] = 0;
            if (slot >= list.size()) {
                continue;
            }
            CompoundTag one = list.getCompound(slot);
            if (one.contains("kind")) {
                kinds[slot] = ItemStack.parse(registries, one.getCompound("kind"))
                        .orElse(ItemStack.EMPTY);
            }
            counts[slot] = Math.max(0, Math.min(drawer.room(), one.getInt("count")));
            if (kinds[slot].isEmpty()) {
                counts[slot] = 0;
            }
        }
        voiding = tag.getBoolean("voiding");
        locked = tag.getBoolean("locked");
    }
}
