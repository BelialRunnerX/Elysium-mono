package com.elysium.spawners;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Spawners: a spawner worth keeping.
 *
 * <h2>What this module replaces</h2>
 *
 * Vanilla's spawner is a fixed object a player finds, lights, and then ignores
 * for the rest of the world. It cannot be moved, tuned, turned off or improved,
 * so the only decision it ever poses is whether to break it — and breaking it
 * is almost always right, because a lit room is worth more than four zombies a
 * minute.
 *
 * <p>Here it is a thing to invest in: picked up with silk touch, carried,
 * placed where it is wanted, tuned along five axes and given behaviours with
 * runes. What it costs is real and it goes up every step.
 *
 * <h2>What it does not do</h2>
 *
 * It does not reach for Apothic Spawners, Apotheosis or Silent Gear, in any
 * direction, optional or otherwise. The validator enforces that, the same way
 * every other module in this repository enforces its own independence.
 */
@Mod(ElysiumSpawners.MODID)
public class ElysiumSpawners {

    public static final String MODID = "elysiumspawners";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumSpawners");

    public ElysiumSpawners(IEventBus modEventBus) {
        com.elysium.spawners.registry.SpawnerComponents.register(modEventBus);
        com.elysium.spawners.registry.SpawnerBlocks.register(modEventBus);
        com.elysium.spawners.registry.SpawnerItems.register(modEventBus);
        com.elysium.spawners.registry.SpawnerBlockEntities.register(modEventBus);
        com.elysium.spawners.registry.SpawnerLoot.register(modEventBus);
        LOGGER.info("Elysium Spawners ready");
    }
}
