package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.block.BuddingGeodeBlock;
import com.elysium.arcana.block.GeodeClusterBlock;
import com.elysium.arcana.rules.GeodeRules;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Two hundred and ten blocks from a thirty-row table.
 *
 * <p>Seven shapes per material: a block, a budding block, three sizes of bud,
 * a cluster and tinted glass. The mod this replaces writes out twenty-nine
 * materials' worth by hand, and most of those materials do not exist in this
 * pack — so the table here is what the ground actually gives you and the
 * validator checks it against the foundry's own mined list.
 */
public final class GeodeBlocks {

    private GeodeBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumArcana.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArcana.MODID);

    /** How tall each step of the chain stands, in pixels, and how wide. */
    public static final int[] BUD_HEIGHT = {3, 4, 5, 7};
    public static final int[] BUD_ACROSS = {3, 4, 4, 6};

    private static BlockBehaviour.Properties solid() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.AMETHYST)
                .strength(1.5F);
    }

    private static BlockBehaviour.Properties glassy() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)
                .sound(SoundType.GLASS)
                .strength(0.4F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties crystal() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                .sound(SoundType.AMETHYST_CLUSTER)
                .strength(1.5F)
                .noOcclusion()
                .lightLevel(state -> 3);
    }

    public static final Map<String, DeferredBlock<Block>> ALL = register();

    private static Map<String, DeferredBlock<Block>> register() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String material : GeodeRules.materials()) {
            // The chain first, because the budding block is handed it and a
            // budding block that does not know what it grows is a block that
            // does nothing at all on a random tick.
            List<Supplier<Block>> chain = new ArrayList<>();
            for (int step = 0; step < GeodeRules.CHAIN.size(); step++) {
                String kind = GeodeRules.CHAIN.get(step);
                final int height = BUD_HEIGHT[step];
                final int across = BUD_ACROSS[step];
                DeferredBlock<Block> bud = simple(
                        new GeodeRules.Geode(material, kind).id(),
                        () -> new GeodeClusterBlock(crystal(), height, across));
                made.put(new GeodeRules.Geode(material, kind).id(), bud);
                chain.add(bud::get);
            }
            made.put(new GeodeRules.Geode(material, GeodeRules.BLOCK).id(),
                    simple(new GeodeRules.Geode(material, GeodeRules.BLOCK).id(),
                            () -> new Block(solid())));
            made.put(new GeodeRules.Geode(material, GeodeRules.GLASS).id(),
                    simple(new GeodeRules.Geode(material, GeodeRules.GLASS).id(),
                            () -> new Block(glassy())));
            made.put(new GeodeRules.Geode(material, GeodeRules.BUDDING).id(),
                    simple(new GeodeRules.Geode(material, GeodeRules.BUDDING).id(),
                            () -> new BuddingGeodeBlock(solid(), chain)));
        }
        if (made.size() != GeodeRules.catalogue().size()) {
            throw new IllegalStateException("the catalogue has "
                    + GeodeRules.catalogue().size() + " geode blocks and "
                    + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    private static DeferredBlock<Block> simple(String name,
                                               Supplier<Block> make) {
        DeferredBlock<Block> block = BLOCKS.register(name, make);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
