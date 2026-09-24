package com.elysium.lib.research;

/**
 * How far along one field is, as a named band rather than a number.
 *
 * <h2>Why bands</h2>
 *
 * The same reason {@code PowerClass} is names and not numbers: a player has to
 * be able to say where they are and what it gets them. "Geology 3" is a thing
 * somebody can tell a friend; "geology 1,340" is a number they have to look up.
 *
 * <h2>Why the thresholds grow and where they stop</h2>
 *
 * Each band costs roughly twice the last, so the early ones arrive while a
 * player is still learning what the field <em>is</em> — the first Geology band
 * should land during somebody's first serious mining trip, not as a reward for
 * having decided to pursue it. The last band is reachable and then the track
 * ends: a field that kept counting forever would turn into the grind this
 * system exists to avoid, and there is nothing left to unlock past the top.
 */
public enum ElysiumResearchRank {

    /** Nothing yet. Everyone starts here in every field. */
    UNSTUDIED("unstudied", 0),

    /** Enough to recognise things. A morning's work. */
    FAMILIAR("familiar", 40),

    /** Enough to be useful. A few sessions in one direction. */
    PRACTISED("practised", 120),

    /** Enough to be relied on. A deliberate pursuit. */
    ADEPT("adept", 300),

    /** Enough to teach it. */
    AUTHORITY("authority", 700),

    /** The end of the track. There is nothing past this. */
    PREEMINENT("preeminent", 1500);

    /**
     * The end of every track.
     *
     * Declared here rather than on {@code ElysiumResearch}, which is where it
     * belongs by subject and cannot live by mechanics: that class registers
     * attachments in its static initialiser, so reading one constant off it
     * pulls in NeoForge's registries and fails outside a running game. The
     * harness executes the whole progression against this number, so it has to
     * be somewhere that loads on its own. {@code ElysiumLevelling} was split
     * out of {@code ElysiumCharacter} for exactly the same reason.
     *
     * Read off the last band rather than written twice. An enum constant may
     * not refer forward to a static field, so the literal lives on PREEMINENT
     * and this is derived from it — the other way round does not compile, and
     * two literals would be two numbers to keep in step.
     */
    public static final int CEILING = PREEMINENT.threshold();

    private final String id;
    private final int threshold;

    ElysiumResearchRank(String id, int threshold) {
        this.id = id;
        this.threshold = threshold;
    }

    public String id() {
        return id;
    }

    /** Points needed to be in this band. */
    public int threshold() {
        return threshold;
    }

    /** The band a point total falls in. */
    public static ElysiumResearchRank of(int points) {
        ElysiumResearchRank found = UNSTUDIED;
        for (ElysiumResearchRank rank : values()) {
            if (points >= rank.threshold) {
                found = rank;
            }
        }
        return found;
    }

    /** The next band up, or null at the top. */
    public ElysiumResearchRank next() {
        int index = ordinal() + 1;
        return index < values().length ? values()[index] : null;
    }

    /**
     * How far through the current band a total is, 0..1.
     *
     * One at the top band, rather than zero or a division by nothing. A bar
     * that emptied on reaching the highest rank is the sort of thing that reads
     * as a bug and gets reported as one.
     */
    public static float progress(int points) {
        ElysiumResearchRank here = of(points);
        ElysiumResearchRank up = here.next();
        if (up == null) {
            return 1.0F;
        }
        int span = up.threshold - here.threshold;
        return Math.min(1.0F, Math.max(0.0F, (points - here.threshold) / (float) span));
    }

    public String translationKey() {
        return "elysium.research.rank." + id;
    }
}
