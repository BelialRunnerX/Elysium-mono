package com.elysium.armory.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How the traits on a piece of gear's parts become the traits on the gear
 * (Silent Gear's SG-60).
 *
 * <h2>Conditions</h2>
 *
 * A material grants a trait outright, or only when enough of the part is made
 * of it. The conditions a file may state are the ones Silent Gear had, in the
 * spelling the port writes them:
 *
 * <ul>
 *   <li>{@code ratio>=0.5} — at least half the part's materials are this one;</li>
 *   <li>{@code count>=2} — at least this many of them are;</li>
 *   <li>{@code on:axe} — only on that gear type, or anything under it;</li>
 *   <li>{@code not:<condition>} — the opposite of one of the above;</li>
 *   <li>several conditions on one grant all have to hold.</li>
 * </ul>
 *
 * <h2>Levels</h2>
 *
 * A trait on several parts is averaged, but not evenly: the divisor is
 * {@code min(instances / 2, times this trait appears)}, so a trait carried by
 * one part out of five keeps most of its level while one carried by all five is
 * averaged down. That is Silent Gear's rule and it is kept, because it is what
 * makes a single well-chosen part worth something.
 *
 * <p>The level is <b>not</b> capped at the trait's maximum here — the maximum
 * is what the trait's own effects are written for, and {@link #capped} applies
 * it where the effect is read. A trait above its maximum is a real thing a
 * player built, and the tooltip says so.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public final class ArmoryTraitRules {

    private ArmoryTraitRules() {
    }

    /** One grant of a trait, as a part states it. */
    public record Instance(String trait, int level, List<String> conditions) {

        public Instance {
            conditions = List.copyOf(conditions);
        }
    }

    /** What a grant is judged against: the part it is on and the gear it is in. */
    public record Context(double ratio, int count, String gearType, List<String> gearLineage) {

        public Context {
            gearLineage = List.copyOf(gearLineage);
        }
    }

    /** Whether one condition holds. An unknown condition is false: a trait is not granted by accident. */
    public static boolean holds(String condition, Context context) {
        String text = condition.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("not:")) {
            return !holds(text.substring(4), context);
        }
        if (text.startsWith("on:")) {
            String type = text.substring(3).trim();
            return type.equals(context.gearType()) || context.gearLineage().contains(type);
        }
        if (text.startsWith("ratio>=")) {
            return context.ratio() >= number(text.substring(7));
        }
        if (text.startsWith("count>=")) {
            return context.count() >= number(text.substring(7));
        }
        return false;
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;            // an unreadable number never passes
        }
    }

    /** Whether every condition on a grant holds. */
    public static boolean granted(Instance instance, Context context) {
        for (String condition : instance.conditions()) {
            if (!holds(condition, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The traits a piece of gear ends up with, from every grant that held.
     * {@code total} is how many grants there were before conditions were
     * checked, which is what the divisor is taken from.
     */
    public static Map<String, Integer> combine(List<Instance> granted, int total) {
        Map<String, Integer> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Instance instance : granted) {
            sums.merge(instance.trait(), instance.level(), Integer::sum);
            counts.merge(instance.trait(), 1, Integer::sum);
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            int count = counts.get(entry.getKey());
            double divisor = Math.max(1.0D, Math.min(total / 2.0D, count));
            int level = (int) Math.round(entry.getValue() / divisor);
            out.put(entry.getKey(), Math.max(1, level));
        }
        return out;
    }

    /** A level as an effect reads it: never past what the trait was written for. */
    public static int capped(int level, int maxLevel) {
        return maxLevel <= 0 ? level : Math.min(level, maxLevel);
    }

    /** Roman numerals for a tooltip, which is all a player ever sees of a level. */
    public static String roman(int level) {
        if (level <= 0) {
            return "";
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int left = level;
        for (int i = 0; i < values.length; i++) {
            while (left >= values[i]) {
                out.append(letters[i]);
                left -= values[i];
            }
        }
        return out.toString();
    }

    /** Every trait id a list of grants mentions, in the order first seen. */
    public static List<String> names(List<Instance> instances) {
        List<String> out = new ArrayList<>();
        for (Instance instance : instances) {
            if (!out.contains(instance.trait())) {
                out.add(instance.trait());
            }
        }
        return out;
    }
}
