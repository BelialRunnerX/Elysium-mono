package com.elysium.armory.material;

import java.util.List;

/**
 * A trait a material grants, with the conditions it grants it under (Silent
 * Gear's trait conditions). What a trait <em>does</em> is P4's effect engine;
 * this is what a material file states about it, carried through the pipeline so
 * that engine has something to read.
 *
 * <p>Conditions are kept as the strings the file states ({@code ratio>=0.5},
 * {@code count>=2}, {@code on:axe}, {@code not:ratio>=0.67}). They are parsed
 * where they are used, not here, so a condition kind the engine does not know
 * yet is carried rather than dropped.
 *
 * <p>Imports nothing but java, and must not: it is executed by a harness.
 */
public record ArmoryTrait(String trait, int level, List<String> conditions) {

    public ArmoryTrait {
        conditions = List.copyOf(conditions);
    }

    public static ArmoryTrait of(String trait, int level) {
        return new ArmoryTrait(trait, level, List.of());
    }

    /** Whether this grant is unconditional. */
    public boolean always() {
        return conditions.isEmpty();
    }
}
