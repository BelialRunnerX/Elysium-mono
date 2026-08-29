package com.elysium.core.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class NeutroniumOreBlock extends Block {

    public NeutroniumOreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(6.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }
}
