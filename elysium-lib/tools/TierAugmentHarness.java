package com.elysium.lib.tier;

/**
 * What a world tier is worth, executed.
 *
 * The one claim here that matters more than any number is the asymmetry: the
 * world's contribution converges and the player's does not. That is the whole
 * design of the tier, and it is one sign flip away from being the opposite.
 */
public final class TierAugmentHarness {

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
        theWorldConverges();
        thePlayerDoesNot();
        theFrontier();
        theLoop();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: mobs approach x"
                + (1.0D + ElysiumTierAugments.MOB_HEALTH_LIMIT) + " health and never pass it; "
                + "a player's augments climb forever; the Frontier gives nothing");
    }

    private static void theWorldConverges() {
        check(ElysiumTierAugments.mobHealth(0) == 1.0D, "a Frontier mob was already hardened");
        check(ElysiumTierAugments.mobDamage(0) == 1.0D, "a Frontier mob already hit harder");
        check(ElysiumTierAugments.mobArmour(0) == 1.0D, "a Frontier mob was already armoured");
        double previous = 0.0D;
        for (int tier = 0; tier < 5000; tier++) {
            double health = ElysiumTierAugments.mobHealth(tier);
            check(health < 1.0D + ElysiumTierAugments.MOB_HEALTH_LIMIT,
                    "mob health passed its limit at tier " + tier);
            check(tier == 0 || health > previous, "mob health stopped rising at tier " + tier);
            if (health >= 1.0D + ElysiumTierAugments.MOB_HEALTH_LIMIT) {
                break;
            }
            previous = health;
        }
        check(ElysiumTierAugments.mobDamage(1_000_000) < 1.0D + ElysiumTierAugments.MOB_DAMAGE_LIMIT,
                "mob damage passed its limit at an absurd tier");
        check(ElysiumTierAugments.mobArmour(1_000_000) < 1.0D + ElysiumTierAugments.MOB_ARMOUR_LIMIT,
                "mob armour passed its limit at an absurd tier");
        // Half the limit at the top of the named range, by construction: the
        // pace is four tiers. Stated as a check because it is the sentence the
        // design makes — most of the world's hardening happens past the tiers
        // that have names, where the players have long since outgrown them.
        check(ElysiumTierAugments.mobHealth(ElysiumWorldTier.MAX_NAMED) - 1.0D
                        <= ElysiumTierAugments.MOB_HEALTH_LIMIT * 0.5D + 1e-9,
                "more than half the world's hardening had already happened by the last "
                        + "named tier, so the deep tiers would add almost nothing");
        check(ElysiumTierAugments.mobHealth(ElysiumWorldTier.MAX_NAMED) > 1.5D,
                "the named tiers harden a mob so little that reaching them is not felt");
    }

    private static void thePlayerDoesNot() {
        check(ElysiumTierAugments.health(1000) == ElysiumTierAugments.HEALTH_PER_TIER * 1000,
                "the player's health augment curved instead of climbing");
        check(ElysiumTierAugments.luck(1000) == ElysiumTierAugments.LUCK_PER_TIER * 1000,
                "the player's luck augment curved instead of climbing");
        check(ElysiumTierAugments.health(400) > 1.0D + ElysiumTierAugments.MOB_HEALTH_LIMIT,
                "at a deep tier the player's augment is still smaller than the world's whole "
                        + "limit, which is the asymmetry the tier exists for");
        check(ElysiumTierAugments.health(-3) == 0.0D, "a negative tier took health away");
        check(ElysiumTierAugments.luck(-3) == 0.0D, "a negative tier took luck away");
        check(ElysiumTierAugments.experience(-3) == 0.0D, "a negative tier took experience away");
        for (int tier = 1; tier < 500; tier++) {
            check(ElysiumTierAugments.health(tier) > ElysiumTierAugments.health(tier - 1),
                    "the health augment stopped climbing at tier " + tier);
        }
    }

    private static void theFrontier() {
        check(!ElysiumTierAugments.augments(ElysiumWorldTier.FRONTIER),
                "the Frontier grants an augment, which is a bonus for existing");
        check(ElysiumTierAugments.augments(1), "the first tier past the Frontier grants nothing");
        check(ElysiumTierAugments.health(0) == 0.0D && ElysiumTierAugments.luck(0) == 0.0D,
                "the Frontier's augments are not nothing");
    }

    private static void theLoop() {
        // Luck raises rarity raises affixes. The loop has to be safe: more luck
        // always helps and never makes anything certain.
        double low = ElysiumLootRarity.base(4, ElysiumTierAugments.luck(4));
        double high = ElysiumLootRarity.base(4, ElysiumTierAugments.luck(40));
        check(high > low, "a deeper tier's luck did not improve what drops");
        check(ElysiumLootRarity.base(4, ElysiumTierAugments.luck(1_000_000)) < 1.0D,
                "the luck a tier grants made a better drop certain, which would close the loop "
                        + "on itself");
    }
}
