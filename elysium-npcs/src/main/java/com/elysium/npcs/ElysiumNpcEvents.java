package com.elysium.npcs;

import com.elysium.npcs.entity.ImperialEnvoy;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * The mod-bus listeners, which is one of them.
 *
 * An entity type with no registered attributes throws when the first one
 * spawns, with a message that names the attribute and not the entity. Its own
 * class rather than a lambda in the constructor so the failure, if it ever
 * happens, has a file to point at.
 */
public final class ElysiumNpcEvents {

    private ElysiumNpcEvents() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ElysiumNpcs.ENVOY.get(), ImperialEnvoy.attributes().build());
    }
}
