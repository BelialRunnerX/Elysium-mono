package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.ElysiumSolarpunk;
import com.elysium.solarpunk.SolarpunkContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The machines that work, and what other mods can see of them.
 *
 * <h2>Capabilities are the whole interoperability story</h2>
 *
 * Registering {@code IEnergyStorage} and {@code IItemHandler} against these
 * block entities is what makes a condenser charge somebody else's machine and a
 * vanilla hopper empty a bioreactor. Neither side knows the other exists. Leave
 * this event unhandled and both machines still run perfectly in isolation and
 * connect to nothing — which is a failure with no error message, because
 * nothing asked for a capability that was not there.
 *
 * <h2>Sided access, and what it is for</h2>
 *
 * The energy capability ignores the side it is asked from: a condenser will
 * push out of any face and a bioreactor take power on any face, because making
 * a player work out which face is the input is a puzzle with no content in it.
 * The item handler is the same — a hopper under the bioreactor and a hopper
 * beside it both work.
 */
@EventBusSubscriber(modid = ElysiumSolarpunk.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SolarpunkMachines {

    private SolarpunkMachines() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ElysiumSolarpunk.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarCondenserEntity>>
            SOLAR_CONDENSER_ENTITY = BLOCK_ENTITIES.register("solar_condenser",
                    () -> BlockEntityType.Builder.of(
                            SolarCondenserEntity::new,
                            SolarpunkContent.SOLAR_CONDENSER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlgaeBioreactorEntity>>
            ALGAE_BIOREACTOR_ENTITY = BLOCK_ENTITIES.register("algae_bioreactor",
                    () -> BlockEntityType.Builder.of(
                            AlgaeBioreactorEntity::new,
                            SolarpunkContent.ALGAE_BIOREACTOR.get()).build(null));

    /** The conduit's dye, and nothing else: a run remembers its colour. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.lib.run.RunDyeBlockEntity>>
            POWER_CONDUIT_ENTITY = BLOCK_ENTITIES.register("power_conduit",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new com.elysium.lib.run.RunDyeBlockEntity(
                                    SolarpunkMachines.POWER_CONDUIT_ENTITY.get(), pos, state),
                            SolarpunkContent.POWER_CONDUIT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindCapstanEntity>>
            WIND_CAPSTAN_ENTITY = BLOCK_ENTITIES.register("wind_capstan",
                    () -> BlockEntityType.Builder.of(
                            WindCapstanEntity::new,
                            SolarpunkContent.WIND_CAPSTAN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterReclaimerEntity>>
            WATER_RECLAIMER_ENTITY = BLOCK_ENTITIES.register("water_reclaimer",
                    () -> BlockEntityType.Builder.of(
                            WaterReclaimerEntity::new,
                            SolarpunkContent.WATER_RECLAIMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeramicKilnEntity>>
            CERAMIC_KILN_ENTITY = BLOCK_ENTITIES.register("ceramic_kiln",
                    () -> BlockEntityType.Builder.of(
                            CeramicKilnEntity::new,
                            SolarpunkContent.CERAMIC_KILN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MyceliumPressEntity>>
            MYCELIUM_PRESS_ENTITY = BLOCK_ENTITIES.register("mycelium_press",
                    () -> BlockEntityType.Builder.of(
                            MyceliumPressEntity::new,
                            SolarpunkContent.MYCELIUM_PRESS.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FibreLoomEntity>>
            FIBRE_LOOM_ENTITY = BLOCK_ENTITIES.register("fibre_loom",
                    () -> BlockEntityType.Builder.of(
                            FibreLoomEntity::new,
                            SolarpunkContent.FIBRE_LOOM.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AquaponicsTankEntity>>
            AQUAPONICS_TANK_ENTITY = BLOCK_ENTITIES.register("aquaponics_tank",
                    () -> BlockEntityType.Builder.of(
                            AquaponicsTankEntity::new,
                            SolarpunkContent.AQUAPONICS_TANK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HarvesterEntity>>
            HARVESTER_ENTITY = BLOCK_ENTITIES.register("harvester",
                    () -> BlockEntityType.Builder.of(
                            HarvesterEntity::new,
                            SolarpunkContent.HARVESTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TreeHarvesterEntity>>
            TREE_HARVESTER_ENTITY = BLOCK_ENTITIES.register("tree_harvester",
                    () -> BlockEntityType.Builder.of(
                            TreeHarvesterEntity::new,
                            SolarpunkContent.TREE_HARVESTER.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    @SubscribeEvent
    public static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                SOLAR_CONDENSER_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ALGAE_BIOREACTOR_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ALGAE_BIOREACTOR_ENTITY.get(),
                (machine, side) -> machine.getOutput());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                WIND_CAPSTAN_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                WATER_RECLAIMER_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                WATER_RECLAIMER_ENTITY.get(),
                (machine, side) -> machine.getTank());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                CERAMIC_KILN_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                CERAMIC_KILN_ENTITY.get(),
                (machine, side) -> machine.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                MYCELIUM_PRESS_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                MYCELIUM_PRESS_ENTITY.get(),
                (machine, side) -> machine.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                FIBRE_LOOM_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                FIBRE_LOOM_ENTITY.get(),
                (machine, side) -> machine.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                AQUAPONICS_TANK_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                AQUAPONICS_TANK_ENTITY.get(),
                (machine, side) -> machine.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                HARVESTER_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                HARVESTER_ENTITY.get(),
                (machine, side) -> machine.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                TREE_HARVESTER_ENTITY.get(),
                (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                TREE_HARVESTER_ENTITY.get(),
                (machine, side) -> machine.getInventory());
    }
}
