package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.menu.ElysiumChests;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The kit's box screen, for every chest menu type the library declares. */
@EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ElysiumChestScreens {
    private ElysiumChestScreens() {
    }

    @SubscribeEvent
    public static void onScreens(RegisterMenuScreensEvent event) {
        for (DeferredHolder<MenuType<?>, MenuType<ChestMenu>> type : ElysiumChests.TYPES) {
            event.<ChestMenu, ElysiumChestScreen<ChestMenu>>register(type.get(),
                    (menu, inventory, title) -> new ElysiumChestScreen<>(menu, inventory, title));
        }
    }
}
