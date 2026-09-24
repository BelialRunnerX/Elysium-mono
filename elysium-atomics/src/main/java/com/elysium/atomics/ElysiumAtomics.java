package com.elysium.atomics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Atomics: the third age.
 *
 * <h2>The fiction</h2>
 *
 * The Ascent. Somebody has stopped burning things. This is the age of the
 * sealed core and the coolant loop, of a shed full of batteries that means the
 * lights stay on, and of the first machine in the pack that can take the roof
 * off if you are careless with it.
 *
 * <h2>The mechanism, and how it differs from the ages below</h2>
 *
 * Age I is rigidly coupled: hang another machine on a line and the whole hall
 * slows down together. Age II is divided: ask a grid for more than it has and
 * every machine gets the same share. Age III is <em>buffered</em>: a grid that
 * has more than it needs keeps the difference, and a grid that has less spends
 * what it kept.
 *
 * <p>That is the first time in the spine that time is a resource. Every grid
 * until now has been instantaneous, so building more generation than you need
 * was waste; a cell makes surplus worth having and makes a factory survive the
 * night.
 *
 * <p>The second thing it adds is heat, which is the whole of a reactor's
 * danger. It settles rather than running away — a given throttle with a given
 * amount of coolant reaches a temperature and stays there — so a meltdown is
 * something a player causes by turning it up rather than something that
 * happens while they are in the Nether.
 *
 * <h2>What it names</h2>
 *
 * Vanilla, elysium-lib, elysium-kinetics and elysium-industry. The ages depend
 * downwards only, and this one does not declare a second model of electricity:
 * the grid, the voltage tiers and the wire that burns are Age II's.
 */
@Mod(ElysiumAtomics.MODID)
public class ElysiumAtomics {

    public static final String MODID = "elysiumatomics";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumAtomics");

    public ElysiumAtomics(IEventBus modEventBus) {
        com.elysium.atomics.registry.AtomicBlocks.BLOCKS.register(modEventBus);
        com.elysium.atomics.registry.AtomicItems.ITEMS.register(modEventBus);
        com.elysium.atomics.registry.AtomicItems.TABS.register(modEventBus);
        com.elysium.atomics.registry.AtomicBlockEntities.TYPES.register(modEventBus);
        com.elysium.atomics.registry.AtomicRecipes.TYPES.register(modEventBus);
        com.elysium.atomics.registry.AtomicRecipes.SERIALIZERS.register(modEventBus);
        LOGGER.info("Elysium Atomics ready");
    }
}
