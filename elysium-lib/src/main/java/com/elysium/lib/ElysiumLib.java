package com.elysium.lib;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumComponents;
import com.elysium.lib.standing.ElysiumCommission;
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

        // All of these are declared in the elysiumlib namespace, so they have
        // to go on *this* mod's event bus. Registering them from a content
        // mod's constructor instead — which is what the split briefly did with
        // the standing attachments — produces ids whose namespace does not
        // match the bus they were registered on, and leaves the library broken
        // when it is installed on its own.
        ElysiumCharacter.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.menu.ElysiumChests.MENUS.register(modEventBus);
        ElysiumStanding.ATTACHMENTS.register(modEventBus);
        ElysiumCommission.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.standing.ElysiumFile.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.event.ElysiumSquadHandler.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.social.ElysiumMemories.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.medical.ElysiumMedical.ATTACHMENTS.register(modEventBus);
        com.elysium.lib.tier.ElysiumTiers.ATTACHMENTS.register(modEventBus);
        // Every class that registers onto ElysiumTiers.ATTACHMENTS has to be
        // loaded before RegisterEvent fires, or its entry is refused when a
        // mob first spawns. ElysiumTierMobs is loaded by the subscriber scan;
        // ElysiumElites was loaded by nobody until the first mob was hardened.
        com.elysium.lib.elite.ElysiumElites.bootstrap();

        // Touching the class runs its registrations; without this every
        // handler looks up a kind that is not there and villagers remember
        // nothing at all.
        com.elysium.lib.social.ElysiumMemoryKinds.bootstrap();
        ElysiumComponents.COMPONENTS.register(modEventBus);

        // The five hazard wards. Effects rather than items, so the library can
        // ship the cheap counter to a hazard without shipping anything a player
        // can hold — any mod's food, potion or machine may grant one, and the
        // damage tick reads it without knowing which did.
        com.elysium.lib.hazard.ElysiumWards.Effects.register(modEventBus);

        // The Null field (Valoria's Nihility): a sixth hazard on the Void
        // element, the gauge it fills, and the ward against it. Registered
        // here rather than among the five so those stay the specification's.
        com.elysium.lib.nihil.ElysiumNull.bootstrap();
        com.elysium.lib.nihil.ElysiumNull.register(modEventBus);

        // The combat attributes (crit, dodge, pierce, shred, lifesteal and the
        // rest). In the library so gear and affixes share one holder each.
        com.elysium.lib.attribute.ElysiumAttributes.register(modEventBus);
        com.elysium.lib.combat.ElysiumEffects.register(modEventBus);
        com.elysium.lib.combat.ElysiumPotions.register(modEventBus);
        com.elysium.lib.sound.ElysiumSounds.register(modEventBus);

        // §8.5's rooms. Registered rather than enumerated, so a content mod can
        // declare a room of its own and have the hazard tick read it — the
        // requirements are block tags, which is the one thing a content mod is
        // certain to want to add to.
        com.elysium.lib.room.ElysiumRooms.bootstrap();

        // The affixes rolled onto an item, offered to the effect engine as one
        // of the things that can be attached to it. Traits are what a piece is
        // made of; affixes are what happened to it, and both are read by the
        // same handlers because both speak the same vocabulary.
        com.elysium.lib.affix.ElysiumAffixEffects.register();
        com.elysium.lib.affix.ElysiumAffixHandler.register();
        com.elysium.lib.loot.ElysiumLoot.register(modEventBus);
        // Gear sets: a third attachment on the same engine. What makes a set
        // bonus different is only where it looks — at the whole of what is
        // worn rather than at the one stack in hand.
        com.elysium.lib.set.ElysiumGearSets.register();

        // "Uncap everything": vanilla's attribute ceilings, lifted once the
        // game is set up. Guarded inside — a failure logs and leaves vanilla's.
        modEventBus.addListener(
                (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent setup) ->
                        setup.enqueueWork(() -> LOGGER.info(
                                "Elysium: lifted {} vanilla attribute ceiling(s)",
                                com.elysium.lib.stats.ElysiumAttributeLimits.raise())));

        LOGGER.info("Elysium Library ready: {} elements, {} stats, {} hazard wards, {} rooms",
                com.elysium.lib.element.ElysiumElement.REGISTRY.size(),
                com.elysium.lib.stats.ElysiumStat.REGISTRY.size(),
                com.elysium.lib.hazard.ElysiumWards.bound(),
                com.elysium.lib.room.ElysiumRoom.REGISTRY.size());
    }
}
