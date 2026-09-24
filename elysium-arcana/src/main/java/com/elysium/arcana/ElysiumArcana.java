package com.elysium.arcana;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Arcana: turning things into other things.
 *
 * <h2>What it is</h2>
 *
 * Dissolve something and you get most of what it was worth as motes. Spend
 * motes and you get back anything you have dissolved before. That is the whole
 * of it, and it is the oldest idea in modded Minecraft.
 *
 * <h2>What is different about this one</h2>
 *
 * Every mod that has ever priced every item in the game has shipped an
 * arrangement that makes value out of nothing — craft A into B, dissolve B,
 * condense A, come out ahead. It is not a rare bug; it is the default outcome
 * of deriving ten thousand numbers from ten thousand recipes and having nobody
 * read the result.
 *
 * <p>Two rules make it impossible here. No recipe may make more than it
 * consumes, which is guaranteed by taking the <em>cheapest</em> way of
 * arriving at each item and then checked over the whole table anyway. And a
 * round trip loses a tenth, so even a rounding error cannot be ridden. Over a
 * million random acts of crafting, dissolving and condensing, the harness has
 * never seen a player's estate rise.
 */
@Mod(ElysiumArcana.MODID)
public class ElysiumArcana {

    public static final String MODID = "elysiumarcana";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumArcana");

    public ElysiumArcana(IEventBus modEventBus) {
        com.elysium.arcana.registry.ArcanaAttachments.ATTACHMENTS.register(modEventBus);
        com.elysium.arcana.registry.ArcanaBlocks.BLOCKS.register(modEventBus);
        com.elysium.arcana.registry.ArcanaItems.ITEMS.register(modEventBus);
        com.elysium.arcana.registry.GeodeBlocks.BLOCKS.register(modEventBus);
        com.elysium.arcana.registry.GeodeBlocks.ITEMS.register(modEventBus);
        com.elysium.arcana.registry.GeodeItems.ITEMS.register(modEventBus);
        com.elysium.arcana.registry.RelicItems.ITEMS.register(modEventBus);
        com.elysium.arcana.registry.ArcanaItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Arcana ready");
    }
}
