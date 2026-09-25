package com.elysium.foundry.registry;

import com.elysium.foundry.ElysiumFoundry;
import com.elysium.foundry.block.AlloySmelterBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Two block entities, one per machine: the smelter and the refinery. */
public final class FoundryBlockEntities {

    private FoundryBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumFoundry.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlloySmelterBlock.AlloySmelterBlockEntity>>
            ALLOY_SMELTER = TYPES.register("alloy_smelter",
                    () -> BlockEntityType.Builder.of(
                            AlloySmelterBlock.AlloySmelterBlockEntity::new,
                            FoundryBlocks.ALLOY_SMELTER.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.foundry.block.RefineryBlock.RefineryBlockEntity>>
            REFINERY = TYPES.register("refinery",
                    () -> BlockEntityType.Builder.of(
                            com.elysium.foundry.block.RefineryBlock.RefineryBlockEntity::new,
                            FoundryBlocks.REFINERY.get())
            .build(null));

    /**
     * Its three slots, so a factory can feed it.
     *
     * A machine at the top of a material chain that has to be hand-fed is a
     * machine nobody reaches the top of the chain with.
     */
    @EventBusSubscriber(modid = ElysiumFoundry.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ALLOY_SMELTER.get(),
                    (entity, side) -> entity.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REFINERY.get(),
                    (entity, side) -> entity.items());
        }
    }
}
