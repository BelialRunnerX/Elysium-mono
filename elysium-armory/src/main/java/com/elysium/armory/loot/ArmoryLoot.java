package com.elysium.armory.loot;

import com.elysium.armory.ElysiumArmory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The loot pieces the traits need (SG-64).
 *
 * Two of them, and both exist so a datapack can do something it otherwise
 * could not: ask what the tool in hand is, and hand out a piece of modular
 * gear that is actually built.
 *
 * <p>What Silent Gear also did here — bonus drops from a trait, and a magmatic
 * tool smelting what it breaks — Elysium does as events instead, in
 * {@code ArmoryTraitEvents}. A global loot modifier only sees drops that come
 * from a loot table; an event sees every drop, including the ones other mods
 * produce by hand. The effect kinds are the same, read from the same files, by
 * the same engine.
 */
public final class ArmoryLoot {

    private ArmoryLoot() {
    }

    public static final DeferredRegister<LootItemConditionType> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, ElysiumArmory.MODID);

    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ElysiumArmory.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> GEAR =
            CONDITIONS.register("gear", () -> new LootItemConditionType(ArmoryGearCondition.CODEC));

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<ArmorySetPartsFunction>>
            SET_PARTS = FUNCTIONS.register("set_parts",
                    () -> new LootItemFunctionType<>(ArmorySetPartsFunction.CODEC));

    public static void register(IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
        FUNCTIONS.register(modEventBus);
    }
}
