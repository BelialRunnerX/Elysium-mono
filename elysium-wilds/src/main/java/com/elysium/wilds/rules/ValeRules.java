package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Vale: one dimension, fifteen biomes, and a claim on the climate that is
 * a <em>partition</em>. The JDK and nothing else.
 *
 * <h2>The bug this shape exists to make impossible</h2>
 *
 * A biome does not get picked from a weighted list. Every biome claims a box
 * in six-dimensional climate space and each column of the world goes to
 * whichever box is nearest. So a biome can be declared, registered,
 * translated, listed in every compass in the pack — and never generate
 * anywhere at all, because some other box is nearer to every climate the noise
 * ever makes. It is not a crash and not a warning. This repository has
 * shipped one, and it was found by arithmetic and by nothing else.
 *
 * <p>So the Vale does not hand-place fifteen boxes and hope. It crosses
 * <b>five bands of temperature with three bands of humidity</b>, each band a
 * half-open interval that runs from where the last one stopped, and leaves
 * every other axis wide open. That is a partition of the plane: every climate
 * is in exactly one band pair, so every biome wins its own rectangle and no
 * biome can be contained in another's claim. Fifteen biomes, fifteen
 * rectangles, no gaps and no overlaps, and the proof is arithmetic rather
 * than two hundred thousand samples.
 *
 * <h2>And everything else about a biome follows from where it sits</h2>
 *
 * Its temperature, its rainfall, the colour of its sky and its water and its
 * grass — all worked out from which band it is in, so two biomes cannot be
 * the same biome under two names and the coldest one cannot be greener than
 * the warmest. Which is the other half of the escape: fifteen hand-written
 * biome files have two that look identical in them, and nobody ever finds out
 * which two.
 */
public final class ValeRules {

    private ValeRules() {
    }

    /** What the dimension is called, in this module's namespace. */
    public static final String VALE = "vale";

    /** How many bands of temperature the Vale is cut into. */
    public static final int WARMTHS = 5;

    /** How many bands of rainfall. */
    public static final int WETS = 3;

    /** The names of the temperature bands, coldest first. */
    public static final List<String> WARMTH_NAMES =
            List.of("frozen", "cold", "mild", "warm", "hot");

    /** The names of the humidity bands, driest first. */
    public static final List<String> WET_NAMES = List.of("dry", "fair", "wet");

    /**
     * What each crossing is called.
     *
     * <p>Fifteen names, written out rather than made up out of the two band
     * names, because "hot wet" is not a place and "sulphur springs" is. The
     * count is checked against the crossing, so a name that goes missing is a
     * build failure rather than a biome called {@code vale_hot_wet}.
     */
    public static final List<String> NAMES = List.of(
            "rime_barrens", "glacier_vale", "frostmere",
            "ashen_steppe", "pinewold", "coldmarsh",
            "chalk_downs", "verdant_vale", "bloomfen",
            "amber_scrub", "goldwood", "reedbasin",
            "cinder_flats", "emberwood", "sulphur_springs");

    /**
     * The low edge of a band, on an axis that runs from -1 to 1.
     *
     * <p>Half-open: band {@code at} runs from {@code edge(at, bands)} up to
     * {@code edge(at + 1, bands)}, and the last one ends exactly at 1. That
     * "exactly" is the whole of why this is a partition rather than fifteen
     * boxes that nearly meet.
     */
    public static double edge(int at, int bands) {
        return -1.0D + 2.0D * at / bands;
    }

    /**
     * One biome of the Vale.
     *
     * @param name   what it is called
     * @param warmth which temperature band, 0 the coldest
     * @param wet    which humidity band, 0 the driest
     */
    public record Biome(String name, int warmth, int wet) {

        public Biome {
            Objects.requireNonNull(name, "a biome with no name");
            if (warmth < 0 || warmth >= WARMTHS) {
                throw new IllegalArgumentException(name + " is in temperature band "
                        + warmth + " and there are " + WARMTHS);
            }
            if (wet < 0 || wet >= WETS) {
                throw new IllegalArgumentException(name + " is in humidity band "
                        + wet + " and there are " + WETS);
            }
        }

        /** Its block id, without the namespace. */
        public String id() {
            return VALE + "_" + name;
        }

        /** The temperature interval it claims. */
        public double[] temperatureSpan() {
            return new double[] {edge(warmth, WARMTHS), edge(warmth + 1, WARMTHS)};
        }

        /** The humidity interval it claims. */
        public double[] humiditySpan() {
            return new double[] {edge(wet, WETS), edge(wet + 1, WETS)};
        }

        /**
         * How warm it is, in the number a biome file carries.
         *
         * <p>Worked out from the band, so the coldest band cannot end up
         * warmer than the warmest and nobody has to check fifteen files
         * against each other by eye.
         */
        public float warmthValue() {
            return -0.5F + warmth * (2.5F / (WARMTHS - 1));
        }

        /** How wet it is, in the number a biome file carries. */
        public float wetValue() {
            return wet * (1.0F / (WETS - 1));
        }

        /** Whether it rains here at all. */
        public boolean rains() {
            return wet > 0;
        }

        /** The colour of its sky: bluer when cold, hazier when hot. */
        public int sky() {
            return colour(0x88, 0xB8, 0xF0, 0xE8, 0x9C, 0x60);
        }

        /** The colour of its fog. */
        public int fog() {
            return colour(0xB0, 0xC8, 0xE8, 0xD8, 0xB0, 0x88);
        }

        /** The colour of its water: clearer when cold, greener when wet. */
        public int water() {
            return colour(0x3A, 0x6E, 0xB4, 0x2E, 0x7A, 0x5E);
        }

        /** The colour of its grass: bare when dry, deep when wet. */
        public int grass() {
            int red = mix(0xB8, 0x4E, wet, WETS);
            int green = mix(0x9C, 0x9E, wet, WETS);
            int blue = mix(0x74, 0x3A, wet, WETS);
            return (red << 16) | (green << 8) | blue;
        }

        /** The colour of its leaves. */
        public int foliage() {
            int red = mix(0xA4, 0x36, wet, WETS);
            int green = mix(0x92, 0x8A, wet, WETS);
            int blue = mix(0x6A, 0x30, wet, WETS);
            return (red << 16) | (green << 8) | blue;
        }

        private int colour(int coldRed, int coldGreen, int coldBlue,
                           int hotRed, int hotGreen, int hotBlue) {
            int red = mix(coldRed, hotRed, warmth, WARMTHS);
            int green = mix(coldGreen, hotGreen, warmth, WARMTHS);
            int blue = mix(coldBlue, hotBlue, warmth, WARMTHS);
            return (red << 16) | (green << 8) | blue;
        }

        private static int mix(int from, int to, int at, int bands) {
            if (bands < 2) {
                return from;
            }
            return from + (to - from) * at / (bands - 1);
        }
    }

    /** Every biome of the Vale, coldest and driest first. */
    public static final List<Biome> BIOMES = biomes();

    private static List<Biome> biomes() {
        List<Biome> made = new ArrayList<>();
        int at = 0;
        for (int warmth = 0; warmth < WARMTHS; warmth++) {
            for (int wet = 0; wet < WETS; wet++) {
                made.add(new Biome(NAMES.get(at), warmth, wet));
                at++;
            }
        }
        return List.copyOf(made);
    }

    /** A biome by name, or null. */
    public static Biome biome(String name) {
        for (Biome one : BIOMES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    /** How many biomes that is. */
    public static int howMany() {
        return BIOMES.size();
    }

    // ==================================================================
    // What has to be true of the claim
    // ==================================================================

    /** Which biome a climate lands in, or null if the bands do not cover it. */
    public static Biome at(double temperature, double humidity) {
        for (Biome one : BIOMES) {
            double[] warm = one.temperatureSpan();
            double[] wet = one.humiditySpan();
            boolean inWarm = temperature >= warm[0]
                    && (temperature < warm[1] || warm[1] >= 1.0D);
            boolean inWet = humidity >= wet[0]
                    && (humidity < wet[1] || wet[1] >= 1.0D);
            if (inWarm && inWet) {
                return one;
            }
        }
        return null;
    }

    /**
     * Everything wrong with the claim. Must be empty.
     *
     * <p>Four things: the crossing has to be the whole roster, the names have
     * to be distinct, the bands have to run edge to edge with no gap, and no
     * biome's claim may sit inside another's — which is the shape that made a
     * river in this repository never generate once.
     */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        if (NAMES.size() != WARMTHS * WETS) {
            bad.add("there are " + NAMES.size() + " names for " + WARMTHS * WETS
                    + " crossings, so a biome is unnamed or a name is for nothing");
        }
        if (WARMTH_NAMES.size() != WARMTHS || WET_NAMES.size() != WETS) {
            bad.add("the bands and the names for them are different lengths");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Biome one : BIOMES) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " names two biomes, so one of them never "
                        + "generates and nothing reports it");
            }
        }
        // The bands run edge to edge. A gap is climate that belongs to
        // nothing and goes to whatever box happens to be nearest.
        for (int bands : new int[] {WARMTHS, WETS}) {
            if (edge(0, bands) != -1.0D) {
                bad.add("a band on a " + bands + "-band axis starts at "
                        + edge(0, bands) + " and the axis starts at -1");
            }
            if (edge(bands, bands) != 1.0D) {
                bad.add("a band on a " + bands + "-band axis ends at "
                        + edge(bands, bands) + " and the axis ends at 1");
            }
            for (int at = 1; at < bands; at++) {
                if (edge(at, bands) <= edge(at - 1, bands)) {
                    bad.add("band " + at + " of " + bands + " starts at or before "
                            + "the one below it");
                }
            }
        }
        // And no claim inside another's. With a partition this cannot happen,
        // which is the point — but "cannot happen" is what was said about the
        // river too, so it is checked.
        for (Biome one : BIOMES) {
            for (Biome other : BIOMES) {
                if (one == other) {
                    continue;
                }
                if (inside(one, other)) {
                    bad.add(one.name() + "'s claim sits inside " + other.name()
                            + "'s, so it wins nothing anywhere, ever");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Whether one biome's rectangle sits entirely within another's. */
    public static boolean inside(Biome one, Biome other) {
        double[] mineWarm = one.temperatureSpan();
        double[] theirsWarm = other.temperatureSpan();
        double[] mineWet = one.humiditySpan();
        double[] theirsWet = other.humiditySpan();
        return mineWarm[0] >= theirsWarm[0] && mineWarm[1] <= theirsWarm[1]
                && mineWet[0] >= theirsWet[0] && mineWet[1] <= theirsWet[1];
    }

    /**
     * The largest share any one biome may hold of the Vale.
     *
     * <p>Fifteen equal rectangles is one fifteenth apiece, so anything much
     * above that means the bands are not what they say they are. The slack is
     * for the noise, which does not hand out climates evenly.
     */
    public static final double MOST_OF_THE_WORLD = 0.30D;

    /** The smallest share any one biome may hold. */
    public static final double LEAST_OF_THE_WORLD = 0.005D;
}
