package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.BeeRules;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A block of comb for every bee, and a nest for every place one lives.
 *
 * <p>A comb block is storage: nine combs in, nine out, the same bargain every
 * resource in the game makes. A nest is where a place-bound bee is found, and
 * there is one per <em>place</em> rather than one per bee, because two bees
 * that live in the same biome do not need two different-looking homes.
 */
public final class BeeBlocks {

    private BeeBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAgronomy.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    private static BlockBehaviour.Properties waxy() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.HONEYCOMB_BLOCK)
                .mapColor(MapColor.COLOR_ORANGE)
                .sound(SoundType.CORAL_BLOCK)
                .strength(0.6F);
    }

    private static BlockBehaviour.Properties nesting() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.BEE_NEST)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(0.4F);
    }

    public static final Map<String, DeferredBlock<Block>> COMB_BLOCKS = registerCombs();

    public static final Map<String, DeferredBlock<Block>> NESTS = registerNests();

    private static Map<String, DeferredBlock<Block>> registerCombs() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            DeferredBlock<Block> block = BLOCKS.register(bee.combBlock(),
                    () -> new Block(waxy()));
            ITEMS.register(bee.combBlock(),
                    () -> new BlockItem(block.get(), new Item.Properties()));
            made.put(bee.id(), block);
        }
        if (made.size() != BeeRules.BEES.size()) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()
                    + " bees and " + made.size() + " comb block(s) were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredBlock<Block>> registerNests() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String home : BeeRules.homes()) {
            DeferredBlock<Block> block = BLOCKS.register(home + "_nest",
                    () -> new Block(nesting()));
            ITEMS.register(home + "_nest",
                    () -> new BlockItem(block.get(), new Item.Properties()));
            made.put(home, block);
        }
        // Every place a bee is bound to has somewhere for it to be found. A
        // bee with a home and no nest spawns nowhere, which is the same hole
        // as a bee for a metal that does not exist.
        if (made.size() != BeeRules.homes().size()) {
            throw new IllegalStateException("bees live in "
                    + BeeRules.homes().size() + " place(s) and " + made.size()
                    + " nest(s) were registered");
        }
        return Map.copyOf(made);
    }
}
