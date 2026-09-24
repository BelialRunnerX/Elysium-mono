package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.registry.BeastEntities;
import com.elysium.bestiary.rules.BeastRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Three layers, nine renderers, and not one name typed out.
 *
 * <p>Both loops walk {@link BeastRules}, so a tenth beast is a row and a
 * sprite. A renderer registered by hand for eight of nine beasts is how a pack
 * ends up with one mob rendering as a white-and-black cube, and it is only
 * ever found by someone meeting that mob.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class BestiaryClient {

    private BestiaryClient() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String family : BeastRules.FAMILIES) {
            event.registerLayerDefinition(BeastModel.LAYERS.get(family),
                    () -> BeastModel.createLayer(family));
        }
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            event.registerEntityRenderer(BeastEntities.ALL.get(beast.id()).get(),
                    context -> new BeastRenderer(context, beast.family()));
        }
    }
}
