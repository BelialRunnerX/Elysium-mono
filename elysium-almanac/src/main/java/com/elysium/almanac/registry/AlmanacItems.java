package com.elysium.almanac.registry;

import com.elysium.almanac.ElysiumAlmanac;
import com.elysium.almanac.item.AlmanacItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One item: the book. */
public final class AlmanacItems {

    private AlmanacItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAlmanac.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumAlmanac.MODID);

    public static final DeferredItem<AlmanacItem> ALMANAC =
            ITEMS.register("almanac", AlmanacItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "almanac", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumalmanac"))
                    .icon(() -> new ItemStack(ALMANAC.get()))
                    .displayItems((parameters, output) -> output.accept(ALMANAC.get()))
                    .build());
}
