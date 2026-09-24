package com.elysium.space.block;

import com.elysium.space.registry.SpaceBlocks;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;

/** The body of pale vines. */
public class PaleVinesPlantBlock extends CaveVinesPlantBlock {

    public PaleVinesPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return (GrowingPlantHeadBlock) SpaceBlocks.PALE_VINES.get();
    }
}
