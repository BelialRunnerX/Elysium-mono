package com.elysium.atelier.registry;

import com.elysium.atelier.ElysiumAtelier;
import com.elysium.atelier.block.ShapedBlock;
import com.elysium.atelier.rules.AtelierRules;
import com.elysium.atelier.rules.JoinRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two hundred blocks, registered from the catalogue rather than typed.
 *
 * <h2>Why this file is forty lines and not two thousand</h2>
 *
 * Because twelve shapes times sixteen materials is a loop, and the moment it
 * is written as two hundred declarations it is two hundred chances to give one
 * of them the wrong sound, the wrong hardness or somebody else's name. A
 * thirteenth shape is a line in {@link AtelierRules#SHAPES} and nothing here
 * changes at all.
 */
public final class AtelierBlocks {

    private AtelierBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAtelier.MODID);

    /**
     * The shapes that stand along an axis rather than facing a way.
     *
     * <p>One, and it is a full block like the game's own pillars. A beam is
     * not here because a beam is thin, and a thin block with a full block's
     * collision is a block you cannot walk under and can see through.
     */
    private static final List<String> UPRIGHT = List.of("pillar");

    /** Everything, by its id, in the order the catalogue lists it. */
    public static final Map<String, DeferredBlock<Block>> ALL = register();

    private static BlockBehaviour.Properties like(AtelierRules.Material material) {
        return material.soft()
                ? BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                        .sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion()
                : BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                        .sound(SoundType.STONE).strength(2.0F, 6.0F).noOcclusion();
    }

    /**
     * The five kinds whose whole point is meeting their neighbours.
     *
     * <p>Separate from {@link #ALL} because they are a different question. A
     * chair is a shape; a fence is a <em>relation</em>, and everything that
     * can go wrong with it goes wrong between blocks rather than inside one.
     * See {@link JoinRules}.
     *
     * <p>Every one of them is a vanilla class with a vanilla block set behind
     * it, which is not laziness: the joining logic in those classes is what
     * the rest of the game's fences and doors also run, so ours join theirs
     * for the same reason theirs join each other. What this module supplies is
     * the part that is always got wrong — which tags each one goes in.
     */
    /**
     * A stone block set that a hand does not open.
     *
     * <p>Not {@code BlockSetType.STONE}: that one <em>does</em> open by hand —
     * it is what stone buttons and pressure plates are built on, and the game
     * has no stone door for it to have been wrong about. Building our doors on
     * it made them openable by hand while the rules said they were not, which
     * is precisely the disagreement the guard below exists to catch, and it
     * caught it: at registry time, four seconds into loading, as a crash.
     *
     * <p>Not {@code BlockSetType.IRON} either, which does not open by hand and
     * sounds like a bank vault. This is stone's own sounds with iron's answer
     * to a hand, which is the block we actually wanted.
     */
    public static final BlockSetType HARD = BlockSetType.register(new BlockSetType(
            ElysiumAtelier.MODID + "_hard",
            false,
            BlockSetType.STONE.canOpenByWindCharge(),
            BlockSetType.STONE.canButtonBeActivatedByArrows(),
            BlockSetType.STONE.pressurePlateSensitivity(),
            BlockSetType.STONE.soundType(),
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            BlockSetType.STONE.pressurePlateClickOff(),
            BlockSetType.STONE.pressurePlateClickOn(),
            BlockSetType.STONE.buttonClickOff(),
            BlockSetType.STONE.buttonClickOn()));

    public static final Map<String, DeferredBlock<Block>> OPENINGS = openings();

    private static Map<String, DeferredBlock<Block>> openings() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            BlockBehaviour.Properties properties = like(opening.material()).noOcclusion();
            BlockSetType set = opening.soft() ? BlockSetType.OAK : HARD;

            // The game's own block set knows whether a hand opens it, and so
            // do our rules. Two answers to one question is the bug this whole
            // class of block is prone to, so they are made to agree here,
            // loudly, at load time rather than at somebody's front door.
            if (set.canOpenByHand() != JoinRules.openedByHand(opening)
                    && (JoinRules.DOOR.equals(opening.kind())
                    || JoinRules.TRAPDOOR.equals(opening.kind()))) {
                throw new IllegalStateException(opening.id() + " is built on a block "
                        + "set that " + (set.canOpenByHand() ? "does" : "does not")
                        + " open by hand and the rules say it "
                        + (JoinRules.openedByHand(opening) ? "does" : "does not"));
            }

            made.put(opening.id(), switch (opening.kind()) {
                case JoinRules.DOOR -> BLOCKS.register(opening.id(),
                        () -> new DoorBlock(set, properties.strength(3.0F)
                                .pushReaction(PushReaction.DESTROY)));
                case JoinRules.TRAPDOOR -> BLOCKS.register(opening.id(),
                        () -> new TrapDoorBlock(set, properties.strength(3.0F)
                                .isValidSpawn((state, getter, pos, type) -> false)));
                case JoinRules.GATE -> BLOCKS.register(opening.id(),
                        () -> new FenceGateBlock(properties.strength(2.0F, 3.0F),
                                opening.soft() ? SoundEvents.FENCE_GATE_OPEN
                                        : SoundEvents.IRON_DOOR_OPEN,
                                opening.soft() ? SoundEvents.FENCE_GATE_CLOSE
                                        : SoundEvents.IRON_DOOR_CLOSE));
                case JoinRules.FENCE -> BLOCKS.register(opening.id(),
                        () -> new FenceBlock(properties.strength(2.0F, 3.0F)));
                case JoinRules.PANE -> BLOCKS.register(opening.id(),
                        () -> new IronBarsBlock(properties.strength(1.0F, 3.0F)));
                default -> throw new IllegalStateException(opening.kind());
            });
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredBlock<Block>> register() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
                String id = material.name() + "_" + shape.name();
                BlockBehaviour.Properties properties = like(material);
                made.put(id, UPRIGHT.contains(shape.name())
                        ? BLOCKS.register(id, () -> new RotatedPillarBlock(properties))
                        : BLOCKS.register(id, () -> new ShapedBlock(properties,
                                shape.name())));
            }
        }
        return Map.copyOf(made);
    }
}
