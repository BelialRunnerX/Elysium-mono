package com.elysium.core.menu;

import com.elysium.core.Elysium;
import com.elysium.core.block.ReforgeTableBlockEntity;
import com.elysium.core.item.ElysiumGearAscension;
import com.elysium.core.item.ElysiumArmorItem;
import com.elysium.core.item.ElysiumReforgeHandler;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The workstation menu shared by the Reforge Table, Rune Socket Table and
 * Ascension Forge.
 *
 * Slot 0 takes the Elysium piece, slot 1 the catalyst (or a second matching
 * piece for ascension), slot 2 a rune.
 */
public class ReforgeTableMenu extends AbstractContainerMenu {

    /** Button id sent by the screen's action button. */
    public static final int BUTTON_PERFORM = 0;

    private static final int WORK_SLOTS = ReforgeTableBlockEntity.SLOT_COUNT;

    @Nullable
    private final ReforgeTableBlockEntity blockEntity;
    private final IItemHandler itemHandler;

    /**
     * Client-side constructor. The client gets its own empty handler; slot
     * contents arrive through the menu's normal synchronisation.
     */
    public ReforgeTableMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null);
    }

    public ReforgeTableMenu(int containerId,
                            Inventory playerInventory,
                            @Nullable ReforgeTableBlockEntity blockEntity) {
        super(Elysium.REFORGE_TABLE_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.itemHandler = blockEntity != null ? blockEntity.getItems() : new ItemStackHandler(WORK_SLOTS);

        // Gear slot. Anything socketable goes here — armour, weapons and the
        // area tools. Reforging and ascension still only accept armour, and
        // say so by simply doing nothing.
        this.addSlot(new SlotItemHandler(this.itemHandler, ReforgeTableBlockEntity.SLOT_ARMOR, 30, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ElysiumSocketable;
            }
        });

        // Catalyst / second armour piece
        this.addSlot(new SlotItemHandler(this.itemHandler, ReforgeTableBlockEntity.SLOT_MATERIAL, 80, 35));

        // Rune slot
        this.addSlot(new SlotItemHandler(this.itemHandler, ReforgeTableBlockEntity.SLOT_RUNE, 130, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ElysiumRuneItem;
            }
        });

        // Player inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) {
            return true;
        }
        if (this.blockEntity.getLevel() == null
                || this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) != this.blockEntity) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(this.blockEntity.getBlockPos())) <= 64.0D;
    }

    /**
     * Shift-click transfer. The old implementation returned
     * {@link ItemStack#EMPTY} unconditionally, which makes the client and
     * server disagree about the cursor and can duplicate or eat the stack.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < WORK_SLOTS) {
                if (!this.moveItemStackTo(stack, WORK_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, WORK_SLOTS, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_PERFORM) {
            performAction(player);
            return true;
        }
        return false;
    }

    /**
     * Runs whichever operation the current contents describe:
     * socketing a rune, ascending two matching pieces, or reforging.
     */
    public void performAction(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        ItemStack armor = this.itemHandler.getStackInSlot(ReforgeTableBlockEntity.SLOT_ARMOR);
        ItemStack material = this.itemHandler.getStackInSlot(ReforgeTableBlockEntity.SLOT_MATERIAL);
        ItemStack rune = this.itemHandler.getStackInSlot(ReforgeTableBlockEntity.SLOT_RUNE);

        if (armor.isEmpty() || !(armor.getItem() instanceof ElysiumSocketable socketable)) {
            return;
        }

        // 1. Socket a rune. Every kind of Elysium gear can take one.
        if (!rune.isEmpty() && rune.getItem() instanceof ElysiumRuneItem runeItem) {
            if (socketable.socketRune(armor, runeItem.getRune())) {
                rune.shrink(1);
                setChanged();
                // Working through Imperial channels is what Favor is for.
                ElysiumStanding.addFavor(player, 2);
            }
            return;
        }

        // Reforging and ascension used to be gated to ElysiumArmorItem here,
        // which is why a hammer could be socketed at this table and neither
        // reforged nor ascended at it. Both operations only ever needed
        // ElysiumSocketable, which was checked above.

        // 2. Ascend two matching pieces.
        if (ElysiumGearAscension.canAscend(armor, material)) {
            ItemStack ascended = ElysiumGearAscension.ascend(armor, material);
            if (!ascended.isEmpty()) {
                setSlot(ReforgeTableBlockEntity.SLOT_ARMOR, ascended);
                material.shrink(1);
                setChanged();
                // Ascension is power taken rather than granted. The Code has
                // opinions about that.
                ElysiumStanding.addSuspicion(player, 8);
            }
            return;
        }

        // 3. Reforge with a catalyst.
        if (!material.isEmpty()) {
            ItemStack reforged = ElysiumReforgeHandler.reforge(armor, material, player.level().getRandom(), player);
            if (!reforged.isEmpty()) {
                setSlot(ReforgeTableBlockEntity.SLOT_ARMOR, reforged);
                material.shrink(1);
                setChanged();
                ElysiumStanding.addFavor(player, 2);
            }
        }
    }

    private void setSlot(int slot, ItemStack stack) {
        if (this.itemHandler instanceof ItemStackHandler handler) {
            handler.setStackInSlot(slot, stack);
        }
    }

    private void setChanged() {
        if (this.blockEntity != null) {
            this.blockEntity.setChanged();
        }
        this.broadcastChanges();
    }
}
