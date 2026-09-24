package com.elysium.arsenal.rules;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link ArsenalRules}, executed.
 *
 * The claim is that not one tool here is simply better than the one it
 * replaces, and not one of them is so much worse that nobody would make it.
 * That is a band rather than a number, and a band is the sort of thing a
 * designer is confident about and wrong about.
 */
public final class ArsenalHarness {

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
        theBand();
        theCost();
        theScythe();
        theCatalogue();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every broad tool slower than the one it "
                + "replaces on a single block and faster over a full swing, every block "
                + "it takes paid for as if it had been mined singly, and a scythe that "
                + "never hands back more seed than there were crops");
    }

    // ==================================================================

    private static void theBand() {
        check(ArsenalRules.REACH == 1,
                "a broad tool reaches " + ArsenalRules.REACH + ", which is "
                        + ArsenalRules.blocksIn(ArsenalRules.REACH) + " blocks a swing — "
                        + "either no wider than a pickaxe or a ceiling you drop on "
                        + "yourself");
        check(ArsenalRules.blocksIn(1) == 9, "one of reach is not three by three");
        check(ArsenalRules.blocksIn(0) == 1, "no reach at all is not one block");
        check(ArsenalRules.blocksIn(-4) == 1, "a negative reach took blocks");

        for (ArsenalRules.Broad broad : ArsenalRules.BROADS) {
            check(broad.leavesTheSpecialistAlive(),
                    broad.name() + " swings in " + broad.swing() + " of a specialist's "
                            + "swing, so it is strictly better than a " + broad.replaces()
                            + " and the " + broad.replaces() + " is now a crafting "
                            + "ingredient");
            if (broad.wide()) {
                check(broad.worthCarrying(),
                        broad.name() + " clears nine blocks in " + broad.clearing()
                                + " and a " + broad.replaces() + " does it in "
                                + broad.specialistClearing() + ", so nobody would ever "
                                + "make one");
                check(broad.blocks() == 9, broad.name() + " takes " + broad.blocks()
                        + " blocks a swing");
            } else {
                check(broad.blocks() == 1,
                        broad.name() + " is not wide and takes " + broad.blocks()
                                + " blocks");
            }
        }
        check(ArsenalRules.dominant().isEmpty(),
                "tools that are simply better than what they replace: "
                        + ArsenalRules.dominant());
        check(ArsenalRules.pointless().isEmpty(),
                "tools nobody would ever make: " + ArsenalRules.pointless());

        // The band itself, swept. Every share strictly inside it has to pass
        // both tests and every share outside it has to fail one, or the two
        // tests are not describing a band at all.
        for (int step = 1; step < 1000; step++) {
            float share = step / 1000.0F;
            ArsenalRules.Broad made = new ArsenalRules.Broad("swept", true, share, "x");
            boolean inside = share > 1.0F / 9.0F && share < 1.0F;
            boolean passes = made.leavesTheSpecialistAlive() && made.worthCarrying();
            check(passes == inside,
                    "a share of " + share + " is " + (inside ? "inside" : "outside")
                            + " the band and the two tests say " + passes);
        }

        // The one that does every job has to be worse than the ones that do
        // one, or a player carries it and nothing else for the rest of the game.
        ArsenalRules.Broad paxel = null;
        for (ArsenalRules.Broad broad : ArsenalRules.BROADS) {
            if (!broad.wide()) {
                paxel = broad;
            }
        }
        check(paxel != null, "there is no tool that does every job");
        if (paxel != null) {
            check(paxel.swing() > 1.0F,
                    "the paxel is as fast as the tools it replaces, so it replaces all "
                            + "of them and nothing else is ever carried");
            check(ArsenalRules.PAXEL_SHARE > ArsenalRules.BROAD_SHARE,
                    "the paxel is slower than a hammer as well as narrower, which is "
                            + "two penalties for one convenience and nobody makes one");
        }
        System.out.println("  the band: every tool inside it, and a thousand shares "
                + "swept where the two tests agree with the band at every one");
    }

    private static void theCost() {
        check(ArsenalRules.COST_PER_BLOCK >= 1,
                "a block costs " + ArsenalRules.COST_PER_BLOCK + " durability, so nine "
                        + "blocks are cheaper than nine and every other tool in the "
                        + "game is a mistake");
        Random random = new Random(1_812_015L);
        for (int round = 0; round < 500_000; round++) {
            int blocks = random.nextInt(4_096) - 64;
            int cost = ArsenalRules.costOf(blocks);
            check(cost >= 0, "taking " + blocks + " blocks cost " + cost);
            check(cost >= Math.max(0, blocks),
                    "taking " + blocks + " blocks cost " + cost + ", which is cheaper "
                            + "than mining them one at a time");
        }
        check(ArsenalRules.costOf(9) == 9,
                "a full swing does not cost what nine single blocks cost");
        check(ArsenalRules.costOf(0) == 0, "a swing that took nothing cost something");
        System.out.println("  the cost: half a million swings, and not one of them "
                + "cheaper per block than mining by hand");
    }

    private static void theScythe() {
        for (int harvested = 0; harvested < 100_000; harvested++) {
            int seeds = ArsenalRules.seedsFrom(harvested);
            check(seeds <= harvested,
                    "harvesting " + harvested + " crops gave " + seeds + " seeds, which "
                            + "is a seed farm that needs no field");
            check(ArsenalRules.replanted(harvested) <= seeds,
                    "more was replanted than came back");
            check(ArsenalRules.seedsKept(harvested) >= 0,
                    "the player kept a negative number of seeds");
            check(ArsenalRules.replanted(harvested)
                            + ArsenalRules.seedsKept(harvested) == seeds,
                    "what was replanted and what was kept do not add up to what came "
                            + "back, so a seed went somewhere");
        }
        check(ArsenalRules.seedsFrom(-10) == 0, "harvesting nothing gave seeds");
        System.out.println("  the scythe: a hundred thousand harvests, every seed "
                + "accounted for, and never one more than there were crops");
    }

    private static void theCatalogue() {
        check(ArsenalRules.TIERS.size() >= 5,
                "there are only " + ArsenalRules.TIERS.size() + " tiers");
        Set<String> seen = new HashSet<>();
        for (String name : ArsenalRules.catalogue()) {
            check(seen.add(name), name + " is in the catalogue twice");
            check(name.matches("[a-z_]+"),
                    name + " is not a name an item id can be made of");
        }
        check(seen.size() == ArsenalRules.TIERS.size() * ArsenalRules.BROADS.size(),
                "the catalogue is " + seen.size() + " items and should be "
                        + ArsenalRules.TIERS.size() * ArsenalRules.BROADS.size());
        Set<String> shapes = new HashSet<>();
        for (ArsenalRules.Broad broad : ArsenalRules.BROADS) {
            check(shapes.add(broad.name()), broad.name() + " is declared twice");
        }
        System.out.println("  catalogue: " + ArsenalRules.BROADS.size() + " shapes x "
                + ArsenalRules.TIERS.size() + " tiers = " + seen.size() + " tools");
    }
}
