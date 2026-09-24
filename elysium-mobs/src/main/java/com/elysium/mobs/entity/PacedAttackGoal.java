package com.elysium.mobs.entity;

import net.minecraft.world.entity.PathfinderMob;
import com.elysium.lib.telegraph.ElysiumTelegraphedAttack;

/**
 * A melee attack with a cadence of its own.
 *
 * <h2>Why this is needed at all</h2>
 *
 * Every family in this mod differs in health, damage and speed, and those three
 * are the only knobs the sub-variant budget spends. None of them is <em>how
 * often it swings</em>, and vanilla gives a mob no way to vary it: mobs do not
 * use {@code ATTACK_SPEED} — that attribute only drives the player's cooldown
 * bar — and vanilla's MeleeAttackGoal hard-codes a twenty-tick interval
 * for everything from a silverfish to a ravager.
 *
 * So "attacks fast but dies quickly" and "hits slowly but will not go down"
 * could be described in this mod and not built. They came out as the same
 * animation at the same tempo with different numbers attached, which a player
 * reads as one enemy with a bigger health bar.
 *
 * <h2>What it changes, and what it deliberately does not</h2>
 *
 * Only the interval. Reach, pathing, target selection and the damage itself are
 * vanilla's, because the point is the <em>rhythm</em> of the fight — how long
 * you have after a blow lands to move, drink, or hit back. That window is what
 * makes a Harrier something you back away from and a Bulwark something you walk
 * around, and it costs one overridden method.
 *
 * The interval is in ticks and vanilla halves it on Hard, which is kept: a
 * difficulty setting that stopped applying to this mod's mobs would be a
 * surprise in the wrong direction.
 */
/**
 * <h2>Why it extends the telegraphed attack and not vanilla's</h2>
 *
 * Because this is the goal the whole bestiary actually uses, and §13.2's
 * promise — nothing hits you hard without warning — has to hold for every mob
 * or it holds for none. Inheriting it means there is exactly one place in this
 * mod where a melee blow is decided on, so a family cannot be given a rhythm
 * and quietly lose its warning at the same time.
 *
 * The two are also the same subject: the interval is how long you have after a
 * blow, and the wind-up is how long you have before one. A goal that owned one
 * and inherited the other from somewhere else would be the obvious place for
 * them to drift apart.
 */
public class PacedAttackGoal extends ElysiumTelegraphedAttack {

    /** Ticks between swings on Normal. Vanilla's own figure is 20. */
    private final int interval;

    public PacedAttackGoal(PathfinderMob mob, double speed, int interval) {
        super(mob, speed, false);
        this.interval = interval;
    }

    @Override
    protected int getAttackInterval() {
        return adjustedTickDelay(interval);
    }
}
