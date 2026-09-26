package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every number in {@link ComfortRules}, executed.
 *
 * The claim is that nothing a player owned is ever lost, and it is a counting
 * problem rather than a sentiment: a grave with fewer slots than the player
 * had stacks quietly eats the remainder, and nobody notices until it is their
 * evening in it.
 */
public final class ComfortHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theSize();
        theHeight();
        theLock();
        theCost();
        theConservation();
        theRolls();
        theResting();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a grave is built around what it is given "
                + "rather than given a size, it is never in the void or in bedrock, it "
                + "is yours until you have plainly given up on it, over a million "
                + "random inventories not one item went missing, and no two players "
                + "ever share a roll");
    }

    // ==================================================================

    private static void theSize() {
        check(ComfortRules.slotsFor(0) >= 1, "a grave with no slots at all");
        check(ComfortRules.slotsFor(41) == 41,
                "a vanilla inventory does not get a vanilla-sized grave");
        check(ComfortRules.MOST_SLOTS > 41 * 4,
                "the cap is " + ComfortRules.MOST_SLOTS + ", which is smaller than an "
                        + "inventory with backpacks and curios in it — and being wrong "
                        + "downwards costs somebody their evening");

        Random random = new Random(4_071_986L);
        for (int round = 0; round < 500_000; round++) {
            int stacks = random.nextInt(ComfortRules.MOST_SLOTS + 1);
            int slots = ComfortRules.slotsFor(stacks);
            check(slots >= Math.max(1, stacks),
                    stacks + " stacks got a grave with " + slots + " slots");
            check(ComfortRules.spilled(stacks) == 0,
                    stacks + " stacks spilled " + ComfortRules.spilled(stacks));
            check(slots <= ComfortRules.MOST_SLOTS, "a grave larger than the cap");
        }
        check(ComfortRules.spilled(-5) == 0, "a negative number of stacks spilled");
        System.out.println("  size: half a million inventories, every one of them fitting "
                + "exactly, and nothing ever spilled");
    }

    private static void theHeight() {
        Random random = new Random(1_609_344L);
        for (int round = 0; round < 500_000; round++) {
            int floor = -random.nextInt(128);
            int ceiling = floor + 1 + random.nextInt(512);
            int died = random.nextInt(4_000) - 2_000;
            int at = ComfortRules.graveHeight(died, floor, ceiling);
            check(at > floor || at == ceiling,
                    "a grave at " + at + " with a world floor of " + floor
                            + ", which is in the bedrock nobody can break");
            check(at <= ceiling, "a grave at " + at + " above a ceiling of " + ceiling);
            if (died > floor && died <= ceiling) {
                check(at == died, "a grave moved from " + died + " to " + at + " when "
                        + died + " was a perfectly good height");
                check(!ComfortRules.moved(died, floor, ceiling),
                        "a grave that did not move says it did");
            } else {
                check(ComfortRules.moved(died, floor, ceiling),
                        "a grave that had to move says it did not");
            }
        }
        // The one that matters: falling out of the world.
        check(ComfortRules.graveHeight(-500, -64, 320) > -64,
                "dying in the void leaves the grave in the void, which is no grave");
        check(ComfortRules.graveHeight(9_000, -64, 320) == 320,
                "dying above the world leaves the grave above it");
        check(ComfortRules.LOOK_WITHIN > 0 && ComfortRules.LOOK_WITHIN <= 8,
                "a grave may wander " + ComfortRules.LOOK_WITHIN + " blocks, which is "
                        + "either nowhere or out of sight");
        System.out.println("  height: half a million deaths, and not one grave in the "
                + "void, in bedrock or above the sky");
    }

    private static void theLock() {
        check(ComfortRules.mayOpen(true, false, 0L),
                "a player cannot open their own grave the moment it is made");
        check(!ComfortRules.mayOpen(false, false, 0L),
                "anybody can open a fresh grave, so dying beside somebody is losing "
                        + "everything to them");
        check(ComfortRules.mayOpen(false, true, 0L),
                "an operator cannot clear a grave, so one in a doorway is permanent");
        check(ComfortRules.mayOpen(false, false, ComfortRules.PRIVATE_FOR),
                "a grave is private for ever, so one in a shared base is an obstacle "
                        + "nobody can ever clear");
        check(!ComfortRules.mayOpen(false, false, ComfortRules.PRIVATE_FOR - 1),
                "a grave stops being private a tick early, which is a race somebody "
                        + "can win");
        check(ComfortRules.PRIVATE_FOR > 20 * 60 * 5,
                "a grave is private for under five minutes, which is not long enough to "
                        + "run back from a bed");
        check(ComfortRules.publicIn(0L) == ComfortRules.PRIVATE_FOR,
                "a fresh grave does not count down from the full time");
        check(ComfortRules.publicIn(ComfortRules.PRIVATE_FOR) == 0L,
                "an old grave is still counting down");
        check(ComfortRules.publicIn(ComfortRules.PRIVATE_FOR * 10L) == 0L,
                "a very old grave counts down to a negative number");
        for (long age = 0; age <= ComfortRules.PRIVATE_FOR; age += 37) {
            boolean open = ComfortRules.mayOpen(false, false, age);
            check(open == (ComfortRules.publicIn(age) == 0L),
                    "at " + age + " ticks the grave says it may be opened by anybody "
                            + "and the countdown disagrees");
        }
        System.out.println("  the lock: yours for " + (ComfortRules.PRIVATE_FOR / 20 / 60)
                + " minutes, and the countdown agrees with the lock at every tick");
    }

    private static void theCost() {
        check(ComfortRules.experienceKept(0) == 0, "dying with nothing kept something");
        check(ComfortRules.XP_KEPT < 1.0F,
                "a grave keeps all your experience, so dying costs nothing at all and "
                        + "the whole of the game's risk goes with it");
        check(ComfortRules.XP_KEPT > 0.25F,
                "a grave keeps almost none of your experience, which is a punishment "
                        + "rather than a mercy");
        int was = -1;
        for (int had = 0; had < 200_000; had++) {
            int kept = ComfortRules.experienceKept(had);
            check(kept <= had, "dying with " + had + " experience kept " + kept);
            check(kept >= was, "experience kept fell between " + (had - 1) + " and " + had);
            check(kept >= 0, "a negative amount of experience was kept");
            was = kept;
        }
        check(ComfortRules.experienceKept(-100) == 0,
                "a negative amount of experience kept something");
        System.out.println("  the cost: " + Math.round(ComfortRules.XP_KEPT * 100)
                + "% of your levels, never more than you had, and never falling as you "
                + "had more");
    }

    /**
     * Loot that belongs to whoever opened it.
     *
     * <p>Three properties, and every one of them is a way this goes wrong that
     * nobody would report as a bug: a reroll on reopening, two players sharing
     * a roll, or a record of every player who has ever opened a chest growing
     * for as long as the server runs.
     */
    private static void theRolls() {
        // Deterministic. A player who comes back gets what they left.
        Random random = new Random(90_210L);
        for (int round = 0; round < 200_000; round++) {
            long container = random.nextLong();
            long high = random.nextLong();
            long low = random.nextLong();
            long once = ComfortRules.seedFor(container, high, low);
            check(once == ComfortRules.seedFor(container, high, low),
                    "the same player got two different rolls for the same container, so "
                            + "closing and reopening is a reroll");
        }

        // Distinct. Two players at one chest, and one player at two chests.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        long container = 0x5EED5EEDL;
        for (int player = 0; player < 300_000; player++) {
            seen.add(ComfortRules.seedFor(container, player, ~player));
        }
        check(seen.size() == 300_000,
                "three hundred thousand players at one chest produced only " + seen.size()
                        + " different rolls, so some of them are sharing loot");
        seen.clear();
        for (int chest = 0; chest < 300_000; chest++) {
            seen.add(ComfortRules.seedFor(chest, 12L, 34L));
        }
        check(seen.size() == 300_000,
                "one player at three hundred thousand chests got only " + seen.size()
                        + " different rolls");

        // Avalanche. Two players whose ids differ by one bit must not get two
        // rolls that differ by one bit, or a server that hands out sequential
        // ids hands out nearly identical loot.
        long flipped = 0L;
        int samples = 0;
        for (int bit = 0; bit < 64; bit++) {
            for (int trial = 0; trial < 500; trial++) {
                long high = random.nextLong();
                long low = random.nextLong();
                long before = ComfortRules.seedFor(container, high, low);
                long after = ComfortRules.seedFor(container, high, low ^ (1L << bit));
                flipped += Long.bitCount(before ^ after);
                samples++;
            }
        }
        double average = (double) flipped / samples;
        check(average > 28.0 && average < 36.0,
                "flipping one bit of a player's id changes " + average + " bits of "
                        + "their roll on average; it has to be about half of 64, or two "
                        + "players who differ by one bit get two rolls that differ by "
                        + "almost nothing");

        // Nothing is remembered about a player who took everything.
        check(ComfortRules.forgettable(true),
                "an emptied copy of a container is kept, so the table grows for as long "
                        + "as the server runs and is never safe to clear");
        check(!ComfortRules.forgettable(false),
                "a copy with something still in it is forgotten, so a player who comes "
                        + "back finds their loot gone");
        System.out.println("  rolls: 200,000 repeats all identical, 600,000 rolls all "
                + "different, and one bit of a player id moves " + Math.round(average)
                + " bits of their seed");
    }

    /**
     * The one the module exists for.
     *
     * <p>Build a random inventory, put it in a grave, take it out again, and
     * count. Every stack, in order, with the same number in it.
     */
    private static void theConservation() {
        Random random = new Random(31_337L);
        long stacksSeen = 0L;
        long itemsSeen = 0L;
        for (int round = 0; round < 40_000; round++) {
            List<ComfortRules.Holding> carried = new ArrayList<>();
            int stacks = random.nextInt(ComfortRules.MOST_SLOTS + 1);
            for (int at = 0; at < stacks; at++) {
                carried.add(new ComfortRules.Holding("item" + random.nextInt(400),
                        1 + random.nextInt(64)));
            }
            List<ComfortRules.Holding> grave = ComfortRules.intoGrave(carried);
            check(grave.size() == carried.size(),
                    "a player carrying " + carried.size() + " stacks was buried with "
                            + grave.size());
            check(ComfortRules.itemsIn(grave) == ComfortRules.itemsIn(carried),
                    "a grave holds " + ComfortRules.itemsIn(grave) + " items and the "
                            + "player was carrying " + ComfortRules.itemsIn(carried));
            for (int at = 0; at < carried.size(); at++) {
                check(grave.get(at).equals(carried.get(at)),
                        "slot " + at + " came back as " + grave.get(at)
                                + " and went in as " + carried.get(at));
            }
            check(grave.size() <= ComfortRules.slotsFor(carried.size()),
                    "a grave holds more than it has slots for");
            stacksSeen += grave.size();
            itemsSeen += ComfortRules.itemsIn(grave);
        }
        check(stacksSeen > 1_000_000L,
                "only " + stacksSeen + " stacks were buried, which is not enough to "
                        + "have met anything unusual");
        System.out.println("  conservation: " + stacksSeen + " stacks and " + itemsSeen
                + " items buried and recovered, and not one of them changed on the way");
    }

    // ==================================================================

    /**
     * Somewhere to sleep that is not a bed.
     *
     * <p>The claim is that the two kinds divide the clock between them with no
     * gap and no overlap, and that you always wake on the other side of the
     * line. A gap is an hour when neither works and nothing says why; an
     * overlap is a pair of items that between them delete the day.
     */
    private static void theResting() {
        List<String> faults = RestRules.faults();
        check(faults.isEmpty(), "something wrong with the resting: " + faults);

        // Every tick of the day, which is the only way to know there is no gap.
        int bags = 0;
        int hammocks = 0;
        for (int tick = 0; tick < RestRules.DAY; tick++) {
            String kind = RestRules.usableAt(tick);
            check(RestRules.KINDS.contains(kind),
                    "at tick " + tick + " you may rest in " + kind + ", which is "
                            + "not a thing this module makes");
            if (RestRules.BAG.equals(kind)) {
                bags++;
            } else {
                hammocks++;
            }
            // And the one that works is the one the dark says it should be.
            check(RestRules.BAG.equals(kind) == RestRules.isNight(tick),
                    "at tick " + tick + " the kind that works and whether it is "
                            + "dark disagree, so a player is told to use a "
                            + "hammock in the pitch dark");
        }
        check(bags + hammocks == RestRules.DAY,
                "the two kinds cover " + (bags + hammocks) + " of "
                        + RestRules.DAY + " ticks");
        check(bags > 0 && hammocks > 0,
                "one of the two kinds is usable at no hour at all, so sixteen "
                        + "blocks are decoration");

        // Wherever you lie down, you wake where the other one is the answer,
        // and you never skip backwards.
        for (String kind : RestRules.KINDS) {
            for (int tick = 0; tick < RestRules.DAY; tick++) {
                if (!RestRules.usableAt(tick).equals(kind)) {
                    continue;
                }
                int skip = RestRules.skipsFrom(tick, kind);
                check(skip > 0 && skip < RestRules.DAY,
                        "resting in a " + kind + " at tick " + tick + " skips "
                                + skip + " ticks, which is either nothing at all "
                                + "or more than a day");
                int woke = (tick + skip) % RestRules.DAY;
                check(woke == RestRules.wakesAt(kind),
                        "resting in a " + kind + " at tick " + tick + " lands at "
                                + woke + " and it is meant to land at "
                                + RestRules.wakesAt(kind));
                check(!RestRules.usableAt(woke).equals(kind),
                        "resting in a " + kind + " at tick " + tick + " wakes you "
                                + "somewhere you can lie straight back down in "
                                + "it, which is a button that skips time");
            }
        }

        // Controls, built to be caught.
        check(!RestRules.faultsInColours(java.util.List.of("white")).isEmpty(),
                "a roster of one colour is not reported, so fifteen of the "
                        + "game's dyes could have nothing to make with them");
        check(!RestRules.faultsInColours(java.util.List.of(
                        "white", "white", "orange", "magenta", "light_blue",
                        "yellow", "lime", "pink", "gray", "light_gray", "cyan",
                        "purple", "blue", "brown", "green", "red")).isEmpty(),
                "a colour listed twice is not reported, so one of the sixteen "
                        + "would quietly not exist");
        check(RestRules.faultsInColours(RestRules.COLOURS).isEmpty(),
                "the colours this module ships are reported as faulty");

        check(!RestRules.faultsInCount(longer(RestRules.roster())).isEmpty(),
                "a roster one block too long is not reported");
        check(!RestRules.faultsInDuplicates(twice(RestRules.roster())).isEmpty(),
                "a roster with a block in it twice is not reported");
        check(!RestRules.faultsInMissing(java.util.List.of()).isEmpty(),
                "a roster with nothing in it is not reported, so every colour "
                        + "could quietly have nothing to craft");
        check(RestRules.faultsInCount(RestRules.roster()).isEmpty()
                        && RestRules.faultsInDuplicates(RestRules.roster()).isEmpty()
                        && RestRules.faultsInMissing(RestRules.roster()).isEmpty(),
                "the roster this module ships is reported as faulty");

        check(!RestRules.faultsInClock(RestRules.DUSK).isEmpty(),
                "half a day, in which a sleeping bag is usable at no hour at "
                        + "all, is not reported — so sixteen blocks could be "
                        + "decoration and nothing would say so");
        check(!RestRules.faultsInMissing(swapped(RestRules.roster())).isEmpty(),
                "a roster of the right size with the wrong thing in it is not "
                        + "reported, so one colour could quietly not exist");
        check(RestRules.faultsInClock(RestRules.DAY).isEmpty(),
                "the clock this module ships is reported as broken");

        check(!RestRules.faultsInWaking(java.util.List.of("wibble")).isEmpty(),
                "waking from something that is not a kind of rest is not "
                        + "reported");
        check(RestRules.faultsInWaking(RestRules.KINDS).isEmpty(),
                "the waking this module ships is reported as broken");

        check(!RestRules.faultsInRefusals(java.util.List.of("a", "b")).isEmpty(),
                "a module with two ways to say no and six ways to fail is not "
                        + "reported");
        check(!RestRules.faultsInRefusals(java.util.List.of(
                        "a", "b", "c", "d", "d")).isEmpty(),
                "two reasons wearing one sentence are not reported");
        check(!RestRules.faultsInRefusals(java.util.List.of(
                        "a", "b", "c", "")).isEmpty(),
                "a refusal with no words at all is not reported");
        check(RestRules.faultsInRefusals(RestRules.REFUSALS).isEmpty(),
                "the refusals this module ships are reported as faulty");

        System.out.println("  somewhere to sleep: " + RestRules.roster().size()
                + " block(s), " + RestRules.DAY + " ticks of the day swept, "
                + bags + " of them a sleeping bag's and " + hammocks + " a "
                + "hammock's, no gap between them and no hour that is both, and "
                + "every rest waking you where the other one is the answer");
    }

    /** A roster one longer than it should be, for a control. */
    private static List<String> longer(List<String> one) {
        List<String> made = new ArrayList<>(one);
        made.add("wibble");
        return made;
    }

    /** A roster of the right size with one thing in it wrong, for a control. */
    private static List<String> swapped(List<String> one) {
        List<String> made = new ArrayList<>(one);
        made.set(0, "wibble");
        return made;
    }

    /** A list with everything in it twice, for a control. */
    private static List<String> twice(List<String> one) {
        List<String> made = new ArrayList<>(one);
        made.addAll(one);
        return made;
    }
}
