package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.block.TransmuterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One block: the transmuter. */
public final class ArcanaBlocks {

    private ArcanaBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumArcana.MODID);

    public static final DeferredBlock<Block> TRANSMUTER = BLOCKS.register("transmuter",
            () -> new TransmuterBlock(BlockBehaviour.Properties
                    .ofFullCopy(Blocks.OBSIDIAN)
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)
                    .strength(5.0F, 1200.0F)));
}
