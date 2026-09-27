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

    /**
     * Whether that threw.
     *
     * <p>A compact constructor that refuses nonsense is a check like any other,
     * and a check nothing exercises is a check that can be deleted by accident
     * and never missed.
     */
    private static boolean threwOn(Runnable what) {
        try {
            what.run();
            return false;
        } catch (RuntimeException expected) {
            return true;
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
        theChimes();
        theStations();
        thePost();
        theLighting();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a grave is built around what it is given "
                + "rather than given a size, it is never in the void or in bedrock, it "
                + "is yours until you have plainly given up on it, over a million "
                + "random inventories not one item went missing, no two players "
                + "ever share a roll, and a room can be furnished without being lit");
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

    // ==================================================================

    /**
     * Something that makes a sound when the wind moves it.
     *
     * <p>The claim is that a room full of them is no louder than one of them.
     * Every ambient block that has ever had to be muted failed it the same
     * way: each rolled its own dice and nothing knew how many were rolling.
     */
    private static void theChimes() {
        List<String> faults = ChimeRules.faults();
        check(faults.isEmpty(), "something wrong with the chimes: " + faults);

        // However many there are, and whatever the wind, the room is never
        // louder than the ceiling and never silent when the wind is up.
        int swept = 0;
        for (int many = 1; many <= 256; many++) {
            for (int step = 0; step <= 20; step++) {
                double wind = step / 20.0D;
                double loud = ChimeRules.soundsASecond(many, wind);
                swept++;
                check(loud <= ChimeRules.MOST_A_SECOND + 1.0E-9D,
                        many + " chimes in a wind of " + wind + " come to "
                                + loud + " sounds a second and the ceiling is "
                                + ChimeRules.MOST_A_SECOND);
                check(loud >= -1.0E-9D,
                        many + " chimes come to " + loud + " sounds a second");
                if (wind > 0.0D) {
                    check(loud > 0.0D,
                            many + " chimes in a wind of " + wind + " make no "
                                    + "sound at all");
                } else {
                    check(loud == 0.0D,
                            many + " chimes in a still world make " + loud
                                    + " sounds a second");
                }
                int waits = ChimeRules.waitOf(many, wind);
                check(waits >= 1 && waits <= ChimeRules.LONGEST_WAIT,
                        "a wait of " + waits + " ticks is not a wait");
                double chance = ChimeRules.chanceOnWaking(many, wind);
                check(chance >= 0.0D && chance <= 1.0D,
                        "a chance of " + chance + " on waking is not a chance");
            }
        }

        // One more chime never makes one chime sound more often.
        for (int many = 1; many < 256; many++) {
            check(ChimeRules.waitOf(many + 1, 1.0D)
                            >= ChimeRules.waitOf(many, 1.0D),
                    "hanging another chime made every chime already there ring "
                            + "more often, which is the wall of noise this rule "
                            + "exists to stop");
        }

        // And a still day is a long wait rather than a busy one.
        check(ChimeRules.waitOf(1, 0.0D) == ChimeRules.LONGEST_WAIT,
                "a chime in no wind at all is woken as often as one in a gale");

        // The wind is something to build around rather than a switch.
        double last = -1.0D;
        for (int height = ChimeRules.NO_WIND; height <= ChimeRules.FULL_WIND;
                height++) {
            double wind = ChimeRules.windAt(height, true);
            check(wind >= last, "the wind falls off as you climb");
            last = wind;
            check(ChimeRules.windAt(height, false) == 0.0D,
                    "a chime under a roof at " + height + " hears the wind, and "
                            + "the rule that should have said so did not");
        }

        // Controls, built to be caught.
        check(!ChimeRules.faultsInMaterials(java.util.List.of("oak"),
                        ChimeRules.MADE_OF).isEmpty(),
                "a single note with nothing to be a chord with is not reported");
        check(!ChimeRules.faultsInMaterials(java.util.List.of("oak", "oak"),
                        ChimeRules.MADE_OF).isEmpty(),
                "a material listed twice is not reported");
        check(!ChimeRules.faultsInMaterials(ChimeRules.MATERIALS,
                        java.util.Map.of()).isEmpty(),
                "a chime made of nothing at all is not reported, so ninety-six "
                        + "blocks could have no recipe that can fire");
        check(ChimeRules.faultsInMaterials(ChimeRules.MATERIALS,
                        ChimeRules.MADE_OF).isEmpty(),
                "the materials this module ships are reported as faulty");

        check(!ChimeRules.faultsInNotes(java.util.List.of("wibble", "wobble"))
                        .isEmpty(),
                "two materials landing on the same note are not reported");
        check(ChimeRules.faultsInNotes(ChimeRules.MATERIALS).isEmpty(),
                "the notes this module ships are reported as the same note");

        check(!ChimeRules.faultsInBand(ChimeRules.MATERIALS,
                        ChimeRules.HIGHEST, ChimeRules.HIGHEST).isEmpty(),
                "a note under its own floor is not reported");
        check(!ChimeRules.faultsInBand(ChimeRules.MATERIALS,
                        ChimeRules.LOWEST, ChimeRules.LOWEST).isEmpty(),
                "a note over its own ceiling is not reported");
        check(ChimeRules.faultsInBand(ChimeRules.MATERIALS, ChimeRules.LOWEST,
                        ChimeRules.HIGHEST).isEmpty(),
                "the notes this module ships are reported as outside their band");

        check(!ChimeRules.faultsInWindRange(ChimeRules.FULL_WIND,
                        ChimeRules.NO_WIND).isEmpty(),
                "a world where the wind is strongest at the bottom is not "
                        + "reported");
        check(ChimeRules.faultsInWindRange(ChimeRules.NO_WIND,
                        ChimeRules.FULL_WIND).isEmpty(),
                "the heights this module ships are reported as upside down");

        check(!ChimeRules.faultsInSlope(ChimeRules.NO_WIND,
                        ChimeRules.NO_WIND + 1).isEmpty(),
                "a wind that goes from nothing to everything with no middle is "
                        + "not reported, so height would be a switch");
        check(ChimeRules.faultsInSlope(ChimeRules.NO_WIND,
                        ChimeRules.FULL_WIND).isEmpty(),
                "the slope this module ships is reported as a switch");

        check(ChimeRules.faultsInRoof(ChimeRules.NO_WIND,
                        ChimeRules.FULL_WIND).isEmpty(),
                "a roof over a chime this module ships is reported as no help");

        check(!ChimeRules.faultsInCeiling(0.0D).isEmpty(),
                "a ceiling of nothing, under which no chime ever sounds, is "
                        + "not reported");
        check(ChimeRules.faultsInCeiling(ChimeRules.MOST_A_SECOND).isEmpty(),
                "the ceiling this module ships is reported as no ceiling");

        check(!ChimeRules.faultsInLoudness(ChimeRules.MOST_A_SECOND / 2.0D)
                        .isEmpty(),
                "a room louder than its own ceiling is not reported, which is "
                        + "the whole of what this file is for");
        check(ChimeRules.faultsInLoudness(ChimeRules.MOST_A_SECOND).isEmpty(),
                "the loudness this module ships is reported as over its ceiling");

        check(!ChimeRules.faultsInWaking(0).isEmpty(),
                "a chime woken every nought ticks is not reported");
        check(!ChimeRules.faultsInWaking(20 * 60 * 60).isEmpty(),
                "a chime that goes an hour between looking at the sky is not "
                        + "reported, so a roof built over one would seem not to "
                        + "work");
        check(ChimeRules.faultsInWaking(ChimeRules.LONGEST_WAIT).isEmpty(),
                "the waking this module ships is reported as too rare");

        check(!ChimeRules.faultsInRoster(java.util.List.of("white_oak_chimes"))
                        .isEmpty(),
                "a roster with one chime in it is not reported");
        check(ChimeRules.faultsInRoster(ChimeRules.roster()).isEmpty(),
                "the roster this module ships is reported as faulty");

        System.out.println("  the chimes: " + ChimeRules.roster().size()
                + " chime(s) over " + ChimeRules.MATERIALS.size()
                + " note(s), " + swept + " rooms swept from one chime to two "
                + "hundred and fifty-six at every strength of wind, and not one "
                + "of them louder than " + ChimeRules.MOST_A_SECOND
                + " sounds a second");
    }

    // ==================================================================

    /**
     * Eight blocks that each do one thing a player would otherwise do.
     *
     * <p>The claim is that not one of them is better value than a hand, and
     * not one of them is quicker. What a station buys is attention, and a
     * station that bought throughput as well would be a reason never to stand
     * at a villager again.
     */
    private static void theStations() {
        List<String> faults = StationRules.faults();
        check(faults.isEmpty(), "something wrong with the stations: " + faults);

        // Controls, built to be caught.
        StationRules.Station first = StationRules.STATIONS.get(0);

        check(!StationRules.faultsInJobs(java.util.List.of(
                        new StationRules.Station("control", "wibble", 1, 1, 100,
                                true)), StationRules.JOBS).isEmpty(),
                "a station that does something nothing knows how to do is not "
                        + "reported");
        check(StationRules.faultsInJobs(StationRules.STATIONS,
                        StationRules.JOBS).isEmpty(),
                "the jobs this module ships are reported as unknown");

        check(!StationRules.faultsInOneEach(java.util.List.of(first,
                        new StationRules.Station("control", first.job(), 1, 1,
                                first.ticks(), true))).isEmpty(),
                "two stations doing one job are not reported");
        check(StationRules.faultsInOneEach(StationRules.STATIONS).isEmpty(),
                "the stations this module ships are reported as doubling up");

        check(!StationRules.faultsInNames(java.util.List.of(first,
                        new StationRules.Station(first.id(),
                                StationRules.LOOK, 0, 0, 20, false))).isEmpty(),
                "two stations written under one name are not reported");
        check(StationRules.faultsInNames(StationRules.STATIONS).isEmpty(),
                "the names this module ships are reported as doubled");

        check(!StationRules.faultsInGaps(java.util.List.of(),
                        StationRules.JOBS).isEmpty(),
                "a job nothing does is not reported, so a block could be named "
                        + "in the rules and never exist");
        check(StationRules.faultsInGaps(StationRules.STATIONS,
                        StationRules.JOBS).isEmpty(),
                "the stations this module ships leave a job undone");

        check(!StationRules.faultsInValue(java.util.List.of(
                        new StationRules.Station("control", StationRules.TRADE,
                                1, 2, 100, true)), StationRules.BY_HAND).isEmpty(),
                "a station that hands back twice what a player gets is not "
                        + "reported, which is a multiplier on everything");
        check(!StationRules.faultsInValue(StationRules.STATIONS,
                        java.util.Map.of()).isEmpty(),
                "a station measured against nothing at all is not reported");
        check(StationRules.faultsInValue(StationRules.STATIONS,
                        StationRules.BY_HAND).isEmpty(),
                "the value this module ships is reported as a multiplier");

        check(!StationRules.faultsInPace(java.util.List.of(
                        new StationRules.Station("control", StationRules.CURE,
                                1, 1, 1, true)), StationRules.BY_HAND_TICKS)
                        .isEmpty(),
                "a station quicker than the player it replaces is not reported");
        check(!StationRules.faultsInPace(StationRules.STATIONS,
                        java.util.Map.of()).isEmpty(),
                "a station with nothing to be slower than is not reported");
        check(StationRules.faultsInPace(StationRules.STATIONS,
                        StationRules.BY_HAND_TICKS).isEmpty(),
                "the pace this module ships is reported as too quick");

        check(!StationRules.faultsInHolding(java.util.List.of(
                        new StationRules.Station("control", StationRules.LOOK,
                                0, 0, 20, false))).isEmpty(),
                "a set of stations not one of which holds a villager is not "
                        + "reported");
        check(!StationRules.faultsInHolding(java.util.List.of(
                        new StationRules.Station("control", StationRules.LOOK,
                                0, 0, 20, true))).isEmpty(),
                "a set of stations every one of which swallows a villager is "
                        + "not reported, so looking at one would cost you it");
        check(StationRules.faultsInHolding(StationRules.STATIONS).isEmpty(),
                "the stations this module ships are reported as swallowing "
                        + "villagers");

        // Over a hundred thousand rosters nobody wrote, the two rules say the
        // same thing they say about this one.
        Random random = new Random(3_317_745L);
        int swept = 0;
        for (int round = 0; round < 100_000; round++) {
            String job = StationRules.JOBS.get(
                    random.nextInt(StationRules.JOBS.size()));
            int takes = 1 + random.nextInt(20);
            int gives = random.nextInt(20);
            int ticks = 1 + random.nextInt(6_000);
            StationRules.Station made = new StationRules.Station(
                    "s" + round, job, takes, gives, ticks, true);
            swept++;
            boolean tooGood = made.perInput()
                    > StationRules.BY_HAND.get(job) + 1.0E-9D;
            check(tooGood == !StationRules.faultsInValue(java.util.List.of(made),
                            StationRules.BY_HAND).isEmpty(),
                    "a station worth " + made.perInput() + " against a hand's "
                            + StationRules.BY_HAND.get(job) + " is reported "
                            + "wrongly");
            boolean tooQuick = ticks < StationRules.BY_HAND_TICKS.get(job);
            check(tooQuick == !StationRules.faultsInPace(java.util.List.of(made),
                            StationRules.BY_HAND_TICKS).isEmpty(),
                    "a station taking " + ticks + " ticks against a hand's "
                            + StationRules.BY_HAND_TICKS.get(job)
                            + " is reported wrongly");
        }

        System.out.println("  the stations: " + StationRules.STATIONS.size()
                + " block(s) over " + StationRules.JOBS.size() + " job(s), "
                + swept + " stations nobody wrote swept against what a hand "
                + "does with the same input, and not one of this module's own "
                + "either better value than a player or quicker than one");
    }
    private static void thePost() {
        List<String> faults = PostRules.faults();
        check(faults.isEmpty(), "something wrong with the post: " + faults);

        // ----------------------------------------------------------------
        // Controls first and the sweep after, because a quarter of a million
        // failing rounds fill the failure report before a control gets to say
        // anything.
        // ----------------------------------------------------------------

        PostRules.Mail letter = PostRules.mail(PostRules.LETTER);
        PostRules.Mail parcel = PostRules.mail(PostRules.PACKAGE);

        check(!PostRules.faultsInKinds(List.of(new PostRules.Mail("control", 1, 1,
                        false, false, true)), PostRules.KINDS).isEmpty(),
                "mail of a kind nothing else names is not reported");
        check(PostRules.faultsInKinds(PostRules.MAIL, PostRules.KINDS).isEmpty(),
                "the kinds this module ships are reported as unknown");

        check(!PostRules.faultsInNames(List.of(letter, letter)).isEmpty(),
                "two pieces of mail written under one name are not reported");
        check(PostRules.faultsInNames(PostRules.MAIL).isEmpty(),
                "the names this module ships are reported as doubled");

        check(!PostRules.faultsInGaps(List.of(), PostRules.KINDS).isEmpty(),
                "a kind of mail nothing makes is not reported, so a kind could "
                        + "be named in the rules and never exist");
        check(PostRules.faultsInGaps(PostRules.MAIL, PostRules.KINDS).isEmpty(),
                "the mail this module ships leaves a kind unmade");

        check(!PostRules.faultsInPostage(List.of(parcel,
                        new PostRules.Mail("control", parcel.slots() + 1,
                                parcel.postage() - 1, parcel.sealed(),
                                parcel.pays(), true))).isEmpty(),
                "a parcel with more room for less paper than another is not "
                        + "reported, so one of the two is an item nobody sends");
        check(PostRules.faultsInPostage(PostRules.MAIL).isEmpty(),
                "the postage this module ships has something dominated");

        check(!PostRules.faultsInPosted(List.of(new PostRules.Mail("control", 1,
                        0, false, false, true))).isEmpty(),
                "free postage on something that is posted is not reported");
        check(!PostRules.faultsInPosted(List.of(new PostRules.Mail("control", 1,
                        3, false, false, false))).isEmpty(),
                "a charge on something that is never posted is not reported");
        check(PostRules.faultsInPosted(PostRules.MAIL).isEmpty(),
                "the postage this module ships is reported as free or as a "
                        + "charge for nothing");

        check(!PostRules.faultsInSeals(List.of(
                        new PostRules.Mail("open", 9, 4, false, false, true),
                        new PostRules.Mail("shut", 9, 4, true, false, true)))
                        .isEmpty(),
                "a seal that costs no more than no seal is not reported");
        check(PostRules.faultsInSeals(PostRules.MAIL).isEmpty(),
                "the seals this module ships are reported as free");

        check(!PostRules.faultsInFloor(600, 6000, 5000, 0, 1.0D).isEmpty(),
                "a post with no handling at either end, which arrives the "
                        + "instant it is sent, is not reported");
        check(!PostRules.faultsInFloor(0, 6000, 5000, 600, 1.0D).isEmpty(),
                "a floor of nought ticks is not reported");
        check(PostRules.faultsInFloor(PostRules.SOONEST, PostRules.LATEST,
                        PostRules.FURTHEST, PostRules.HANDLING,
                        PostRules.BLOCKS_A_TICK).isEmpty(),
                "the post this module ships is reported as arriving instantly");

        check(!PostRules.faultsInBand(600, 600).isEmpty(),
                "a band with no width in it, where every parcel takes the same "
                        + "time whatever the distance, is not reported");
        check(PostRules.faultsInBand(PostRules.SOONEST, PostRules.LATEST).isEmpty(),
                "the band this module ships is reported as having no width");

        check(!PostRules.faultsInCeiling(600, 6000, 9000, 600, 1.0D).isEmpty(),
                "a journey that runs past the longest wait the post promises is "
                        + "not reported, so somebody far out is cut off");
        check(PostRules.faultsInCeiling(PostRules.SOONEST, PostRules.LATEST,
                        PostRules.FURTHEST, PostRules.HANDLING,
                        PostRules.BLOCKS_A_TICK).isEmpty(),
                "the post this module ships is reported as taking too long");

        check(PostRules.faultsInClimb(PostRules.FURTHEST, PostRules.HANDLING,
                        PostRules.BLOCKS_A_TICK).isEmpty(),
                "the post this module ships is reported as quicker the further "
                        + "it goes");
        check(PostRules.faultsInCap(PostRules.FURTHEST, PostRules.HANDLING,
                        PostRules.BLOCKS_A_TICK).isEmpty(),
                "the post this module ships is reported as never stopping");

        check(!PostRules.faultsInServices(List.of(
                        new PostRules.Service("a", 100, false),
                        new PostRules.Service("b", 100, false)),
                        PostRules.FURTHEST).isEmpty(),
                "two services the same distance off, which cost the same and "
                        + "take the same time, are not reported");
        check(!PostRules.faultsInServices(List.of(
                        new PostRules.Service("a", PostRules.FURTHEST + 1, false)),
                        PostRules.FURTHEST).isEmpty(),
                "a service past where the journey stops growing is not reported");
        check(PostRules.faultsInServices(PostRules.SERVICES, PostRules.FURTHEST)
                        .isEmpty(),
                "the services this module ships are reported as doubled up");

        check(PostRules.faultsInAddresses(PostRules.LONGEST_ADDRESS).isEmpty(),
                "the addresses this module allows are reported as wrong");

        check(!PostRules.faultsInWaits(List.of(180, 30, 4320)).isEmpty(),
                "payback windows out of order are not reported");
        check(!PostRules.faultsInWaits(List.of(30, 30)).isEmpty(),
                "two payback windows of the same length are not reported");
        check(PostRules.faultsInWaits(PostRules.WAITS).isEmpty(),
                "the payback windows this module ships are reported as out of "
                        + "order");

        check(!PostRules.faultsInPayback(List.of(1), PostRules.LATEST).isEmpty(),
                "a payback window shorter than the longest delivery is not "
                        + "reported, so the post could ship a swindle");
        check(PostRules.faultsInPayback(PostRules.WAITS, PostRules.LATEST).isEmpty(),
                "the payback windows this module ships are reported as shorter "
                        + "than a delivery");

        check(!PostRules.faultsInLoss(0, 100, 0.25D).isEmpty(),
                "a post that never loses anything is not reported");
        check(!PostRules.faultsInLoss(101, 100, 0.25D).isEmpty(),
                "a post that loses more than it allows is not reported");
        check(!PostRules.faultsInLoss(15, 100, 1.0D).isEmpty(),
                "lost mail worth as much as what was posted is not reported, so "
                        + "posting to nowhere would be a way to play");
        check(!PostRules.faultsInLoss(15, 100, -0.5D).isEmpty(),
                "lost mail worth less than nothing is not reported");
        check(PostRules.faultsInLoss(PostRules.LOSS_IN_A_THOUSAND,
                        PostRules.MOST_LOSS_IN_A_THOUSAND,
                        PostRules.LOST_MAIL_WORTH).isEmpty(),
                "the losses this module ships are reported as wrong");

        check(!PostRules.faultsInHoleGaps(List.of(), PostRules.WOODS).isEmpty(),
                "a wood with no pigeonhole in it is not reported");
        check(!PostRules.faultsInHoleWoods(List.of("granite_pigeonhole"),
                        PostRules.WOODS).isEmpty(),
                "a pigeonhole in a wood the pack does not have is not reported");
        check(!PostRules.faultsInHoleNames(List.of("oak_pigeonhole",
                        "oak_pigeonhole")).isEmpty(),
                "a pigeonhole made twice is not reported");
        check(PostRules.faultsInHoleGaps(PostRules.pigeonholes(),
                        PostRules.WOODS).isEmpty(),
                "the pigeonholes this module ships are reported as missing a wood");
        check(PostRules.faultsInHoleWoods(PostRules.pigeonholes(),
                        PostRules.WOODS).isEmpty(),
                "the pigeonholes this module ships are reported as built from "
                        + "woods the pack has not got");
        check(PostRules.faultsInHoleNames(PostRules.pigeonholes()).isEmpty(),
                "the pigeonholes this module ships are reported as doubled");

        check(!PostRules.faultsInFouling(0).isEmpty(),
                "a pigeonhole that never gets dirty is not reported");
        check(PostRules.faultsInFouling(PostRules.FOULEST).isEmpty(),
                "the pigeonholes this module ships are reported as never getting "
                        + "dirty");
        check(!PostRules.faultsInWaste(List.of(0, 2, 2, 2)).isEmpty(),
                "a pigeonhole worth no more at level two than at level one is "
                        + "not reported, so there is no reason to keep a bird");
        check(!PostRules.faultsInWaste(List.of(0, 4, 2, 6)).isEmpty(),
                "a pigeonhole that is worth less for being dirtier is not "
                        + "reported");
        check(!PostRules.faultsInWaste(List.of(1, 2, 4, 6)).isEmpty(),
                "a clean pigeonhole that pays anyway is not reported, so a "
                        + "player is paid for a bird that has not been there");
        check(!PostRules.faultsInWaste(List.of()).isEmpty(),
                "a pigeonhole with no ladder at all is not reported");
        check(PostRules.faultsInWaste(PostRules.FOULEST).isEmpty(),
                "the waste this module ships is reported as flat");
        check(PostRules.wasteLadder(PostRules.FOULEST).size()
                        == PostRules.FOULEST + 1,
                "the ladder has " + PostRules.wasteLadder(PostRules.FOULEST).size()
                        + " rung(s) where the post says a pigeonhole has "
                        + (PostRules.FOULEST + 1));

        check(!PostRules.faultsInTop(3, 6, 16).isEmpty(),
                "a pigeonhole that goes on paying past its dirtiest level is "
                        + "not reported, so the top level is not the top");
        check(PostRules.faultsInTop(PostRules.FOULEST).isEmpty(),
                "the top level of dirt this module ships is reported as not the "
                        + "top");

        check(!PostRules.faultsInBirds(List.of(
                        new PostRules.Bird("a", PostRules.ABOVE, null, 0, 1),
                        new PostRules.Bird("b", PostRules.ABOVE, null, 0, 1)))
                        .isEmpty(),
                "two birds that belong in one dimension are not reported");
        check(!PostRules.faultsInBirds(List.of(
                        new PostRules.Bird("a", PostRules.ABOVE, null, 0, 1),
                        new PostRules.Bird("a", PostRules.BELOW, null, 0, 1)))
                        .isEmpty(),
                "two birds written under one name are not reported");
        check(PostRules.faultsInBirds(PostRules.BIRDS).isEmpty(),
                "the birds this module ships are reported as doubled");

        check(!PostRules.faultsInNowhere(List.of(
                        new PostRules.Bird("a", PostRules.ABOVE, null, 0, 1)))
                        .isEmpty(),
                "a bird that turns into nothing wherever it is carried is not "
                        + "reported");
        check(PostRules.faultsInNowhere(PostRules.BIRDS).isEmpty(),
                "the birds this module ships are reported as turning into "
                        + "nothing");
        check(!PostRules.faultsInUnknown(List.of(
                        new PostRules.Bird("a", PostRules.ABOVE, "nobody", 300, 1)))
                        .isEmpty(),
                "a bird that turns into something this module does not have is "
                        + "not reported, so carrying one below would lose it");
        check(PostRules.faultsInUnknown(PostRules.BIRDS).isEmpty(),
                "the birds this module ships are reported as turning into "
                        + "something it has not got");
        check(!PostRules.faultsInLoop(List.of(
                        new PostRules.Bird("a", PostRules.ABOVE, "b", 300, 1),
                        new PostRules.Bird("b", PostRules.BELOW, "c", 300, 1),
                        new PostRules.Bird("c", "end", "b", 300, 1))).isEmpty(),
                "a one-way change, where a bird carried below never comes back, "
                        + "is not reported");
        check(PostRules.faultsInLoop(PostRules.BIRDS).isEmpty(),
                "the birds this module ships are reported as one-way");

        check(!PostRules.faultsInSymbolNames(List.of("a", "a")).isEmpty(),
                "a symbol cut twice is not reported");
        check(PostRules.faultsInSymbolNames(PostRules.symbols()).isEmpty(),
                "the symbols this module ships are reported as doubled");
        check(!PostRules.faultsInAlphabet(List.of("a")).isEmpty(),
                "an alphabet with letters missing from it is not reported");
        check(PostRules.faultsInAlphabet(PostRules.symbols()).isEmpty(),
                "the alphabet this module ships is reported as incomplete");
        check(!PostRules.faultsInDigits(List.of("0")).isEmpty(),
                "a set of figures with numbers missing from it is not reported");
        check(PostRules.faultsInDigits(PostRules.symbols()).isEmpty(),
                "the figures this module ships are reported as incomplete");

        check(!PostRules.faultsInRosterNames(List.of("red_wax_a", "red_wax_a"))
                        .isEmpty(),
                "a seal in the roster twice is not reported");
        check(PostRules.faultsInRosterNames(PostRules.seals()).isEmpty(),
                "the seals this module ships are reported as doubled");
        check(!PostRules.faultsInRosterCuts(List.of("red_wax_a"),
                        PostRules.SEAL_MATERIALS, PostRules.symbols()).isEmpty(),
                "a roster with a material barely cut at all is not reported");
        check(PostRules.faultsInRosterCuts(PostRules.seals(),
                        PostRules.SEAL_MATERIALS, PostRules.symbols()).isEmpty(),
                "the seals this module ships are reported as half cut");
        check(!PostRules.faultsInRosterCount(List.of("red_wax_a"),
                        PostRules.SEAL_MATERIALS, PostRules.symbols()).isEmpty(),
                "a roster that does not come to what the two lists multiply to "
                        + "is not reported");
        check(PostRules.faultsInRosterCount(PostRules.seals(),
                        PostRules.SEAL_MATERIALS, PostRules.symbols()).isEmpty(),
                "the seals this module ships are reported as short");

        // Throwing controls, for the compact constructors.
        check(threwOn(() -> new PostRules.Mail("control", -1, 1, false, false,
                        true)),
                "mail with less than no room in it is accepted");
        check(threwOn(() -> new PostRules.Mail("control", 1, -1, false, false,
                        true)),
                "mail that costs less than nothing to send is accepted");
        check(threwOn(() -> new PostRules.Service("control", -1, false)),
                "a service less than nowhere away is accepted");
        check(threwOn(() -> new PostRules.Bird("control", PostRules.ABOVE, null,
                        0, 0)),
                "a bird with no colourings at all, which nothing could render, "
                        + "is accepted");
        check(threwOn(() -> new PostRules.Bird("control", PostRules.ABOVE,
                        "other", 0, 1)),
                "a bird that changes in no time at all is accepted");

        // ----------------------------------------------------------------
        // And now the sweep: journeys nobody wrote.
        // ----------------------------------------------------------------

        Random random = new Random(8_812_099L);
        int swept = 0;
        int before = PostRules.travelTicks(0);
        for (int blocks = 0; blocks <= PostRules.FURTHEST * 2; blocks++) {
            int took = PostRules.travelTicks(blocks);
            check(took >= before, "a parcel going " + blocks + " blocks arrives "
                    + "sooner than one going less far: " + took + " against "
                    + before);
            check(took >= PostRules.SOONEST && took <= PostRules.LATEST,
                    "a parcel going " + blocks + " blocks takes " + took
                            + " ticks, outside the band " + PostRules.SOONEST
                            + " to " + PostRules.LATEST);
            before = took;
            swept++;
        }
        int capped = PostRules.travelTicks(PostRules.FURTHEST);
        for (int round = 0; round < 200_000; round++) {
            int blocks = PostRules.FURTHEST + 1
                    + random.nextInt(Integer.MAX_VALUE - PostRules.FURTHEST - 1);
            int took = PostRules.travelTicks(blocks);
            check(took == capped, "a parcel going " + blocks + " blocks, which is "
                    + "past the cap, takes " + took + " rather than the capped "
                    + capped);
            swept++;
        }
        for (int round = 0; round < 50_000; round++) {
            int length = random.nextInt(PostRules.LONGEST_ADDRESS + 4);
            StringBuilder made = new StringBuilder();
            for (int at = 0; at < length; at++) {
                made.append((char) ('a' + random.nextInt(26)));
            }
            boolean fits = length >= 1 && length <= PostRules.LONGEST_ADDRESS;
            check(PostRules.addressable(made.toString()) == fits,
                    "an address of " + length + " characters is taken wrongly");
            swept++;
        }

        System.out.println("  the post: " + PostRules.MAIL.size()
                + " kind(s) of mail, " + PostRules.pigeonholes().size()
                + " pigeonhole(s), " + PostRules.seals().size() + " seal(s) and "
                + PostRules.BIRDS.size() + " bird(s); " + swept + " journeys and "
                + "addresses nobody wrote, every one of them inside the band "
                + PostRules.SOONEST + " to " + PostRules.LATEST + " ticks and "
                + "never quicker for being further");
    }

    // ==================================================================

    /**
     * Every claim {@link LightRules} makes, executed.
     *
     * <p>The rules are a table plus ten sweeps over it, and a table is exactly
     * the thing that looks right in a diff while being wrong. Each rule here is
     * handed a roster built to be caught, so that a rule which has quietly
     * stopped being able to fail says so.
     */
    private static void theLighting() {
        check(LightRules.faults().isEmpty(), "the fittings that ship report "
                + LightRules.faults().size() + " fault(s): " + LightRules.faults());

        // The table is the shape it claims to be.
        check(LightRules.KINDS.size() == 6, "there are "
                + LightRules.KINDS.size() + " kind(s) of fitting and the module "
                + "is written around six");
        check(LightRules.COLOURS.size() == 16, "there are "
                + LightRules.COLOURS.size() + " colour(s) and the game dyes in 16");
        for (LightRules.Fitting one : LightRules.FITTINGS) {
            check(LightRules.fitting(one.id()) == one, one.id()
                    + " is in the roster and cannot be looked up by name");
            check(LightRules.KINDS.contains(one.kind()), one.id() + " is a "
                    + one.kind() + ", which is not one of the kinds");
            check(one.light() >= 0 && one.light() <= 15, one.id() + " gives "
                    + one.light() + " light and a block gives nought to fifteen");
            check(one.cost() >= 1, one.id() + " costs " + one.cost());
            check(one.dyed() == !one.colour().isEmpty(), one.id()
                    + " disagrees with itself about whether it is dyed");
        }
        check(LightRules.fitting("a fitting nobody wrote") == null,
                "a fitting nobody wrote is found in the roster");

        // The compact constructor refuses nonsense.
        check(threwOn(() -> new LightRules.Fitting("", LightRules.HELD, 1, 1, "")),
                "a fitting with no name is accepted");
        check(threwOn(() -> new LightRules.Fitting("control", "not a kind", 1, 1, "")),
                "a fitting of a kind that does not exist is accepted");
        check(threwOn(() -> new LightRules.Fitting("control", LightRules.HELD, 16, 1, "")),
                "a fitting brighter than a block can be is accepted");
        check(threwOn(() -> new LightRules.Fitting("control", LightRules.HELD, -1, 1, "")),
                "a fitting darker than a block can be is accepted");
        check(threwOn(() -> new LightRules.Fitting("control", LightRules.HELD, 1, 0, "")),
                "a fitting that costs nothing is accepted");
        check(threwOn(() -> new LightRules.Fitting("control", LightRules.HELD, 1, 1, null)),
                "a fitting with a null colour is accepted");

        // A dyed set is one shape, however many colours it has.
        List<LightRules.Fitting> shapes = LightRules.shapes(LightRules.FITTINGS);
        check(shapes.size() < LightRules.FITTINGS.size(), "the roster has "
                + LightRules.FITTINGS.size() + " fitting(s) and just as many "
                + "shapes, so nothing is collapsing the dyed set");
        int dyedShapes = 0;
        for (LightRules.Fitting one : shapes) {
            if (one.dyed()) {
                dyedShapes++;
            }
        }
        check(dyedShapes == 1, "the shapes hold " + dyedShapes + " dyed "
                + "fitting(s), and the roster has one dyed set");

        // Adding colours to a dyed set changes nothing about what the roster is like.
        List<LightRules.Fitting> widened = new ArrayList<>(LightRules.FITTINGS);
        for (int extra = 0; extra < 40; extra++) {
            widened.add(new LightRules.Fitting("control_" + extra + "_"
                    + LightRules.HELD, LightRules.HELD, 6, 3, "control" + extra));
        }
        check(LightRules.shapes(widened).size() == shapes.size(),
                "forty more colours of candle holder changed the roster from "
                + shapes.size() + " shape(s) to "
                + LightRules.shapes(widened).size() + ", so a dyed set can "
                + "outvote everything else by being dyed harder");

        // dim() counts, and counts at the boundary the right way round.
        Random random = new Random(20_260_926L);
        int swept = 0;
        for (int round = 0; round < 200_000; round++) {
            int stillDark = random.nextInt(16);
            int wanted = 0;
            for (LightRules.Fitting one : LightRules.FITTINGS) {
                if (one.light() <= stillDark) {
                    wanted++;
                }
            }
            check(LightRules.dim(LightRules.FITTINGS, stillDark) == wanted,
                    "at or under " + stillDark + " light the roster has " + wanted
                    + " fitting(s) and dim() says "
                    + LightRules.dim(LightRules.FITTINGS, stillDark));
            swept++;
        }
        check(LightRules.dim(List.of(new LightRules.Fitting("control",
                        LightRules.HELD, 7, 1, "")), 7) == 1,
                "a fitting exactly at the top of the band is not counted as dim, "
                + "so the band is open at the top and the number means nothing");
        check(LightRules.dim(List.of(new LightRules.Fitting("control",
                        LightRules.HELD, 8, 1, "")), 7) == 0,
                "a fitting one over the top of the band is counted as dim");

        // Each rule, handed something built to be caught.
        List<LightRules.Fitting> glaring = new ArrayList<>(LightRules.FITTINGS);
        glaring.add(new LightRules.Fitting("control_" + LightRules.FLOOR,
                LightRules.FLOOR, 15, 99, ""));
        check(!LightRules.faultsInGlare(glaring, LightRules.BRIGHTEST).isEmpty(),
                "a fitting at the game's own full light is not reported");
        check(LightRules.faultsInGlare(LightRules.FITTINGS,
                LightRules.BRIGHTEST).isEmpty(),
                "the fittings that ship are reported as too bright");

        check(!LightRules.faultsInNames(List.of(
                        new LightRules.Fitting("control", LightRules.HELD, 1, 1, ""),
                        new LightRules.Fitting("control", LightRules.HUNG, 2, 2, "")))
                .isEmpty(), "two fittings under one name are not reported");

        check(!LightRules.faultsInGaps(LightRules.FITTINGS,
                        List.of(LightRules.HELD, "a kind nothing is")).isEmpty(),
                "a kind of fitting nothing in the roster is is not reported");

        check(!LightRules.faultsInKinds(List.of(
                        new LightRules.Fitting("control", LightRules.HELD, 1, 1, "")),
                        List.of(LightRules.HUNG)).isEmpty(),
                "a fitting of a kind the module does not have is not reported");

        check(!LightRules.faultsInBargains(List.of(
                        new LightRules.Fitting("bright", LightRules.HUNG, 12, 3, ""),
                        new LightRules.Fitting("dim", LightRules.HUNG, 4, 3, "")))
                .isEmpty(), "a fitting that is brighter and no dearer than "
                + "another of its kind is not reported");
        check(LightRules.faultsInBargains(List.of(
                        new LightRules.Fitting("bright", LightRules.HUNG, 12, 3, ""),
                        new LightRules.Fitting("dim", LightRules.FLOOR, 4, 3, "")))
                .isEmpty(), "two fittings of different kinds are compared on "
                + "price, and a lamp is not a lantern");

        check(!LightRules.faultsInColour(List.of(
                        new LightRules.Fitting("red_x", LightRules.HELD, 6, 3, "red"),
                        new LightRules.Fitting("blue_x", LightRules.HELD, 9, 3, "blue")))
                .isEmpty(), "two colours of one fitting at two light levels are "
                + "not reported");
        check(!LightRules.faultsInColour(List.of(
                        new LightRules.Fitting("red_x", LightRules.HELD, 6, 3, "red"),
                        new LightRules.Fitting("blue_x", LightRules.HELD, 6, 5, "blue")))
                .isEmpty(), "two colours of one fitting at two prices are not "
                + "reported");

        check(!LightRules.faultsInUnlit(List.of(
                        new LightRules.Fitting("control", LightRules.HELD, 1, 1, "")))
                .isEmpty(), "a roster in which everything gives light is not "
                + "reported");
        check(LightRules.faultsInUnlit(LightRules.FITTINGS).isEmpty(),
                "the fittings that ship are reported as all giving light");

        check(!LightRules.faultsInGrid(List.of(
                        new LightRules.Fitting("control", LightRules.FLOOR,
                                1, LightRules.GRID + 1, "")),
                        List.of(), LightRules.GRID).isEmpty(),
                "a fitting costing more than a crafting grid holds is not "
                + "reported, so its recipe is dropped at load and nothing says so");
        check(LightRules.faultsInGrid(List.of(
                        new LightRules.Fitting("control", LightRules.FLOOR,
                                1, LightRules.GRID, "")),
                        List.of(), LightRules.GRID).isEmpty(),
                "a fitting costing exactly what a crafting grid holds is "
                + "reported, so the last usable price is refused");
        check(LightRules.faultsInGrid(List.of(
                        new LightRules.Fitting("control", LightRules.FLOOR,
                                1, LightRules.GRID + 1, "")),
                        List.of("control"), LightRules.GRID).isEmpty(),
                "a fitting this module already has is priced by this table's "
                + "rule, and it is made somewhere else");
        for (LightRules.Fitting one : LightRules.FITTINGS) {
            if (LightRules.ALREADY.contains(one.id())) {
                continue;
            }
            check(one.cost() <= LightRules.GRID, one.id() + " costs "
                    + one.cost() + " and a crafting grid holds " + LightRules.GRID);
        }

        check(!LightRules.faultsInDoubles(List.of(
                        new LightRules.Fitting("rope", LightRules.STRUNG, 0, 1, ""),
                        new LightRules.Fitting(RestRules.ROPE, LightRules.STRUNG,
                                0, 1, "")),
                        List.of(RestRules.ROPE)).isEmpty(),
                "a fitting called rope beside a block called " + RestRules.ROPE
                + " is not reported, so the pack ships two ropes");
        check(!LightRules.faultsInDoubles(List.of(
                        new LightRules.Fitting("nail", LightRules.STRUNG, 0, 1, ""),
                        new LightRules.Fitting(RestRules.ROPE, LightRules.STRUNG,
                                0, 1, "")),
                        List.of(RestRules.ROPE)).isEmpty(),
                "a fitting whose name is the tail of a block this module already "
                + "has is not reported");
        check(LightRules.faultsInDoubles(List.of(
                        new LightRules.Fitting(RestRules.ROPE, LightRules.STRUNG,
                                0, 1, "")), List.of(RestRules.ROPE)).isEmpty(),
                "the block this module already has is reported as a double of "
                + "itself, so the right answer fails the rule");
        check(LightRules.faultsInDoubles(List.of(
                        new LightRules.Fitting("doormat", LightRules.UNDERFOOT,
                                0, 2, ""),
                        new LightRules.Fitting(RestRules.ROPE, LightRules.STRUNG,
                                0, 1, "")), List.of(RestRules.ROPE)).isEmpty(),
                "a fitting sharing no part of its name with anything this module "
                + "has is reported as a double");
        check(!LightRules.faultsInDoubles(LightRules.FITTINGS,
                        List.of("a block nothing here is")).isEmpty(),
                "a block this module is said to already have that no fitting "
                + "is, is not reported — so the registry counts itself against "
                + "a number that is one too small");
        check(LightRules.faultsInDoubles(LightRules.FITTINGS,
                        LightRules.ALREADY).isEmpty(),
                "the fittings that ship are reported as doubling something "
                + "this module already has");
        check(LightRules.ALREADY.contains(RestRules.ROPE), "the rope this module "
                + "already has is not in the list of things it already has, so "
                + "nothing stops a second one");

        check(!LightRules.faultsInMatching(List.of(
                        new LightRules.Fitting("red_x", LightRules.HELD,
                                LightRules.STILL_DARK + 1, 3, "red")),
                        LightRules.STILL_DARK).isEmpty(),
                "a dyed fitting over the band is not reported");
        check(LightRules.faultsInMatching(List.of(
                        new LightRules.Fitting("x", LightRules.HELD,
                                LightRules.STILL_DARK + 1, 3, "")),
                        LightRules.STILL_DARK).isEmpty(),
                "an undyed fitting over the band is reported as a dyed one, so "
                + "the rule is not about the set a player places by the dozen");

        // The darkness rule, walked past its own edge rather than argued about.
        List<LightRules.Fitting> lit = new ArrayList<>();
        for (int at = 0; at < 9; at++) {
            lit.add(new LightRules.Fitting("control_" + at, LightRules.HELD,
                    12, at + 1, ""));
        }
        check(!LightRules.faultsInDarkness(lit, LightRules.STILL_DARK,
                LightRules.DIM_SHARE).isEmpty(),
                "a roster of nine bright shapes and nothing dim is not reported");
        for (int at = 0; at < 4; at++) {
            lit.add(new LightRules.Fitting("dim_" + at, LightRules.HELD,
                    LightRules.STILL_DARK, 20 + at, ""));
        }
        check(!LightRules.faultsInDarkness(lit, LightRules.STILL_DARK,
                LightRules.DIM_SHARE).isEmpty(),
                "a roster of thirteen shapes, four of them dim, is passed, and "
                + "a third of thirteen is four and a third");
        lit.add(new LightRules.Fitting("dim_4", LightRules.HELD,
                LightRules.STILL_DARK, 24, ""));
        check(LightRules.faultsInDarkness(lit, LightRules.STILL_DARK,
                LightRules.DIM_SHARE).isEmpty(),
                "a roster of fourteen shapes, five of them dim, is reported as "
                + "too bright, and a third of fourteen is under five");
        check(LightRules.faultsInDarkness(List.of(), LightRules.STILL_DARK,
                LightRules.DIM_SHARE).isEmpty(),
                "an empty roster is reported rather than passed over");

        // And the share is read from the shapes, not from the blocks.
        List<LightRules.Fitting> dyedBright = new ArrayList<>();
        for (String colour : LightRules.COLOURS) {
            dyedBright.add(new LightRules.Fitting(colour + "_x", LightRules.HELD,
                    1, 1, colour));
        }
        for (int at = 0; at < 8; at++) {
            dyedBright.add(new LightRules.Fitting("control_" + at, LightRules.HUNG,
                    12, at + 1, ""));
        }
        check(!LightRules.faultsInDarkness(dyedBright, LightRules.STILL_DARK,
                LightRules.DIM_SHARE).isEmpty(),
                "sixteen colours of one dim thing and eight bright ones is "
                + "reported as dark enough, so the share is counted in blocks "
                + "and a dyed set answers the question sixteen times");

        check(!LightRules.faultsInDyedSets(List.of(
                        new LightRules.Fitting("red_x", LightRules.HELD, 6, 3, "red")),
                        List.of("red", "blue")).isEmpty(),
                "a dyed set missing a colour is not reported");
        check(LightRules.faultsInDyedSets(List.of(
                        new LightRules.Fitting("x", LightRules.HELD, 6, 3, "")),
                        List.of("red", "blue")).isEmpty(),
                "a kind with no dyed set at all is reported as missing colours");

        System.out.println("  the lighting: " + LightRules.FITTINGS.size()
                + " fitting(s) in " + shapes.size() + " shape(s) over "
                + LightRules.KINDS.size() + " kind(s), " + swept + " counts of "
                + "a band nobody asked for, nothing over " + LightRules.BRIGHTEST
                + " light, nothing brighter for less, and "
                + LightRules.dim(shapes, LightRules.STILL_DARK) + " of "
                + shapes.size() + " shape(s) still dark enough at "
                + LightRules.STILL_DARK + " to leave the bestiary somewhere to be");
    }
}
