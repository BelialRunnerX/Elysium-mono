package com.elysium.wilds.structure;

import com.elysium.wilds.registry.WildStructures;
import com.elysium.wilds.rules.GrammarRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * One room of a building, built out of blocks.
 *
 * <p>Which room it is comes from {@link GrammarRules}, and so does where its
 * doors are — so the walls this leaves gaps in are the same doors the harness
 * walked through when it proved every room reachable. There is one description
 * of a door in this module and this is it.
 */
public class RoomPiece extends StructurePiece {

    /** How many blocks a room is, along each axis. */
    public static final int SPAN = 7;

    /** How tall the inside of a room is. */
    public static final int HEAD = 4;

    private final String room;

    public RoomPiece(String room, BoundingBox box) {
        super(WildStructures.ROOM.get(), 0, box);
        this.room = room;
        // A piece with no orientation places its blocks at its own relative
        // coordinates - beside the world's origin, not inside its box - so
        // every keep, camp and lodge located fine and stood nowhere (the
        // mechanics survey, 27 September 2026).
        setOrientation(net.minecraft.core.Direction.NORTH);
    }

    public RoomPiece(CompoundTag tag) {
        super(WildStructures.ROOM.get(), tag);
        this.room = tag.getString("Room");
        setOrientation(net.minecraft.core.Direction.NORTH);
    }

    /** The box a room at these grid coordinates fills. */
    public static BoundingBox boxFor(int originX, int originY, int originZ,
                                     int x, int y, int z) {
        int lowX = originX + x * SPAN;
        int lowY = originY + y * (HEAD + 1);
        int lowZ = originZ + z * SPAN;
        return new BoundingBox(lowX, lowY, lowZ,
                lowX + SPAN - 1, lowY + HEAD, lowZ + SPAN - 1);
    }

    @Override
    protected void addAdditionalSaveData(
            net.minecraft.world.level.levelgen.structure.pieces
                    .StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Room", room);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox within, ChunkPos chunk, BlockPos origin) {
        GrammarRules.Room kind = GrammarRules.room(room);
        if (kind == null) {
            return;
        }
        BlockState wall = wallFor(room);
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int last = SPAN - 1;
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                placeBlock(level, floor, x, 0, z, within);
                placeBlock(level, floor, x, HEAD, z, within);
                for (int y = 1; y < HEAD; y++) {
                    boolean edge = x == 0 || z == 0 || x == last || z == last;
                    placeBlock(level, edge ? wall : air, x, y, z, within);
                }
            }
        }
        // The doors. A gap two blocks high in the middle of a wall, on exactly
        // the sides the rules say this room opens on — so the building a
        // player walks through is the building the harness walked through.
        int middle = SPAN / 2;
        for (int way = 0; way < GrammarRules.WAYS; way++) {
            if (!kind.opens(way)) {
                continue;
            }
            switch (way) {
                case GrammarRules.NORTH -> gap(level, within, middle, 0);
                case GrammarRules.SOUTH -> gap(level, within, middle, last);
                case GrammarRules.WEST -> gap(level, within, 0, middle);
                case GrammarRules.EAST -> gap(level, within, last, middle);
                case GrammarRules.UP -> {
                    for (int x = middle - 1; x <= middle + 1; x++) {
                        for (int z = middle - 1; z <= middle + 1; z++) {
                            placeBlock(level, air, x, HEAD, z, within);
                        }
                    }
                }
                case GrammarRules.DOWN -> {
                    for (int x = middle - 1; x <= middle + 1; x++) {
                        for (int z = middle - 1; z <= middle + 1; z++) {
                            placeBlock(level, air, x, 0, z, within);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    private void gap(WorldGenLevel level, BoundingBox within, int x, int z) {
        BlockState air = Blocks.AIR.defaultBlockState();
        placeBlock(level, air, x, 1, z, within);
        placeBlock(level, air, x, 2, z, within);
    }

    /**
     * What a room's walls are made of.
     *
     * <p>Read off the room rather than off the grammar, so that a cellar is
     * always a cellar whichever building it is in — which is what makes a
     * generated building readable at all.
     */
    public static BlockState wallFor(String room) {
        return switch (room) {
            case "cellar", "mine" -> Blocks.COBBLESTONE.defaultBlockState();
            case "loft", "watch" -> Blocks.OAK_PLANKS.defaultBlockState();
            case "yard" -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case "shrine" -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case "store" -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case "door" -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }
}
