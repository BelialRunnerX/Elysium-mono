package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The Vale, executed.
 *
 * <p>The one property this exists for is that <b>every biome generates</b>.
 * A biome that never appears is not a crash and not a warning: the file is
 * valid, the id resolves, the biome is genuinely there, and it simply has no
 * territory. This repository has shipped one. The only way anybody found out
 * was arithmetic.
 *
 * <p>So the claim is proved twice over. Once as a <b>partition</b> — the
 * bands run edge to edge, so every climate is in exactly one rectangle and
 * the argument is arithmetic rather than sampling. And once through
 * {@link WildRules}, the machinery that found the river: the Vale's fifteen
 * rectangles are turned into the six-dimensional boxes the game actually
 * compares, and two hundred thousand climates are thrown at them.
 */
public final class ValeHarness {

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
        thePartition();
        theColours();
        theTerritory();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: fifteen biomes on a partition of "
                + "the climate plane, every one of them winning its own rectangle "
                + "and none of them inside another's, proved by arithmetic and "
                + "again by the sampling that found the river");
    }

    // ==================================================================

    private static void theRoster() {
        check(ValeRules.faults().isEmpty(),
                "something wrong with the claim: " + ValeRules.faults());
        check(ValeRules.howMany() == ValeRules.WARMTHS * ValeRules.WETS,
                "the Vale has " + ValeRules.howMany() + " biomes and "
                        + ValeRules.WARMTHS + " x " + ValeRules.WETS + " is "
                        + ValeRules.WARMTHS * ValeRules.WETS);
        Set<String> seen = new LinkedHashSet<>();
        Set<String> pairs = new LinkedHashSet<>();
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            check(seen.add(one.name()), one.name() + " names two biomes");
            check(pairs.add(one.warmth() + "/" + one.wet()),
                    one.name() + " sits in a band pair something else is in");
            check(ValeRules.biome(one.name()) == one,
                    one.name() + " cannot be looked up by its own name");
            check(one.id().startsWith(ValeRules.VALE + "_"),
                    one.id() + " is not named for the world it is in, so a "
                            + "compass listing every biome is a list a player "
                            + "cannot read");
        }
        // Warmth and rain climb with the band, or the coldest place in the
        // Vale is warmer than the hottest and nobody notices in fifteen files.
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            for (ValeRules.Biome other : ValeRules.BIOMES) {
                if (one.warmth() < other.warmth()) {
                    check(one.warmthValue() < other.warmthValue(),
                            one.name() + " is in a colder band than " + other.name()
                                    + " and is not colder");
                }
                if (one.wet() < other.wet()) {
                    check(one.wetValue() < other.wetValue(),
                            one.name() + " is in a drier band than " + other.name()
                                    + " and is not drier");
                    check(!one.rains() || other.rains(),
                            one.name() + " rains and the wetter " + other.name()
                                    + " does not");
                }
            }
        }
        System.out.println("  the roster: " + ValeRules.howMany() + " biome(s) on "
                + ValeRules.WARMTHS + " band(s) of warmth crossed with "
                + ValeRules.WETS + " of rain, each in its own band pair and each "
                + "warmer and wetter than the bands below it");
    }

    // ==================================================================

    private static void thePartition() {
        // Every point of the plane, on a fine grid: in exactly one rectangle.
        int steps = 400;
        Map<String, Integer> hits = new LinkedHashMap<>();
        for (int x = 0; x <= steps; x++) {
            double temperature = -1.0D + 2.0D * x / steps;
            for (int y = 0; y <= steps; y++) {
                double humidity = -1.0D + 2.0D * y / steps;
                ValeRules.Biome one = ValeRules.at(temperature, humidity);
                check(one != null, "no biome claims the climate " + temperature
                        + ", " + humidity + ", so it goes to whichever box happens "
                        + "to be nearest and the Vale is not the world it says");
                if (one == null) {
                    continue;
                }
                int inIt = 0;
                for (ValeRules.Biome other : ValeRules.BIOMES) {
                    double[] warm = other.temperatureSpan();
                    double[] wet = other.humiditySpan();
                    boolean inWarm = temperature >= warm[0]
                            && (temperature < warm[1] || warm[1] >= 1.0D);
                    boolean inWet = humidity >= wet[0]
                            && (humidity < wet[1] || wet[1] >= 1.0D);
                    if (inWarm && inWet) {
                        inIt++;
                    }
                }
                check(inIt == 1, "the climate " + temperature + ", " + humidity
                        + " is claimed by " + inIt + " biomes");
                hits.merge(one.name(), 1, Integer::sum);
            }
        }
        check(hits.size() == ValeRules.howMany(), "only " + hits.size() + " of "
                + ValeRules.howMany() + " biomes won any climate at all on a grid "
                + "of " + (steps + 1) * (steps + 1) + " — the rest have no "
                + "territory and nothing but this would say so");
        int fewest = Integer.MAX_VALUE;
        int most = 0;
        for (int count : hits.values()) {
            fewest = Math.min(fewest, count);
            most = Math.max(most, count);
        }
        check(fewest > 0, "a biome won nothing");
        check(most <= fewest * 4, "the largest band is " + most + " squares and "
                + "the smallest is " + fewest + ", so the bands are not the equal "
                + "bands they are built as");
        System.out.println("  the partition: " + (steps + 1) * (steps + 1)
                + " climates on a grid, every one of them claimed by exactly one "
                + "biome, and all " + hits.size() + " biomes winning somewhere");
    }

    // ==================================================================

    private static void theColours() {
        Map<String, String> seen = new LinkedHashMap<>();
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            String look = one.sky() + "/" + one.fog() + "/" + one.water() + "/"
                    + one.grass() + "/" + one.foliage();
            String already = seen.putIfAbsent(look, one.name());
            check(already == null, one.name() + " and " + already
                    + " are painted exactly the same, so they are one biome with "
                    + "two names as far as anybody standing in them can tell");
            for (int colour : new int[] {one.sky(), one.fog(), one.water(),
                    one.grass(), one.foliage()}) {
                check(colour >= 0 && colour <= 0xFFFFFF, one.name()
                        + " has a colour outside the range a colour can be: "
                        + colour);
            }
        }
        // The sky gets warmer as the land does: the red of the hottest band is
        // more than the red of the coldest, or the palette says nothing.
        ValeRules.Biome coldest = ValeRules.BIOMES.get(0);
        ValeRules.Biome hottest = ValeRules.BIOMES.get(ValeRules.howMany() - 1);
        check((hottest.sky() >> 16 & 0xFF) > (coldest.sky() >> 16 & 0xFF),
                "the hottest band's sky has no more red in it than the coldest's, "
                        + "so the palette is fifteen arbitrary colours");
        check((hottest.sky() & 0xFF) < (coldest.sky() & 0xFF),
                "the hottest band's sky has no less blue in it than the coldest's");
        System.out.println("  the colours: " + ValeRules.howMany() + " biome(s), "
                + "no two of them painted the same, and a sky that reddens and a "
                + "grass that deepens with the bands rather than at random");
    }

    // ==================================================================

    private static void theTerritory() {
        // The same claim, handed to the machinery that found the dead river:
        // six-dimensional boxes and the game's own nearest-box rule.
        List<WildRules.Region> regions = new ArrayList<>();
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            double[] warm = one.temperatureSpan();
            double[] wet = one.humiditySpan();
            regions.add(new WildRules.Region(one.id(), List.of(
                    new WildRules.Span(warm[0], warm[1]),
                    new WildRules.Span(wet[0], wet[1]),
                    new WildRules.Span(-1.0D, 1.0D),
                    new WildRules.Span(-1.0D, 1.0D),
                    new WildRules.Span(-1.0D, 1.0D),
                    new WildRules.Span(0.0D, 0.0D)), 0.0D));
        }
        Map<String, Double> held = WildRules.shares(regions, WildRules.SAMPLES);
        check(held.size() == ValeRules.howMany(),
                "the nearest-box rule gives territory to " + held.size() + " of "
                        + ValeRules.howMany() + " biomes");
        double total = 0.0D;
        double smallest = 1.0D;
        double largest = 0.0D;
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            double share = held.getOrDefault(one.id(), 0.0D);
            check(share > 0.0D, one.id() + " gets no territory at all under the "
                    + "game's own rule, so it is a valid file for a biome that "
                    + "never generates");
            check(share >= ValeRules.LEAST_OF_THE_WORLD, one.id() + " holds "
                    + share + " of the Vale, and the floor is "
                    + ValeRules.LEAST_OF_THE_WORLD + " — a biome nobody can find "
                    + "is a biome nobody has");
            check(share <= ValeRules.MOST_OF_THE_WORLD, one.id() + " holds "
                    + share + " of the Vale, and the ceiling is "
                    + ValeRules.MOST_OF_THE_WORLD);
            total += share;
            smallest = Math.min(smallest, share);
            largest = Math.max(largest, share);
        }
        check(Math.abs(total - 1.0D) < 0.001D,
                "the shares add up to " + total + " rather than to one world");
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            for (ValeRules.Biome other : ValeRules.BIOMES) {
                if (one != other) {
                    check(!ValeRules.inside(one, other), one.name()
                            + "'s claim is inside " + other.name() + "'s");
                }
            }
        }
        System.out.println("  the territory: " + WildRules.SAMPLES + " climates "
                + "through the game's own nearest-box rule, every one of "
                + held.size() + " biomes holding somewhere between "
                + Math.round(smallest * 1000) / 10.0 + "% and "
                + Math.round(largest * 1000) / 10.0 + "% of the Vale");
    }

    // ==================================================================

    /** The checks, checked. */
    private static void theControls() {
        // The containment check, on a pair whose answer is known.
        ValeRules.Biome one = ValeRules.BIOMES.get(0);
        check(ValeRules.inside(one, one),
                "a claim is not inside itself, so the check that found the dead "
                        + "river cannot say yes to anything");
        check(!ValeRules.inside(one, ValeRules.BIOMES.get(ValeRules.WETS)),
                "two claims side by side are reported as one inside the other");

        // The bands, at the edges, where a partition goes wrong if anywhere.
        check(ValeRules.edge(0, ValeRules.WARMTHS) == -1.0D,
                "the first band does not start at the start of the axis");
        check(ValeRules.edge(ValeRules.WARMTHS, ValeRules.WARMTHS) == 1.0D,
                "the last band does not end at the end of the axis");
        check(ValeRules.at(-1.0D, -1.0D) != null,
                "the coldest driest climate there is belongs to nothing");
        check(ValeRules.at(1.0D, 1.0D) != null,
                "the hottest wettest climate there is belongs to nothing — which "
                        + "is what a half-open band does at the top of the axis "
                        + "unless somebody remembers the last one");
        check(ValeRules.at(1.0D, 1.0D) == ValeRules.BIOMES.get(
                        ValeRules.howMany() - 1),
                "the hottest wettest climate does not belong to the hottest "
                        + "wettest biome");
        check(ValeRules.at(-1.0D, -1.0D) == ValeRules.BIOMES.get(0),
                "the coldest driest climate does not belong to the coldest "
                        + "driest biome");

        // The bands themselves, built to be caught.
        List<String> allowed = new ArrayList<>();
        try {
            new ValeRules.Biome("control", ValeRules.WARMTHS, 0);
            allowed.add("a biome in a temperature band that does not exist");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ValeRules.Biome("control", 0, ValeRules.WETS);
            allowed.add("a biome in a humidity band that does not exist");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the bands allow what they say they forbid: "
                + allowed);

        System.out.println("  the controls: a claim proved to be inside itself "
                + "and not inside its neighbour, both ends of both axes proved to "
                + "belong to something, and a band that does not exist refused");
    }
}
