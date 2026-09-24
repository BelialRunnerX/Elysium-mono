package com.elysium.lib.research;

/**
 * Everything a player can do that teaches them something, and what it teaches.
 *
 * <h2>This table is the feature</h2>
 *
 * "Research advances by doing the thing" is a sentence until somebody writes
 * down which things. Keeping every one of them in a single enum means the
 * answer to "why did my Geology go up" is one file, and it means the balance
 * between the six fields is visible in one screenful rather than scattered
 * across the eleven call sites that award it.
 *
 * <h2>Novelty, and why most deeds need it</h2>
 *
 * A deed that pays every time is a deed a player will repeat. Scanning the same
 * cow four hundred times must be worth nothing, or the track stops describing
 * what somebody has done and starts describing how patient they are.
 *
 * So most deeds are {@linkplain #novel() novel-only}: they pay the first time
 * that <em>particular</em> thing happens and never again. The exceptions are
 * the ones where the repetition <em>is</em> the work — an hour in a hazard is
 * an hour of learning the hazard, and the tenth firing of a kiln is still a
 * firing — and those pay a tenth as much, so an afternoon of them is worth
 * about one discovery.
 *
 * <h2>No Minecraft here</h2>
 *
 * The same reason as the rest of this package: the harness executes the whole
 * progression, including how long each field takes to fill, and it can only do
 * that if nothing here needs a world.
 */
public enum ElysiumDeed {

    // --- catalogued, once each ------------------------------------------
    /** A plant nobody has scanned before. */
    SCANNED_FLORA(ElysiumResearchField.XENOBIOLOGY, 6, true),
    /** A creature nobody has scanned before. */
    SCANNED_FAUNA(ElysiumResearchField.XENOBIOLOGY, 10, true),
    /** An ore or vein nobody has scanned before. */
    SCANNED_MINERAL(ElysiumResearchField.GEOLOGY, 10, true),
    /** Somebody's building, read rather than looted. */
    SCANNED_STRUCTURE(ElysiumResearchField.IMPERIAL_SYSTEMS, 14, true),
    /** A rift, a derelict, something that should not be there. */
    SCANNED_ANOMALY(ElysiumResearchField.RIFT_SCIENCE, 20, true),

    // --- been somewhere, once each --------------------------------------
    /** Walked through a rift into whatever is on the other side. */
    ENTERED_A_RIFT(ElysiumResearchField.RIFT_SCIENCE, 25, true),
    /** Read your own file at an Imperial Archive. */
    READ_THE_ARCHIVE(ElysiumResearchField.IMPERIAL_SYSTEMS, 12, true),
    /**
     * The first time each kind of working machine is built.
     *
     * Added because the harness measured Engineering as twelve times the work
     * of the quickest field: its only deeds were a bench operation and a
     * refining, both repeatable and both worth a point or two, so the only
     * route to the top was ten thousand identical actions. Engineering is
     * learned by building things that work, and now it is paid for that —
     * once per kind, so a wall of kilns teaches what one kiln teaches.
     */
    BUILT_A_MACHINE(ElysiumResearchField.ENGINEERING, 15, true),

    // --- kept doing it ---------------------------------------------------
    /**
     * A minute spent in a hazard that was actually hurting, or would have been.
     *
     * Paid per minute rather than per tick, because a tick is a rounding error
     * and twelve hundred of them is an afternoon. Environmental is the one
     * field with no scan behind it: it is learned by standing in the weather.
     */
    ENDURED_A_HAZARD(ElysiumResearchField.ENVIRONMENTAL, 5, false),
    /** One piece of gear reforged, socketed or ascended. */
    WORKED_A_BENCH(ElysiumResearchField.ENGINEERING, 2, false),
    /** One ingot's worth of ore refined. */
    REFINED_A_MATERIAL(ElysiumResearchField.ENGINEERING, 1, false),
    /** One block of the mod's own ore, mined. */
    CUT_A_VEIN(ElysiumResearchField.GEOLOGY, 1, false);

    private final ElysiumResearchField field;
    private final int points;
    private final boolean novel;

    ElysiumDeed(ElysiumResearchField field, int points, boolean novel) {
        this.field = field;
        this.points = points;
        this.novel = novel;
    }

    public ElysiumResearchField field() {
        return field;
    }

    public int points() {
        return points;
    }

    /** Whether this deed only counts the first time for a given subject. */
    public boolean novel() {
        return novel;
    }

    /**
     * The deed a scan of this kind of thing counts as.
     *
     * The mapping lives here rather than on {@code ElysiumDiscoveryKind}
     * because a discovery kind is a fact about the world and a deed is a fact
     * about a curriculum. Putting the curriculum on the kind would mean the
     * scanner could not be reused by a mod that wanted a different one, and
     * would put a research concern inside the one file the catalogue code reads
     * on every scan.
     *
     * @param kindId {@code ElysiumDiscoveryKind.getId()} — taken as a string so
     *               that nothing in this package has to import the discovery
     *               package, which is what keeps the whole progression
     *               executable outside a world
     * @return the deed, or null for a kind this curriculum has no opinion about
     */
    public static ElysiumDeed forScanOf(String kindId) {
        if (kindId == null) {
            return null;
        }
        return switch (kindId) {
            case "flora" -> SCANNED_FLORA;
            case "fauna" -> SCANNED_FAUNA;
            case "mineral" -> SCANNED_MINERAL;
            case "structure" -> SCANNED_STRUCTURE;
            case "anomaly" -> SCANNED_ANOMALY;
            default -> null;
        };
    }

    /** Every deed that feeds one field. Used by the screen, and by the harness. */
    public static java.util.List<ElysiumDeed> feeding(ElysiumResearchField field) {
        java.util.List<ElysiumDeed> out = new java.util.ArrayList<>();
        for (ElysiumDeed deed : values()) {
            if (deed.field == field) {
                out.add(deed);
            }
        }
        return out;
    }
}
