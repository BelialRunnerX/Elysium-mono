package com.elysium.wilds.structure;

import com.elysium.wilds.registry.WildStructures;
import com.elysium.wilds.rules.GrammarRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A building laid out by a grammar rather than copied from a template.
 *
 * <p>Everything about the shape of it is worked out in
 * {@link GrammarRules#layOut(String, long)}, which is the JDK alone and is
 * swept over eighty thousand buildings by the harness: every room walkable
 * from the door, no two rooms in one place, nothing outside the box it
 * claimed, and the same seed laying out the same building. What is left for
 * this class is turning that plan into pieces, which is why it is forty lines.
 *
 * <p>The seed is the world seed mixed with the chunk, so a building is the
 * same every time that chunk is generated and different from the one in the
 * chunk next door.
 */
public class GrammarStructure extends Structure {

    public static final MapCodec<GrammarStructure> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    com.mojang.serialization.Codec.STRING.fieldOf("grammar")
                            .forGetter(one -> one.grammar)
            ).apply(instance, GrammarStructure::new));

    private final String grammar;

    public GrammarStructure(StructureSettings settings, String grammar) {
        super(settings);
        this.grammar = grammar;
    }

    /** Which grammar lays this one out. */
    public String grammar() {
        return grammar;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (GrammarRules.grammar(grammar) == null) {
            return Optional.empty();
        }
        long seed = context.seed()
                ^ ((long) context.chunkPos().x * 341873128712L)
                ^ ((long) context.chunkPos().z * 132897987541L);
        GrammarRules.Plan plan = GrammarRules.layOut(grammar, seed);
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> {
                    int originX = context.chunkPos().getMinBlockX();
                    int originZ = context.chunkPos().getMinBlockZ();
                    int originY = context.chunkGenerator().getFirstFreeHeight(
                            originX, originZ, Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(), context.randomState());
                    for (GrammarRules.Placed one : plan.rooms()) {
                        builder.addPiece(new RoomPiece(one.room(),
                                RoomPiece.boxFor(originX, originY, originZ,
                                        one.x(), one.y(), one.z())));
                    }
                });
    }

    @Override
    public StructureType<?> type() {
        return WildStructures.GRAMMAR.get();
    }
}
