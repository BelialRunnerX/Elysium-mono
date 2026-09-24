package com.elysium.lib.trinket;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Items that want to be wearable in an accessory slot.
 *
 * <h2>Why a registry rather than an interface</h2>
 *
 * Because the module that makes the item and the module that owns the
 * accessory slots are not allowed to see each other. {@code elysium-armory}
 * builds a modular ring out of parts and knows nothing about Curios;
 * {@code elysium-trinkets} owns the one file in this project that knows Curios
 * exists and must not grow a dependency on the armoury to find out which items
 * are rings.
 *
 * <p>So the library holds a list in the middle. A module says "this item is
 * worn on a finger"; the accessory bridge reads the list at setup and tells
 * whatever accessory mod is installed. Neither module imports the other, and a
 * pack with no accessory mod simply has a list nobody reads.
 *
 * <h2>Why the slot is a string</h2>
 *
 * Because slot names are the accessory mod's vocabulary, not ours — "ring",
 * "necklace", "charm" — and inventing an enum here would mean this file had to
 * be edited every time a pack added a slot. The bridge is free to map a name
 * it does not recognise onto whatever it does.
 */
public final class ElysiumWearables {

    private ElysiumWearables() {
    }

    /**
     * One wearable item.
     *
     * @param item the item itself
     * @param slot the accessory slot it belongs in, in the accessory mod's
     *             own vocabulary
     */
    public record Wearable(Item item, String slot) {
    }

    private static final Map<Item, Wearable> REGISTERED = new LinkedHashMap<>();

    /**
     * Declares an item wearable.
     *
     * Called during setup, after the registries exist — an item resolved
     * during mod construction is a crash with a confusing stack trace, which
     * this project has had twice.
     */
    public static void register(Item item, String slot) {
        if (item == null || slot == null || slot.isBlank()) {
            return;
        }
        REGISTERED.put(item, new Wearable(item, slot));
    }

    /** Everything declared wearable, in the order it was declared. */
    public static List<Wearable> all() {
        return List.copyOf(new ArrayList<>(REGISTERED.values()));
    }

    /** Whether this item was declared wearable, and where. */
    public static Wearable of(Item item) {
        return REGISTERED.get(item);
    }

    /** How many there are, for a validator or a log line. */
    public static int size() {
        return REGISTERED.size();
    }
}
