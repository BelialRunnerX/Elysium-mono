package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.FaunaEntity;
import com.elysium.bestiary.rules.FaunaRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Twelve animals, registered from the fauna rather than typed.
 *
 * <p>A thirteenth is a line in {@link FaunaRules#ANIMALS} and nothing here
 * changes — including its attributes, its category and its spawn rule, all
 * three of which are walked from the same list.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FaunaEntities {

    private FaunaEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumBestiary.MODID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<FaunaEntity>>>
            ALL = register();

    /**
     * Which of the game's spawn buckets an animal belongs in.
     *
     * <p>Not a detail. The game counts creatures and water creatures against
     * separate caps, and a fish in the land bucket is a fish that crowds out
     * every animal in the world — which reads as "the pack has too few cows"
     * and is diagnosed nowhere.
     */
    public static MobCategory categoryOf(FaunaRules.Animal animal) {
        return FaunaRules.SWIMMER.equals(animal.kind())
                ? MobCategory.WATER_CREATURE
                : MobCategory.CREATURE;
    }

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<FaunaEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<FaunaEntity>>> made =
                new LinkedHashMap<>();
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            made.put(animal.id(), TYPES.register(animal.id(),
                    () -> EntityType.Builder.<FaunaEntity>of(FaunaEntity::new,
                                    categoryOf(animal))
                            .sized(animal.width(), animal.height())
                            .clientTrackingRange(8)
                            .build(animal.id())));
        }
        if (made.size() != FaunaRules.ANIMALS.size()) {
            throw new IllegalStateException("the fauna has "
                    + FaunaRules.ANIMALS.size() + " animals and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            event.put(ALL.get(animal.id()).get(), FaunaEntity.attributes(animal).build());
        }
    }

    /**
     * Where they may appear.
     *
     * <p>A swimmer is placed in water and everything else on the ground, and
     * the haunt decides the rest. Inventing a placement is how a pack ends up
     * with deer in the ocean.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            event.register(ALL.get(animal.id()).get(),
                    FaunaRules.SWIMMER.equals(animal.kind())
                            ? SpawnPlacementTypes.IN_WATER
                            : SpawnPlacementTypes.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types
                            .MOTION_BLOCKING_NO_LEAVES,
                    FaunaEntity::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
