package com.elysium.space.machine;

import com.elysium.space.registry.SpaceMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * One menu for every machine: its energy, each tank (fluid, amount,
 * capacity), and its item slots when it has any, over the player's inventory.
 * The numbers ride on ContainerData, which the game sends as 16-bit values, so
 * each int travels as two halves ({@link #PER_INT}).
 */
public class MachineMenu extends AbstractContainerMenu {

    /** Data slots per int value: low and high 16 bits. */
    static final int PER_INT = 2;
    /** Ints before the tanks: energy stored, energy capacity. */
    static final int HEAD = 2;
    /** Ints per tank: amount, capacity, fluid id. */
    static final int PER_TANK = 3;

    // At the right edge: at 80 the energy line ran under it (the client stage, 27 September 2026).
    public static final int SLOT_X = 150;
    public static final int SLOT_Y = 20;
    public static final int INVENTORY_Y = 84;

    public final BlockPos pos;
    public final int tanks;
    private final ContainerData data;
    private final boolean hasSlot;

    /** Server side: data read live from the machine. */
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) {
        this(id, inventory, machine.getBlockPos(), machine.tankList().size(), live(machine), machine.slots());
    }

    /** Client side, from the opening packet. */
    public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readVarInt(), null, buf.readBoolean() ? new ItemStackHandler(1) : null);
    }

    private MachineMenu(int id, Inventory inventory, BlockPos pos, int tanks, ContainerData data,
                        net.neoforged.neoforge.items.IItemHandler items) {
        super(SpaceMenus.MACHINE.get(), id);
        this.pos = pos;
        this.tanks = tanks;
        this.data = data != null ? data : new SimpleContainerData(size(tanks));
        this.hasSlot = items != null;
        if (items != null) {
            addSlot(new SlotItemHandler(items, 0, SLOT_X, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(this.data);
    }

    static int size(int tanks) {
        return (HEAD + tanks * PER_TANK) * PER_INT;
    }

    /** The ints the screen shows, in order: energy, capacity, then per tank amount, capacity, fluid id. */
    static int[] values(MachineBlockEntity m) {
        int[] v = new int[HEAD + m.tankList().size() * PER_TANK];
        v[0] = m.energy().getEnergyStored();
        v[1] = m.energy().getMaxEnergyStored();
        for (int i = 0; i < m.tankList().size(); i++) {
            FluidTank t = m.tankList().get(i);
            v[HEAD + i * PER_TANK] = t.getFluidAmount();
            v[HEAD + i * PER_TANK + 1] = t.getCapacity();
            Fluid fluid = t.getFluid().isEmpty() ? Fluids.EMPTY : t.getFluid().getFluid();
            v[HEAD + i * PER_TANK + 2] = BuiltInRegistries.FLUID.getId(fluid);
        }
        return v;
    }

    private static ContainerData live(MachineBlockEntity m) {
        int count = size(m.tankList().size());
        return new ContainerData() {
            @Override
            public int get(int index) {
                int value = values(m)[index / PER_INT];
                return index % PER_INT == 0 ? value & 0xFFFF : (value >>> 16) & 0xFFFF;
            }

            @Override
            public void set(int index, int value) {
                // read-only: the machine owns its numbers
            }

            @Override
            public int getCount() {
                return count;
            }
        };
    }

    int value(int i) {
        return (data.get(i * PER_INT) & 0xFFFF) | ((data.get(i * PER_INT + 1) & 0xFFFF) << 16);
    }

    public int energy() {
        return value(0);
    }

    public int energyCapacity() {
        return value(1);
    }

    public int amount(int tank) {
        return value(HEAD + tank * PER_TANK);
    }

    public int capacity(int tank) {
        return value(HEAD + tank * PER_TANK + 1);
    }

    public Fluid fluid(int tank) {
        return BuiltInRegistries.FLUID.byId(value(HEAD + tank * PER_TANK + 2));
    }

    public boolean hasSlot() {
        return hasSlot;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof MachineBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!hasSlot) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : moveItemStackTo(stack, 0, 1, false);
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
