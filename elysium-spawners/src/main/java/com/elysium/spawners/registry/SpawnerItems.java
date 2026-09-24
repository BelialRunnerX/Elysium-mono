package com.elysium.spawners.registry;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.item.SpawnerRuneItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The runes (Apotheosis' AP-29).
 *
 * <h2>Eleven, and each one does exactly one thing</h2>
 *
 * Five tune an axis and six switch on a behaviour. Nothing here does two
 * things: a rune that made a spawner faster <em>and</em> quieter would be a
 * rune everybody uses and ten runes nobody does, and the decision about which
 * wall of the room to spend on is the whole point.
 *
 * <p>What each one actually does is a data file, not this class. These are the
 * items; {@code data/elysiumspawners/elysium_spawner_modifiers/} is the rules.
 */
public final class SpawnerItems {

    private SpawnerItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumSpawners.MODID);

    // -- the five axes ------------------------------------------------------

    /** Waits less. */
    public static final DeferredItem<Item> RUNE_OF_HASTE = rune("rune_of_haste");

    /** Sends more at once. */
    public static final DeferredItem<Item> RUNE_OF_SWARMING = rune("rune_of_swarming");

    /** Tolerates more of its own standing around. */
    public static final DeferredItem<Item> RUNE_OF_CROWDING = rune("rune_of_crowding");

    /** Notices a player further off. */
    public static final DeferredItem<Item> RUNE_OF_VIGILANCE = rune("rune_of_vigilance");

    /** Puts them further from itself. */
    public static final DeferredItem<Item> RUNE_OF_SCATTERING = rune("rune_of_scattering");

    // -- the six behaviours -------------------------------------------------

    /** Stops caring whether anybody is watching. */
    public static final DeferredItem<Item> RUNE_OF_INDIFFERENCE = rune("rune_of_indifference");

    /** Stops caring how bright it is. */
    public static final DeferredItem<Item> RUNE_OF_DARKNESS = rune("rune_of_darkness");

    /** What it sends stays: it does not despawn when nobody is looking. */
    public static final DeferredItem<Item> RUNE_OF_TETHERING = rune("rune_of_tethering");

    /** What it sends does not move. */
    public static final DeferredItem<Item> RUNE_OF_STILLNESS = rune("rune_of_stillness");

    /** What it sends makes no sound. */
    public static final DeferredItem<Item> RUNE_OF_SILENCE = rune("rune_of_silence");

    /** It waits to be told. */
    public static final DeferredItem<Item> RUNE_OF_OBEDIENCE = rune("rune_of_obedience");

    private static DeferredItem<Item> rune(String name) {
        DeferredItem<Item> item = ITEMS.registerItem(name, properties ->
                new SpawnerRuneItem(properties, "elysiumspawners.tooltip." + name));
        SpawnerBlocks.inTab(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
