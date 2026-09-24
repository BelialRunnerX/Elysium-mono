package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Two items, and one tab to find them in. */
public final class ComfortItems {

    private ComfortItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumComforts.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumComforts.MODID);

    public static final DeferredItem<BlockItem> GRAVE =
            ITEMS.registerSimpleBlockItem(ComfortBlocks.GRAVE);

    public static final DeferredItem<com.elysium.comforts.item.TraderItem> TRADER =
            ITEMS.register("trader_crate", () ->
                    new com.elysium.comforts.item.TraderItem(
                            new net.minecraft.world.item.Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "comforts", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumcomforts"))
                    .icon(() -> new ItemStack(GRAVE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(GRAVE.get());
                        output.accept(TRADER.get());
                    })
                    .build());
}
