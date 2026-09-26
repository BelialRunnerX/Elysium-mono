package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.FishRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Three items for every fish, and the tackle you catch them with.
 *
 * <p>The fish, the bucket and the egg are one loop each rather than three
 * hundred and fifteen lines. The mod this replaces writes all three out by
 * hand for every species, which is why it has a hundred and two buckets for a
 * hundred and five fish.
 */
public final class FishItems {

    private FishItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    /** How long eating a fish takes, which is the same for all of them. */
    public static final float EATING_SECONDS = 1.6F;

    /**
     * How much saturation a fish is worth per point of nutrition.
     *
     * <p>Under the game's own cooked fish, deliberately: a raw fish should be
     * worth carrying and not worth living on.
     */
    public static final float SATURATION_PER_POINT = 0.12F;

    public static final Map<String, DeferredItem<Item>> RAW = registerRaw();
    public static final Map<String, DeferredItem<MobBucketItem>> BUCKETS
            = registerBuckets();
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS
            = registerEggs();

    private static Map<String, DeferredItem<Item>> registerRaw() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (FishRules.Fish one : FishRules.FISH) {
            made.put(one.id(), ITEMS.register(one.id(), () -> new Item(
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(one.nutrition())
                            .saturationModifier(one.nutrition()
                                    * SATURATION_PER_POINT)
                            .build()))));
        }
        if (made.size() != FishRules.FISH.size()) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<MobBucketItem>> registerBuckets() {
        Map<String, DeferredItem<MobBucketItem>> made = new LinkedHashMap<>();
        for (FishRules.Fish one : FishRules.FISH) {
            boolean molten = FishRules.LAVA.equals(one.family().water());
            made.put(one.id(), ITEMS.register(one.bucket(), () -> new MobBucketItem(
                    FishEntities.of(one.id()),
                    molten ? Fluids.LAVA : Fluids.WATER,
                    molten ? SoundEvents.BUCKET_EMPTY_LAVA
                            : SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1))));
        }
        if (made.size() != FishRules.FISH.size()) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " bucket(s) were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> registerEggs() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (FishRules.Fish one : FishRules.FISH) {
            int background = BeeItems.shade(one.family().name(), 0x2E6F8E, 30);
            int highlight = BeeItems.shade(one.id(), 0xC8DCE6, 40);
            made.put(one.id(), ITEMS.register(one.egg(),
                    () -> new DeferredSpawnEggItem(() -> FishEntities.of(one.id()),
                            background, highlight, new Item.Properties())));
        }
        if (made.size() != FishRules.FISH.size()) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** The bucket item for a species. */
    public static Item bucketOf(String species) {
        DeferredItem<MobBucketItem> bucket = BUCKETS.get(species);
        if (bucket == null) {
            throw new IllegalArgumentException("no fish called " + species);
        }
        return bucket.get();
    }

    // ------------------------------------------------------------------
    // The tackle
    // ------------------------------------------------------------------

    /** How much each rung of rod costs in durability terms. */
    public static final int[] ROD_USES = {64, 160, 384, 900, 2048};

    /** One rod per rung of the luck ladder the rules describe. */
    public static final Map<Integer, DeferredItem<Item>> RODS = registerRods();

    private static Map<Integer, DeferredItem<Item>> registerRods() {
        Map<Integer, DeferredItem<Item>> made = new LinkedHashMap<>();
        String[] names = {"reed_rod", "oak_rod", "iron_rod", "steel_rod", "starsteel_rod"};
        for (int rung = 0; rung < FishRules.RODS; rung++) {
            final int uses = ROD_USES[Math.min(ROD_USES.length - 1, rung)];
            final int held = rung;
            made.put(rung, ITEMS.register(names[Math.min(names.length - 1, rung)],
                    () -> new com.elysium.agronomy.item.ElysiumRodItem(held,
                            new Item.Properties().durability(uses))));
        }
        // One rod per rung, or a rung of the ladder the rules describe cannot
        // be held and the tilt at that step does nothing.
        if (made.size() != FishRules.RODS) {
            throw new IllegalStateException("the rules describe " + FishRules.RODS
                    + " rungs of rod and " + made.size() + " rod(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** One bait per family, so every habitat can be fished for on purpose. */
    /**
     * The hooks and the bobbers, walked out of the rules.
     *
     * <p>They stack to one. A hook is a thing fitted to a rod, and sixty-four
     * of them in a slot that takes one is a number a player has to be told
     * does not mean what it says.
     */
    public static final Map<String, DeferredItem<Item>> TACKLE = registerTackle();

    private static Map<String, DeferredItem<Item>> registerTackle() {
        Map<String, DeferredItem<Item>> made = new java.util.LinkedHashMap<>();
        for (String name : com.elysium.agronomy.rules.TackleRules.items()) {
            made.put(name, ITEMS.register(name,
                    () -> new Item(new Item.Properties().stacksTo(1))));
        }
        if (made.size() != com.elysium.agronomy.rules.TackleRules.items().size()) {
            throw new IllegalStateException("the rules have "
                    + com.elysium.agronomy.rules.TackleRules.items().size()
                    + " piece(s) of tackle and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    public static final Map<String, DeferredItem<Item>> BAITS = registerBaits();

    private static Map<String, DeferredItem<Item>> registerBaits() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (FishRules.Family family : FishRules.FAMILIES) {
            made.put(family.name(), ITEMS.register(family.name() + "_bait",
                    () -> new Item(new Item.Properties())));
        }
        if (made.size() != FishRules.FAMILIES.size()) {
            throw new IllegalStateException("there are " + FishRules.FAMILIES.size()
                    + " families and " + made.size() + " bait(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** Takes a fish off the hook without spoiling it. */
    public static final DeferredItem<Item> FILLET_KNIFE =
            ITEMS.register("fillet_knife", () -> new Item(new Item.Properties()
                    .durability(240)));

    /** Says how big the last one was. */
    public static final DeferredItem<Item> MEASURING_LINE =
            ITEMS.register("measuring_line", () -> new Item(new Item.Properties()
                    .stacksTo(1)));
}
