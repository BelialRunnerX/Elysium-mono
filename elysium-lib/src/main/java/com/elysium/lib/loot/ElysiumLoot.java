package com.elysium.lib.loot;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.registries.Registries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The library's loot pieces: the function a pack calls to roll affixes, and the
 * global modifier that rolls them on everything else.
 */
public final class ElysiumLoot {

    private ElysiumLoot() {
    }

    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ElysiumLib.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>,
            LootItemFunctionType<ElysiumRollAffixesFunction>> ROLL_AFFIXES =
            FUNCTIONS.register("roll_affixes",
                    () -> new LootItemFunctionType<>(ElysiumRollAffixesFunction.CODEC));

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    ElysiumLib.MODID);

    /**
     * The one that puts affixes on whatever the world was already dropping.
     * A modifier rather than a loot function so that every table gets it —
     * including tables written by packs that have never heard of this mod.
     */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<ElysiumAffixInjector>> AFFIX_INJECTOR =
            MODIFIERS.register("affixes", () -> ElysiumAffixInjector.CODEC);

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        MODIFIERS.register(modEventBus);
    }
}
