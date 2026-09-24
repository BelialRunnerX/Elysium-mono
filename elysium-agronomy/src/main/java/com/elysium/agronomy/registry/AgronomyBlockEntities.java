package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.block.AcceleratorBlock;
import com.elysium.agronomy.block.HiveBlock;
import com.elysium.agronomy.block.PotBlock;
import com.elysium.agronomy.block.RockGeneratorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The block entities, and the slots a hopper can reach. */
public final class AgronomyBlockEntities {

    private AgronomyBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumAgronomy.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PotBlock.PotBlockEntity>>
            POT = TYPES.register("pot", () -> BlockEntityType.Builder.of(
                    PotBlock.PotBlockEntity::new, AgronomyBlocks.POT.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcceleratorBlock.AcceleratorBlockEntity>>
            ACCELERATOR = TYPES.register("accelerator", () -> BlockEntityType.Builder.of(
                    AcceleratorBlock.AcceleratorBlockEntity::new,
                    AgronomyBlocks.ACCELERATOR.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HiveBlock.HiveBlockEntity>>
            HIVE = TYPES.register("hive", () -> BlockEntityType.Builder.of(
                    HiveBlock.HiveBlockEntity::new, AgronomyBlocks.HIVE.get())
            .build(null));

    /** One type for all three rungs: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RockGeneratorBlock.RockGeneratorBlockEntity>>
            ROCK_GENERATOR = TYPES.register("rock_generator",
                    () -> BlockEntityType.Builder.of(
                            RockGeneratorBlock.RockGeneratorBlockEntity::new,
                            AgronomyBlocks.ROCK_GENERATOR.get(),
                            AgronomyBlocks.DRIVEN_ROCK_GENERATOR.get(),
                            AgronomyBlocks.INDUSTRIAL_ROCK_GENERATOR.get())
            .build(null));

    /**
     * Every slot a hopper can reach.
     *
     * A resource farm that has to be harvested by hand is a resource farm that
     * gets built once, and this is the module whose whole promise is that it
     * runs while you are somewhere else.
     */
    @EventBusSubscriber(modid = ElysiumAgronomy.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, POT.get(),
                    (entity, side) -> entity.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HIVE.get(),
                    (entity, side) -> entity.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ROCK_GENERATOR.get(),
                    (entity, side) -> entity.items());
        }
    }
}
