package com.elysium.core.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The Rune Socket Table - a workstation variant tuned for socketing runes.
 * Shares the Reforge Table's block entity and menu.
 */
public class RuneSocketTableBlock extends ReforgeTableBlock {

    public RuneSocketTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }
}
