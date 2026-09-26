package com.elysium.bestiary;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Bestiary: things that hunt you.
 *
 * <h2>The three ways more mobs makes a pack worse</h2>
 *
 * Seven jars in this pack exist to put more things in the dark, and between
 * them they bring three problems nobody ever reports as bugs, because all
 * three look like the game working.
 *
 * <p>The first is <b>a mob that is harder and worth less</b>. Every player
 * works it out inside a week and then avoids it for the rest of the save, and
 * the mob may as well not exist — and whoever added it never notices, because
 * they were balancing the fight rather than the ledger.
 *
 * <p>The second is <b>a mob that is far and away the best thing to farm</b>.
 * One mob with twice the reward per unit of danger turns every grinder in the
 * pack into a grinder for that mob, and the other thirty are scenery.
 *
 * <p>The third is <b>a bestiary the base game outfarms</b>. If a vanilla
 * zombie pays better per unit of danger than anything added, every mob farm in
 * the pack is a zombie farm and the whole module is decoration — and nobody
 * checks, because checking means measuring your own work against somebody
 * else's game.
 *
 * <p>All three are ratios over the whole set. So the stats here are chosen —
 * they are what a fight feels like — and the rewards are <em>worked out from
 * them</em> and never written down, and the harness proves that a harder thing
 * is always worth more, that best-to-worst farming sits inside a stated band,
 * and that the band contains the zombie. The first table written failed all of
 * that, three different ways; the second wrote the derived rewards back as
 * literals and they promptly disagreed with the sum that made them.
 */
@Mod(ElysiumBestiary.MODID)
public class ElysiumBestiary {

    public static final String MODID = "elysiumbestiary";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumBestiary");

    public ElysiumBestiary(IEventBus modEventBus) {
        com.elysium.bestiary.registry.BeastEntities.TYPES.register(modEventBus);
        com.elysium.bestiary.registry.BeastItems.ITEMS.register(modEventBus);
        com.elysium.bestiary.registry.BeastItems.TABS.register(modEventBus);
        com.elysium.bestiary.registry.FaunaEntities.TYPES.register(modEventBus);
        com.elysium.bestiary.registry.FaunaItems.ITEMS.register(modEventBus);
        com.elysium.bestiary.registry.FaunaItems.TABS.register(modEventBus);
        // Two more rosters register onto BeastEntities.TYPES from classes of
        // their own, and a class nothing loads is a roster nothing registers.
        // Touching them here makes that happen before the bus is handed the
        // register rather than whenever FML happens to scan the annotation —
        // and the symptom of getting it wrong is eight creatures that simply
        // are not in the game, with nothing in the log saying so.
        com.elysium.bestiary.registry.MythEntities.ALL.size();
        com.elysium.bestiary.registry.CompanionEntities.ALL.size();
        com.elysium.bestiary.registry.KinEntities.ALL.size();
        com.elysium.bestiary.registry.SummonEntities.BOLT.getId();
        LOGGER.info("Elysium Bestiary ready: {} beast(s), {} animal(s), {} myth(s) "
                        + "and {} companion(s)",
                com.elysium.bestiary.rules.BeastRules.BEASTS.size(),
                com.elysium.bestiary.rules.FaunaRules.ANIMALS.size(),
                com.elysium.bestiary.rules.MythRules.MYTHS.size(),
                com.elysium.bestiary.rules.CompanionRules.COMPANIONS.size());
    }
}
