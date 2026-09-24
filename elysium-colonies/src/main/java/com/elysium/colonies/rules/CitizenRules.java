package com.elysium.colonies.rules;

/**
 * A citizen is a person, and what happens to them shows up in their work.
 *
 * <h2>The problem this solves</h2>
 *
 * Both mods already model people, and neither knows the other does. Elysium
 * has stressors, wounds that take days to heal, memories that fade on a
 * half-life and skill that comes from repetition — and, until now, nothing
 * driving any of it for anyone but the player. MineColonies has citizens with
 * eleven skills and a happiness number, and nothing that makes standing in a
 * thermal hazard for a week feel different from standing in a meadow.
 *
 * <p>So this is the exchange rate between the two. Elysium's reading of what a
 * person's life is like becomes a number MineColonies already knows how to
 * use; MineColonies' idea of who this person is and what they do becomes the
 * input Elysium's stressors want.
 *
 * <h2>Why happiness and not a second bar</h2>
 *
 * Because a colony already has a happiness screen, a player already reads it,
 * and a second, parallel, Elysium-flavoured misery meter would be two numbers
 * that disagree about the same person. What this adds is <em>reasons</em>:
 * every point comes from a named stressor a player can go and fix.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class CitizenRules {

    private CitizenRules() {
    }

    // ------------------------------------------------------------------
    // Stress into happiness
    // ------------------------------------------------------------------

    /**
     * The stress at which a citizen's contribution to happiness is halved.
     *
     * Forty, which on the library's scale is roughly the distance from settled
     * to distressed: homeless and unemployed together, or one full-intensity
     * hazard with nothing sealed against it.
     */
    public static final float HAPPINESS_PACE = 40.0F;

    /**
     * The least a stressed citizen's happiness factor can fall to.
     *
     * A quarter rather than nothing. A factor of zero would mean a single
     * miserable week could not be recovered from by anything the player did,
     * and this system's whole claim is that every point of it has a cause you
     * can go and fix.
     */
    public static final float HAPPINESS_FLOOR = 0.25F;

    /**
     * What this citizen's life does to their happiness, as a multiplier on the
     * neutral one.
     *
     * {@code 1 / (1 + stress/PACE)}, floored. One at no stress, a half at the
     * pace, approaching the floor and never passing it.
     */
    public static float happiness(int stress) {
        if (stress <= 0) {
            return 1.0F;
        }
        float factor = 1.0F / (1.0F + stress / HAPPINESS_PACE);
        return Math.max(HAPPINESS_FLOOR, factor);
    }

    // ------------------------------------------------------------------
    // Stress into work
    // ------------------------------------------------------------------

    /**
     * The slowest a citizen works, however bad things get.
     *
     * Two-fifths. A person in crisis still turns up and still does something;
     * a colony that stopped dead would be a colony a player cannot dig out of,
     * because digging out is done by the same people.
     */
    public static final float WORK_FLOOR = 0.4F;

    /** How fast this citizen works, given their stress. Never below the floor. */
    public static float pace(int stress) {
        return Math.max(WORK_FLOOR, happiness(stress));
    }

    // ------------------------------------------------------------------
    // Wounds
    // ------------------------------------------------------------------

    /**
     * How much of a citizen's pace one untreated wound costs.
     *
     * A twelfth each, so a badly mauled citizen is markedly slower and four
     * wounds is not a corpse. Linear in the count, because wounds do not
     * interact — two broken fingers is twice one broken finger, not four
     * times.
     */
    public static final float PER_WOUND = 1.0F / 12.0F;

    /** The pace of a citizen carrying this many wounds, on top of their stress. */
    public static float wounded(float pace, int wounds) {
        float kept = 1.0F - PER_WOUND * Math.max(0, wounds);
        return Math.max(WORK_FLOOR, pace * Math.max(0.0F, kept));
    }

    /**
     * How much faster wounds heal in a town with a hospital.
     *
     * Three times, matching the library's own med-bay rate, so a colony's
     * hospital is worth the same as a player's med bay rather than a different
     * number for the same idea.
     */
    public static final int HOSPITAL_RATE = 3;

    /** The healing rate for a citizen in a town with or without a hospital. */
    public static int healRate(boolean hospital) {
        return hospital ? HOSPITAL_RATE : 1;
    }

    // ------------------------------------------------------------------
    // Practice
    // ------------------------------------------------------------------

    /**
     * The b in {@code x / (x + b)} for how well somebody knows their trade.
     *
     * Two hundred and fifty pieces of work. A citizen who has made the same
     * thing two hundred and fifty times is halfway to as good as they will
     * ever get; one who has made it a thousand is four-fifths of the way. Long
     * enough that mastery is a fact about a save rather than about an
     * afternoon.
     */
    public static final float PRACTICE_PACE = 250.0F;

    /** How practised somebody is at what they do. Approaches one; never reaches it. */
    public static float mastery(int done) {
        float at = Math.max(0, done);
        return at / (at + PRACTICE_PACE);
    }

    /**
     * The most practice is worth, as a share added to one.
     *
     * Half again. Large enough that a long-running town is visibly better at
     * what it does than a new one — which is the thing a colony mod is for —
     * and small enough that it does not make a new town pointless.
     */
    public static final float PRACTICE_BONUS = 0.5F;

    /** What a practised citizen's work is worth, as a multiple. */
    public static float practised(int done) {
        return 1.0F + PRACTICE_BONUS * mastery(done);
    }

    /**
     * Everything at once: what one citizen's day is actually worth.
     *
     * Stress and wounds slow a person down; practice speeds them up; the world
     * they are on decides what a full day is worth in the first place. The
     * three multiply, and the order does not matter, which is the point of
     * expressing all three as factors rather than as additions.
     */
    public static float output(int stress, int wounds, int done, float worldFactor) {
        float rate = wounded(pace(stress), wounds);
        return rate * practised(done) * Math.max(0.0F, worldFactor);
    }
}
