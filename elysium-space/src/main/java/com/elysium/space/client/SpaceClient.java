package com.elysium.space.client;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.registry.SpaceEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import com.elysium.space.registry.SpaceFluids;
import net.minecraft.resources.ResourceLocation;

@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpaceClient {

    private SpaceClient() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RocketModel.LAYER, RocketModel::createLayer);
        com.elysium.space.client.mob.MobRenderers.layers(event);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpaceEntities.ROCKET.get(), RocketRenderer::new);
        com.elysium.space.client.mob.MobRenderers.renderers(event);
    }

    @SubscribeEvent
    public static void onScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.elysium.space.registry.SpaceMenus.MACHINE.get(), MachineScreen::new);
    }

    @SubscribeEvent
    public static void onSkies(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        com.elysium.space.client.sky.SpaceSkies.register(event);
    }

    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    /** Each fluid is vanilla's water, tinted: no fluid art of our own until the S8 reskin. */
    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        for (SpaceFluids.Kind kind : SpaceFluids.ALL) {
            int tint = kind.tint;
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOW;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            }, kind.type.get());
        }
    }
}
