package com.elysium.armory.property;

/**
 * One contribution to one property: from a material, a part, a trait.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public record ArmoryModifier(ArmoryOperation operation, double value) {

    public static ArmoryModifier average(double value) {
        return new ArmoryModifier(ArmoryOperation.AVERAGE, value);
    }

    public static ArmoryModifier add(double value) {
        return new ArmoryModifier(ArmoryOperation.ADD, value);
    }
}
