package com.elysium.atelier.block;

import com.elysium.atelier.rules.TrimRules;

/**
 * One class for every fitting.
 *
 * <p>Everything a fitting needs — a facing, waterlogging, a collision box
 * turned to match — a course needs too, so this is {@link CourseBlock} with
 * its boxes taken from {@link TrimRules} instead. The shape a player walks
 * into is the shape the harness proved was a ladder, a hollow or an edge, and
 * the shape the generator drew; there is one of it.
 */
public class TrimBlock extends CourseBlock {

    public TrimBlock(Properties properties, String fitting) {
        super(properties, fitting, TrimRules.boxesFor(fitting));
    }
}
