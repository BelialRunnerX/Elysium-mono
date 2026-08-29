package com.elysium.lib.character;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What a race or a class does that a stat cannot express.
 *
 * <h2>Why this exists</h2>
 *
 * Passives used to be a switch statement: {@code if (race == DRUUN) ...} inside
 * the engine's own combat handler. That works exactly once, for the races the
 * engine's author wrote. An add-on's race could be registered, could carry
 * stats, and would then do nothing at all, because the code that decides what
 * a race does had never heard of it.
 *
 * So behaviour travels with the thing it belongs to. Every hook below is a
 * question the engine asks each of a player's passives in turn; a race or class
 * answers the ones it cares about and ignores the rest.
 *
 * <h2>How the answers combine</h2>
 *
 * A player has at most two passives, one from their race and one from their
 * class, and both are always consulted. Multipliers are <b>multiplied</b>
 * together, so two sources of +25% come to +56% rather than +50% and neither
 * can cancel the other by returning zero unless it means to. Proportional
 * shares are combined through {@code 1 - (1-a)(1-b)}, so they approach 1.0 and
 * never exceed it. Neither is a sum, and that is deliberate: sums need clamps,
 * and a clamp is where a carefully tuned curve goes to stop mattering.
 *
 * Every method has a do-nothing default. Implement two, or eleven.
 */
public interface ElysiumPassive {

    /** The passive's name, for the character screen and tooltips. */
    Component getDisplayName();

    /** One line on what it does. */
    Component getDescription();

    // ------------------------------------------------------------------
    // Offence
    // ------------------------------------------------------------------

    /**
     * A multiplier on outgoing melee damage.
     *
     * @return 1.0 for no change; multiplied with every other passive's answer
     */
    default float attackScale(Player attacker, LivingEntity victim) {
        return 1.0F;
    }

    /**
     * What a critical hit is worth, as a multiplier on the blow.
     *
     * @return the vanilla-ish 1.5 by default; the largest answer wins rather
     *         than the product, because two passives that each "make crits
     *         hurt" should not multiply into something absurd
     */
    default float critMultiplier(Player attacker) {
        return 1.5F;
    }

    // ------------------------------------------------------------------
    // Defence
    // ------------------------------------------------------------------

    /** A multiplier on incoming damage, applied after Resilience. */
    default float defenceScale(Player defender, DamageSource source) {
        return 1.0F;
    }

    /**
     * A share of every blow returned to its owner, on top of the Retribution
     * stat. Combined proportionally, so it can approach 1.0 but not pass it.
     */
    default float reflectShare(Player defender) {
        return 0.0F;
    }

    /**
     * A multiplier on the share this player reflects from all other sources.
     *
     * Separate from {@link #reflectShare} because "add some reflection" and
     * "double whatever reflection you already have" are different ideas, and a
     * passive that meant the second had no way to say so.
     */
    default float reflectMultiplier(Player defender) {
        return 1.0F;
    }

    /** Multiplier on fall damage. Return 0 to ignore falling entirely. */
    default float fallDamageScale(Player player) {
        return 1.0F;
    }

    /** Blocks of fall distance ignored before damage is calculated at all. */
    default float fallDistanceIgnored(Player player) {
        return 0.0F;
    }

    // ------------------------------------------------------------------
    // Body
    // ------------------------------------------------------------------

    /** A multiplier on passive health regeneration. */
    default float regenScale(Player player) {
        return 1.0F;
    }

    /** A multiplier on the Willpower shield's capacity. */
    default float shieldScale(Player player) {
        return 1.0F;
    }

    /**
     * Anything that happens on the regeneration tick — roughly twice a second,
     * server side. This is where a passive that heals other players, or applies
     * an effect, or checks a condition, does its work.
     */
    default void onServerTick(Player player) {
    }

    // ------------------------------------------------------------------
    // Standing, loot and work
    // ------------------------------------------------------------------

    /** Multiplier on Favor gained. */
    default float favorScale(Player player) {
        return 1.0F;
    }

    /** Multiplier on Suspicion gained. */
    default float suspicionScale(Player player) {
        return 1.0F;
    }

    /** How many points of standing decay per tick of the decay clock. */
    default int decayRate(Player player) {
        return 1;
    }

    /** An added chance of a second roll on any Elysium drop. */
    default float extraDropChance(Player player) {
        return 0.0F;
    }

    /** A multiplier on psionic potency — elemental advantage and rune strength. */
    default float psionicScale(Player player) {
        return 1.0F;
    }

    /** A multiplier on reforge quality. */
    default float reforgeScale(Player player) {
        return 1.0F;
    }

    /** True when this use of a tool should cost no durability. */
    default boolean savesDurability(Player player) {
        return false;
    }

    /** True when this break of an Elysium ore should pay a second time. */
    default boolean doublesOre(Player player) {
        return false;
    }

    // ------------------------------------------------------------------
    // Moments, rather than multipliers
    // ------------------------------------------------------------------
    //
    // Everything above is "how much"; these are "when". They exist because a
    // rule needs a moment to fire at, and a passive that wanted to do something
    // on a kill previously had no way of being told a kill had happened — which
    // meant every effect had to be expressed as a percentage of something.
    //
    // Races and classes may implement these too. There is deliberately no such
    // thing as a trinket-only hook: one mechanism, as everywhere else.

    /**
     * Something this player killed has died.
     *
     * Server side, after the death is certain. Fires for indirect kills too, so
     * a passive that cares only about melee must check for itself.
     */
    default void onKill(Player killer, LivingEntity victim) {
    }

    /**
     * This player has just taken damage, after every modifier has been applied.
     *
     * The amount is what actually landed, so an effect reading "when you are
     * hurt" sees the same figure the health bar does. Fires even when the blow
     * was fully absorbed, with an amount of zero, because "you were attacked
     * and it did nothing" is something some effects want to know.
     */
    default void onDamaged(Player defender, DamageSource source, float amount) {
    }

    /**
     * A share of incoming blows avoided outright.
     *
     * Combined proportionally, so it approaches 1.0 and never reaches it.
     * Total immunity is not reachable by stacking, which is the reason this is
     * a share and not a sum.
     */
    default float dodgeChance(Player defender, DamageSource source) {
        return 0.0F;
    }

    /**
     * A share of damage dealt returned to the attacker as health.
     *
     * Of the figure that landed, not the figure attempted — the opposite of
     * Retribution, and deliberately so. Reflection answers for what was tried
     * at you; lifesteal pays out on what you actually did.
     */
    default float lifestealShare(Player attacker, LivingEntity victim) {
        return 0.0F;
    }

    /** A multiplier on Elysium experience gained. */
    default float xpScale(Player player) {
        return 1.0F;
    }
}
