package com.elysium.agronomy.client;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.registry.BeeEntities;
import com.elysium.agronomy.registry.FishEntities;
import com.elysium.agronomy.rules.BeeRules;
import com.elysium.agronomy.rules.FishRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Seventy-five renderers, and not one species name typed out.
 *
 * <p>The loop walks the roster. A renderer registered by hand for
 * seventy-four of seventy-five bees is how a pack ends up with one mob
 * rendering as a black-and-magenta cube, and it is only ever found by the
 * player who breeds that one.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class AgronomyClient {

    private AgronomyClient() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BeeModel.LAYER, BeeModel::createLayer);
        event.registerLayerDefinition(FishModel.LAYER, FishModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BeeRules.Bee bee : BeeRules.BEES) {
            event.registerEntityRenderer(BeeEntities.of(bee.id()), BeeRenderer::new);
        }
        for (FishRules.Fish one : FishRules.FISH) {
            event.registerEntityRenderer(FishEntities.of(one.id()), FishRenderer::new);
        }
    }
}
