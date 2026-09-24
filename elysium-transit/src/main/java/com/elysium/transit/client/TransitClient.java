package com.elysium.transit.client;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.registry.TransitEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** One model layer and one renderer, shared by all three rungs. */
@EventBusSubscriber(modid = ElysiumTransit.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class TransitClient {

    private TransitClient() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VehicleModel.LAYER, VehicleModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TransitEntities.CART.get(), VehicleRenderer::new);
        event.registerEntityRenderer(TransitEntities.TRUCK.get(), VehicleRenderer::new);
        event.registerEntityRenderer(TransitEntities.HAULER.get(), VehicleRenderer::new);
    }
}
