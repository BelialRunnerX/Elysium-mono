package com.elysium.ascendancy;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Elysium Ascendancy — adventure loot: the weighting engine, and everything built on it.
 *
 * One of Elysium's separate module jars, merged into elysium-all with the
 * rest. It depends on the library and nothing else: not on elysium-core, and
 * not on the mod whose mechanics it replaces. See docs/SPEC_CLONE.md for the
 * build order it is working down.
 */
@Mod(ElysiumAscendancy.MODID)
public final class ElysiumAscendancy {

    public static final String MODID = "elysiumascendancy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElysiumAscendancy(IEventBus modEventBus) {
        LOGGER.info("Elysium Ascendancy ready");
    }
}
