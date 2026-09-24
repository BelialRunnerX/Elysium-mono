package com.elysium.armory.property;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How a list of modifiers becomes one number.
 *
 * <h2>The pipeline</h2>
 *
 * <ol>
 *   <li>No modifiers at all: the property's base.</li>
 *   <li>{@code f = base + weightedAverage(AVERAGE values)}.</li>
 *   <li>{@code f = max(f, every MAX value)}.</li>
 *   <li>{@code r = f + sum(f * v)} over MULTIPLY_BASE.</li>
 *   <li>{@code r = r * product(1 + v)} over MULTIPLY_TOTAL.</li>
 *   <li>{@code r = r + sum(v)} over ADD.</li>
 *   <li>{@code r = max(r, floor)}.</li>
 * </ol>
 *
 * Step 7 is where this departs from the gear mods it replaces. They clamp to
 * a range; Elysium keeps only the floor, and only on properties where a value
 * below it would invert the property rather than weaken it (attack speed
 * below -3.9 is a weapon that never swings). There is no maximum anywhere:
 * the owner's ruling is "uncap everything", and the gear that comes out of
 * this should scale as hard as the player behind it.
 *
 * <h2>The weighted average</h2>
 *
 * Not a plain mean. The first value is the reference; every value is weighted
 * by {@code 1 + v / (1 + |reference|)}, so a stronger material pulls the
 * result toward itself. Iron (250) and diamond (1561) average to about 1277,
 * not 905 — a player who puts one great material into a part gets most of
 * what it is worth, which is what makes finding one worth something.
 *
 * <p>Imports nothing Minecraft, and must not: it is executed by a harness.
 */
public final class ArmoryCompute {

    private ArmoryCompute() {
    }

    /** The floor on a property that has none. */
    public static final double NO_FLOOR = Double.NEGATIVE_INFINITY;

    /**
     * The weighted average of these values.
     *
     * A reference of zero or below is treated as one, so that a set of
     * non-positive values still averages instead of dividing by nothing.
     * When the weights cancel to zero the weighted sum is returned, which is
     * the only defined answer and matches the original's behaviour.
     */
    public static double weightedAverage(double[] values) {
        if (values.length == 0) {
            return 0.0D;
        }
        double reference = values[0] > 0.0D ? values[0] : 1.0D;
        double scale = 1.0D + Math.abs(reference);
        double weighted = 0.0D;
        double weights = 0.0D;
        for (double v : values) {
            double w = 1.0D + v / scale;
            weighted += v * w;
            weights += w;
        }
        return weights == 0.0D ? weighted : weighted / weights;
    }

    /** The pipeline. See the class comment. */
    public static double compute(double base, List<ArmoryModifier> modifiers, double floor) {
        if (modifiers == null || modifiers.isEmpty()) {
            return Math.max(base, floor);
        }
        Map<ArmoryOperation, List<Double>> by = split(modifiers);

        double f = base + weightedAverage(unbox(by.get(ArmoryOperation.AVERAGE)));
        for (double v : by.get(ArmoryOperation.MAX)) {
            f = Math.max(f, v);
        }
        double r = f;
        for (double v : by.get(ArmoryOperation.MULTIPLY_BASE)) {
            r += f * v;
        }
        for (double v : by.get(ArmoryOperation.MULTIPLY_TOTAL)) {
            r *= 1.0D + v;
        }
        for (double v : by.get(ArmoryOperation.ADD)) {
            r += v;
        }
        return Math.max(r, floor);
    }

    /**
     * One part's modifiers collapsed to at most one per operation: several MAX
     * become their maximum, several of anything else become their weighted
     * average with the operation kept. What a finished part contributes to the
     * gear it goes into, so a part made of three materials counts once.
     */
    public static List<ArmoryModifier> compress(List<ArmoryModifier> modifiers) {
        Map<ArmoryOperation, List<Double>> by = split(modifiers);
        List<ArmoryModifier> out = new ArrayList<>();
        for (ArmoryOperation op : ArmoryOperation.values()) {
            List<Double> values = by.get(op);
            if (values.isEmpty()) {
                continue;
            }
            double collapsed;
            if (op == ArmoryOperation.MAX) {
                collapsed = Double.NEGATIVE_INFINITY;
                for (double v : values) {
                    collapsed = Math.max(collapsed, v);
                }
            } else {
                collapsed = weightedAverage(unbox(values));
            }
            out.add(new ArmoryModifier(op, collapsed));
        }
        return out;
    }

    private static Map<ArmoryOperation, List<Double>> split(List<ArmoryModifier> modifiers) {
        Map<ArmoryOperation, List<Double>> by = new EnumMap<>(ArmoryOperation.class);
        for (ArmoryOperation op : ArmoryOperation.values()) {
            by.put(op, new ArrayList<>());
        }
        if (modifiers != null) {
            for (ArmoryModifier m : modifiers) {
                if (m != null && m.operation() != null && !Double.isNaN(m.value())) {
                    by.get(m.operation()).add(m.value());
                }
            }
        }
        return by;
    }

    private static double[] unbox(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
