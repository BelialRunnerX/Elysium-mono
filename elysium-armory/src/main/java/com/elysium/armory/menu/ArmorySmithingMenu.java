package com.elysium.armory.menu;

import com.elysium.armory.item.ArmoryBlueprintItem;
import com.elysium.armory.registry.ArmoryItems;
import com.elysium.armory.registry.ArmoryMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gear Smithing Table's menu: paper in, a blueprint for the chosen part
 * out.
 *
 * <p>The choice is a menu button rather than a packet of our own — vanilla
 * already has one, and a screen that uses it cannot desynchronise from the
 * server's idea of what is selected.
 */
public class ArmorySmithingMenu extends AbstractContainerMenu {

    /** How many blueprint papers one plan costs; a template costs one too and is used up when crafting. */
    public static final int PAPER_PER_PLAN = 1;

    public static final int INPUT_X = 30;
    public static final int INPUT_Y = 47;
    public static final int OUTPUT_X = 130;
    public static final int OUTPUT_Y = 47;
    public static final int INVENTORY_Y = 84;

    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArmorySmithingMenu.this.slotsChanged(this);
        }
    };
    private final Container output = new SimpleContainer(1);
    private final List<String> parts;
    private int selected = -1;

    public ArmorySmithingMenu(int id, Inventory inventory, List<String> parts) {
        super(ArmoryMenus.SMITHING.get(), id);
        this.parts = List.copyOf(parts);
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ArmoryItems.BLUEPRINT_PAPER.get());
            }
        });
        addSlot(new Slot(output, 0, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                input.removeItem(0, PAPER_PER_PLAN);
                super.onTake(player, stack);
                update();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    /** Client side, from the opening packet. */
    public ArmorySmithingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, read(buf));
    }

    private static List<String> read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(buf.readUtf());
        }
        return out;
    }

    public List<String> parts() {
        return parts;
    }

    public int selected() {
        return selected;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= parts.size()) {
            return false;
        }
        selected = id;
        update();
        return true;
    }

    private void update() {
        ItemStack paper = input.getItem(0);
        if (selected < 0 || paper.getCount() < PAPER_PER_PLAN) {
            output.setItem(0, ItemStack.EMPTY);
        } else {
            ArmoryBlueprintItem blueprint = (ArmoryBlueprintItem) ArmoryItems.BLUEPRINT.get();
            output.setItem(0, blueprint.of(parts.get(selected)));
        }
        broadcastChanges();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        update();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            clearContainer(player, input);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean moved = index < 2 ? moveItemStackTo(stack, 2, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
