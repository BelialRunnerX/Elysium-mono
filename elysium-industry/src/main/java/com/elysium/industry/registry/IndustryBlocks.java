package com.elysium.industry.registry;

import com.elysium.industry.ElysiumIndustry;
import com.elysium.industry.block.AlternatorBlock;
import com.elysium.industry.block.ArcFurnaceBlock;
import com.elysium.industry.block.CrusherBlock;
import com.elysium.industry.block.DieselEngineBlock;
import com.elysium.industry.block.MotorBlock;
import com.elysium.industry.block.RollingMillBlock;
import com.elysium.industry.block.SeparatorBlock;
import com.elysium.industry.block.SteamTurbineBlock;
import com.elysium.industry.block.TransformerBlock;
import com.elysium.industry.block.WireBlock;
import com.elysium.industry.rules.IndustryRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block Age II is made of.
 *
 * <p>Twelve, and they divide into four jobs: three wires and a transformer to
 * carry it, two engines to make it, two spindles to trade it with the age
 * below, and four machines to spend it on. There is no fifth wire and no
 * second transformer, because the tiers are the ladder and a ladder with
 * optional rungs is a ladder nobody climbs in order.
 */
public final class IndustryBlocks {

    private IndustryBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumIndustry.MODID);

    private static BlockBehaviour.Properties wire() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                .mapColor(MapColor.COLOR_ORANGE)
                .sound(SoundType.COPPER)
                .strength(1.0F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(4.0F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties heavy() {
        return steel().strength(5.0F);
    }

    // --- carrying it ---------------------------------------------------

    public static final DeferredBlock<Block> COPPER_WIRE = BLOCKS.register("copper_wire",
            () -> new WireBlock(wire(), IndustryRules.Voltage.LOW));

    public static final DeferredBlock<Block> GOLD_WIRE = BLOCKS.register("gold_wire",
            () -> new WireBlock(wire().mapColor(MapColor.GOLD), IndustryRules.Voltage.MEDIUM));

    public static final DeferredBlock<Block> STEEL_WIRE = BLOCKS.register("steel_wire",
            () -> new WireBlock(wire().mapColor(MapColor.COLOR_GRAY).sound(SoundType.METAL),
                    IndustryRules.Voltage.HIGH));

    public static final DeferredBlock<Block> TRANSFORMER = BLOCKS.register("transformer",
            () -> new TransformerBlock(heavy()));

    // --- making it -----------------------------------------------------

    public static final DeferredBlock<Block> STEAM_TURBINE = BLOCKS.register("steam_turbine",
            () -> new SteamTurbineBlock(heavy()));

    public static final DeferredBlock<Block> DIESEL_ENGINE = BLOCKS.register("diesel_engine",
            () -> new DieselEngineBlock(heavy()));

    // --- trading it with the age below ---------------------------------

    public static final DeferredBlock<Block> ALTERNATOR = BLOCKS.register("alternator",
            () -> new AlternatorBlock(steel()));

    public static final DeferredBlock<Block> MOTOR = BLOCKS.register("motor",
            () -> new MotorBlock(steel()));

    // --- spending it ---------------------------------------------------

    public static final DeferredBlock<Block> CRUSHER = BLOCKS.register("crusher",
            () -> new CrusherBlock(steel()));

    public static final DeferredBlock<Block> ROLLING_MILL = BLOCKS.register("rolling_mill",
            () -> new RollingMillBlock(steel()));

    public static final DeferredBlock<Block> ARC_FURNACE = BLOCKS.register("arc_furnace",
            () -> new ArcFurnaceBlock(heavy()));

    public static final DeferredBlock<Block> SEPARATOR = BLOCKS.register("separator",
            () -> new SeparatorBlock(heavy()));
}
