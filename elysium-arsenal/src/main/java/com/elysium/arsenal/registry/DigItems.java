package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.ArtifactItem;
import com.elysium.arsenal.item.BrushItem;
import com.elysium.arsenal.item.TotemItem;
import com.elysium.arsenal.rules.ArtifactRules;
import com.elysium.arsenal.rules.DigRules;
import com.elysium.arsenal.rules.TotemRules;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the digging adds that is an item, including one per block.
 *
 * <p>The block items are made by walking {@link DigBlocks#all()}. A block
 * registered without one is a block that exists, generates and cannot be
 * picked up — which looks exactly like a block that does not exist, from the
 * only side a player can see.
 */
public final class DigItems {

    private DigItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArsenal.MODID);

    /** How many sites a brush of each tier survives. */
    public static final Map<String, Integer> USES = Map.of(
            "iron_brush", 64, "diamond_brush", 256, "netherite_brush", 1_024);

    public static final Map<String, DeferredItem<Item>> BRUSHES = registerBrushes();
    public static final Map<String, DeferredItem<Item>> TOTEMS = registerTotems();
    public static final Map<String, DeferredItem<Item>> IDENTIFIED = registerFound();
    public static final Map<String, DeferredItem<Item>> BLOCK_ITEMS = registerBlocks();

    public static final DeferredItem<Item> SHARD = ITEMS.register(
            ArtifactRules.SHARD, () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> UNIDENTIFIED = ITEMS.register(
            ArtifactRules.UNIDENTIFIED, () -> new ArtifactItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    // There was a disc here, retired on 29 September 2026: a plain item with
    // no song, which no jukebox would play and which nothing handed out. The
    // dirt pool hands out the game's own relic disc instead.

    private static Map<String, DeferredItem<Item>> registerBrushes() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (DigRules.Brush one : DigRules.BRUSHES) {
            Integer uses = USES.get(one.name());
            if (uses == null) {
                throw new IllegalStateException(one.name() + " has no durability, so "
                        + "it would be an unbreakable brush and every rung below it "
                        + "pointless");
            }
            made.put(one.name(), ITEMS.register(one.name(),
                    () -> new BrushItem(one, new Item.Properties().durability(uses))));
        }
        if (made.size() != DigRules.BRUSHES.size()) {
            throw new IllegalStateException("the rules list "
                    + DigRules.BRUSHES.size() + " brushes and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<Item>> registerTotems() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (TotemRules.Totem one : TotemRules.of(TotemRules.HELD)) {
            made.put(one.name(), ITEMS.register(one.name(),
                    () -> new TotemItem(one, new Item.Properties()
                            .stacksTo(16).rarity(Rarity.UNCOMMON))));
        }
        if (made.size() != TotemRules.of(TotemRules.HELD).size()) {
            throw new IllegalStateException("the rules list "
                    + TotemRules.of(TotemRules.HELD).size() + " held totems and "
                    + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<Item>> registerFound() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (ArtifactRules.Artifact one : ArtifactRules.ARTIFACTS) {
            String id = "identified_" + one.name();
            made.put(one.name(), ITEMS.register(id,
                    () -> new com.elysium.arsenal.item.IdentifiedArtifactItem(one,
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        }
        if (made.size() != ArtifactRules.ARTIFACTS.size()) {
            throw new IllegalStateException("the roster lists "
                    + ArtifactRules.ARTIFACTS.size() + " artefacts and " + made.size()
                    + " were registered, so identifying can land on nothing");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<Item>> registerBlocks() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        Map<String, DeferredBlock<net.minecraft.world.level.block.Block>> blocks =
                DigBlocks.all();
        for (Map.Entry<String,
                DeferredBlock<net.minecraft.world.level.block.Block>> row
                : blocks.entrySet()) {
            made.put(row.getKey(), ITEMS.register(row.getKey(),
                    () -> new BlockItem(row.getValue().get(), new Item.Properties())));
        }
        if (made.size() != blocks.size()) {
            throw new IllegalStateException("there are " + blocks.size()
                    + " blocks and " + made.size() + " of them can be picked up");
        }
        return Map.copyOf(made);
    }

    /** Every item this file registers, in the order a tab should show them. */
    public static Map<String, DeferredItem<Item>> all() {
        Map<String, DeferredItem<Item>> everything = new LinkedHashMap<>();
        everything.putAll(BRUSHES);
        everything.put(ArtifactRules.SHARD, SHARD);
        everything.put(ArtifactRules.UNIDENTIFIED, UNIDENTIFIED);
        everything.putAll(IDENTIFIED);
        everything.putAll(TOTEMS);
        everything.putAll(BLOCK_ITEMS);
        return Map.copyOf(everything);
    }
}
