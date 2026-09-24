package com.elysium.lib.claim;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * What a claim is worth, and what ascending one costs.
 *
 * <h2>What is executed here</h2>
 *
 * The radius ladder and the Suspicion floor. Both are arithmetic over a record,
 * both decide how territory feels, and one of them guards an invariant stated
 * three files away: {@link ElysiumClaim#MAX_FLOOR} is seventy because the
 * Hunted band starts at seventy-five, and that gap is what stops any amount of
 * building or ascending from summoning a five-wave siege. Adding a second
 * contributor to the floor is exactly how a gap like that gets closed by
 * accident.
 */
public final class ClaimHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static ElysiumClaim claim(int structures, int ascension) {
        return new ElysiumClaim(UUID.nameUUIDFromBytes(new byte[] {1}), "tester",
                new BlockPos(0, 64, 0), ElysiumClaim.radiusFor(ascension),
                structures, ascension);
    }

    /** Sixty-four a tier, exactly, for as long as anybody can climb. */
    private static void theRadiusLadder() {
        check(ElysiumClaim.radiusFor(0) == 64,
                "an unascended claim starts at " + ElysiumClaim.radiusFor(0)
                        + " rather than the 64 it has always been");

        // Sixty-four is written here as a literal, not read from
        // RADIUS_PER_ASCENSION.
        //
        // The first version computed the expected radius from that constant, so
        // changing it to 32 changed both sides of the comparison and every
        // assertion still passed. A check whose expectation is derived from the
        // thing it is checking cannot fail — it only restates the code. The
        // design statement is "each ascension tier grants 64 blocks of radius",
        // and 64 is therefore the number this file has to know independently.
        final int perTier = 64;
        check(ElysiumClaim.RADIUS_PER_ASCENSION == perTier,
                "a tier grants " + ElysiumClaim.RADIUS_PER_ASCENSION
                        + " blocks of radius, not the " + perTier + " it is meant to");
        for (int tier = 0; tier <= 64; tier++) {
            int expected = 64 + perTier * tier;
            check(ElysiumClaim.radiusFor(tier) == expected,
                    "tier " + tier + " gives radius " + ElysiumClaim.radiusFor(tier)
                            + ", not the " + expected + " that sixty-four a tier means");
        }

        // Monotonic, and never negative however absurd the input. A negative
        // radius contains nothing, so an overflow here would silently un-claim
        // somebody's entire base.
        int previous = -1;
        for (int tier = 0; tier < 4096; tier++) {
            int radius = ElysiumClaim.radiusFor(tier);
            check(radius > previous, "radius stopped growing at tier " + tier);
            check(radius > 0, "radius went non-positive at tier " + tier);
            previous = radius;
        }
        check(ElysiumClaim.radiusFor(Integer.MAX_VALUE) > 0,
                "a hand-edited save with a huge ascension overflows to a claim "
                        + "that contains nothing");
        check(ElysiumClaim.radiusFor(-5) == ElysiumClaim.DEFAULT_RADIUS,
                "a negative ascension shrinks the claim below its default");

        System.out.println("  radius: tier 0 " + ElysiumClaim.radiusFor(0)
                + ", tier 1 " + ElysiumClaim.radiusFor(1)
                + ", tier 4 " + ElysiumClaim.radiusFor(4)
                + " — area at tier 4 is "
                + Math.round(Math.pow((double) ElysiumClaim.radiusFor(4)
                        / ElysiumClaim.radiusFor(0), 2))
                + "x the ground tier 0 covered");
    }

    /**
     * The gap below the Hunted band, which nothing may close.
     *
     * The most valuable check here. Ascension is a second contributor to a
     * number that already had one, and the whole design of §14.2 rests on that
     * number never reaching seventy-five.
     */
    private static void theFloorStaysBelowHunted() {
        final int huntedBand = 75;
        check(ElysiumClaim.MAX_FLOOR < huntedBand,
                "the claim floor cap is " + ElysiumClaim.MAX_FLOOR
                        + " and the Hunted band starts at " + huntedBand
                        + ", so building can now summon a siege by itself");

        for (int structures = 0; structures <= 2_000_000; structures += 50_000) {
            for (int ascension = 0; ascension <= 200; ascension += 10) {
                int floor = claim(structures, ascension).floor();
                check(floor <= ElysiumClaim.MAX_FLOOR,
                        structures + " structures at ascension " + ascension
                                + " gives a floor of " + floor
                                + ", above the cap of " + ElysiumClaim.MAX_FLOOR);
                check(floor < huntedBand,
                        "a claim can hold its owner at " + floor
                                + ", inside the Hunted band");
                check(floor >= ElysiumClaim.BASE_FLOOR,
                        "a claim floor fell below what an empty claim is worth");
            }
        }

        // Ascending has to move the floor, or it is the one consequential act
        // in the mod that costs materials and nothing else.
        check(claim(0, 1).floor() > claim(0, 0).floor(),
                "ascending a claim does not raise the Suspicion floor at all");

        int atCap = claim(0, 1000).floor();
        check(atCap == ElysiumClaim.MAX_FLOOR,
                "a thousand tiers of ascension reaches a floor of " + atCap
                        + " rather than resting at the cap");

        System.out.println("  floor: empty " + claim(0, 0).floor()
                + ", +1 tier " + claim(0, 1).floor()
                + ", +4 tiers " + claim(0, 4).floor()
                + ", capped at " + ElysiumClaim.MAX_FLOOR
                + " against a Hunted band of " + huntedBand);
    }

    /** Ascending changes the size and nothing else about whose claim it is. */
    private static void ascendingPreservesIdentity() {
        ElysiumClaim before = claim(1234, 2);
        ElysiumClaim after = before.ascended();

        check(after.ascension() == before.ascension() + 1,
                "ascended() did not raise the tier by exactly one");
        check(after.radius() == ElysiumClaim.radiusFor(after.ascension()),
                "the grown claim's radius does not match its tier, so the record "
                        + "and the ladder disagree about how big it is");
        check(after.owner().equals(before.owner())
                        && after.ownerName().equals(before.ownerName()),
                "ascending a claim changed who owns it");
        check(after.anchor().equals(before.anchor()),
                "ascending a claim moved it");
        check(after.structures() == before.structures(),
                "ascending a claim changed how much is built on it");

        // Growing by structures must not quietly reset the tier — the two
        // operations touch the same record and are written in different files.
        ElysiumClaim built = after.grown(500);
        check(built.ascension() == after.ascension(),
                "recording new structures reset the claim's ascension, so a "
                        + "player's territory shrinks every time they build");
        check(built.radius() == after.radius(),
                "recording new structures changed the claim's radius");
    }

    /** A claim contains what its radius says it contains. */
    private static void containment() {
        for (int tier = 0; tier <= 6; tier++) {
            ElysiumClaim c = claim(0, tier);
            int r = c.radius();
            check(c.contains(new BlockPos(0, 64, 0)), "tier " + tier
                    + " does not contain its own anchor");
            check(c.contains(new BlockPos(r, 64, 0)),
                    "tier " + tier + " excludes a block exactly on its edge");
            check(!c.contains(new BlockPos(r + 1, 64, 0)),
                    "tier " + tier + " reaches past its own radius");
            // Unbounded vertically: a cylinder, not a sphere. A mine under the
            // beacon has to still be inside the claim.
            check(c.contains(new BlockPos(0, -2000, 0))
                            && c.contains(new BlockPos(0, 5000, 0)),
                    "tier " + tier + " is a sphere, so a deep mine under the "
                            + "beacon is outside its owner's own claim");
        }
        System.out.println("  containment: a cylinder at every tier, edge "
                + "inclusive, unbounded above and below");
    }

    public static void main(String[] args) {
        theRadiusLadder();
        theFloorStaysBelowHunted();
        ascendingPreservesIdentity();
        containment();

        System.out.println("claims: the radius ladder, the Suspicion floor and "
                + "the Hunted gap — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("sixty-four a tier, and no amount of it reaches Hunted");
    }
}
