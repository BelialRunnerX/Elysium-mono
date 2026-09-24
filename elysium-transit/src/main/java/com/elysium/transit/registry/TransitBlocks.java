package com.elysium.transit.registry;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.block.WaystoneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What transit is made of, so far.
 *
 * One waystone and four road surfaces. The stone is the half that needs
 * power; the roads are the half that needs an afternoon.
 */
public final class TransitBlocks {

    private TransitBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumTransit.MODID);

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_TILES)
                .mapColor(MapColor.DEEPSLATE)
                .sound(SoundType.DEEPSLATE)
                .strength(4.0F);
    }

    private static BlockBehaviour.Properties paving() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(1.8F);
    }

    public static final DeferredBlock<Block> WAYSTONE = BLOCKS.register("waystone",
            () -> new WaystoneBlock(stone()));

    /**
     * Road, and the cheaper thing that is not road.
     *
     * <p>Two blocks rather than four, because two of the four surfaces in the
     * rules are whatever the world already is. A pack does not need to ship
     * "grass" and "ice"; it needs to ship the two you lay on purpose.
     */
    public static final DeferredBlock<Block> PAVING = BLOCKS.register("paving",
            () -> new Block(paving()));

    public static final DeferredBlock<Block> GRAVEL_PATH = BLOCKS.register("gravel_path",
            () -> new Block(paving().strength(1.0F)
                    .sound(SoundType.GRAVEL).mapColor(MapColor.STONE)));
}
