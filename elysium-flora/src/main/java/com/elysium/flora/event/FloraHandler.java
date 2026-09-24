package com.elysium.flora.event;

import com.elysium.flora.ElysiumFlora;
import com.elysium.flora.registry.FloraItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

/**
 * How long emberwood burns.
 *
 * <h2>Why an event and not a tag</h2>
 *
 * Because a tag can say that something is fuel and cannot say for how long. The
 * numbers are {@link com.elysium.flora.rules.FloraRules}', and the harness pins
 * the relationships between them, so this file is only the wiring.
 */
@EventBusSubscriber(modid = ElysiumFlora.MODID)
public final class FloraHandler {

    private FloraHandler() {
    }

    @SubscribeEvent
    public static void onBurnTime(FurnaceFuelBurnTimeEvent event) {
        int ticks = FloraItems.burnTicks(event.getItemStack().getItem());
        if (ticks > 0) {
            event.setBurnTime(ticks);
        }
    }
}
