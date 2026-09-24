package com.elysium.lib.combat;

import com.elysium.lib.attribute.ElysiumAttributeValues;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The cooldown clock every triggered effect shares (AA-8): affix and gem
 * procs, rune abilities, anything that fires and then has to wait.
 *
 * <p>An entity's {@code cooldown_reduction} shortens every cooldown it starts,
 * through {@link ElysiumCombatRules#cooldownTicks}, which never lets one reach
 * zero — an effect with no cooldown at all is a different effect.
 *
 * <p>Kept in memory rather than saved: a cooldown is a few seconds of a fight,
 * and the alternative is a data attachment per entity per effect for something
 * that is meaningless once the server restarts. Entities are held weakly, so
 * this map cannot keep a dead mob or a logged-out player alive.
 */
public final class ElysiumCooldowns {

    private ElysiumCooldowns() {
    }

    private static final Map<LivingEntity, Map<String, Long>> READY_AT = new WeakHashMap<>();

    /** Whether {@code key} may fire for this entity now. */
    public static synchronized boolean ready(LivingEntity entity, String key) {
        Map<String, Long> keys = READY_AT.get(entity);
        Long at = keys == null ? null : keys.get(key);
        return at == null || entity.level().getGameTime() >= at;
    }

    /** Ticks left before {@code key} may fire again; zero when it is ready. */
    public static synchronized int remaining(LivingEntity entity, String key) {
        Map<String, Long> keys = READY_AT.get(entity);
        Long at = keys == null ? null : keys.get(key);
        if (at == null) {
            return 0;
        }
        return (int) Math.max(0L, at - entity.level().getGameTime());
    }

    /**
     * Starts {@code key}'s cooldown, shortened by the entity's
     * cooldown_reduction. Returns the ticks it will actually wait.
     */
    public static synchronized int start(LivingEntity entity, String key, int baseTicks) {
        double reduction = ElysiumAttributeValues.has(entity, "cooldown_reduction")
                ? ElysiumAttributeValues.chance(entity, "cooldown_reduction") : 0.0D;
        int ticks = ElysiumCombatRules.cooldownTicks(baseTicks, reduction);
        if (ticks > 0) {
            READY_AT.computeIfAbsent(entity, e -> new HashMap<>())
                    .put(key, entity.level().getGameTime() + ticks);
        }
        return ticks;
    }

    /**
     * Fires if it can: starts the cooldown and answers true, or answers false
     * and changes nothing. The whole pattern in one call, so no caller can
     * check and then forget to start the clock.
     */
    public static synchronized boolean tryUse(LivingEntity entity, String key, int baseTicks) {
        if (!ready(entity, key)) {
            return false;
        }
        start(entity, key, baseTicks);
        return true;
    }

    /** Forgets one entity's cooldowns (a death, a dimension change that rebuilds the entity). */
    public static synchronized void forget(LivingEntity entity) {
        READY_AT.remove(entity);
    }

    /** Only for the harness and the validator: how many entities are being tracked. */
    public static synchronized int tracked() {
        return READY_AT.size();
    }

}
