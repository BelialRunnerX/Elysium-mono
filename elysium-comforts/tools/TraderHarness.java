package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every number in {@link TraderRules}, executed.
 *
 * <p>Two claims. Writing a villager down and reading it back gives the same
 * villager — for every villager that can exist, not for the three somebody
 * thought to test — and no amount of picking one up and putting it down
 * yields more restocks than the time that actually passed.
 *
 * <p>The second is the one that matters. Every mod that lets you carry a
 * villager has, at some point, shipped the version where placing one resets
 * its clock, because a freshly placed mob having no history is the natural way
 * to write it. That version is a player with one villager, two hands and
 * infinite emeralds, and it is never reported as an exploit — it is reported
 * as "trading feels a bit easy in this pack".
 */
public final class TraderHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    private static final String[] JOBS = {
            "farmer", "librarian", "cleric", "armourer", "toolsmith", "weaponsmith",
            "cartographer", "fletcher", "shepherd", "butcher", "leatherworker",
            "mason", "nitwit"};

    private static final String[] GOODS = {
            "minecraft:emerald", "minecraft:enchanted_book", "minecraft:bread",
            "minecraft:iron_pickaxe", "minecraft:glass", "elysium:voidglass_ingot",
            "minecraft:cooked_beef", "minecraft:map"};

    private static TraderRules.Trader someone(Random random) {
        int many = random.nextInt(11);
        List<TraderRules.Offer> offers = new ArrayList<>(many);
        for (int at = 0; at < many; at++) {
            int mostUses = 1 + random.nextInt(64);
            offers.add(new TraderRules.Offer(
                    GOODS[random.nextInt(GOODS.length)],
                    random.nextInt(mostUses + 1),
                    mostUses,
                    random.nextInt(64),
                    // Awkward on purpose: a price multiplier is the field a
                    // round trip through text is likeliest to round off.
                    random.nextFloat() * 2.0F,
                    random.nextInt(20)));
        }
        return new TraderRules.Trader(
                JOBS[random.nextInt(JOBS.length)],
                1 + random.nextInt(TraderRules.MOST_LEVELS),
                random.nextInt(500),
                offers,
                random.nextInt(1_000_000),
                random.nextInt(4));
    }

    public static void main(String[] args) {
        theRoundTrip();
        theClock();
        theExploit();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a villager written down and read back is "
                + "the same villager, and no amount of carrying one about earns it a "
                + "restock that time did not");
    }

    // ==================================================================

    private static void theRoundTrip() {
        Random random = new Random(60_221_408L);
        int offers = 0;
        for (int round = 0; round < 300_000; round++) {
            TraderRules.Trader who = someone(random);
            offers += who.offers().size();
            check(TraderRules.survivesTheTrip(who),
                    "a trader did not survive being written down: " + who + " came back "
                            + "as " + TraderRules.unpacked(TraderRules.packed(who)));

            // Field by field, so a failure says which one went missing rather
            // than only that one did.
            TraderRules.Trader back = TraderRules.unpacked(TraderRules.packed(who));
            check(back.profession().equals(who.profession()), "the profession changed");
            check(back.level() == who.level(), "the level changed");
            check(back.experience() == who.experience(), "the experience changed");
            check(back.restockedAt() == who.restockedAt(), "the restock clock changed");
            check(back.restocks() == who.restocks(), "the restock count changed");
            check(back.offers().size() == who.offers().size(), "an offer went missing");
            for (int at = 0; at < who.offers().size(); at++) {
                TraderRules.Offer was = who.offers().get(at);
                TraderRules.Offer now = back.offers().get(at);
                check(now.sell().equals(was.sell()), "an offer sells something else");
                check(now.uses() == was.uses(), "an offer's uses changed, so a trade a "
                        + "player had locked out is available again");
                check(now.mostUses() == was.mostUses(), "an offer's limit changed");
                check(now.demand() == was.demand(), "an offer's demand changed, so a "
                        + "price a player moved is back where it started");
                check(now.priceMultiplier() == was.priceMultiplier(),
                        "an offer's price multiplier changed, which is a discount a "
                        + "player earned, gone");
                check(now.experience() == was.experience(), "an offer's reward changed");
            }

            // And twice, because a round trip that is the identity once and
            // not twice is a round trip that loses a little each time.
            check(TraderRules.unpacked(TraderRules.packed(back)).equals(who),
                    "a trader survived one round trip and not two, so it loses a little "
                            + "each time it is carried");

            // Picking one up must be the identity outright.
            check(TraderRules.pocketed(who).equals(who),
                    "picking a villager up changed it");
            check(TraderRules.placed(who, random.nextInt(1_000_000)).equals(who),
                    "putting a villager down changed it");
        }
        check(offers > 1_000_000,
                "only " + offers + " offers were generated, so the round trip is being "
                        + "proved about villagers with almost nothing to lose");
        System.out.println("  the round trip: 300,000 traders and " + offers
                + " offers written down and read back, field for field, twice");
    }

    private static void theClock() {
        TraderRules.Trader who = new TraderRules.Trader("librarian", 3, 40,
                List.of(new TraderRules.Offer("minecraft:enchanted_book", 12, 12, 3,
                        1.0F, 5)),
                0L, 0);
        check(!TraderRules.mayRestock(who, 0L),
                "a villager that has just restocked may restock again");
        check(!TraderRules.mayRestock(who, TraderRules.A_DAY - 1L),
                "a villager may restock before a day has passed");
        check(TraderRules.mayRestock(who, TraderRules.A_DAY),
                "a villager may not restock after a whole day");

        TraderRules.Trader after = TraderRules.restocked(who, TraderRules.A_DAY);
        check(after.offers().get(0).uses() == 0,
                "a restock did not free the offers");
        check(after.offers().get(0).demand() < who.offers().get(0).demand(),
                "a restock did not bring demand back down, so a price a player pushed "
                        + "up stays up for ever");
        check(after.restockedAt() == TraderRules.A_DAY,
                "a restock did not move the clock, so the next one is free");
        check(after.restocks() == 1, "a restock was not counted");

        // Time that has not passed earns nothing, however often it is asked.
        TraderRules.Trader asked = who;
        for (int round = 0; round < 1_000; round++) {
            asked = TraderRules.restocked(asked, 10L);
        }
        check(asked.equals(who),
                "asking a villager to restock a thousand times inside one day restocked "
                        + "it, which is the whole exploit with no item involved at all");

        // Backwards time — a world loaded from a backup — earns nothing
        // rather than everything.
        TraderRules.Trader later = new TraderRules.Trader("cleric", 1, 0, List.of(),
                1_000_000L, 0);
        check(TraderRules.restocksAllowed(later, 0L) == 0,
                "a clock that has gone backwards earns a villager "
                        + TraderRules.restocksAllowed(later, 0L) + " restocks");
        System.out.println("  the clock: a day is a day, a thousand askings inside one "
                + "are none, and a world restored from a backup owes nothing");
    }

    /**
     * The exploit, attempted rather than reasoned about.
     *
     * <p>A player with one villager and two hands, picking it up and putting
     * it down as fast as the game allows, for a hundred thousand ticks.
     */
    private static void theExploit() {
        Random random = new Random(1_000_003L);
        for (int attempt = 0; attempt < 200; attempt++) {
            TraderRules.Trader who = someone(random);
            who = new TraderRules.Trader(who.profession(), who.level(), who.experience(),
                    who.offers(), 0L, 0);
            long span = 40_000L + random.nextInt(200_000);
            int restocks = 0;
            long now = 0L;
            while (now < span) {
                // Pick it up, put it down, ask for a restock. As often as a
                // player could conceivably manage it.
                who = TraderRules.pocketed(who);
                now += 1L + random.nextInt(20);
                who = TraderRules.placed(who, now);
                if (TraderRules.mayRestock(who, now)) {
                    who = TraderRules.restocked(who, now);
                    restocks++;
                }
            }
            long earned = (span / TraderRules.A_DAY) * TraderRules.RESTOCKS_A_DAY;
            check(restocks <= earned + 1,
                    "a player carrying one villager about for " + span + " ticks got "
                            + restocks + " restocks out of it and time allowed "
                            + earned + "; that is a player with one villager, two hands "
                            + "and infinite emeralds");
        }
        System.out.println("  the exploit: 200 players carrying one villager about as "
                + "fast as the game allows, and not one of them beat the clock");
    }
}
