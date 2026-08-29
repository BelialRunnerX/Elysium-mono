package com.elysium.core.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The Ascension Forge - a workstation variant tuned for combining two matching
 * pieces into a higher tier. Shares the Reforge Table's block entity and menu.
 */
public class AscensionForgeBlock extends ReforgeTableBlock {

    public AscensionForgeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(6.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)
                .sound(SoundType.ANVIL));
    }
}
