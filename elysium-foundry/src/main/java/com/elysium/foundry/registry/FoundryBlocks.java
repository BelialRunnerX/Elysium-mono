package com.elysium.foundry.registry;

import com.elysium.foundry.ElysiumFoundry;
import com.elysium.foundry.block.AlloySmelterBlock;
import com.elysium.foundry.rules.FoundryRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The machine, and one casing per rung of the chain.
 *
 * A casing is not decoration: it is what a machine of that tier is built out
 * of, and the rule that it may never be made of something above its own rung
 * is what stops the ladder having a skippable step in it.
 */
public final class FoundryBlocks {

    private FoundryBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumFoundry.MODID);

    private static BlockBehaviour.Properties metal(float strength) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(strength);
    }

    public static final DeferredBlock<Block> ALLOY_SMELTER =
            BLOCKS.register("alloy_smelter", () -> new AlloySmelterBlock(metal(4.5F)));

    /** The second and last machine: recovery (docs/SPEC_FOUNDRY.md §3). */
    public static final DeferredBlock<Block> REFINERY =
            BLOCKS.register("refinery", () -> new com.elysium.foundry.block.RefineryBlock(metal(5.5F)));

    private static final Map<Integer, DeferredBlock<Block>> CASINGS = new LinkedHashMap<>();

    static {
        for (FoundryRules.Casing casing : FoundryRules.CASINGS) {
            CASINGS.put(casing.tier(), BLOCKS.register(name(casing.tier()),
                    () -> new Block(metal(3.0F + casing.tier()))));
        }
    }

    /** What a casing of that tier is called. */
    public static String name(int tier) {
        return "tier_" + tier + "_casing";
    }

    public static DeferredBlock<Block> casing(int tier) {
        return CASINGS.get(tier);
    }

    /** Every casing, in order. */
    public static Map<Integer, DeferredBlock<Block>> casings() {
        return Map.copyOf(CASINGS);
    }
}
