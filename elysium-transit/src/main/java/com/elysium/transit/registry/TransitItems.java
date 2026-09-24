package com.elysium.transit.registry;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.item.VehicleItem;
import com.elysium.transit.rules.TransitRules;
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
public final class TransitItems {

    private TransitItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumTransit.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumTransit.MODID);

    public static final DeferredItem<BlockItem> WAYSTONE =
            ITEMS.registerSimpleBlockItem(TransitBlocks.WAYSTONE);
    public static final DeferredItem<BlockItem> PAVING =
            ITEMS.registerSimpleBlockItem(TransitBlocks.PAVING);
    public static final DeferredItem<BlockItem> GRAVEL_PATH =
            ITEMS.registerSimpleBlockItem(TransitBlocks.GRAVEL_PATH);

    public static final DeferredItem<VehicleItem> CART = ITEMS.register("cart",
            () -> new VehicleItem(TransitRules.CART, TransitEntities.CART::get));
    public static final DeferredItem<VehicleItem> TRUCK = ITEMS.register("truck",
            () -> new VehicleItem(TransitRules.TRUCK, TransitEntities.TRUCK::get));
    public static final DeferredItem<VehicleItem> HAULER = ITEMS.register("hauler",
            () -> new VehicleItem(TransitRules.HAULER, TransitEntities.HAULER::get));

    /** Which item puts a given rung down, for when one is broken. */
    public static Item itemFor(net.minecraft.world.entity.EntityType<?> type) {
        String name = net.minecraft.world.entity.EntityType.getKey(type).getPath();
        if (TransitRules.HAULER.name().equals(name)) {
            return HAULER.get();
        }
        if (TransitRules.TRUCK.name().equals(name)) {
            return TRUCK.get();
        }
        return CART.get();
    }

    private static final List<DeferredItem<BlockItem>> ORDER =
            List.of(WAYSTONE, PAVING, GRAVEL_PATH);

    private static final List<DeferredItem<VehicleItem>> RIDES =
            List.of(CART, TRUCK, HAULER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "transit", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumtransit"))
                    .icon(() -> new ItemStack(WAYSTONE.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                        for (DeferredItem<? extends Item> item : RIDES) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
