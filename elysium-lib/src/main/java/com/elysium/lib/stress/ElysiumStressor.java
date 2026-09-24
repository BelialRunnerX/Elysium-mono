package com.elysium.lib.stress;

/**
 * One nameable reason somebody is or is not under strain.
 *
 * <h2>Why stress is a list of these and not a saved number</h2>
 *
 * The obvious design accumulates: a villager has a stress score, things add to
 * it, time takes it away. It is also the design that makes §8.3's
 * explainability requirement impossible to keep. Once the number has been
 * accumulated, the reasons are gone — a villager at 63 cannot say <em>why</em>,
 * only that it got there, and any "reason" a screen showed would be a second
 * tally kept alongside the first and free to disagree with it.
 *
 * So stress is not stored. It is <em>derived</em>, every time it is asked for,
 * from the conditions that are true right now. The total is the sum of this
 * list; the explanation <em>is</em> the list. They cannot disagree, because
 * they are the same computation, and a player who asks why gets the arithmetic
 * rather than a description of it.
 *
 * It also means there is nothing to migrate, nothing to drift, and no way for a
 * villager to stay miserable about a house that was rebuilt an hour ago.
 *
 * <h2>Relief is negative stress, not a separate list</h2>
 *
 * A bed and a roof could have been "comfort" with its own total subtracted at
 * the end. Keeping them in one list means the readout can show them in the same
 * ordering as everything else, and a villager who is calm <em>because</em> they
 * are home says so — which is the more interesting half of the answer and the
 * half a two-list design throws away.
 *
 * <h2>No Minecraft here</h2>
 *
 * Ids and weights. What makes each one true about a particular villager lives
 * in {@code ElysiumStressField}, which needs a world; this file does not, so
 * the harness can run the whole band model.
 */
public enum ElysiumStressor {

    // --- what wears somebody down ---------------------------------------
    /** Saw somebody killed. The heaviest single thing in the list. */
    SAW_A_KILLING("saw_a_killing", 30),
    /** Standing in a hazard, with nothing answering it. */
    IN_A_HAZARD("in_a_hazard", 30),
    /** Was hurt not long ago. */
    HURT_RECENTLY("hurt_recently", 25),
    /** Something hostile is close enough to matter. */
    THREATENED("threatened", 25),
    /** Hurt badly enough to still be showing it. */
    INJURED("injured", 20),
    /** No bed anywhere they can claim. */
    HOMELESS("homeless", 20),
    /** Out after dark with no light. */
    IN_THE_DARK("in_the_dark", 15),
    /** No work to go to. */
    UNEMPLOYED("unemployed", 10),
    /** Too many of them in too little room. */
    CROWDED("crowded", 10),

    // --- and what settles them -------------------------------------------
    /** Near their own bed. */
    AT_HOME("at_home", -20),
    /** Inside a room the mod recognises as one. */
    SHELTERED("sheltered", -15),
    /** Somebody nearby they think well of. */
    AMONG_FRIENDS("among_friends", -10);

    private final String id;
    private final int weight;

    ElysiumStressor(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    /** Positive wears down, negative settles. */
    public int weight() {
        return weight;
    }

    public boolean relieves() {
        return weight < 0;
    }

    public String translationKey() {
        return "elysium.stress.reason." + id;
    }
}
