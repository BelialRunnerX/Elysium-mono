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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * One property, and every bird this module ships.
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

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PigeonRenderer.LAYER, PigeonModel::createLayer);
    }

    /**
     * A renderer for every bird, walked from the rules.
     *
     * <p>Walked rather than listed, because a renderer registered for one bird
     * of two is a pack with one creature drawn as a black-and-magenta cube, and
     * it is only ever found by the player who meets that one.
     */
    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (com.elysium.comforts.rules.PostRules.Bird bird
                : com.elysium.comforts.rules.PostRules.BIRDS) {
            event.registerEntityRenderer(
                    com.elysium.comforts.registry.ComfortEntities.ALL
                            .get(bird.id()).get(), PigeonRenderer::new);
        }
    }
}
