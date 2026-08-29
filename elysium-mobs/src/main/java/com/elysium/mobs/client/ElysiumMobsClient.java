package com.elysium.mobs.client;

import com.elysium.mobs.ElysiumMobs;
import com.elysium.mobs.client.model.AdeptModel;
import com.elysium.mobs.client.model.ChoirModel;
import com.elysium.mobs.client.model.DroneModel;
import com.elysium.mobs.client.model.LictorModel;
import com.elysium.mobs.client.model.PraetorModel;
import com.elysium.mobs.client.model.ReaverModel;
import com.elysium.mobs.client.model.ScavengerModel;
import com.elysium.mobs.client.model.WhisperModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Everything the client needs to draw these creatures.
 *
 * Two registrations per family, and both are required: the layer definition
 * supplies the geometry, and the renderer says which model class and texture to
 * use. Missing either produces a mob that exists on the server and is invisible
 * or absent on the client — which looks exactly like a mod that does not work,
 * and gives no error to search for.
 *
 * Client-only. The annotation's {@code value} keeps this class off a dedicated
 * server entirely; without it, a server would try to load renderer classes that
 * reference client code and crash on startup.
 */
@EventBusSubscriber(modid = ElysiumMobs.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ElysiumMobsClient {

    private ElysiumMobsClient() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElysiumMobLayers.SCAVENGER, ScavengerModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.REAVER, ReaverModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.WHISPER, WhisperModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.DRONE, DroneModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.LICTOR, LictorModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.ADEPT, AdeptModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.CHOIR, ChoirModel::createBodyLayer);
        event.registerLayerDefinition(ElysiumMobLayers.PRAETOR, PraetorModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ElysiumMobs.SCAVENGER.get(), ScavengerRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.REAVER.get(), ReaverRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.WHISPER.get(), WhisperRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.LICTOR.get(), LictorRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.ADEPT.get(), AdeptRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.CHOIR.get(), ChoirRenderer::new);
        event.registerEntityRenderer(ElysiumMobs.PRAETOR.get(), PraetorRenderer::new);
    }
}
