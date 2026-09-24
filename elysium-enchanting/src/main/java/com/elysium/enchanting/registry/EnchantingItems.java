package com.elysium.enchanting.registry;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.item.TomeItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The tomes: four one-shot books that an anvil understands.
 *
 * <h2>Four uses of one idea</h2>
 *
 * Each of them is a thing a long-running world eventually needs and vanilla
 * has no answer for: strip an item so a table will look at it again, move a
 * set of enchantments off gear you are done with, narrow a mixed book down to
 * the one line you wanted, or clear the history that has made an item
 * untouchable. None of them is free and none is reusable, so each is a
 * decision rather than a tool.
 */
public final class EnchantingItems {

    private EnchantingItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumEnchanting.MODID);

    /** Everything off an item, so an enchanting table will look at it again. */
    public static final DeferredItem<Item> SCRAPPING_TOME = tome("scrapping_tome");

    /** Everything off an item and onto a book, at the cost of the item. */
    public static final DeferredItem<Item> TRANSCRIPTION_TOME = tome("transcription_tome");

    /** A mixed book narrowed to its best line. */
    public static final DeferredItem<Item> CONCENTRATION_TOME = tome("concentration_tome");

    /** An item's history cleared, once, for what that history was costing. */
    public static final DeferredItem<Item> PATIENCE_TOME = tome("patience_tome");

    private static DeferredItem<Item> tome(String name) {
        DeferredItem<Item> item = ITEMS.registerItem(name, properties ->
                new TomeItem(properties, "elysiumenchanting.tooltip." + name));
        EnchantingBlocks.inTab(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
