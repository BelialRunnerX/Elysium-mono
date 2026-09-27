package com.elysium.agronomy.registry;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.Block;
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

    /** One type for the whole housing ladder: the block says which rung it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HiveBlock.HiveBlockEntity>>
            HIVE = TYPES.register("hive", () -> BlockEntityType.Builder.of(
                    HiveBlock.HiveBlockEntity::new, everyHousing())
            .build(null));

    /**
     * The plain hive and every rung above it.
     *
     * <p>A block entity type that does not name a block refuses to attach to
     * it, and the symptom is an apiary you can place, fill with bees and
     * watch do nothing at all — with no error anywhere, because nothing went
     * wrong. The pots learned this the same way.
     */
    private static net.minecraft.world.level.block.Block[] everyHousing() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        all.add(AgronomyBlocks.HIVE.get());
        for (com.elysium.agronomy.rules.ApiaryRules.Housing housing
                : com.elysium.agronomy.rules.ApiaryRules.HOUSINGS) {
            if (!housing.first()) {
                all.add(AgronomyBlocks.APIARIES.get(housing.id()).get());
            }
        }
        if (all.size() != com.elysium.agronomy.rules.ApiaryRules.HOUSINGS.size()) {
            throw new IllegalStateException("the ladder has "
                    + com.elysium.agronomy.rules.ApiaryRules.HOUSINGS.size()
                    + " rung(s) and " + all.size()
                    + " were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.agronomy.block.CentrifugeBlock.CentrifugeBlockEntity>>
            CENTRIFUGE = TYPES.register(
                    com.elysium.agronomy.rules.ApiaryRules.CENTRIFUGE,
                    () -> BlockEntityType.Builder.of(
                            com.elysium.agronomy.block.CentrifugeBlock
                                    .CentrifugeBlockEntity::new,
                            AgronomyBlocks.CENTRIFUGE.get())
                    .build(null));

    /** One type for all three rungs: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RockGeneratorBlock.RockGeneratorBlockEntity>>
            ROCK_GENERATOR = TYPES.register("rock_generator",
                    () -> BlockEntityType.Builder.of(
                            RockGeneratorBlock.RockGeneratorBlockEntity::new,
                            rockGeneratorBlocks())
            .build(null));

    /**
     * Every block the rock generator's entity may sit in: the three rungs and
     * one generator per vein. A block left off this list is one the game
     * refuses to place at all - "an unexpected error" to the player's face -
     * which is how the seven vein generators shipped until the catalogue
     * test set each block once (27 September 2026).
     */
    private static Block[] rockGeneratorBlocks() {
        java.util.List<Block> out = new java.util.ArrayList<>(java.util.List.of(
                AgronomyBlocks.ROCK_GENERATOR.get(),
                AgronomyBlocks.DRIVEN_ROCK_GENERATOR.get(),
                AgronomyBlocks.INDUSTRIAL_ROCK_GENERATOR.get()));
        for (DeferredBlock<Block> vein : AgronomyBlocks.VEINS.values()) {
            out.add(vein.get());
        }
        return out.toArray(new Block[0]);
    }

    /** The middle of an altar. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.agronomy.block.AwakeningAltarBlock.AltarBlockEntity>>
            ALTAR = TYPES.register(
                    com.elysium.agronomy.rules.AltarRules.CENTRE,
                    () -> BlockEntityType.Builder.of(
                            com.elysium.agronomy.block.AwakeningAltarBlock
                                    .AltarBlockEntity::new,
                            AgronomyBlocks.ALTAR.get(
                                    com.elysium.agronomy.rules.AltarRules.CENTRE)
                                    .get())
                    .build(null));

    /** One type for both stands: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.agronomy.block.AltarBlock.HeldBlockEntity>>
            ALTAR_STAND = TYPES.register("altar_stand",
                    () -> BlockEntityType.Builder.of(
                            com.elysium.agronomy.block.AltarBlock
                                    .HeldBlockEntity::new,
                            AgronomyBlocks.ALTAR.get(
                                    com.elysium.agronomy.rules.AltarRules.PEDESTAL)
                                    .get(),
                            AgronomyBlocks.ALTAR.get(
                                    com.elysium.agronomy.rules.AltarRules.RELAY)
                                    .get())
                    .build(null));

    /** One type for all four stations: the block says which it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.agronomy.block.StationBlock.StationBlockEntity>>
            STATION = TYPES.register("station", () -> BlockEntityType.Builder.of(
                    com.elysium.agronomy.block.StationBlock.StationBlockEntity::new,
                    everyStation())
            .build(null));

    /**
     * Every station the kitchen has.
     *
     * <p>Walked from the rules, so a fifth station is a block entity the day
     * it is added rather than a pot that takes ingredients and never cooks.
     */
    private static net.minecraft.world.level.block.Block[] everyStation() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        for (String station : com.elysium.agronomy.rules.KitchenRules.STATIONS) {
            all.add(KitchenBlocks.STATIONS.get(station).get());
        }
        if (all.size() != com.elysium.agronomy.rules.KitchenRules.STATIONS.size()) {
            throw new IllegalStateException("there are "
                    + com.elysium.agronomy.rules.KitchenRules.STATIONS.size()
                    + " station(s) and " + all.size() + " were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

    /** One type for every cabinet, whatever it is made of. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.elysium.agronomy.block.CabinetBlock.CabinetBlockEntity>>
            CABINET = TYPES.register("cabinet", () -> BlockEntityType.Builder.of(
                    com.elysium.agronomy.block.CabinetBlock.CabinetBlockEntity::new,
                    everyCabinet())
            .build(null));

    /**
     * Every cabinet the kitchen has, walked from the woods.
     *
     * <p>A twelfth wood is a cabinet that opens the day it is added, rather
     * than one that places, looks like a cupboard, and holds nothing.
     */
    private static net.minecraft.world.level.block.Block[] everyCabinet() {
        java.util.List<net.minecraft.world.level.block.Block> all =
                new java.util.ArrayList<>();
        for (String wood : KitchenBlocks.WOODS) {
            all.add(KitchenBlocks.CABINETS.get(wood).get());
        }
        if (all.size() != KitchenBlocks.WOODS.size()) {
            throw new IllegalStateException("there are " + KitchenBlocks.WOODS.size()
                    + " woods and " + all.size() + " cabinet(s) were given a block entity");
        }
        return all.toArray(new net.minecraft.world.level.block.Block[0]);
    }

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
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENTRIFUGE.get(),
                    (entity, side) -> entity.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ALTAR.get(),
                    (entity, side) -> entity.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                    ALTAR_STAND.get(), (entity, side) -> entity.items());
        }
    }
}
