package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.block.FossilBlock;
import com.elysium.arsenal.block.SiteBlock;
import com.elysium.arsenal.block.TotemBlock;
import com.elysium.arsenal.rules.DigRules;
import com.elysium.arsenal.rules.FossilRules;
import com.elysium.arsenal.rules.TotemRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the digging adds that is a block.
 *
 * <p>Three kinds, and each is walked from its own roster rather than listed:
 * the sites a brush works on, the twenty-one halves of seven fossils, and the
 * two totems a player sets down. The decorative sets at the bottom are the one
 * place a list is the roster, because a rotten fence is not a rule about
 * anything — but even those are built from one shape table, so a set with a
 * stair and no slab is a build failure rather than a gap somebody notices in a
 * year.
 */
public final class DigBlocks {

    private DigBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumArsenal.MODID);

    private static BlockBehaviour.Properties earth() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
                .strength(0.6F).sound(SoundType.ROOTED_DIRT);
    }

    private static BlockBehaviour.Properties bone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(1.4F).sound(SoundType.BONE_BLOCK).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties rotten() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                .strength(1.6F).sound(SoundType.WOOD).ignitedByLava();
    }

    private static BlockBehaviour.Properties mud() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN)
                .strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS)
                .requiresCorrectToolForDrops();
    }

    // ---- the sites -------------------------------------------------------

    public static final Map<String, DeferredBlock<Block>> SITES = registerSites();

    private static Map<String, DeferredBlock<Block>> registerSites() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (DigRules.Site site : DigRules.SITES) {
            made.put(site.block(), BLOCKS.register(site.block(),
                    () -> new SiteBlock(earth(), site.pool(), site.below())));
        }
        if (made.size() != DigRules.SITES.size()) {
            throw new IllegalStateException("the rules list " + DigRules.SITES.size()
                    + " sites and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    // ---- the fossils -----------------------------------------------------

    public static final Map<String, DeferredBlock<Block>> FOSSILS = registerFossils();

    private static Map<String, DeferredBlock<Block>> registerFossils() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            for (String part : FossilRules.PARTS) {
                String id = one.id(part);
                made.put(id, BLOCKS.register(id,
                        () -> new FossilBlock(one, part, bone())));
            }
        }
        if (made.size() != FossilRules.catalogue().size()) {
            throw new IllegalStateException("the roster lists "
                    + FossilRules.catalogue().size() + " fossil blocks and "
                    + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    // ---- the totems ------------------------------------------------------

    public static final Map<String, DeferredBlock<Block>> TOTEMS = registerTotems();

    private static Map<String, DeferredBlock<Block>> registerTotems() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (TotemRules.Totem one : TotemRules.of(TotemRules.PLACED)) {
            made.put(one.name(), BLOCKS.register(one.name(),
                    () -> new TotemBlock(one, BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD).strength(2.0F)
                            .sound(SoundType.METAL).lightLevel(state -> 10))));
        }
        if (made.size() != TotemRules.of(TotemRules.PLACED).size()) {
            throw new IllegalStateException("the rules list "
                    + TotemRules.of(TotemRules.PLACED).size()
                    + " placed totems and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    // ---- what a dig turns up that is only decoration ---------------------

    /** The shapes every stone-like set comes in. */
    public static final List<String> STONE_SHAPES = List.of("", "_slab", "_stairs");

    /** And every wood-like one. */
    public static final List<String> WOOD_SHAPES = List.of("_planks", "_slab",
            "_stairs", "_fence", "_fence_gate", "_door", "_trapdoor",
            "_pressure_plate");

    public static final DeferredBlock<Block> BONE_BLOCK =
            BLOCKS.register("chiseled_bone_block", () -> new Block(bone()));

    public static final DeferredBlock<Block> ARCHEOLOGY_TABLE =
            BLOCKS.register("archeology_table", () -> new Block(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.5F).sound(SoundType.WOOD)));

    public static final Map<String, DeferredBlock<Block>> ROTTEN = rotten("rotten");
    public static final Map<String, DeferredBlock<Block>> MUD = mudBricks();

    /**
     * The rotten set, every shape the game's own class for it.
     *
     * <p>Each shape also carries what the game's oak one has beyond its class,
     * as {@code ofFullCopy} gives elysium-flora's: fences, gates and plates
     * count as solid for what is set on them, a door and a plate break rather
     * than being pushed in half by a piston, and nothing spawns on a trapdoor.
     */
    private static Map<String, DeferredBlock<Block>> rotten(String name) {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        made.put(name + "_log", BLOCKS.register(name + "_log",
                () -> new net.minecraft.world.level.block.RotatedPillarBlock(rotten())));
        for (String shape : WOOD_SHAPES) {
            String id = name + shape;
            made.put(id, BLOCKS.register(id, () -> switch (shape) {
                case "_slab" -> new SlabBlock(rotten());
                case "_stairs" -> new StairBlock(
                        Blocks.OAK_PLANKS.defaultBlockState(), rotten());
                case "_fence" -> new FenceBlock(rotten().forceSolidOn());
                case "_fence_gate" -> new FenceGateBlock(WoodType.OAK,
                        rotten().forceSolidOn());
                case "_door" -> new DoorBlock(BlockSetType.OAK,
                        rotten().noOcclusion().pushReaction(PushReaction.DESTROY));
                case "_trapdoor" -> new TrapDoorBlock(BlockSetType.OAK,
                        rotten().noOcclusion().isValidSpawn(Blocks::never));
                case "_pressure_plate" -> new PressurePlateBlock(BlockSetType.OAK,
                        rotten().forceSolidOn().noCollission()
                                .pushReaction(PushReaction.DESTROY));
                default -> new Block(rotten());
            }));
        }
        if (made.size() != WOOD_SHAPES.size() + 1) {
            throw new IllegalStateException("a wooden set is " + WOOD_SHAPES.size()
                    + " shapes and a log, and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredBlock<Block>> mudBricks() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String shape : STONE_SHAPES) {
            String id = "cracked_mud_brick" + (shape.isEmpty() ? "s" : shape);
            made.put(id, BLOCKS.register(id, () -> switch (shape) {
                case "_slab" -> new SlabBlock(mud());
                case "_stairs" -> new StairBlock(
                        Blocks.MUD_BRICKS.defaultBlockState(), mud());
                default -> new Block(mud());
            }));
        }
        made.put("infested_mud_bricks", BLOCKS.register("infested_mud_bricks",
                () -> new net.minecraft.world.level.block.InfestedBlock(
                        Blocks.MUD_BRICKS, mud().strength(0.0F))));
        if (made.size() != STONE_SHAPES.size() + 1) {
            throw new IllegalStateException("a stone set is " + STONE_SHAPES.size()
                    + " shapes and an infested one, and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    /** Every block this file registers, in one list. */
    public static Map<String, DeferredBlock<Block>> all() {
        Map<String, DeferredBlock<Block>> everything = new LinkedHashMap<>();
        everything.putAll(SITES);
        everything.putAll(FOSSILS);
        everything.putAll(TOTEMS);
        everything.putAll(ROTTEN);
        everything.putAll(MUD);
        everything.put("chiseled_bone_block", BONE_BLOCK);
        everything.put("archeology_table", ARCHEOLOGY_TABLE);
        return Map.copyOf(everything);
    }
}
