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

import java.util.List;

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
                    })
                    .build());
}
