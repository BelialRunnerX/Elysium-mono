package com.elysium.lib.discovery;

/**
 * What filing a discovery is worth, executed.
 *
 * <h2>Why this is arithmetic worth running</h2>
 *
 * §17.3 calls filing "a repeated low-stakes lesson in visibility versus
 * reward", and that phrase is a constraint on numbers, not a description of a
 * feeling. Low-stakes means no single scan can matter much. Repeated means the
 * pattern has to accumulate into something. Those two pull against each other,
 * and the table in {@link ElysiumFiling} is where they are balanced — so it is
 * the table that has to be checked rather than read.
 *
 * The specific failure this guards against: a player who catalogues diligently
 * being pushed into a Suspicion band they never chose. Cataloguing is a
 * peaceful activity and §14.2's bands are meant to be reached by what you do.
 */
public final class DiscoveryHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** The three offices have to be genuinely different offers. */
    private static void theThreeOffers() {
        for (ElysiumDiscoveryKind kind : ElysiumDiscoveryKind.values()) {
            int empire = ElysiumFiling.EMPIRE.payFor(kind);
            int unsworn = ElysiumFiling.UNSWORN.payFor(kind);
            int quiet = ElysiumFiling.PRIVATE.payFor(kind);

            check(empire > unsworn,
                    kind + ": the Empire pays " + empire + " and the Unsworn "
                            + unsworn + " — the Empire is supposed to pay more");
            check(unsworn > quiet,
                    kind + ": the Unsworn pay " + unsworn + " and telling nobody "
                            + "pays " + quiet + " — there is no reason to file at all");
            check(quiet == 0, kind + ": keeping it private paid " + quiet);

            // Each office moves exactly one meter, and private moves neither.
            check(ElysiumFiling.EMPIRE.suspicionFor(kind) > 0
                            && ElysiumFiling.EMPIRE.favorFor(kind) == 0,
                    kind + ": filing with the Empire does not raise Suspicion "
                            + "alone, so the cost of being seen is not the cost");
            check(ElysiumFiling.UNSWORN.favorFor(kind) > 0
                            && ElysiumFiling.UNSWORN.suspicionFor(kind) == 0,
                    kind + ": filing with the Unsworn does not raise Favor alone");
            check(ElysiumFiling.PRIVATE.suspicionFor(kind) == 0
                            && ElysiumFiling.PRIVATE.favorFor(kind) == 0,
                    kind + ": telling nobody moved a meter");
        }
        System.out.println("  offers: Empire pays full and is noticed, Unsworn "
                + "pay half and approve, private pays nothing and is invisible");
    }

    /** Harder things to find are worth more to report. */
    private static void rewardsRiseWithDifficulty() {
        ElysiumDiscoveryKind[] byDifficulty = ElysiumDiscoveryKind.values();
        int previousPay = -1;
        int previousFavor = -1;
        for (ElysiumDiscoveryKind kind : byDifficulty) {
            int pay = ElysiumFiling.EMPIRE.payFor(kind);
            int favor = ElysiumFiling.UNSWORN.favorFor(kind);
            check(pay >= previousPay,
                    kind + " pays " + pay + ", less than the easier kind before "
                            + "it — so the catalogue is best filled in a meadow");
            check(favor >= previousFavor,
                    kind + " is worth " + favor + " Favor, less than the easier "
                            + "kind before it");
            previousPay = pay;
            previousFavor = favor;
        }
        check(ElysiumFiling.EMPIRE.payFor(ElysiumDiscoveryKind.ANOMALY)
                        >= ElysiumFiling.EMPIRE.payFor(ElysiumDiscoveryKind.FLORA) * 4,
                "an anomaly pays less than four times what a flower does, which "
                        + "is not enough to be worth the journey");

        StringBuilder line = new StringBuilder("  by kind: ");
        for (ElysiumDiscoveryKind kind : byDifficulty) {
            line.append(kind.getId()).append(' ')
                .append(ElysiumFiling.EMPIRE.payFor(kind)).append("e/")
                .append(ElysiumFiling.UNSWORN.favorFor(kind)).append("f  ");
        }
        System.out.println(line.toString().trim());
    }

    /**
     * Low-stakes: cataloguing must not push anybody into a band by itself.
     *
     * The number that matters. A player filing everything they see with the
     * Empire should reach the Empire's notice eventually — that is the lesson —
     * but should have to work at it, and should never be swept past it by a
     * productive afternoon.
     */
    private static void cataloguingIsLowStakes() {
        final int notice = com.elysium.lib.standing.ElysiumStanding.NOTICE;

        int worstPerFiling = 0;
        for (ElysiumDiscoveryKind kind : ElysiumDiscoveryKind.values()) {
            worstPerFiling = Math.max(worstPerFiling,
                    ElysiumFiling.EMPIRE.suspicionFor(kind));
        }
        check(worstPerFiling > 0, "filing with the Empire is free of consequence");

        int filingsToNotice = notice / worstPerFiling;
        check(filingsToNotice >= 15,
                "filing " + filingsToNotice + " discoveries reaches the Empire's "
                        + "notice at " + notice + " Suspicion. That is one "
                        + "afternoon of scanning, which makes a peaceful activity "
                        + "the fastest route to being Marked");

        // ...and it must be reachable at all, or the choice is not a choice.
        check(filingsToNotice <= 200,
                "it takes " + filingsToNotice + " filings to be noticed at all, "
                        + "so the Empire half of the decision never arrives");

        System.out.println("  low stakes: " + worstPerFiling + " Suspicion a "
                + "filing, " + filingsToNotice + " of them to reach the Empire's "
                + "notice at " + notice);
    }

    /** The scanner cycles through every office and comes back. */
    private static void theCycleIsClosed() {
        ElysiumFiling start = ElysiumFiling.values()[0];
        ElysiumFiling at = start;
        java.util.Set<ElysiumFiling> seen = new java.util.HashSet<>();
        for (int step = 0; step < ElysiumFiling.values().length; step++) {
            check(seen.add(at), "the filing cycle repeats " + at
                    + " before it has visited every option, so some office "
                    + "cannot be selected at all");
            at = at.next();
        }
        check(at == start,
                "cycling through every option does not return to the first, so "
                        + "the setting cannot be put back without dropping the item");
        check(seen.size() == ElysiumFiling.values().length,
                "the cycle reaches " + seen.size() + " of "
                        + ElysiumFiling.values().length + " offices");
    }

    public static void main(String[] args) {
        theThreeOffers();
        rewardsRiseWithDifficulty();
        cataloguingIsLowStakes();
        theCycleIsClosed();

        System.out.println("discovery: three offices, five kinds and the cost of "
                + "being seen — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("visibility costs, quiet pays nothing, and neither is free");
    }
}
