package com.elysium.core.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class VoidglassOreBlock extends Block {

    public VoidglassOreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(4.5F, 900.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.GLASS));
    }
}
