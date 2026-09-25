package com.elysium.lib.attribute;

import com.elysium.lib.stats.ElysiumShares;

import java.util.List;

/**
 * The combat attributes Elysium adds, as numbers only. Loads without a game,
 * so a harness can read every row and check the rules below.
 *
 * <h2>Four kinds, and why chances are stored as odds</h2>
 *
 * <ul>
 *   <li><b>FLAT</b>: a plain amount (armour pierce, bonus fire damage).</li>
 *   <li><b>MULTIPLIER</b>: 1 is "unchanged" (arrow damage, crit damage).</li>
 *   <li><b>CHANCE</b>: a percentage. The attribute holds the <i>odds</i>
 *       {@code p/(1-p)}, not {@code p}. Vanilla adds ADD_VALUE modifiers
 *       linearly, and adding odds is exactly how {@link ElysiumShares#add}
 *       combines shares. So two items giving 50% each make 50% on 50%
 *       (odds 1 + 1 = 2, which is 66.7%), never 100%. The chance is
 *       {@link #chance(double)}, which gets close to 100% but never reaches
 *       it. That is the "stat/(stat+b)" ruling, and it holds for any number
 *       of modifiers with no cap.</li>
 *   <li><b>TOGGLE</b>: on when above zero (elytra flight).</li>
 * </ul>
 *
 * No attribute has a real maximum ({@link #MAX}), per the "uncap everything"
 * ruling. Two of Apotheosis's attributes are intentionally left out:
 * {@code mining_speed} is vanilla's {@code block_break_speed}, and
 * {@code ghost_health} was never implemented there.
 */
public record ElysiumAttributeSpec(String name, Kind kind, double nominalDefault,
                                   double min) {

    public enum Kind { FLAT, MULTIPLIER, CHANCE, TOGGLE }

    /** Same ceiling as the lifted vanilla attributes: finite, never reached. */
    public static final double MAX = 1.0E15D;

    /** The value the attribute actually stores by default. */
    public double storedDefault() {
        return kind == Kind.CHANCE ? odds(nominalDefault) : nominalDefault;
    }

    /** The lowest stored value allowed. */
    public double storedMin() {
        return kind == Kind.CHANCE ? odds(min) : min;
    }

    /** The odds of a chance {@code p}; this is what a modifier adds. */
    public static double odds(double p) {
        return ElysiumShares.odds((float) p);
    }

    /** The chance an attribute value (odds) stands for. */
    public static double chance(double storedOdds) {
        return storedOdds <= 0.0D ? 0.0D : ElysiumShares.fromOdds(storedOdds);
    }

    /**
     * How a chance modifier reads on a tooltip: the percentage its odds stand
     * for, e.g. "5%". Kept here, where it can be tested without a game; the
     * attribute only forwards to it. A malus shows the same number (the sign
     * is printed by vanilla's tooltip line).
     */
    public static String percentText(double odds) {
        java.text.DecimalFormat format = new java.text.DecimalFormat("#.##",
                java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(chance(Math.abs(odds)) * 100.0D) + "%";
    }

    private static ElysiumAttributeSpec flat(String n) {
        return new ElysiumAttributeSpec(n, Kind.FLAT, 0.0D, 0.0D);
    }

    private static ElysiumAttributeSpec mult(String n, double d, double min) {
        return new ElysiumAttributeSpec(n, Kind.MULTIPLIER, d, min);
    }

    private static ElysiumAttributeSpec chance(String n, double d) {
        return new ElysiumAttributeSpec(n, Kind.CHANCE, d, 0.0D);
    }

    /** Every attribute, in registration order. */
    public static final List<ElysiumAttributeSpec> ALL = List.of(
            flat("armor_pierce"),
            chance("armor_shred", 0.0D),
            mult("arrow_damage", 1.0D, 0.0D),
            mult("arrow_velocity", 1.0D, 0.0D),
            flat("cold_damage"),
            chance("crit_chance", 0.05D),
            mult("crit_damage", 1.5D, 1.0D),
            chance("current_hp_damage", 0.0D),
            chance("dodge_chance", 0.0D),
            mult("draw_speed", 1.0D, 0.0D),
            mult("experience_gained", 1.0D, 0.0D),
            flat("fire_damage"),
            mult("healing_received", 1.0D, 0.0D),
            chance("life_steal", 0.0D),
            chance("overheal", 0.0D),
            mult("projectile_damage", 1.0D, 0.0D),
            flat("prot_pierce"),
            chance("prot_shred", 0.0D),
            new ElysiumAttributeSpec("elytra_flight", Kind.TOGGLE, 0.0D, 0.0D),
            chance("cooldown_reduction", 0.0D),
            // Valoria's, in Elysium's names (docs/SPEC_VALORIA.md). Flat
            // damage per element and a chance-kind resistance to each, so a
            // resistance stacks as odds and never reaches all of it; Valoria
            // summed percentages and clamped at a hundred.
            flat("dash_distance"),
            flat("strike_radius"),
            mult("summon_damage", 1.0D, 0.0D),
            new ElysiumAttributeSpec("summon_count", Kind.FLAT, 1.0D, 0.0D),
            flat("summon_lifetime"),
            chance("miss_chance", 0.0D),
            chance("element_resistance", 0.0D),
            flat("void_damage"),
            chance("void_resistance", 0.0D),
            flat("plasma_damage"),
            chance("plasma_resistance", 0.0D),
            flat("neural_damage"),
            chance("neural_resistance", 0.0D),
            flat("dimensional_damage"),
            chance("dimensional_resistance", 0.0D),
            flat("kinetic_damage"),
            chance("kinetic_resistance", 0.0D),
            new ElysiumAttributeSpec("null_capacity", Kind.FLAT, 100.0D, 1.0D),
            new ElysiumAttributeSpec("null_resilience", Kind.FLAT, 5.0D, 1.0D),
            chance("null_resistance", 0.0D),
            flat("null_damage"));

    /** Apotheosis attributes covered by something other than a new one. */
    public static final List<String> MAPPED_ELSEWHERE = List.of("mining_speed", "ghost_health");

    /** The rows Valoria's attributes brought; everything else in ALL is Apotheosis's. */
    public static final List<String> VALORIA_ROWS = List.of(
            "dash_distance", "strike_radius", "summon_damage", "summon_count", "summon_lifetime",
            "miss_chance", "element_resistance",
            "void_damage", "void_resistance", "plasma_damage", "plasma_resistance",
            "neural_damage", "neural_resistance", "dimensional_damage", "dimensional_resistance",
            "kinetic_damage", "kinetic_resistance",
            "null_capacity", "null_resilience", "null_resistance", "null_damage");

    /** A Valoria attribute and the row, or vanilla attribute, that carries it here. */
    public record Renamed(String valoria, String elysium) {
    }

    /** Vanilla attributes a Valoria one maps onto instead of a row. */
    public static final List<String> VANILLA = List.of("block_break_speed");

    /**
     * Valoria's attributes, every one: the eighteen its language file names and
     * the five its code registered without words. Elements follow the
     * SPEC_VALORIA map (infernal is plasma, depth is dimensional, nature is
     * neural, nihility is the Null field); the ranged crit pair folds into the
     * one crit pair, because a bow and a blade should read the same stat.
     */
    public static final List<Renamed> VALORIA = List.of(
            new Renamed("dash_distance", "dash_distance"),
            new Renamed("attack_radius", "strike_radius"),
            new Renamed("excavation_speed", "block_break_speed"),
            new Renamed("summon_damage", "summon_damage"),
            new Renamed("necromancy_count", "summon_count"),
            new Renamed("necromancy_lifetime", "summon_lifetime"),
            new Renamed("elemental_resistance", "element_resistance"),
            new Renamed("nature_damage", "neural_damage"),
            new Renamed("nature_resistance", "neural_resistance"),
            new Renamed("depth_damage", "dimensional_damage"),
            new Renamed("depth_resistance", "dimensional_resistance"),
            new Renamed("infernal_damage", "plasma_damage"),
            new Renamed("infernal_resistance", "plasma_resistance"),
            new Renamed("nihility_damage", "null_damage"),
            new Renamed("nihility_resistance", "null_resistance"),
            new Renamed("max_nihility", "null_capacity"),
            new Renamed("nihility_resilience", "null_resilience"),
            new Renamed("dodge_chance", "dodge_chance"),
            new Renamed("miss_chance", "miss_chance"),
            new Renamed("crit_chance", "crit_chance"),
            new Renamed("crit_damage", "crit_damage"),
            new Renamed("ranged_crit_chance", "crit_chance"),
            new Renamed("ranged_crit_damage", "crit_damage"));

    public static ElysiumAttributeSpec named(String name) {
        for (ElysiumAttributeSpec s : ALL) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }
}
