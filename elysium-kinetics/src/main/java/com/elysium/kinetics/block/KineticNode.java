package com.elysium.kinetics.block;

import com.elysium.kinetics.rules.KineticRules;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Anything a line can run through.
 *
 * <h2>Three questions and no more</h2>
 *
 * A block on a line is asked what axis it turns about, how many teeth it has,
 * and whether it gives torque or takes it. Everything else the traversal needs
 * — who is next to whom, what the ratio across a mesh is, whether two paths
 * agree — is worked out from those, in one place, rather than by each block
 * describing its own connection rules.
 *
 * <p>That matters more than it sounds. Create's connection logic lives partly
 * in the blocks and partly in the network, and the result is that adding a
 * block means reading both. Here a new machine implements this and is on the
 * network; a new kind of *connection* is a change to {@link KineticLine} and
 * nowhere else.
 */
public interface KineticNode {

    /** The axis this block turns about. A shaft's length; a cog's pin. */
    Direction.Axis axis();

    /**
     * How many teeth, or zero for anything that is not a cog.
     *
     * A block with teeth meshes sideways with another block with teeth. A
     * block without them only connects along its own axis, which is what makes
     * a shaft a shaft.
     */
    default int teeth() {
        return 0;
    }

    /**
     * Whether this block joins shafts whatever way they run.
     *
     * True for exactly one thing: the gearbox. Everything else turns about one
     * axis and only meets things that turn about the same one, which is what
     * makes a machine hall readable — you can see which way the power goes.
     * A gearbox is the exception a player builds on purpose when the power has
     * to turn a corner.
     */
    default boolean couplesAnyAxis() {
        return false;
    }

    /** What this is made of, which decides how fast it can turn before it shears. */
    default KineticRules.Shaft material() {
        return KineticRules.Shaft.TIMBER;
    }

    /** What this block drives the line with, or null if it drives nothing. */
    @Nullable
    default KineticRules.Source source() {
        return null;
    }

    /** What this block takes out of the line, or null if it takes nothing. */
    @Nullable
    default KineticRules.Load load() {
        return null;
    }

    /**
     * Told what the line settled at.
     *
     * Called on every member each time the line is solved, including with a
     * stopped reading when it jams or stalls, so nothing has to notice that it
     * stopped being told.
     *
     * @param rpm     this block's own speed, signed, not the root's
     * @param running whether the line as a whole is turning and intact
     */
    void driven(float rpm, boolean running);

    /** Ask for the line to be worked out again, because something changed. */
    void unsettle();
}
