package com.elysium.lib.research;

/**
 * What knowing something gets you.
 *
 * <h2>The rule, stated once: research never buys power</h2>
 *
 * Every unlock here is <em>information</em> or <em>access</em>. Nothing in this
 * file raises damage, health, armour, speed or drop rates, and nothing ever
 * will — the validator enforces it by refusing any unlock whose effect names a
 * combat stat, and there are negative tests that prove the refusal works.
 *
 * That is a design decision worth defending rather than a stylistic one. A
 * research tree that grants power has to be pursued: a player who skips it is
 * playing a harder game, so it stops being a description of what they did and
 * becomes a checklist they resent. A tree that grants information can be
 * ignored completely and the game is the same game — slightly more opaque.
 * Which means the players who pursue it are the ones who wanted to know, and
 * that is the only group it was ever for.
 *
 * <h2>The shape of an unlock</h2>
 *
 * A field, a rank, and an effect. The effect is a named capability that some
 * other part of the mod asks about — {@code ElysiumResearch.has(player,
 * SURVEY_DEPTH)} — rather than a callback registered here, so this file stays a
 * table and the behaviour stays next to the thing that behaves.
 *
 * <h2>Nothing here is aspirational</h2>
 *
 * Every constant in this enum is read somewhere, and the validator fails the
 * build if one is not. An unlock that is declared, translated, announced to the
 * player on promotion and then does nothing is worse than a missing feature: it
 * is a promise the game makes out loud and does not keep, and it is invisible
 * to everything except a player who went looking for what they had earned.
 *
 * That rule is why this list is short. Several obvious entries — a bench that
 * previews a reroll, a forecast read off a rift before stepping through it —
 * are not here, because they need a screen or an allocation that does not exist
 * yet. They belong in the table on the day they work.
 */
public enum ElysiumUnlock {

    // --- Geology ---------------------------------------------------------
    /** The scanner reports the depth a vein was found at. */
    SURVEY_DEPTH("survey_depth", ElysiumResearchField.GEOLOGY,
            ElysiumResearchRank.FAMILIAR),

    // --- Xenobiology -----------------------------------------------------
    /** The scanner names a creature's category and how much of it there is. */
    FIELD_TAXONOMY("field_taxonomy", ElysiumResearchField.XENOBIOLOGY,
            ElysiumResearchRank.FAMILIAR),

    // --- Environmental ---------------------------------------------------
    /** The hazard warning shows the number, not just the colour. */
    HAZARD_TELEMETRY("hazard_telemetry", ElysiumResearchField.ENVIRONMENTAL,
            ElysiumResearchRank.FAMILIAR),
    /** And how much of it your protections are actually stopping. */
    PROTECTION_AUDIT("protection_audit", ElysiumResearchField.ENVIRONMENTAL,
            ElysiumResearchRank.PRACTISED),

    // --- Imperial systems ------------------------------------------------
    /** The Archive shows the band edges and the decay rate, not just the band. */
    LEDGER_DETAIL("ledger_detail", ElysiumResearchField.IMPERIAL_SYSTEMS,
            ElysiumResearchRank.FAMILIAR),

    // --- Rift science ----------------------------------------------------
    /** Arriving in a dungeon, you already know its size and where the loot is. */
    RIFT_CARTOGRAPHY("rift_cartography", ElysiumResearchField.RIFT_SCIENCE,
            ElysiumResearchRank.PRACTISED),

    // --- Engineering -----------------------------------------------------
    /** A bench states what a piece of gear currently is, in full. */
    BENCH_TEARDOWN("bench_teardown", ElysiumResearchField.ENGINEERING,
            ElysiumResearchRank.FAMILIAR);

    private final String id;
    private final ElysiumResearchField field;
    private final ElysiumResearchRank rank;

    ElysiumUnlock(String id, ElysiumResearchField field, ElysiumResearchRank rank) {
        this.id = id;
        this.field = field;
        this.rank = rank;
    }

    public String id() {
        return id;
    }

    public ElysiumResearchField field() {
        return field;
    }

    /** The band at which this becomes available. */
    public ElysiumResearchRank rank() {
        return rank;
    }

    public String translationKey() {
        return "elysium.research.unlock." + id;
    }

    public String blurbKey() {
        return "elysium.research.unlock." + id + ".blurb";
    }

    /** Whether a point total in this unlock's field is enough for it. */
    public boolean metBy(int points) {
        return points >= rank.threshold();
    }

    /** Everything one field unlocks, in the order it unlocks. */
    public static java.util.List<ElysiumUnlock> of(ElysiumResearchField field) {
        java.util.List<ElysiumUnlock> out = new java.util.ArrayList<>();
        for (ElysiumUnlock unlock : values()) {
            if (unlock.field == field) {
                out.add(unlock);
            }
        }
        out.sort(java.util.Comparator.comparingInt(one -> one.rank.threshold()));
        return out;
    }
}
