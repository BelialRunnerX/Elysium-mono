package com.elysium.enchanting.registry;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.block.ArcaneTableBlock;
import com.elysium.enchanting.block.InfusionBasinBlock;
import com.elysium.enchanting.block.ScriptoriumBlock;
import com.elysium.enchanting.block.ShelfBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks this module adds, and the tab they are found in.
 *
 * <h2>Its own tab</h2>
 *
 * Not elysium-core's: this module does not depend on elysium-core and cannot
 * name its tab, and a pack running enchanting without the content mod would
 * have a table it could only obtain by command. The tab is filled in
 * registration order by {@link #blockItem}, so an item added below is an item
 * in the tab — the alternative is a list kept beside the registrations that
 * somebody eventually forgets to add to.
 */
public final class EnchantingBlocks {

    private EnchantingBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumEnchanting.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumEnchanting.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumEnchanting.MODID);

    /** Everything the tab shows, in the order it was registered. */
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final DeferredBlock<Block> ARCANE_TABLE =
            BLOCKS.register("arcane_table", ArcaneTableBlock::new);

    public static final DeferredItem<Item> ARCANE_TABLE_ITEM =
            blockItem("arcane_table", ARCANE_TABLE);

    public static final DeferredBlock<Block> INFUSION_BASIN =
            BLOCKS.register("infusion_basin", InfusionBasinBlock::new);

    public static final DeferredItem<Item> INFUSION_BASIN_ITEM =
            blockItem("infusion_basin", INFUSION_BASIN);

    public static final DeferredBlock<Block> SCRIPTORIUM =
            BLOCKS.register("scriptorium",
                    () -> new ScriptoriumBlock(ScriptoriumBlock.Size.SHELF));

    public static final DeferredItem<Item> SCRIPTORIUM_ITEM =
            blockItem("scriptorium", SCRIPTORIUM);

    public static final DeferredBlock<Block> GRAND_SCRIPTORIUM =
            BLOCKS.register("grand_scriptorium",
                    () -> new ScriptoriumBlock(ScriptoriumBlock.Size.GRAND));

    public static final DeferredItem<Item> GRAND_SCRIPTORIUM_ITEM =
            blockItem("grand_scriptorium", GRAND_SCRIPTORIUM);

    // The made shelves. Order is the order of the tab, which is roughly the
    // order a player meets them: the Nether, then the deep, then the End, then
    // the two that are only worth making once a room is already good.
    public static final DeferredBlock<Block> HELLSHELF = shelf("hellshelf", MapColor.NETHER, 7);
    public static final DeferredItem<Item> HELLSHELF_ITEM = blockItem("hellshelf", HELLSHELF);

    public static final DeferredBlock<Block> DEEPSHELF = shelf("deepshelf", MapColor.COLOR_BLACK, 3);
    public static final DeferredItem<Item> DEEPSHELF_ITEM = blockItem("deepshelf", DEEPSHELF);

    public static final DeferredBlock<Block> SIGHTSHELF =
            shelf("sightshelf", MapColor.COLOR_PURPLE, 5);
    public static final DeferredItem<Item> SIGHTSHELF_ITEM = blockItem("sightshelf", SIGHTSHELF);

    public static final DeferredBlock<Block> ENDSHELF = shelf("endshelf", MapColor.COLOR_MAGENTA, 6);
    public static final DeferredItem<Item> ENDSHELF_ITEM = blockItem("endshelf", ENDSHELF);

    public static final DeferredBlock<Block> KEEPERS_SHELF =
            shelf("keepers_shelf", MapColor.GOLD, 4);
    public static final DeferredItem<Item> KEEPERS_SHELF_ITEM =
            blockItem("keepers_shelf", KEEPERS_SHELF);

    public static final DeferredBlock<Block> ARCHIVE_COLUMN =
            shelf("archive_column", MapColor.COLOR_LIGHT_BLUE, 4);
    public static final DeferredItem<Item> ARCHIVE_COLUMN_ITEM =
            blockItem("archive_column", ARCHIVE_COLUMN);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("elysiumenchanting", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumenchanting"))
                    .icon(() -> new ItemStack(ARCANE_TABLE_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> entry : TAB_ORDER) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    /** A shelf: no behaviour, and the four numbers it gives live in its data file. */
    private static DeferredBlock<Block> shelf(String name, MapColor colour, int light) {
        return BLOCKS.register(name, () -> new ShelfBlock(colour, light));
    }

    /** Something registered elsewhere, shown in this module's tab. */
    static void inTab(DeferredItem<? extends Item> item) {
        TAB_ORDER.add(item);
    }

    /** A block's item, registered and put in the tab in one place. */
    private static DeferredItem<Item> blockItem(String name, DeferredBlock<Block> block) {
        DeferredItem<Item> item =
                ITEMS.registerItem(name, properties -> new BlockItem(block.get(), properties));
        TAB_ORDER.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
