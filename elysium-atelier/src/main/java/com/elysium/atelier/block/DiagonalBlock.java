package com.elysium.atelier.block;

import com.elysium.atelier.rules.DiagonalRules;

/**
 * One class for every diagonal.
 *
 * <p>{@link CourseBlock} with its boxes taken from {@link DiagonalRules}. The
 * facing it inherits is what turns the run from the north-west/south-east
 * corner to the north-east/south-west one, which is the only difference
 * between the two diagonals of a square and is why there are three of these
 * rather than six.
 */
public class DiagonalBlock extends CourseBlock {

    public DiagonalBlock(Properties properties, String diagonal) {
        super(properties, diagonal, DiagonalRules.boxesFor(diagonal));
    }
}
