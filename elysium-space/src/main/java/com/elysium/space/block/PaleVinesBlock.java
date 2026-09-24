package com.elysium.space.block;

import com.elysium.space.registry.SpaceBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVinesBlock;

/** Pale vines (Stellaris's moon vines): cave vines that grow into their own body block. */
public class PaleVinesBlock extends CaveVinesBlock {

    public PaleVinesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected Block getBodyBlock() {
        return SpaceBlocks.PALE_VINES_PLANT.get();
    }
}
