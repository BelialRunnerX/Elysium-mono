package com.elysium.atomics.registry;

import com.elysium.atomics.ElysiumAtomics;
import com.elysium.atomics.block.CellBlock;
import com.elysium.atomics.block.CentrifugeBlock;
import com.elysium.atomics.block.CoolantChannelBlock;
import com.elysium.atomics.block.EnricherBlock;
import com.elysium.atomics.block.ReactionChamberBlock;
import com.elysium.atomics.block.ReactorBlock;
import com.elysium.atomics.block.ServoBlock;
import com.elysium.atomics.block.SolarPanelBlock;
import com.elysium.atomics.rules.AtomicRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block Age III is made of.
 *
 * <p>Ten, in four jobs: three cells to keep it, a core and its plumbing and a
 * panel to make it, a servo to hand it back to the age below, and three
 * machines to spend it on. There is no fourth cell tier and no second reactor,
 * because the top cell already saturates the best wire in the game and one
 * core already needs that wire to itself.
 */
public final class AtomicBlocks {

    private AtomicBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAtomics.MODID);

    private static BlockBehaviour.Properties sealed() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.TERRACOTTA_WHITE)
                .sound(SoundType.METAL)
                .strength(5.0F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties heavy() {
        return sealed().strength(6.0F).mapColor(MapColor.COLOR_LIGHT_GRAY);
    }

    // --- keeping it ----------------------------------------------------

    public static final DeferredBlock<Block> BASIC_CELL = BLOCKS.register("basic_cell",
            () -> new CellBlock(sealed(), AtomicRules.BASIC_CELL));

    public static final DeferredBlock<Block> ADVANCED_CELL = BLOCKS.register("advanced_cell",
            () -> new CellBlock(sealed().mapColor(MapColor.COLOR_LIGHT_BLUE),
                    AtomicRules.ADVANCED_CELL));

    public static final DeferredBlock<Block> QUANTUM_CELL = BLOCKS.register("quantum_cell",
            () -> new CellBlock(sealed().mapColor(MapColor.COLOR_PURPLE),
                    AtomicRules.QUANTUM_CELL));

    // --- making it -----------------------------------------------------

    public static final DeferredBlock<Block> REACTOR_CORE = BLOCKS.register("reactor_core",
            () -> new ReactorBlock(heavy()));

    public static final DeferredBlock<Block> COOLANT_CHANNEL =
            BLOCKS.register("coolant_channel",
                    () -> new CoolantChannelBlock(sealed()
                            .mapColor(MapColor.COLOR_CYAN).strength(3.0F)));

    public static final DeferredBlock<Block> SOLAR_PANEL = BLOCKS.register("solar_panel",
            () -> new SolarPanelBlock(sealed().strength(2.0F)
                    .mapColor(MapColor.COLOR_BLUE)));

    // --- handing it back to the age below ------------------------------

    public static final DeferredBlock<Block> SERVO = BLOCKS.register("servo",
            () -> new ServoBlock(heavy()));

    // --- spending it ---------------------------------------------------

    public static final DeferredBlock<Block> ENRICHER = BLOCKS.register("enricher",
            () -> new EnricherBlock(heavy()));

    public static final DeferredBlock<Block> REACTION_CHAMBER =
            BLOCKS.register("reaction_chamber", () -> new ReactionChamberBlock(sealed()));

    public static final DeferredBlock<Block> CENTRIFUGE = BLOCKS.register("centrifuge",
            () -> new CentrifugeBlock(heavy()));
}
