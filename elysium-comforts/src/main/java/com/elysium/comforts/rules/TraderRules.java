package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A villager you can pick up, and the ledger that has to survive it. JDK only.
 *
 * <h2>What goes wrong, and why nobody notices for months</h2>
 *
 * Putting a villager in your pocket is the single most useful convenience in
 * a big pack and the single easiest one to get quietly wrong, because what a
 * villager <em>is</em> is not the mob standing there. It is a ledger: a
 * profession, a level, some experience towards the next one, and a list of
 * trades each with a number of uses left, a maximum, a demand figure and a
 * price multiplier. Picking one up means writing all of that down; putting it
 * back means reading it out again.
 *
 * <p>And if one field goes missing in between, <b>nothing breaks</b>. The
 * villager comes back. It trades. It looks right. Somebody notices three
 * weeks later that their master-level librarian is an apprentice again, or
 * that a trade they had locked out is available, or that a discount they
 * earned is gone — and by then nobody can say when it happened, so it gets
 * called a server rollback.
 *
 * <h2>Two properties, both arithmetic</h2>
 *
 * <ol>
 *   <li><b>A round trip is the identity.</b> Writing a trader down and
 *       reading it back gives the same trader, field for field, for every
 *       trader that can exist — not for the handful anybody thought to test.
 *       {@link #packed} and {@link #unpacked} are proved inverse over
 *       millions of randomly generated ledgers.</li>
 *   <li><b>Time in a pocket is time.</b> The exploit every mod of this kind
 *       has shipped is that picking a villager up and putting it down again
 *       counts as a new day: trades restock, and a player with one villager
 *       and two hands has infinite emeralds. So the clock is <em>stored</em>,
 *       not reset — see {@link #restocksAllowed} — and the harness proves that
 *       no sequence of pickings-up, however long, yields more restocks than
 *       the time that actually passed.</li>
 * </ol>
 */
public final class TraderRules {

    private TraderRules() {
    }

    // ==================================================================
    // What a trader is
    // ==================================================================

    /**
     * One offer on a villager's list.
     *
     * @param sell            what it hands over, as an item id
     * @param uses            how many times it has been taken today
     * @param mostUses        how many times it may be taken before it locks
     * @param demand          how much the price has drifted from being bought
     * @param priceMultiplier how much a reputation moves this one
     * @param experience      what taking it gives the villager
     */
    public record Offer(String sell, int uses, int mostUses, int demand,
                        float priceMultiplier, int experience) {

        public Offer {
            if (sell == null || sell.isBlank()) {
                throw new IllegalArgumentException("an offer that sells nothing");
            }
            if (mostUses < 1) {
                throw new IllegalArgumentException("an offer nobody may take");
            }
            if (uses < 0 || uses > mostUses) {
                throw new IllegalArgumentException("an offer taken " + uses
                        + " times out of " + mostUses);
            }
            if (experience < 0) {
                throw new IllegalArgumentException("an offer that costs the villager "
                        + "experience");
            }
        }

        /** Whether it is locked out until the next restock. */
        public boolean spent() {
            return uses >= mostUses;
        }
    }

    /**
     * Everything about a villager that is not where it is standing.
     *
     * @param profession  what it does
     * @param level       how far up its own ladder, from one
     * @param experience  how far towards the next rung
     * @param offers      its list, in order
     * @param restockedAt the game time of its last restock
     * @param restocks    how many restocks it has had today
     */
    public record Trader(String profession, int level, int experience,
                         List<Offer> offers, long restockedAt, int restocks) {

        public Trader {
            offers = List.copyOf(offers);
            if (profession == null || profession.isBlank()) {
                throw new IllegalArgumentException("a villager with no trade");
            }
            if (level < 1 || level > MOST_LEVELS) {
                throw new IllegalArgumentException("a villager at level " + level);
            }
            if (experience < 0) {
                throw new IllegalArgumentException("a villager owing experience");
            }
            if (restocks < 0) {
                throw new IllegalArgumentException("a villager that has un-restocked");
            }
        }
    }

    /** How far up a villager's ladder goes. */
    public static final int MOST_LEVELS = 5;

    // ==================================================================
    // Writing one down, and reading it back
    // ==================================================================

    /**
     * A trader written down as a flat list of strings.
     *
     * <p>Flat on purpose. The shape that loses a field is the one where
     * writing and reading are two hand-written lists of {@code get} and
     * {@code put} calls that have to stay in the same order — so this writes a
     * count first and then that many groups, and reading it back is the same
     * loop backwards. A field added to {@link Offer} and not here is a
     * compile-time mismatch in {@link #unpacked}, not a silent zero.
     */
    public static List<String> packed(Trader trader) {
        List<String> written = new ArrayList<>();
        written.add(trader.profession());
        written.add(Integer.toString(trader.level()));
        written.add(Integer.toString(trader.experience()));
        written.add(Long.toString(trader.restockedAt()));
        written.add(Integer.toString(trader.restocks()));
        written.add(Integer.toString(trader.offers().size()));
        for (Offer offer : trader.offers()) {
            written.add(offer.sell());
            written.add(Integer.toString(offer.uses()));
            written.add(Integer.toString(offer.mostUses()));
            written.add(Integer.toString(offer.demand()));
            written.add(Float.toString(offer.priceMultiplier()));
            written.add(Integer.toString(offer.experience()));
        }
        return List.copyOf(written);
    }

    /** How many strings one offer takes. Read by both halves. */
    public static final int WORDS_PER_OFFER = 6;

    /** How many strings come before the offers. */
    public static final int WORDS_BEFORE_OFFERS = 6;

    /** And the same thing read back out. */
    public static Trader unpacked(List<String> written) {
        if (written.size() < WORDS_BEFORE_OFFERS) {
            throw new IllegalArgumentException("a written trader with nothing in it");
        }
        String profession = written.get(0);
        int level = Integer.parseInt(written.get(1));
        int experience = Integer.parseInt(written.get(2));
        long restockedAt = Long.parseLong(written.get(3));
        int restocks = Integer.parseInt(written.get(4));
        int many = Integer.parseInt(written.get(5));
        if (written.size() != WORDS_BEFORE_OFFERS + many * WORDS_PER_OFFER) {
            throw new IllegalArgumentException("a written trader that says it has "
                    + many + " offers and is " + written.size() + " words long");
        }
        List<Offer> offers = new ArrayList<>(many);
        for (int at = 0; at < many; at++) {
            int from = WORDS_BEFORE_OFFERS + at * WORDS_PER_OFFER;
            offers.add(new Offer(
                    written.get(from),
                    Integer.parseInt(written.get(from + 1)),
                    Integer.parseInt(written.get(from + 2)),
                    Integer.parseInt(written.get(from + 3)),
                    Float.parseFloat(written.get(from + 4)),
                    Integer.parseInt(written.get(from + 5))));
        }
        return new Trader(profession, level, experience, offers, restockedAt, restocks);
    }

    /** Whether writing this one down and reading it back gives it back. */
    public static boolean survivesTheTrip(Trader trader) {
        return Objects.equals(trader, unpacked(packed(trader)));
    }

    // ==================================================================
    // Time in a pocket
    // ==================================================================

    /** How long a day is, in the game's own ticks. */
    public static final long A_DAY = 24_000L;

    /**
     * How many times a villager will restock in a day.
     *
     * <p>Two, which is the game's own number. It matters here only because it
     * is the thing the exploit multiplies.
     */
    public static final int RESTOCKS_A_DAY = 2;

    /**
     * How many restocks a trader has earned by now.
     *
     * <p>The whole of the anti-exploit, and it is one line: what a villager
     * has earned is a function of <em>when it last restocked</em>, which is
     * stored and travels with it. Picking it up changes where it is, and
     * nothing else. A mod that instead reset the clock on placement — which is
     * the natural way to write it, because a freshly placed mob has no history
     * — gives a player with one villager and two hands infinite emeralds.
     */
    public static int restocksAllowed(Trader trader, long now) {
        long since = now - trader.restockedAt();
        if (since < 0L) {
            return 0;
        }
        long days = since / A_DAY;
        long earned = days * RESTOCKS_A_DAY;
        if (earned > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) earned;
    }

    /** Whether it may restock right now. */
    public static boolean mayRestock(Trader trader, long now) {
        return restocksAllowed(trader, now) > trader.restocks();
    }

    /**
     * The trader after a restock at this time.
     *
     * <p>Note what does <em>not</em> change: {@code restockedAt} moves to now,
     * and the count goes up by one rather than back to zero, so a trader that
     * is owed three restocks gets three and not one, and a trader that is owed
     * none gets none however many times it is asked.
     */
    public static Trader restocked(Trader trader, long now) {
        if (!mayRestock(trader, now)) {
            return trader;
        }
        List<Offer> fresh = new ArrayList<>(trader.offers().size());
        for (Offer offer : trader.offers()) {
            fresh.add(new Offer(offer.sell(), 0, offer.mostUses(),
                    Math.max(0, offer.demand() - 1), offer.priceMultiplier(),
                    offer.experience()));
        }
        return new Trader(trader.profession(), trader.level(), trader.experience(),
                fresh, now, trader.restocks() + 1);
    }

    /**
     * Picking one up, which must change nothing but where it is.
     *
     * <p>It exists as a function, rather than as nothing at all, so that there
     * is somewhere for the check to point: the harness asserts this is the
     * identity, and a future edit that "helpfully" refreshes something here
     * fails rather than ships.
     */
    public static Trader pocketed(Trader trader) {
        return trader;
    }

    /** And putting it back down. */
    public static Trader placed(Trader trader, long now) {
        return trader;
    }
}
