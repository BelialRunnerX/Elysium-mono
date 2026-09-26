package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.GraveBlock;
import com.elysium.comforts.block.ChimeBlock;
import com.elysium.comforts.block.RestBlock;
import com.elysium.comforts.block.RopeBlock;
import com.elysium.comforts.block.MailboxBlock;
import com.elysium.comforts.block.PigeonholeBlock;
import com.elysium.comforts.block.StationBlock;
import com.elysium.comforts.rules.ChimeRules;
import com.elysium.comforts.rules.RestRules;
import com.elysium.comforts.rules.PostRules;
import com.elysium.comforts.rules.StationRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The grave, and thirty-two places to sleep that are not a bed.
 *
 * <p>Every one of the thirty-two is walked out of {@link RestRules#roster()},
 * so a seventeenth colour is a row in the rules and nothing here changes. A
 * register that names its blocks by hand is a register with the thirty-second
 * missing, and the symptom is one dye that has nothing to make with it.
 */
public final class ComfortBlocks {

    private ComfortBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumComforts.MODID);

    /**
     * Soft enough to break by hand, because the person most likely to want
     * one gone is the person who just died beside it and has no tools.
     */
    public static final DeferredBlock<Block> GRAVE = BLOCKS.register("grave",
            () -> new GraveBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.STONE)
                    .strength(1.0F, 3_600_000.0F)
                    .noOcclusion()));

    /** The rope and nail a hammock hangs from. */
    public static final DeferredBlock<Block> ROPE = BLOCKS.register(RestRules.ROPE,
            () -> new RopeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE)
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(0.4F)
                    .noOcclusion()));

    /** Every bag and every hammock, in every colour, walked from the rules. */
    public static final java.util.Map<String, DeferredBlock<RestBlock>> RESTS = rests();

    private static java.util.Map<String, DeferredBlock<RestBlock>> rests() {
        java.util.Map<String, DeferredBlock<RestBlock>> made =
                new java.util.LinkedHashMap<>();
        for (String kind : RestRules.KINDS) {
            for (String colour : RestRules.COLOURS) {
                String name = colour + "_" + kind;
                made.put(name, BLOCKS.register(name,
                        () -> new RestBlock(BlockBehaviour.Properties
                                .ofFullCopy(Blocks.WHITE_WOOL)
                                .mapColor(MapColor.WOOL)
                                .sound(SoundType.WOOL)
                                .strength(0.2F)
                                .noOcclusion(), kind)));
            }
        }
        // Thirty-one of thirty-two is a pack that works until somebody dyes
        // the thirty-second, which is a month later and a bug report nobody
        // can reproduce. Counting here turns it into a game that does not
        // start.
        int wanted = RestRules.KINDS.size() * RestRules.COLOURS.size();
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " place(s) to sleep and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** Every chime, in every material and every colour, walked from the rules. */
    public static final java.util.Map<String, DeferredBlock<ChimeBlock>> CHIMES = chimes();

    private static java.util.Map<String, DeferredBlock<ChimeBlock>> chimes() {
        java.util.Map<String, DeferredBlock<ChimeBlock>> made =
                new java.util.LinkedHashMap<>();
        for (String material : ChimeRules.MATERIALS) {
            for (String colour : RestRules.COLOURS) {
                String name = colour + "_" + material + "_chimes";
                made.put(name, BLOCKS.register(name,
                        () -> new ChimeBlock(BlockBehaviour.Properties
                                .ofFullCopy(Blocks.CHAIN)
                                .mapColor(MapColor.METAL)
                                .sound(SoundType.CHAIN)
                                .strength(0.3F)
                                .noOcclusion(), material)));
            }
        }
        int wanted = ChimeRules.MATERIALS.size() * RestRules.COLOURS.size();
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " chime(s) and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** The eight stations, walked out of the rules. */
    public static final java.util.Map<String, DeferredBlock<StationBlock>> STATIONS =
            stations();

    private static java.util.Map<String, DeferredBlock<StationBlock>> stations() {
        java.util.Map<String, DeferredBlock<StationBlock>> made =
                new java.util.LinkedHashMap<>();
        for (StationRules.Station station : StationRules.STATIONS) {
            made.put(station.id(), BLOCKS.register(station.id(),
                    () -> new StationBlock(BlockBehaviour.Properties
                            .ofFullCopy(Blocks.OAK_PLANKS)
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(2.0F)
                            .noOcclusion(), station.id())));
        }
        if (made.size() != StationRules.STATIONS.size()) {
            throw new IllegalStateException("the rules ask for "
                    + StationRules.STATIONS.size() + " station(s) and "
                    + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** One pigeonhole per wood the pack builds in, walked from the rules. */
    public static final java.util.Map<String, DeferredBlock<PigeonholeBlock>>
            PIGEONHOLES = pigeonholes();

    private static java.util.Map<String, DeferredBlock<PigeonholeBlock>> pigeonholes() {
        java.util.Map<String, DeferredBlock<PigeonholeBlock>> made =
                new java.util.LinkedHashMap<>();
        for (String wood : PostRules.WOODS) {
            String name = PostRules.pigeonhole(wood);
            made.put(name, BLOCKS.register(name,
                    () -> new PigeonholeBlock(BlockBehaviour.Properties
                            .ofFullCopy(Blocks.OAK_PLANKS)
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(1.5F)
                            .noOcclusion(), wood)));
        }
        if (made.size() != PostRules.WOODS.size()) {
            throw new IllegalStateException("the pack builds in "
                    + PostRules.WOODS.size() + " wood(s) and " + made.size()
                    + " pigeonhole(s) were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** Where mail lands. */
    public static final DeferredBlock<MailboxBlock> MAILBOX = BLOCKS.register("mailbox",
            () -> new MailboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(2.0F)
                    .noOcclusion()));
}
