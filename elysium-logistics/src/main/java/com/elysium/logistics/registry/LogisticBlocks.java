package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.block.BusBlock;
import com.elysium.logistics.block.AssemblerBlock;
import com.elysium.logistics.block.AssemblyCoreBlock;
import com.elysium.logistics.block.ControllerBlock;
import com.elysium.logistics.block.DeviceBlock;
import com.elysium.logistics.block.DrawerBlock;
import com.elysium.logistics.block.TerminalBlock;
import com.elysium.logistics.rules.CompressRules;
import com.elysium.logistics.rules.LogisticRules;
import com.elysium.logistics.rules.NetRules;
import com.elysium.logistics.rules.PackRules;
import com.elysium.logistics.pack.PackBlock;
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
 * Every block storage is made of, so far.
 *
 * Eight: three drawers, a run of bus, the controller that indexes them, and
 * the three that make the warehouse build things for you — a core that plans,
 * assemblers that do it, and a terminal to ask at.
 *
 * <p>The drawers work the day they are crafted and cost nothing to run. The
 * controller is worth building the day a player has a grid. The last three
 * want a grid that can hold a charge, which is the next age up, and their
 * voltage says so rather than an advancement.
 */
public final class LogisticBlocks {

    private LogisticBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumLogistics.MODID);

    private static BlockBehaviour.Properties timber() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties brass() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                .mapColor(MapColor.GOLD)
                .sound(SoundType.COPPER)
                .strength(3.0F);
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(4.0F);
    }

    /**
     * Every compression, in every material.
     *
     * <p>Five hundred and thirty-one blocks from a loop over two lists, which
     * is the whole argument for writing it this way: the jar this replaces
     * declares four and a half thousand of them by hand, and a hand-written
     * catalogue that size has a block in it with the wrong hardness and
     * nobody has ever found it.
     *
     * <p>Hardness and blast resistance come off the tier rather than being
     * typed, so the ladder the harness proved climbs is the ladder a player
     * swings a pickaxe at.
     */
    /**
     * The seven devices that make a network reach anything outside itself.
     *
     * <p>Registered from the roster for the same reason everything else here
     * is: an eighth device is a line in {@link NetRules} and nothing in this
     * file changes at all.
     */
    public static final Map<String, DeferredBlock<Block>> DEVICES = devices();

    private static Map<String, DeferredBlock<Block>> devices() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (NetRules.Device device : NetRules.DEVICES) {
            made.put(device.name(), BLOCKS.register(device.name(),
                    () -> new DeviceBlock(steel().noOcclusion(), device)));
        }
        if (made.size() != NetRules.howMany()) {
            throw new IllegalStateException("the rules list " + NetRules.howMany()
                    + " devices and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    public static final Map<String, DeferredBlock<Block>> COMPRESSED = compressed();

    private static Map<String, DeferredBlock<Block>> compressed() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String material : CompressRules.MATERIALS) {
            for (int tier = 1; tier <= CompressRules.TIERS; tier++) {
                String id = CompressRules.idFor(material, tier);
                float hardness = CompressRules.hardness(tier);
                float resistance = CompressRules.resistance(tier);
                made.put(id, BLOCKS.register(id, () -> new Block(
                        BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                                .strength(hardness, resistance)
                                .requiresCorrectToolForDrops())));
            }
        }
        if (made.size() != CompressRules.howMany()) {
            throw new IllegalStateException("the rules list "
                    + CompressRules.howMany() + " compressions and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * The bodies that are put down rather than worn: a crate, a strongbox and
     * a vault. Walked off the same roster the bags are, so a fourth placed
     * body is a line in the rules and a block here without anything changing.
     */
    public static final Map<String, DeferredBlock<Block>> PLACED = placed();

    private static Map<String, DeferredBlock<Block>> placed() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (PackRules.Body body : PackRules.BODIES) {
            if (!body.carried()) {
                made.put(body.name(), BLOCKS.register(body.name(),
                        () -> new PackBlock(timber().strength(2.5F), body)));
            }
        }
        if (made.size() != PackBlock.placedBodies().size()) {
            throw new IllegalStateException("the rules list "
                    + PackBlock.placedBodies().size() + " placed bodies and "
                    + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    public static final DeferredBlock<Block> TIMBER_DRAWER = BLOCKS.register("timber_drawer",
            () -> new DrawerBlock(timber(), LogisticRules.TIMBER_DRAWER));

    public static final DeferredBlock<Block> BRASS_DRAWER = BLOCKS.register("brass_drawer",
            () -> new DrawerBlock(brass(), LogisticRules.BRASS_DRAWER));

    public static final DeferredBlock<Block> STEEL_DRAWER = BLOCKS.register("steel_drawer",
            () -> new DrawerBlock(steel(), LogisticRules.STEEL_DRAWER));

    public static final DeferredBlock<Block> BUS = BLOCKS.register("bus",
            () -> new BusBlock(steel().strength(2.0F).noOcclusion()));

    public static final DeferredBlock<Block> CONTROLLER = BLOCKS.register("controller",
            () -> new ControllerBlock(steel().strength(4.5F).noOcclusion()));

    public static final DeferredBlock<Block> ASSEMBLER = BLOCKS.register("assembler",
            () -> new AssemblerBlock(steel().strength(4.5F)));

    public static final DeferredBlock<Block> ASSEMBLY_CORE =
            BLOCKS.register("assembly_core",
                    () -> new AssemblyCoreBlock(steel().strength(5.0F)));

    public static final DeferredBlock<Block> TERMINAL = BLOCKS.register("terminal",
            () -> new TerminalBlock(steel().strength(3.5F)));
}
