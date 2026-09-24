package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.GraveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One block: the grave. */
public final class ComfortBlocks {

    private ComfortBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumComforts.MODID);

    /**
     * Soft enough to break by hand, because the person most likely to want
     * one gone is the person who just died beside it and has no tools.
     */
    public static final DeferredBlock<Block> GRAVE = BLOCKS.register("grave",
            () -> new GraveBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.STONE)
                    .strength(1.0F, 3_600_000.0F)
                    .noOcclusion()));
}
