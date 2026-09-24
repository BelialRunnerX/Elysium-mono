package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.BeeEntity;
import com.elysium.agronomy.rules.BeeRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entity type per species, walked out of the rules.
 *
 * <p>A seventy-sixth bee is a line in {@link BeeRules#BEES} and nothing in
 * this file changes. That is the whole point: the mod this replaces has 463
 * entity types and no way to notice that 428 of them lead nowhere, because
 * each was written out by hand.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class BeeEntities {

    private BeeEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumAgronomy.MODID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<BeeEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<BeeEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<BeeEntity>>> made =
                new LinkedHashMap<>();
        for (BeeRules.Bee bee : BeeRules.BEES) {
            made.put(bee.id(), TYPES.register(bee.entity(),
                    () -> EntityType.Builder.<BeeEntity>of(BeeEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.7F, 0.6F)
                            .eyeHeight(0.3F)
                            .clientTrackingRange(8)
                            .build(bee.entity())));
        }
        // Seventy-four of seventy-five registered is a module that works until
        // somebody breeds the seventy-fifth, which is a fortnight later and a
        // crash nobody can reproduce. Counting turns it into a game that will
        // not start, which is the kind of failure you can act on.
        if (made.size() != BeeRules.BEES.size()) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()
                    + " bees and " + made.size() + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** The type for a species. Throws rather than returning null. */
    public static EntityType<BeeEntity> of(String species) {
        DeferredHolder<EntityType<?>, EntityType<BeeEntity>> holder = ALL.get(species);
        if (holder == null) {
            throw new IllegalArgumentException("no bee called " + species);
        }
        return holder.get();
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (BeeRules.Bee bee : BeeRules.BEES) {
            event.put(ALL.get(bee.id()).get(), BeeEntity.attributes().build());
        }
    }

    /**
     * Where a bee may appear on its own.
     *
     * <p>Only the four you find in the world. Everything else is bred, and a
     * bred bee that also spawns wild is a breeding tree nobody needs to climb.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (BeeRules.Bee bee : BeeRules.BEES) {
            if (!bee.found()) {
                continue;
            }
            event.register(ALL.get(bee.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) ->
                            level.getRawBrightness(pos, 0) >= 8,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
