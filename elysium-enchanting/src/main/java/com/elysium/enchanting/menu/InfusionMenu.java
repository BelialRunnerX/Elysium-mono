package com.elysium.enchanting.menu;

import com.elysium.enchanting.data.EnchantingSurvey;
import com.elysium.enchanting.registry.EnchantingMenus;
import com.elysium.enchanting.rules.EnchantingRules;
import com.elysium.enchanting.rules.InfusionRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The Infusion Basin's menu: a book, a piece of gear, and a price.
 *
 * <h2>Everything is decided before the button</h2>
 *
 * The plan — which enchantments would land, at what level, for what — is
 * worked out whenever a slot changes and is what the screen reads. Pressing
 * the button re-works it and applies it, rather than trusting what the client
 * was told: a menu that acts on numbers it sent to the client is a menu a
 * modified client can talk into anything.
 *
 * <p>The book is consumed. Not because it has to be — the mechanism would work
 * either way — but because a book that survives infusion is a book that
 * enchants every piece of gear a player owns, and then the only question left
 * is how long they are willing to stand there.
 */
public class InfusionMenu extends AbstractContainerMenu {

    public static final int GEAR_X = 15;
    public static final int GEAR_Y = 30;
    public static final int BOOK_X = 15;
    public static final int BOOK_Y = 54;
    public static final int LAPIS_X = 47;
    public static final int LAPIS_Y = 54;
    public static final int INVENTORY_Y = 84;

    /** What the screen reads out of the container data. */
    public static final int ETERNA = 0;
    public static final int ARCANA = 1;
    public static final int RECTIFICATION = 2;
    public static final int HIGHEST = 3;
    public static final int COST = 4;
    public static final int LAPIS = 5;
    public static final int STATUS = 6;
    public static final int READOUT = 7;

    /** Why the basin will not do it, in the order the screen asks. */
    public static final int NOTHING = 0;
    public static final int READY = 1;
    public static final int TOO_WEAK = 2;
    public static final int TOO_RARE = 3;
    public static final int WRONG_ITEM = 4;
    public static final int CONFLICT = 5;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player owner;
    private final ContainerData readout = new SimpleContainerData(READOUT);

    private final Container inputs = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            InfusionMenu.this.slotsChanged(this);
        }
    };

    /** One enchantment the basin would put on the gear, and how high. */
    private record Landing(Holder<Enchantment> enchantment, int level) {
    }

    /** What would happen if the button were pressed now. */
    private record Plan(List<Landing> landings, int cost, int lapis, int status) {

        static Plan nothing(int status) {
            return new Plan(List.of(), 0, 0, status);
        }
    }

    public InfusionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public InfusionMenu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
        super(EnchantingMenus.INFUSION_BASIN.get(), id);
        this.access = access;
        this.pos = pos;
        this.owner = inventory.player;
        addSlot(new Slot(inputs, 0, GEAR_X, GEAR_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputs, 1, BOOK_X, BOOK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.ENCHANTED_BOOK);
            }
        });
        addSlot(new Slot(inputs, 2, LAPIS_X, LAPIS_Y) {
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
    }

    /** The room's numbers, as tenths so they survive a ContainerData's ints. */
    public float stat(int which) {
        return readout.get(which) / 10.0F;
    }

    public int number(int which) {
        return readout.get(which);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, at) -> {
            EnchantingRules.Stats stats = EnchantingSurvey.survey(level, pos, owner);
            readout.set(ETERNA, Math.round(stats.eterna() * 10.0F));
            readout.set(ARCANA, Math.round(stats.arcana() * 10.0F));
            readout.set(RECTIFICATION, Math.round(stats.rectification() * 10.0F));
            readout.set(HIGHEST, InfusionRules.highestLevel(stats.eterna()));
            Plan plan = plan(stats);
            readout.set(COST, plan.cost());
            readout.set(LAPIS, plan.lapis());
            readout.set(STATUS, plan.status());
            broadcastChanges();
        });
        super.slotsChanged(container);
    }

    /**
     * What the basin would do, and why it would not.
     *
     * The reasons are separated rather than collapsed into "no", because a
     * player looking at a bench that will not work needs to know whether to
     * build a better room, find a different book or use a different tool.
     */
    private Plan plan(EnchantingRules.Stats stats) {
        ItemStack gear = inputs.getItem(0);
        ItemStack book = inputs.getItem(1);
        if (gear.isEmpty() || book.isEmpty()) {
            return Plan.nothing(NOTHING);
        }
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(book);
        if (stored.isEmpty()) {
            return Plan.nothing(NOTHING);
        }
        ItemEnchantments already = EnchantmentHelper.getEnchantmentsForCrafting(gear);
        List<Landing> landings = new ArrayList<>();
        int status = NOTHING;
        int cost = 0;
        int lapis = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.value().canEnchant(gear)) {
                status = Math.max(status, WRONG_ITEM);
                continue;
            }
            if (!InfusionRules.allows(stats, enchantment.value().getWeight())) {
                status = Math.max(status, TOO_RARE);
                continue;
            }
            boolean clashes = false;
            for (Holder<Enchantment> worn : already.keySet()) {
                if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {
                    clashes = true;
                    break;
                }
            }
            if (clashes) {
                status = Math.max(status, CONFLICT);
                continue;
            }
            int wanted = InfusionRules.levelFor(entry.getIntValue(), stats.eterna(),
                    enchantment.value().getMaxLevel());
            if (wanted <= already.getLevel(enchantment)) {
                // The gear already has it at least this high. Not a failure —
                // a room too weak to improve on what is already there.
                status = Math.max(status, TOO_WEAK);
                continue;
            }
            landings.add(new Landing(enchantment, wanted));
            cost += InfusionRules.cost(wanted, stats.rectification());
            lapis += InfusionRules.lapis(wanted);
        }
        if (landings.isEmpty()) {
            return Plan.nothing(status);
        }
        return new Plan(landings, cost, lapis, READY);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != 0) {
            return false;
        }
        access.execute((level, at) -> {
            EnchantingRules.Stats stats = EnchantingSurvey.survey(level, pos, owner);
            // Worked out again here rather than read out of the readout: the
            // readout is what the client was told, and a client can be told
            // anything.
            Plan plan = plan(stats);
            if (plan.status() != READY) {
                return;
            }
            boolean free = player.getAbilities().instabuild;
            if (!free && (player.experienceLevel < plan.cost()
                    || inputs.getItem(2).getCount() < plan.lapis())) {
                return;
            }
            ItemStack gear = inputs.getItem(0);
            for (Landing landing : plan.landings()) {
                gear.enchant(landing.enchantment(), landing.level());
            }
            if (!free) {
                player.onEnchantmentPerformed(gear, plan.cost());
                inputs.removeItem(2, plan.lapis());
            }
            inputs.setItem(0, gear);
            inputs.removeItem(1, 1);
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
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.ENCHANTED_BOOK)) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.LAPIS_LAZULI)) {
            if (!moveItemStackTo(stack, 2, 3, false)) {
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
