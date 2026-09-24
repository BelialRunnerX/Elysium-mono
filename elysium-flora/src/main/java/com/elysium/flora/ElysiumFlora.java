package com.elysium.flora;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Flora: the two materials nobody can farm.
 *
 * <h2>Why these and not more plants</h2>
 *
 * String and charcoal gate a surprising amount of the early game — every bow,
 * every bed, every torch, every fishing rod — and neither can be grown. String
 * comes off spiders, which means off a mob farm or off luck; charcoal comes
 * out of a furnace fed with the wood you were going to build with. A player
 * who wants either has to stop doing what they were doing.
 *
 * <p>So: two fibre crops and a tree that grows where nothing should. Nothing
 * here replaces the existing roads — spiders still drop string, coal is still
 * under your feet, and the farmed versions are deliberately the slower option
 * — it adds the road that was missing, which is the one you can plan.
 *
 * <h2>What it names</h2>
 *
 * Vanilla and elysium-lib. The world tier decides what a field pays, which is
 * the only reason the library is here; there is no dependency on any content
 * module and the validator enforces that.
 */
@Mod(ElysiumFlora.MODID)
public class ElysiumFlora {

    public static final String MODID = "elysiumflora";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumFlora");

    public ElysiumFlora(IEventBus modEventBus) {
        com.elysium.flora.registry.FloraBlocks.register(modEventBus);
        com.elysium.flora.registry.FloraItems.register(modEventBus);
        LOGGER.info("Elysium Flora ready");
    }
}
