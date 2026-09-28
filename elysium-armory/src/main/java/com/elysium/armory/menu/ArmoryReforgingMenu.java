package com.elysium.armory.menu;

import com.elysium.armory.craft.ArmoryReforge;
import com.elysium.armory.property.ArmorySalvageRules;
import com.elysium.armory.registry.ArmoryItems;
import com.elysium.armory.registry.ArmoryMenus;
import com.elysium.lib.tier.ElysiumTiers;
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
 * The Reforging Table's menu: gear and essence in, three futures to pick from.
 *
 * <h2>The same three</h2>
 *
 * The seed is the bench's, set when the inputs change, so the three results
 * stay the same until the player changes something. A reroll that changed
 * every time the screen was reopened would not be a choice — it would be a
 * slot machine with a free lever.
 */
public class ArmoryReforgingMenu extends AbstractContainerMenu {

    public static final int GEAR_X = 20;
    public static final int GEAR_Y = 24;
    public static final int ESSENCE_X = 20;
    public static final int ESSENCE_Y = 50;
    public static final int CHOICE_X = 80;
    public static final int CHOICE_Y = 24;
    public static final int INVENTORY_Y = 84;

    /** What the screen reads: 0 the target rarity, 1 the cost in essence. */
    public static final int TARGET = 0;
    public static final int COST = 1;

    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArmoryReforgingMenu.this.slotsChanged(this);
        }
    };
    private final Container choices = new SimpleContainer(ArmorySalvageRules.CHOICES);
    private final ContainerData readout = new SimpleContainerData(2);
    private final Player owner;
    private long seed;

    public ArmoryReforgingMenu(int id, Inventory inventory) {
        super(ArmoryMenus.REFORGING.get(), id);
        this.owner = inventory.player;
        this.seed = inventory.player.level().getGameTime();
        addSlot(new Slot(inputs, 0, GEAR_X, GEAR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ArmoryReforge.canReforge(stack);
            }
        });
        addSlot(new Slot(inputs, 1, ESSENCE_X, ESSENCE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ArmoryItems.AFFIX_ESSENCE.get());
            }
        });
        for (int index = 0; index < ArmorySalvageRules.CHOICES; index++) {
            final int chosen = index;
            addSlot(new Slot(choices, index, CHOICE_X + index * 22, CHOICE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !getItem().isEmpty() && affordable();
                }

                @Override
                public void onTake(Player player, ItemStack stack) {
                    take(chosen);
                    super.onTake(player, stack);
                }
            });
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
        addDataSlots(readout);
    }

    public int target() {
        return readout.get(TARGET);
    }

    public int cost() {
        return readout.get(COST);
    }

    private boolean affordable() {
        return readout.get(COST) > 0 && inputs.getItem(1).getCount() >= readout.get(COST);
    }

    @Override
    public void slotsChanged(Container container) {
        // A new set of inputs is a new set of futures; the same inputs are the
        // same three, however many times the screen is opened.
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        update();
        super.slotsChanged(container);
    }

    private void update() {
        ItemStack gear = inputs.getItem(0);
        int essence = inputs.getItem(1).getCount();
        int target = ArmoryReforge.targetRarity(essence);
        readout.set(TARGET, target);
        readout.set(COST, target <= 0 ? 0 : ArmoryReforge.cost(target));
        for (int index = 0; index < ArmorySalvageRules.CHOICES; index++) {
            choices.setItem(index, ItemStack.EMPTY);
        }
        if (!ArmoryReforge.canReforge(gear) || target <= 0) {
            return;
        }
        List<ItemStack> rolled = ArmoryReforge.choices(gear, target, ElysiumTiers.of(owner), seed);
        for (int index = 0; index < rolled.size() && index < ArmorySalvageRules.CHOICES; index++) {
            choices.setItem(index, rolled.get(index));
        }
    }

    /** Pays for the chosen result and clears the rest. */
    private void take(int chosen) {
        int cost = readout.get(COST);
        inputs.setItem(0, ItemStack.EMPTY);
        inputs.removeItem(1, cost);
        for (int index = 0; index < ArmorySalvageRules.CHOICES; index++) {
            if (index != chosen) {
                choices.setItem(index, ItemStack.EMPTY);
            }
        }
        slotsChanged(inputs);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = 2 + ArmorySalvageRules.CHOICES;
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        // The take is what a result slot charges for (pay(), the choice's
        // take): a quick-move that skipped it handed the result over for
        // nothing and left the inputs in place (found by hand, 28 Sep 2026).
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, inputs);
        // The choices were never the player's: they are shown, not held.
        for (int index = 0; index < ArmorySalvageRules.CHOICES; index++) {
            choices.setItem(index, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
