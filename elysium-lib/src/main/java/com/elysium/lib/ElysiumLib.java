package com.elysium.lib;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumComponents;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.stats.ElysiumStats;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Elysium Library — the engine, with no content of its own.
 *
 * <h2>What this is</h2>
 *
 * Everything Elysium mods share and nothing anyone can hold: the character and
 * stat systems, the psionic elements and their counter matrix, the gear and
 * rune framework, faction standing, and the event handlers that make all of it
 * actually happen during play.
 *
 * It registers no items, no blocks, no mobs and no recipes. Installed on its
 * own it is inert but not broken — characters still level, stats still apply,
 * standing still accrues. There is simply nothing to mine and nobody to fight
 * about it.
 *
 * <h2>What a mod built on it registers</h2>
 *
 * <table border="1">
 *   <caption>Extension points</caption>
 *   <tr><th>Registry</th><th>What it adds</th></tr>
 *   <tr><td>{@code ElysiumElement.REGISTRY}</td>
 *       <td>a psionic element and the elements it answers</td></tr>
 *   <tr><td>{@code ElysiumStat.REGISTRY}</td>
 *       <td>a stat that persists, displays and can be spent into</td></tr>
 *   <tr><td>{@code ElysiumRace.REGISTRY}</td>
 *       <td>a race: starting stats, growth, and a passive</td></tr>
 *   <tr><td>{@code ElysiumClass.REGISTRY}</td>
 *       <td>a class: growth and a passive</td></tr>
 *   <tr><td>{@code ElysiumRune.REGISTRY}</td>
 *       <td>a rune: an affix, an effect, or both</td></tr>
 *   <tr><td>{@code ElysiumGearMaterial.REGISTRY}</td>
 *       <td>a material: element, tier, and a tag-based ingredient</td></tr>
 *   <tr><td>{@code ElysiumFaction.addRule}</td>
 *       <td>which side an entity is on</td></tr>
 *   <tr><td>{@code ElysiumDispatch.register}</td>
 *       <td>a mob standing can send after a player</td></tr>
 *   <tr><td>{@code ElysiumRewards.register}</td>
 *       <td>what a tier of standing pays out</td></tr>
 *   <tr><td>{@code ElysiumBestiary.register}</td>
 *       <td>a creature, by faction and role, that anything can ask for</td></tr>
 *   <tr><td>{@code ElysiumHooks}</td>
 *       <td>ore blocks, and the character codex item</td></tr>
 * </table>
 *
 * All of it from your mod's constructor. The registries freeze on first read,
 * and say so clearly if you are late.
 *
 * <h2>The one thing to understand before extending it</h2>
 *
 * <b>The library owns the number; you own what it does.</b> A stat you register
 * is summed, saved, displayed and spendable for free — and does nothing until
 * your own event handler reads it and acts. That is not a gap. There is no way
 * to express "reduce incoming fire damage by 12%" as data without inventing a
 * scripting language, and a system that only does what its author anticipated
 * is worse than one that hands you the number and gets out of the way.
 *
 * Races, classes and runes are the exception: their behaviour travels with them
 * as an object, so those genuinely are complete once registered.
 */
@Mod(ElysiumLib.MODID)
public class ElysiumLib {

    public static final String MODID = "elysiumlib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElysiumLib(IEventBus modEventBus) {
        // Touching these registers the canonical elements and stats. Order
        // matters exactly once: elements name the stats they grant, so the
        // stats have to exist first.
        ElysiumStats.bootstrap();
        ElysiumElements.bootstrap();
        ElysiumElements.registerPsionicAffixes();

        // All three are declared in the elysiumlib namespace, so they have to
        // go on *this* mod's event bus. Registering them from a content mod's
        // constructor instead — which is what the split briefly did with the
        // standing attachments — produces ids whose namespace does not match
        // the bus they were registered on, and leaves the library broken when
        // it is installed on its own.
        ElysiumCharacter.ATTACHMENTS.register(modEventBus);
        ElysiumStanding.ATTACHMENTS.register(modEventBus);
        ElysiumComponents.COMPONENTS.register(modEventBus);

        LOGGER.info("Elysium Library ready: {} elements, {} stats",
                com.elysium.lib.element.ElysiumElement.REGISTRY.size(),
                com.elysium.lib.stats.ElysiumStat.REGISTRY.size());
    }
}
