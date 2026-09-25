package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.HammerItem;
import com.elysium.arsenal.rules.ForgeRules;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The hammers, one per entry in the rules' own table.
 *
 * <p>Walked rather than listed. Three is a small enough number to type out
 * and that is exactly why it is worth not typing out: a fourth hammer added
 * to the rules and forgotten here is a kind of gear nothing can reforge, and
 * the only symptom is a player wondering why their shield never changes.
 */
public final class ForgeItems {

    private ForgeItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArsenal.MODID);

    /** How many swings a hammer has in it before it breaks. */
    public static final int USES = 250;

    public static final Map<String, DeferredItem<Item>> HAMMERS = register();

    private static Map<String, DeferredItem<Item>> register() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (String hammer : new TreeMap<>(ForgeRules.HAMMERS).keySet()) {
            made.put(hammer, ITEMS.register(hammer,
                    () -> new HammerItem(hammer, new Item.Properties()
                            .durability(USES).rarity(Rarity.UNCOMMON))));
        }
        if (made.size() != ForgeRules.HAMMERS.size()) {
            throw new IllegalStateException("the rules list "
                    + ForgeRules.HAMMERS.size() + " hammers and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }
}
