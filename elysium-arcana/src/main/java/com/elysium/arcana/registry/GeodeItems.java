package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.rules.GeodeRules;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A shard and a spyglass for every material.
 *
 * <p>The shard is what a cluster breaks into. The spyglass is the game's own
 * joke made sixty times over, and it is here because the mod this replaces
 * has it and because a spyglass of uranium is funnier than it has any right
 * to be.
 */
public final class GeodeItems {

    private GeodeItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArcana.MODID);

    public static final Map<String, DeferredItem<Item>> ALL = register();

    private static Map<String, DeferredItem<Item>> register() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (String name : GeodeRules.items()) {
            made.put(name, ITEMS.register(name, () -> new Item(
                    name.endsWith("_spyglass")
                            ? new Item.Properties().stacksTo(1)
                            : new Item.Properties())));
        }
        if (made.size() != GeodeRules.items().size()) {
            throw new IllegalStateException("the catalogue has "
                    + GeodeRules.items().size() + " geode items and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }
}
