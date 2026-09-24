package com.elysium.trinkets.client;

import com.elysium.trinkets.ElysiumTrinkets;
import com.elysium.trinkets.item.PotionCharmItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * The one thing this module draws differently.
 *
 * <h2>Why the charm is tinted rather than drawn per potion</h2>
 *
 * A charm holds any potion there is, including ones added by mods that do not
 * know this exists. Drawing one sprite per potion is therefore impossible;
 * drawing one grey sprite makes every charm look alike in a hotbar. So the
 * sprite has two layers — the token, and a white field — and the field is
 * multiplied by the potion's own colour, which is exactly what vanilla does to
 * a bottle.
 */
@EventBusSubscriber(modid = ElysiumTrinkets.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class TrinketsClient {

    private TrinketsClient() {
    }

    /** The layer the potion's colour is painted on. Layer 0 is the metal. */
    public static final int TINTED_LAYER = 1;

    @SubscribeEvent
    public static void onColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            if (layer != TINTED_LAYER) {
                return -1;
            }
            PotionContents contents = PotionCharmItem.contentsOf(stack);
            return contents.equals(PotionContents.EMPTY) ? -1
                    : 0xFF000000 | contents.getColor();
        }, ElysiumTrinkets.POTION_CHARM.get());
    }
}
