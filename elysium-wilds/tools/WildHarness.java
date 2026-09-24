package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link WildRules}, executed.
 *
 * <p>The claim that matters is the one that found a bug already shipped in
 * this pack: <b>a biome whose claim on the space sits inside an earlier
 * biome's claim never generates anywhere.</b> Not rarely — never. It is
 * registered, translated, listed in every compass, and it is not in the world.
 * Nothing warns, because nothing is wrong: every file is valid and every id
 * resolves.
 *
 * <p>So it is proved here as a theorem about the rule rather than as a fact
 * about one dimension file: ten thousand random nested pairs, swept over a
 * million climates, and the inner one wins nothing in any of them.
 */
public final class WildHarness {

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
        theMetric();
        theSwallowing();
        theShares();
        theWalk();
        theSearch();
        theSpacing();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: the game's own nearest-box rule, the "
                + "theorem that a claim inside an earlier claim never generates, shares "
                + "that add to one world, a walk that shortens as a biome gets commoner, "
                + "one search of the same size for anything however rare, and a spacing "
                + "a player actually meets");
    }

    // ==================================================================

    private static WildRules.Region box(String name, double half, double offset,
                                        Random random) {
        List<WildRules.Span> spans = new ArrayList<>(WildRules.AXES);
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            double middle = random.nextDouble() * 2.0D - 1.0D;
            spans.add(new WildRules.Span(middle - half, middle + half));
        }
        return new WildRules.Region(name, spans, offset);
    }

    /** A claim that is the whole space except on the two axes it names. */
    private static WildRules.Region world(String name, int firstAxis, double firstLow,
                                          double firstHigh, int secondAxis,
                                          double secondLow, double secondHigh) {
        List<WildRules.Span> spans = new ArrayList<>(WildRules.AXES);
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            if (axis == firstAxis) {
                spans.add(new WildRules.Span(firstLow, firstHigh));
            } else if (axis == secondAxis) {
                spans.add(new WildRules.Span(secondLow, secondHigh));
            } else {
                spans.add(new WildRules.Span(-1.0D, 1.0D));
            }
        }
        return new WildRules.Region(name, spans, 0.0D);
    }

    private static double[] climate(Random random) {
        double[] point = new double[WildRules.AXES];
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            point[axis] = random.nextDouble() * 2.0D - 1.0D;
        }
        return point;
    }

    private static void theMetric() {
        WildRules.Span span = new WildRules.Span(-0.25D, 0.25D);
        check(span.distance(0.0D) == 0.0D, "a value inside a span costs something");
        check(span.distance(0.25D) == 0.0D, "a value on the edge of a span costs "
                + "something, so a box does not include its own boundary");
        check(Math.abs(span.distance(0.75D) - 0.5D) < 1.0e-12,
                "a value outside a span is not its distance from the near edge");
        check(Math.abs(span.distance(-0.75D) - 0.5D) < 1.0e-12,
                "a value below a span is measured from the wrong edge");

        // The whole reason a wide claim beats a point: inside costs nothing,
        // so it is not merely likelier to win, it wins outright.
        Random random = new Random(918_273L);
        WildRules.Region wide = box("wide", 0.5D, 0.0D, new Random(4L));
        for (int round = 0; round < 200_000; round++) {
            double[] point = climate(random);
            double far = wide.distance(point);
            check(far >= 0.0D, "a box at a negative distance from a climate");
            boolean within = true;
            for (int axis = 0; axis < WildRules.AXES; axis++) {
                if (wide.spans().get(axis).distance(point[axis]) > 0.0D) {
                    within = false;
                    break;
                }
            }
            check(within == (far == 0.0D),
                    "a climate inside a box at a distance, or outside one at none");
        }

        // Offset is the seventh axis and the target's is always nothing, so it
        // is a flat penalty: a biome carrying one loses every tie it is in.
        List<WildRules.Span> everywhere = new ArrayList<>();
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            everywhere.add(new WildRules.Span(-1.0D, 1.0D));
        }
        WildRules.Region free = new WildRules.Region("free", everywhere, 0.0D);
        WildRules.Region taxed = new WildRules.Region("taxed", everywhere, 0.3D);
        for (int round = 0; round < 50_000; round++) {
            double[] point = climate(random);
            check(taxed.distance(point) > free.distance(point),
                    "a biome with an offset is no further away than one without, so "
                            + "the offset does nothing at all");
        }
        System.out.println("  the metric: inside costs nothing, outside costs the gap, "
                + "and an offset is a penalty that loses every tie");
    }

    /**
     * The theorem, which is the bug.
     *
     * <p>Generated rather than asserted about one dimension file, because the
     * file that had it was fixed and the next one has not been written yet.
     */
    private static void theSwallowing() {
        Random random = new Random(4_242_424L);
        int pairs = 0;
        int climates = 0;
        for (int round = 0; round < 10_000; round++) {
            WildRules.Region outer = box("outer", 0.2D + random.nextDouble() * 0.7D,
                    0.0D, random);
            // An inner box built to sit inside the outer one on every axis.
            List<WildRules.Span> spans = new ArrayList<>(WildRules.AXES);
            for (int axis = 0; axis < WildRules.AXES; axis++) {
                WildRules.Span around = outer.spans().get(axis);
                double room = (around.high() - around.low()) * random.nextDouble() * 0.5D;
                double low = around.low() + room;
                double high = around.high() - room;
                spans.add(new WildRules.Span(Math.min(low, high), Math.max(low, high)));
            }
            WildRules.Region inner = new WildRules.Region("inner", spans, 0.0D);
            check(inner.inside(outer),
                    "a box built inside another is not reported as inside it, so the "
                            + "one check that needs no sampling is blind");
            pairs++;

            List<WildRules.Region> both = List.of(outer, inner);
            check(!WildRules.swallowed(both).isEmpty(),
                    "a biome swallowed by one declared before it is not reported");
            check(WildRules.swallowed(List.of(inner, outer)).isEmpty(),
                    "a biome declared before the one that contains it is reported as "
                            + "swallowed, which is the arrangement that works");

            for (int try_ = 0; try_ < 100; try_++) {
                double[] point = climate(random);
                climates++;
                check(WildRules.nearest(both, point) == outer,
                        "the inner box won a climate, so a claim inside an earlier "
                                + "claim can generate after all and this whole check is "
                                + "measuring nothing");
            }
        }
        // And the arrangement the fix uses: two bands either side of a third.
        List<WildRules.Span> lower = new ArrayList<>();
        List<WildRules.Span> middle = new ArrayList<>();
        List<WildRules.Span> upper = new ArrayList<>();
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            lower.add(new WildRules.Span(-1.0D, axis == 5 ? -0.05D : 1.0D));
            middle.add(new WildRules.Span(axis == 5 ? -0.05D : -1.0D,
                    axis == 5 ? 0.05D : 1.0D));
            upper.add(new WildRules.Span(axis == 5 ? 0.05D : -1.0D, 1.0D));
        }
        List<WildRules.Region> split = List.of(
                new WildRules.Region("below", lower, 0.0D),
                new WildRules.Region("above", upper, 0.0D),
                new WildRules.Region("river", middle, 0.0D));
        check(WildRules.swallowed(split).isEmpty(),
                "splitting a claim either side of a band still leaves something "
                        + "swallowed, so the fix does not fix it");
        check(WildRules.unreachable(split).isEmpty(),
                "splitting a claim either side of a band leaves a biome that never "
                        + "generates: " + WildRules.unreachable(split));
        System.out.println("  the swallowing: " + pairs + " nested pairs over " + climates
                + " climates, and the inner claim won not one of them");
    }

    private static void theShares() {
        Random random = new Random(55_129L);
        for (int round = 0; round < 400; round++) {
            int many = 2 + random.nextInt(7);
            List<WildRules.Region> world = new ArrayList<>(many);
            Set<String> named = new HashSet<>();
            for (int at = 0; at < many; at++) {
                world.add(box("b" + at, 0.05D + random.nextDouble() * 0.4D, 0.0D, random));
                named.add("b" + at);
            }
            Map<String, Double> held = WildRules.shares(world, 4_000);
            double total = 0.0D;
            for (double share : held.values()) {
                total += share;
            }
            check(Math.abs(total - 1.0D) < 1.0e-9,
                    "the shares of a world come to " + total + " rather than one, so "
                            + "some climate belongs to no biome at all");
            check(held.size() == named.size(),
                    "a world of " + named.size() + " biomes reported " + held.size()
                            + " shares");
            for (String dead : WildRules.unreachable(world)) {
                check(held.getOrDefault(dead.split(" ")[0], 1.0D) <= 0.0D,
                        "a biome reported as never generating holds some of the world");
            }
        }
        // The same world twice has to come out the same, or a build that fails
        // one time in five gets rerun rather than read.
        List<WildRules.Region> world = new ArrayList<>();
        Random fixed = new Random(7L);
        for (int at = 0; at < 5; at++) {
            world.add(box("b" + at, 0.3D, 0.0D, fixed));
        }
        check(WildRules.shares(world, 20_000).equals(WildRules.shares(world, 20_000)),
                "the same world measured twice comes out differently, so every check "
                        + "built on this is a coin toss");

        // A world may declare what it is meant to look like, and must then be it.
        List<WildRules.Region> themed = new ArrayList<>();
        List<WildRules.Span> most = new ArrayList<>();
        List<WildRules.Span> corner = new ArrayList<>();
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            most.add(new WildRules.Span(-1.0D, 1.0D));
            corner.add(new WildRules.Span(0.9D, 1.0D));
        }
        themed.add(new WildRules.Region("everywhere", most, 0.0D));
        themed.add(new WildRules.Region("corner", corner, 0.0D));
        check(!WildRules.lopsided(themed, 0.5D).isEmpty(),
                "a world that is almost entirely one biome passes a ceiling of half");
        check(WildRules.lopsided(themed, 0.999D).size() >= 1,
                "a world allowed to be one biome is not told that is not a world");

        // A thin biome is allowed to be thin - a river is a thread through
        // somewhere else - so long as a player can walk to one. That is the
        // only floor there is, and it is measured in blocks.
        // The world that had the bug, as it is now: plains split either side of
        // a river's weirdness band, with highlands taking the eroded end. A
        // river holds about three per cent of it, which is what a river is.
        List<WildRules.Region> threaded = List.of(
                world("plains", 3, -0.2D, 1.0D, 5, -1.0D, -0.05D),
                world("plains", 3, -0.2D, 1.0D, 5, 0.05D, 1.0D),
                world("highlands", 3, -1.0D, -0.375D, 5, -1.0D, 1.0D),
                world("river", 3, -0.2D, 1.0D, 5, -0.05D, 0.05D));
        List<WildRules.Span> sliver = new ArrayList<>();
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            sliver.add(new WildRules.Span(0.99999D, 1.0D));
        }
        check(WildRules.lopsided(threaded, 0.70D).isEmpty(),
                "a river threading a world is reported as wrong for being a river: "
                        + WildRules.lopsided(threaded, 0.70D));
        check(WildRules.tooFar(threaded).isEmpty(),
                "a river a few thousand blocks away is reported as unreachable: "
                        + WildRules.tooFar(threaded));
        List<WildRules.Region> slivered = new ArrayList<>(threaded);
        slivered.add(new WildRules.Region("sliver", sliver, 0.9D));
        check(!WildRules.unreachable(slivered).isEmpty()
                        || !WildRules.tooFar(slivered).isEmpty(),
                "a biome in a corner of the space nobody could reach is reported as "
                        + "neither dead nor far, so there is no floor at all");
        System.out.println("  the shares: 400 random worlds that each add up to one, and "
                + "the same world twice is the same world");
    }

    private static void theWalk() {
        double closer = WildRules.walkTo(0.25D);
        double further = WildRules.walkTo(0.01D);
        check(further > closer,
                "a rarer biome is not further away, so the number a compass would show "
                        + "means nothing");
        check(WildRules.walkTo(1.0D) <= WildRules.WORLD_GRAIN,
                "a biome that is the whole world is more than one patch away");
        Random random = new Random(3_141_592L);
        for (int round = 0; round < 200_000; round++) {
            double share = random.nextDouble();
            double rarer = share * (0.05D + random.nextDouble() * 0.9D);
            check(WildRules.walkTo(rarer) >= WildRules.walkTo(share),
                    "a biome got rarer and closer at the same time");
            check(WildRules.walkTo(share) > 0.0D, "a biome at no distance at all");
        }
        System.out.println("  the walk: 200,000 pairs, and the rarer thing is never the "
                + "nearer one");
    }

    /**
     * The claim that keeps a compass from being a server problem.
     *
     * <p>Every search costs the same. Not "about the same", and not "the same
     * for the biomes we ship" — the same for any share a biome could hold,
     * swept, because the one that is expensive is always the one nobody
     * thought of.
     */
    private static void theSearch() {
        check(WildRules.tooExpensive().isEmpty(),
                "the search as configured: " + WildRules.tooExpensive());

        Random random = new Random(191_919L);
        long dearest = 0L;
        double coarsest = 0.0D;
        for (int round = 0; round < 500_000; round++) {
            // Every share a biome could hold, including ones far outside what
            // anything sane would ship.
            double share = Math.pow(10.0D, -9.0D * random.nextDouble());
            double radius = WildRules.searchRadius(share);
            double step = WildRules.stepFor(radius);
            long cost = WildRules.samples(radius, step);
            dearest = Math.max(dearest, cost);
            coarsest = Math.max(coarsest, step);

            check(radius > 0.0D, "a search that looks nowhere at all");
            check(radius <= WildRules.MOST_SEARCH_BLOCKS,
                    "a search reaching " + radius + " blocks, past the stated ceiling");
            check(step >= WildRules.FINEST_STEP,
                    "a search sampling finer than climate changes, which is work for "
                            + "no answer");
            check(WildRules.mightMiss(step) < WildRules.WORLD_GRAIN,
                    "a search sampling every " + step + " blocks, which can walk over "
                            + "a patch of biome and report it missing");
            check(cost <= WildRules.SAMPLES_PER_SEARCH * 1.05D + 4_000L,
                    "a search costing " + cost + " samples against a budget of "
                            + WildRules.SAMPLES_PER_SEARCH + "; the whole point of "
                            + "choosing the step from the radius is that it cannot");

            // Rarer things are looked for further, never nearer.
            double rarer = share * (0.01D + random.nextDouble() * 0.98D);
            check(WildRules.searchRadius(rarer) >= radius - 1.0e-9,
                    "a rarer biome was searched for over a smaller area");
        }
        check(WildRules.searchRadius(0.0D) == WildRules.MOST_SEARCH_BLOCKS,
                "a biome holding none of the world is searched for over a small area, "
                        + "so the compass gives up early on the one case where looking "
                        + "everywhere is the only honest answer");
        check(WildRules.samples(10.0D, 0.0D) == Long.MAX_VALUE,
                "a search at no step at all is priced as cheap rather than impossible");
        System.out.println("  the search: 500,000 shares, the dearest search "
                + dearest + " samples against a budget of " + WildRules.SAMPLES_PER_SEARCH
                + ", the blurriest every " + Math.round(coarsest) + " blocks against a "
                + Math.round(WildRules.WORLD_GRAIN) + "-block patch, and one player "
                + "costing at most " + Math.round(WildRules.worstPerSecond())
                + " samples a second");
    }

    private static void theSpacing() {
        WildRules.Spacing close = new WildRules.Spacing(12, 5);
        WildRules.Spacing far = new WildRules.Spacing(48, 16);
        check(far.apart() > close.apart(), "more spacing is not further apart");
        check(close.within(WildRules.EXPLORED_EARLY) > far.within(WildRules.EXPLORED_EARLY),
                "a closer-spaced structure is not met more often");

        boolean refused = false;
        try {
            new WildRules.Spacing(8, 8);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a separation as large as the spacing is accepted; the game "
                + "rejects it outright and the structure never places at all");

        Map<String, WildRules.Spacing> rare = Map.of("thing", new WildRules.Spacing(512, 128));
        check(!WildRules.neverMet(rare).isEmpty(),
                "a structure a player meets once in a hundred thousand blocks is not "
                        + "reported");
        Map<String, WildRules.Spacing> everywhere =
                Map.of("thing", new WildRules.Spacing(2, 1));
        check(!WildRules.wallpaper(everywhere).isEmpty(),
                "a structure in sight of the last one is not reported");
        // Vanilla's own villages are 34 chunks apart. A band that does not
        // contain the game's commonest landmark is a band that is wrong about
        // the game rather than about the pack.
        Map<String, WildRules.Spacing> village =
                Map.of("thing", new WildRules.Spacing(34, 8));
        check(WildRules.neverMet(village).isEmpty() && WildRules.wallpaper(village).isEmpty(),
                "the game's own village spacing is reported as wrong, so this band is "
                        + "wrong about the game rather than about the pack");

        // And the band has to be a band. Both ends are worked out here rather
        // than asserted, so an edit that closes it fails instead of quietly
        // making every spacing wrong.
        int tightest = 1;
        while (tightest < 4_096 && !WildRules.wallpaper(
                Map.of("t", new WildRules.Spacing(tightest, 0))).isEmpty()) {
            tightest++;
        }
        int loosest = tightest;
        while (loosest < 4_096 && WildRules.neverMet(
                Map.of("t", new WildRules.Spacing(loosest, 0))).isEmpty()) {
            loosest++;
        }
        check(loosest - tightest > 20,
                "a structure may be spaced between " + tightest + " and " + (loosest - 1)
                        + " chunks apart, which is not a band, it is a number");
        check(tightest <= 34 && loosest > 34,
                "the band runs from " + tightest + " to " + (loosest - 1)
                        + " chunks and the game's own villages are at 34");

        Random random = new Random(86_753_09L);
        for (int round = 0; round < 200_000; round++) {
            int spacing = 2 + random.nextInt(200);
            int separation = random.nextInt(spacing);
            WildRules.Spacing one = new WildRules.Spacing(spacing, separation);
            WildRules.Spacing wider = new WildRules.Spacing(spacing + 1, separation);
            check(wider.within(WildRules.EXPLORED_EARLY)
                            < one.within(WildRules.EXPLORED_EARLY),
                    "spacing a structure further apart did not make it rarer");
        }
        System.out.println("  the spacing: a band from " + tightest + " to "
                + (loosest - 1) + " chunks with the game's own villages inside it, and "
                + "200,000 pairs where spacing further apart is always met less often");
    }
}
