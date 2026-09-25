package com.elysium.wilds.rules;

import java.util.List;

/**
 * The roster, printed, so the generator can be checked against it.
 *
 * <p>{@code gen_wilds.py} reads the bands and the names out of {@link
 * OverworldRules} and works the rest out again in Python — the colours, the
 * claims, what each biome adds of its own. Two implementations of the same
 * arithmetic drift, and the drift is silent: a biome painted one colour in
 * the proof and another in the file the game reads, and nothing anywhere
 * says so.
 *
 * <p>So this prints what the Java thinks, one line per biome, and {@code
 * validate.py} compares it against what was actually written. Either they
 * agree or the build stops.
 *
 * <p>Columns: name, model, sky, fog, water, grass, foliage, temperature,
 * downfall, rains, then the features of ours it adds, then one box per band
 * of weirdness as low/high pairs across the six axes.
 */
public final class OverworldRoster {

    private OverworldRoster() {
    }

    public static void main(String[] args) {
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            System.out.println(line(one.name(), one.model(), one.paint(),
                    one.warmthValue(), one.wetValue(), one.rains(),
                    OverworldRules.extras(one), one.claim()));
        }
        for (OverworldRules.Cave one : OverworldRules.CAVES) {
            System.out.println(line(one.name(), one.model(), one.paint(),
                    one.warmthValue(), one.wetValue(), false,
                    OverworldRules.extras(one), one.claim()));
        }
    }

    private static String line(String name, String model, List<Integer> paint,
                               float warmth, float wet, boolean rains,
                               List<String> extras,
                               List<WildRules.Region> claim) {
        StringBuilder out = new StringBuilder();
        out.append(name).append('\t').append(model);
        for (int colour : paint) {
            out.append('\t').append(colour);
        }
        out.append('\t').append(round(warmth)).append('\t').append(round(wet));
        out.append('\t').append(rains);
        out.append('\t').append(String.join(",", extras));
        for (WildRules.Region box : claim) {
            for (WildRules.Span span : box.spans()) {
                out.append('\t').append(round(span.low()))
                   .append('\t').append(round(span.high()));
            }
        }
        return out.toString();
    }

    /** The same rounding the generator writes with, so a match is a match. */
    private static String round(double value) {
        return String.format("%.4f", value);
    }
}
