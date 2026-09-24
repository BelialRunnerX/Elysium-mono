package com.elysium.core.menu;

import com.elysium.core.Elysium;
import com.elysium.core.block.ReforgeTableBlockEntity;
import com.elysium.core.item.ElysiumGearAscension;
import com.elysium.core.item.ElysiumArmorItem;
import com.elysium.core.item.ElysiumReforgeHandler;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.lib.item.ElysiumGear;
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
                return ElysiumGear.is(stack);
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

        // 0. Merge two runes into one of the next tier. Only with the gear slot
        //    empty, so a rune waiting to be socketed is never merged instead.
        if (armor.isEmpty()) {
            mergeRunes(player, material, rune);
            return;
        }

        ElysiumSocketable socketable = ElysiumGear.of(armor);
        if (socketable == null) {
            return;
        }

        // What Engineering research buys: the bench states what is on the table.
        //
        // Server-side and printed to chat rather than added to the tooltip,
        // which would be the obvious place and is the wrong one: a tooltip is
        // drawn on the client, and what a player has researched lives on the
        // server. A tooltip that asked would either be wrong for everyone on a
        // server or need the whole research state synced to every client to
        // answer a cosmetic question.
        teardown(player, armor);

        // 1. Socket a rune. Every kind of Elysium gear can take one.
        if (!rune.isEmpty() && rune.getItem() instanceof ElysiumRuneItem runeItem) {
            if (socketable.socketRune(armor, runeItem.getRune(),
                    com.elysium.lib.item.ElysiumRuneStacks.tierOf(rune))) {
                rune.shrink(1);
                setChanged();
                // Working through Imperial channels is what Favor is for.
                ElysiumStanding.addFavor(player, 2, ElysiumStanding.CAUSE_CRAFT);
                worked(player);
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
                ElysiumStanding.addSuspicion(player, 8, ElysiumStanding.CAUSE_ASCEND);
                worked(player);
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
                ElysiumStanding.addFavor(player, 2, ElysiumStanding.CAUSE_CRAFT);
                worked(player);
            }
        }
    }

    /**
     * One successful operation at this bench, toward Engineering.
     *
     * Called from each of the three branches rather than once at the top,
     * because the top of this method runs on every slot change — including the
     * ones where nothing is craftable and the player is still deciding. Paying
     * for those would make the field advance by fiddling.
     *
     * Repeatable rather than novel: the tenth reroll is still a reroll, and
     * this is two points against a discovery's ten.
     */
    /**
     * Print a piece of gear's full state, once per piece placed.
     *
     * Guarded on the stack itself rather than on a timer: this method runs on
     * every slot change, and a player nudging a rune in and out of the socket
     * slot would otherwise get the same six lines each time.
     */
    /**
     * Two runes of one kind and one tier become one of the next tier.
     *
     * Works with the slots the table already has, because the GUI is signed
     * off: the pair is either two runes stacked in the rune slot, or one there
     * and its twin in the middle slot. The middle slot is used first when it
     * holds a twin, so a player who put one in each gets what they meant.
     *
     * The merged rune takes the rune slot when that slot is left empty, and
     * otherwise goes to the player's inventory — dropping at their feet if
     * that is full, never destroyed.
     */
    private void mergeRunes(Player player, ItemStack material, ItemStack rune) {
        if (rune.isEmpty() || !(rune.getItem() instanceof ElysiumRuneItem)) {
            return;
        }
        int tier = com.elysium.lib.item.ElysiumRuneStacks.tierOf(rune);
        boolean twinInMaterial = !material.isEmpty()
                && material.getItem() == rune.getItem()
                && com.elysium.lib.item.ElysiumRuneStacks.tierOf(material) == tier;
        if (!twinInMaterial && rune.getCount() < 2) {
            return;                         // nothing to merge with
        }
        if (!com.elysium.lib.item.ElysiumRuneTiers.canMerge(tier, tier)) {
            return;                         // at the top
        }

        ItemStack merged = new ItemStack(rune.getItem());
        com.elysium.lib.item.ElysiumRuneStacks.setTier(merged,
                com.elysium.lib.item.ElysiumRuneTiers.merged(tier));

        if (twinInMaterial) {
            material.shrink(1);
            rune.shrink(1);
        } else {
            rune.shrink(2);
        }
        if (rune.isEmpty()) {
            setSlot(ReforgeTableBlockEntity.SLOT_RUNE, merged);
        } else if (!player.getInventory().add(merged)) {
            player.drop(merged, false);
        }
        setChanged();
        ElysiumStanding.addFavor(player, 2, ElysiumStanding.CAUSE_CRAFT);
        worked(player);
    }

    private void teardown(Player player, ItemStack gear) {
        if (gear.isEmpty()
                || !com.elysium.lib.research.ElysiumResearch.has(
                        player, com.elysium.lib.research.ElysiumUnlock.BENCH_TEARDOWN)) {
            return;
        }
        if (ItemStack.isSameItemSameComponents(lastTornDown, gear)) {
            return;
        }
        lastTornDown = gear.copy();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "elysium.research.teardown", gear.getHoverName())
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
        // Built from Elysium's own data, never from the tooltip: tooltip code
        // is client code, and another mod's hook into it (Relics) crashed a
        // dedicated server from here. See ElysiumSocketable.describe.
        //
        // And guarded, because this is a courtesy readout on a button press:
        // nothing it does is worth a server. A failure is logged and the
        // bench carries on with the ascension or reforge the player asked for.
        java.util.List<net.minecraft.network.chat.Component> lines =
                new java.util.ArrayList<>();
        ElysiumSocketable socketable = ElysiumGear.of(gear);
        try {
            if (socketable != null) {
                socketable.describe(gear, lines);
            }
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Elysium: bench readout for {} failed; skipped", gear, failure);
            lines.clear();
        }
        for (net.minecraft.network.chat.Component line : lines) {
            player.displayClientMessage(line, false);
        }
    }

    /** The last piece this bench described, so it describes each one once. */
    private ItemStack lastTornDown = ItemStack.EMPTY;

    private static void worked(Player player) {
        com.elysium.lib.research.ElysiumResearch.credit(
                player, com.elysium.lib.research.ElysiumDeed.WORKED_A_BENCH,
                "reforge_table");
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
