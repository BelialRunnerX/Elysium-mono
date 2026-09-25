package com.elysium.wilds.rules;

/**
 * The Vale's fifteen, printed, for the same reason the overworld's are.
 *
 * <p>The Vale's biome files are written by {@code gen_wilds.py} and its
 * properties are proved by {@link ValeHarness} over {@link ValeRules}, and
 * for as long as the Vale has existed nothing compared the two. They had in
 * fact drifted: Python floors an integer division and Java truncates it, and
 * every colour ramp in the Vale that runs from bright down to dark came out
 * one darker in the file than in the proof. Nobody could have seen it and no
 * amount of arithmetic would have found it, because the arithmetic was right
 * — it was the other implementation that was wrong.
 *
 * <p>Columns: name, sky, fog, water, grass, foliage, temperature, downfall,
 * whether it rains.
 */
public final class ValeRoster {

    private ValeRoster() {
    }

    public static void main(String[] args) {
        for (ValeRules.Biome one : ValeRules.BIOMES) {
            System.out.println(one.id() + "\t" + one.sky() + "\t" + one.fog()
                    + "\t" + one.water() + "\t" + one.grass() + "\t"
                    + one.foliage() + "\t" + String.format("%.4f", one.warmthValue())
                    + "\t" + String.format("%.4f", one.wetValue())
                    + "\t" + one.rains());
        }
    }
}
