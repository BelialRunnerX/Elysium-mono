package com.elysium.armory.client;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryTint;
import com.elysium.armory.registry.ArmoryComponents;
import com.elysium.armory.registry.ArmoryItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Tints the grey sprites: layer 0 the part's material, layer 1 the handle's. */
@EventBusSubscriber(modid = ElysiumArmory.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArmoryClient {

    private ArmoryClient() {
    }

    @SubscribeEvent
    public static void onScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.elysium.armory.registry.ArmoryMenus.SMITHING.get(), ArmorySmithingScreen::new);
        event.register(com.elysium.armory.registry.ArmoryMenus.SALVAGER.get(), ArmorySalvagerScreen::new);
        event.register(com.elysium.armory.registry.ArmoryMenus.REFORGING.get(), ArmoryReforgingScreen::new);
    }

    @SubscribeEvent
    public static void onItemColours(RegisterColorHandlersEvent.Item event) {
        ArmoryItems.gear().values().forEach(item -> event.register(ArmoryClient::tint, item.get()));
        ArmoryItems.parts().values().forEach(item -> event.register(ArmoryClient::tint, item.get()));
    }

    private static int tint(net.minecraft.world.item.ItemStack stack, int layer) {
        return stack.getOrDefault(ArmoryComponents.TINT.get(), ArmoryTint.PLAIN).layer(layer);
    }
}
