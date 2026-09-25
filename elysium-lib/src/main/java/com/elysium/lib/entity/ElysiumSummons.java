package com.elysium.lib.entity;

import com.elysium.lib.attribute.ElysiumAttributeValues;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a summoner's attributes say about the things they call up.
 *
 * <p>{@code summon_damage} multiplies every blow an owned creature lands, read
 * off the owner at the moment of the blow, so a summoner who takes off their
 * armour mid-fight weakens what they already have out. {@code summon_count}
 * is how many a summoning item may keep alive at once; the item asks here
 * rather than carrying its own number, so two summoning weapons share one
 * budget and a trinket that raises it raises both. {@code summon_lifetime}
 * is seconds added to however long the item keeps them.
 */
public final class ElysiumSummons {

    private ElysiumSummons() {
    }

    public static double damageMultiplier(LivingEntity owner) {
        return ElysiumAttributeValues.multiplier(owner, "summon_damage");
    }

    public static int limit(LivingEntity owner) {
        return (int) Math.round(ElysiumAttributeValues.flat(owner, "summon_count"));
    }

    /** Extra ticks a summon lasts beyond its item's own, from summon_lifetime (seconds). */
    public static int extraLifetimeTicks(LivingEntity owner) {
        return (int) Math.round(ElysiumAttributeValues.flat(owner, "summon_lifetime") * 20.0D);
    }
}
