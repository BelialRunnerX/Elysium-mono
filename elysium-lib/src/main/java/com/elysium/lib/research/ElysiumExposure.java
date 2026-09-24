package com.elysium.lib.research;

import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How long each player has been out in the weather, counted toward a minute.
 *
 * <h2>Why a transient counter and not an attachment</h2>
 *
 * Environmental is the one field with no discovery behind it. It is earned by
 * standing in a hazard, which means the unit is <em>time</em>, and time has to
 * be accumulated somewhere between the second it starts and the minute it is
 * worth something.
 *
 * That somewhere is deliberately not the save file. A part-finished minute is
 * worth at most three points; writing it to disk would mean a field in every
 * player's data, a codec to migrate, and a migration to get wrong, to protect
 * something a player would never notice losing. Forgetting it on logout costs
 * up to fifty-nine seconds of credit and costs nothing else.
 *
 * <h2>Why exposure is counted even when the player is unhurt</h2>
 *
 * A player in a full set of thermal plate standing in a scorching desert is
 * learning the desert. Refusing to count that would mean the only way to raise
 * Environmental is to take damage, which turns a research track into an
 * instruction to hurt yourself — and would make the field unreachable for
 * exactly the player who has best understood it.
 *
 * Standing in a filed claim does not count. That is being indoors.
 *
 * <h2>Server thread only</h2>
 *
 * A plain map in access order, like the hazard and room caches, and for the
 * same reason: the player tick is the only caller and two of them would not be
 * safe.
 */
public final class ElysiumExposure {

    private ElysiumExposure() {
    }

    /** Seconds in a hazard that make one {@link ElysiumDeed#ENDURED_A_HAZARD}. */
    public static final int SECONDS_PER_CREDIT = 60;

    /**
     * How many players are tracked at once.
     *
     * Bounded because the key is a player and a long-running server sees many
     * of them. The eldest falls out and starts their minute again, which is the
     * same cost as logging out.
     */
    public static final int CAPACITY = 256;

    private static final Map<UUID, Integer> SECONDS =
            new LinkedHashMap<>(CAPACITY, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
                    return size() > CAPACITY;
                }
            };

    /**
     * Note one second spent in a hazard, and credit a minute when one is full.
     *
     * @param hazardId which hazard, for the credit's subject. Only the fact of
     *                 exposure is counted, not which one — a player who spends
     *                 half a minute in the cold and half in the heat has spent
     *                 a minute learning what weather does, and splitting that
     *                 into two half-credits neither of which pays would mean
     *                 the most varied environment taught the least.
     * @return the gain, or a nothing-gain on the other fifty-nine seconds
     */
    public static ElysiumResearch.Gain second(Player player, String hazardId) {
        if (player == null || player.level().isClientSide()) {
            return ElysiumResearch.Gain.NOTHING;
        }
        UUID who = player.getUUID();
        int so_far = SECONDS.getOrDefault(who, 0) + 1;
        if (so_far < SECONDS_PER_CREDIT) {
            SECONDS.put(who, so_far);
            return ElysiumResearch.Gain.NOTHING;
        }
        SECONDS.put(who, 0);
        return ElysiumResearch.credit(player, ElysiumDeed.ENDURED_A_HAZARD, hazardId);
    }

    /** Forget everything. For a world unload, and for the tests. */
    public static void clear() {
        SECONDS.clear();
    }

    /** How far into the current minute a player is. Reported, and checked. */
    public static int secondsFor(Player player) {
        return SECONDS.getOrDefault(player.getUUID(), 0);
    }
}
