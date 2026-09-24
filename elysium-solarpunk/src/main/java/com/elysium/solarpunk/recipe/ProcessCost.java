package com.elysium.solarpunk.recipe;

/**
 * Turning a recipe's total cost into what a machine spends each tick.
 *
 * <h2>Why this is not a method on the recipe</h2>
 *
 * It was. Touching {@code SolarpunkProcessRecipe} loads the {@code Recipe}
 * interface, whose static initialiser wants a bootstrapped Minecraft — so
 * checking this arithmetic meant standing up the whole game to divide two
 * integers, and a check that expensive is a check that gets skipped.
 *
 * It is also, on reflection, where the arithmetic belongs: nothing here is
 * about recipes. It is about the difference between the number a designer
 * writes down and the number a machine spends.
 */
public final class ProcessCost {

    private ProcessCost() {
    }

    /**
     * Energy per tick for a run costing {@code energy} over {@code time} ticks.
     *
     * <h3>Why it rounds up, and why the floor of one matters</h3>
     *
     * Recipes name a total because that is the figure a designer can compare
     * between two recipes; machines spend per tick. Round that division down
     * and any recipe costing less than one unit per tick is free — and the
     * cheapest recipe in a set is exactly where that lands, so the bug ships
     * as "the loom seems to run without power" and never gets reported as a
     * bug, only as a rumour.
     *
     * A zero-length run cannot divide by its time and costs its whole total at
     * once, which is the only answer that is not a crash or a gift.
     */
    public static int perTick(int energy, int time) {
        if (time <= 0) {
            return energy;
        }
        return Math.max(1, (energy + time - 1) / time);
    }
}
