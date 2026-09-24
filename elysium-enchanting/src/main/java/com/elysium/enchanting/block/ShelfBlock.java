package com.elysium.enchanting.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * A shelf: a block whose entire job is the four numbers it gives a table.
 *
 * <h2>Why these exist when vanilla blocks already work</h2>
 *
 * The vanilla stat blocks are what a player builds with for the first half of
 * the game, and they are deliberately imperfect: every one of them answers one
 * question well and drags something else along with it. These are the made
 * ones — each is a pure statement of a single number, or of a pair nobody can
 * find together in the world, and each costs something a player has to have
 * been somewhere to get.
 *
 * <p>No behaviour, no block entity, no interaction. The number is in the data
 * file; the block is a thing to stack.
 */
public class ShelfBlock extends Block {

    public ShelfBlock(MapColor colour, int light) {
        super(BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(1.8F, 3.0F)
                .lightLevel(state -> light)
                .sound(SoundType.WOOD));
    }
}
