package com.elysium.arsenal;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Arsenal: the tools that take a wall at a time.
 *
 * <h2>Why a hammer is hard to add</h2>
 *
 * Clearing a three-by-three in one swing is a good idea — quarrying by hand is
 * the least interesting hour in the game — and it is almost impossible to do
 * without making the pickaxe pointless. Once it is pointless, a whole tier of
 * the game's crafting is a step nobody takes for its own sake.
 *
 * <h2>So every one of these is worse at everything except breadth</h2>
 *
 * Slower per swing than the tool it replaces, and every block it takes costs a
 * point of durability exactly as if it had been mined singly. That leaves one
 * band of speed where a broad tool is worth carrying — slower than the
 * specialist on one block, faster over a full nine — and the harness sweeps a
 * thousand speeds to check the band is a band rather than an opinion.
 */
@Mod(ElysiumArsenal.MODID)
public class ElysiumArsenal {

    public static final String MODID = "elysiumarsenal";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumArsenal");

    public ElysiumArsenal(IEventBus modEventBus) {
        com.elysium.arsenal.registry.ArsenalItems.ITEMS.register(modEventBus);
        com.elysium.arsenal.registry.RangedItems.ITEMS.register(modEventBus);
        com.elysium.arsenal.registry.UniqueItems.ITEMS.register(modEventBus);
        com.elysium.arsenal.registry.ForgeItems.ITEMS.register(modEventBus);
        com.elysium.arsenal.forge.ForgeComponents.TYPES.register(modEventBus);
        com.elysium.arsenal.registry.DigBlocks.BLOCKS.register(modEventBus);
        com.elysium.arsenal.registry.DigItems.ITEMS.register(modEventBus);
        com.elysium.arsenal.registry.DigBlockEntities.TYPES.register(modEventBus);
        com.elysium.arsenal.registry.ArsenalItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Arsenal ready: {} tool(s)",
                com.elysium.arsenal.rules.ArsenalRules.catalogue().size());
    }
}
