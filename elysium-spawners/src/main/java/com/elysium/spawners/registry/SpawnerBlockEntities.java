package com.elysium.spawners.registry;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.block.ElysiumSpawnerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The spawner's block entity, which is where everything about it lives. */
public final class SpawnerBlockEntities {

    private SpawnerBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumSpawners.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<ElysiumSpawnerBlockEntity>> SPAWNER =
            TYPES.register("spawner", () -> BlockEntityType.Builder.of(
                    ElysiumSpawnerBlockEntity::new, SpawnerBlocks.SPAWNER.get()).build(null));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
