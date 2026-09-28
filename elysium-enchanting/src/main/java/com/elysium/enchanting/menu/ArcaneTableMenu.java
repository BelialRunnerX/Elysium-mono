package com.elysium.enchanting.menu;

import com.elysium.enchanting.data.EnchantingSurvey;
import com.elysium.enchanting.registry.EnchantingMenus;
import com.elysium.enchanting.rules.EnchantingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The Arcane Table's menu: what the room is worth, and what it will sell you.
 *
 * <h2>The three offers are the same three</h2>
 *
 * Seeded from the item and the room, so opening and closing the screen does
 * not reroll them. A table that rerolled on every open would make the clue
 * system pointless — a player would simply keep looking until they liked what
 * they saw, and the decision the clues exist to inform would not be one.
 */
public class ArcaneTableMenu extends AbstractContainerMenu {

    public static final int ITEM_X = 15;
    public static final int ITEM_Y = 64;
    public static final int LAPIS_X = 35;
    public static final int LAPIS_Y = 64;
    // The upper half was 52 pixels for four stat lines, two slots and three offers,
    // and the client stage of 27 September 2026 photographed them drawn over one
    // another: sixteen more pixels above the inventory, like every crowded screen.
    public static final int INVENTORY_Y = 100;

    /** What the screen reads out of the container data. */
    public static final int ETERNA = 0;
    public static final int QUANTA = 1;
    public static final int ARCANA = 2;
    public static final int RECTIFICATION = 3;
    public static final int FIRST_LEVEL = 4;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player owner;
    private final ContainerData readout = new SimpleContainerData(FIRST_LEVEL
            + EnchantingRules.OFFERS);
    private final List<List<EnchantmentInstance>> offers = new ArrayList<>();

    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArcaneTableMenu.this.slotsChanged(this);
        }
    };

    public ArcaneTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public ArcaneTableMenu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
        super(EnchantingMenus.ARCANE_TABLE.get(), id);
        this.access = access;
        this.pos = pos;
        this.owner = inventory.player;
        addSlot(new Slot(inputs, 0, ITEM_X, ITEM_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputs, 1, LAPIS_X, LAPIS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });
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
        for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
            offers.add(List.of());
        }
    }

    /** The room's numbers, as tenths so they survive a ContainerData's ints. */
    public float stat(int which) {
        return readout.get(which) / 10.0F;
    }

    /** The level the offer in this slot would enchant at, or 0 when there is none. */
    public int offeredLevel(int slot) {
        return readout.get(FIRST_LEVEL + Math.max(0, Math.min(EnchantingRules.OFFERS - 1, slot)));
    }

    /** What the table is willing to tell the player about an offer. */
    public List<EnchantmentInstance> clues(int slot) {
        if (slot < 0 || slot >= offers.size()) {
            return List.of();
        }
        List<EnchantmentInstance> all = offers.get(slot);
        int shown = Math.min(all.size(), EnchantingRules.clues(current(), slot));
        return all.subList(0, shown);
    }

    private EnchantingRules.Stats current() {
        return new EnchantingRules.Stats(stat(ETERNA), stat(QUANTA), stat(ARCANA),
                stat(RECTIFICATION));
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, at) -> {
            EnchantingRules.Stats stats = EnchantingSurvey.survey(level, pos, owner);
            readout.set(ETERNA, Math.round(stats.eterna() * 10.0F));
            readout.set(QUANTA, Math.round(stats.quanta() * 10.0F));
            readout.set(ARCANA, Math.round(stats.arcana() * 10.0F));
            readout.set(RECTIFICATION, Math.round(stats.rectification() * 10.0F));
            ItemStack target = inputs.getItem(0);
            for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
                offers.set(slot, List.of());
                readout.set(FIRST_LEVEL + slot, 0);
            }
            if (target.isEmpty() || !target.isEnchantable() || stats.isEmpty()) {
                broadcastChanges();
                return;
            }
            // Seeded from what is on the table and the room around it: the
            // same three offers until something changes, so the clues are
            // worth reading.
            long seed = (long) target.getDescriptionId().hashCode() * 31L
                    + Math.round(stats.eterna() * 100.0F)
                    + Math.round(stats.arcana() * 7.0F) * 131L
                    + pos.asLong();
            for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
                int offered = EnchantingRules.offerLevel(stats, slot);
                readout.set(FIRST_LEVEL + slot, offered);
                offers.set(slot, roll(offered, stats, target, seed + slot));
            }
            broadcastChanges();
        });
        super.slotsChanged(container);
    }

    /**
     * What an offer contains.
     *
     * Vanilla's own selection, given a level this mod worked out — so every
     * enchantment any mod has registered is eligible without this module
     * knowing they exist. Arcana then removes what the room was not built to
     * reach.
     */
    private List<EnchantmentInstance> roll(int level, EnchantingRules.Stats stats,
                                           ItemStack target, long seed) {
        RandomSource random = RandomSource.create(seed);
        HolderSet<Enchantment> allowed = owner.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(named -> (HolderSet<Enchantment>) named)
                .orElse(HolderSet.direct());
        List<EnchantmentInstance> rolled = EnchantmentHelper.selectEnchantment(
                random, target, level, allowed.stream());
        List<EnchantmentInstance> out = new ArrayList<>();
        for (EnchantmentInstance instance : rolled) {
            if (EnchantingRules.available(stats, instance.enchantment.value().getWeight())) {
                out.add(instance);
            }
        }
        return out;
    }

    @Override
    public boolean clickMenuButton(Player player, int slot) {
        if (slot < 0 || slot >= EnchantingRules.OFFERS) {
            return false;
        }
        ItemStack target = inputs.getItem(0);
        List<EnchantmentInstance> chosen = offers.get(slot);
        int cost = EnchantingRules.cost(slot);
        if (target.isEmpty() || chosen.isEmpty()) {
            return false;
        }
        if (!player.getAbilities().instabuild
                && (player.experienceLevel < cost || inputs.getItem(1).getCount() < cost)) {
            return false;
        }
        access.execute((level, at) -> {
            if (!player.getAbilities().instabuild) {
                player.onEnchantmentPerformed(target, cost);
                inputs.removeItem(1, cost);
            }
            EnchantingRules.Stats stats = current();
            // Quanta has its say here rather than when the offer was made: a
            // player buys a level and finds out what it was worth, which is
            // the whole reason rectification is something to build for.
            int rolled = EnchantingRules.rolledLevel(stats, EnchantingRules.offerLevel(stats, slot),
                    level.random::nextDouble);
            List<EnchantmentInstance> applied = rolled == EnchantingRules.offerLevel(stats, slot)
                    ? chosen : roll(rolled, stats, target, level.random.nextLong());
            for (EnchantmentInstance instance : applied) {
                target.enchant(instance.enchantment, instance.level);
            }
            inputs.setItem(0, target);
            slotsChanged(inputs);
        });
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
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.LAPIS_LAZULI)) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
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
        access.execute((level, at) -> clearContainer(player, inputs));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
