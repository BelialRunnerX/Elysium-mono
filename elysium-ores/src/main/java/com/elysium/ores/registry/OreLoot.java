package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.loot.UnifyingModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The one loot modifier this module adds. */
public final class OreLoot {

    private OreLoot() {
    }

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    ElysiumOres.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<UnifyingModifier>> UNIFYING =
            MODIFIERS.register("unifying", () -> UnifyingModifier.CODEC);

    public static void register(IEventBus bus) {
        MODIFIERS.register(bus);
    }
}
