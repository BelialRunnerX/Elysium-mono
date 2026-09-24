package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.data.OreCatalogue;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The block items, and the one creative tab they live in.
 *
 * A tab of their own rather than scattering them through the vanilla building
 * blocks tab: a hundred and fifty ores dropped into someone else's tab is a
 * tab nobody can use any more, and this module's whole claim is that it tidies
 * up rather than adding to the pile.
 */
public final class OreItems {

    private OreItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumOres.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumOres.MODID);

    static {
        for (OreCatalogue.Entry entry : OreCatalogue.ENTRIES) {
            ITEMS.register(entry.name(),
                    () -> new BlockItem(OreBlocks.of(entry.name()).get(), new Item.Properties()));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("ores", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumores.ores"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(
                            OreBlocks.of(OreCatalogue.ENTRIES.get(0).name()).get()))
                    .displayItems((parameters, output) -> {
                        for (OreCatalogue.Entry entry : OreCatalogue.ENTRIES) {
                            output.accept(OreBlocks.of(entry.name()).get());
                        }
                    })
                    .build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
