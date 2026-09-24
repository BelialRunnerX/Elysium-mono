package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * A line with gears in it, as arithmetic.
 *
 * <h2>What a "line" really is</h2>
 *
 * {@link KineticRules#settle} takes sources and loads that are all turning at
 * one speed. Put a pair of cogs anywhere in a machine hall and that stops
 * being true: the half past the cogs turns three times as fast, backwards.
 *
 * <p>So a line is not one speed, it is a <b>tree of speeds</b>. Every part of
 * it turns at some fixed multiple of every other part, and that multiple never
 * changes while the machines stay where they are. Pick any shaft, call it the
 * root, and every other shaft has a <b>ratio</b> to it — the number you
 * multiply the root's rpm by to get that shaft's rpm — and a count of how many
 * gear stages away it is.
 *
 * <p>Then the whole thing collapses back to one problem: reflect every source
 * and every load onto the root through its own ratio (which is what
 * {@link KineticRules.Source#through} and {@link KineticRules.Load#through}
 * do), settle the root, and read every other shaft's speed back off the root's.
 *
 * <h2>Jamming</h2>
 *
 * The failure this introduces is a loop. Run a shaft out through a 3:1
 * step-up, round the hall, and back into where it started, and you have built
 * something that insists one shaft turns at both one and three times its own
 * speed. In a real machine hall the teeth strip. Here the line <b>jams</b>:
 * nothing turns, and the block that closed the loop is the one that says so.
 *
 * <p>Two paths that arrive at the same ratio are fine and common — a loop of
 * plain shaft is a loop, not a fault. Two paths that disagree are a jam. So the
 * rule is about the ratios and not about the shape.
 *
 * <h2>Why this is in rules/</h2>
 *
 * Nothing here knows what a block is. The traversal that finds the neighbours
 * lives in the block layer and hands its results to this, so the part that can
 * be wrong — composition, conflict, reflection, reading a speed back — is the
 * part a harness can execute.
 */
public final class LineRules {

    private LineRules() {
    }

    /**
     * How close two ratios have to be to count as the same.
     *
     * Ratios are built by multiplying floats together — a 3:1 into a 1:3 into
     * a 3:1 does not come back to exactly 3.0 — so an equality test would jam
     * a line that is perfectly sound. Relative rather than absolute, because
     * a gear train can reach ratios in the hundreds and a fixed epsilon would
     * be far too strict at one end and far too loose at the other.
     */
    public static final float SAME_RATIO = 1.0e-4F;

    /**
     * How far a line may run before it is not one machine any more.
     *
     * A bound on traversal rather than a rule of the game: a thousand blocks
     * of shaft is already a build nobody has made, and a network that walks
     * without a limit is a server tick that walks without a limit.
     */
    public static final int MOST_MEMBERS = 1024;

    /** Whether two ratios arriving at one shaft disagree about how fast it turns. */
    public static boolean conflict(float a, float b) {
        float size = Math.max(Math.abs(a), Math.abs(b));
        if (size <= 0.0F) {
            return false;
        }
        return Math.abs(a - b) / size > SAME_RATIO;
    }

    /** One more gear stage on the way out from the root. */
    public static float compose(float ratioToHere, float acrossTheTeeth) {
        return ratioToHere * acrossTheTeeth;
    }

    /** What a shaft at this ratio is actually turning at, given the root's speed. */
    public static float speedAt(float rootRpm, float ratio) {
        return rootRpm * ratio;
    }

    /**
     * Something on the line, and where on it.
     *
     * @param ratio  what to multiply the root's speed by to get this one's
     * @param stages how many gear stages lie between this and the root
     */
    public record At(float ratio, int stages) {

        public At {
            if (ratio == 0.0F) {
                throw new IllegalArgumentException("a member geared to a standstill is not "
                        + "on the line at all");
            }
            if (stages < 0) {
                throw new IllegalArgumentException("a member cannot be a negative number of "
                        + "gears from the root");
            }
        }

        /** The root itself. */
        public static final At ROOT = new At(1.0F, 0);
    }

    /** A source, and where on the line it sits. */
    public record Driven(KineticRules.Source source, At at) {
    }

    /** A load, and where on the line it sits. */
    public record Drawn(KineticRules.Load load, At at) {
    }

    /**
     * What a whole line is doing.
     *
     * @param root    the reading at the root shaft
     * @param jammed  true when two paths disagreed about how fast a shaft turns
     * @param members how many blocks the line was assembled from
     */
    public record Assembly(KineticRules.Reading root, boolean jammed, int members) {

        /** A line that is turning, holding together, and not fighting itself. */
        public boolean running() {
            return !jammed && root.running();
        }

        /** What a member at this position is turning at. Zero if the line is not. */
        public float speedOf(At at) {
            return running() ? speedAt(root.rpm(), at.ratio()) : 0.0F;
        }
    }

    /**
     * Reflect everything onto the root and settle it.
     *
     * <p>The shaft rating is checked against the <em>fastest</em> member, not
     * against the root: a timber shaft three gear stages out from a water
     * wheel is the thing that shears, and the root may be turning slowly
     * enough to look perfectly safe while it happens.
     *
     * @param shaft the weakest material anywhere on the line
     */
    public static Assembly assemble(List<Driven> sources, List<Drawn> loads,
                                    KineticRules.Shaft shaft, boolean jammed) {
        int members = sources.size() + loads.size();
        if (jammed) {
            return new Assembly(new KineticRules.Reading(0.0F, 0.0F, 0.0F, false, false),
                    true, members);
        }

        List<KineticRules.Source> reflected = new ArrayList<>(sources.size());
        float fastest = 1.0F;
        for (Driven driven : sources) {
            reflected.add(driven.source().through(driven.at().ratio(), driven.at().stages()));
            fastest = Math.max(fastest, Math.abs(driven.at().ratio()));
        }
        List<KineticRules.Load> drawn = new ArrayList<>(loads.size());
        for (Drawn load : loads) {
            drawn.add(load.load().through(load.at().ratio(), load.at().stages()));
            fastest = Math.max(fastest, Math.abs(load.at().ratio()));
        }

        // Settle the root with shearing switched off, then ask the question
        // again at the speed the fastest shaft on the line actually reaches.
        KineticRules.Reading atRoot =
                KineticRules.settle(reflected, drawn, KineticRules.Shaft.ASCENDANT);
        boolean sheared = KineticRules.shears(shaft, atRoot.rpm() * fastest);
        return new Assembly(new KineticRules.Reading(atRoot.rpm(), atRoot.demand(),
                atRoot.capacity(), atRoot.stalled(), sheared), false, members);
    }

    /**
     * The speed the fussiest stopped machine on the line is waiting for, at
     * its own position rather than at the root.
     *
     * <p>{@link KineticRules#waitingFor} answers for one speed. A line with
     * gears in it has many, and the number worth showing a player is the one
     * written on the machine that stopped — not that number divided by three
     * because there happened to be a cog in between.
     *
     * @return the least speed of the fussiest stopped machine, or 0 if all work
     */
    public static float waitingFor(List<Drawn> loads, Assembly assembly) {
        float wanted = 0.0F;
        for (Drawn drawn : loads) {
            float here = assembly.speedOf(drawn.at());
            if (!drawn.load().working(here) && drawn.load().leastRpm() > wanted) {
                wanted = drawn.load().leastRpm();
            }
        }
        return wanted;
    }

    /**
     * Whether a line of this many members is bigger than we are willing to walk.
     *
     * The block layer stops when this says so and reports the line as jammed,
     * which is the honest outcome: a network too big to resolve is a network
     * whose speed nobody can state.
     */
    public static boolean tooBig(int members) {
        return members > MOST_MEMBERS;
    }
}
