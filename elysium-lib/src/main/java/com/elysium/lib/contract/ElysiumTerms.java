package com.elysium.lib.contract;

/**
 * What an office will pay, how long it will wait, and what it does if you fail.
 *
 * <h2>The three numbers a contract system lives or dies on</h2>
 *
 * <ol>
 *   <li><b>Is it worth taking?</b> The pay has to beat the work, or nobody
 *       takes one twice.</li>
 *   <li><b>Is the deadline achievable?</b> A contract nobody can finish is a
 *       trap, and a player who has been trapped once stops reading the
 *       board.</li>
 *   <li><b>Does failing cost something?</b> A deadline with no consequence is
 *       not a deadline; it is a suggestion, and the whole point of §19.2 is
 *       that issued work has a standing consequence attached.</li>
 * </ol>
 *
 * All three are decided here, and all three are walked by the harness across
 * every size of contract the offices can issue — because the failure mode is
 * not that one of them is wrong, it is that they are each fine and the three
 * together make a contract nobody would rationally accept.
 *
 * <h2>Why failing costs less than succeeding pays</h2>
 *
 * Deliberately, and by a wide margin. A system where a failed contract undoes
 * several successful ones teaches a player to take none — the rational move is
 * to stay off the board entirely, which is the one outcome that makes the
 * feature pointless. Failure should sting enough to be felt and never enough to
 * make the board a risk.
 *
 * <h2>No Minecraft here</h2>
 *
 * Arithmetic on ints. The harness walks it.
 */
public final class ElysiumTerms {

    private ElysiumTerms() {
    }

    /** The smallest quantity any office will bother writing down. */
    public static final int MIN_QUANTITY = 4;

    /** The largest a single contract will ask for. */
    public static final int MAX_QUANTITY = 32;

    /** Emeralds per unit of work, before the kind's weight. */
    public static final float PAY_PER_UNIT = 0.75F;

    /** Standing per contract, either meter, before the size scales it. */
    public static final int STANDING_BASE = 3;

    /** Ticks allowed per unit of work. Twenty is one second. */
    public static final int TICKS_PER_UNIT = 20 * 60;

    /** The least time any contract allows, however small. */
    public static final int MIN_DEADLINE = 20 * 60 * 10;

    /** The most, however large. */
    public static final int MAX_DEADLINE = 20 * 60 * 60;

    /** How many contracts one player may hold at once. */
    public static final int MAX_HELD = 3;

    /** The size of ask, clamped to what an office will write down. */
    public static int quantityFor(int rolled) {
        return Math.min(MAX_QUANTITY, Math.max(MIN_QUANTITY, rolled));
    }

    /**
     * What it pays, in emeralds.
     *
     * At least one. A contract that pays nothing is an errand, and an office
     * that hands out errands is one a player learns to walk past.
     */
    public static int payFor(ElysiumContractKind kind, int quantity) {
        int quantised = quantityFor(quantity);
        return Math.max(1, Math.round(quantised * PAY_PER_UNIT * kind.weight()));
    }

    /**
     * What it moves on the issuing office's meter.
     *
     * Grows with the ask but slowly — the square root rather than the size, so
     * that three small contracts are worth more standing than one large one.
     * A player who keeps showing up is better regarded than one who did
     * something enormous once, which is how being regarded works.
     */
    public static int standingFor(ElysiumContractKind kind, int quantity) {
        int quantised = quantityFor(quantity);
        return Math.max(1, STANDING_BASE
                + (int) Math.round(Math.sqrt(quantised * kind.weight()) / 2.0D));
    }

    /**
     * What failing costs, on the same meter.
     *
     * A quarter of what success pays, rounded up, and never nothing. Enough to
     * notice; never enough to make taking a contract a gamble.
     *
     * It was a third, which put the smallest contract on the board at exactly
     * half its own reward — passing the harness's ceiling by landing on it. A
     * rule satisfied at its boundary is one the next edit breaks, and the
     * boundary was the interesting number, so the formula moved rather than
     * the rule.
     */
    public static int penaltyFor(ElysiumContractKind kind, int quantity) {
        return Math.max(1, (standingFor(kind, quantity) + 3) / 4);
    }

    /** How long the office will wait, in ticks. */
    public static int deadlineFor(ElysiumContractKind kind, int quantity) {
        int quantised = quantityFor(quantity);
        long allowed = (long) Math.ceil(quantised * kind.weight()) * TICKS_PER_UNIT;
        return (int) Math.min(MAX_DEADLINE, Math.max(MIN_DEADLINE, allowed));
    }

    /**
     * Minutes per unit of work the deadline actually allows.
     *
     * Derived rather than configured, and printed by the harness: it is the
     * number that says whether a contract is achievable, and nothing else in
     * the codebase states it.
     */
    public static float minutesPerUnit(ElysiumContractKind kind, int quantity) {
        int quantised = quantityFor(quantity);
        return deadlineFor(kind, quantity) / 20.0F / 60.0F / quantised;
    }
}
