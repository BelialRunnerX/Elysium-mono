package com.elysium.comforts.client;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.item.TraderItem;
import com.elysium.comforts.registry.ComfortItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * One property, and it is the only thing about a crate a player needs to see.
 *
 * <p>Whether there is somebody in it. A chest of identical crates is a chest
 * somebody opens one at a time, and the tooltip does not help because a
 * tooltip is one item at a time as well.
 */
@EventBusSubscriber(modid = ElysiumComforts.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ComfortsClient {

    private ComfortsClient() {
    }

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ComfortItems.TRADER.get(),
                ResourceLocation.fromNamespaceAndPath(ElysiumComforts.MODID, "occupied"),
                (stack, level, holder, seed) -> TraderItem.occupied(stack) ? 1.0F : 0.0F));
    }
}
