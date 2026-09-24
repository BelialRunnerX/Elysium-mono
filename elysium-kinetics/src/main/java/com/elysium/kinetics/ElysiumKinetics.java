package com.elysium.kinetics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Kinetics: the first age of machinery.
 *
 * <h2>The fiction</h2>
 *
 * Nobody in Elysium invents this. The gearing was already in the ground when
 * the Ascendancy arrived — salvaged out of pre-collapse works, cleaned up, and
 * restarted with the three things anyone can get hold of: timber, brass and
 * falling water. Age I is not a technology tree, it is a repair job.
 *
 * <h2>The mechanism</h2>
 *
 * A <b>line</b> is one rigidly coupled set of shafts. Sources push along a
 * speed-torque curve — strong when slow, giving up as they approach their free
 * speed — and machines draw a constant torque, so a line settles at the speed
 * where the two balance. Hang another millstone off it and the whole line gets
 * slower. Hang enough and it stops.
 *
 * <p>That is deliberately not how Create does it, and the difference is the
 * point: there is no OVERSTRESSED light. The line degrades, the gauge climbs,
 * the fussiest machine stops first and says what speed it wanted. See
 * {@code docs/SPEC_TECH.md} for the arithmetic and the reasoning.
 *
 * <h2>What it names</h2>
 *
 * Vanilla and elysium-lib. Ages II, III and IV are built on this module's
 * rotation, but this module knows nothing about them — the dependency runs one
 * way, as it does everywhere else in this project.
 */
@Mod(ElysiumKinetics.MODID)
public class ElysiumKinetics {

    public static final String MODID = "elysiumkinetics";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumKinetics");

    public ElysiumKinetics(IEventBus modEventBus) {
        com.elysium.kinetics.registry.KineticBlocks.BLOCKS.register(modEventBus);
        com.elysium.kinetics.registry.KineticItems.ITEMS.register(modEventBus);
        com.elysium.kinetics.registry.KineticItems.TABS.register(modEventBus);
        com.elysium.kinetics.registry.KineticBlockEntities.TYPES.register(modEventBus);
        com.elysium.kinetics.registry.KineticRecipes.TYPES.register(modEventBus);
        com.elysium.kinetics.registry.KineticRecipes.SERIALIZERS.register(modEventBus);
        LOGGER.info("Elysium Kinetics ready");
    }
}
