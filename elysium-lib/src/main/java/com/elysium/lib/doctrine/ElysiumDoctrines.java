package com.elysium.lib.doctrine;

import com.elysium.lib.entity.ElysiumFaction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Reading a player's doctrines, and what they come to.
 *
 * <h2>Why the tags are read rather than cached</h2>
 *
 * A scoreboard tag is already a set on the entity; asking it is a hash lookup.
 * Caching would mean an invalidation problem — a player respecs, a command
 * removes a tag, an operator edits one — in exchange for saving nothing.
 *
 * <h2>Held at once</h2>
 *
 * A player may hold several doctrines and they combine, except where
 * {@link ElysiumDoctrine#OPPOSED} says two are the same choice made both ways.
 * The skill tree makes those mutually exclusive with an exclusive connection,
 * but a tag can also arrive from a command, so the opposed pairs are resolved
 * here too: the first one declared wins, deterministically, rather than
 * whichever the set happened to iterate first.
 */
public final class ElysiumDoctrines {

    private ElysiumDoctrines() {
    }

    /** Whether this player holds a particular doctrine. */
    public static boolean has(Player player, ElysiumDoctrine doctrine) {
        return player != null && doctrine != null
                && player.getTags().contains(doctrine.tag());
    }

    /**
     * Whether a doctrine is in force, after opposed pairs are resolved.
     *
     * A player wearing both halves of a pair — which the tree cannot produce
     * but a command can — keeps the one declared first in the enum and loses
     * the other. Silently, and on purpose: the alternative is both applying,
     * which is the combination nobody designed and the one somebody would go
     * looking for.
     */
    public static boolean inForce(Player player, ElysiumDoctrine doctrine) {
        if (!has(player, doctrine)) {
            return false;
        }
        for (String[] pair : ElysiumDoctrine.OPPOSED) {
            ElysiumDoctrine first = ElysiumDoctrine.byId(pair[0]);
            ElysiumDoctrine second = ElysiumDoctrine.byId(pair[1]);
            if (first == null || second == null) {
                continue;
            }
            if (doctrine == second && has(player, first)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What extraction costs this player in attention.
     *
     * Multiplied rather than added, so the Empire's interest in a rich vein and
     * an inspection beacon's repricing both still apply — a doctrine changes
     * how loud you are, not what the Empire cares about.
     */
    public static float suspicionMultiplier(Player player) {
        float scale = 1.0F;
        for (ElysiumDoctrine doctrine : ElysiumDoctrine.values()) {
            if (inForce(player, doctrine)) {
                scale *= doctrine.suspicionMultiplier();
            }
        }
        return scale;
    }

    /**
     * What a blow against this victim is worth.
     *
     * A warrant sharpens you against the Unsworn; a writ against the Empire's
     * own. Neither touches anything else, so neither is a flat damage upgrade
     * wearing a faction's name.
     */
    public static float damageMultiplier(Player attacker, Entity victim) {
        if (attacker == null || victim == null) {
            return 1.0F;
        }
        ElysiumFaction faction = ElysiumFaction.of(victim);
        if (faction == ElysiumFaction.UNSWORN
                && inForce(attacker, ElysiumDoctrine.WARRANTED)) {
            return ElysiumDoctrine.WARRANTED.damageMultiplier();
        }
        if (faction == ElysiumFaction.EMPIRE
                && inForce(attacker, ElysiumDoctrine.UNSWORN_WRIT)) {
            return ElysiumDoctrine.UNSWORN_WRIT.damageMultiplier();
        }
        return 1.0F;
    }

    /**
     * The share a player's doctrines take off a hazard.
     *
     * The best single share, not the sum. Two doctrines that both acclimatise
     * are one commitment taken further, and adding them would let a player
     * stack their way to immunity — which is a named state in this mod, earned
     * three specific ways, and not something a skill tree gets to hand out.
     */
    public static float hazardShare(Player player) {
        float best = 0.0F;
        for (ElysiumDoctrine doctrine : ElysiumDoctrine.values()) {
            if (inForce(player, doctrine)) {
                best = Math.max(best, doctrine.hazardShare());
            }
        }
        return best;
    }

    // There was a MAX_SHARE of 0.5 here. Removed by the owner's ruling —
    // "uncap everything" — and never binding in practice: the largest doctrine
    // share is 0.35. The best-of rule above is not a cap but a statement about
    // what stacking two doctrines means, and it stays.
}
