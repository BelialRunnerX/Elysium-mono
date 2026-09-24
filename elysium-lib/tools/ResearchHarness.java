package com.elysium.lib.research;

/**
 * The research curriculum, executed.
 *
 * <h2>What is worth running here</h2>
 *
 * Not the attachments — those need a player, a level and a registry, and
 * mocking them would be testing the mock. What can be run, and what decides
 * whether the six fields are a system or a table of numbers, is the
 * <em>pace</em>:
 *
 * <ul>
 *   <li>the bands being crossable at all, and in a sane amount of doing;</li>
 *   <li>every field having a route — a field with no deed feeding it is a bar
 *       that can never move, and nothing else in the codebase would say so;</li>
 *   <li>every field having something to unlock, and every unlock being
 *       reachable before the ceiling;</li>
 *   <li>the six fields being roughly comparable in effort, so that one of them
 *       is not four times the work of another for the same number of bands.</li>
 * </ul>
 *
 * The last is the one that could not be checked by reading: it is decided by
 * three tables in three files, none of which mentions the others.
 *
 * <h2>Measured, not only asserted</h2>
 *
 * Several of these print. "How many distinct creatures must a player scan to
 * reach Adept Xenobiology" is the question that decides whether the track is a
 * description of a playthrough or a chore, and no assertion states it — so it
 * is derived here and printed, and an edit that quietly triples it is visible
 * in the output even while everything still passes.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/rh elysium-lib/tools/ResearchHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/rh" \
 *         com.elysium.lib.research.ResearchHarness
 */
public final class ResearchHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    // ------------------------------------------------------------------
    // Ranks
    // ------------------------------------------------------------------

    /** The bands are ordered, start at nothing, and every total lands in one. */
    private static void ranksAreOrdered() {
        ElysiumResearchRank[] ranks = ElysiumResearchRank.values();
        check(ranks[0].threshold() == 0,
                "the first band starts at " + ranks[0].threshold()
                        + "; every player begins every field at nothing");
        for (int i = 1; i < ranks.length; i++) {
            check(ranks[i].threshold() > ranks[i - 1].threshold(),
                    ranks[i] + " is at " + ranks[i].threshold() + ", not above "
                            + ranks[i - 1] + " at " + ranks[i - 1].threshold());
        }
        // Every total from nothing to well past the ceiling lands in the band
        // whose threshold it has passed and not the next one. Walked rather
        // than spot-checked: an off-by-one at a threshold is one value out of
        // two thousand and is exactly what a hand-picked example misses.
        for (int points = 0; points <= ElysiumResearchRank.CEILING + 200; points++) {
            ElysiumResearchRank here = ElysiumResearchRank.of(points);
            check(points >= here.threshold(),
                    points + " was placed in " + here + ", which starts at "
                            + here.threshold());
            ElysiumResearchRank up = here.next();
            check(up == null || points < up.threshold(),
                    points + " was placed in " + here + " but has reached " + up);
        }
    }

    /** Progress through a band runs 0 to 1 and never leaves it. */
    private static void progressIsBounded() {
        for (int points = 0; points <= ElysiumResearchRank.CEILING + 200; points++) {
            float progress = ElysiumResearchRank.progress(points);
            check(progress >= 0.0F && progress <= 1.0F,
                    "progress at " + points + " is " + progress);
        }
        check(ElysiumResearchRank.progress(ElysiumResearchRank.CEILING) == 1.0F,
                "the bar empties at the top of the track, which reads as a bug");
        check(ElysiumResearchRank.progress(0) == 0.0F,
                "a fresh player's first bar is not empty");
    }

    // ------------------------------------------------------------------
    // Every field has a way in and a way up
    // ------------------------------------------------------------------

    private static void everyFieldIsReachable() {
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            java.util.List<ElysiumDeed> deeds = ElysiumDeed.feeding(field);
            check(!deeds.isEmpty(),
                    field + " has no deed feeding it, so its bar can never move "
                            + "and its unlocks can never be earned");
            check(!ElysiumUnlock.of(field).isEmpty(),
                    field + " unlocks nothing, so there is no reason to raise it");
        }
    }

    private static void everyUnlockIsReachable() {
        // Every field's first unlock arrives early.
        //
        // This replaced an assertion that every unlock sat below the ceiling,
        // which could not fail on its own: a rank above the ceiling is a rank
        // above the last rank, which the ordering check already catches. What
        // is independently worth guarding is the shape of a field's offer — a
        // field whose first reward is three bands up is a field nobody finds
        // out is there, because nothing happens for the whole of the period
        // when a player is deciding whether to bother.
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            java.util.List<ElysiumUnlock> offered = ElysiumUnlock.of(field);
            if (offered.isEmpty()) {
                continue;               // reported by everyFieldIsReachable
            }
            ElysiumUnlock first = offered.get(0);
            check(first.rank().threshold()
                            <= ElysiumResearchRank.PRACTISED.threshold(),
                    field + " offers nothing until " + first.rank()
                            + ". A field whose first reward is that far up is a "
                            + "field a player never learns is there");
        }
        for (ElysiumUnlock unlock : ElysiumUnlock.values()) {
            check(unlock.rank().threshold() < ElysiumResearchRank.CEILING,
                    unlock + " needs " + unlock.rank().threshold()
                            + " and the track ends at "
                            + ElysiumResearchRank.CEILING + "; it would be "
                            + "granted on the last point a player can earn");
            check(!unlock.metBy(unlock.rank().threshold() - 1),
                    unlock + " is granted below its own threshold");
            check(unlock.metBy(unlock.rank().threshold()),
                    unlock + " is not granted at exactly its own threshold");
        }
        // No two unlocks share an id: the id is a translation key and a save
        // key, and two of the same would silently mean one of them.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ElysiumUnlock unlock : ElysiumUnlock.values()) {
            check(ids.add(unlock.id()), "two unlocks share the id " + unlock.id());
        }
        java.util.Set<String> fieldIds = new java.util.HashSet<>();
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            check(fieldIds.add(field.id()), "two fields share the id " + field.id());
        }
    }

    // ------------------------------------------------------------------
    // Pace
    // ------------------------------------------------------------------

    /**
     * How many of a field's cheapest deed it takes to reach each band.
     *
     * The number that decides whether a track is a playthrough or a chore.
     */
    private static int deedsToReach(ElysiumResearchField field,
                                    ElysiumResearchRank rank) {
        int best = Integer.MAX_VALUE;
        for (ElysiumDeed deed : ElysiumDeed.feeding(field)) {
            best = Math.min(best,
                    (rank.threshold() + deed.points() - 1) / deed.points());
        }
        return best;
    }

    private static void thePaceIsReasonable() {
        System.out.println("  what each field costs, in its most generous deed:");
        int slowest = 0;
        int quickest = Integer.MAX_VALUE;
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            int first = deedsToReach(field, ElysiumResearchRank.FAMILIAR);
            int adept = deedsToReach(field, ElysiumResearchRank.ADEPT);
            int top = deedsToReach(field, ElysiumResearchRank.PREEMINENT);
            System.out.println("    " + String.format("%-18s", field.id())
                    + " familiar " + String.format("%4d", first)
                    + ", adept " + String.format("%5d", adept)
                    + ", preeminent " + String.format("%5d", top));
            check(first <= 20,
                    field + " needs " + first + " deeds for its first band. The "
                            + "first band is meant to arrive while a player is "
                            + "still finding out what the field is");
            slowest = Math.max(slowest, top);
            quickest = Math.min(quickest, top);
        }
        // Comparable, not equal. Environmental is time and Geology is finding
        // things, and those will never cost the same; a factor of six would
        // mean one of the six fields is a different game.
        check(slowest <= quickest * 6,
                "the slowest field to finish takes " + slowest + " deeds and the "
                        + "quickest " + quickest + " — more than six times apart, "
                        + "which means one of the six is not really on offer");
    }

    /** Enduring a hazard is the slow way in, and must not be the fast one. */
    private static void enduranceIsNotAShortcut() {
        int perMinute = ElysiumDeed.ENDURED_A_HAZARD.points();
        int hoursToTop = (ElysiumResearchRank.CEILING / perMinute) / 60;
        System.out.println("  environmental: " + perMinute + " a minute, so "
                + hoursToTop + " hours in the weather to finish the track");
        check(hoursToTop >= 4,
                "the Environmental track finishes in " + hoursToTop + " hours of "
                        + "standing still, which makes it the cheapest field in "
                        + "the game and the least interesting");
        // Every repeatable deed is worth strictly less than every once-only
        // one. Stated as a relation rather than as a magic number, because a
        // magic number has to be re-argued every time a deed is added and this
        // does not: the moment repeating something out-earns finding something
        // new, the game is telling players to stop exploring.
        int cheapestNovel = Integer.MAX_VALUE;
        int dearestRepeat = 0;
        for (ElysiumDeed deed : ElysiumDeed.values()) {
            if (deed.novel()) {
                cheapestNovel = Math.min(cheapestNovel, deed.points());
            } else {
                dearestRepeat = Math.max(dearestRepeat, deed.points());
            }
        }
        check(dearestRepeat < cheapestNovel,
                "the best repeatable deed pays " + dearestRepeat + " and the "
                        + "worst discovery pays " + cheapestNovel
                        + ". Grinding now beats exploring");
    }

    /** A scan of every kind maps to a deed, and no kind is left out. */
    private static void everyScanTeachesSomething() {
        for (String kind : new String[]{"flora", "fauna", "mineral",
                                        "structure", "anomaly"}) {
            ElysiumDeed deed = ElysiumDeed.forScanOf(kind);
            check(deed != null,
                    "scanning a " + kind + " teaches nothing; the scanner is the "
                            + "main way into four of the six fields");
            check(deed == null || deed.novel(),
                    "a " + kind + " scan pays every time, so the track can be "
                            + "farmed by rescanning one thing");
        }
        check(ElysiumDeed.forScanOf("something_a_mod_added") == null,
                "an unknown discovery kind is being mapped to a deed rather than "
                        + "being left alone");
        check(ElysiumDeed.forScanOf(null) == null,
                "a null kind is not handled, so one bad scan is a crash");
    }

    /** Round-tripping a field id, the way a save file does. */
    private static void idsSurviveASave() {
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            check(ElysiumResearchField.byId(field.id()) == field,
                    field + " does not come back from its own id");
        }
        check(ElysiumResearchField.byId("a_removed_mods_field") == null,
                "an unknown field id throws instead of being skipped; a player "
                        + "who removes a content mod would lose the world, not "
                        + "one bar");
    }

    public static void main(String[] args) {
        ranksAreOrdered();
        progressIsBounded();
        everyFieldIsReachable();
        everyUnlockIsReachable();
        thePaceIsReasonable();
        enduranceIsNotAShortcut();
        everyScanTeachesSomething();
        idsSurviveASave();

        System.out.println("research: " + ElysiumResearchField.values().length
                + " fields, " + ElysiumResearchRank.values().length + " bands, "
                + ElysiumDeed.values().length + " deeds, "
                + ElysiumUnlock.values().length + " unlocks — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("every field has a way in and something to earn");
    }
}
