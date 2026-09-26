package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.BeeRules;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A comb and a spawn egg for every bee, and the four tools you work a hive
 * with.
 *
 * <p>Both colours of every egg are computed from the species name rather than
 * written down. Seventy-five hand-picked pairs of colours is seventy-five
 * chances to give two bees the same egg and never notice; a hash gives a
 * stable, well-spread colour for free and cannot drift from the roster.
 */
public final class BeeItems {

    private BeeItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    /** The band each kind of bee's egg sits in, so a kind reads at a glance. */
    private static final Map<String, Integer> KIND_HUE = Map.of(
            BeeRules.WILD, 0xD8B23A,
            BeeRules.RESOURCE, 0x8C8F96,
            BeeRules.PLACE, 0x5F8D6A,
            BeeRules.PRODUCT, 0xB0715E);

    /**
     * A colour for a species, spread around its kind's hue.
     *
     * <p>Deterministic: the same name always gives the same colour, in this
     * run and the next, so a texture drawn against one today still matches.
     */
    static int shade(String id, int base, int spread) {
        int hash = 0;
        for (int index = 0; index < id.length(); index++) {
            hash = hash * 31 + id.charAt(index);
        }
        hash = Math.abs(hash);
        int red = clamp(((base >> 16) & 0xFF) + (hash % (2 * spread + 1)) - spread);
        int green = clamp(((base >> 8) & 0xFF)
                + ((hash / 7) % (2 * spread + 1)) - spread);
        int blue = clamp((base & 0xFF) + ((hash / 53) % (2 * spread + 1)) - spread);
        return (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    public static final Map<String, DeferredItem<Item>> COMBS = registerCombs();

    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS
            = registerEggs();

    private static Map<String, DeferredItem<Item>> registerCombs() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            made.put(bee.id(), ITEMS.register(bee.comb(),
                    () -> new Item(new Item.Properties())));
        }
        if (made.size() != BeeRules.BEES.size()) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()
                    + " bees and " + made.size() + " comb(s) were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> registerEggs() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            int background = shade(bee.id(), KIND_HUE.getOrDefault(bee.kind(), 0x888888),
                    26);
            int highlight = shade(bee.produce(), 0xF2E4A8, 34);
            made.put(bee.id(), ITEMS.register(bee.id() + "_bee_spawn_egg",
                    () -> new DeferredSpawnEggItem(() -> BeeEntities.of(bee.id()),
                            background, highlight, new Item.Properties())));
        }
        if (made.size() != BeeRules.BEES.size()) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()
                    + " bees and " + made.size() + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    // ------------------------------------------------------------------
    // The four things you work a hive with
    // ------------------------------------------------------------------

    /** Takes a finished comb off a bee: that species' own comb. */
    public static final DeferredItem<Item> SCOOP =
            ITEMS.register("bee_scoop", () -> new com.elysium.agronomy.item.BeeScoopItem(
                    new Item.Properties().durability(128)));

    /** Calms a hive long enough to open it. */
    public static final DeferredItem<Item> SMOKER =
            ITEMS.register("bee_smoker", () -> new com.elysium.agronomy.item.BeeSmokerItem(
                    new Item.Properties().durability(64)));

    /** Carries a bee out of the world, or out of a hive, and into a hive. */
    public static final DeferredItem<Item> JAR =
            ITEMS.register("bee_jar", () -> new com.elysium.agronomy.item.BeeJarItem(
                    new Item.Properties().stacksTo(1)));

    /**
     * Persuades two bees in one hive to make a third: the child of the pair
     * if the hive holds a pair {@link BeeRules} breeds, and one more of the
     * hive's own otherwise.
     */
    public static final DeferredItem<Item> TREAT =
            ITEMS.register("bee_treat", () -> new Item(new Item.Properties()));
}
