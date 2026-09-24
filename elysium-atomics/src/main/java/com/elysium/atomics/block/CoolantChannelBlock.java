package com.elysium.atomics.block;

import net.minecraft.world.level.block.Block;

/**
 * A length of plumbing. It does nothing; being there is the whole of its job.
 *
 * <h2>Why a block and not a number on a screen</h2>
 *
 * Because the one decision this age asks a player to make is how much cooling
 * a reactor gets, and a decision you make by typing into a field is a decision
 * you make once and forget. Four of these round a core is a thing you can see
 * from across the room, a thing you can be half-way through, and a thing you
 * can be caught having skimped on.
 *
 * <p>A core counts however many touch it, up to
 * {@code AtomicRules.MOST_CHANNELS}, on any of its six faces — so the shape is
 * the player's and only the number matters.
 */
public class CoolantChannelBlock extends Block {

    public CoolantChannelBlock(Properties properties) {
        super(properties);
    }
}
