package com.elysium.core.client;

import com.elysium.core.Elysium;
import com.elysium.core.screen.ReforgeTableScreen;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup.
 *
 * Without the screen registration the menu opens server-side with nothing to
 * show for it, which disconnects the player. Without the renderer the first
 * enforcer to spawn crashes the client.
 */
@EventBusSubscriber(modid = Elysium.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ElysiumClient {

    private ElysiumClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Elysium.REFORGE_TABLE_MENU.get(), ReforgeTableScreen::new);
    }

    /**
     * The enforcer renders through vanilla's zombie renderer.
     *
     * This is the whole reason it subclasses Zombie: ZombieRenderer already
     * draws worn armour, so an enforcer in a full Elysium set is visually
     * distinct without the mod shipping a model, a texture or a renderer of its
     * own — none of which could be checked without launching the game.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Elysium.IMPERIAL_ENFORCER.get(), ZombieRenderer::new);
        event.registerEntityRenderer(Elysium.UNSWORN_RAIDER.get(), HuskRenderer::new);
    }
}
