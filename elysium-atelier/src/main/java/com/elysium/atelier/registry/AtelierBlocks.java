package com.elysium.atelier.registry;

import com.elysium.atelier.ElysiumAtelier;
import com.elysium.atelier.block.ArchBlock;
import com.elysium.atelier.block.CourseBlock;
import com.elysium.atelier.block.ClothBlock;
import com.elysium.atelier.block.DiagonalBlock;
import com.elysium.atelier.block.TrimBlock;
import com.elysium.atelier.block.ShapedBlock;
import com.elysium.atelier.rules.ArchRules;
import com.elysium.atelier.rules.AtelierRules;
import com.elysium.atelier.rules.ClothRules;
import com.elysium.atelier.rules.CourseRules;
import com.elysium.atelier.rules.DiagonalRules;
import com.elysium.atelier.rules.JoinRules;
import com.elysium.atelier.rules.TrimRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * Every architectural piece, in every material.
     *
     * <p>A loop over two rosters, the same as everything else here. The ones
     * that have to reach their neighbours are built on the game's own pane
     * class, for the reason the openings are: the joining logic in that class
     * is what the rest of the game's panes run, so ours join theirs because
     * they are the same code rather than because somebody copied it.
     */
    public static final Map<String, DeferredBlock<Block>> PIECES = pieces();

    private static Map<String, DeferredBlock<Block>> pieces() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (ArchRules.Piece piece : ArchRules.PIECES) {
                String id = material.name() + "_" + piece.name();
                BlockBehaviour.Properties properties = like(material);
                if (piece.burns()) {
                    // Read off the piece rather than written here, so the
                    // ladder the rules prove climbs is the ladder a player
                    // sees in a dark room.
                    properties = properties.lightLevel(state -> piece.light());
                }
                BlockBehaviour.Properties settled = properties;
                made.put(id, piece.joins()
                        ? BLOCKS.register(id, () -> new IronBarsBlock(settled))
                        : BLOCKS.register(id, () -> new ArchBlock(settled, piece)));
            }
        }
        if (made.size() != ArchRules.howMany()) {
            throw new IllegalStateException("the rules list " + ArchRules.howMany()
                    + " architectural blocks and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * Every course, in every material the game has not already cut that way.
     *
     * <p>The four cuts the game already knows are registered as the game's own
     * classes. A stair that is a {@link StairBlock} turns its own corners, a
     * slab that is a {@link SlabBlock} stacks into a block, a wall that is a
     * {@link WallBlock} reaches the wall beside it, and a ladder that is a
     * {@link LadderBlock} is climbed — all of it the same code the rest of the
     * game runs, so ours behave like theirs because they <em>are</em> theirs.
     * Reimplementing any of it would be reimplementing its bugs as well.
     *
     * <p>What the game has no class for — a vertical slab, five ways up, a
     * balcony and a platform — is a {@link CourseBlock}, whose collision comes
     * out of the same boxes the harness proved walkable.
     */
    public static final Map<String, DeferredBlock<Block>> COURSES = courses();

    private static Map<String, DeferredBlock<Block>> courses() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (CourseRules.Course course : CourseRules.catalogue()) {
            String id = course.id();
            BlockBehaviour.Properties settled = like(course.material());
            String kind = course.kind();
            String from = course.material().block();
            made.put(id, switch (kind) {
                case CourseRules.STAIR -> BLOCKS.register(id,
                        () -> new StairBlock(cut(from), settled));
                case CourseRules.SLAB -> BLOCKS.register(id,
                        () -> new SlabBlock(settled));
                case CourseRules.WALL -> BLOCKS.register(id,
                        () -> new WallBlock(settled));
                case CourseRules.LADDER -> BLOCKS.register(id,
                        () -> new LadderBlock(settled));
                case CourseRules.RAILING -> BLOCKS.register(id,
                        () -> new IronBarsBlock(settled));
                default -> BLOCKS.register(id,
                        () -> new CourseBlock(settled, kind));
            });
        }
        if (made.size() != CourseRules.howMany()) {
            throw new IllegalStateException("the rules list " + CourseRules.howMany()
                    + " courses and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * The state a stair of this material is cut out of.
     *
     * <p>Looked up rather than written down: a stair's base state is what the
     * game uses for its sound, its map colour and its particles when it
     * breaks, and a stair of deepslate that breaks into stone particles is the
     * kind of wrong nobody reports and everybody notices.
     */
    private static net.minecraft.world.level.block.state.BlockState cut(String from) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(from))
                .defaultBlockState();
    }

    /**
     * Every fitting, in every material it can be made of.
     *
     * <p>The three that join are the game's own pane class, for the reason the
     * openings are: a banister that meets the banister beside it does so
     * because it is running the same code the game's own panes run. The rest
     * are a {@link TrimBlock}, drawn and collided out of the boxes the harness
     * proved were a ladder, a hollow or an edge.
     */
    public static final Map<String, DeferredBlock<Block>> FITTINGS = fittings();

    private static Map<String, DeferredBlock<Block>> fittings() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (TrimRules.Fitting fitting : TrimRules.catalogue()) {
            String id = fitting.id();
            TrimRules.Kind kind = TrimRules.kind(fitting.kind());
            BlockBehaviour.Properties settled = like(fitting.material());
            String name = fitting.kind();
            made.put(id, kind.joins()
                    ? BLOCKS.register(id, () -> new IronBarsBlock(settled))
                    : BLOCKS.register(id, () -> new TrimBlock(settled, name)));
        }
        if (made.size() != TrimRules.howMany()) {
            throw new IllegalStateException("the rules list " + TrimRules.howMany()
                    + " fittings and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * Every upholstered seat and every piece of cloth.
     *
     * <p>The seats are the shape they upholster — a red oak chair collides
     * exactly as an oak chair does, because it <em>is</em> an oak chair with
     * two of its six boxes drawn in wool. So this registers a
     * {@link ShapedBlock} under the shape's own name, and the only thing that
     * differs between the five hundred and twenty-eight of them is the model.
     *
     * <p>The cloth on its own is a {@link TrimBlock}, drawn out of
     * {@link ClothRules#boxesFor(String)} like everything else here.
     */
    public static final Map<String, DeferredBlock<Block>> CLOTH = cloth();

    private static Map<String, DeferredBlock<Block>> cloth() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        BlockBehaviour.Properties soft = BlockBehaviour.Properties
                .ofFullCopy(Blocks.WHITE_WOOL).sound(SoundType.WOOL)
                .strength(0.8F, 0.8F).noOcclusion();
        for (ClothRules.Upholstered seat : ClothRules.seats()) {
            String shape = seat.shape();
            made.put(seat.id(), BLOCKS.register(seat.id(),
                    () -> new ShapedBlock(BlockBehaviour.Properties
                            .ofFullCopy(Blocks.OAK_PLANKS).sound(SoundType.WOOD)
                            .strength(2.0F, 3.0F).noOcclusion(), shape)));
        }
        for (String id : ClothRules.cloths()) {
            String kind = id.substring(id.lastIndexOf('_') + 1);
            made.put(id, BLOCKS.register(id, () -> new ClothBlock(soft, kind)));
        }
        if (made.size() != ClothRules.howMany()) {
            throw new IllegalStateException("the rules list " + ClothRules.howMany()
                    + " coloured blocks and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * Every diagonal, in every material.
     *
     * <p>A {@link DiagonalBlock} rather than one of the game's own classes,
     * because there is no game class for this: the game's fence meets the
     * fence north, south, east and west of it and nothing in between, which
     * is the whole reason these exist. What it does share with the game's own
     * is the tags, so a diagonal fence is in {@code minecraft:fences} and an
     * ordinary fence beside it meets it.
     */
    public static final Map<String, DeferredBlock<Block>> DIAGONALS = diagonals();

    private static Map<String, DeferredBlock<Block>> diagonals() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (DiagonalRules.Run run : DiagonalRules.catalogue()) {
            String kind = run.kind();
            BlockBehaviour.Properties settled = like(run.material());
            made.put(run.id(), BLOCKS.register(run.id(),
                    () -> new DiagonalBlock(settled, kind)));
        }
        if (made.size() != DiagonalRules.howMany()) {
            throw new IllegalStateException("the rules list "
                    + DiagonalRules.howMany() + " diagonals and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

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
