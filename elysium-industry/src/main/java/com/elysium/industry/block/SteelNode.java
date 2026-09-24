package com.elysium.industry.block;

import com.elysium.kinetics.block.KineticNode;
import com.elysium.kinetics.rules.KineticRules;

/**
 * Anything of the second age that sits on a line of the first.
 *
 * <h2>The one thing it says, and why it is worth a file</h2>
 *
 * A line's safe speed is the rating of the weakest thing on it, and the
 * default for a node that does not say what it is made of is timber. So an
 * Age II machine that forgot to answer would quietly cap a brass line at
 * timber's rating: the line would come apart at a speed the player had built
 * for, been told was safe, and could see written on the shaft.
 *
 * <p>That is a bug with no symptom until it costs somebody a machine hall, and
 * it is one missing method away at every point where Age II touches Age I.
 * Putting the answer here means there is one place to get it right and one
 * rule for the validator to check, instead of four classes that each have to
 * remember.
 */
public interface SteelNode extends KineticNode {

    /** Steel, which is what the second age is made of. */
    @Override
    default KineticRules.Shaft material() {
        return KineticRules.Shaft.STEEL;
    }
}
