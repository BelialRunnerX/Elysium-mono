package com.elysium.wilds.registry;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.item.BiomeCompassItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The compass, the frame a way into the Vale is built from, and a tab to find them in. */
public final class WildItems {

    private WildItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumWilds.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumWilds.MODID);

    public static final DeferredItem<BiomeCompassItem> COMPASS = ITEMS.register(
            "biome_compass", () -> new BiomeCompassItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> VALE_FRAME =
            ITEMS.registerSimpleBlockItem(WildBlocks.VALE_FRAME);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "wilds", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumwilds"))
                    .icon(() -> new ItemStack(COMPASS.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(COMPASS.get());
                        output.accept(VALE_FRAME.get());
                    })
                    .build());
}
