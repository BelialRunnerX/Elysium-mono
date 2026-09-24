package com.elysium.enchanting.client;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.registry.EnchantingMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** What this module draws. */
@EventBusSubscriber(modid = ElysiumEnchanting.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class EnchantingClient {

    private EnchantingClient() {
    }

    @SubscribeEvent
    public static void onScreens(RegisterMenuScreensEvent event) {
        event.register(EnchantingMenus.ARCANE_TABLE.get(), ArcaneTableScreen::new);
        event.register(EnchantingMenus.INFUSION_BASIN.get(), InfusionScreen::new);
        event.register(EnchantingMenus.SCRIPTORIUM.get(), ScriptoriumScreen::new);
    }
}
