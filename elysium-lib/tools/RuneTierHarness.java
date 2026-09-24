package com.elysium.lib.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import java.util.List;

/**
 * Rune tiers, executed at every tier there is.
 *
 * <h2>What has to be true</h2>
 *
 * <ol>
 *   <li><b>The rule, exactly.</b> Tier N does N times tier 1: tier 3 is three
 *       times, tier 4 four times — asked of every tier, not sampled.</li>
 *   <li><b>Merging is honest.</b> Only two of the same tier merge; the result
 *       is one tier up; a tier-N rune costs 2^(N-1) base runes and that count
 *       never overflows into something cheap.</li>
 *   <li><b>Nothing is capped.</b> Every potion level is N times, Resistance
 *       included, and saturates only at vanilla's byte rather than
 *       wrapping round to a weak effect.</li>
 *   <li><b>Old gear is tier 1.</b> A piece socketed before tiers existed
 *       decodes with every rune at tier 1, and a new rune added beside old
 *       ones lands in its own socket.</li>
 * </ol>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/rt elysium-lib/tools/RuneTierHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/rt" \
 *         com.elysium.lib.item.RuneTierHarness
 */
public final class RuneTierHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static void theRule() {
        float base = ElysiumRuneTiers.multiplier(ElysiumRuneTiers.BASE);
        check(base == 1.0F, "tier 1 is " + base + "x the base; it is the base");
        for (int n = 1; n <= ElysiumRuneTiers.MAX_TIER; n++) {
            check(ElysiumRuneTiers.multiplier(n) == n * base,
                    "tier " + n + " is " + ElysiumRuneTiers.multiplier(n)
                            + "x the base; the rule is exactly " + n + "x");
        }
        // The two the rule was stated with.
        check(ElysiumRuneTiers.multiplier(3) == 3.0F, "tier 3 is not three times tier 1");
        check(ElysiumRuneTiers.multiplier(4) == 4.0F, "tier 4 is not four times tier 1");

        for (int nonsense : new int[] {Integer.MIN_VALUE, -7, -1, 0}) {
            check(ElysiumRuneTiers.multiplier(nonsense) == 1.0F,
                    "a stored tier of " + nonsense + " is not read as the base");
        }
        check(ElysiumRuneTiers.multiplier(Integer.MAX_VALUE)
                        == ElysiumRuneTiers.MAX_TIER,
                "a huge stored tier is not held at the top");
    }

    private static void merging() {
        for (int a = -2; a <= ElysiumRuneTiers.MAX_TIER + 2; a++) {
            for (int b = -2; b <= ElysiumRuneTiers.MAX_TIER + 2; b++) {
                boolean can = ElysiumRuneTiers.canMerge(a, b);
                if (a != b) {
                    check(!can, "tiers " + a + " and " + b + " merged; only a matched pair may");
                }
                if (can) {
                    check(ElysiumRuneTiers.merged(a) == a + 1,
                            "two tier " + a + " made tier " + ElysiumRuneTiers.merged(a));
                }
            }
        }
        check(!ElysiumRuneTiers.canMerge(ElysiumRuneTiers.MAX_TIER, ElysiumRuneTiers.MAX_TIER),
                "the top tier merged past the top");
        check(ElysiumRuneTiers.canMerge(1, 1), "two base runes cannot merge");

        // Climb by actually merging, counting base runes, and compare.
        for (int target = 1; target <= 20; target++) {
            long spent = climb(target);
            check(spent == ElysiumRuneTiers.baseRunesIn(target),
                    "reaching tier " + target + " by merging took " + spent
                            + " base runes; baseRunesIn says "
                            + ElysiumRuneTiers.baseRunesIn(target));
        }
        long last = 0;
        for (int t = 1; t <= ElysiumRuneTiers.MAX_TIER; t++) {
            long cost = ElysiumRuneTiers.baseRunesIn(t);
            check(cost > last, "tier " + t + " costs " + cost
                    + ", no more than the tier below — the price overflowed");
            last = cost;
        }
    }

    /** Base runes to make one rune of this tier, by merging pairs. */
    private static long climb(int target) {
        if (target <= 1) {
            return 1;
        }
        return climb(target - 1) * 2;
    }

    private static void potions() {
        for (int baseLevel = 1; baseLevel <= 2; baseLevel++) {
            int last = -1;
            for (int n = 1; n <= ElysiumRuneTiers.MAX_TIER; n++) {
                int amp = ElysiumRuneTiers.amplifier(baseLevel, n);
                check(amp >= 0 && amp <= ElysiumRuneTiers.MAX_AMPLIFIER,
                        "level " + baseLevel + " at tier " + n + " gave amplifier " + amp);
                long wanted = Math.min(ElysiumRuneTiers.MAX_AMPLIFIER,
                        (long) baseLevel * n - 1L);
                check(amp == wanted, "level " + baseLevel + " at tier " + n
                        + " is level " + (amp + 1) + ", not " + (baseLevel * n));
                check(amp >= last, "potion level fell at tier " + n);
                last = amp;

            }
        }
        // Uncapped, by the owner's instruction: the level keeps climbing past
        // anything a design might have wanted to stop at. Resistance V is
        // immunity, and an aligned tier-3 Voidward reaches level VI.
        check(ElysiumRuneTiers.amplifier(2, 3) + 1 == 6,
                "an aligned tier-3 rune is level " + (ElysiumRuneTiers.amplifier(2, 3) + 1)
                        + ", not VI — something is capping potion levels");
        check(ElysiumRuneTiers.amplifier(1, 20) + 1 == 20,
                "a tier-20 rune is not level XX — something is capping potion levels");
        check(ElysiumRuneTiers.amplifier(Integer.MAX_VALUE, Integer.MAX_VALUE)
                        == ElysiumRuneTiers.MAX_AMPLIFIER,
                "the largest potion level wrapped");
        // Resistance: a percentage, so stat / (stat + b). Tier 1 is exactly the
        // level it always was; every tier climbs; level V — all damage — is
        // never reached; and it does reach IV, so nothing caps it at III.
        int lastLevel = 0;
        boolean reachedFour = false;
        for (int base = 1; base <= 2; base++) {
            lastLevel = 0;
            for (int n = 1; n <= ElysiumRuneTiers.MAX_TIER; n++) {
                int level = ElysiumRuneTiers.percentileAmplifier(base, n,
                        ElysiumRuneTiers.RESISTANCE_PER_LEVEL) + 1;
                check(level < 5, "Resistance reached level " + level + " at tier " + n
                        + " — level V is all damage, which a finite stat never is");
                check(level >= lastLevel, "Resistance fell at tier " + n);
                reachedFour |= level == 4;
                lastLevel = level;
            }
        }
        check(reachedFour, "Resistance never passes level III: something is capping it");
        // Tier 1 is the base stat at the owner's pace ("scale really hard":
        // ElysiumShares.PACE), so its level is whatever that share buys —
        // computed here the long way, not read back off the function.
        for (int base = 1; base <= 2; base++) {
            float share = com.elysium.lib.stats.ElysiumShares.scaled(
                    base * ElysiumRuneTiers.RESISTANCE_PER_LEVEL,
                    com.elysium.lib.stats.ElysiumShares.PACE);
            int wanted = (int) Math.floor(share / ElysiumRuneTiers.RESISTANCE_PER_LEVEL + 1.0e-4D);
            check(ElysiumRuneTiers.percentileAmplifier(base, 1,
                            ElysiumRuneTiers.RESISTANCE_PER_LEVEL) + 1 == wanted,
                    "tier-1 Voidward (base level " + base + ") is not the level its "
                            + "paced share buys, " + wanted);
        }

        // Unaligned tier 1 is level I, aligned tier 1 level II: as before tiers.
        check(ElysiumRuneTiers.amplifier(1, 1) == 0 && ElysiumRuneTiers.amplifier(2, 1) == 1,
                "tier 1 runes no longer give the levels they always did");
    }

    private static void oldGearIsTierOne() {
        // A save from before tiers: runes, no rune_tiers field.
        JsonObject old = new JsonObject();
        JsonArray runes = new JsonArray();
        runes.add("elysium:voidward");
        runes.add("elysium:reflex");
        old.add("runes", runes);
        old.addProperty("ascended_tier", 2);
        ElysiumGearData read = ElysiumGearData.CODEC.parse(JsonOps.INSTANCE, old)
                .result().orElse(null);
        check(read != null, "a pre-tier save did not decode at all");
        if (read == null) {
            return;
        }
        check(read.runes().size() == 2 && read.tierAt(0) == 1 && read.tierAt(1) == 1,
                "a pre-tier save's runes are not tier 1");
        check(read.tierAt(-1) == 1 && read.tierAt(99) == 1,
                "a socket that does not exist reads as a tier other than 1");

        // A tier-3 rune added beside two old ones lands in its own socket.
        ElysiumGearData added = read.withRuneAdded("elysium:barrier", 3);
        check(added.tierAt(0) == 1 && added.tierAt(1) == 1 && added.tierAt(2) == 3,
                "a tier-3 rune added beside two old ones read as "
                        + added.tierAt(0) + "," + added.tierAt(1) + "," + added.tierAt(2));

        // Nothing else that rewrites gear data may drop tiers.
        check(added.withAscendedTier(5).tierAt(2) == 3,
                "ascending the gear forgot its rune's tier");
        check(added.withReforgedStats(1, 1, 1).tierAt(2) == 3,
                "reforging the gear forgot its rune's tier");

        // And the round trip.
        ElysiumGearData back = ElysiumGearData.CODEC.parse(JsonOps.INSTANCE,
                ElysiumGearData.CODEC.encodeStart(JsonOps.INSTANCE, added)
                        .result().orElseThrow()).result().orElse(null);
        check(added.equals(back), "tiered gear did not survive a save and load");

        // A hand-edited tier is made sane on read.
        ElysiumGearData silly = new ElysiumGearData(List.of("a", "b", "c"), 0, 0, 0,
                -1, 0, List.of(0, -40, 99999));
        check(silly.tierAt(0) == 1 && silly.tierAt(1) == 1
                        && silly.tierAt(2) == ElysiumRuneTiers.MAX_TIER,
                "a hand-edited tier was not clamped");
    }

    private static void numerals() {
        check("I".equals(ElysiumRuneTiers.numeral(1)), "tier 1 is not I");
        check("III".equals(ElysiumRuneTiers.numeral(3)), "tier 3 is not III");
        check("X".equals(ElysiumRuneTiers.numeral(10)), "tier 10 is not X");
        check("11".equals(ElysiumRuneTiers.numeral(11)), "tier 11 is not written as 11");
        check("I".equals(ElysiumRuneTiers.numeral(-3)), "a nonsense tier did not read as I");
    }

    public static void main(String[] args) {
        theRule();
        merging();
        potions();
        oldGearIsTierOne();
        numerals();
        if (failures > 0) {
            System.out.println(failures + " of " + checked + " checks failed");
            System.exit(1);
        }
        System.out.println(checked + " checks: tier N is exactly Nx at all "
                + ElysiumRuneTiers.MAX_TIER + " tiers, tier 5 costs "
                + ElysiumRuneTiers.baseRunesIn(5) + " base runes, nothing capped short of "
                + "vanilla's byte, old gear reads as tier 1");
    }
}
