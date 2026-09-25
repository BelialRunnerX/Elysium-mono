package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Compression, executed.
 *
 * <p>Two properties, and a save depends on both.
 *
 * <p><b>The round trip.</b> Compress a pile and decompress it and exactly what
 * went in comes back — every tier, every material, nothing rounded and nothing
 * left over. It is exact by construction, and "exact by construction" is what
 * everybody says about the arithmetic that turns out to have eaten four
 * ingots, so it is swept: every material, every tier, every amount up to a
 * stack, and then random amounts up to the largest pool a slot can hold.
 *
 * <p><b>Nine, exactly.</b> A tier worth eight of the one below lets a player
 * destroy matter in a loop; one worth ten lets them make it, and there is
 * nothing in this pack that survives an infinite source of iron. Proved by
 * arithmetic on every tier and then by a long random walk of compressing and
 * decompressing that must always come back with what it started with.
 */
public final class CompressHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theRoster();
        theLadder();
        theRoundTrip();
        theWalk();
        theStrength();
        theCatalogue();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every tier worth exactly nine of the "
                + "one below, every round trip coming back with what went in at "
                + "every tier for every material, and a long walk of compressing "
                + "and decompressing that never once made or destroyed anything");
    }

    // ==================================================================

    /**
     * The roster, before anything else reads it.
     *
     * <p>First and on its own, because every check below walks the materials
     * and the tiers: a roster that has lost a name makes all of them mean
     * something other than what they say, and the honest thing is to stop
     * rather than to report nine hundred consequences of one mistake.
     */
    private static void theRoster() {
        check(CompressRules.muddled().isEmpty(),
                "the roster disagrees with itself: " + CompressRules.muddled());
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("  the roster: " + CompressRules.MATERIALS.size()
                + " material(s), " + CompressRules.TIERS + " tier(s), a name and a "
                + "frame apiece and no two the same");
    }

    // ==================================================================

    private static void theLadder() {
        check(CompressRules.mispriced().isEmpty(),
                "a tier that is not nine of the one below: "
                        + CompressRules.mispriced());
        check(CompressRules.PER_STEP == 9, "compression is "
                + CompressRules.PER_STEP + " deep a step and a crafting grid is 9");
        check(CompressRules.TIERS >= 3, "compression that goes "
                + CompressRules.TIERS + " deep is a block, not a ladder");
        long before = 1;
        for (int tier = 1; tier <= CompressRules.TIERS; tier++) {
            long here = CompressRules.worth(tier);
            check(here == before * CompressRules.PER_STEP, "tier " + tier
                    + " is worth " + here + " and nine of the tier below is "
                    + before * CompressRules.PER_STEP);
            check(here > 0, "tier " + tier + " is worth " + here
                    + ", which has gone round the end of the number it is kept in");
            before = here;
        }
        // The count that does not fit in an int, which is why every one of
        // them is a long. Asserted rather than left to a comment.
        check(CompressRules.biggestPool() > Integer.MAX_VALUE,
                "the largest pool a slot can hold is "
                        + CompressRules.biggestPool() + ", which fits in an int, so "
                        + "the comment about needing a long is about a different "
                        + "ladder than the one that ships");
        check(!CompressRules.overflows(CompressRules.TIERS),
                "a ladder " + CompressRules.TIERS + " deep goes round the end of "
                        + "the number it is kept in, so a full stack of the "
                        + "deepest tier holds a negative number of cobblestone");
        System.out.println("  the ladder: " + CompressRules.TIERS + " tiers of "
                + CompressRules.PER_STEP + ", the deepest worth "
                + CompressRules.worth(CompressRules.TIERS) + " of its material and "
                + "a full stack of it " + CompressRules.biggestPool()
                + " — which is why every count here is a long");
    }

    // ==================================================================

    private static void theRoundTrip() {
        List<Long> amounts = new ArrayList<>();
        for (long one = 1; one <= CompressRules.STACK; one++) {
            amounts.add(one);
        }
        check(CompressRules.lossy(amounts).isEmpty(),
                "a round trip that lost something: "
                        + CompressRules.lossy(amounts));

        // And every material, one at a time, because the property is about
        // the catalogue and not only about the arithmetic: a material left
        // out of the crossing is a pile nobody can compress.
        for (String material : CompressRules.MATERIALS) {
            for (int tier = 1; tier <= CompressRules.TIERS; tier++) {
                for (long amount : new long[] {1, 2, 7, 63, 64}) {
                    long[] back = CompressRules.fromBase(
                            CompressRules.inBase(amount, tier), tier);
                    check(back[0] == amount, material + " at tier " + tier
                            + ": " + amount + " came back as " + back[0]);
                    check(back[1] == 0, material + " at tier " + tier + ": "
                            + back[1] + " left over");
                }
                check(!CompressRules.idFor(material, tier).isEmpty(),
                        material + " has no name at tier " + tier);
                check(CompressRules.madeOf(material, tier)
                                .equals(tier == 1
                                        ? CompressRules.blockOf(material)
                                        : "elysiumlogistics:"
                                                + CompressRules.idFor(material,
                                                        tier - 1)),
                        CompressRules.idFor(material, tier)
                                + " is not made of the tier below it");
            }
        }

        // Random amounts, up to what a slot can actually hold.
        Random random = new Random(3_141_592L);
        for (int round = 0; round < 40_000; round++) {
            int tier = 1 + random.nextInt(CompressRules.TIERS);
            long amount = 1 + random.nextInt(CompressRules.STACK);
            long base = CompressRules.inBase(amount, tier);
            long[] back = CompressRules.fromBase(base, tier);
            check(back[0] == amount && back[1] == 0,
                    amount + " at tier " + tier + " came back as " + back[0]
                            + " with " + back[1] + " over");
            check(base == amount * CompressRules.worth(tier),
                    amount + " at tier " + tier + " is worth " + base
                            + " and should be " + amount
                            * CompressRules.worth(tier));
            // And the part that is not a whole number of the tier: a pool one
            // short of a tier must read as one fewer with the rest left over.
            if (base > 1) {
                long[] shy = CompressRules.fromBase(base - 1, tier);
                check(shy[0] == amount - 1,
                        "a pool one short of " + amount + " at tier " + tier
                                + " reads as " + shy[0]);
                check(shy[1] == CompressRules.worth(tier) - 1,
                        "a pool one short of " + amount + " at tier " + tier
                                + " leaves " + shy[1] + " over rather than "
                                + (CompressRules.worth(tier) - 1));
            }
        }
        System.out.println("  the round trip: every amount up to a stack at every "
                + "one of " + CompressRules.TIERS + " tiers for all "
                + CompressRules.MATERIALS.size() + " materials, and 40,000 random "
                + "pools besides, every one of them coming back with exactly what "
                + "went in");
    }

    // ==================================================================

    private static void theWalk() {
        // Compress and decompress at random for a long time. A single step
        // being exact does not stop a chain of them drifting, and a chain is
        // what a player actually does.
        Random random = new Random(2_718_281L);
        int walks = 0;
        for (int walk = 0; walk < 60_000; walk++) {
            long base = 1 + random.nextInt(1_000_000);
            long started = base;
            for (int step = 0; step < 12; step++) {
                int tier = 1 + random.nextInt(CompressRules.TIERS);
                long[] split = CompressRules.fromBase(base, tier);
                // Compress what will go, keep the rest, then put it all back.
                base = CompressRules.inBase(split[0], tier) + split[1];
            }
            walks++;
            check(base == started, "a walk of compressing and decompressing "
                    + "started with " + started + " and came back with " + base);
        }
        check(walks == 60_000, "the walk did not run");
        System.out.println("  the walk: " + walks + " chains of twelve "
                + "compressions and decompressions apiece, not one of which came "
                + "back with a different pile than it started with");
    }

    // ==================================================================

    private static void theStrength() {
        check(CompressRules.unhardened().isEmpty(),
                "a tier softer or more fragile than the one below it: "
                        + CompressRules.unhardened());
        float hard = 0;
        float tough = 0;
        int capped = 0;
        for (int tier = 1; tier <= CompressRules.TIERS; tier++) {
            check(CompressRules.hardness(tier) >= hard, "tier " + tier
                    + " is softer than the tier below it");
            check(CompressRules.resistance(tier) >= tough, "tier " + tier
                    + " is more fragile than the tier below it");
            check(CompressRules.hardness(tier) <= CompressRules.HARDEST,
                    "tier " + tier + " is harder than obsidian");
            check(CompressRules.resistance(tier) <= CompressRules.TOUGHEST,
                    "tier " + tier + " is tougher than obsidian");
            if (CompressRules.resistance(tier) >= CompressRules.TOUGHEST) {
                capped++;
            }
            hard = CompressRules.hardness(tier);
            tough = CompressRules.resistance(tier);
        }
        check(capped > 0, "nothing reaches the blast ceiling, so the ceiling has "
                + "never been applied to anything and would not be noticed if it "
                + "were wrong");
        check(capped < CompressRules.TIERS, "every tier is at the blast ceiling, "
                + "so the ladder of toughness is one rung with nine names");
        System.out.println("  the strength: hardness from "
                + CompressRules.hardness(1) + " to "
                + CompressRules.hardness(CompressRules.TIERS) + " and blast "
                + "resistance from " + CompressRules.resistance(1) + " up to the "
                + "game's own obsidian, which " + capped + " of "
                + CompressRules.TIERS + " tiers reach");
    }

    // ==================================================================

    private static void theCatalogue() {
        check(CompressRules.howMany()
                        == CompressRules.MATERIALS.size() * CompressRules.TIERS,
                "the catalogue holds " + CompressRules.howMany() + " and "
                        + CompressRules.MATERIALS.size() + " materials times "
                        + CompressRules.TIERS + " tiers is "
                        + CompressRules.MATERIALS.size() * CompressRules.TIERS);
        Set<String> seen = new LinkedHashSet<>();
        for (String id : CompressRules.catalogue()) {
            check(seen.add(id), id + " is in the catalogue twice");
            check(!id.contains(":"), id + " carries a namespace into its own id");
        }
        for (String marker : CompressRules.MARKERS) {
            check(marker.startsWith("minecraft:"), marker + " frames a tier and is "
                    + "not one of the game's own blocks, so this module would ship "
                    + "a texture");
        }
        for (String material : CompressRules.MATERIALS) {
            check(CompressRules.blockOf(material).startsWith("minecraft:"),
                    material + " is not one of the game's own blocks");
            check(!CompressRules.MATERIALS.contains(
                            CompressRules.idFor(material, 1)),
                    "a compression of " + material + " is itself a material here, "
                            + "which is a ladder that eats its own tail");
        }
        System.out.println("  the catalogue: " + CompressRules.MATERIALS.size()
                + " material(s) x " + CompressRules.TIERS + " tier(s) = "
                + CompressRules.howMany() + " block(s), each framed in one of "
                + CompressRules.MARKERS.size() + " of the game's own metals so the "
                + "tier reads across a room without a single new sprite");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        // The round-trip check, handed a trip that does not come back.
        check(CompressRules.lossy(List.of(1L)).isEmpty(),
                "an ordinary round trip is reported as lossy, so the check says "
                        + "yes to everything");
        long[] short_ = CompressRules.fromBase(CompressRules.PER_STEP - 1, 1);
        check(short_[0] == 0 && short_[1] == CompressRules.PER_STEP - 1,
                "eight of something reads as " + short_[0] + " compressed with "
                        + short_[1] + " over, and it is none with eight over — so "
                        + "the split that stops a drawer eating the remainder is "
                        + "not splitting");
        check(CompressRules.fromBase(CompressRules.PER_STEP, 1)[0] == 1,
                "nine of something does not read as one compressed, so the whole "
                        + "ladder is off by a step");
        check(CompressRules.inBase(1, 0) == 1,
                "one of the base material is worth " + CompressRules.inBase(1, 0)
                        + " of itself");
        check(CompressRules.inBase(5, 2) == 5L * CompressRules.PER_STEP
                        * CompressRules.PER_STEP,
                "five at tier two is not five times eighty-one");

        // The price check, handed a step built to be caught. It walks the
        // real tiers, so over a healthy ladder it can only answer no — which
        // is exactly what a check that has stopped working also answers.
        check(!CompressRules.faultsInStep(4, 8, 1).isEmpty(),
                "a tier worth eight of the one below is not reported, so the price "
                        + "check cannot say yes and the one number this whole file "
                        + "exists for is unguarded");
        check(!CompressRules.faultsInStep(4, 10, 1).isEmpty(),
                "a tier worth ten of the one below is not reported");
        check(!CompressRules.faultsInStep(4, 1, 1).isEmpty(),
                "a tier worth the same as the one below it is not reported");
        check(CompressRules.faultsInStep(4, CompressRules.PER_STEP, 1).isEmpty(),
                "an honest step of nine is reported as wrong, so the price check "
                        + "says yes to everything");

        // And the round-trip check, handed a trip that lost something.
        check(!CompressRules.faultsInTrip(5, 2, 4, 0).isEmpty(),
                "a pile that went in as five and came back as four is not "
                        + "reported, so the round-trip check cannot say yes");
        check(!CompressRules.faultsInTrip(5, 2, 5, 3).isEmpty(),
                "a trip that left three over is not reported, so the remainder "
                        + "half of the round trip cannot say yes");
        check(CompressRules.faultsInTrip(5, 2, 5, 0).isEmpty(),
                "an exact round trip is reported as lossy");

        check(CompressRules.worth(2) / CompressRules.worth(1)
                        == CompressRules.PER_STEP,
                "the arithmetic the price check is built on does not hold");

        // And the ceiling, asked about depths whose answers are known.
        check(CompressRules.overflows(40),
                "a ladder forty tiers deep is not reported as going round the end "
                        + "of a long, so the ceiling would let a deeper ladder "
                        + "ship holding a negative number of cobblestone");
        check(!CompressRules.overflows(1),
                "one tier is reported as going round the end of a long, so the "
                        + "ceiling says no to everything");

        // The names, handed a tier that does not exist.
        List<String> boom = new ArrayList<>();
        try {
            CompressRules.idFor("cobblestone", 0);
            boom.add("tier zero was allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            CompressRules.idFor("cobblestone", CompressRules.TIERS + 1);
            boom.add("a tier past the last was allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(boom.isEmpty(), "the names allow what they say they forbid: " + boom);
        check(CompressRules.idFor("cobblestone", 1).equals("compressed_cobblestone"),
                "the first tier of cobblestone is called "
                        + CompressRules.idFor("cobblestone", 1));

        System.out.println("  the controls: eight of something proved to read as "
                + "none with eight over, a tier that does not exist refused, and "
                + "the ladder's own arithmetic checked against itself");
    }
}
