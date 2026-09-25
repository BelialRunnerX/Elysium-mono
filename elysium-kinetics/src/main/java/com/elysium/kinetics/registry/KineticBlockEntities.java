package com.elysium.kinetics.registry;

import com.elysium.kinetics.ElysiumKinetics;
import com.elysium.kinetics.block.CogwheelBlock;
import com.elysium.kinetics.block.GearboxBlock;
import com.elysium.kinetics.block.HandCrankBlock;
import com.elysium.kinetics.block.MachineBlock;
import com.elysium.kinetics.block.ShaftBlock;
import com.elysium.kinetics.block.WaterWheelBlock;
import com.elysium.kinetics.block.WindmillBearingBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The block entities, and the one capability any of them offers. */
public final class KineticBlockEntities {

    private KineticBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumKinetics.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShaftBlock.ShaftBlockEntity>>
            SHAFT = TYPES.register("shaft", () -> BlockEntityType.Builder.of(
                    ShaftBlock.ShaftBlockEntity::new,
                    KineticBlocks.TIMBER_SHAFT.get(), KineticBlocks.BRASS_SHAFT.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CogwheelBlock.CogwheelBlockEntity>>
            COGWHEEL = TYPES.register("cogwheel", () -> BlockEntityType.Builder.of(
                    CogwheelBlock.CogwheelBlockEntity::new,
                    KineticBlocks.SMALL_COGWHEEL.get(), KineticBlocks.LARGE_COGWHEEL.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HandCrankBlock.HandCrankBlockEntity>>
            HAND_CRANK = TYPES.register("hand_crank", () -> BlockEntityType.Builder.of(
                    HandCrankBlock.HandCrankBlockEntity::new, KineticBlocks.HAND_CRANK.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterWheelBlock.WaterWheelBlockEntity>>
            WATER_WHEEL = TYPES.register("water_wheel", () -> BlockEntityType.Builder.of(
                    WaterWheelBlock.WaterWheelBlockEntity::new, KineticBlocks.WATER_WHEEL.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearboxBlock.GearboxBlockEntity>>
            GEARBOX = TYPES.register("gearbox", () -> BlockEntityType.Builder.of(
                    GearboxBlock.GearboxBlockEntity::new, KineticBlocks.GEARBOX.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindmillBearingBlock.WindmillBearingBlockEntity>>
            WINDMILL_BEARING = TYPES.register("windmill_bearing",
                    () -> BlockEntityType.Builder.of(
                            WindmillBearingBlock.WindmillBearingBlockEntity::new,
                            KineticBlocks.WINDMILL_BEARING.get())
            .build(null));

    /**
     * One block entity type per machine, all of them the same class.
     *
     * <p>The factory a type is built with needs the type itself — a block
     * entity has to know what it is — and the type does not exist yet at that
     * point. A one-slot holder closes the loop: the factory reads it, and it
     * is filled the moment the type is built, which is long before any block
     * entity is made.
     */
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            machine(String name,
                    java.util.function.Supplier<net.minecraft.world.level.block.Block> block) {
        java.util.concurrent.atomic.AtomicReference<BlockEntityType<MachineBlock.MachineBlockEntity>>
                itself = new java.util.concurrent.atomic.AtomicReference<>();
        return TYPES.register(name, () -> {
            BlockEntityType<MachineBlock.MachineBlockEntity> type = BlockEntityType.Builder.of(
                    (pos, state) -> new MachineBlock.MachineBlockEntity(itself.get(), pos, state),
                    block.get()).build(null);
            itself.set(type);
            return type;
        });
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            MILLSTONE = machine("millstone", KineticBlocks.MILLSTONE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            PRESS = machine("press", KineticBlocks.PRESS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            SAW = machine("saw", KineticBlocks.SAW);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            MIXER = machine("mixer", KineticBlocks.MIXER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
            REFINERY = machine(com.elysium.kinetics.rules.EngineRules.REFINERY,
                    KineticBlocks.REFINERY);

    /** One type for all three engines: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.kinetics.block.EngineBlock.EngineBlockEntity>>
            ENGINE = TYPES.register("engine", () -> BlockEntityType.Builder.of(
                    com.elysium.kinetics.block.EngineBlock.EngineBlockEntity::new,
                    everyEngine()).build(null));

    /**
     * Every engine on the ladder.
     *
     * <p>A block entity type that does not name a block refuses to attach to
     * it, and the symptom is an engine you can place, fill with coal and
     * watch do nothing at all — with no error anywhere, because nothing went
     * wrong.
     */
    private static net.minecraft.world.level.block.Block[] everyEngine() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        for (com.elysium.kinetics.rules.EngineRules.Engine engine
                : com.elysium.kinetics.rules.EngineRules.ENGINES) {
            all.add(KineticBlocks.ENGINES.get(engine.id()).get());
        }
        if (all.size()
                != com.elysium.kinetics.rules.EngineRules.ENGINES.size()) {
            throw new IllegalStateException("the ladder has "
                    + com.elysium.kinetics.rules.EngineRules.ENGINES.size()
                    + " engine(s) and " + all.size()
                    + " were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

    /** The dynamo, which is a load on one side and an FE store on the other. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.kinetics.block.DynamoBlock.DynamoBlockEntity>>
            DYNAMO = TYPES.register(com.elysium.kinetics.block.DynamoBlock.NAME,
                    () -> BlockEntityType.Builder.of(
                            com.elysium.kinetics.block.DynamoBlock.DynamoBlockEntity::new,
                            KineticBlocks.DYNAMO.get()).build(null));

    /** One type for both anchors: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.kinetics.block.AnchorBlock.AnchorBlockEntity>>
            ANCHOR = TYPES.register("anchor", () -> BlockEntityType.Builder.of(
                    com.elysium.kinetics.block.AnchorBlock.AnchorBlockEntity::new,
                    everyAnchor()).build(null));

    private static net.minecraft.world.level.block.Block[] everyAnchor() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        for (com.elysium.kinetics.rules.AnchorRules.Anchor anchor
                : com.elysium.kinetics.rules.AnchorRules.ANCHORS) {
            all.add(KineticBlocks.ANCHORS.get(anchor.id()).get());
        }
        if (all.size()
                != com.elysium.kinetics.rules.AnchorRules.ANCHORS.size()) {
            throw new IllegalStateException("the rules have "
                    + com.elysium.kinetics.rules.AnchorRules.ANCHORS.size()
                    + " anchor(s) and " + all.size()
                    + " were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

    /**
     * The one ticket controller this module has, which is what an anchor
     * holds a chunk open with.
     *
     * <p>Held here rather than in the block so there is exactly one of it: a
     * controller per block entity would be a controller per anchor, and the
     * tickets of a removed one would have nobody left to release them.
     */
    public static final net.neoforged.neoforge.common.world.chunk.TicketController
            ANCHORS = new net.neoforged.neoforge.common.world.chunk.TicketController(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            ElysiumKinetics.MODID, "anchors"),
                    (level, helper) -> {
                        // Every ticket in a freshly loaded world belongs to a
                        // block that has not ticked yet, and a block that is
                        // gone will never tick. Drop them all; the ones with
                        // an anchor behind them are re-asked for within a
                        // tick, and the ones without were a leak.
                        helper.getBlockTickets().forEach((pos, tickets) ->
                                helper.removeAllTickets(pos));
                    });

    /**
     * Every machine's two slots, so a hopper can feed one and a hopper under
     * it can take what comes out.
     *
     * A mod whose first machine cannot be automated is a mod whose first
     * machine gets built once.
     */
    @EventBusSubscriber(modid = ElysiumKinetics.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onTicketControllers(
                net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) {
            event.register(ANCHORS);
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            for (DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlock.MachineBlockEntity>>
                    machine : java.util.List.of(MILLSTONE, PRESS, SAW, MIXER, REFINERY)) {
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, machine.get(),
                        (entity, side) -> entity.items());
            }
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ENGINE.get(),
                    (entity, side) -> entity.items());
            // And the seam: the one block here that offers Forge Energy at
            // all, which is how Age II's cables ever reach Age I.
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DYNAMO.get(),
                    (entity, side) -> entity.energy());
        }
    }
}
