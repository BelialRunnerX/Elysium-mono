package com.elysium.lib.entity;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * What level a spawn should be, and how to apply it.
 *
 * <h2>Why this is in the library rather than in the mob mod</h2>
 *
 * Because two mods need to agree about it. A dungeon that populates its rooms
 * and a standing dispatch that sends enforcers after you are both spawning the
 * same creatures, and if they computed difficulty differently the same mob
 * would be two different fights depending on where you met it. One rule, in the
 * place both can reach.
 *
 * <h2>The rule</h2>
 *
 * <b>The average character level of players within {@value #RANGE} blocks</b>,
 * then adjusted for how far up the standing meters that group is.
 *
 * The average rather than the highest is a deliberate choice with a real cost:
 * a veteran can soften a fight by bringing low-level friends. It buys the thing
 * that matters more — a newcomer who joins a high-level player's world is not
 * immediately facing mobs scaled entirely past them, which is what "highest
 * nearby" produces and what makes a mod unplayable to the second person who
 * installs it.
 *
 * With nobody nearby the answer is {@link #FALLBACK_LEVEL}. That happens for a
 * mob spawned by a command, or one built into a dungeon room before anyone has
 * walked in, and the alternative — scaling to zero — would fill a level-80
 * player's dungeon with harmless mobs.
 */
public final class ElysiumScaling {

    private ElysiumScaling() {
    }

    /** How far to look for players whose level counts. */
    public static final double RANGE = 64.0D;

    /** Used when nobody is close enough to scale to. */
    public static final int FALLBACK_LEVEL = 1;

    /**
     * How much a full standing meter adds, proportionally.
     *
     * A player at the top band faces mobs a third stronger than one at the
     * bottom. Enough to feel like the Empire is taking you seriously; not
     * enough that climbing the meter is a punishment.
     */
    private static final float BAND_STEP = 0.11F;

    /**
     * The level a mob spawning here should be built for.
     *
     * @param faction which meter to read: the Empire's attention is Suspicion,
     *                the Unsworn's is Favor. Passing NEUTRAL reads neither.
     */
    public static int levelFor(ServerLevel level, BlockPos where, ElysiumFaction faction) {
        List<Player> nearby = level.getEntitiesOfClass(Player.class,
                new net.minecraft.world.phys.AABB(where).inflate(RANGE));
        if (nearby.isEmpty()) {
            return FALLBACK_LEVEL;
        }

        int total = 0;
        int band = 0;
        for (Player player : nearby) {
            total += Math.max(1, ElysiumCharacter.getLevel(player));
            band = Math.max(band, bandFor(player, faction));
        }
        int average = Math.max(1, total / nearby.size());

        // The band raises the effective level rather than multiplying the
        // attributes separately, so a mob's difficulty is one number a player
        // can reason about instead of two that interact.
        return Math.max(1, Math.round(average * (1.0F + band * BAND_STEP)));
    }

    /**
     * The level content built <em>for</em> a particular player should be.
     *
     * <h3>Why proximity is the wrong question sometimes</h3>
     *
     * {@link #levelFor(ServerLevel, BlockPos, ElysiumFaction)} asks "who is
     * standing here". That is right for a mob spawning into a world somebody is
     * walking through, and wrong for one built into a place before anybody has
     * arrived — a dungeon room, a structure, anything generated ahead of the
     * player.
     *
     * A dungeon is generated in full the moment it is opened, while the player
     * who opened it is still standing at the portal in another dimension. Every
     * creature in it therefore found nobody within {@value #RANGE} blocks and
     * came out at {@link #FALLBACK_LEVEL} — which is to say every dungeon, at
     * every level of play, was a level-1 dungeon, and the boss at the end of it
     * was a level-1 boss with a level-1 boss's health for the rest of the save.
     * Teleporting the player in first would not have fixed it either: the boss
     * room can be a hundred and ninety blocks from the entrance, well outside
     * the range.
     *
     * So the caller that knows who the content is being built for says so,
     * instead of the library guessing from who happens to be standing nearby.
     */
    public static int levelFor(Player player, ElysiumFaction faction) {
        if (player == null) {
            return FALLBACK_LEVEL;
        }
        int level = Math.max(1, ElysiumCharacter.getLevel(player));
        return Math.max(1, Math.round(level * (1.0F + bandFor(player, faction) * BAND_STEP)));
    }

    private static int bandFor(Player player, ElysiumFaction faction) {
        return switch (faction) {
            case EMPIRE -> ElysiumStanding.bandOf(ElysiumStanding.getSuspicion(player));
            case UNSWORN -> ElysiumStanding.bandOf(ElysiumStanding.getFavor(player));
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Applying it
    // ------------------------------------------------------------------

    /**
     * Scales a mob's attributes to a level.
     *
     * <h3>Why health is a multiplier and damage is nearly linear</h3>
     *
     * They are not the same kind of number. Health decides how long a fight
     * lasts, and a fight that lasts proportionally longer at every level is a
     * fight that becomes tedious rather than harder — so health grows on a
     * curve that flattens. Damage decides whether the player can afford a
     * mistake, and that has to keep pace with the armour and Resilience a
     * levelling player accumulates, or every mob eventually becomes scenery.
     *
     * <h3>Why the base values are read rather than assumed</h3>
     *
     * Each family sets its own base health and damage; a scaler that assumed
     * vanilla's would make a heavy brute and a scout identical at level 40,
     * which is the point at which the six families stop being six things.
     */
    public static void apply(LivingEntity mob, int mobLevel) {
        int clamped = Math.max(1, mobLevel);

        scale(mob, Attributes.MAX_HEALTH, healthMultiplier(clamped));
        scale(mob, Attributes.ATTACK_DAMAGE, damageMultiplier(clamped));
        scale(mob, Attributes.ARMOR, armourMultiplier(clamped));
        scale(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0F);

        // Set health last: raising MAX_HEALTH does not refill, so a mob scaled
        // after spawning would arrive at a fraction of its own health bar and
        // die to the first hit.
        mob.setHealth(mob.getMaxHealth());
    }

    /**
     * Health: roughly triples by level 50, and keeps climbing slowly after.
     *
     * The square root is what flattens it. Linear health means a level-100 mob
     * takes a hundred times as long to kill as a level-1 one, and no amount of
     * damage scaling on the player's side makes that fight interesting.
     */
    public static float healthMultiplier(int level) {
        return 1.0F + 0.30F * (float) Math.sqrt(level - 1);
    }

    /** Damage: near-linear, because the player's mitigation is too. */
    public static float damageMultiplier(int level) {
        return 1.0F + 0.045F * (level - 1);
    }

    /** Armour: slow, and deliberately capped by the curve rather than a clamp. */
    public static float armourMultiplier(int level) {
        return 1.0F + 0.20F * (float) Math.sqrt(level - 1);
    }

    private static void scale(LivingEntity mob,
                              net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              float multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            // Not every mob has every attribute - a mob with no ATTACK_DAMAGE
            // is one that does not hit things, and giving it some would be a
            // surprise rather than a scaling.
            return;
        }
        instance.setBaseValue(instance.getBaseValue() * multiplier);
    }
}
