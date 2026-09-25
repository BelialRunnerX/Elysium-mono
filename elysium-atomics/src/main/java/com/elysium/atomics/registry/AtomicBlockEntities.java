package com.elysium.atomics.registry;

import com.elysium.atomics.ElysiumAtomics;
import com.elysium.atomics.block.CellBlock;
import com.elysium.atomics.block.ReactorBlock;
import com.elysium.atomics.block.ServoBlock;
import com.elysium.atomics.block.SolarPanelBlock;
import com.elysium.industry.block.PlantBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** The block entities, and the slots a hopper can reach. */
public final class AtomicBlockEntities {

    private AtomicBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumAtomics.MODID);

    /** One type for all three tiers: a cell is a cell, and the block says which. */
    /** The port a reactor hall forms around. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.atomics.block.ReactorPortBlock.ReactorPortBlockEntity>>
            REACTOR_PORT = TYPES.register("reactor_port", () -> BlockEntityType.Builder.of(
                    com.elysium.atomics.block.ReactorPortBlock.ReactorPortBlockEntity::new,
                    AtomicBlocks.HALL.get(
                            com.elysium.atomics.rules.HallRules.CONTROLLER).get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellBlock.CellBlockEntity>>
            CELL = TYPES.register("cell", () -> BlockEntityType.Builder.of(
                    CellBlock.CellBlockEntity::new,
                    AtomicBlocks.BASIC_CELL.get(),
                    AtomicBlocks.ADVANCED_CELL.get(),
                    AtomicBlocks.QUANTUM_CELL.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorBlock.ReactorBlockEntity>>
            REACTOR = TYPES.register("reactor", () -> BlockEntityType.Builder.of(
                    ReactorBlock.ReactorBlockEntity::new, AtomicBlocks.REACTOR_CORE.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlock.SolarPanelBlockEntity>>
            SOLAR_PANEL = TYPES.register("solar_panel", () -> BlockEntityType.Builder.of(
                    SolarPanelBlock.SolarPanelBlockEntity::new,
                    AtomicBlocks.SOLAR_PANEL.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServoBlock.ServoBlockEntity>>
            SERVO = TYPES.register("servo", () -> BlockEntityType.Builder.of(
                    ServoBlock.ServoBlockEntity::new, AtomicBlocks.SERVO.get())
            .build(null));

    /**
     * One block entity type per machine, all of them Age II's machine class.
     *
     * The same one-slot holder as the ages below use, and for the same reason:
     * the factory a type is built with needs the type itself, and the type
     * does not exist yet at that point.
     */
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            works(String name, Supplier<Block> block) {
        AtomicReference<BlockEntityType<PlantBlock.PlantBlockEntity>> itself =
                new AtomicReference<>();
        return TYPES.register(name, () -> {
            BlockEntityType<PlantBlock.PlantBlockEntity> type = BlockEntityType.Builder.of(
                    (pos, state) -> new PlantBlock.PlantBlockEntity(itself.get(), pos, state),
                    block.get()).build(null);
            itself.set(type);
            return type;
        });
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            ENRICHER = works("enricher", AtomicBlocks.ENRICHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            REACTION_CHAMBER = works("reaction_chamber", AtomicBlocks.REACTION_CHAMBER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            CENTRIFUGE = works("centrifuge", AtomicBlocks.CENTRIFUGE);

    /**
     * Every machine's two slots and the reactor's one.
     *
     * A reactor that had to be hand-fed would be a reactor nobody leaves
     * running, and this is the age that is supposed to run while you are
     * somewhere else.
     */
    @EventBusSubscriber(modid = ElysiumAtomics.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            for (DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
                    machine : List.of(ENRICHER, REACTION_CHAMBER, CENTRIFUGE)) {
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, machine.get(),
                        (entity, side) -> entity.items());
            }
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REACTOR.get(),
                    (entity, side) -> entity.items());
        }
    }
}
