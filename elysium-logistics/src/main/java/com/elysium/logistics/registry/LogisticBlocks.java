package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.block.BusBlock;
import com.elysium.logistics.block.AssemblerBlock;
import com.elysium.logistics.block.AssemblyCoreBlock;
import com.elysium.logistics.block.ControllerBlock;
import com.elysium.logistics.block.DrawerBlock;
import com.elysium.logistics.block.TerminalBlock;
import com.elysium.logistics.rules.LogisticRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block storage is made of, so far.
 *
 * Eight: three drawers, a run of bus, the controller that indexes them, and
 * the three that make the warehouse build things for you — a core that plans,
 * assemblers that do it, and a terminal to ask at.
 *
 * <p>The drawers work the day they are crafted and cost nothing to run. The
 * controller is worth building the day a player has a grid. The last three
 * want a grid that can hold a charge, which is the next age up, and their
 * voltage says so rather than an advancement.
 */
public final class LogisticBlocks {

    private LogisticBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumLogistics.MODID);

    private static BlockBehaviour.Properties timber() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties brass() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                .mapColor(MapColor.GOLD)
                .sound(SoundType.COPPER)
                .strength(3.0F);
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(4.0F);
    }

    public static final DeferredBlock<Block> TIMBER_DRAWER = BLOCKS.register("timber_drawer",
            () -> new DrawerBlock(timber(), LogisticRules.TIMBER_DRAWER));

    public static final DeferredBlock<Block> BRASS_DRAWER = BLOCKS.register("brass_drawer",
            () -> new DrawerBlock(brass(), LogisticRules.BRASS_DRAWER));

    public static final DeferredBlock<Block> STEEL_DRAWER = BLOCKS.register("steel_drawer",
            () -> new DrawerBlock(steel(), LogisticRules.STEEL_DRAWER));

    public static final DeferredBlock<Block> BUS = BLOCKS.register("bus",
            () -> new BusBlock(steel().strength(2.0F).noOcclusion()));

    public static final DeferredBlock<Block> CONTROLLER = BLOCKS.register("controller",
            () -> new ControllerBlock(steel().strength(4.5F).noOcclusion()));

    public static final DeferredBlock<Block> ASSEMBLER = BLOCKS.register("assembler",
            () -> new AssemblerBlock(steel().strength(4.5F)));

    public static final DeferredBlock<Block> ASSEMBLY_CORE =
            BLOCKS.register("assembly_core",
                    () -> new AssemblyCoreBlock(steel().strength(5.0F)));

    public static final DeferredBlock<Block> TERMINAL = BLOCKS.register("terminal",
            () -> new TerminalBlock(steel().strength(3.5F)));
}
