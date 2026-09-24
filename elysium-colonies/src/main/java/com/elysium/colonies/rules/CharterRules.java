package com.elysium.colonies.rules;

/**
 * What a world does to the town built on it.
 *
 * <h2>The problem this solves</h2>
 *
 * A colony is the same colony on every world. Plant one in a river valley and
 * one on Cinderveil and, once the walls are up, they behave identically: the
 * same buildings, the same output, the same working day. The only difference a
 * hostile world makes is on the walk there.
 *
 * <p>That is a waste of five worlds. A settlement is the most expensive thing
 * a player builds and the most rooted; it ought to be the thing that most
 * obviously belongs to <em>where it is</em>. So a colony is how you work a
 * world, and which world decides what working it is like.
 *
 * <h2>Three things a world decides</h2>
 *
 * <ol>
 *   <li><b>What is to hand.</b> A material the world produces is worked well;
 *       one that has to be carried in is worked badly. Both through
 *       {@code x / (x + b)} — see {@link #localShare} — so a town with nothing
 *       local is slow rather than stopped, and one with everything local is
 *       fast rather than free.</li>
 *   <li><b>What it costs to live there.</b> Hazards and unbreathable air are a
 *       standing burden on the people, paid every day, reduced by how much of
 *       the town is sealed and never to nothing.</li>
 *   <li><b>How far from anywhere it is.</b> Distance is what makes the first
 *       two matter: a town one portal from home can import its way out of a
 *       bad charter, and one on the far side of the system cannot.</li>
 * </ol>
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class CharterRules {

    private CharterRules() {
    }

    // ------------------------------------------------------------------
    // What is to hand
    // ------------------------------------------------------------------

    /**
     * The b in {@code x / (x + b)} for how much of a town's work is local.
     *
     * Four: a town whose charter covers four of the families it uses is
     * halfway; one covering twelve is three-quarters. Chosen so that the
     * difference between a bare world and a rich one is felt inside the first
     * few buildings rather than at the end of a tech tree.
     */
    public static final float LOCAL_PACE = 4.0F;

    /**
     * How much of this town's work is done with materials the world itself
     * produces. Approaches one; never reaches it.
     *
     * @param nativeFamilies how many of the families it uses are native here
     */
    public static float localShare(int nativeFamilies) {
        float at = Math.max(0, nativeFamilies);
        return at / (at + LOCAL_PACE);
    }

    /**
     * The most a rich charter speeds a town's work, as a share added to one.
     *
     * A quarter. Deliberately modest: this is a reason to site a town
     * somewhere, not a reason to rebuild an existing one, and a multiplier
     * large enough to force a move would be a punishment for having chosen
     * before the rule existed.
     */
    public static final float LOCAL_BONUS = 0.25F;

    /**
     * The most a bare charter slows it. Larger than the bonus, because being
     * somewhere with nothing is meant to be the harder story.
     */
    public static final float FOREIGN_PENALTY = 0.35F;

    /**
     * What a day's work is worth here, as a multiple of a day's work at home.
     *
     * Runs from {@code 1 - FOREIGN_PENALTY} at no local supply towards
     * {@code 1 + LOCAL_BONUS}, and reaches neither end: the worst world is
     * still worth working and the best one still has a ceiling.
     */
    public static float workFactor(int nativeFamilies) {
        float share = localShare(nativeFamilies);
        return (1.0F - FOREIGN_PENALTY)
                + share * (FOREIGN_PENALTY + LOCAL_BONUS);
    }

    // ------------------------------------------------------------------
    // What it costs to live there
    // ------------------------------------------------------------------

    /**
     * Stress points a citizen carries per unit of hazard intensity, before any
     * sealing.
     *
     * Twelve, which on the library's scale is most of the way from settled to
     * strained on its own. Cinderveil's thermal hazard is declared at full
     * intensity; a town there with nothing sealed is a town of unhappy people,
     * which is the correct answer and the reason to build properly.
     */
    public static final int BURDEN_PER_HAZARD = 12;

    /**
     * The share of a hazard's burden that sealing can take away.
     *
     * Not all of it. A sealed room is shelter, not a different planet, and a
     * colony that could zero out its world would be back to the colony that is
     * the same everywhere.
     */
    public static final float MOST_SEALED = 0.8F;

    /**
     * The standing daily burden on a citizen of living here.
     *
     * @param intensity the world's hazard intensity, summed across hazards
     * @param sealed    the share of the town that is sealed, in [0, 1]
     */
    public static int burden(float intensity, float sealed) {
        float raw = Math.max(0.0F, intensity) * BURDEN_PER_HAZARD;
        float relief = Math.min(MOST_SEALED, Math.max(0.0F, Math.min(1.0F, sealed))
                * MOST_SEALED);
        return Math.round(raw * (1.0F - relief));
    }

    /**
     * Air that cannot be breathed is its own burden, on top of the hazards,
     * and sealing does not reduce it — a helmet is not a home.
     */
    public static final int AIRLESS_BURDEN = 8;

    /** Half of it for thin or toxic air, which is survivable and unpleasant. */
    public static int airBurden(boolean breathable, boolean none) {
        if (breathable) {
            return 0;
        }
        return none ? AIRLESS_BURDEN : AIRLESS_BURDEN / 2;
    }

    // ------------------------------------------------------------------
    // How far from anywhere
    // ------------------------------------------------------------------

    /**
     * The b in {@code x / (x + b)} for how isolated a town is.
     *
     * Two worlds out. So the first hop barely registers, the second is a third
     * of the way, and the far side of the system is most of the way — without
     * any world ever being completely cut off.
     */
    public static final float REACH_PACE = 2.0F;

    /** How cut off a town this many worlds from home is. Never one. */
    public static float isolation(int worldsFromHome) {
        float at = Math.max(0, worldsFromHome);
        return at / (at + REACH_PACE);
    }

    /**
     * What isolation does: it makes the charter matter more.
     *
     * A town next door can import its way out of a bad world, so its local
     * supply hardly decides anything; one on the far side cannot, so its
     * charter is most of what it is. This scales the distance of
     * {@link #workFactor} from one, rather than scaling the factor itself —
     * so a well-sited far colony is better off than a well-sited near one, and
     * a badly sited far colony is worse, which is the shape the decision
     * should have.
     */
    public static float weighted(float factor, int worldsFromHome) {
        float away = isolation(worldsFromHome);
        return 1.0F + (factor - 1.0F) * (0.4F + 0.6F * away);
    }
}
