package com.elysium.core.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class AetheriumOreBlock extends Block {

    public AetheriumOreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(4.0F, 800.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }
}
