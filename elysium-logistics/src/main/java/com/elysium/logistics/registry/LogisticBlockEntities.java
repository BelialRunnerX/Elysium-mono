package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.block.AssemblerBlock;
import com.elysium.logistics.block.AssemblyCoreBlock;
import com.elysium.logistics.block.ControllerBlock;
import com.elysium.logistics.block.DrawerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The block entities, and the inventories a hopper can reach. */
public final class LogisticBlockEntities {

    private LogisticBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumLogistics.MODID);

    /** One type for all three tiers: a drawer is a drawer, and the block says which. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrawerBlock.DrawerBlockEntity>>
            DRAWER = TYPES.register("drawer", () -> BlockEntityType.Builder.of(
                    DrawerBlock.DrawerBlockEntity::new,
                    LogisticBlocks.TIMBER_DRAWER.get(),
                    LogisticBlocks.BRASS_DRAWER.get(),
                    LogisticBlocks.STEEL_DRAWER.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlock.ControllerBlockEntity>>
            CONTROLLER = TYPES.register("controller", () -> BlockEntityType.Builder.of(
                    ControllerBlock.ControllerBlockEntity::new,
                    LogisticBlocks.CONTROLLER.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlock.AssemblerBlockEntity>>
            ASSEMBLER = TYPES.register("assembler", () -> BlockEntityType.Builder.of(
                    AssemblerBlock.AssemblerBlockEntity::new,
                    LogisticBlocks.ASSEMBLER.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyCoreBlock.AssemblyCoreBlockEntity>>
            ASSEMBLY_CORE = TYPES.register("assembly_core",
                    () -> BlockEntityType.Builder.of(
                            AssemblyCoreBlock.AssemblyCoreBlockEntity::new,
                            LogisticBlocks.ASSEMBLY_CORE.get())
                    .build(null));

    /**
     * A drawer is an inventory; a controller is every drawer on its bus.
     *
     * The second of those is the whole point of the block. A hopper into a
     * controller reaches a wall of drawers and finds the right one, which is
     * what a storage network is for in a factory and needs no screen at all.
     *
     * <p>An assembler is deliberately <em>not</em> one. What is in it belongs
     * to a job the core has already taken the ingredients out of the warehouse
     * for, and a hopper that could reach in would be a hopper that could take
     * half a recipe away mid-run.
     */
    @EventBusSubscriber(modid = ElysiumLogistics.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DRAWER.get(),
                    (entity, side) -> entity.store());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONTROLLER.get(),
                    (entity, side) -> entity.asOneInventory());
        }
    }
}
