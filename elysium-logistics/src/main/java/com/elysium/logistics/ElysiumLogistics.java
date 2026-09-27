package com.elysium.logistics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Logistics: where everything goes.
 *
 * <h2>Why this is not an age</h2>
 *
 * The four ages are a ladder and you climb them in order. Storage is not like
 * that: a drawer is one of the first things a player builds, a network arrives
 * with the first grid, and a digital core waits for a buffered one. It sits
 * <em>across</em> the ladder rather than on a rung of it, which is why it is
 * its own module and why it names Age I and Age II rather than being part of
 * either.
 *
 * <h2>Four ideas out of seventeen jars</h2>
 *
 * A drawer holds one thing and a great deal of it, for free, where you can see
 * it. A network indexes drawers and is a machine on the grid like any other —
 * it draws power, runs at whatever share the grid can afford, and slows rather
 * than stopping. A core stores what a warehouse could not. And autocrafting,
 * which is the one genuinely hard thing here and the reason most of
 * {@code LogisticRules} is about proving that a plan terminates.
 *
 * <h2>The two properties everything rests on</h2>
 *
 * Items are never created and never destroyed except by something the player
 * asked for, and a crafting plan always comes back whatever nonsense is in the
 * recipe set. Both are proved by execution rather than by reading.
 */
@Mod(ElysiumLogistics.MODID)
public class ElysiumLogistics {

    public static final String MODID = "elysiumlogistics";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumLogistics");

    public ElysiumLogistics(IEventBus modEventBus) {
        com.elysium.logistics.registry.LogisticBlocks.BLOCKS.register(modEventBus);
        // The bus is a run; its plates are the network's teal until a dye says otherwise.
        com.elysium.lib.run.Runs.declare(com.elysium.logistics.registry.LogisticBlocks.BUS, 0x5FB3CE);
        com.elysium.logistics.registry.LogisticItems.ITEMS.register(modEventBus);
        com.elysium.logistics.registry.LogisticItems.TABS.register(modEventBus);
        com.elysium.logistics.registry.LogisticBlockEntities.TYPES.register(modEventBus);
        com.elysium.logistics.registry.LogisticComponents.COMPONENTS.register(modEventBus);
        LOGGER.info("Elysium Logistics ready");
    }
}
