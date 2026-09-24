package com.elysium.space.worldgen;

import com.elysium.space.ElysiumSpace;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Stellaris's structure-void processor, ported: a template block is skipped
 * if it is structure void, or if the world is air where it would go. So a
 * meteor only replaces solid ground and sits embedded in it, rather than
 * floating a full cube of rock.
 */
public final class SpaceProcessors {

    private SpaceProcessors() {
    }

    public static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ElysiumSpace.MODID);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<GroundOnly>> STRUCTURE_VOID =
            TYPES.register("structure_void_processor", () -> () -> GroundOnly.CODEC);

    public static final class GroundOnly extends StructureProcessor {

        public static final GroundOnly INSTANCE = new GroundOnly();
        public static final MapCodec<GroundOnly> CODEC = MapCodec.unit(() -> INSTANCE);

        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                                StructureTemplate.StructureBlockInfo original,
                                                                StructureTemplate.StructureBlockInfo current,
                                                                StructurePlaceSettings settings) {
            if (current.state().is(Blocks.STRUCTURE_VOID)) {
                return null;
            }
            return level.getBlockState(current.pos()).isAir() ? null : current;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return STRUCTURE_VOID.get();
        }
    }
}
