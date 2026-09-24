package com.elysium.armory.property;

/**
 * How one modifier to a property combines with the others.
 *
 * Applied in this order, and only this order — see {@link ArmoryCompute}. The
 * order is the whole character of the system: averages first, so the
 * materials a piece is made of set its baseline; multipliers next, so a
 * better binding makes a better head better; flat additions last, so a
 * trait's "+2 damage" means two damage whatever the materials were.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public enum ArmoryOperation {
    /** A value to be averaged with the others, weighted toward the stronger. */
    AVERAGE,
    /** A floor: the result is at least this. */
    MAX,
    /** A share of the averaged value, added. 0.1 is "+10% of the base". */
    MULTIPLY_BASE,
    /** A multiplier on everything so far. 0.1 is "x1.1". */
    MULTIPLY_TOTAL,
    /** A flat amount, added last. */
    ADD
}
