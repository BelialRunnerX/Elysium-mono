package com.elysium.mobs.variant;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * What a sub-variant does that its numbers cannot express.
 *
 * <h2>Why abilities are objects</h2>
 *
 * Thirty variants that differ only in health and damage are not thirty
 * creatures; they are one creature with a slider. What makes a Bulwark
 * different from a Skirmisher is that one of them raises a shield and the other
 * closes the distance — and neither of those is a number.
 *
 * The same lesson the library learned about race passives applies here, for the
 * same reason: an ability written as a {@code switch} in the mob's own tick
 * method works exactly once, for the variants this mod ships. Behaviour travels
 * with the variant, so a variant registered by another mod does something.
 *
 * <h2>When each hook runs</h2>
 *
 * Every hook has a do-nothing default, so a variant implements the one or two
 * it cares about. They run on the server only — an ability that ran on the
 * client would apply effects the server never agreed to and be corrected a
 * moment later.
 */
public interface MobAbility {

    /**
     * Once, when the mob is first built and scaled.
     *
     * The place for anything permanent: an attribute the family does not have,
     * equipment, a size change. The mob's level is already applied by the time
     * this runs, so an ability can scale itself against it.
     */
    default void onSpawn(Mob mob, int mobLevel) {
    }

    /**
     * Roughly twice a second while the mob is alive, server side.
     *
     * Deliberately not every tick. An ability that needed 20 Hz precision would
     * be a goal rather than an ability, and running thirty variants' worth of
     * logic every tick for every mob in a dungeon is how a mod becomes the
     * reason a server lags.
     */
    default void onServerTick(Mob mob, int mobLevel) {
    }

    /**
     * A multiplier on damage this mob deals.
     *
     * @return 1.0 for no change; multiplied with anything else that answers
     */
    default float outgoingScale(Mob mob, LivingEntity victim, int mobLevel) {
        return 1.0F;
    }

    /**
     * A multiplier on damage this mob takes, applied before its armour.
     *
     * Returning 0 makes it immune, which is a legitimate thing for a phase to
     * do and a terrible thing for a variant to do permanently — an ability that
     * cannot be hurt is not a fight.
     */
    default float incomingScale(Mob mob, DamageSource source, int mobLevel) {
        return 1.0F;
    }

    /** Called after this mob is hurt and survived. */
    default void onHurt(Mob mob, DamageSource source, float amount, int mobLevel) {
    }

    /** Called when this mob dies, before drops. */
    default void onDeath(Mob mob, DamageSource source, int mobLevel) {
    }

    /** A one-line description, for the variant's tooltip and the codex. */
    default String descriptionKey() {
        return "";
    }
}
