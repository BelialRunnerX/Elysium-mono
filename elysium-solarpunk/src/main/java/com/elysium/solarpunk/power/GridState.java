package com.elysium.solarpunk.power;

/**
 * Everything a rule is allowed to look at.
 *
 * <h2>Why the conditions cannot see the world directly</h2>
 *
 * A condition that reached into the level could ask anything — the time, the
 * weather, a block three hundred away, a player's inventory — and the automation
 * vocabulary would stop being finite the moment somebody added one. The spec
 * calls for a small language on purpose: a player has to be able to hold the
 * whole of it in their head, and an author has to be able to say what a rule
 * can and cannot do without hedging.
 *
 * So the world is measured once, into this, and the conditions see nothing
 * else. Adding a fact to the language means adding a field here, which is a
 * deliberate act rather than an accident of what happened to be in scope.
 *
 * It is also what makes the whole vocabulary executable: the harness builds
 * these by hand and runs every rule against every state, which would be
 * impossible if a condition needed a server.
 *
 * @param daylight whether the sun is up and the sky is visible to the grid
 * @param raining  whether it is raining or storming
 * @param charge   how full the grid's buffers are, 0..1, across every machine
 *                 and generator on it
 */
public record GridState(boolean daylight, boolean raining, float charge) {

    /** Clamped on the way in: a fraction outside 0..1 is a measurement bug,
     *  and a rule firing on it would be that bug wearing a costume. */
    public GridState {
        charge = Math.min(1.0F, Math.max(0.0F, charge));
    }

    /** What a grid with nothing on it looks like. */
    public static final GridState EMPTY = new GridState(false, false, 0.0F);
}
