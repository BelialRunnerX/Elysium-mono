package com.elysium.industry.registry;

import com.elysium.industry.ElysiumIndustry;
import com.elysium.industry.block.AlternatorBlock;
import com.elysium.industry.block.FueledBlock;
import com.elysium.industry.block.MotorBlock;
import com.elysium.industry.block.PlantBlock;
import com.elysium.industry.block.TransformerBlock;
import com.elysium.industry.block.WireBlock;
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

/** The block entities, and the one capability any of them offers. */
public final class IndustryBlockEntities {

    private IndustryBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumIndustry.MODID);

    /** One type for all three tiers: a wire is a wire, and the block says which. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireBlock.WireBlockEntity>>
            WIRE = TYPES.register("wire", () -> BlockEntityType.Builder.of(
                    WireBlock.WireBlockEntity::new,
                    IndustryBlocks.COPPER_WIRE.get(),
                    IndustryBlocks.GOLD_WIRE.get(),
                    IndustryBlocks.STEEL_WIRE.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransformerBlock.TransformerBlockEntity>>
            TRANSFORMER = TYPES.register("transformer", () -> BlockEntityType.Builder.of(
                    TransformerBlock.TransformerBlockEntity::new,
                    IndustryBlocks.TRANSFORMER.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlternatorBlock.AlternatorBlockEntity>>
            ALTERNATOR = TYPES.register("alternator", () -> BlockEntityType.Builder.of(
                    AlternatorBlock.AlternatorBlockEntity::new, IndustryBlocks.ALTERNATOR.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorBlock.MotorBlockEntity>>
            MOTOR = TYPES.register("motor", () -> BlockEntityType.Builder.of(
                    MotorBlock.MotorBlockEntity::new, IndustryBlocks.MOTOR.get())
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
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            plant(String name, Supplier<Block> block) {
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

    /** The same trick for the engines, which also share one class. */
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<FueledBlock.FueledBlockEntity>>
            engine(String name, Supplier<Block> block) {
        AtomicReference<BlockEntityType<FueledBlock.FueledBlockEntity>> itself =
                new AtomicReference<>();
        return TYPES.register(name, () -> {
            BlockEntityType<FueledBlock.FueledBlockEntity> type = BlockEntityType.Builder.of(
                    (pos, state) -> new FueledBlock.FueledBlockEntity(itself.get(), pos, state),
                    block.get()).build(null);
            itself.set(type);
            return type;
        });
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FueledBlock.FueledBlockEntity>>
            STEAM_TURBINE = engine("steam_turbine", IndustryBlocks.STEAM_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FueledBlock.FueledBlockEntity>>
            DIESEL_ENGINE = engine("diesel_engine", IndustryBlocks.DIESEL_ENGINE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            CRUSHER = plant("crusher", IndustryBlocks.CRUSHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            ROLLING_MILL = plant("rolling_mill", IndustryBlocks.ROLLING_MILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            ARC_FURNACE = plant("arc_furnace", IndustryBlocks.ARC_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
            SEPARATOR = plant("separator", IndustryBlocks.SEPARATOR);

    /**
     * Every machine's two slots and every engine's one, so a hopper can feed
     * them and one underneath can take what comes out.
     *
     * A mod whose factory has to be hand-fed is a mod whose factory gets built
     * once, and Age II is the age that is supposed to run while you are
     * somewhere else.
     */
    @EventBusSubscriber(modid = ElysiumIndustry.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            for (DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantBlock.PlantBlockEntity>>
                    machine : List.of(CRUSHER, ROLLING_MILL, ARC_FURNACE, SEPARATOR)) {
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, machine.get(),
                        (entity, side) -> entity.items());
            }
            for (DeferredHolder<BlockEntityType<?>, BlockEntityType<FueledBlock.FueledBlockEntity>>
                    fired : List.of(STEAM_TURBINE, DIESEL_ENGINE)) {
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, fired.get(),
                        (entity, side) -> entity.items());
            }
        }
    }
}
