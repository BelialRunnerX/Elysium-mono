package com.elysium.industry.recipe;

import com.elysium.industry.rules.IndustryRules;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Anything a machine on a grid can be asked to make.
 *
 * <h2>Why this is an interface and not just {@link IndustryRecipe}</h2>
 *
 * Because the ages above have their own machines, and a machine on a grid is
 * the same thing in all of them: one slot in, one slot out, a job that takes
 * a number of ticks at a full share and proportionally longer at less. What
 * differs is the catalogue and the name on the block.
 *
 * <p>So {@code PlantBlock} works against this rather than against Age II's own
 * recipe class, and Age III's enricher is a subclass with three answers in it
 * rather than a second copy of the same two hundred lines — which is the same
 * argument that made four Age II machines share one class, applied one level
 * up.
 *
 * <p>What it deliberately does <em>not</em> carry is a speed floor. A grid
 * divides rather than stalling: every machine on it gets the same share and a
 * starved one is slow rather than stopped, at every age. That is the sentence
 * Age I's {@code Pace} exists to express and no grid needs.
 */
public interface PlantRecipe extends Recipe<SingleRecipeInput> {

    /** How long this takes at a full share. Less share is proportionally longer. */
    int ticks();

    /** The whole of this job, in the work units a machine counts in. */
    default int work() {
        return IndustryRules.workFor(ticks());
    }

    /** The same thing said as a countdown, for anything that has to show one. */
    default int ticksAt(float share) {
        return IndustryRules.ticksAtShare(ticks(), share);
    }
}
