package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.block.OxygenDistributorBlockEntity;
import com.elysium.space.machine.CoalGeneratorBlockEntity;
import com.elysium.space.machine.FuelRefineryBlockEntity;
import com.elysium.space.machine.MachineBlockEntity;
import com.elysium.space.machine.SolarPanelBlockEntity;
import com.elysium.space.machine.WaterSeparatorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** The machines' block entities, and the energy and fluid capabilities they expose to every mod. */
@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceBlockEntities {

    private SpaceBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumSpace.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenDistributorBlockEntity>>
            OXYGEN_DISTRIBUTOR = TYPES.register("oxygen_distributor", () -> BlockEntityType.Builder
                    .of(OxygenDistributorBlockEntity::new, SpaceBlocks.OXYGEN_DISTRIBUTOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalGeneratorBlockEntity>>
            COAL_GENERATOR = TYPES.register("coal_generator", () -> BlockEntityType.Builder
                    .of(CoalGeneratorBlockEntity::new, SpaceBlocks.COAL_GENERATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>>
            SOLAR_PANEL = TYPES.register("solar_panel", () -> BlockEntityType.Builder
                    .of(SolarPanelBlockEntity::new, SpaceBlocks.SOLAR_PANEL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelRefineryBlockEntity>>
            FUEL_REFINERY = TYPES.register("fuel_refinery", () -> BlockEntityType.Builder
                    .of(FuelRefineryBlockEntity::new, SpaceBlocks.FUEL_REFINERY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterSeparatorBlockEntity>>
            WATER_SEPARATOR = TYPES.register("water_separator", () -> BlockEntityType.Builder
                    .of(WaterSeparatorBlockEntity::new, SpaceBlocks.WATER_SEPARATOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.elysium.space.freight.FreightTerminalBlockEntity>>
            FREIGHT_TERMINAL = TYPES.register("freight_terminal",
                    () -> BlockEntityType.Builder.of(
                            com.elysium.space.freight.FreightTerminalBlockEntity::new,
                            SpaceBlocks.FREIGHT_TERMINAL.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.space.machine.ConduitBlockEntity.Cable>>
            CABLE = TYPES.register("cable", () -> BlockEntityType.Builder
                    .of(com.elysium.space.machine.ConduitBlockEntity.Cable::new, SpaceBlocks.CABLE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.space.machine.ConduitBlockEntity.Pipe>>
            PIPE = TYPES.register("pipe", () -> BlockEntityType.Builder
                    .of(com.elysium.space.machine.ConduitBlockEntity.Pipe::new, SpaceBlocks.PIPE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.space.machine.PumpjackBlockEntity>>
            PUMPJACK = TYPES.register("pumpjack", () -> BlockEntityType.Builder
                    .of(com.elysium.space.machine.PumpjackBlockEntity::new, SpaceBlocks.PUMPJACK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.space.machine.DieselGeneratorBlockEntity>>
            DIESEL_GENERATOR = TYPES.register("diesel_generator", () -> BlockEntityType.Builder
                    .of(com.elysium.space.machine.DieselGeneratorBlockEntity::new, SpaceBlocks.DIESEL_GENERATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.space.machine.FluidTankBlockEntity>>
            FLUID_TANK = TYPES.register("fluid_tank", () -> BlockEntityType.Builder
                    .of(com.elysium.space.machine.FluidTankBlockEntity::new, SpaceBlocks.FLUID_TANK.get()).build(null));

    @SubscribeEvent
    public static void onCapabilities(RegisterCapabilitiesEvent event) {
        for (DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends MachineBlockEntity>> type
                : List.of(OXYGEN_DISTRIBUTOR, COAL_GENERATOR, SOLAR_PANEL, FUEL_REFINERY, WATER_SEPARATOR,
                PUMPJACK, DIESEL_GENERATOR, FLUID_TANK)) {
            register(event, type.get());
        }
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COAL_GENERATOR.get(), (be, side) -> be.items());
        // The terminal is a plain container, so hoppers already work; this is
        // what lets every mod's pipes and every mod's storage bus work too.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FREIGHT_TERMINAL.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.InvWrapper(be));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FREIGHT_TERMINAL.get(),
                (be, side) -> be.fuelTank());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CABLE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PIPE.get(), (be, side) -> be.fluids());
    }

    private static <T extends MachineBlockEntity> void register(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.fluids());
    }
}
