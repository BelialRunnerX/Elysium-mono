package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.registry.BeastEntities;
import com.elysium.bestiary.registry.FaunaEntities;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.FaunaRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Seven layers, twenty-one renderers, and not one name typed out.
 *
 * <p>Every loop walks a roster, so a tenth beast or a thirteenth animal is a
 * row and a sprite. A renderer registered by hand for eight of nine is how a
 * pack ends up with one mob rendering as a white-and-black cube, and it is
 * only ever found by someone meeting that mob.
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
        for (String kind : FaunaRules.KINDS) {
            event.registerLayerDefinition(FaunaModel.LAYERS.get(kind),
                    () -> FaunaModel.createLayer(kind));
        }
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            event.registerEntityRenderer(BeastEntities.ALL.get(beast.id()).get(),
                    context -> new BeastRenderer(context, beast.family()));
        }
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            event.registerEntityRenderer(FaunaEntities.ALL.get(animal.id()).get(),
                    context -> new FaunaRenderer(context, animal.kind()));
        }
    }
}
