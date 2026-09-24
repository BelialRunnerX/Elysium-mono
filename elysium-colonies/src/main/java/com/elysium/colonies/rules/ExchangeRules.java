package com.elysium.colonies.rules;

/**
 * The Frontier Exchange: how a town that has run out of something gets it.
 *
 * <h2>The problem this solves</h2>
 *
 * A colony left to itself will stop. Not because anything is broken — because
 * it wanted forty spruce planks and the world it is on has no spruce, and
 * MineColonies' answer to that is to put the request on a list and wait for a
 * player to walk over with the planks. Every other part of a town's autonomy
 * is real work the town does; this one thing is a note addressed to somebody
 * who is not there.
 *
 * <p>So the town trades its way out. Everything it has too much of is surplus,
 * and surplus buys what it lacks.
 *
 * <h2>Why it is expensive, and why it is capped</h2>
 *
 * Because a town that could buy anything would never need to mine, farm or be
 * sited anywhere in particular, and the charter would stop meaning anything.
 * A town can only put so much through in a day, and what it gets for what it
 * sells is the market's business rather than this file's — see
 * {@link MarketRules}, which prices a thing by where it is and when.
 *
 * <p>What is left here is the part that is about the town rather than about
 * the price: how much it keeps back before anything is spare, how much it will
 * put through in a day, and what it costs in the Empire's attention. Those are
 * limits on the behaviour, and they hold whatever the prices happen to be.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ExchangeRules {

    private ExchangeRules() {
    }

    /**
     * How many of a thing a town keeps before any of it counts as surplus.
     *
     * A stack. Below this it is stock; above it, it is sitting in a warehouse
     * doing nothing. Set at a stack rather than at one so that the exchange
     * never sells the last of something a builder was about to want.
     */
    public static final int KEEP = 64;

    /** What is available to trade out of a given quantity held. */
    public static int surplus(int held) {
        return Math.max(0, held - KEEP);
    }

    /**
     * The most a town will put through the exchange in a day.
     *
     * Two stacks in. Enough to unstick a builder waiting on a few blocks;
     * nowhere near enough to supply a town that has decided not to produce
     * anything.
     */
    public static final int MOST_SPENT_PER_DAY = 128;

    // ------------------------------------------------------------------
    // Being noticed
    // ------------------------------------------------------------------

    /**
     * Suspicion the owner earns per trade.
     *
     * One. A settlement that keeps signalling for supplies is a settlement
     * somebody is keeping a file on, which is the same sentence this module
     * already says about owning a town at all — said again about the way the
     * town stays alive. Small, because a town that traded itself into an
     * Imperial problem by unsticking its builder would be a mechanic that
     * punishes the player for a system working.
     */
    public static final int NOTICE_PER_TRADE = 1;

    /** And a ceiling per day, so a busy day is not a catastrophe. */
    public static final int MOST_NOTICE_PER_DAY = 5;

    /** How much notice a day's trading earns. */
    public static int noticeFor(int trades) {
        return Math.min(MOST_NOTICE_PER_DAY,
                Math.max(0, trades) * NOTICE_PER_TRADE);
    }
}
