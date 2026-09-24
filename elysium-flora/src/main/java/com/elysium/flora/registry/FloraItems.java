package com.elysium.flora.registry;

import com.elysium.flora.ElysiumFlora;
import com.elysium.flora.rules.FloraRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The items, the seeds, and the one tab they all live in.
 *
 * <h2>Burn times</h2>
 *
 * Declared here rather than in a data file because NeoForge asks the item, and
 * the numbers come from {@link FloraRules} so the harness can pin them. The
 * rule the harness cares about is the relationship — charcoal beats coal,
 * one block is worth exactly the nine that made it, planks burn like planks —
 * rather than any single figure.
 */
public final class FloraItems {

    private FloraItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumFlora.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumFlora.MODID);

    /** Every block that gets a plain block item, in the order the tab shows them. */
    private static final List<DeferredItem<? extends Item>> SHOWN = new ArrayList<>();

    private static DeferredItem<Item> simple(String name) {
        DeferredItem<Item> made = ITEMS.register(name, () -> new Item(new Item.Properties()));
        SHOWN.add(made);
        return made;
    }

    private static DeferredItem<BlockItem> block(String name,
                                                 java.util.function.Supplier<Block> which) {
        DeferredItem<BlockItem> made = ITEMS.register(name,
                () -> new BlockItem(which.get(), new Item.Properties()));
        SHOWN.add(made);
        return made;
    }

    // --- what the crops give ----------------------------------------------

    public static final DeferredItem<Item> LOOMFLAX_FIBRE = simple("loomflax_fibre");
    public static final DeferredItem<Item> DOWNBLOOM_TUFT = simple("downbloom_tuft");
    public static final DeferredItem<Item> EMBERWOOD_CHARCOAL = simple("emberwood_charcoal");

    // --- the seeds, which are the crops ------------------------------------

    public static final DeferredItem<ItemNameBlockItem> LOOMFLAX_SEEDS =
            ITEMS.register("loomflax_seeds", () -> new ItemNameBlockItem(
                    FloraBlocks.LOOMFLAX.get(), new Item.Properties()));

    public static final DeferredItem<ItemNameBlockItem> DOWNBLOOM_SEEDS =
            ITEMS.register("downbloom_seeds", () -> new ItemNameBlockItem(
                    FloraBlocks.DOWNBLOOM.get(), new Item.Properties()));

    static {
        SHOWN.add(LOOMFLAX_SEEDS);
        SHOWN.add(DOWNBLOOM_SEEDS);
    }

    // --- the blocks --------------------------------------------------------

    public static final DeferredItem<BlockItem> WILD_LOOMFLAX =
            block("wild_loomflax", () -> FloraBlocks.WILD_LOOMFLAX.get());
    public static final DeferredItem<BlockItem> WILD_DOWNBLOOM =
            block("wild_downbloom", () -> FloraBlocks.WILD_DOWNBLOOM.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_LOG =
            block("emberwood_log", () -> FloraBlocks.EMBERWOOD_LOG.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_WOOD =
            block("emberwood_wood", () -> FloraBlocks.EMBERWOOD_WOOD.get());
    public static final DeferredItem<BlockItem> STRIPPED_EMBERWOOD_LOG =
            block("stripped_emberwood_log", () -> FloraBlocks.STRIPPED_EMBERWOOD_LOG.get());
    public static final DeferredItem<BlockItem> STRIPPED_EMBERWOOD_WOOD =
            block("stripped_emberwood_wood", () -> FloraBlocks.STRIPPED_EMBERWOOD_WOOD.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_PLANKS =
            block("emberwood_planks", () -> FloraBlocks.EMBERWOOD_PLANKS.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_LEAVES =
            block("emberwood_leaves", () -> FloraBlocks.EMBERWOOD_LEAVES.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_SAPLING =
            block("emberwood_sapling", () -> FloraBlocks.EMBERWOOD_SAPLING.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_SLAB =
            block("emberwood_slab", () -> FloraBlocks.EMBERWOOD_SLAB.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_STAIRS =
            block("emberwood_stairs", () -> FloraBlocks.EMBERWOOD_STAIRS.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_FENCE =
            block("emberwood_fence", () -> FloraBlocks.EMBERWOOD_FENCE.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_FENCE_GATE =
            block("emberwood_fence_gate", () -> FloraBlocks.EMBERWOOD_FENCE_GATE.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_DOOR =
            block("emberwood_door", () -> FloraBlocks.EMBERWOOD_DOOR.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_TRAPDOOR =
            block("emberwood_trapdoor", () -> FloraBlocks.EMBERWOOD_TRAPDOOR.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_BUTTON =
            block("emberwood_button", () -> FloraBlocks.EMBERWOOD_BUTTON.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_PRESSURE_PLATE =
            block("emberwood_pressure_plate", () -> FloraBlocks.EMBERWOOD_PRESSURE_PLATE.get());
    public static final DeferredItem<BlockItem> DOWNBLOOM_BALE =
            block("downbloom_bale", () -> FloraBlocks.DOWNBLOOM_BALE.get());
    public static final DeferredItem<BlockItem> EMBERWOOD_CHARCOAL_BLOCK =
            block("emberwood_charcoal_block", () -> FloraBlocks.EMBERWOOD_CHARCOAL_BLOCK.get());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("flora", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumflora.flora"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(LOOMFLAX_FIBRE.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : SHOWN) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    /** What burns, and for how long. Read by the burn-time handler. */
    public static int burnTicks(net.minecraft.world.item.Item item) {
        if (item == EMBERWOOD_CHARCOAL.get()) {
            return FloraRules.CHARCOAL_TICKS;
        }
        if (item == EMBERWOOD_CHARCOAL_BLOCK.get()) {
            return FloraRules.blockTicks();
        }
        if (item == EMBERWOOD_PLANKS.get() || item == EMBERWOOD_SLAB.get()
                || item == EMBERWOOD_STAIRS.get() || item == EMBERWOOD_FENCE.get()
                || item == EMBERWOOD_FENCE_GATE.get() || item == EMBERWOOD_LOG.get()
                || item == EMBERWOOD_WOOD.get() || item == STRIPPED_EMBERWOOD_LOG.get()
                || item == STRIPPED_EMBERWOOD_WOOD.get()) {
            return FloraRules.PLANK_TICKS;
        }
        return 0;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
