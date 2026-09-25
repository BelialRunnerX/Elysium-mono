package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.rules.CordonMaterials;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The storage blocks of the Cordon's materials, registered from the roster.
 *
 * <p>A loop over {@link CordonMaterials#MATERIALS} for the same reason the ore
 * blocks are a loop over the catalogue: the list is a table, and a table typed
 * twice is two tables. A block of raw metal is a little softer than a block of
 * the ingots, as vanilla's are; a block of gems or shards rings like
 * amethyst.
 */
public final class MaterialBlocks {

    private MaterialBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumOres.MODID);

    private static final Map<String, DeferredBlock<Block>> BY_NAME = new LinkedHashMap<>();

    static {
        for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
            for (String name : material.blocks()) {
                BY_NAME.put(name, BLOCKS.register(name, () -> new Block(propertiesFor(material, name))));
            }
        }
    }

    private static BlockBehaviour.Properties propertiesFor(CordonMaterials.Material material, String name) {
        boolean raw = name.startsWith("raw_");
        boolean metal = material.kind() == CordonMaterials.Kind.METAL
                || material.kind() == CordonMaterials.Kind.ALLOY;
        if (material.kind() == CordonMaterials.Kind.CLOTH) {
            return BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(0.8F);
        }
        return BlockBehaviour.Properties.of()
                .requiresCorrectToolForDrops()
                .strength(raw ? 5.0F : metal ? 5.0F : 3.0F, raw ? 6.0F : metal ? 6.0F : 3.0F)
                .sound(raw ? SoundType.STONE : metal ? SoundType.METAL : SoundType.AMETHYST);
    }

    /** The block the roster produced under this name. */
    public static DeferredBlock<Block> of(String name) {
        return BY_NAME.get(name);
    }

    /** Every block, in roster order. */
    public static Map<String, DeferredBlock<Block>> all() {
        return Map.copyOf(BY_NAME);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
