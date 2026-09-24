package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.block.ArmoryStarlightChargerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one block in this module that holds something while nobody is looking.
 *
 * Every other bench answers in the moment it is asked; the Starlight Charger
 * waits, and waiting needs somewhere to keep what it is waiting with.
 */
public final class ArmoryBlockEntities {

    private ArmoryBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumArmory.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<ArmoryStarlightChargerBlockEntity>> STARLIGHT_CHARGER =
            TYPES.register("starlight_charger",
                    () -> BlockEntityType.Builder.of(ArmoryStarlightChargerBlockEntity::new,
                            ArmoryBlocks.STARLIGHT_CHARGER.get()).build(null));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
