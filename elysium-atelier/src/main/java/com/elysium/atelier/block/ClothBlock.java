package com.elysium.atelier.block;

import com.elysium.atelier.rules.ClothRules;

/**
 * One class for every piece of cloth.
 *
 * <p>{@link CourseBlock} with its boxes taken from {@link ClothRules}, for the
 * same reason {@link TrimBlock} is: a facing, waterlogging and a collision box
 * turned to match are the same three things every time, and three copies of
 * the rotation is three places for one off-by-one.
 */
public class ClothBlock extends CourseBlock {

    public ClothBlock(Properties properties, String cloth) {
        super(properties, cloth, ClothRules.boxesFor(cloth));
    }
}
