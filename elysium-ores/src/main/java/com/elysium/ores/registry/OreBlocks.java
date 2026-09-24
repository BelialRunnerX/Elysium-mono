package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.data.OreCatalogue;
import com.elysium.ores.ore.ElysiumOreBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every ore block, registered from the generated catalogue rather than typed
 * out one static field at a time.
 *
 * <h2>Why a loop and not a hundred and fifty fields</h2>
 *
 * Because the list is derived, not designed. Its length is a fact about which
 * mods are installed and which stones exist, and it changes when the generator
 * is re-run against a different pack. A hundred and fifty hand-written fields
 * would be a hundred and fifty places for the table and the code to disagree.
 *
 * <p>The cost is that nothing else can refer to one of these by name at compile
 * time — and nothing needs to. They are looked up by the row that made them.
 */
public final class OreBlocks {

    private OreBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumOres.MODID);

    private static final Map<String, DeferredBlock<Block>> BY_NAME = new LinkedHashMap<>();

    static {
        for (OreCatalogue.Entry entry : OreCatalogue.ENTRIES) {
            BY_NAME.put(entry.name(), BLOCKS.register(entry.name(),
                    () -> new ElysiumOreBlock(entry, propertiesFor(entry))));
        }
    }

    /**
     * How the ore feels to mine: like the stone it is in.
     *
     * An ore in netherrack that mined like stone would feel wrong in a way a
     * player would notice without being able to say why, so the properties are
     * copied from the host rather than invented — and the strength is raised a
     * little for the rarer bands, which is the one thing vanilla also does.
     */
    private static BlockBehaviour.Properties propertiesFor(OreCatalogue.Entry entry) {
        Block host = switch (entry.host()) {
            case "deepslate" -> Blocks.DEEPSLATE;
            case "netherrack" -> Blocks.NETHERRACK;
            case "end_stone" -> Blocks.END_STONE;
            default -> Blocks.STONE;
        };
        return BlockBehaviour.Properties.ofFullCopy(host)
                .strength(3.0F + entry.band(), 3.0F)
                .requiresCorrectToolForDrops()
                .sound(host == Blocks.NETHERRACK ? SoundType.NETHERRACK : SoundType.STONE);
    }

    /** The block a catalogue row produced. */
    public static DeferredBlock<Block> of(String name) {
        return BY_NAME.get(name);
    }

    /** Every ore, in catalogue order. */
    public static Map<String, DeferredBlock<Block>> all() {
        return Map.copyOf(BY_NAME);
    }

    /** The id of one, for a tag or a report. */
    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumOres.MODID, name);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
