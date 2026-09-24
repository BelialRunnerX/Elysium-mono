package com.elysium.spawners.client;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.registry.SpawnerBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** What this module draws. */
@EventBusSubscriber(modid = ElysiumSpawners.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SpawnersClient {

    private SpawnersClient() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SpawnerBlockEntities.SPAWNER.get(),
                SpawnerCageRenderer::new);
    }
}
