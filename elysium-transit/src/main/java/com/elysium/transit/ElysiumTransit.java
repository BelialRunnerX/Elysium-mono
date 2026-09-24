package com.elysium.transit;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Transit: the short way home, and the long way everywhere else.
 *
 * <h2>The one sentence</h2>
 *
 * A player who has built a factory should not have to walk, and the pack
 * charges them for that in the currency the factory produces. A waystone is a
 * machine on Age II's grid; wire one and the wait goes away.
 *
 * <h2>Why a teleport network is an arithmetic problem</h2>
 *
 * Any network of jumps with a price on it can be arbitraged unless the price
 * is subadditive: if going by way of somewhere is ever cheaper than going
 * direct, every long jump in the game is free to whoever notices. That is not
 * a hypothetical — it is what a "free under N blocks" allowance does, and it
 * is what the first draft of these rules had. The harness threw it out over a
 * million random triples before a single block existed.
 *
 * <h2>It sits beside the waygates rather than replacing them</h2>
 *
 * A waygate is found, costs nothing, and goes between worlds. A waystone is
 * built, costs power, and goes where you have been. Two answers to two
 * questions, and they share a screen so they do not look like two mods.
 */
@Mod(ElysiumTransit.MODID)
public class ElysiumTransit {

    public static final String MODID = "elysiumtransit";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumTransit");

    public ElysiumTransit(IEventBus modEventBus) {
        com.elysium.transit.registry.TransitAttachments.ATTACHMENTS.register(modEventBus);
        com.elysium.transit.registry.TransitBlocks.BLOCKS.register(modEventBus);
        com.elysium.transit.registry.TransitBlockEntities.TYPES.register(modEventBus);
        com.elysium.transit.registry.TransitEntities.TYPES.register(modEventBus);
        com.elysium.transit.registry.TransitItems.ITEMS.register(modEventBus);
        com.elysium.transit.registry.TransitItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Transit ready");
    }
}
