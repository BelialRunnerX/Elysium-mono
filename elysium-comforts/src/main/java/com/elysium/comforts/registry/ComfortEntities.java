package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.entity.PigeonEntity;
import com.elysium.comforts.rules.PostRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
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
 * Two birds, registered from the rules rather than typed.
 *
 * <p>Attributes, category and spawn rule are all walked from the same list, so
 * a third bird is a row in {@link PostRules#BIRDS} and nothing here changes. A
 * bird registered without one of the three is a bird that either has no health,
 * crowds out every animal in the world, or cannot appear at all — and all three
 * read to a player as something else entirely.
 */
@EventBusSubscriber(modid = ElysiumComforts.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ComfortEntities {

    private ComfortEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumComforts.MODID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<PigeonEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<PigeonEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<PigeonEntity>>> made =
                new LinkedHashMap<>();
        for (PostRules.Bird bird : PostRules.BIRDS) {
            made.put(bird.id(), TYPES.register(bird.id(),
                    () -> EntityType.Builder.<PigeonEntity>of(PigeonEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.5F, 0.6F)
                            .clientTrackingRange(8)
                            .build(bird.id())));
        }
        if (made.size() != PostRules.BIRDS.size()) {
            throw new IllegalStateException("the post has "
                    + PostRules.BIRDS.size() + " bird(s) and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (PostRules.Bird bird : PostRules.BIRDS) {
            event.put(ALL.get(bird.id()).get(), PigeonEntity.attributes().build());
        }
    }

    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (PostRules.Bird bird : PostRules.BIRDS) {
            event.register(ALL.get(bird.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Animal::checkAnimalSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
