package com.elysium.colonies.rules;

/**
 * Who decides where the next building goes.
 *
 * <h2>The problem this solves</h2>
 *
 * A town that sites its own buildings is the whole point of the autonomy —
 * and it is also, for a player who is standing in that town with a build tool
 * and a plan for the north quarter, an intrusion. Waking up to find a hut
 * where you were going to put the market is worse than having no autonomy at
 * all, because you cannot undo it without breaking something.
 *
 * <p>So placement follows attendance. A town its owner is actually tending
 * defers to them; a town left to itself gets on with it. Everything else —
 * raising what is already there, going over it for damage, trading its way out
 * of a shortage — carries on either way, because none of those put a building
 * anywhere new.
 *
 * <h2>Why days and not presence</h2>
 *
 * Because "the owner is logged in" is the wrong question. A player who logs in
 * for ten minutes on the other side of the world has not taken charge of
 * anything, and a town that stopped developing every time they did would
 * develop in an unpredictable stutter. What matters is whether somebody has
 * been <em>here</em> lately, and lately is measured in the colony's own days.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class StewardRules {

    private StewardRules() {
    }

    /** Who is deciding. */
    public enum Steward {
        /**
         * Whoever has been here lately. The default, and the answer for almost
         * every town: present owner decides, absent owner delegates.
         */
        ATTENDANCE("attendance"),
        /** The owner decides, always, however long they stay away. */
        OWNER("owner"),
        /** The town decides, always, even with its owner standing in it. */
        TOWN("town");

        private final String id;

        Steward(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Steward named(String id) {
            for (Steward steward : values()) {
                if (steward.id.equalsIgnoreCase(id)) {
                    return steward;
                }
            }
            return ATTENDANCE;
        }
    }

    /**
     * Colony days of absence after which a town starts siting for itself.
     *
     * Three. Long enough that a player who logs off for the night, or spends a
     * day mining, comes back to the town exactly as they left it; short enough
     * that a settlement they have genuinely gone away from does not sit idle
     * for a week first.
     */
    public static final int ABSENCE = 3;

    /**
     * Whether the town may choose where the next building goes.
     *
     * @param steward    what the owner has asked for
     * @param day        the colony's own day count
     * @param lastSeen   the day its owner was last inside it, or a negative
     *                   number for a town nobody has ever visited
     */
    public static boolean maySite(Steward steward, int day, int lastSeen) {
        return switch (steward) {
            case OWNER -> false;
            case TOWN -> true;
            // A town nobody has ever come to is not a town whose owner is
            // about to decide anything, so it starts on its own from day one
            // rather than waiting out an absence from a visit that never
            // happened.
            case ATTENDANCE -> !everVisited(lastSeen) || day - lastSeen >= ABSENCE;
        };
    }

    /**
     * Whether the owner is close enough to the town's affairs to be told about
     * them rather than built around.
     *
     * The complement of {@link #maySite} for the attendance case, and
     * deliberately not its exact complement for the other two: an owner who has
     * said the town decides does not want to be told what it decided every
     * three days, and one who has said they decide does.
     */
    public static boolean shouldSuggest(Steward steward, int day, int lastSeen) {
        return switch (steward) {
            case OWNER -> true;
            case TOWN -> false;
            case ATTENDANCE -> !maySite(steward, day, lastSeen);
        };
    }

    /**
     * Days between one suggestion and the next.
     *
     * Four, a day longer than the absence threshold, so a player who is
     * tending their town is reminded of what it wants about once per in-game
     * week rather than every morning. A suggestion that arrives too often is a
     * suggestion a player turns off.
     */
    public static final int BETWEEN_SUGGESTIONS = 4;

    /** Whether enough time has passed to mention it again. */
    public static boolean dueToSuggest(int day, int lastSuggested) {
        return day - lastSuggested >= BETWEEN_SUGGESTIONS;
    }

    /**
     * Whether a town has ever been visited by its owner.
     *
     * A town founded and immediately abandoned should start building for
     * itself, not wait three days from a visit that never happened — so a
     * never-visited town reads as absent from the beginning.
     */
    public static boolean everVisited(int lastSeen) {
        return lastSeen >= 0;
    }
}
