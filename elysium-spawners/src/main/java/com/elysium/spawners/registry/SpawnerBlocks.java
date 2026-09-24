package com.elysium.spawners.registry;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.block.ElysiumSpawnerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** The one block this module adds, and the tab everything of its own is in. */
public final class SpawnerBlocks {

    private SpawnerBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumSpawners.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumSpawners.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumSpawners.MODID);

    /** Everything the tab shows, in the order it was registered. */
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final DeferredBlock<Block> SPAWNER =
            BLOCKS.register("spawner", ElysiumSpawnerBlock::new);

    public static final DeferredItem<Item> SPAWNER_ITEM =
            ITEMS.registerItem("spawner", properties -> new BlockItem(SPAWNER.get(), properties));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("elysiumspawners", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumspawners"))
                    .icon(() -> new ItemStack(SPAWNER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> entry : TAB_ORDER) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    static {
        TAB_ORDER.add(SPAWNER_ITEM);
    }

    /** Something registered elsewhere, shown in this module's tab. */
    static void inTab(DeferredItem<? extends Item> item) {
        TAB_ORDER.add(item);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
