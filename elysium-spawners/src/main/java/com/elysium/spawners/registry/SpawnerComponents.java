package com.elysium.spawners.registry;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.data.SpawnerTuning;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one component this module adds: what has been done to a spawner.
 *
 * <h2>Why a component and not custom NBT</h2>
 *
 * Because everything downstream is then free. A loot table can copy it off a
 * block entity, placing the block puts it back, the item shows it in a
 * tooltip, {@code /give} can write it, and a data pack can name it — none of
 * which is true of a block entity tag a mod reads by hand.
 */
public final class SpawnerComponents {

    private SpawnerComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumSpawners.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpawnerTuning>>
            TUNING = TYPES.register("tuning", () ->
                    DataComponentType.<SpawnerTuning>builder()
                            .persistent(SpawnerTuning.CODEC)
                            .networkSynchronized(SpawnerTuning.STREAM_CODEC)
                            .build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
