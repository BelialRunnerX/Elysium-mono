package com.elysium.enchanting.registry;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.block.ScriptoriumBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The one thing in this module that remembers something between sessions. */
public final class EnchantingBlockEntities {

    private EnchantingBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumEnchanting.MODID);

    /**
     * One type for both sizes of Scriptorium: they hold different amounts and
     * are otherwise the same block, and a second type would be a second thing
     * to keep in step for no gain.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScriptoriumBlockEntity>>
            SCRIPTORIUM = TYPES.register("scriptorium", () -> BlockEntityType.Builder.of(
                    ScriptoriumBlockEntity::new,
                    EnchantingBlocks.SCRIPTORIUM.get(),
                    EnchantingBlocks.GRAND_SCRIPTORIUM.get()).build(null));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
