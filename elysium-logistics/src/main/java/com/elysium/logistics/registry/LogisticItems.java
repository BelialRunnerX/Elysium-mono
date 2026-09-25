package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.elysium.logistics.rules.CompressRules;
import com.elysium.logistics.rules.NetRules;
import com.elysium.logistics.rules.PackRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One item per block, and one tab to find them in. */
public final class LogisticItems {

    private LogisticItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumLogistics.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumLogistics.MODID);

    public static final DeferredItem<BlockItem> TIMBER_DRAWER =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.TIMBER_DRAWER);
    public static final DeferredItem<BlockItem> BRASS_DRAWER =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.BRASS_DRAWER);
    public static final DeferredItem<BlockItem> STEEL_DRAWER =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.STEEL_DRAWER);
    public static final DeferredItem<BlockItem> BUS =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.BUS);
    public static final DeferredItem<BlockItem> CONTROLLER =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.CONTROLLER);
    public static final DeferredItem<BlockItem> ASSEMBLY_CORE =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.ASSEMBLY_CORE);
    public static final DeferredItem<BlockItem> ASSEMBLER =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> TERMINAL =
            ITEMS.registerSimpleBlockItem(LogisticBlocks.TERMINAL);

    /**
     * One item per compression, walked from the same catalogue the blocks were.
     *
     * <p>A block with no item is a block only a command can place, and five
     * hundred of them would be five hundred nobody ever sees.
     */
    public static final Map<String, DeferredItem<BlockItem>> COMPRESSED = compressed();

    private static Map<String, DeferredItem<BlockItem>> compressed() {
        Map<String, DeferredItem<BlockItem>> made = new LinkedHashMap<>();
        for (Map.Entry<String,
                net.neoforged.neoforge.registries.DeferredBlock<
                        net.minecraft.world.level.block.Block>> entry
                : LogisticBlocks.COMPRESSED.entrySet()) {
            made.put(entry.getKey(),
                    ITEMS.registerSimpleBlockItem(entry.getValue()));
        }
        if (made.size() != CompressRules.howMany()) {
            throw new IllegalStateException("there are " + CompressRules.howMany()
                    + " compressions and " + made.size() + " items for them");
        }
        return Map.copyOf(made);
    }

    /** One item per device, walked from the roster the blocks were. */
    public static final Map<String, DeferredItem<BlockItem>> DEVICES = devices();

    private static Map<String, DeferredItem<BlockItem>> devices() {
        Map<String, DeferredItem<BlockItem>> made = new LinkedHashMap<>();
        for (Map.Entry<String,
                net.neoforged.neoforge.registries.DeferredBlock<
                        net.minecraft.world.level.block.Block>> entry
                : LogisticBlocks.DEVICES.entrySet()) {
            made.put(entry.getKey(),
                    ITEMS.registerSimpleBlockItem(entry.getValue()));
        }
        if (made.size() != NetRules.howMany()) {
            throw new IllegalStateException("there are " + NetRules.howMany()
                    + " devices and " + made.size() + " items for them");
        }
        return Map.copyOf(made);
    }

    /**
     * Every body and every part, walked from the same roster the arithmetic is.
     *
     * <p>A plain item apiece: what a pack <em>is</em> lives in
     * {@link PackRules}, and a class per bag would be eight places for the
     * same capacity bug.
     */
    public static final Map<String, DeferredItem<Item>> PACKS = packs();

    private static Map<String, DeferredItem<Item>> packs() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (String name : PackRules.catalogue()) {
            made.put(name, ITEMS.registerSimpleItem(name,
                    new Item.Properties().stacksTo(1)));
        }
        if (made.size() != PackRules.howMany()) {
            throw new IllegalStateException("the rules list " + PackRules.howMany()
                    + " packs and parts and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /** Hold it, join it up, index it, then have it build things for you. */
    private static final List<DeferredItem<BlockItem>> ORDER = List.of(
            TIMBER_DRAWER, BRASS_DRAWER, STEEL_DRAWER, BUS, CONTROLLER,
            ASSEMBLY_CORE, ASSEMBLER, TERMINAL);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "logistics", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumlogistics"))
                    .icon(() -> new ItemStack(STEEL_DRAWER.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                        // And the compressions, in the order the rules list
                        // them, so the tab reads as a ladder per material
                        // rather than as five hundred blocks in a heap.
                        for (String id : CompressRules.catalogue()) {
                            output.accept(COMPRESSED.get(id).get());
                        }
                        for (String id : NetRules.catalogue()) {
                            output.accept(DEVICES.get(id).get());
                        }
                        for (String id : PackRules.catalogue()) {
                            output.accept(PACKS.get(id).get());
                        }
                    })
                    .build());
}
