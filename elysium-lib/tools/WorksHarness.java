package com.elysium.lib.works;

import com.elysium.lib.doctrine.ElysiumDoctrine;

/**
 * Unregistered works, executed across the ways people actually build.
 *
 * <h2>What has to be true</h2>
 *
 * Two things pull against each other, and the numbers are only right if both
 * hold at once:
 *
 * <ol>
 *   <li><b>Ordinary building is never noticed.</b> A house, a kitchen, a small
 *       workshop put up over an evening. If the survey finds those, the
 *       feature is a tax on playing the game, and a player will read it as a
 *       bug.</li>
 *   <li><b>Industry is.</b> A factory put down in a hurry is found, found again
 *       sooner if it keeps growing, and — unlike a claim — can push its builder
 *       past the seventy a registered base never exceeds. Without that last
 *       part, registering is simply worse, and the choice is not a choice.</li>
 * </ol>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/wh elysium-lib/tools/WorksHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/wh" \
 *         com.elysium.lib.works.WorksHarness
 */
public final class WorksHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static final long MINUTE = ElysiumConspicuity.TICKS_PER_MINUTE;

    /** The claim cap, inlined by javac: reading it loads no Minecraft class. */
    private static final int CLAIM_CAP = com.elysium.lib.claim.ElysiumClaim.MAX_FLOOR;

    /** Builds {@code count} blocks of {@code weight}, {@code gap} ticks apart. Returns total Suspicion and notice count. */
    private static int[] build(int count, int weight, long gap) {
        ElysiumSite site = ElysiumSite.quiet(0, 0, 0L);
        long now = 0L;
        int suspicion = 0;
        int notices = 0;
        for (int i = 0; i < count; i++) {
            ElysiumSite.Placed placed = site.placed(weight, now);
            if (placed.noticed()) {
                suspicion += placed.suspicion();
                notices++;
            }
            site = placed.site();
            now += gap;
        }
        return new int[] {suspicion, notices};
    }

    // ------------------------------------------------------------------

    private static void ordinaryBuildingIsNeverNoticed() {
        // A kitchen: furnace, smoker, blast furnace, an anvil, all at once.
        check(build(4, ElysiumConspicuity.WEIGHT_CONSPICUOUS, 0L)[1] == 0,
                "a kitchen of four conspicuous blocks was noticed");

        // A furnace bank of twenty, at once, is still not a factory.
        check(build(20, ElysiumConspicuity.WEIGHT_CONSPICUOUS, 0L)[1] == 0,
                "twenty furnaces placed together were noticed");

        // Eleven machines at once: one short.
        check(build(11, ElysiumConspicuity.WEIGHT_MACHINE, 0L)[1] == 0,
                "eleven machines placed together were noticed; the line is twelve");

        // An evening's workshop: six machines, one every two minutes. The
        // realistic burst — and the one that actually tests the threshold.
        // (The steady case below balances decay exactly, so the site never
        // passes ten whatever the line is; on its own it passed with the line
        // dropped to thirty, which is how this case came to exist.)
        int[] evening = build(6, ElysiumConspicuity.WEIGHT_MACHINE, 2 * MINUTE);
        check(evening[1] == 0,
                "an evening's workshop of six machines was noticed; the survey "
                        + "is for factories, and this is a shed");

        // But keep that pace up and it stops being a workshop. A machine every
        // two minutes, sustained, must be found — by the thirtieth at the
        // latest, an hour of doing nothing but putting down machines.
        check(build(30, ElysiumConspicuity.WEIGHT_MACHINE, 2 * MINUTE)[1] >= 1,
                "thirty machines over an hour were never noticed; a sustained "
                        + "build is a factory, and the survey never came");

        // A workshop grown at leisure: a machine every five minutes, for
        // as long as anyone cares to keep going. Decay must keep up.
        for (int machines = 1; machines <= 500; machines++) {
            int[] result = build(machines, ElysiumConspicuity.WEIGHT_MACHINE,
                    5 * MINUTE);
            if (result[1] != 0) {
                check(false, "a machine every five minutes was noticed after "
                        + machines + "; unhurried building must be free forever");
                break;
            }
        }
        check(true, "");
    }

    private static void industryIsNoticed() {
        int[] rush = build(12, ElysiumConspicuity.WEIGHT_MACHINE, 0L);
        check(rush[1] == 1 && rush[0] == ElysiumConspicuity.FIRST_NOTICE,
                "twelve machines at once gave " + rush[1] + " notice(s) and "
                        + rush[0] + " Suspicion; expected exactly one, of "
                        + ElysiumConspicuity.FIRST_NOTICE);

        // Forty conspicuous blocks at once is the same threshold.
        check(build(40, ElysiumConspicuity.WEIGHT_CONSPICUOUS, 0L)[1] == 1,
                "forty furnaces at once were not noticed exactly once");

        // A second notice comes sooner than the first did.
        ElysiumSite site = ElysiumSite.quiet(0, 0, 0L);
        int first = -1;
        int second = -1;
        for (int i = 1; i <= 100 && second < 0; i++) {
            ElysiumSite.Placed placed = site.placed(ElysiumConspicuity.WEIGHT_MACHINE, 0L);
            site = placed.site();
            if (placed.noticed()) {
                if (first < 0) {
                    first = i;
                } else {
                    second = i - first;
                }
            }
        }
        check(first > 0 && second > 0 && second < first,
                "the second notice took " + second + " machines against the "
                        + "first's " + first + "; a works that keeps growing "
                        + "must be found sooner, not at the same pace");
    }

    /** The whole point: unregistered is not simply better than registered. */
    private static void unregisteredCanExceedTheClaimCap() {
        int[] factory = build(60, ElysiumConspicuity.WEIGHT_MACHINE, 0L);
        check(factory[0] > CLAIM_CAP,
                "sixty machines built in a rush cost " + factory[0]
                        + " Suspicion, which is inside the " + CLAIM_CAP
                        + " a claim can never exceed. Then nobody should ever "
                        + "register, and the choice is not one");
    }

    private static void escalationIsMonotoneAndCapped() {
        int previous = 0;
        for (int prior = -3; prior <= 10_000; prior++) {
            int cost = ElysiumConspicuity.suspicionFor(prior);
            check(cost > 0, "a notice after " + prior + " cost nothing");
            check(cost <= ElysiumConspicuity.MOST_PER_NOTICE,
                    "a notice after " + prior + " cost " + cost + ", over the cap");
            check(cost >= previous, "escalation fell at " + prior);
            previous = cost;
        }
        check(ElysiumConspicuity.suspicionFor(Integer.MAX_VALUE)
                        == ElysiumConspicuity.MOST_PER_NOTICE,
                "a huge notice count overflowed the cost");
    }

    private static void onePlacementIsOneNotice() {
        for (int weight : new int[] {ElysiumConspicuity.NOTICE_AT,
                ElysiumConspicuity.NOTICE_AT * 5, Integer.MAX_VALUE}) {
            ElysiumSite.Placed placed = ElysiumSite.quiet(0, 0, 0L).placed(weight, 0L);
            check(placed.suspicion() == ElysiumConspicuity.FIRST_NOTICE
                            && placed.site().notices() == 1,
                    "one placement of weight " + weight + " was noticed as "
                            + placed.site().notices() + " incident(s)");
            check(placed.site().weight() == ElysiumConspicuity.RESIDUAL,
                    "a notice left " + placed.site().weight() + ", not the residual");
        }
        // Weight zero changes nothing, even at a loud site.
        ElysiumSite loud = new ElysiumSite(0, 0, 100, 0L, 2);
        ElysiumSite.Placed wall = loud.placed(0, 0L);
        check(!wall.noticed() && wall.site().weight() == 100,
                "a wall moved a works");
    }

    private static void decayIsSane() {
        for (int weight : new int[] {0, 1, 7, 119, 120, 5000, Integer.MAX_VALUE}) {
            int last = weight;
            for (long t : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, MINUTE,
                    60 * MINUTE, 1_000_000L * MINUTE, Long.MAX_VALUE}) {
                int left = ElysiumConspicuity.decayed(weight, t);
                check(left >= 0, "decay of " + weight + " over " + t + " went negative");
                check(left <= Math.max(0, weight),
                        "decay of " + weight + " over " + t + " grew the site");
                if (t <= 0) {
                    check(left == Math.max(0, weight),
                            "no time (or negative time) changed a site of " + weight);
                } else {
                    check(left <= last, "decay was not monotone at " + t);
                    last = left;
                }
            }
        }
        // A timestamp from a corrupted save: the subtraction itself wraps.
        ElysiumSite ancient = new ElysiumSite(0, 0, 500, Long.MIN_VALUE, 3);
        check(ancient.at(1_000L).weight() == 0,
                "a site stamped at the start of time is still loud");
        ElysiumSite future = new ElysiumSite(0, 0, 50, Long.MAX_VALUE, 1);
        check(future.at(0L).weight() == 50,
                "a site stamped in the future changed weight");
        check(ElysiumConspicuity.decayed(ElysiumConspicuity.NOTICE_AT, 60 * MINUTE) == 0,
                "a works left an hour did not go quiet");
    }

    private static void goingQuietForgivesEscalation() {
        ElysiumSite watched = new ElysiumSite(0, 0, 40, 0L, 5);
        check(watched.at(10 * MINUTE).notices() == 5,
                "a site still warm forgot its notices");
        check(watched.at(100 * MINUTE).notices() == 0,
                "a site gone quiet kept its notices — a grudge against an empty field");
    }

    private static void saturation() {
        check(ElysiumConspicuity.added(Integer.MAX_VALUE, Integer.MAX_VALUE)
                == Integer.MAX_VALUE, "adding weight wrapped");
        check(ElysiumConspicuity.added(-50, 10) == 10, "a negative site stayed negative");
        check(ElysiumConspicuity.added(10, -50) == 10, "a negative placement lowered a site");
    }

    private static void cells() {
        check(ElysiumConspicuity.cellOf(-1) == -1, "block -1 folded into cell 0");
        check(ElysiumConspicuity.cellOf(0) == 0 && ElysiumConspicuity.cellOf(127) == 0,
                "cell 0 does not span 0..127");
        check(ElysiumConspicuity.cellOf(128) == 1, "block 128 is not in cell 1");
        check(ElysiumConspicuity.cellOf(-128) == -1 && ElysiumConspicuity.cellOf(-129) == -2,
                "negative cells are misaligned");
        check(ElysiumSite.key(1, -1) != ElysiumSite.key(-1, 1)
                        && ElysiumSite.key(0, -1) != ElysiumSite.key(-1, 0),
                "two sites share a key");
        ElysiumSite far = ElysiumSite.quiet(-3, 7, 0L);
        check(ElysiumConspicuity.cellOf(far.centreX()) == -3
                        && ElysiumConspicuity.cellOf(far.centreZ()) == 7,
                "a site's centre is outside the site");
    }

    private static void doctrine() {
        for (float scale : new float[] {Float.NaN, -1.0F, 0.0F, 0.01F, 0.6F, 1.0F, 1.35F, 1e30F}) {
            for (int weight : new int[] {0, 1, 3, 10}) {
                int scaled = ElysiumConspicuity.scaled(weight, scale);
                if (weight == 0) {
                    check(scaled == 0, "doctrine turned a wall into noise at " + scale);
                } else {
                    check(scaled >= 1, "doctrine " + scale + " made weight " + weight + " free");
                }
            }
        }
        check(ElysiumConspicuity.scaled(10, 1.0F) == 10, "a neutral doctrine changed weight");

        // Clandestine builds more before it is found; Sanctioned less.
        float quiet = ElysiumDoctrine.CLANDESTINE.suspicionMultiplier();
        float loud = ElysiumDoctrine.SANCTIONED.suspicionMultiplier();
        int plain = firstNotice(ElysiumConspicuity.WEIGHT_MACHINE);
        int hidden = firstNotice(ElysiumConspicuity.scaled(ElysiumConspicuity.WEIGHT_MACHINE, quiet));
        int open = firstNotice(ElysiumConspicuity.scaled(ElysiumConspicuity.WEIGHT_MACHINE, loud));
        check(hidden > plain && open < plain,
                "Clandestine found after " + hidden + ", neutral " + plain
                        + ", Sanctioned " + open + "; the doctrines do not order");
    }

    private static int firstNotice(int weight) {
        ElysiumSite site = ElysiumSite.quiet(0, 0, 0L);
        for (int i = 1; i <= 10_000; i++) {
            ElysiumSite.Placed placed = site.placed(weight, 0L);
            if (placed.noticed()) {
                return i;
            }
            site = placed.site();
        }
        return Integer.MAX_VALUE;
    }

    private static void warningIsVisibleAndOnce() {
        ElysiumSite site = ElysiumSite.quiet(0, 0, 0L);
        int warnings = 0;
        boolean warnedBeforeNotice = false;
        for (int i = 0; i < 12; i++) {
            int before = site.weight();
            ElysiumSite.Placed placed = site.placed(ElysiumConspicuity.WEIGHT_MACHINE, 0L);
            if (!placed.noticed() && ElysiumConspicuity.warns(before, placed.site().weight())) {
                warnings++;
                warnedBeforeNotice = true;
            }
            if (placed.noticed()) {
                check(warnedBeforeNotice,
                        "the first notice arrived with no warning before it");
            }
            site = placed.site();
        }
        check(warnings == 1, "a rush to the first notice warned " + warnings + " times");
    }

    public static void main(String[] args) {
        ordinaryBuildingIsNeverNoticed();
        industryIsNoticed();
        unregisteredCanExceedTheClaimCap();
        escalationIsMonotoneAndCapped();
        onePlacementIsOneNotice();
        decayIsSane();
        goingQuietForgivesEscalation();
        saturation();
        cells();
        doctrine();
        warningIsVisibleAndOnce();

        if (failures > 0) {
            System.out.println(failures + " of " + checked + " checks failed");
            System.exit(1);
        }
        System.out.println(checked + " checks: ordinary building free forever, "
                + "a rush found at " + firstNotice(ElysiumConspicuity.WEIGHT_MACHINE)
                + " machines, a sixty-machine factory past the claim cap of "
                + CLAIM_CAP + " at " + build(60, ElysiumConspicuity.WEIGHT_MACHINE, 0L)[0]);
    }
}
