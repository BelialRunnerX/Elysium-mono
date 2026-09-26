package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.GraveBlock;
import com.elysium.comforts.block.StationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Two block entities: what is in the grave, and who is in a station. */
public final class ComfortBlockEntities {

    private ComfortBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumComforts.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<GraveBlock.GraveBlockEntity>>
            GRAVE = TYPES.register("grave", () -> BlockEntityType.Builder.of(
                    GraveBlock.GraveBlockEntity::new, ComfortBlocks.GRAVE.get())
            .build(null));

    /**
     * One type for every station that holds somebody.
     *
     * <p>Built from the blocks the rules produced rather than from a list, so
     * a ninth station is a row and nothing here changes — and a station left
     * out of this would be a block that silently forgets whoever was in it
     * the first time the chunk unloaded.
     */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<StationBlockEntity>>
            STATION = TYPES.register("station", () -> BlockEntityType.Builder.of(
                    StationBlockEntity::new, holders())
            .build(null));

    private static net.minecraft.world.level.block.Block[] holders() {
        java.util.List<net.minecraft.world.level.block.Block> made =
                new java.util.ArrayList<>();
        for (com.elysium.comforts.rules.StationRules.Station station
                : com.elysium.comforts.rules.StationRules.STATIONS) {
            if (station.holds()) {
                made.add(ComfortBlocks.STATIONS.get(station.id()).get());
            }
        }
        return made.toArray(new net.minecraft.world.level.block.Block[0]);
    }
}
