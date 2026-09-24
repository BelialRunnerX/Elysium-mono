package com.elysium.solarpunk;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Elysium Solarpunk — a building set for the other future.
 *
 * <h2>What this is</h2>
 *
 * Copper gone green, woven cane, rammed earth, lime plaster, mosaic tile,
 * glass that is lit from inside, and eight machines that work.
 * Rammed earth is dirt, gravel and clay; woven cane is bamboo; plaster is clay
 * and bone meal. The expensive end of the set costs copper and a little gold.
 * The point is that a player can build with this on the afternoon they install
 * it, not after an ore chain.
 *
 * <h2>Why it is its own mod and depends on nothing</h2>
 *
 * Every other content mod in this repository is built on elysium-lib, because
 * every one of them registers <em>into</em> the library: races, runes, rewards,
 * a bestiary, prices. This one is blocks. It has no standing, no stats, no
 * factions and no opinion about the Empire, so a dependency on the library
 * would be a requirement that buys the player nothing and stops the set being
 * installable on its own. See the note at the foot of neoforge.mods.toml.
 *
 * That is also why it is not folded into elysium-core, which already carries
 * two hundred items and five hundred data files, all of them about a different
 * game.
 *
 * <h2>The machines</h2>
 *
 * Eight of the blocks are machines, and all eight now do what their name says:
 * two generators that want different sites, a reclaimer, a kiln, and three
 * that read recipe books. {@code MACHINES.md} records that nothing is
 * outstanding.
 *
 * Every one of them started as decoration, and that was written down in three
 * places that could not drift apart — {@code MACHINES.md}, the classes under
 * {@code block/pending/}, and the tooltip the player read — with
 * {@code validate.py} failing if they ever disagreed. The scaffolding is still
 * here ({@link com.elysium.solarpunk.block.PendingMachine} is the marker) and
 * is deliberately kept: the empty list is a zero rather than an end, and a
 * ninth machine added to the table gets its stub, its row and its tooltip back
 * with nothing else to do.
 *
 * <h2>Where the content actually is</h2>
 *
 * {@link SolarpunkContent}, which is generated from the table in
 * {@code tools/gen_solarpunk.py} along with every blockstate, model, texture,
 * recipe, loot table, tag and language entry that goes with it. Thirty-three
 * blocks is too many to keep in sync by hand.
 */
@Mod(ElysiumSolarpunk.MODID)
public class ElysiumSolarpunk {

    public static final String MODID = "elysiumsolarpunk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElysiumSolarpunk(IEventBus modEventBus) {
        // Loads SolarpunkContent, which is what runs its registrations, and
        // attaches the three registers to the bus. Nothing is resolved here:
        // every get() in that class is inside a lambda that runs after the
        // registry events have fired.
        SolarpunkContent.register(modEventBus);

        LOGGER.info("Elysium Solarpunk ready: {} blocks", SolarpunkContent.blockCount());
    }
}
