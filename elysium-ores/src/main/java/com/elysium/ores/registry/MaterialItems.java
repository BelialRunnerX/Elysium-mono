package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.rules.CordonMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Cordon's materials as items: every raw, ingot, nugget, gem, shard,
 * cloth, drop and part the roster names, and a block item for every block.
 *
 * <p>All plain items. What a material <em>does</em> is the arsenal's and the
 * armory's business; here a cobalt ingot is a thing in a {@code c:} tag, which
 * is exactly what the ore half of this module resolves an ore's drop to. That
 * is the point of shipping them in this module and not in a gear one: the ore
 * and the item it drops are one table apart, and the tag between them is the
 * same tag every other mod's recipes read.
 */
public final class MaterialItems {

    private MaterialItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumOres.MODID);

    private static final Map<String, DeferredItem<Item>> BY_NAME = new LinkedHashMap<>();

    static {
        for (String name : CordonMaterials.items()) {
            BY_NAME.put(name, ITEMS.registerSimpleItem(name, new Item.Properties()));
        }
        for (String name : CordonMaterials.blocks()) {
            BY_NAME.put(name, ITEMS.register(name,
                    () -> new BlockItem(MaterialBlocks.of(name).get(), new Item.Properties())));
        }
    }

    /** The item registered under this name, or null. */
    public static DeferredItem<Item> of(String name) {
        return BY_NAME.get(name);
    }

    /** Every item, items first then block items, in roster order. */
    public static List<DeferredItem<Item>> all() {
        return List.copyOf(BY_NAME.values());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
