package com.elysium.space.freight;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.FreightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A freight terminal: a chest that can be aimed at another world.
 *
 * <h2>Why it is a chest</h2>
 *
 * Because a logistics system that cannot be automated is a chore rather than a
 * system, and the way to be automatable in Minecraft is to be a container that
 * everything already knows how to fill. Twenty-seven slots, a hopper can feed
 * it, a pipe can feed it, and the screen that opens is the one a player has
 * opened ten thousand times.
 *
 * <p>Its own half is the tank of rocket fuel and the address it is pointed at.
 */
public class FreightTerminalBlockEntity extends BaseContainerBlockEntity
        implements WorldlyContainer {

    private NonNullList<ItemStack> held =
            NonNullList.withSize(FreightRules.SLOTS, ItemStack.EMPTY);

    /** Rocket fuel, and only rocket fuel: this is not a tank to store things in. */
    private final FluidTank fuel = new FluidTank(FreightRules.TANK_MB,
            stack -> stack.getFluid().isSame(SpaceFluids.FUEL.source.get()));

    public FreightTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceBlockEntities.FREIGHT_TERMINAL.get(), pos, state);
    }

    public FluidTank fuelTank() {
        return fuel;
    }

    /** How many of its slots have something in them, which is what a run costs by. */
    public int stacksAboard() {
        int full = 0;
        for (ItemStack stack : held) {
            if (!stack.isEmpty()) {
                full++;
            }
        }
        return full;
    }

    public boolean empty() {
        return stacksAboard() == 0;
    }

    /** Takes everything out, for a rocket that is leaving. */
    public java.util.List<ItemStack> unload() {
        java.util.List<ItemStack> cargo = new java.util.ArrayList<>();
        for (int slot = 0; slot < held.size(); slot++) {
            ItemStack stack = held.get(slot);
            if (!stack.isEmpty()) {
                cargo.add(stack.copy());
                held.set(slot, ItemStack.EMPTY);
            }
        }
        setChanged();
        return cargo;
    }

    /**
     * Puts down what a rocket brought, and says what would not fit.
     *
     * Whatever does not fit stays aboard rather than being dropped on the
     * floor or deleted: the rocket holds and tries again. A player who comes
     * back to a full terminal should find their cargo waiting, not scattered.
     */
    public java.util.List<ItemStack> land(java.util.List<ItemStack> cargo) {
        java.util.List<ItemStack> left = new java.util.ArrayList<>();
        for (ItemStack stack : cargo) {
            ItemStack rest = stack.copy();
            for (int slot = 0; slot < held.size() && !rest.isEmpty(); slot++) {
                ItemStack there = held.get(slot);
                if (there.isEmpty()) {
                    held.set(slot, rest.copy());
                    rest = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(there, rest)) {
                    int room = Math.min(there.getMaxStackSize(), getMaxStackSize())
                            - there.getCount();
                    int moved = Math.min(room, rest.getCount());
                    if (moved > 0) {
                        there.grow(moved);
                        rest.shrink(moved);
                    }
                }
            }
            if (!rest.isEmpty()) {
                left.add(rest);
            }
        }
        setChanged();
        return left;
    }

    // --- the container ------------------------------------------------

    @Override
    protected NonNullList<ItemStack> getItems() {
        return held;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        held = items;
    }

    @Override
    public int getContainerSize() {
        return FreightRules.SLOTS;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.elysiumspace.freight_terminal");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return com.elysium.lib.menu.ElysiumChests.threeRows(id, inventory, this);
    }

    /**
     * Which sides a hopper may use.
     *
     * All of them, both ways. A terminal is a place goods pass through, and a
     * rule about which face a pipe has to be on would be a rule a player has
     * to look up for no gain.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[FreightRules.SLOTS];
        for (int slot = 0; slot < all.length; slot++) {
            all[slot] = slot;
        }
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    // --- saving --------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        held = NonNullList.withSize(FreightRules.SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, held, lookup);
        fuel.readFromNBT(lookup, tag.getCompound("fuel"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        ContainerHelper.saveAllItems(tag, held, lookup);
        tag.put("fuel", fuel.writeToNBT(lookup, new CompoundTag()));
    }

    /** The one-line answer to "what is this doing". */
    public Component status(FreightNetwork network) {
        FreightNetwork.Terminal me = new FreightNetwork.Terminal(
                level == null ? net.minecraft.resources.ResourceLocation
                        .withDefaultNamespace("overworld")
                        : level.dimension().location(), worldPosition);
        return Component.translatable("elysiumspace.freight.status",
                stacksAboard(), FreightRules.SLOTS,
                fuel.getFluidAmount(), FreightRules.TANK_MB,
                network.inFlightFrom(me), network.inbound(me).size());
    }
}
