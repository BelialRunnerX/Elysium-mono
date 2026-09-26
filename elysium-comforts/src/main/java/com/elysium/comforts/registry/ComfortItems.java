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

/** Two items, thirty-three blocks to carry, and one tab to find them in. */
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

    /** One block item per place to sleep, and the rope. */
    public static final java.util.Map<String, DeferredItem<BlockItem>> RESTS = rests();

    private static java.util.Map<String, DeferredItem<BlockItem>> rests() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, ?> one
                : ComfortBlocks.RESTS.entrySet()) {
            made.put(one.getKey(), ITEMS.registerSimpleBlockItem(
                    ComfortBlocks.RESTS.get(one.getKey())));
        }
        made.put(com.elysium.comforts.rules.RestRules.ROPE,
                ITEMS.registerSimpleBlockItem(ComfortBlocks.ROPE));
        for (String one : ComfortBlocks.CHIMES.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(ComfortBlocks.CHIMES.get(one)));
        }
        for (String one : ComfortBlocks.STATIONS.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(ComfortBlocks.STATIONS.get(one)));
        }
        int wanted = com.elysium.comforts.rules.RestRules.roster().size()
                + com.elysium.comforts.rules.ChimeRules.roster().size()
                + com.elysium.comforts.rules.StationRules.STATIONS.size();
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " thing(s) to carry and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "comforts", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumcomforts"))
                    .icon(() -> new ItemStack(GRAVE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(GRAVE.get());
                        output.accept(TRADER.get());
                        for (DeferredItem<BlockItem> one : RESTS.values()) {
                            output.accept(one.get());
                        }
                    })
                    .build());
}
