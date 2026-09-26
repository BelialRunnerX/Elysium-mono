package com.elysium.colonies.registry;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.settle.SettlementSeedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one block the world places for a settlement: the survey stake.
 *
 * No item, no recipe, no creative tab. A player cannot put a town's promise
 * in the ground; the world does, at the sites the structure set chooses.
 */
public final class SettleBlocks {

    private SettleBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumColonies.MODID);

    public static final DeferredBlock<Block> SETTLEMENT_SEED = BLOCKS.register(
            "settlement_seed", () -> new SettlementSeedBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)));
}
