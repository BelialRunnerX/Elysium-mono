package com.elysium.lib.stress;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The stress model, executed against every combination of reasons there is.
 *
 * <h2>Why exhaustively and not by example</h2>
 *
 * Twelve stressors is four thousand and ninety-six villagers. Every one of them
 * is a state a player can actually produce — there is no combination in here
 * that requires a debug command — and the ones that would break the model are
 * the unlikely ones: the villager who is homeless, unemployed, injured,
 * threatened, standing in lava <em>and</em> next to a friend. A hand-picked
 * example set would contain none of them, and the bug they expose is the one
 * where relief pushes a total negative and the band wraps round to Stable.
 *
 * <h2>What is worth asserting</h2>
 *
 * <ul>
 *   <li>the bands are a partition — every total lands in exactly one, and
 *       adding a burden can never make somebody calmer;</li>
 *   <li>relief can never bank: no set of reasons produces a negative total,
 *       so being happy today cannot pay for a disaster tomorrow;</li>
 *   <li>Recovery is never reachable by getting worse, and never sticks;</li>
 *   <li>the explanation always adds up to the number it explains.</li>
 * </ul>
 *
 * The last is the one the whole design exists for and the one nothing else in
 * the codebase would notice breaking.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/sh elysium-lib/tools/StressHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/sh" \
 *         com.elysium.lib.stress.StressHarness
 */
public final class StressHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Every subset of the stressors. Four thousand-odd villagers. */
    private static List<Set<ElysiumStressor>> everyCombination() {
        ElysiumStressor[] all = ElysiumStressor.values();
        List<Set<ElysiumStressor>> out = new ArrayList<>();
        for (int mask = 0; mask < (1 << all.length); mask++) {
            EnumSet<ElysiumStressor> set = EnumSet.noneOf(ElysiumStressor.class);
            for (int i = 0; i < all.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    set.add(all[i]);
                }
            }
            out.add(set);
        }
        return out;
    }

    /** No combination of relief ever produces a negative total. */
    private static void reliefCannotBank() {
        for (Set<ElysiumStressor> reasons : everyCombination()) {
            int total = ElysiumStress.total(reasons);
            check(total >= 0,
                    "a villager totalled " + total + " from " + reasons
                            + "; relief banking against a future disaster means "
                            + "a happy village absorbs one for free");
        }
        EnumSet<ElysiumStressor> allRelief = EnumSet.noneOf(ElysiumStressor.class);
        for (ElysiumStressor reason : ElysiumStressor.values()) {
            if (reason.relieves()) {
                allRelief.add(reason);
            }
        }
        check(ElysiumStress.total(allRelief) == 0,
                "every comfort at once totals "
                        + ElysiumStress.total(allRelief) + ", not zero");
    }

    /**
     * Comfort softens a bad situation and never erases one.
     *
     * Added because a negative test made every comfort enormous and nothing
     * complained: the floor at zero absorbed it, so the model stayed
     * self-consistent while a villager at home became immune to anything that
     * could happen to them. The floor was guarding arithmetic; this guards the
     * thing the arithmetic is for.
     *
     * Stated as "less than the two heaviest burdens", which is a design claim
     * worth making out loud: a good home can carry somebody through one awful
     * day and cannot make two of them invisible.
     */
    private static void comfortCannotEraseEverything() {
        int relief = 0;
        List<Integer> burdens = new ArrayList<>();
        for (ElysiumStressor reason : ElysiumStressor.values()) {
            if (reason.relieves()) {
                relief += -reason.weight();
            } else {
                burdens.add(reason.weight());
            }
        }
        burdens.sort((one, two) -> Integer.compare(two, one));
        int worstTwo = burdens.get(0) + burdens.get(1);
        System.out.println("  comfort: every relief together is " + relief
                + " against the two heaviest burdens at " + worstTwo);
        check(relief < worstTwo,
                "every comfort together is worth " + relief + " and the two "
                        + "heaviest things that can happen are worth " + worstTwo
                        + ". A villager with a good home would be immune to "
                        + "both at once, and the memory system would stop "
                        + "mattering to anybody who had built a village well");
        check(relief >= burdens.get(burdens.size() - 1),
                "every comfort together is worth less than the mildest single "
                        + "burden, so building a good village does nothing");
    }

    /** Adding a burden never makes somebody calmer, and removing one never worse. */
    private static void theModelIsMonotonic() {
        for (Set<ElysiumStressor> reasons : everyCombination()) {
            int before = ElysiumStress.total(reasons);
            for (ElysiumStressor added : ElysiumStressor.values()) {
                if (reasons.contains(added)) {
                    continue;
                }
                EnumSet<ElysiumStressor> more = EnumSet.copyOf(reasons.isEmpty()
                        ? EnumSet.noneOf(ElysiumStressor.class) : reasons);
                more.add(added);
                int after = ElysiumStress.total(more);
                if (added.relieves()) {
                    check(after <= before,
                            added + " made things worse: " + before + " to " + after);
                } else {
                    check(after >= before,
                            added + " made things better: " + before + " to " + after);
                }
            }
        }
    }

    /** Every total lands in exactly one band, and the bands are ordered. */
    private static void bandsPartition() {
        ElysiumStressBand[] scale = ElysiumStressBand.scale();
        for (int i = 1; i < scale.length; i++) {
            check(scale[i - 1].threshold() > scale[i].threshold(),
                    scale[i - 1] + " is not above " + scale[i] + " on the scale");
        }
        int worst = 0;
        for (ElysiumStressor reason : ElysiumStressor.values()) {
            if (!reason.relieves()) {
                worst += reason.weight();
            }
        }
        for (int total = 0; total <= worst + 50; total++) {
            ElysiumStressBand band = ElysiumStressBand.of(total);
            check(band != ElysiumStressBand.RECOVERY,
                    "a bare total of " + total + " produced Recovery, which is a "
                            + "direction and not a level");
            check(total >= band.threshold(),
                    total + " was placed in " + band + ", which starts at "
                            + band.threshold());
            for (ElysiumStressBand higher : scale) {
                if (higher.threshold() > band.threshold()) {
                    check(total < higher.threshold(),
                            total + " was placed in " + band + " but has reached "
                                    + higher);
                }
            }
        }
        System.out.println("  bands: worst possible villager totals " + worst
                + " (" + ElysiumStressBand.of(worst) + "), crisis begins at "
                + ElysiumStressBand.CRISIS.threshold());
        check(ElysiumStressBand.of(worst) == ElysiumStressBand.CRISIS,
                "the worst possible villager is only "
                        + ElysiumStressBand.of(worst) + "; crisis is unreachable");
    }

    /** Crisis has to be reachable without stacking every misfortune at once. */
    private static void crisisIsReachableButNotCommon() {
        int reachedBy = Integer.MAX_VALUE;
        for (Set<ElysiumStressor> reasons : everyCombination()) {
            if (ElysiumStress.total(reasons)
                    >= ElysiumStressBand.CRISIS.threshold()) {
                reachedBy = Math.min(reachedBy, reasons.size());
            }
        }
        System.out.println("  crisis: reachable with as few as " + reachedBy
                + " things going wrong at once");
        check(reachedBy >= 3,
                "crisis is reachable with only " + reachedBy + " thing(s) wrong, "
                        + "so an ordinary bad afternoon shuts the village");
        check(reachedBy <= 4,
                "crisis needs " + reachedBy + " things wrong at once, which no "
                        + "player will ever see happen");
    }

    /** The explanation always adds up to the number it explains. */
    private static void theReadingExplainsItself() {
        for (Set<ElysiumStressor> reasons : everyCombination()) {
            ElysiumStress.Reading reading =
                    ElysiumStress.read(reasons, null, Long.MAX_VALUE);
            int sum = 0;
            for (ElysiumStressor reason : reading.reasons()) {
                sum += reason.weight();
            }
            check(Math.max(0, sum) == reading.total(),
                    "a reading of " + reading.total() + " is explained by reasons "
                            + "totalling " + sum + "; the explanation and the "
                            + "number are supposed to be the same computation");
            check(reading.ordered().size() == reading.reasons().size(),
                    "ordering the reasons lost or gained one");
            ElysiumStressor worstOf = reading.worst();
            check(worstOf == null || !worstOf.relieves(),
                    "the worst thing wrong was reported as " + worstOf
                            + ", which is a comfort");
        }
    }

    /** Recovery only ever follows improvement, and always expires. */
    private static void recoveryIsADirection() {
        Set<ElysiumStressor> calm = EnumSet.of(ElysiumStressor.UNEMPLOYED);
        check(ElysiumStress.read(calm, ElysiumStressBand.CRISIS, 20L).band()
                        == ElysiumStressBand.RECOVERY,
                "a villager who was in crisis and is now nearly fine is not "
                        + "reported as recovering");
        check(ElysiumStress.read(calm, ElysiumStressBand.CRISIS,
                        ElysiumStress.RECOVERY_WINDOW + 1).band()
                        != ElysiumStressBand.RECOVERY,
                "recovery never expires, so everybody who ever had a bad day is "
                        + "permanently Recovering");
        check(ElysiumStress.read(calm, null, Long.MAX_VALUE).band()
                        != ElysiumStressBand.RECOVERY,
                "a villager with no history is recovering from nothing");

        // Getting worse is never recovery, at any peak and any delay.
        Set<ElysiumStressor> bad = EnumSet.of(ElysiumStressor.SAW_A_KILLING,
                ElysiumStressor.IN_A_HAZARD, ElysiumStressor.THREATENED);
        for (ElysiumStressBand peak : ElysiumStressBand.values()) {
            for (long since : new long[]{0L, 20L, ElysiumStress.RECOVERY_WINDOW}) {
                check(ElysiumStress.read(bad, peak, since).band()
                                != ElysiumStressBand.RECOVERY,
                        "a villager in " + ElysiumStressBand.of(
                                ElysiumStress.total(bad))
                                + " was called Recovering (peak " + peak
                                + ", " + since + " ticks ago)");
            }
        }
        // Being at the peak is not recovery either: you have not got better.
        check(ElysiumStress.read(bad, ElysiumStressBand.of(
                        ElysiumStress.total(bad)), 20L).band()
                        != ElysiumStressBand.RECOVERY,
                "standing still at your worst counts as recovering");
    }

    /** The peak only ever moves one way. */
    private static void thePeakOnlyRises() {
        for (ElysiumStressBand candidate : ElysiumStressBand.values()) {
            for (ElysiumStressBand peak : ElysiumStressBand.values()) {
                boolean rises = ElysiumStress.isNewPeak(candidate, peak);
                if (rises) {
                    check(candidate != ElysiumStressBand.RECOVERY,
                            "Recovery was recorded as a peak");
                    check(peak == null || peak == ElysiumStressBand.RECOVERY
                                    || candidate.atLeast(peak),
                            candidate + " replaced a worse peak of " + peak);
                }
            }
        }
        check(!ElysiumStress.isNewPeak(ElysiumStressBand.STABLE, null),
                "a villager with nothing wrong records a peak, so everybody in "
                        + "the world is always one bad moment from Recovering");
        check(ElysiumStress.isNewPeak(ElysiumStressBand.CRISIS,
                        ElysiumStressBand.STRAINED),
                "getting much worse does not update the peak");
        check(!ElysiumStress.isNewPeak(ElysiumStressBand.STRAINED,
                        ElysiumStressBand.CRISIS),
                "getting better overwrote the peak, which is what makes "
                        + "recovery visible at all");
    }

    /** Recovery is not on the scale, and nothing may treat it as a level. */
    private static void recoveryIsNotALevel() {
        for (ElysiumStressBand band : ElysiumStressBand.values()) {
            check(!ElysiumStressBand.RECOVERY.atLeast(band),
                    "Recovery counts as at least " + band);
            check(!band.atLeast(ElysiumStressBand.RECOVERY),
                    band + " counts as at least Recovery");
        }
        check(ElysiumStressBand.CRISIS.atLeast(ElysiumStressBand.DISTRESSED),
                "Crisis is not at least Distressed");
        check(!ElysiumStressBand.STABLE.atLeast(ElysiumStressBand.STRAINED),
                "Stable counts as at least Strained");
    }

    public static void main(String[] args) {
        reliefCannotBank();
        comfortCannotEraseEverything();
        theModelIsMonotonic();
        bandsPartition();
        crisisIsReachableButNotCommon();
        theReadingExplainsItself();
        recoveryIsADirection();
        thePeakOnlyRises();
        recoveryIsNotALevel();

        System.out.println("stress: " + ElysiumStressor.values().length
                + " reasons across " + (1 << ElysiumStressor.values().length)
                + " combinations, " + (ElysiumStressBand.values().length - 1)
                + " bands and a direction — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("the explanation is the number");
    }
}
