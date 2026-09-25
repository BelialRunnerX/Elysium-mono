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
import net.neoforged.neoforge.registries.DeferredBlock;
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
    /**
     * The seeds, read out of the rules rather than kept here.
     *
     * <p>This was a second copy of the table for a while, and the generator
     * had a third. Three hand-written copies of thirty rows is three chances
     * for a seed to take one length of time to grow and cost another to make,
     * which is the shape of bug nobody finds by reading.
     */
    public static final Map<String, Integer> SEEDS = new LinkedHashMap<>();

    static {
        for (com.elysium.agronomy.rules.SeedRules.Seed seed
                : com.elysium.agronomy.rules.SeedRules.SEEDS) {
            SEEDS.put(seed.material(), seed.tier());
        }
        if (SEEDS.size() != com.elysium.agronomy.rules.SeedRules.SEEDS.size()) {
            throw new IllegalStateException("the rules list "
                    + com.elysium.agronomy.rules.SeedRules.SEEDS.size()
                    + " seeds and " + SEEDS.size() + " were read");
        }
    }

    private static final Map<String, DeferredItem<Item>> SOWN = new LinkedHashMap<>();

    /** One per seed, off an altar. */
    private static final Map<String, DeferredItem<Item>> AWOKEN =
            new LinkedHashMap<>();

    static {
        for (Map.Entry<String, Integer> seed : SEEDS.entrySet()) {
            int tier = seed.getValue();
            SOWN.put(seed.getKey(), ITEMS.register(seed.getKey() + "_seed",
                    () -> new EssenceSeed(new Item.Properties(), tier,
                            () -> essence(tier).get())));
        }
        if (SOWN.size() != SEEDS.size()) {
            throw new IllegalStateException("there are " + SEEDS.size()
                    + " seeds and " + SOWN.size() + " were registered");
        }
        for (Map.Entry<String, Integer> seed : SEEDS.entrySet()) {
            int tier = seed.getValue();
            AWOKEN.put(seed.getKey(), ITEMS.register(
                    com.elysium.agronomy.rules.AltarRules.awakenedSeed(
                            seed.getKey()),
                    () -> new EssenceSeed(new Item.Properties(), tier,
                            () -> essence(tier).get(), true)));
        }
        if (AWOKEN.size() != SEEDS.size()) {
            throw new IllegalStateException("there are " + SEEDS.size()
                    + " seeds and " + AWOKEN.size()
                    + " awakened ones; a seed nobody can awaken is a seed the "
                    + "altar has never heard of");
        }
    }

    /** The awakened seed of a material. */
    public static DeferredItem<Item> awakened(String material) {
        return AWOKEN.get(material);
    }

    /**
     * A chunk for every material whose essence has nowhere else to turn into.
     *
     * <p>Seventeen of the thirty seeds grow something the foundry makes
     * ingots of, and the foundry sits above this module. A recipe result has
     * to be a concrete item, so the essence becomes a chunk here and the
     * chunk carries the tag the foundry already smelts.
     */
    public static final Map<String, DeferredItem<Item>> CHUNKS = registerChunks();

    private static Map<String, DeferredItem<Item>> registerChunks() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        // The seeds' seventeen and the bees' materials together, walked from
        // the rules rather than listed. A comb that spins into a chunk nobody
        // registered is a centrifuge that eats combs and gives nothing back.
        for (String material : com.elysium.agronomy.rules.ApiaryRules.chunked()) {
            made.put(material, ITEMS.registerSimpleItem(
                    com.elysium.agronomy.rules.SeedRules.chunkOf(material)));
        }
        if (made.size()
                != com.elysium.agronomy.rules.ApiaryRules.chunked().size()) {
            throw new IllegalStateException("the rules want "
                    + com.elysium.agronomy.rules.ApiaryRules.chunked().size()
                    + " chunk(s) and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * An item by the id the rules name it with, or an empty stack.
     *
     * <p>Looked up rather than switched on, so a rule that names something
     * this pack does not have gives an empty stack a validator can notice
     * rather than a crash halfway through a tick.
     */
    public static net.minecraft.world.item.ItemStack thing(String id) {
        net.minecraft.resources.ResourceLocation where =
                net.minecraft.resources.ResourceLocation.tryParse(id);
        if (where == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        Item found = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(where);
        return found == net.minecraft.world.item.Items.AIR
                ? net.minecraft.world.item.ItemStack.EMPTY
                : new net.minecraft.world.item.ItemStack(found);
    }

    /** Every comb a centrifuge will take. */
    public static final TagKey<Item> COMB_TAG = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "combs"));

    /** The seed that grows a given material. */
    public static DeferredItem<Item> seed(String material) {
        return SOWN.get(material);
    }

    /**
     * The book that keeps a record of what you have eaten.
     *
     * <p>The record itself lives on the player, not in the book: losing the
     * book would otherwise lose ten hearts, and a mechanic about variety
     * should not also be a mechanic about inventory management. The book is
     * how you read it.
     */
    public static final DeferredItem<Item> FOOD_BOOK =
            ITEMS.register("food_book", () -> new com.elysium.agronomy.item.FoodBook(
                    new Item.Properties().stacksTo(1)));

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

    public static final DeferredItem<BlockItem> CENTRIFUGE =
            ITEMS.registerSimpleBlockItem(AgronomyBlocks.CENTRIFUGE);

    /** The sixth rung, which is made rather than grown. */
    public static final DeferredItem<Item> INSANIUM =
            ITEMS.registerSimpleItem(
                    com.elysium.agronomy.rules.AltarRules.INSANIUM);

    /** The three blocks an altar is made of. */
    public static final java.util.Map<String, DeferredItem<BlockItem>> ALTAR =
            registerAltar();

    private static java.util.Map<String, DeferredItem<BlockItem>> registerAltar() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (String name : com.elysium.agronomy.rules.AltarRules.everything()) {
            made.put(name, ITEMS.registerSimpleBlockItem(
                    AgronomyBlocks.ALTAR.get(name)));
        }
        if (made.size()
                != com.elysium.agronomy.rules.AltarRules.everything().size()) {
            throw new IllegalStateException("an altar is "
                    + com.elysium.agronomy.rules.AltarRules.everything().size()
                    + " block(s) and " + made.size() + " item(s) were made");
        }
        return java.util.Map.copyOf(made);
    }

    /** One item per rung of the housing ladder above the plain hive. */
    public static final java.util.Map<String, DeferredItem<BlockItem>> APIARIES =
            registerApiaries();

    private static java.util.Map<String, DeferredItem<BlockItem>> registerApiaries() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, DeferredBlock<net.minecraft.world.level.block.Block>> rung
                : AgronomyBlocks.APIARIES.entrySet()) {
            made.put(rung.getKey(), ITEMS.registerSimpleBlockItem(rung.getValue()));
        }
        if (made.size() != AgronomyBlocks.APIARIES.size()) {
            throw new IllegalStateException("there are "
                    + AgronomyBlocks.APIARIES.size() + " apiary block(s) and "
                    + made.size() + " item(s) for them");
        }
        return java.util.Map.copyOf(made);
    }

    /**
     * The upgrades, walked out of the rules.
     *
     * <p>They stack to one apiece. An upgrade is a slot, a slot holds one of
     * a kind, and sixty-four of them in a slot that takes one is a number a
     * player has to be told does not mean what it says.
     */
    public static final java.util.Map<String, DeferredItem<Item>> UPGRADES =
            registerUpgrades();

    private static java.util.Map<String, DeferredItem<Item>> registerUpgrades() {
        java.util.Map<String, DeferredItem<Item>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.agronomy.rules.ApiaryRules.Upgrade upgrade
                : com.elysium.agronomy.rules.ApiaryRules.UPGRADES) {
            made.put(upgrade.id(), ITEMS.register(upgrade.id(),
                    () -> new Item(new Item.Properties().stacksTo(1))));
        }
        if (made.size()
                != com.elysium.agronomy.rules.ApiaryRules.UPGRADES.size()) {
            throw new IllegalStateException("the rules have "
                    + com.elysium.agronomy.rules.ApiaryRules.UPGRADES.size()
                    + " upgrade(s) and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** The order the tab shows them in: grow it, hurry it, keep bees, make rock. */
    /**
     * One item per vein generator, walked out of the same table the blocks are.
     *
     * <p>A block with no item is a block that exists and cannot be got, which
     * is the same hole as a bee for a metal nothing makes — and it is only
     * ever found by the player who went looking for that one generator.
     */
    public static final java.util.Map<String, DeferredItem<BlockItem>> VEINS =
            registerVeins();

    private static java.util.Map<String, DeferredItem<BlockItem>> registerVeins() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.agronomy.rules.AgronomyRules.Vein vein
                : com.elysium.agronomy.rules.AgronomyRules.VEINS) {
            made.put(vein.material(), ITEMS.registerSimpleBlockItem(
                    AgronomyBlocks.VEINS.get(vein.material())));
        }
        if (made.size() != com.elysium.agronomy.rules.AgronomyRules.VEINS.size()) {
            throw new IllegalStateException("there are "
                    + com.elysium.agronomy.rules.AgronomyRules.VEINS.size()
                    + " veins and " + made.size() + " item(s) were registered");
        }
        return java.util.Map.copyOf(made);
    }

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
