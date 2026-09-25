package com.elysium.wilds.registry;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.structure.GrammarStructure;
import com.elysium.wilds.structure.RoomPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one structure type this module has, and the one piece it is made of.
 *
 * <p>One of each, because the variety is in the grammar rather than in the
 * classes: eight kinds of building and twelve kinds of room, and every one of
 * them is this type reading a different name out of a data file. The jar this
 * replaces ships hundreds of templates; this ships two classes and a list.
 */
public final class WildStructures {

    private WildStructures() {
    }

    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ElysiumWilds.MODID);

    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ElysiumWilds.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GrammarStructure>>
            GRAMMAR = TYPES.register("grammar", () -> () -> GrammarStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType>
            ROOM = PIECES.register("room",
                    () -> (StructurePieceType.ContextlessType) RoomPiece::new);
}
