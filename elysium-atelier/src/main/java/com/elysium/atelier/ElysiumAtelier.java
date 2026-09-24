package com.elysium.atelier;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Atelier: the shapes a house is made of.
 *
 * <h2>What it is</h2>
 *
 * Twelve shapes the game does not have — somewhere to sit, somewhere to put
 * things, and the pieces that make a wall look built — in sixteen materials it
 * already has. Two hundred blocks, all of them wearing a vanilla texture, and
 * not one new dependency.
 *
 * <h2>The one thing a decoration mod gets wrong</h2>
 *
 * A way of turning a material into a shape and back again that comes out
 * ahead. It is never deliberate: somebody writes "four planks make four
 * chairs" because four is a nice number, somebody else writes "a chair gives a
 * plank back" because it would be annoying not to, and nobody reads the four
 * hundred recipes that result.
 *
 * <p>So every shape has a price in eighths of a block, every recipe is
 * generated from that price rather than typed, and the harness plays two
 * thousand lives of crafting and breaking at random and fails if any of them
 * ever comes back with more wood than it started with.
 */
@Mod(ElysiumAtelier.MODID)
public class ElysiumAtelier {

    public static final String MODID = "elysiumatelier";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumAtelier");

    public ElysiumAtelier(IEventBus modEventBus) {
        com.elysium.atelier.registry.AtelierBlocks.BLOCKS.register(modEventBus);
        com.elysium.atelier.registry.AtelierItems.ITEMS.register(modEventBus);
        com.elysium.atelier.registry.AtelierItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Atelier ready: {} blocks",
                com.elysium.atelier.rules.AtelierRules.howMany());
    }
}
