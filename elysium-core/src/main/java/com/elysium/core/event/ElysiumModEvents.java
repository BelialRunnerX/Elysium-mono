package com.elysium.core.event;

import com.elysium.core.Elysium;
import com.elysium.core.entity.ImperialEnforcer;
import com.elysium.core.entity.UnswornRaider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Mod-bus setup that is not a registry.
 *
 * An entity type without an attribute supplier crashes the moment one is
 * spawned, so this is not optional bookkeeping.
 */
@EventBusSubscriber(modid = Elysium.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ElysiumModEvents {

    private ElysiumModEvents() {
    }

    @SubscribeEvent
    public static void createEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(Elysium.IMPERIAL_ENFORCER.get(),
                ImperialEnforcer.createEnforcerAttributes().build());
        event.put(Elysium.UNSWORN_RAIDER.get(),
                UnswornRaider.createRaiderAttributes().build());
    }
}
