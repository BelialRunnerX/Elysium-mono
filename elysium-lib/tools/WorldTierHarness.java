package com.elysium.lib.tier;

/**
 * The world tier, executed.
 *
 * A tier decides what may drop, so every claim about it is a claim about what
 * a player is allowed to find. The ones worth executing are the ones that are
 * easy to get subtly wrong: where a tier begins, that a floor is a floor and
 * not a term, and that nothing here has a ceiling while everything percentile
 * it buys does.
 */
public final class WorldTierHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        fromLevel();
        theFloor();
        noCeiling();
        shares();
        gates();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + ElysiumWorldTier.LEVELS_PER_TIER
                + " levels a tier, a world floor that only rises, no ceiling, "
                + "and shares that approach one without reaching it");
    }

    private static void fromLevel() {
        check(ElysiumWorldTier.of(1, 0) == 0, "a first-level character was past the Frontier");
        check(ElysiumWorldTier.of(9, 0) == 0, "a tier began one level early");
        check(ElysiumWorldTier.of(10, 0) == 1, "ten levels did not buy the first tier");
        check(ElysiumWorldTier.of(45, 0) == 4, "forty-five levels was not four tiers");
        check(ElysiumWorldTier.of(0, 0) == 0, "level zero produced something other than the Frontier");
        check(ElysiumWorldTier.of(-5, 0) == 0, "a negative level produced a negative tier");
        for (int tier = 0; tier < 20; tier++) {
            check(ElysiumWorldTier.of(ElysiumWorldTier.levelFor(tier), 0) == tier,
                    "the level for tier " + tier + " does not reach tier " + tier);
            check(tier == 0 || ElysiumWorldTier.of(ElysiumWorldTier.levelFor(tier) - 1, 0) == tier - 1,
                    "one level short of tier " + tier + " was already tier " + tier);
        }
    }

    private static void theFloor() {
        check(ElysiumWorldTier.of(1, 3) == 3, "standing on a tier-3 world did not raise a tier-0 player");
        check(ElysiumWorldTier.of(45, 1) == 4,
                "a low floor pulled a high-level player down; a floor is a floor, not a term");
        check(ElysiumWorldTier.of(45, 1) != 5,
                "a floor was added to the level rather than compared with it");
        check(ElysiumWorldTier.of(1, -3) == 0, "a negative floor went below the Frontier");
    }

    private static void noCeiling() {
        check(ElysiumWorldTier.of(10_000, 0) == 1000, "the tier stopped climbing with level");
        check(ElysiumWorldTier.of(1, 999) == 999, "the floor stopped climbing");
        check(ElysiumWorldTier.name(0).equals("Frontier") && ElysiumWorldTier.name(4).equals("Apotheosis"),
                "the named tiers are not named");
        check(ElysiumWorldTier.name(5).equals("Apotheosis 1"),
                "the first tier past the names is not counted from the top of the names");
        check(ElysiumWorldTier.name(9).equals("Apotheosis 5"), "the count past the names is wrong");
        check(ElysiumWorldTier.nameKey(4).equals("elysium.world_tier.apotheosis"),
                "a named tier reads the wrong key");
        check(ElysiumWorldTier.nameKey(40).equals("elysium.world_tier.beyond"),
                "every tier past the names would need a translation of its own");
    }

    private static void shares() {
        check(ElysiumWorldTier.share(0) == 0.0D, "the Frontier already granted a share of something");
        double previous = -1.0D;
        for (int tier = 0; tier < 5000; tier++) {
            double share = ElysiumWorldTier.share(tier);
            check(share > previous || tier == 0, "the share stopped rising at tier " + tier);
            check(share < 1.0D, "the share reached certainty at tier " + tier);
            if (share >= 1.0D || (share <= previous && tier > 0)) {
                break;
            }
            previous = share;
        }
        check(Math.abs(ElysiumWorldTier.share((int) ElysiumWorldTier.SHARE_PACE) - 0.5D) < 1e-9,
                "the share is not half its limit at the pace, which is what the pace means");
        check(ElysiumWorldTier.share(-5) == 0.0D, "a negative tier produced a share");
        check(ElysiumWorldTier.share(4, 2.0D) > ElysiumWorldTier.share(4),
                "scaling a share did not raise it");
        check(ElysiumWorldTier.share(4, 0.0D) == 0.0D, "a scale of nothing still granted something");
        check(ElysiumWorldTier.share(4, -1.0D) == 0.0D, "a negative scale produced a share");
    }

    private static void gates() {
        check(ElysiumWorldTier.allows(0, 0), "a thing asking for nothing was refused to everyone");
        check(!ElysiumWorldTier.allows(1, 2), "a gate let through someone below it");
        check(ElysiumWorldTier.allows(2, 2) && ElysiumWorldTier.allows(9, 2),
                "a gate refused someone at or past it");
        check(ElysiumWorldTier.beyond(9, 2) == 7, "how far past a gate a player is was miscounted");
        check(ElysiumWorldTier.beyond(1, 2) == 0, "someone below a gate was counted as past it");
    }
}
