package com.elysium.core.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class NeutroniumBlock extends Block {

    public NeutroniumBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(7.0F, 1800.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }
}
