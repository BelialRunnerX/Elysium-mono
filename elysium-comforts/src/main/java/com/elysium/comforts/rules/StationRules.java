package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eight blocks that each do one thing a player would otherwise do by hand.
 *
 * <h2>What automating a villager is actually for</h2>
 *
 * Not speed. A player standing at a villager trades faster than any machine
 * will, because trading is one click and a machine has to be fed. What a
 * station buys is <em>attention</em>: it goes on working while you are three
 * thousand blocks away doing something else.
 *
 * <p>Which gives the rule this file is built on. A station may be slower than
 * a player, and it usually is; what it may never be is <em>better value</em>.
 * The moment one of these gives more out per thing put in than a player
 * standing there would get, it stops being a convenience and becomes a
 * multiplier — and a multiplier on a villager is a multiplier on emeralds,
 * which is a multiplier on everything.
 *
 * <p>So {@link #faultsInValue} holds every station to what a hand does with
 * the same input, and {@link #faultsInPace} holds every one of them to being
 * no quicker. Both directions matter: a station that is faster <em>and</em>
 * better value is a reason never to trade by hand again, and a station that
 * is slower and worse value is a block nobody places twice.
 */
public final class StationRules {

    private StationRules() {
    }

    // ==================================================================
    // The eight jobs
    // ==================================================================

    /** Hold one villager where you can reach it. */
    public static final String TRADE = "trade";

    /** And do that without you. */
    public static final String AUTO_TRADE = "auto_trade";

    /** Two villagers and some food make a third. */
    public static final String BREED = "breed";

    /** A zombie villager and the cure become a villager. */
    public static final String CURE = "cure";

    /** A child becomes an adult. */
    public static final String RAISE = "raise";

    /** A farmer works a plot. */
    public static final String FARM = "farm";

    /** Villagers frightened enough to call for help. */
    public static final String IRON = "iron";

    /** See what one is carrying. */
    public static final String LOOK = "look";

    /** The eight, in the order everything walks them. */
    public static final List<String> JOBS = List.of(
            TRADE, AUTO_TRADE, BREED, CURE, RAISE, FARM, IRON, LOOK);

    /**
     * One station.
     *
     * @param id      the block's name
     * @param job     which of the eight it does
     * @param takes   how many of whatever it consumes, per go
     * @param gives   and how many it hands back
     * @param ticks   how long one go takes
     * @param holds   whether it has a villager in it
     */
    public record Station(String id, String job, int takes, int gives, int ticks,
                          boolean holds) {

        public Station {
            if (takes < 0 || gives < 0) {
                throw new IllegalArgumentException(id + " takes " + takes
                        + " and gives " + gives);
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException(id + " does its job in "
                        + ticks + " ticks, which is not a length of time");
            }
        }

        /** How much comes out per thing put in. */
        public double perInput() {
            return takes <= 0 ? gives : gives / (double) takes;
        }
    }

    /** Ticks in an hour. */
    public static final int AN_HOUR = 72_000;

    /**
     * The eight.
     *
     * <p>Every one of them slower than a player and none of them better
     * value. The numbers are what a station costs and what it hands back;
     * what a hand does with the same input is {@link #BY_HAND}, and the rules
     * below compare the two rather than trusting either.
     */
    public static final List<Station> STATIONS = List.of(
            new Station("trading_station", TRADE, 1, 1, 60, true),
            new Station("auto_trader", AUTO_TRADE, 1, 1, 200, true),
            new Station("breeder", BREED, 12, 1, 1_200, true),
            new Station("converter", CURE, 1, 1, 3_000, true),
            new Station("incubator", RAISE, 1, 1, 2_400, true),
            new Station("villager_farm", FARM, 1, 3, 600, true),
            new Station("iron_farm", IRON, 1, 1, 6_000, true),
            new Station("inventory_viewer", LOOK, 0, 0, 20, false));

    /**
     * What a player gets out of the same input, doing it themselves.
     *
     * <p>Named here because it is the thing every station is measured
     * against, and a number a station is measured against cannot live in the
     * station's own row — that is the same mistake as a mob whose reward is
     * written beside its danger, which this pack has made and corrected
     * twice.
     */
    public static final Map<String, Double> BY_HAND = byHand();

    private static Map<String, Double> byHand() {
        Map<String, Double> made = new LinkedHashMap<>();
        made.put(TRADE, 1.0D);
        made.put(AUTO_TRADE, 1.0D);
        made.put(BREED, 1.0D / 12.0D);
        made.put(CURE, 1.0D);
        made.put(RAISE, 1.0D);
        made.put(FARM, 3.0D);
        made.put(IRON, 1.0D);
        made.put(LOOK, 0.0D);
        return Map.copyOf(made);
    }

    /** How long a player takes over one of these, in ticks. */
    public static final Map<String, Integer> BY_HAND_TICKS = byHandTicks();

    private static Map<String, Integer> byHandTicks() {
        Map<String, Integer> made = new LinkedHashMap<>();
        made.put(TRADE, 20);
        made.put(AUTO_TRADE, 20);
        made.put(BREED, 600);
        made.put(CURE, 2_400);
        made.put(RAISE, 1_200);
        made.put(FARM, 200);
        made.put(IRON, 3_000);
        made.put(LOOK, 20);
        return Map.copyOf(made);
    }

    /** The station with this id, or null. */
    public static Station station(String id) {
        for (Station one : STATIONS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** How many goes an hour of this station comes to. */
    public static double goesAnHour(Station station) {
        return AN_HOUR / (double) station.ticks();
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the stations. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInJobs(STATIONS, JOBS));
        bad.addAll(faultsInOneEach(STATIONS));
        bad.addAll(faultsInNames(STATIONS));
        bad.addAll(faultsInGaps(STATIONS, JOBS));
        bad.addAll(faultsInValue(STATIONS, BY_HAND));
        bad.addAll(faultsInPace(STATIONS, BY_HAND_TICKS));
        bad.addAll(faultsInHolding(STATIONS));
        return List.copyOf(bad);
    }

    /** Whether every station does one of the eight things. */
    public static List<String> faultsInJobs(List<Station> stations,
                                            List<String> jobs) {
        List<String> bad = new ArrayList<>();
        for (Station station : stations) {
            if (!jobs.contains(station.job())) {
                bad.add(station.id() + " does " + station.job() + ", which is "
                        + "not one of the eight things anything here knows how "
                        + "to do, so it is a block that sits there");
            }
        }
        return bad;
    }

    /**
     * Whether two stations do the same job.
     *
     * <p>Its own rule, because a roster with a job done twice is also a
     * roster with a job done by nothing, and a control aimed at either was
     * answered by the other.
     */
    public static List<String> faultsInOneEach(List<Station> stations) {
        List<String> bad = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Station station : stations) {
            if (!done.add(station.job())) {
                bad.add(station.job() + " is done by " + station.id() + " and by "
                        + "something else as well, so one of the two is a block "
                        + "nobody would ever place");
            }
        }
        return bad;
    }

    /** Whether two stations are written under one name. */
    public static List<String> faultsInNames(List<Station> stations) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Station station : stations) {
            if (!named.add(station.id())) {
                bad.add(station.id() + " is written twice, so one of the two "
                        + "rows is a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether any of the eight jobs has nothing to do it. */
    public static List<String> faultsInGaps(List<Station> stations,
                                            List<String> jobs) {
        List<String> bad = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Station station : stations) {
            done.add(station.job());
        }
        for (String job : jobs) {
            if (!done.contains(job)) {
                bad.add("nothing does " + job + ", so it is a job the rules "
                        + "name and no block performs");
            }
        }
        return bad;
    }

    /**
     * Whether any station is better value than a player's own hands.
     *
     * <p>The rule this file exists for. Handed what a hand does so it cannot
     * re-solve itself against a broken table.
     */
    public static List<String> faultsInValue(List<Station> stations,
                                             Map<String, Double> byHand) {
        List<String> bad = new ArrayList<>();
        for (Station station : stations) {
            Double hand = byHand.get(station.job());
            if (hand == null) {
                bad.add("nothing says what a player gets out of " + station.job()
                        + " by hand, so " + station.id() + " is measured against "
                        + "nothing and could hand back anything");
            }
            if (hand != null && station.perInput() > hand + 1.0E-9D) {
                bad.add(station.id() + " hands back " + station.perInput()
                        + " for every one put in and a player doing it "
                        + "themselves gets " + hand + " — so it is not a "
                        + "convenience, it is a multiplier, and a multiplier on "
                        + "a villager is a multiplier on everything");
            }
        }
        return bad;
    }

    /**
     * Whether any station is quicker than the player it replaces.
     *
     * <p>What a station buys is attention rather than speed: it works while
     * you are somewhere else. One that is quicker as well is a reason never
     * to stand at a villager again, and a pack where nobody stands at a
     * villager is a pack with no villagers in it.
     */
    public static List<String> faultsInPace(List<Station> stations,
                                            Map<String, Integer> byHand) {
        List<String> bad = new ArrayList<>();
        for (Station station : stations) {
            Integer hand = byHand.get(station.job());
            if (hand == null) {
                bad.add("nothing says how long a player takes over "
                        + station.job() + ", so " + station.id() + " is as "
                        + "quick as somebody felt like");
            }
            if (hand != null && station.ticks() < hand) {
                bad.add(station.id() + " does in " + station.ticks()
                        + " ticks what a player does in " + hand + ", so it is "
                        + "quicker as well as unattended and there is no reason "
                        + "left to do it yourself");
            }
        }
        return bad;
    }

    /**
     * Whether a station that holds a villager is one you can get it back from.
     *
     * <p>This module's own theme, applied to something alive: a block that
     * swallows a villager is the same failure as a grave with too few slots,
     * except that what it ate had a name and a ledger.
     */
    public static List<String> faultsInHolding(List<Station> stations) {
        List<String> bad = new ArrayList<>();
        int holding = 0;
        for (Station station : stations) {
            if (station.holds()) {
                holding++;
            }
            if (station.holds() && station.gives() < 0) {
                bad.add(station.id() + " holds a villager and hands back "
                        + station.gives() + " of what it is given");
            }
        }
        if (holding == 0) {
            bad.add("not one of the stations holds a villager, so all eight of "
                    + "them are blocks that do something to nothing");
        }
        if (holding == stations.size()) {
            bad.add("every station holds a villager, including the one that is "
                    + "only meant to let you look at what it is carrying — so "
                    + "looking at a villager costs you the villager");
        }
        return bad;
    }
}
