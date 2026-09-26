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
        event.registerLayerDefinition(ShadeRenderer.LAYER, ShadeModel::createLayer);
        event.registerLayerDefinition(CompanionModel.LAYER,
                CompanionModel::createLayer);
        // One layer per shape a myth can wear, walked from the rules. A myth
        // whose layer was never registered is a bake that throws, which takes
        // the renderer with it.
        for (String form : com.elysium.bestiary.rules.MythRules.forms()) {
            event.registerLayerDefinition(MythModel.LAYERS.get(form),
                    () -> MythModel.createLayer(form));
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
        // And the two the ability system puts in the world. They were
        // registered, wired into the library and spawned by three abilities
        // for a while with nothing drawing either — which is not a quiet
        // failure: the first shade a player summons takes the client down.
        event.registerEntityRenderer(
                com.elysium.bestiary.registry.SummonEntities.SHADE.get(),
                ShadeRenderer::new);
        event.registerEntityRenderer(
                com.elysium.bestiary.registry.SummonEntities.BOLT.get(),
                RelicBoltRenderer::new);
        for (com.elysium.bestiary.rules.MythRules.Myth myth
                : com.elysium.bestiary.rules.MythRules.MYTHS) {
            event.registerEntityRenderer(
                    com.elysium.bestiary.registry.MythEntities.ALL
                            .get(myth.id()).get(),
                    context -> new MythRenderer(context, myth.form()));
        }
        for (com.elysium.bestiary.rules.CompanionRules.Companion one
                : com.elysium.bestiary.rules.CompanionRules.COMPANIONS) {
            event.registerEntityRenderer(
                    com.elysium.bestiary.registry.CompanionEntities.ALL
                            .get(one.id()).get(), CompanionRenderer::new);
        }
    }
}
