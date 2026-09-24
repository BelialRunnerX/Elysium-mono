package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Hostile mobs do not appear within arm's reach.
 *
 * <h2>The problem</h2>
 *
 * Vanilla's natural spawning refuses a position inside twenty-four blocks of a
 * player, which sounds generous and is not: twenty-four blocks is roughly two
 * seconds of walking, less through a cave mouth, and a creeper that appears at
 * the edge of that ring is on you before it has finished its spawn animation.
 * A player who is killed by something that did not exist when they entered the
 * room has not been beaten by the game, they have been surprised by the
 * bookkeeping.
 *
 * Thirty-two blocks is the whole change. It is not a large number — it is
 * roughly the distance at which a mob is a shape rather than a threat, which is
 * exactly the amount of warning the rule exists to buy.
 *
 * <h2>What this deliberately does not touch</h2>
 *
 * <ul>
 *   <li><b>Spawners</b>, trial spawners and structure spawns. A player who
 *       builds a mob farm or walks into a dungeon has arranged to be near
 *       something dangerous; refusing that would break a mechanic they chose
 *       and expect. Only {@link MobSpawnType#NATURAL} — the continuous
 *       background cycle nobody asked for — is held back.</li>
 *   <li><b>Anything that is not a monster.</b> The rule exists to buy reaction
 *       time, and there is nothing to react to in a cow. Holding passive
 *       spawns to the same radius would mean no animals near a base, ever,
 *       which quietly ruins every farm in the game for a benefit nobody
 *       asked for.</li>
 *   <li><b>Breeding, spawn eggs, jockeys, reinforcements and conversions</b>,
 *       which are not natural spawns and are all things a player or a mob did
 *       on purpose.</li>
 * </ul>
 *
 * <h2>The consequence worth stating</h2>
 *
 * Mob farms built to vanilla's twenty-four-block ring will stop producing until
 * their platform is moved further out. That is not a side effect to apologise
 * for — it is the same change, seen from the other end — but it is the thing a
 * player will notice first, and it is why the radius is one constant with a
 * name rather than a number buried in a condition.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumSpawnDistanceHandler {

    private ElysiumSpawnDistanceHandler() {
    }

    /**
     * How close a natural monster spawn may come to a player, in blocks.
     *
     * Vanilla's own figure is 24. This is deliberately only a little larger:
     * far enough to see something coming, close enough that the world is still
     * populated. A radius of sixty-four would make the overworld feel empty and
     * would push every mob farm out of render distance.
     */
    public static final double MINIMUM_DISTANCE = 32.0D;

    /** Compared against squared distances, so nothing takes a square root. */
    public static final double MINIMUM_DISTANCE_SQUARED =
            MINIMUM_DISTANCE * MINIMUM_DISTANCE;

    /**
     * Whether a spawn of this kind is subject to the rule at all.
     *
     * Pure, and separated from the event so it can be checked: the list of
     * exemptions is the part of this feature most likely to be got wrong, and
     * the symptom of getting it wrong is a dungeon that stops working, which
     * nobody would connect to a spawn-distance change.
     */
    public static boolean governed(MobCategory category, MobSpawnType type) {
        return category == MobCategory.MONSTER && type == MobSpawnType.NATURAL;
    }

    /** Whether this position is far enough from the given squared distance. */
    public static boolean farEnough(double nearestPlayerDistanceSquared) {
        return nearestPlayerDistanceSquared >= MINIMUM_DISTANCE_SQUARED;
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        if (!governed(mob.getType().getCategory(), event.getSpawnType())) {
            return;
        }
        // The nearest player in this level, including one in creative or
        // spectator: a spectator is still somebody's camera, and a monster
        // popping into view beside it is the same failure.
        Player nearest = event.getLevel().getNearestPlayer(
                event.getX(), event.getY(), event.getZ(),
                -1.0D, false);
        if (nearest == null) {
            return;                     // nobody to surprise
        }
        double distanceSquared = nearest.distanceToSqr(
                event.getX(), event.getY(), event.getZ());
        if (!farEnough(distanceSquared)) {
            // FAIL rather than cancelling: this is the hook that exists for
            // "this position will not do", and a refusal here costs nothing
            // because the mob has not been built yet.
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
