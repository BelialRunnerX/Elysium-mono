package com.elysium.wilds.registry;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.block.ValeFrameBlock;
import com.elysium.wilds.block.ValePortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The way into the Vale: a frame to build it from, and the gate that fills it. */
public final class WildBlocks {

    private WildBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumWilds.MODID);

    /**
     * The frame. Stone brick to mine, since that is what it is made of, but it
     * is what a gate hangs in, so breaking one closes the gate.
     */
    public static final DeferredBlock<Block> VALE_FRAME = BLOCKS.register("vale_frame",
            () -> new ValeFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /**
     * The gate itself.
     *
     * <p>Not an item and not a drop, on purpose: a gate a player can carry is a
     * gate with no frame and no ring to be known by, and every trip through it
     * would search and build a new Vale side.
     */
    public static final DeferredBlock<Block> VALE_PORTAL = BLOCKS.register("vale_portal",
            () -> new ValePortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollission()
                    .noLootTable()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)));
}
