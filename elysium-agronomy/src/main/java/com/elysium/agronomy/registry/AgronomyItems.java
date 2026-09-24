package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.item.EssenceSeed;
import com.elysium.agronomy.rules.AgronomyRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The essences, the seeds that grow them, and the bees.
 *
 * <h2>The ladder is a table, not thirty classes</h2>
 *
 * Every seed differs in exactly two ways — which rung it sits on and what it
 * yields — so the whole catalogue is a table read in a loop. Adding a material
 * is a line; adding a sixth tier is a number in {@code AgronomyRules}. That is
 * the same argument that made four Age II machines share one class, and it is
 * the only way a resource-growing mod stays tunable once it has forty entries.
 */
public final class AgronomyItems {

    private AgronomyItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumAgronomy.MODID);

    /** What a hive will accept. Vanilla has no bee item, so this is ours. */
    public static final TagKey<Item> BEE_TAG = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "bees"));

    /** The five rungs, named for the pack rather than for anybody else's mod. */
    public static final List<String> ESSENCE_NAMES = List.of(
            "faint_essence", "bright_essence", "keen_essence",
            "radiant_essence", "ascendant_essence");

    private static final Map<Integer, DeferredItem<Item>> ESSENCES = new LinkedHashMap<>();

    static {
        for (int tier = 1; tier <= AgronomyRules.TIERS; tier++) {
            ESSENCES.put(tier, ITEMS.registerSimpleItem(ESSENCE_NAMES.get(tier - 1)));
        }
    }

    /** The essence of a given rung. */
    public static DeferredItem<Item> essence(int tier) {
        return ESSENCES.get(Math.max(1, Math.min(AgronomyRules.TIERS, tier)));
    }

    /**
     * What each seed grows, and how far up the ladder it sits.
     *
     * <p>The tier is the material's worth, not the essence's: coal is cheap
     * and grows quickly, netherite is dear and takes an age. A player reads
     * the ladder off which seeds they can afford to wait for.
     *
     * <p>Everything here is vanilla on purpose. A seed for a material another
     * module supplies would be a recipe that fails to load the day that module
     * is not installed, and this one is meant to work on its own.
     */
    public static final Map<String, Integer> SEEDS = new LinkedHashMap<>();

    static {
        SEEDS.put("coal", 1);
        SEEDS.put("copper", 1);
        SEEDS.put("iron", 2);
        SEEDS.put("redstone", 2);
        SEEDS.put("lapis", 3);
        SEEDS.put("gold", 3);
        SEEDS.put("diamond", 4);
        SEEDS.put("netherite", 5);
    }

    private static final Map<String, DeferredItem<Item>> SOWN = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, Integer> seed : SEEDS.entrySet()) {
            int tier = seed.getValue();
            SOWN.put(seed.getKey(), ITEMS.register(seed.getKey() + "_seed",
                    () -> new EssenceSeed(new Item.Properties(), tier,
                            () -> essence(tier).get())));
        }
    }

    /** The seed that grows a given material. */
    public static DeferredItem<Item> seed(String material) {
        return SOWN.get(material);
    }

    public static final DeferredItem<Item> COMB = ITEMS.registerSimpleItem("comb");
    public static final DeferredItem<Item> WAX = ITEMS.registerSimpleItem("wax");
    public static final DeferredItem<Item> BEE = ITEMS.registerSimpleItem("bee");

    public static final DeferredItem<BlockItem> POT =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.POT);
    public static final DeferredItem<BlockItem> ACCELERATOR =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.ACCELERATOR);
    public static final DeferredItem<BlockItem> HIVE =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.HIVE);
    public static final DeferredItem<BlockItem> ROCK_GENERATOR =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.ROCK_GENERATOR);
    public static final DeferredItem<BlockItem> DRIVEN_ROCK_GENERATOR =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.DRIVEN_ROCK_GENERATOR);
    public static final DeferredItem<BlockItem> INDUSTRIAL_ROCK_GENERATOR =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.INDUSTRIAL_ROCK_GENERATOR);

    /** The order the tab shows them in: grow it, hurry it, keep bees, make rock. */
    private static final List<DeferredItem<BlockItem>> ORDER = List.of(
            POT, ACCELERATOR, HIVE,
            ROCK_GENERATOR, DRIVEN_ROCK_GENERATOR, INDUSTRIAL_ROCK_GENERATOR);

    private static List<DeferredItem<Item>> parts() {
        List<DeferredItem<Item>> parts = new ArrayList<>(ESSENCES.values());
        parts.addAll(SOWN.values());
        parts.add(BEE);
        parts.add(COMB);
        parts.add(WAX);
        return parts;
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "agronomy", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumagronomy"))
                    .icon(() -> new ItemStack(POT.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                        for (DeferredItem<? extends Item> item : parts()) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
