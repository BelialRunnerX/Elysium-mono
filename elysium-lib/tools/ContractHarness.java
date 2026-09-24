package com.elysium.lib.contract;

/**
 * Contract terms, executed across every contract an office can write.
 *
 * <h2>What this is guarding</h2>
 *
 * Not that one number is wrong. The failure mode of a contract board is that
 * every number is defensible on its own and the set of them makes an offer no
 * player would rationally accept: the pay is fine, the deadline is fine, the
 * penalty is fine, and taking a contract is still a bad idea. That can only be
 * seen by looking at all three together, at every size, which is what this
 * does.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/coh elysium-lib/tools/ContractHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/coh" \
 *         com.elysium.lib.contract.ContractHarness
 */
public final class ContractHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Every quantity an office can ask for, including out-of-range asks. */
    private static int[] everyQuantity() {
        int[] all = new int[ElysiumTerms.MAX_QUANTITY + 20];
        for (int i = 0; i < all.length; i++) {
            all[i] = i - 4;                 // start below zero on purpose
        }
        return all;
    }

    /** Asking for more never pays less, waits less, or costs less to fail. */
    private static void termsAreMonotonic() {
        for (ElysiumContractKind kind : ElysiumContractKind.values()) {
            int payBefore = 0;
            int standingBefore = 0;
            int deadlineBefore = 0;
            for (int quantity = ElysiumTerms.MIN_QUANTITY;
                 quantity <= ElysiumTerms.MAX_QUANTITY; quantity++) {
                int pay = ElysiumTerms.payFor(kind, quantity);
                int standing = ElysiumTerms.standingFor(kind, quantity);
                int deadline = ElysiumTerms.deadlineFor(kind, quantity);
                check(pay >= payBefore,
                        kind + " pays less for " + quantity + " than for "
                                + (quantity - 1) + ": " + pay + " after " + payBefore);
                check(standing >= standingBefore,
                        kind + " is worth less standing at " + quantity);
                check(deadline >= deadlineBefore,
                        kind + " allows less time for " + quantity + " than for "
                                + (quantity - 1));
                payBefore = pay;
                standingBefore = standing;
                deadlineBefore = deadline;
            }
        }
    }

    /** Nothing is ever free, worthless, or out of bounds. */
    private static void termsAreBounded() {
        for (ElysiumContractKind kind : ElysiumContractKind.values()) {
            for (int rolled : everyQuantity()) {
                int quantity = ElysiumTerms.quantityFor(rolled);
                check(quantity >= ElysiumTerms.MIN_QUANTITY
                                && quantity <= ElysiumTerms.MAX_QUANTITY,
                        "a roll of " + rolled + " produced a quantity of " + quantity);
                check(ElysiumTerms.payFor(kind, rolled) >= 1,
                        kind + " would pay nothing for " + rolled
                                + "; an office that hands out errands is one a "
                                + "player learns to walk past");
                check(ElysiumTerms.standingFor(kind, rolled) >= 1,
                        kind + " would move no standing at " + rolled);
                check(ElysiumTerms.penaltyFor(kind, rolled) >= 1,
                        kind + " would cost nothing to fail at " + rolled
                                + ". A deadline with no consequence is a "
                                + "suggestion");
                int deadline = ElysiumTerms.deadlineFor(kind, rolled);
                check(deadline >= ElysiumTerms.MIN_DEADLINE
                                && deadline <= ElysiumTerms.MAX_DEADLINE,
                        kind + " allows " + deadline + " ticks at " + rolled);
            }
        }
    }

    /**
     * A bigger job is worth more than a small one.
     *
     * Added because a negative test zeroed the pay rate and nothing complained:
     * every contract still paid at least one emerald, because of the floor, and
     * the floor is what the existing check was measuring. The floor guards
     * against an errand; this guards against a board where every job pays the
     * same, which is a board with no decision on it.
     */
    private static void sizeIsWorthSomething() {
        for (ElysiumContractKind kind : ElysiumContractKind.values()) {
            int small = ElysiumTerms.payFor(kind, ElysiumTerms.MIN_QUANTITY);
            int large = ElysiumTerms.payFor(kind, ElysiumTerms.MAX_QUANTITY);
            check(large > small * 2,
                    kind + " pays " + small + " for "
                            + ElysiumTerms.MIN_QUANTITY + " and " + large
                            + " for " + ElysiumTerms.MAX_QUANTITY
                            + ". A board where the size of the job barely "
                            + "changes the pay is a board with no decision on it");
            int smallStanding = ElysiumTerms.standingFor(kind, ElysiumTerms.MIN_QUANTITY);
            int largeStanding = ElysiumTerms.standingFor(kind, ElysiumTerms.MAX_QUANTITY);
            check(largeStanding > smallStanding,
                    kind + " is worth the same standing whatever the size");
        }
    }

    /**
     * Failing costs less than succeeding pays — by a wide margin.
     *
     * The single most important relation on the board. A system where a failed
     * contract undoes several successful ones teaches a player that the
     * rational move is to take none, which is the one outcome that makes the
     * whole feature pointless.
     */
    private static void failingIsNotARisk() {
        float worst = 0.0F;
        for (ElysiumContractKind kind : ElysiumContractKind.values()) {
            for (int quantity = ElysiumTerms.MIN_QUANTITY;
                 quantity <= ElysiumTerms.MAX_QUANTITY; quantity++) {
                int standing = ElysiumTerms.standingFor(kind, quantity);
                int penalty = ElysiumTerms.penaltyFor(kind, quantity);
                check(penalty < standing,
                        kind + " at " + quantity + " pays " + standing
                                + " and costs " + penalty + " to fail. Taking "
                                + "one is a gamble, so the rational move is to "
                                + "take none");
                worst = Math.max(worst, penalty / (float) standing);
            }
        }
        System.out.println("  risk: the worst contract on the board costs "
                + String.format(java.util.Locale.ROOT, "%.0f%%", worst * 100)
                + " of its own reward to fail");
        check(worst <= 0.5F,
                "a contract can cost " + String.format(java.util.Locale.ROOT,
                        "%.0f%%", worst * 100) + " of its reward to fail; past "
                        + "about half, two failures wipe out a success and the "
                        + "board becomes something to avoid");
    }

    /**
     * Every contract is achievable, and none is trivial.
     *
     * Stated as minutes per unit of work, which is the number a player
     * actually experiences and which nothing else in the codebase writes down.
     */
    private static void deadlinesAreAchievable() {
        System.out.println("  pace, in minutes allowed per unit of work:");
        for (ElysiumContractKind kind : ElysiumContractKind.values()) {
            float tightest = Float.MAX_VALUE;
            float loosest = 0.0F;
            for (int quantity = ElysiumTerms.MIN_QUANTITY;
                 quantity <= ElysiumTerms.MAX_QUANTITY; quantity++) {
                float pace = ElysiumTerms.minutesPerUnit(kind, quantity);
                tightest = Math.min(tightest, pace);
                loosest = Math.max(loosest, pace);
            }
            System.out.println("    " + String.format("%-10s", kind.id())
                    + String.format(java.util.Locale.ROOT, " %.1f to %.1f",
                            tightest, loosest));
            check(tightest >= 0.5F,
                    kind + " allows as little as " + tightest + " minutes per "
                            + "unit, which is not a deadline anybody meets");
            check(loosest <= 30.0F,
                    kind + " allows up to " + loosest + " minutes per unit; a "
                            + "deadline nobody can miss is not one");
        }
    }

    /** A harder kind of work is worth more per unit than an easier one. */
    private static void harderWorkPaysMore() {
        int quantity = 16;
        int deliver = ElysiumTerms.payFor(ElysiumContractKind.DELIVER, quantity);
        int cull = ElysiumTerms.payFor(ElysiumContractKind.CULL, quantity);
        int survey = ElysiumTerms.payFor(ElysiumContractKind.CATALOGUE, quantity);
        check(cull > deliver && survey > cull,
                "for the same quantity, a delivery pays " + deliver + ", a cull "
                        + cull + " and a survey " + survey + "; they are not in "
                        + "the order of how hard they are");
        ElysiumContractKind[] kinds = ElysiumContractKind.values();
        for (int i = 1; i < kinds.length; i++) {
            check(kinds[i].weight() > kinds[i - 1].weight(),
                    kinds[i] + " is weighted no higher than " + kinds[i - 1]);
        }
    }

    /** The contract record behaves at its edges. */
    private static void theRecordHolds() {
        ElysiumContract contract = new ElysiumContract("deliver",
                "minecraft:iron_ingot", 10, 0, 1000L, 600, 8, 4, true);
        check(contract.remaining(1000L) == 600L,
                "a fresh contract has " + contract.remaining(1000L) + " left");
        check(contract.remaining(1600L) == 0L && contract.expired(1600L),
                "a contract does not expire on the tick its deadline passes");
        check(contract.remaining(999_999L) == 0L,
                "a long-expired contract reports negative time left");
        check(!contract.expired(1599L),
                "a contract expires one tick early");
        check(contract.advancedBy(50).done() == 10,
                "progress can exceed the quantity asked for, so the readout "
                        + "says more than the office asked");
        check(contract.advancedBy(0) == contract,
                "advancing by nothing produced a new record");
        check(contract.advancedBy(-5).done() == 0,
                "advancing by a negative undid progress");
        check(contract.advancedBy(10).complete(),
                "a fully-advanced contract does not read as complete");
        check(new ElysiumContract("nonsense", "", 4, 0, 0L, 600, 1, 1, true)
                        .resolve() == null,
                "an unknown contract kind throws rather than reading as none");
        check(new ElysiumContract("nonsense", "", 4, 0, 0L, 600, 1, 1, true)
                        .penalty() >= 1,
                "an unreadable contract costs nothing to fail");
    }

    /** A player can hold enough to be interesting and not enough to hoard. */
    private static void theBoardIsBounded() {
        check(ElysiumTerms.MAX_HELD >= 2,
                "a player may hold only " + ElysiumTerms.MAX_HELD
                        + " contract(s), so there is never a choice to make");
        check(ElysiumTerms.MAX_HELD <= 5,
                "a player may hold " + ElysiumTerms.MAX_HELD + " contracts; past "
                        + "a handful the board is a chore list rather than a "
                        + "decision");
    }

    public static void main(String[] args) {
        termsAreMonotonic();
        termsAreBounded();
        sizeIsWorthSomething();
        failingIsNotARisk();
        deadlinesAreAchievable();
        harderWorkPaysMore();
        theRecordHolds();
        theBoardIsBounded();

        System.out.println("contracts: " + ElysiumContractKind.values().length
                + " kinds across " + (ElysiumTerms.MAX_QUANTITY
                        - ElysiumTerms.MIN_QUANTITY + 1) + " sizes — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("the board is worth reading");
    }
}
