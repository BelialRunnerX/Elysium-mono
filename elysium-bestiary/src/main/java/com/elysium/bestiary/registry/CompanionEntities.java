package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.CompanionEntity;
import com.elysium.bestiary.rules.CompanionRules;
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
 * Eleven companions, registered from the rules rather than typed.
 *
 * <p>In the creature bucket rather than the monster one, because none of them
 * is a fight — and the bucket is not a detail: the game counts the two against
 * separate caps, so a companion in the monster bucket is a companion that
 * crowds out the things a player is meant to be afraid of.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CompanionEntities {

    private CompanionEntities() {
    }

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<CompanionEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<CompanionEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<CompanionEntity>>> made =
                new LinkedHashMap<>();
        for (CompanionRules.Companion one : CompanionRules.COMPANIONS) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),
                    () -> EntityType.Builder.<CompanionEntity>of(
                                    CompanionEntity::new, MobCategory.CREATURE)
                            .sized(0.7F, 0.9F)
                            .clientTrackingRange(8)
                            .build(one.id())));
        }
        if (made.size() != CompanionRules.COMPANIONS.size()) {
            throw new IllegalStateException("the rules name "
                    + CompanionRules.COMPANIONS.size() + " companion(s) and "
                    + made.size() + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (CompanionRules.Companion one : CompanionRules.COMPANIONS) {
            event.put(ALL.get(one.id()).get(), CompanionEntity.attributes().build());
        }
    }

    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (CompanionRules.Companion one : CompanionRules.COMPANIONS) {
            event.register(ALL.get(one.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Animal::checkAnimalSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
