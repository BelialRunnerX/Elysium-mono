package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One item, and one tab to find it in. */
public final class ArcanaItems {

    private ArcanaItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArcana.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumArcana.MODID);

    public static final DeferredItem<BlockItem> TRANSMUTER =
            ITEMS.registerSimpleBlockItem(ArcanaBlocks.TRANSMUTER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "arcana", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumarcana"))
                    .icon(() -> new ItemStack(TRANSMUTER.get()))
                    .displayItems((parameters, output) -> output.accept(TRANSMUTER.get()))
                    .build());
}
