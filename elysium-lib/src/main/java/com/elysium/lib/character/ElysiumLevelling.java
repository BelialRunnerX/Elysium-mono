package com.elysium.lib.character;

/**
 * The cost of a level, on its own.
 *
 * <h2>Why this is not on ElysiumCharacter</h2>
 *
 * It was. {@code ElysiumCharacter} also holds the attachment registrations, so
 * its class initialiser touches NeoForge's registries — and a harness that
 * calls {@code xpToNext} to check the curve is a harness that cannot start.
 * {@code POINTS_PER_LEVEL} got away with it only because a compile-time
 * constant is inlined and never loads the class. A method is not.
 *
 * So the rule lives here, with nothing else, and {@code ElysiumCharacter}
 * delegates. StatHarness executes it.
 *
 * <h2>Why the slope is shallow</h2>
 *
 * It was {@code 60 + 40 * level}: forty more per level, so level fifty cost
 * twenty times what level one did and the cumulative climb was quadratic. That
 * is the right curve for a game where a level is a large thing. It is the
 * wrong one here, where a level is ten points and the stats scale with a
 * square term of their own — a quadratic cost under a quadratic payoff is two
 * curves fighting, and the player feels the one that says no.
 *
 * The project owner asked for something closer to linear. The cost still rises
 * — a level in the late game should be a little more than one in the first
 * hour, or the last levels arrive as fast as the first — but the slope is a
 * quarter of what it was, so level fifty costs about six times level one
 * rather than twenty, and the cumulative climb to it is a third of what it was.
 */
public final class ElysiumLevelling {

    private ElysiumLevelling() {
    }

    public static final int XP_BASE = 100;
    public static final int XP_PER_LEVEL = 10;

    /** Experience needed to leave the given level. */
    public static int xpToNext(int level) {
        return XP_BASE + XP_PER_LEVEL * Math.max(0, level);
    }
}
