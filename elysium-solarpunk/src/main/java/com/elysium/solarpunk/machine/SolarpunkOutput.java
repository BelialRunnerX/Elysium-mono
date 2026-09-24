package com.elysium.solarpunk.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * An out-tray, and the block underneath it.
 *
 * <h2>Why this is shared</h2>
 *
 * Two machines now take things out of the world and post them downward — the
 * crop harvester and the tree harvester — and the part they have in common is
 * the part where being wrong destroys a player's property. "Check there is room
 * before you break the block" is one rule, and one rule written twice is two
 * rules that will eventually disagree.
 *
 * <h2>The ordering is the guarantee</h2>
 *
 * {@link #fits} is asked before anything is cut, every time. A machine that cut
 * first and looked for space afterwards would quietly destroy a field or a
 * forest the first time a chest filled while its owner was away — a loss
 * discovered days later with nothing in the world saying what happened.
 */
public final class SolarpunkOutput {

    private SolarpunkOutput() {
    }

    /**
     * Whether every one of these stacks would fit, changing nothing.
     *
     * Simulated against a copy rather than against the tray itself, because
     * {@code insertItem(..., true)} answers one stack at a time and a list of
     * five stacks that each fit on their own may not fit together.
     */
    public static boolean fits(ItemStackHandler tray, List<ItemStack> incoming) {
        ItemStackHandler probe = new ItemStackHandler(tray.getSlots());
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            probe.setStackInSlot(slot, tray.getStackInSlot(slot).copy());
        }
        for (ItemStack stack : incoming) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemHandlerHelper.insertItemStacked(probe, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether there is no room left at all. */
    public static boolean isFull(ItemStackHandler tray) {
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            ItemStack held = tray.getStackInSlot(slot);
            if (held.isEmpty() || held.getCount() < held.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Put a stack in the tray, merging where it can. */
    public static void store(ItemStackHandler tray, ItemStack stack) {
        if (!stack.isEmpty()) {
            ItemHandlerHelper.insertItemStacked(tray, stack, false);
        }
    }

    /**
     * Take one of something out of the tray, if it is there.
     *
     * Used to fund a replant out of stock a previous harvest left behind, so a
     * machine that got no sapling from this tree can still use one from the
     * last.
     *
     * @return whether one was taken
     */
    public static boolean takeOne(ItemStackHandler tray, java.util.function.Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            ItemStack held = tray.getStackInSlot(slot);
            if (!held.isEmpty() && wanted.test(held)) {
                held.shrink(1);
                if (held.isEmpty()) {
                    tray.setStackInSlot(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Empty the tray into whatever sits directly below.
     *
     * Asked of the capability rather than of {@code Container}, so a vanilla
     * chest, a barrel, a hopper and any modded storage in the game all work
     * without this file naming one of them.
     */
    public static void pushDown(Level level, BlockPos pos, ItemStackHandler tray) {
        IItemHandler below = level.getCapability(
                Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);
        if (below == null) {
            return;
        }
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            ItemStack held = tray.getStackInSlot(slot);
            if (held.isEmpty()) {
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(below, held.copy(), false);
            if (left.getCount() != held.getCount()) {
                tray.setStackInSlot(slot, left);
            }
        }
    }

    /** Everything in the tray, for a machine that has just been broken. */
    public static NonNullList<ItemStack> spill(ItemStackHandler tray) {
        NonNullList<ItemStack> spilled = NonNullList.create();
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            ItemStack held = tray.getStackInSlot(slot);
            if (!held.isEmpty()) {
                spilled.add(held);
            }
        }
        return spilled;
    }
}
