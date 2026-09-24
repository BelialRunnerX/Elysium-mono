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
                    PotBlock.PotBlockEntity::new, everyPot())
            .build(null));

    /**
     * The plain pot and all hundred and sixty-five of the catalogue.
     *
     * <p>A block entity type that does not name a block refuses to attach to
     * it, and the symptom is a pot you can place, fill and watch do nothing at
     * all. Walking the catalogue means a new colour of pot works the day it
     * is added rather than the day somebody notices.
     */
    private static net.minecraft.world.level.block.Block[] everyPot() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        all.add(AgronomyBlocks.POT.get());
        for (com.elysium.agronomy.rules.PotRules.Pot pot
                : com.elysium.agronomy.rules.PotRules.catalogue()) {
            all.add(PotBlocks.ALL.get(pot.id()).get());
        }
        if (all.size() != com.elysium.agronomy.rules.PotRules.catalogue().size() + 1) {
            throw new IllegalStateException("there are "
                    + com.elysium.agronomy.rules.PotRules.catalogue().size()
                    + " pots in the catalogue and " + (all.size() - 1)
                    + " were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

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
