package com.elysium.enchanting.menu;

import com.elysium.enchanting.block.ScriptoriumBlockEntity;
import com.elysium.enchanting.registry.EnchantingMenus;
import com.elysium.enchanting.rules.LibraryRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Optional;

/**
 * The Scriptorium's menu: file a book, or buy one back.
 *
 * <h2>Two buttons and a list</h2>
 *
 * Filing is unconditional — any enchanted book in the slot goes in, unless the
 * shelf is full, in which case nothing is taken. Buying back needs a plain
 * book in the slot, a line selected, and the pages and experience to pay for
 * it. The level bought is always the best the pages cover, because a player
 * who wanted a worse one can simply take it out again later.
 */
public class ScriptoriumMenu extends AbstractContainerMenu {

    public static final int INPUT_X = 15;
    public static final int INPUT_Y = 58;
    public static final int OUTPUT_X = 143;
    public static final int OUTPUT_Y = 58;
    public static final int INVENTORY_Y = 84;

    /** What the screen reads out of the container data. */
    public static final int SELECTED = 0;
    public static final int PAGES = 1;
    public static final int LEVEL = 2;
    public static final int PRICE = 3;
    public static final int EXPERIENCE = 4;
    public static final int CAPACITY = 5;
    public static final int FILED = 6;
    public static final int READOUT = 7;

    /** The buttons, in the order the screen sends them. */
    public static final int PREVIOUS = 0;
    public static final int NEXT = 1;
    public static final int FILE = 2;
    public static final int BUY = 3;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player owner;
    private final ContainerData readout = new SimpleContainerData(READOUT);
    private int selected;

    private final Container books = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ScriptoriumMenu.this.slotsChanged(this);
        }
    };

    public ScriptoriumMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public ScriptoriumMenu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
        super(EnchantingMenus.SCRIPTORIUM.get(), id);
        this.access = access;
        this.pos = pos;
        this.owner = inventory.player;
        addSlot(new Slot(books, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK);
            }
        });
        addSlot(new Slot(books, 1, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
    }

    public int number(int which) {
        return readout.get(which);
    }

    /** The shelf this menu is looking at, on whichever side is asking. */
    public ScriptoriumBlockEntity shelf(Level level) {
        BlockEntity entity = level == null ? null : level.getBlockEntity(pos);
        return entity instanceof ScriptoriumBlockEntity shelf ? shelf : null;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Which line the player has selected, by the shelf's own fixed order. */
    public int selected() {
        return readout.get(SELECTED);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, at) -> {
            refresh(level);
            broadcastChanges();
        });
        super.slotsChanged(container);
    }

    private void refresh(Level level) {
        ScriptoriumBlockEntity shelf = shelf(level);
        if (shelf == null) {
            return;
        }
        List<String> filed = shelf.filed();
        if (filed.isEmpty()) {
            selected = 0;
        } else {
            selected = Math.floorMod(selected, filed.size());
        }
        readout.set(SELECTED, selected);
        readout.set(FILED, filed.size());
        readout.set(CAPACITY, shelf.capacity());
        if (filed.isEmpty()) {
            readout.set(PAGES, 0);
            readout.set(LEVEL, 0);
            readout.set(PRICE, 0);
            readout.set(EXPERIENCE, 0);
            return;
        }
        String chosen = filed.get(selected);
        int pages = shelf.pagesOf(chosen);
        int ceiling = maxLevel(level, chosen);
        int best = LibraryRules.highest(pages, ceiling);
        readout.set(PAGES, pages);
        readout.set(LEVEL, best);
        readout.set(PRICE, best == 0 ? 0 : LibraryRules.cost(best));
        readout.set(EXPERIENCE, best == 0 ? 0 : LibraryRules.experience(best));
    }

    /** What the enchantment's own file says it may reach. */
    private int maxLevel(Level level, String id) {
        ResourceKey<Enchantment> key = ScriptoriumBlockEntity.keyOf(id);
        if (key == null || level == null) {
            return 1;
        }
        Optional<Holder.Reference<Enchantment>> found =
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        return found.map(holder -> holder.value().getMaxLevel()).orElse(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        access.execute((level, at) -> {
            ScriptoriumBlockEntity shelf = shelf(level);
            if (shelf == null) {
                return;
            }
            switch (button) {
                case PREVIOUS -> selected--;
                case NEXT -> selected++;
                case FILE -> file(shelf);
                case BUY -> buy(level, shelf, player);
                default -> {
                    return;
                }
            }
            refresh(level);
            broadcastChanges();
        });
        return true;
    }

    /**
     * Every enchantment on the book in the slot, filed.
     *
     * All or nothing: a book with two enchantments on it where the shelf has
     * room for one would otherwise be half-eaten, and a player cannot see that
     * happen.
     */
    private void file(ScriptoriumBlockEntity shelf) {
        ItemStack book = books.getItem(0);
        if (!book.is(Items.ENCHANTED_BOOK)) {
            return;
        }
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(book);
        if (stored.isEmpty()) {
            return;
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            String id = ScriptoriumBlockEntity.idOf(entry.getKey());
            if (id.isEmpty()
                    || !LibraryRules.room(shelf.pagesOf(id), entry.getIntValue(),
                    shelf.capacity())) {
                return;
            }
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            shelf.file(ScriptoriumBlockEntity.idOf(entry.getKey()), entry.getIntValue());
        }
        books.removeItem(0, 1);
    }

    private void buy(Level level, ScriptoriumBlockEntity shelf, Player player) {
        List<String> filed = shelf.filed();
        if (filed.isEmpty() || !books.getItem(0).is(Items.BOOK)
                || !books.getItem(1).isEmpty()) {
            return;
        }
        String chosen = filed.get(Math.floorMod(selected, filed.size()));
        int best = LibraryRules.highest(shelf.pagesOf(chosen), maxLevel(level, chosen));
        if (best <= 0) {
            return;
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && player.experienceLevel < LibraryRules.experience(best)) {
            return;
        }
        ResourceKey<Enchantment> key = ScriptoriumBlockEntity.keyOf(chosen);
        Optional<Holder.Reference<Enchantment>> enchantment = key == null ? Optional.empty()
                : level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        if (enchantment.isEmpty() || !shelf.take(chosen, best)) {
            return;
        }
        ItemStack copy = new ItemStack(Items.ENCHANTED_BOOK);
        copy.enchant(enchantment.get(), best);
        books.setItem(1, copy);
        books.removeItem(0, 1);
        if (!free) {
            player.giveExperienceLevels(-LibraryRules.experience(best));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, this.slots.size(), true)) {
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
        access.execute((level, at) -> clearContainer(player, books));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
