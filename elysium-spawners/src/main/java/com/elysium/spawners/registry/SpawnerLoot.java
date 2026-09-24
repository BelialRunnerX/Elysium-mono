package com.elysium.spawners.registry;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.loot.CapturingModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The one loot modifier this module needs, and the reason it needs one. */
public final class SpawnerLoot {

    private SpawnerLoot() {
    }

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    ElysiumSpawners.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<CapturingModifier>> CAPTURING =
            MODIFIERS.register("capturing", () -> CapturingModifier.CODEC);

    public static void register(IEventBus modBus) {
        MODIFIERS.register(modBus);
    }
}
