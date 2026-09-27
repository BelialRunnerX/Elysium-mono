package com.elysium.comforts;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Comforts: the small mercies.
 *
 * <h2>The grave, and why it is the first thing here</h2>
 *
 * Dying should cost you something. It should not cost you the four hours of
 * your evening that went into what you were carrying, and it should not cost
 * you them silently — in lava, at the bottom of a ravine, while you run back
 * from a bed eight hundred blocks away and the despawn timer runs out.
 *
 * <p>So everything you were carrying goes into a grave where you fell, the
 * grave is somewhere a player can actually stand rather than in the void, and
 * it is yours alone until long enough has passed that you have plainly given
 * up on it. What dying costs is a quarter of your levels, which is the one
 * resource you can always get more of by playing.
 *
 * <p><b>Nothing a player owned is ever lost</b> is not a sentiment here, it is
 * a counting problem: a grave built to a fixed size eats whatever an inventory
 * mod added, and every gravestone mod that has lost somebody's backpack did it
 * by assuming forty-one slots. This one is built around what it is handed.
 */
@Mod(ElysiumComforts.MODID)
public class ElysiumComforts {

    public static final String MODID = "elysiumcomforts";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumComforts");

    public ElysiumComforts(IEventBus modEventBus) {
        com.elysium.comforts.registry.ComfortAttachments.ATTACHMENTS.register(modEventBus);
        com.elysium.comforts.registry.ComfortBlocks.BLOCKS.register(modEventBus);
        com.elysium.comforts.registry.ComfortBlockEntities.TYPES.register(modEventBus);
        com.elysium.comforts.registry.ComfortComponents.TYPES.register(modEventBus);
        com.elysium.comforts.registry.ComfortEntities.TYPES.register(modEventBus);
        com.elysium.comforts.registry.ComfortItems.ITEMS.register(modEventBus);
        com.elysium.comforts.registry.ComfortItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Comforts ready");
    }
}
