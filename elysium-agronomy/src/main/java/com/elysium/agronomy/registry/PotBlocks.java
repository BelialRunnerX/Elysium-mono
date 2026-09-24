package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.PotRules;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
 * A hundred and sixty-five pots, from three lists.
 *
 * <p>Sixteen dyes times three fired clays, plus seven stones, times three
 * fittings. Written out that would be a hundred and sixty-five declarations
 * and a hundred and sixty-five chances to leave one colour with two fittings
 * instead of three — which is precisely the kind of gap only the player who
 * wanted that colour ever finds.
 */
public final class PotBlocks {

    private PotBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAgronomy.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    private static BlockBehaviour.Properties fired(String material) {
        boolean stone = PotRules.STONES.contains(material);
        return BlockBehaviour.Properties
                .ofFullCopy(stone ? Blocks.STONE : Blocks.TERRACOTTA)
                .mapColor(stone ? MapColor.STONE : MapColor.COLOR_BROWN)
                .sound(stone ? SoundType.STONE : SoundType.DECORATED_POT)
                .strength(stone ? 1.8F : 1.25F)
                .noOcclusion();
    }

    /**
     * The soils this module makes rather than the game.
     *
     * <p>Five of the fourteen are ours: the rest are dirt, sand, podzol and
     * the like, which the game already has. A soil in the table that nothing
     * registers is a rung of the ladder a player can read about and never
     * build, so the two lists are checked against each other.
     */
    public static final java.util.List<String> OUR_SOILS =
            java.util.List.of("clay_bed", "gravel_bed", "rich_loam", "ashen_bed",
                    "void_silt");

    public static final Map<String, DeferredBlock<Block>> SOILS = registerSoils();

    private static Map<String, DeferredBlock<Block>> registerSoils() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String soil : OUR_SOILS) {
            DeferredBlock<Block> block = BLOCKS.register(soil,
                    () -> new Block(BlockBehaviour.Properties
                            .ofFullCopy(Blocks.DIRT)
                            .mapColor(MapColor.DIRT)
                            .sound(SoundType.GRAVEL)
                            .strength(0.6F)));
            ITEMS.register(soil,
                    () -> new BlockItem(block.get(), new Item.Properties()));
            made.put(soil, block);
        }
        if (made.size() != OUR_SOILS.size()) {
            throw new IllegalStateException("this module makes " + OUR_SOILS.size()
                    + " soil(s) and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    public static final Map<String, DeferredBlock<Block>> ALL = register();

    private static Map<String, DeferredBlock<Block>> register() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (PotRules.Pot pot : PotRules.catalogue()) {
            // A PotBlock, not a Block. A hundred and sixty-five pots that
            // cannot grow anything is a hundred and sixty-five ornaments, and
            // the difference is invisible until somebody plants in one.
            DeferredBlock<Block> block = BLOCKS.register(pot.id(),
                    () -> new com.elysium.agronomy.block.PotBlock(
                            fired(pot.material())));
            ITEMS.register(pot.id(),
                    () -> new BlockItem(block.get(), new Item.Properties()));
            made.put(pot.id(), block);
        }
        if (made.size() != PotRules.catalogue().size()) {
            throw new IllegalStateException("the catalogue has "
                    + PotRules.catalogue().size() + " pots and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }
}
