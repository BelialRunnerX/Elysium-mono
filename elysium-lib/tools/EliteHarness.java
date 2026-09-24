package com.elysium.lib.elite;

import java.util.Random;

/**
 * Who the world promotes, executed.
 *
 * A rank decides what a player meets, so the claims worth executing are the
 * ones about how often: that the Frontier promotes nobody, that neither rank
 * ever becomes the common case, and that an invader stays rarer than an elite
 * however deep the world goes.
 */
public final class EliteHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    public static void main(String[] args) {
        chances();
        rolling();
        augments();
        spoils();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: three ranks, neither common, an invader always "
                + "rarer than an elite, and one more augment every "
                + ElysiumEliteRules.TIERS_PER_EXTRA_AUGMENT + " tiers");
    }

    private static void chances() {
        check(ElysiumEliteRules.eliteChance(0) == 0.0D, "the Frontier promoted somebody");
        check(ElysiumEliteRules.invaderChance(0) == 0.0D, "the Frontier produced an invader");
        for (int tier = 0; tier < 2000; tier++) {
            double elite = ElysiumEliteRules.eliteChance(tier);
            double invader = ElysiumEliteRules.invaderChance(tier);
            check(elite < 1.0D, "every mob became an elite at world tier " + tier);
            check(invader < elite || tier == 0,
                    "an invader was as likely as an elite at world tier " + tier);
            check(tier == 0 || elite > ElysiumEliteRules.eliteChance(tier - 1),
                    "the elite chance stopped rising at world tier " + tier);
        }
        check(ElysiumEliteRules.eliteChance(1_000_000) < ElysiumEliteRules.ELITE_SHARE,
                "the elite chance passed the share it is a share of");
        check(ElysiumEliteRules.eliteChance(-4) == 0.0D, "a negative tier promoted somebody");
    }

    private static void rolling() {
        check(ElysiumEliteRules.roll(0, () -> 0.0D) == ElysiumEliteRules.Rank.ORDINARY,
                "a perfect roll at the Frontier produced a rank");
        check(ElysiumEliteRules.roll(40, () -> 0.0D) == ElysiumEliteRules.Rank.INVADER,
                "a perfect roll produced no invader at all");
        check(ElysiumEliteRules.roll(40, () -> 0.999999D) == ElysiumEliteRules.Rank.ORDINARY,
                "a failed roll produced a rank anyway");
        Random random = new Random(20260923L);
        int elites = 0;
        int invaders = 0;
        for (int i = 0; i < 200_000; i++) {
            switch (ElysiumEliteRules.roll(4, random::nextDouble)) {
                case ELITE -> elites++;
                case INVADER -> invaders++;
                default -> { }
            }
        }
        check(elites + invaders < 40_000,
                "more than a fifth of everything at the last named tier was promoted; a world "
                        + "where every creature is an elite has no elites in it");
        check(elites > 0 && invaders > 0, "nothing was promoted at all in 200,000 spawns");
        check(invaders < elites / 4, "invaders came up " + invaders + " times against "
                + elites + " elites, which is not rare");
    }

    private static void augments() {
        check(ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ORDINARY, 40) == 0,
                "an ordinary mob carried an augment");
        check(ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, 0)
                        == ElysiumEliteRules.ELITE_AUGMENTS,
                "an elite at the first tier did not carry what an elite carries");
        check(ElysiumEliteRules.augments(ElysiumEliteRules.Rank.INVADER, 0)
                        > ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, 0),
                "an invader was no stranger than an elite");
        check(ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, 40)
                        > ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, 4),
                "the deep tiers produced no stranger creatures than the shallow ones");
        for (int tier = 0; tier < 400; tier++) {
            check(ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, tier)
                            <= ElysiumEliteRules.augments(ElysiumEliteRules.Rank.ELITE, tier + 1),
                    "the augment count fell at world tier " + tier);
        }
    }

    private static void spoils() {
        check(ElysiumEliteRules.lootMultiplier(ElysiumEliteRules.Rank.ORDINARY) == 1.0D,
                "an ordinary kill paid more or less than an ordinary kill");
        check(ElysiumEliteRules.lootMultiplier(ElysiumEliteRules.Rank.INVADER)
                        > ElysiumEliteRules.lootMultiplier(ElysiumEliteRules.Rank.ELITE),
                "an invader paid no better than an elite, so its extra weight is a tax");
        check(ElysiumEliteRules.nameKey(ElysiumEliteRules.Rank.ORDINARY).isEmpty(),
                "an ordinary creature has a rank to announce");
        check(!ElysiumEliteRules.nameKey(ElysiumEliteRules.Rank.INVADER).isEmpty(),
                "an invader arrives unannounced");
    }
}
