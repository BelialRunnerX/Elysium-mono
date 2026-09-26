package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.block.AssemblerBlock;
import com.elysium.logistics.block.AssemblyCoreBlock;
import com.elysium.logistics.block.ControllerBlock;
import com.elysium.logistics.block.DeviceBlock;
import com.elysium.logistics.block.DrawerBlock;
import com.elysium.logistics.pack.PackBlock;
import com.elysium.logistics.rules.NetRules;
import com.elysium.logistics.rules.PackRules;
import net.minecraft.world.level.block.Block;
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

    /**
     * One type for all seven devices: a device is a device, and the block
     * says which. The blocks come off the same roster the blocks were
     * registered from, so an eighth device is in here without a line changing.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeviceBlock.DeviceBlockEntity>>
            DEVICE = TYPES.register("device", () -> BlockEntityType.Builder.of(
                    DeviceBlock.DeviceBlockEntity::new, deviceBlocks())
            .build(null));

    /** One type for every placed body, off the same walk the blocks were. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PackBlock.PackBlockEntity>>
            PACK = TYPES.register("pack", () -> BlockEntityType.Builder.of(
                    PackBlock.PackBlockEntity::new, placedBlocks())
            .build(null));

    private static Block[] placedBlocks() {
        Block[] blocks = new Block[LogisticBlocks.PLACED.size()];
        int at = 0;
        for (PackRules.Body body : PackBlock.placedBodies()) {
            blocks[at++] = LogisticBlocks.PLACED.get(body.name()).get();
        }
        return blocks;
    }

    private static Block[] deviceBlocks() {
        Block[] blocks = new Block[NetRules.DEVICES.size()];
        int at = 0;
        for (NetRules.Device device : NetRules.DEVICES) {
            blocks[at++] = LogisticBlocks.DEVICES.get(device.name()).get();
        }
        return blocks;
    }

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
            // An interface is its network with a face; an ender link is the
            // network at the other end. The other five expose nothing: they
            // are things a network does, not places a hopper can reach.
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEVICE.get(),
                    (entity, side) -> entity.exposed());
            // A crate is reached the way a chest is, and its parts rule what
            // a hopper may push into it exactly as they rule a bag's intake.
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PACK.get(),
                    (entity, side) -> entity.handler());
        }
    }
}
