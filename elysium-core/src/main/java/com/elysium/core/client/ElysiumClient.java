package com.elysium.core.client;

import com.elysium.core.Elysium;
import com.elysium.core.screen.ReforgeTableScreen;
import com.elysium.core.entity.ImperialEnforcer;
import com.elysium.core.entity.UnswornRaider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
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
        event.register(Elysium.GEM_CUTTING_MENU.get(),
                com.elysium.core.screen.GemCuttingScreen::new);
        event.register(Elysium.AUGMENTING_MENU.get(),
                com.elysium.core.screen.AugmentingScreen::new);
    }

    /**
     * Both combatants render on vanilla's zombie model, wearing their own skin.
     *
     * <h4>Why the model is vanilla's and the texture is not</h4>
     *
     * The model is vanilla's because these are Zombies, and being a Zombie is
     * what gives them their AI, their armour rendering and their immunity to
     * the sunlight that would otherwise delete the Empire's soldiers at dawn.
     * A model of our own would mean reimplementing all of that to change a
     * silhouette that is, correctly, a person.
     *
     * The texture was vanilla's too, and that was the mistake. The reasoning
     * at the time was that a zombie renderer already draws worn armour, so an
     * enforcer in a full Elysium set is distinct without the mod shipping any
     * rendering code. True, and not enough: a band-0 enforcer wears no armour
     * at all, so what the Empire sent after a player was a zombie in a green
     * shirt, next to ten families of purpose-built mobs.
     *
     * So: vanilla's renderer, subclassed for one method. Everything that made
     * ZombieRenderer the right answer is still the right answer.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Elysium.IMPERIAL_ENFORCER.get(),
                context -> new SkinnedZombieRenderer<ImperialEnforcer>(context,
                        skin("imperial_enforcer")));
        event.registerEntityRenderer(Elysium.UNSWORN_RAIDER.get(),
                context -> new SkinnedZombieRenderer<UnswornRaider>(context,
                        skin("unsworn_raider")));
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID,
                "textures/entity/" + name + ".png");
    }

    /**
     * Vanilla's zombie renderer with the texture swapped.
     *
     * {@code ZombieRenderer} hard-codes zombie.png in one method and does
     * everything else right — the three model layers, the armour layers, the
     * baby scaling, the conversion shake. Overriding that one method is the
     * whole change.
     */
    private static final class SkinnedZombieRenderer<T extends net.minecraft.world.entity.monster.Zombie>
            extends ZombieRenderer {

        private final ResourceLocation texture;

        private SkinnedZombieRenderer(EntityRendererProvider.Context context,
                                      ResourceLocation texture) {
            super(context);
            this.texture = texture;
        }

        @Override
        public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) {
            return texture;
        }
    }
}
